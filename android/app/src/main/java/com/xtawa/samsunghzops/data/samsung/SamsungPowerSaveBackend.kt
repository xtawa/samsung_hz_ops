package com.xtawa.samsunghzops.data.samsung

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.SettingMutation
import com.xtawa.samsunghzops.core.transaction.TransactionCoordinator
import com.xtawa.samsunghzops.data.settings.SettingsBackend
import com.xtawa.samsunghzops.data.settings.SettingsFieldRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Samsung-specific PSM bridge. The values are intentionally isolated in a
 * mapping so a firmware calibration can replace them without changing UI or
 * policy code. On an unknown firmware the UI must show the confidence label.
 */
data class SamsungPsmMapping(
    val enabledValue: String = "1",
    val disabledValue: String = "0",
    val highRefreshTag: String = "1",
    val normalRefreshTag: String = "0",
    val restrictedPerformanceOff: String = "0",
    val confidence: String = "reverse-engineering inference — verify on target firmware",
)

data class PsmState(
    val powerSaveMode: Boolean = false,
    val keepHighRefresh: Boolean = false,
    val mappingConfidence: String = "unknown",
    val lastError: String? = null,
)

class SamsungPowerSaveBackend(
    context: Context,
    private val settings: SettingsBackend,
    private val transactions: TransactionCoordinator,
    private val mapping: SamsungPsmMapping = SamsungPsmMapping(),
) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(PowerManager::class.java)
    private val _state = MutableStateFlow(
        PsmState(
            powerSaveMode = powerManager?.isPowerSaveMode == true,
            mappingConfidence = mapping.confidence,
        ),
    )
    val state: StateFlow<PsmState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) refresh()
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    fun close() = runCatching { appContext.unregisterReceiver(receiver) }

    fun refresh() {
        _state.value = _state.value.copy(
            powerSaveMode = powerManager?.isPowerSaveMode == true,
            mappingConfidence = mapping.confidence,
            lastError = null,
        )
    }

    fun markKeepHighRefreshDisabled(error: String? = null) {
        _state.value = _state.value.copy(
            keepHighRefresh = false,
            lastError = error,
        )
    }

    suspend fun setKeepHighRefresh(enabled: Boolean): OperationResult<Unit> {
        val mutations = listOf(
            SettingMutation(
                spec = SettingsFieldRegistry.psmRefreshRateEnabled,
                value = if (enabled) mapping.enabledValue else mapping.disabledValue,
            ),
            SettingMutation(
                spec = SettingsFieldRegistry.psmRefreshRateTag,
                value = if (enabled) mapping.highRefreshTag else mapping.normalRefreshTag,
            ),
            SettingMutation(
                spec = SettingsFieldRegistry.psmRefreshRateEnabledCover,
                value = if (enabled) mapping.enabledValue else mapping.disabledValue,
            ),
            SettingMutation(
                spec = SettingsFieldRegistry.psmRefreshRateTagCover,
                value = if (enabled) mapping.highRefreshTag else mapping.normalRefreshTag,
            ),
            SettingMutation(
                spec = SettingsFieldRegistry.restrictedDevicePerformance,
                value = if (enabled) mapping.restrictedPerformanceOff else null,
            ),
        )
        val result = transactions.apply(
            operation = if (enabled) "开启省电模式保持高刷" else "关闭省电模式保持高刷",
            requestedMutations = mutations,
        )
        _state.value = _state.value.copy(
            keepHighRefresh = if (result is OperationResult.Success) enabled else _state.value.keepHighRefresh,
            lastError = (result as? OperationResult.Failure)?.message,
        )
        return result
    }

    /**
     * Exposes a capability check for the service. It does not write any value;
     * the service decides whether a policy decision should be re-applied.
     */
    fun canApply(): Boolean = settings.canWrite(SettingsFieldRegistry.psmRefreshRateEnabled)
}
