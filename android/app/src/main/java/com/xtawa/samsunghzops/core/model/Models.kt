package com.xtawa.samsunghzops.core.model

import java.time.Instant

/** The four user-facing operating modes documented by Samsung Hz Ops. */
enum class RefreshMode {
    STANDARD,
    ADAPTIVE,
    MAXIMUM,

    val label: String
        get() = when (this) {
            STANDARD -> "标准"
            ADAPTIVE -> "自适应"
            MAXIMUM -> "最高"
        }
}

enum class SettingNamespace {
    SYSTEM,
    SECURE,
    GLOBAL,
    SYSFS,
}

enum class Capability {
    READ_DISPLAY,
    WRITE_SYSTEM_SETTINGS,
    WRITE_SECURE_SETTINGS,
    WRITE_GLOBAL_SETTINGS,
    SHIZUKU,
    ACCESSIBILITY,
    OVERLAY,
    NOTIFICATIONS,
    IGNORE_BATTERY_OPTIMIZATIONS,
    BATTERY_MANAGER,
}

enum class CapabilityState {
    GRANTED,
    USER_ACTION_REQUIRED,
    UNAVAILABLE,
    NOT_SUPPORTED,
}

data class CapabilityStatus(
    val capability: Capability,
    val state: CapabilityState,
    val explanation: String,
    val actionLabel: String? = null,
)

data class DisplayModeInfo(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRateHz: Float,
    val alternativeRefreshRatesHz: List<Float> = emptyList(),
) {
    val label: String get() = "${refreshRateHz.toInt()} Hz"
}

data class RefreshRange(
    val minHz: Float,
    val maxHz: Float,
) {
    init {
        require(minHz <= maxHz) { "minHz must not exceed maxHz" }
    }
}

data class RefreshSnapshot(
    val activeMode: RefreshMode = RefreshMode.STANDARD,
    val activeRefreshRateHz: Float? = null,
    val supportedModes: List<DisplayModeInfo> = emptyList(),
    val range: RefreshRange? = null,
    val isPowerSaveMode: Boolean = false,
    val lastAppliedAt: Instant? = null,
    val lastError: String? = null,
)

data class DeviceState(
    val foregroundPackage: String? = null,
    val isInteractive: Boolean = true,
    val isKeyguardShowing: Boolean = false,
    val isCameraActive: Boolean = false,
    val isCasting: Boolean = false,
    val brightnessFraction: Float? = null,
    val isFolded: Boolean = false,
    val thermalStatus: Int? = null,
    val eventTimestamp: Instant = Instant.now(),
)

data class RefreshPolicyDecision(
    val mode: RefreshMode,
    val range: RefreshRange?,
    val reason: String,
    val confidence: String = "confirmed",
)

data class SettingSpec(
    val namespace: SettingNamespace,
    val key: String,
    val description: String,
    val requiresPrivilegedWrite: Boolean,
)

data class SettingMutation(
    val spec: SettingSpec,
    val value: String?,
    val previousValue: String? = null,
)

data class TransactionRecord(
    val id: String,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val operation: String,
    val mutations: List<SettingMutation> = emptyList(),
    val committed: Boolean = false,
    val rollbackAttempted: Boolean = false,
    val error: String? = null,
)

sealed interface OperationResult<out T> {
    data class Success<T>(
        val value: T,
        val warnings: List<String> = emptyList(),
    ) : OperationResult<T>

    data class Failure(
        val message: String,
        val recoverable: Boolean = true,
        val missingCapabilities: List<Capability> = emptyList(),
    ) : OperationResult<Nothing>
}

data class FeatureState(
    val id: String,
    val title: String,
    val summary: String,
    val enabled: Boolean,
    val available: Boolean = true,
    val statusLabel: String? = null,
)

data class AppProfile(
    val packageName: String,
    val appLabel: String,
    val enabled: Boolean = true,
    val preferredMode: RefreshMode = RefreshMode.ADAPTIVE,
    val minHz: Float? = null,
    val maxHz: Float? = null,
    val pauseWhenScreenOff: Boolean = true,
    val pauseWhenKeyguard: Boolean = true,
    val updatedAt: Instant = Instant.now(),
)
