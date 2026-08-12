package com.xtawa.samsunghzops.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xtawa.samsunghzops.HzOpsApplication
import com.xtawa.samsunghzops.core.model.AppProfile
import com.xtawa.samsunghzops.core.model.CapabilityStatus
import com.xtawa.samsunghzops.core.model.DisplayModeInfo
import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.RefreshMode
import com.xtawa.samsunghzops.core.model.RefreshPolicyDecision
import com.xtawa.samsunghzops.core.model.RefreshSnapshot
import com.xtawa.samsunghzops.data.samsung.PsmState
import com.xtawa.samsunghzops.data.profile.TransactionEntity
import com.xtawa.samsunghzops.service.AutomationService
import com.xtawa.samsunghzops.service.RefreshMonitorService
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class AppDestination(val label: String, val icon: String) {
    CONTROL("控制", "tune"),
    RULES("规则", "bolt"),
    TOOLS("工具", "build"),
    MORE("更多", "more"),
}

data class MainUiState(
    val destination: AppDestination = AppDestination.CONTROL,
    val snapshot: RefreshSnapshot = RefreshSnapshot(),
    val decision: RefreshPolicyDecision = RefreshPolicyDecision(
        mode = RefreshMode.ADAPTIVE,
        range = null,
        reason = "等待设备信息",
    ),
    val psm: PsmState = PsmState(),
    val capabilities: List<CapabilityStatus> = emptyList(),
    val profiles: List<AppProfile> = emptyList(),
    val journal: List<TransactionEntity> = emptyList(),
    val automationEnabled: Boolean = false,
    val quickDozeEnabled: Boolean = false,
    val animationsEnabled: Boolean = true,
    val aodEnabled: Boolean = false,
    val sensorsOffEnabled: Boolean = false,
    val networkSpeedEnabled: Boolean = false,
    val forceResizableEnabled: Boolean = false,
    val syncEnabled: Boolean = true,
    val managedSnapshotCount: Int = 0,
    val showEmergencyResetConfirm: Boolean = false,
    val detailTitle: String? = null,
    val detailBody: String? = null,
    val snackbar: String? = null,
    val busy: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HzOpsApplication
    private val container = app.container
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        collectState()
        refreshCapabilities()
    }

    private fun collectState() {
        viewModelScope.launch {
            container.refreshRates.snapshot.collect { snapshot ->
                update { it.copy(snapshot = snapshot) }
            }
        }
        viewModelScope.launch {
            container.policy.decision.collect { decision ->
                update { it.copy(decision = decision) }
            }
        }
        viewModelScope.launch {
            container.powerSave.state.collect { psm ->
                update { it.copy(psm = psm) }
            }
        }
        viewModelScope.launch {
            container.profiles.profiles.collect { profiles ->
                update { it.copy(profiles = profiles) }
            }
        }
        viewModelScope.launch {
            container.transactionJournal.recent.collect { records ->
                update { it.copy(journal = records) }
            }
        }
        viewModelScope.launch {
            container.managedSettings.snapshots.collect { snapshots ->
                update { it.copy(managedSnapshotCount = snapshots.size) }
            }
        }
        viewModelScope.launch {
            container.preferences.automationEnabled.collect { enabled ->
                update { it.copy(automationEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            container.preferences.quickDozeEnabled.collect { enabled ->
                update { it.copy(quickDozeEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            container.preferences.networkSpeedEnabled.collect { enabled ->
                update { it.copy(networkSpeedEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            container.preferences.sensorsOffEnabled.collect { enabled ->
                update { it.copy(sensorsOffEnabled = enabled) }
            }
        }
    }

    fun selectDestination(destination: AppDestination) = update { it.copy(destination = destination) }

    fun applyMode(mode: RefreshMode) {
        val state = _uiState.value
        val range = rangeFor(mode, state.snapshot.supportedModes)
        if (range == null) {
            showMessage("设备尚未报告可用刷新率")
            return
        }
        runOperation {
            container.preferences.setDefaultMode(mode)
            container.refreshRates.applyMode(mode, range, "控制页")
        }
    }

    fun applyAdaptiveRange(minHz: Float, maxHz: Float) {
        val state = _uiState.value
        val modes = state.snapshot.supportedModes
        if (modes.isEmpty()) return showMessage("设备尚未报告可用刷新率")
        val min = modes.map { it.refreshRateHz }.minByOrNull { kotlin.math.abs(it - minHz) } ?: minHz
        val max = modes.map { it.refreshRateHz }.minByOrNull { kotlin.math.abs(it - maxHz) } ?: maxHz
        val safeMin = min.coerceAtMost(max)
        runOperation {
            container.preferences.setDefaultMode(RefreshMode.ADAPTIVE)
            container.refreshRates.applyMode(
                RefreshMode.ADAPTIVE,
                com.xtawa.samsunghzops.core.model.RefreshRange(safeMin, max.coerceAtLeast(safeMin)),
                "自适应范围",
            )
        }
    }

    fun setKeepHighRefresh(enabled: Boolean) = runOperation {
        val result = container.powerSave.setKeepHighRefresh(enabled)
        if (result is OperationResult.Success) container.preferences.setPsmHighRefreshEnabled(enabled)
        result
    }

    fun setAutomationEnabled(enabled: Boolean) = runOperation {
        container.preferences.setAutomationEnabled(enabled)
        val result = OperationResult.Success(Unit)
        if (result is OperationResult.Success) {
            val intent = Intent(getApplication(), AutomationService::class.java)
            if (enabled) ContextCompat.startForegroundService(getApplication(), intent)
            else getApplication<Application>().stopService(intent)
        }
        result
    }

    fun startMonitor() {
        ContextCompat.startForegroundService(
            getApplication(),
            Intent(getApplication(), RefreshMonitorService::class.java),
        )
        showMessage("刷新率监视器已启动")
    }

    fun stopMonitor() {
        getApplication<Application>().stopService(Intent(getApplication(), RefreshMonitorService::class.java))
        showMessage("刷新率监视器已停止")
    }

    fun setQuickDoze(enabled: Boolean) = runOperation {
        val result = container.systemFeatures.setQuickDoze(enabled)
        if (result is OperationResult.Success) container.preferences.setQuickDozeEnabled(enabled)
        result
    }

    fun setAnimations(enabled: Boolean) = runOperation {
        val result = container.systemFeatures.setAnimations(enabled)
        if (result is OperationResult.Success) update { it.copy(animationsEnabled = enabled) }
        result
    }

    fun setAod(enabled: Boolean) = runOperation {
        val result = container.systemFeatures.setAod(enabled)
        if (result is OperationResult.Success) update { it.copy(aodEnabled = enabled) }
        result
    }

    fun setSensorsOff(enabled: Boolean) = runOperation {
        val result = container.systemFeatures.setSensorsOff(enabled)
        if (result is OperationResult.Success) container.preferences.setSensorsOffEnabled(enabled)
        result
    }

    fun setNetworkSpeed(enabled: Boolean) = runOperation {
        val result = container.systemFeatures.setNetworkSpeed(enabled)
        if (result is OperationResult.Success) container.preferences.setNetworkSpeedEnabled(enabled)
        result
    }

    fun setForceResizable(enabled: Boolean) = runOperation {
        val result = container.systemFeatures.setForceResizable(enabled)
        if (result is OperationResult.Success) update { it.copy(forceResizableEnabled = enabled) }
        result
    }

    fun setSync(enabled: Boolean) = runOperation {
        val result = container.systemFeatures.setSyncAutomatically(enabled)
        if (result is OperationResult.Success) update { it.copy(syncEnabled = enabled) }
        result
    }

    fun addForegroundProfile() {
        val packageName = container.deviceState.state.value.foregroundPackage
            ?: return showMessage("请先开启辅助功能并进入要配置的应用")
        val profile = AppProfile(
            packageName = packageName,
            appLabel = packageName.substringAfterLast('.'),
        )
        runOperation { container.profiles.save(profile).let { OperationResult.Success(Unit) } }
    }

    fun deleteProfile(profile: AppProfile) = runOperation {
        container.profiles.delete(profile).let { OperationResult.Success(Unit) }
    }

    fun resetDefaults() {
        requestEmergencyReset()
    }

    fun requestEmergencyReset() = update { it.copy(showEmergencyResetConfirm = true) }

    fun dismissEmergencyReset() = update { it.copy(showEmergencyResetConfirm = false) }

    fun confirmEmergencyReset() {
        viewModelScope.launch {
            update { it.copy(busy = true, showEmergencyResetConfirm = false) }
            val result = try {
                container.emergencyReset.resetAll()
            } catch (error: Throwable) {
                OperationResult.Failure("紧急还原失败：${error.message ?: error.javaClass.simpleName}")
            }
            update { state ->
                state.copy(
                    busy = false,
                    snackbar = when (result) {
                        is OperationResult.Success -> {
                            val report = result.value
                            buildString {
                                append("已还原 ${report.restoredSettings} 项系统设置并停止自动化")
                                if (report.warnings.isNotEmpty()) append("；${report.warnings.first()}")
                            }
                        }

                        is OperationResult.Failure -> result.message
                    },
                )
            }
            refreshCapabilities()
        }
    }

    fun refreshCapabilities() {
        update { it.copy(capabilities = container.capabilityProbe.snapshot()) }
    }

    fun openWriteSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${getApplication<Application>().packageName}"),
    )

    fun showDetails(title: String, body: String) = update {
        it.copy(detailTitle = title, detailBody = body)
    }

    fun closeDetails() = update { it.copy(detailTitle = null, detailBody = null) }

    fun clearSnackbar() = update { it.copy(snackbar = null) }

    private fun showMessage(message: String) = update { it.copy(snackbar = message) }

    private fun <T> runOperation(block: suspend () -> OperationResult<T>) {
        viewModelScope.launch {
            update { it.copy(busy = true) }
            val result = try {
                block()
            } catch (error: Throwable) {
                OperationResult.Failure("操作失败：${error.message ?: error.javaClass.simpleName}")
            }
            update { state ->
                state.copy(
                    busy = false,
                    snackbar = when (result) {
                        is OperationResult.Success -> result.warnings.firstOrNull() ?: "已应用"
                        is OperationResult.Failure -> result.message
                    },
                )
            }
            refreshCapabilities()
        }
    }

    private fun update(transform: (MainUiState) -> MainUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun rangeFor(mode: RefreshMode, modes: List<DisplayModeInfo>) = when (mode) {
        RefreshMode.STANDARD -> modes.minByOrNull { kotlin.math.abs(it.refreshRateHz - 60f) }
            ?.let { com.xtawa.samsunghzops.core.model.RefreshRange(it.refreshRateHz, it.refreshRateHz) }
        RefreshMode.ADAPTIVE -> modes.minByOrNull { it.refreshRateHz }?.let {
            com.xtawa.samsunghzops.core.model.RefreshRange(
                modes.minOf { item -> item.refreshRateHz },
                modes.maxOf { item -> item.refreshRateHz },
            )
        }
        RefreshMode.MAXIMUM -> modes.maxByOrNull { it.refreshRateHz }?.let {
            com.xtawa.samsunghzops.core.model.RefreshRange(it.refreshRateHz, it.refreshRateHz)
        }
    }
}
