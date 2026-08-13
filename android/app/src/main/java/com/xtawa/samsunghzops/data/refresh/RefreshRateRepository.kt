package com.xtawa.samsunghzops.data.refresh

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import androidx.core.content.ContextCompat
import com.xtawa.samsunghzops.core.model.DisplayModeInfo
import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.RefreshMode
import com.xtawa.samsunghzops.core.model.RefreshPolicyDecision
import com.xtawa.samsunghzops.core.model.RefreshRange
import com.xtawa.samsunghzops.core.model.RefreshSnapshot
import com.xtawa.samsunghzops.core.model.SettingMutation
import com.xtawa.samsunghzops.core.transaction.TransactionCoordinator
import com.xtawa.samsunghzops.data.settings.SettingsFieldRegistry
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Read and write the platform refresh-rate contract. All writes go through the
 * transaction coordinator; Compose and policy code never touch Settings APIs.
 */
class RefreshRateRepository(
    context: Context,
    private val transactions: TransactionCoordinator,
    private val samsungModeMapping: SamsungModeMapping = SamsungModeMapping(),
) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val displayManager = appContext.getSystemService(DisplayManager::class.java)
    private val powerManager = appContext.getSystemService(PowerManager::class.java)
    private val _snapshot = MutableStateFlow(RefreshSnapshot())
    val snapshot: StateFlow<RefreshSnapshot> = _snapshot.asStateFlow()

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) refresh()
        }
    }

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    init {
        displayManager?.registerDisplayListener(displayListener, null)
        resolver.registerContentObserver(
            Settings.System.getUriFor(SettingsFieldRegistry.minRefreshRate.key),
            false,
            settingsObserver,
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(SettingsFieldRegistry.peakRefreshRate.key),
            false,
            settingsObserver,
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(SettingsFieldRegistry.refreshRateMode.key),
            false,
            settingsObserver,
        )
        ContextCompat.registerReceiver(
            appContext,
            powerReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    fun close() {
        displayManager?.unregisterDisplayListener(displayListener)
        resolver.unregisterContentObserver(settingsObserver)
        runCatching { appContext.unregisterReceiver(powerReceiver) }
    }

    fun refresh() {
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val modes = display?.supportedModes?.map { mode ->
            DisplayModeInfo(
                id = mode.modeId,
                width = mode.physicalWidth,
                height = mode.physicalHeight,
                refreshRateHz = mode.refreshRate,
                alternativeRefreshRatesHz = if (Build.VERSION.SDK_INT >= 31) {
                    mode.alternativeRefreshRates.toList()
                } else {
                    emptyList()
                },
            )
        }.orEmpty()
        val activeRate = display?.mode?.refreshRate
        val settingsRange = readSettingsRange(modes)
        val inferredMode = inferActiveMode(settingsRange, modes)
        _snapshot.value = _snapshot.value.copy(
            activeMode = inferredMode ?: _snapshot.value.activeMode,
            activeRefreshRateHz = activeRate,
            supportedModes = modes,
            range = settingsRange ?: _snapshot.value.range,
            isPowerSaveMode = powerManager?.isPowerSaveMode == true,
            lastError = null,
        )
    }

    suspend fun applyDecision(decision: RefreshPolicyDecision): OperationResult<Unit> =
        applyRange(decision.mode, decision.range, decision.reason)

    suspend fun applyMode(
        mode: RefreshMode,
        range: RefreshRange,
        reason: String = "用户操作",
    ): OperationResult<Unit> = applyRange(mode, range, reason)

    private suspend fun applyRange(
        mode: RefreshMode,
        range: RefreshRange?,
        reason: String,
    ): OperationResult<Unit> {
        if (range == null) {
            return OperationResult.Failure("设备尚未报告可用刷新率")
        }
        val mutations = buildList {
            samsungModeMapping.valueFor(mode)?.let { modeValue ->
                add(
                    SettingMutation(
                        spec = SettingsFieldRegistry.refreshRateMode,
                        value = modeValue,
                    ),
                )
            }
            add(
                SettingMutation(
                    spec = SettingsFieldRegistry.minRefreshRate,
                    value = formatHz(range.minHz),
                ),
            )
            add(
                SettingMutation(
                    spec = SettingsFieldRegistry.peakRefreshRate,
                    value = formatHz(range.maxHz),
                ),
            )
        }
        val result = transactions.apply(
            operation = "刷新率：${mode.name.lowercase(Locale.ROOT)}（$reason）",
            requestedMutations = mutations,
        )
        return when (result) {
            is OperationResult.Success -> {
                // The transaction already performs write/read-back verification.
                // Re-read all UI-visible state after commit so the selected chip,
                // current range and live rate come from the device rather than
                // from an optimistic local assignment.
                refresh()
                val warnings = if (samsungModeMapping.valueFor(mode) == null) {
                    listOf("未写入三星 refresh_rate_mode：需要目标机校准映射")
                } else {
                    emptyList()
                }
                _snapshot.value = _snapshot.value.copy(
                    activeMode = mode,
                    range = range,
                    lastAppliedAt = Instant.now(),
                    lastError = null,
                )
                OperationResult.Success(Unit, warnings)
            }

            is OperationResult.Failure -> {
                // TransactionCoordinator may have rolled back one or more keys.
                // Reload after rollback before showing the error, otherwise the UI
                // can continue displaying the failed target state.
                refresh()
                _snapshot.value = _snapshot.value.copy(lastError = result.message)
                result
            }
        }
    }

    suspend fun resetToSystemDefault(): OperationResult<Unit> {
        val result = transactions.reset(
            operation = "恢复系统默认刷新率",
            specs = buildList {
                add(SettingsFieldRegistry.minRefreshRate)
                add(SettingsFieldRegistry.peakRefreshRate)
                if (samsungModeMapping.standard != null ||
                    samsungModeMapping.adaptive != null ||
                    samsungModeMapping.maximum != null
                ) {
                    add(SettingsFieldRegistry.refreshRateMode)
                }
            },
        )
        refresh()
        return result
    }

    private fun formatHz(value: Float): String =
        String.format(Locale.US, "%.1f", value)

    private fun readSettingsRange(modes: List<DisplayModeInfo>): RefreshRange? {
        val min = Settings.System.getString(resolver, SettingsFieldRegistry.minRefreshRate.key)
            ?.toFloatOrNull()
        val peak = Settings.System.getString(resolver, SettingsFieldRegistry.peakRefreshRate.key)
            ?.toFloatOrNull()
        if (min == null && peak == null) return null
        val supportedHz = modes.map { it.refreshRateHz }.filter { it.isFinite() && it > 0f }
        val fallbackMin = supportedHz.minOrNull() ?: min ?: return null
        val fallbackMax = supportedHz.maxOrNull() ?: peak ?: return null
        val resolvedMin = nearestSupportedHz(min ?: fallbackMin, supportedHz)
        val resolvedMax = nearestSupportedHz(peak ?: fallbackMax, supportedHz)
        return RefreshRange(
            minHz = resolvedMin.coerceAtMost(resolvedMax),
            maxHz = resolvedMax.coerceAtLeast(resolvedMin),
        )
    }

    private fun nearestSupportedHz(value: Float, supportedHz: List<Float>): Float =
        supportedHz.minByOrNull { kotlin.math.abs(it - value) } ?: value

    private fun inferActiveMode(
        range: RefreshRange?,
        modes: List<DisplayModeInfo>,
    ): RefreshMode? {
        if (range == null) return null
        val supportedHz = modes.map { it.refreshRateHz }
            .filter { it.isFinite() && it > 0f }
            .distinct()
            .sorted()
        val lowest = supportedHz.firstOrNull() ?: return if (approximatelyEqual(range.minHz, range.maxHz)) {
            RefreshMode.STANDARD
        } else {
            RefreshMode.ADAPTIVE
        }
        val highest = supportedHz.last()
        val fixed = approximatelyEqual(range.minHz, range.maxHz)
        return when {
            fixed && approximatelyEqual(range.maxHz, highest) && !approximatelyEqual(lowest, highest) -> {
                RefreshMode.MAXIMUM
            }

            fixed -> RefreshMode.STANDARD
            approximatelyEqual(range.minHz, lowest) && approximatelyEqual(range.maxHz, highest) -> {
                RefreshMode.ADAPTIVE
            }

            else -> RefreshMode.ADAPTIVE
        }
    }

    private fun approximatelyEqual(left: Float, right: Float): Boolean =
        kotlin.math.abs(left - right) < REFRESH_RATE_EPSILON

    private companion object {
        const val REFRESH_RATE_EPSILON = 0.1f
    }
}

/** Numeric Samsung mode values vary by One UI/device; keep them injectable. */
data class SamsungModeMapping(
    val standard: String? = null,
    val adaptive: String? = null,
    val maximum: String? = null,
) {
    fun valueFor(mode: RefreshMode): String? = when (mode) {
        RefreshMode.STANDARD -> standard
        RefreshMode.ADAPTIVE -> adaptive
        RefreshMode.MAXIMUM -> maximum
    }
}
