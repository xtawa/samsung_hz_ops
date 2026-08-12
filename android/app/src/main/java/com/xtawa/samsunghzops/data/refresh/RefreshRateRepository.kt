package com.xtawa.samsunghzops.data.refresh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
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
) {
    private val appContext = context.applicationContext
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

    init {
        displayManager?.registerDisplayListener(displayListener, null)
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
        _snapshot.value = _snapshot.value.copy(
            activeRefreshRateHz = activeRate,
            supportedModes = modes,
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
        val mutations = listOf(
            SettingMutation(
                spec = SettingsFieldRegistry.minRefreshRate,
                value = formatHz(range.minHz),
            ),
            SettingMutation(
                spec = SettingsFieldRegistry.peakRefreshRate,
                value = formatHz(range.maxHz),
            ),
        )
        val result = transactions.apply(
            operation = "刷新率：${mode.name.lowercase(Locale.ROOT)}（$reason）",
            requestedMutations = mutations,
        )
        if (result is OperationResult.Success) {
            _snapshot.value = _snapshot.value.copy(
                activeMode = mode,
                range = range,
                lastAppliedAt = Instant.now(),
                lastError = null,
            )
        } else if (result is OperationResult.Failure) {
            _snapshot.value = _snapshot.value.copy(lastError = result.message)
        }
        return result
    }

    suspend fun resetToSystemDefault(): OperationResult<Unit> {
        val result = transactions.reset(
            operation = "恢复系统默认刷新率",
            specs = listOf(
                SettingsFieldRegistry.minRefreshRate,
                SettingsFieldRegistry.peakRefreshRate,
            ),
        )
        refresh()
        return result
    }

    private fun formatHz(value: Float): String =
        String.format(Locale.US, "%.1f", value)
}
