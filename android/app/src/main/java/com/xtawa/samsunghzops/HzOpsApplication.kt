package com.xtawa.samsunghzops

import android.app.Application
import androidx.room.Room
import com.xtawa.samsunghzops.core.recovery.EmergencyResetUseCase
import com.xtawa.samsunghzops.core.state.DeviceStateRepository
import com.xtawa.samsunghzops.data.capability.CapabilityProbe
import com.xtawa.samsunghzops.data.preferences.PreferencesRepository
import com.xtawa.samsunghzops.data.profile.AppProfileRepository
import com.xtawa.samsunghzops.data.profile.ManagedSettingSnapshotRepository
import com.xtawa.samsunghzops.data.profile.ProfileDatabase
import com.xtawa.samsunghzops.data.profile.TransactionJournalRepository
import com.xtawa.samsunghzops.data.refresh.RefreshRateRepository
import com.xtawa.samsunghzops.data.settings.AndroidSettingsBackend
import com.xtawa.samsunghzops.data.settings.ShizukuSettingsWriter
import com.xtawa.samsunghzops.data.settings.SettingsBackend
import com.xtawa.samsunghzops.data.samsung.SamsungPowerSaveBackend
import com.xtawa.samsunghzops.data.system.SystemFeatureRepository
import com.xtawa.samsunghzops.data.signals.DeviceSignalMonitor
import com.xtawa.samsunghzops.core.transaction.TransactionCoordinator
import com.xtawa.samsunghzops.policy.RefreshPolicyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HzOpsApplication : Application() {
    val container: HzOpsContainer by lazy { HzOpsContainer(this) }
}

class HzOpsContainer(application: Application) {
    private val appContext = application.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val preferences = PreferencesRepository(appContext)
    val deviceState = DeviceStateRepository()
    val signalMonitor = DeviceSignalMonitor(appContext, deviceState)
    val privilegedWriter = ShizukuSettingsWriter(appContext)
    val settingsBackend: SettingsBackend = AndroidSettingsBackend(appContext, privilegedWriter)
    val profileDatabase: ProfileDatabase = Room.databaseBuilder(
        appContext,
        ProfileDatabase::class.java,
        "hz_ops_profiles.db",
    ).addMigrations(ProfileDatabase.MIGRATION_1_2).build()
    val transactionJournal = TransactionJournalRepository(profileDatabase.transactionDao())
    val managedSettings = ManagedSettingSnapshotRepository(profileDatabase.managedSettingSnapshotDao())
    val transactions = TransactionCoordinator(
        backend = settingsBackend,
        journalSink = transactionJournal::append,
        managedSnapshotSink = managedSettings::captureIfMissing,
    )
    val refreshRates = RefreshRateRepository(appContext, transactions)
    val powerSave = SamsungPowerSaveBackend(appContext, settingsBackend, transactions)
    val systemFeatures = SystemFeatureRepository(appContext, settingsBackend, transactions, preferences)
    val policy = RefreshPolicyEngine()
    val capabilityProbe = CapabilityProbe(appContext, privilegedWriter)
    val profiles = AppProfileRepository(profileDatabase.profileDao())
    val emergencyReset = EmergencyResetUseCase(
        context = appContext,
        preferences = preferences,
        managedSettings = managedSettings,
        transactions = transactions,
        refreshRates = refreshRates,
        powerSave = powerSave,
    )

    init {
        scope.launch {
            refreshRates.snapshot.collectLatest { snapshot ->
                policy.updateSupportedModes(snapshot.supportedModes)
            }
        }
        scope.launch {
            deviceState.state.collectLatest { state ->
                policy.updateDeviceState(state)
            }
        }
        scope.launch {
            profiles.profiles.collectLatest { appProfiles ->
                policy.updateProfiles(appProfiles)
            }
        }
        scope.launch {
            preferences.defaultMode.collectLatest { mode ->
                policy.updateConfiguration(policy.configuration.value.copy(defaultMode = mode))
            }
        }
    }
}