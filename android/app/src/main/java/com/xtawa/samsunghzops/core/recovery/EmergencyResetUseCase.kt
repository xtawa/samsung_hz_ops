package com.xtawa.samsunghzops.core.recovery

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import com.xtawa.samsunghzops.core.model.EmergencyResetReport
import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.SettingMutation
import com.xtawa.samsunghzops.core.transaction.TransactionCoordinator
import com.xtawa.samsunghzops.data.preferences.PreferencesRepository
import com.xtawa.samsunghzops.data.profile.ManagedSettingSnapshotRepository
import com.xtawa.samsunghzops.data.refresh.RefreshRateRepository
import com.xtawa.samsunghzops.data.samsung.SamsungPowerSaveBackend
import com.xtawa.samsunghzops.service.AutomationService
import com.xtawa.samsunghzops.service.RefreshMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stops every app-owned automation path and restores only values that Samsung
 * Hz Ops previously captured before writing. This is intentionally snapshot
 * driven; it never invents firmware defaults for Samsung private keys.
 */
class EmergencyResetUseCase(
    context: Context,
    private val preferences: PreferencesRepository,
    private val managedSettings: ManagedSettingSnapshotRepository,
    private val transactions: TransactionCoordinator,
    private val refreshRates: RefreshRateRepository,
    private val powerSave: SamsungPowerSaveBackend,
) {
    private val appContext = context.applicationContext

    suspend fun resetAll(): OperationResult<EmergencyResetReport> {
        val stoppedServices = stopAppServices()
        disableAppAutomationState()

        val warnings = mutableListOf<String>()
        restoreMasterSyncIfCaptured()?.let { warnings += it }

        val snapshots = managedSettings.all()
        val restoreResult = if (snapshots.isEmpty()) {
            OperationResult.Success(Unit, listOf("没有找到系统设置快照；已停止本 App 自动化"))
        } else {
            transactions.apply(
                operation = "紧急一键还原所有更改",
                requestedMutations = snapshots.map { snapshot ->
                    SettingMutation(
                        spec = snapshot.spec,
                        value = snapshot.originalValue,
                    )
                },
            )
        }

        return when (restoreResult) {
            is OperationResult.Success -> {
                managedSettings.clear()
                preferences.clearMasterSyncSnapshot()
                refreshRates.refresh()
                powerSave.markKeepHighRefreshDisabled()
                OperationResult.Success(
                    EmergencyResetReport(
                        restoredSettings = snapshots.size,
                        stoppedServices = stoppedServices,
                        warnings = warnings + restoreResult.warnings,
                    ),
                )
            }

            is OperationResult.Failure -> {
                refreshRates.refresh()
                powerSave.markKeepHighRefreshDisabled(restoreResult.message)
                OperationResult.Failure(
                    message = restoreResult.message,
                    recoverable = restoreResult.recoverable,
                    missingCapabilities = restoreResult.missingCapabilities,
                )
            }
        }
    }

    private suspend fun disableAppAutomationState() {
        preferences.setAutomationEnabled(false)
        preferences.setPsmHighRefreshEnabled(false)
        preferences.setQuickDozeEnabled(false)
        preferences.setBatteryProtectEnabled(false)
        preferences.setNetworkSpeedEnabled(false)
        preferences.setSensorsOffEnabled(false)
    }

    private suspend fun restoreMasterSyncIfCaptured(): String? = withContext(Dispatchers.IO) {
        val original = preferences.originalMasterSync() ?: return@withContext null
        runCatching {
            ContentResolver.setMasterSyncAutomatically(original)
            null
        }.getOrElse { error ->
            "自动同步状态未恢复：${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun stopAppServices(): List<String> {
        val stopped = mutableListOf<String>()
        val automationIntent = Intent(appContext, AutomationService::class.java)
        if (appContext.stopService(automationIntent)) stopped += "自动化策略服务"
        val monitorIntent = Intent(appContext, RefreshMonitorService::class.java)
        if (appContext.stopService(monitorIntent)) stopped += "刷新率监视器"
        return stopped
    }
}
