package com.xtawa.samsunghzops.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xtawa.samsunghzops.core.model.RefreshMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hzOpsDataStore by preferencesDataStore(name = "hz_ops_preferences")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val defaultMode = stringPreferencesKey("default_refresh_mode")
        val masterEnabled = booleanPreferencesKey("master_enabled")
        val automationEnabled = booleanPreferencesKey("automation_enabled")
        val psmHighRefreshEnabled = booleanPreferencesKey("psm_high_refresh_enabled")
        val quickDozeEnabled = booleanPreferencesKey("quick_doze_enabled")
        val batteryProtectEnabled = booleanPreferencesKey("battery_protect_enabled")
        val networkSpeedEnabled = booleanPreferencesKey("network_speed_enabled")
        val sensorsOffEnabled = booleanPreferencesKey("sensors_off_enabled")
        val restoreGuardEnabled = booleanPreferencesKey("restore_guard_enabled")
        val adaptiveModEnabled = booleanPreferencesKey("adaptive_mod_enabled")
        val masterSyncSnapshotCaptured = booleanPreferencesKey("master_sync_snapshot_captured")
        val masterSyncOriginal = booleanPreferencesKey("master_sync_original")
        val themeMode = stringPreferencesKey("theme_mode")
        val language = stringPreferencesKey("language")
        val lowBatteryThreshold = intPreferencesKey("low_battery_threshold")
        val lowBatteryResumeThreshold = intPreferencesKey("low_battery_resume_threshold")
        val ignoreLowBatteryWhenCharging = booleanPreferencesKey("ignore_low_battery_when_charging")
        val originalDeviceIdleConstants = stringPreferencesKey("original_device_idle_constants")
        val originalDeviceIdleCaptured = booleanPreferencesKey("original_device_idle_captured")
    }

    val defaultMode: Flow<RefreshMode> = context.hzOpsDataStore.data.map { preferences ->
        runCatching {
            RefreshMode.valueOf(preferences[Keys.defaultMode] ?: RefreshMode.ADAPTIVE.name)
        }.getOrDefault(RefreshMode.ADAPTIVE)
    }
    val masterEnabled: Flow<Boolean> = context.hzOpsDataStore.data.map {
        it[Keys.masterEnabled] ?: true
    }
    val automationEnabled: Flow<Boolean> = context.hzOpsDataStore.data.map {
        it[Keys.automationEnabled] ?: false
    }
    val psmHighRefreshEnabled: Flow<Boolean> = context.hzOpsDataStore.data.map {
        it[Keys.psmHighRefreshEnabled] ?: false
    }
    val quickDozeEnabled: Flow<Boolean> = context.hzOpsDataStore.data.map {
        it[Keys.quickDozeEnabled] ?: false
    }
    val batteryProtectEnabled: Flow<Boolean> = context.hzOpsDataStore.data.map {
        it[Keys.batteryProtectEnabled] ?: false
    }
    val networkSpeedEnabled: Flow<Boolean> = context.hzOpsDataStore.data.map {
        it[Keys.networkSpeedEnabled] ?: false
    }
    val sensorsOffEnabled: Flow<Boolean> = context.hzOpsDataStore.data.map {
        it[Keys.sensorsOffEnabled] ?: false
    }
    val restoreGuardEnabled: Flow<Boolean> = context.hzOpsDataStore.data.map {
        it[Keys.restoreGuardEnabled] ?: false
    }
    val adaptiveModEnabled: Flow<Boolean> = context.hzOpsDataStore.data.map {
        it[Keys.adaptiveModEnabled] ?: false
    }
    val themeMode: Flow<String> = context.hzOpsDataStore.data.map { it[Keys.themeMode] ?: "system" }
    val language: Flow<String> = context.hzOpsDataStore.data.map { it[Keys.language] ?: "system" }
    val lowBatteryThreshold: Flow<Int> = context.hzOpsDataStore.data.map {
        it[Keys.lowBatteryThreshold] ?: 15
    }
    val lowBatteryResumeThreshold: Flow<Int> = context.hzOpsDataStore.data.map {
        it[Keys.lowBatteryResumeThreshold] ?: 18
    }
    val ignoreLowBatteryWhenCharging: Flow<Boolean> = context.hzOpsDataStore.data.map {
        it[Keys.ignoreLowBatteryWhenCharging] ?: true
    }

    suspend fun setDefaultMode(mode: RefreshMode) = context.hzOpsDataStore.edit {
        it[Keys.defaultMode] = mode.name
    }

    suspend fun setMasterEnabled(enabled: Boolean) = context.hzOpsDataStore.edit {
        it[Keys.masterEnabled] = enabled
    }

    suspend fun setAutomationEnabled(enabled: Boolean) = context.hzOpsDataStore.edit {
        it[Keys.automationEnabled] = enabled
    }

    suspend fun setPsmHighRefreshEnabled(enabled: Boolean) = context.hzOpsDataStore.edit {
        it[Keys.psmHighRefreshEnabled] = enabled
    }

    suspend fun setQuickDozeEnabled(enabled: Boolean) = context.hzOpsDataStore.edit {
        it[Keys.quickDozeEnabled] = enabled
    }

    suspend fun setBatteryProtectEnabled(enabled: Boolean) = context.hzOpsDataStore.edit {
        it[Keys.batteryProtectEnabled] = enabled
    }

    suspend fun setNetworkSpeedEnabled(enabled: Boolean) = context.hzOpsDataStore.edit {
        it[Keys.networkSpeedEnabled] = enabled
    }

    suspend fun setSensorsOffEnabled(enabled: Boolean) = context.hzOpsDataStore.edit {
        it[Keys.sensorsOffEnabled] = enabled
    }

    suspend fun setRestoreGuardEnabled(enabled: Boolean) = context.hzOpsDataStore.edit {
        it[Keys.restoreGuardEnabled] = enabled
    }

    suspend fun setAdaptiveModEnabled(enabled: Boolean) = context.hzOpsDataStore.edit {
        it[Keys.adaptiveModEnabled] = enabled
    }

    suspend fun setLowBatteryThreshold(threshold: Int) = context.hzOpsDataStore.edit {
        it[Keys.lowBatteryThreshold] = threshold.coerceIn(5, 50)
    }

    suspend fun setLowBatteryResumeThreshold(threshold: Int) = context.hzOpsDataStore.edit {
        it[Keys.lowBatteryResumeThreshold] = threshold.coerceIn(6, 60)
    }

    suspend fun setIgnoreLowBatteryWhenCharging(ignore: Boolean) = context.hzOpsDataStore.edit {
        it[Keys.ignoreLowBatteryWhenCharging] = ignore
    }

    suspend fun captureMasterSyncIfMissing(enabled: Boolean) = context.hzOpsDataStore.edit {
        if (it[Keys.masterSyncSnapshotCaptured] != true) {
            it[Keys.masterSyncSnapshotCaptured] = true
            it[Keys.masterSyncOriginal] = enabled
        }
    }

    suspend fun originalMasterSync(): Boolean? {
        val preferences = context.hzOpsDataStore.data.first()
        return if (preferences[Keys.masterSyncSnapshotCaptured] == true) {
            preferences[Keys.masterSyncOriginal] ?: true
        } else {
            null
        }
    }

    suspend fun clearMasterSyncSnapshot() = context.hzOpsDataStore.edit {
        it.remove(Keys.masterSyncSnapshotCaptured)
        it.remove(Keys.masterSyncOriginal)
    }

    suspend fun captureDeviceIdleConstantsIfMissing(raw: String?) = context.hzOpsDataStore.edit {
        if (it[Keys.originalDeviceIdleCaptured] != true) {
            it[Keys.originalDeviceIdleCaptured] = true
            it[Keys.originalDeviceIdleConstants] = raw.orEmpty()
        }
    }

    suspend fun originalDeviceIdleConstants(): String? {
        val preferences = context.hzOpsDataStore.data.first()
        return if (preferences[Keys.originalDeviceIdleCaptured] == true) {
            preferences[Keys.originalDeviceIdleConstants]
        } else {
            null
        }
    }

    suspend fun clearDeviceIdleSnapshot() = context.hzOpsDataStore.edit {
        it.remove(Keys.originalDeviceIdleCaptured)
        it.remove(Keys.originalDeviceIdleConstants)
    }

    suspend fun setThemeMode(mode: String) = context.hzOpsDataStore.edit {
        it[Keys.themeMode] = mode
    }

    suspend fun setLanguage(language: String) = context.hzOpsDataStore.edit {
        it[Keys.language] = language
    }
}
