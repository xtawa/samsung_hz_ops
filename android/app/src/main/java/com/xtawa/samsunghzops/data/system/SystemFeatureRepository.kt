package com.xtawa.samsunghzops.data.system

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.SettingMutation
import com.xtawa.samsunghzops.data.preferences.PreferencesRepository
import com.xtawa.samsunghzops.core.transaction.TransactionCoordinator
import com.xtawa.samsunghzops.data.settings.SettingsBackend
import com.xtawa.samsunghzops.data.settings.SettingsFieldRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** System tools that are independent from the refresh policy engine. */
class SystemFeatureRepository(
    context: Context,
    private val settings: SettingsBackend,
    private val transactions: TransactionCoordinator,
    private val preferences: PreferencesRepository,
) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    suspend fun setAnimations(enabled: Boolean): OperationResult<Unit> = transactions.apply(
        operation = if (enabled) "恢复系统动画" else "关闭系统动画",
        requestedMutations = listOf(
            SettingMutation(SettingsFieldRegistry.windowAnimationScale, if (enabled) "1.0" else "0.0"),
            SettingMutation(SettingsFieldRegistry.transitionAnimationScale, if (enabled) "1.0" else "0.0"),
            SettingMutation(SettingsFieldRegistry.animatorDurationScale, if (enabled) "1.0" else "0.0"),
        ),
    )

    suspend fun setForceResizable(enabled: Boolean): OperationResult<Unit> = transactions.apply(
        operation = if (enabled) "开启强制可调整大小" else "关闭强制可调整大小",
        requestedMutations = listOf(
            SettingMutation(
                SettingsFieldRegistry.forceResizableActivities,
                if (enabled) "1" else "0",
            ),
        ),
    )

    suspend fun setAod(enabled: Boolean): OperationResult<Unit> = transactions.apply(
        operation = if (enabled) "开启息屏显示" else "关闭息屏显示",
        requestedMutations = listOf(
            SettingMutation(SettingsFieldRegistry.aodMode, if (enabled) "1" else "0"),
        ),
    )

    suspend fun setSensorsOff(enabled: Boolean): OperationResult<Unit> = transactions.apply(
        operation = if (enabled) "关闭传感器" else "恢复传感器",
        requestedMutations = listOf(
            SettingMutation(SettingsFieldRegistry.sensorsOff, if (enabled) "1" else "0"),
        ),
    )

    suspend fun setNetworkSpeed(enabled: Boolean): OperationResult<Unit> = transactions.apply(
        operation = if (enabled) "显示网络速度" else "隐藏网络速度",
        requestedMutations = listOf(
            SettingMutation(SettingsFieldRegistry.networkSpeed, if (enabled) "1" else "0"),
        ),
    )

    suspend fun setQuickDoze(enabled: Boolean): OperationResult<Unit> = transactions.apply(
        operation = if (enabled) "启用快速 Doze" else "恢复 Doze 默认参数",
        requestedMutations = listOf(
            SettingMutation(
                SettingsFieldRegistry.deviceIdleConstants,
                if (enabled) {
                    "inactive_to=60000,sensing_to=0,locating_to=0,motion_inactive_to=60000"
                } else {
                    null
                },
            ),
        ),
    )

    suspend fun setLowPower(enabled: Boolean): OperationResult<Unit> = transactions.apply(
        operation = if (enabled) "开启省电模式" else "关闭省电模式",
        requestedMutations = listOf(
            SettingMutation(SettingsFieldRegistry.lowPower, if (enabled) "1" else "0"),
        ),
    )

    suspend fun setSyncAutomatically(enabled: Boolean): OperationResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                preferences.captureMasterSyncIfMissing(ContentResolver.getMasterSyncAutomatically())
                ContentResolver.setMasterSyncAutomatically(enabled)
                OperationResult.Success(Unit)
            }.getOrElse { error ->
                OperationResult.Failure("同步设置失败：${error.message ?: error.javaClass.simpleName}")
            }
        }

    suspend fun addQuickSettingsTile(tileSpec: String): OperationResult<Unit> = withContext(Dispatchers.IO) {
        if (!settings.canWrite(SettingsFieldRegistry.qsTiles)) {
            return@withContext OperationResult.Failure("没有修改快捷设置磁贴所需的 Secure 写入权限")
        }
        val current = Settings.Secure.getString(resolver, SettingsFieldRegistry.qsTiles.key).orEmpty()
        val tiles = current.split(',').map(String::trim).filter(String::isNotEmpty)
        if (tiles.any { it == tileSpec }) return@withContext OperationResult.Success(Unit)
        when (val result = settings.write(SettingsFieldRegistry.qsTiles, (tiles + tileSpec).joinToString(","))) {
            is OperationResult.Success -> result
            is OperationResult.Failure -> result
        }
    }

    fun supportsResolutionControl(): Boolean = false

    fun supportsBatteryChargeLimit(): Boolean = false
}
