package com.xtawa.samsunghzops.core.model

import java.time.Instant

enum class RefreshMode {
    STANDARD,
    ADAPTIVE,
    MAXIMUM;

    val label: String
        get() = when (this) {
            STANDARD -> "标准"
            ADAPTIVE -> "自适应"
            MAXIMUM -> "最高"
        }
}

enum class DisplayTarget {
    MAIN,
    COVER,
    EXTERNAL,
}

enum class RefreshReason(val priority: Int) {
    THERMAL(100),
    EMERGENCY_OR_MASTER_OFF(90),
    CAMERA_CAST_EXTERNAL(80),
    SCREEN_OFF_AOD(70),
    POWER_SAVE_OR_LOW_BATTERY(60),
    FOLD(50),
    PER_APP(40),
    ADAPTIVE(30),
    NORMAL(20),
    WAITING(0),
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

    ;

    val label: String
        get() = when (this) {
            READ_DISPLAY -> "读取屏幕刷新率"
            WRITE_SYSTEM_SETTINGS -> "修改系统设置"
            WRITE_SECURE_SETTINGS -> "安全设置权限"
            WRITE_GLOBAL_SETTINGS -> "全局设置权限"
            SHIZUKU -> "Shizuku 授权"
            ACCESSIBILITY -> "辅助功能服务"
            OVERLAY -> "悬浮窗权限"
            NOTIFICATIONS -> "通知权限"
            IGNORE_BATTERY_OPTIMIZATIONS -> "忽略电池优化"
            BATTERY_MANAGER -> "电池状态读取"
        }
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
    val label: String get() = formatHz(refreshRateHz)
}

data class RefreshRange(
    val minHz: Float,
    val maxHz: Float,
) {
    init {
        require(minHz <= maxHz) { "minHz must not exceed maxHz" }
    }

    val label: String get() = "${formatHz(minHz)}–${formatHz(maxHz)}"
}

data class RefreshSnapshot(
    val activeMode: RefreshMode = RefreshMode.STANDARD,
    val activeRefreshRateHz: Float? = null,
    val supportedModes: List<DisplayModeInfo> = emptyList(),
    val range: RefreshRange? = null,
    val isPowerSaveMode: Boolean = false,
    val lastAppliedAt: Instant? = null,
    val lastError: String? = null,
    val applying: Boolean = false,
    val applyingLabel: String? = null,
)

data class ManagedSettingSnapshot(
    val spec: SettingSpec,
    val originalValue: String?,
    val capturedAt: Instant,
    val lastTouchedAt: Instant,
)

data class EmergencyResetReport(
    val restoredSettings: Int,
    val stoppedServices: List<String>,
    val warnings: List<String> = emptyList(),
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
    val thermalRestricted: Boolean = false,
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val isAod: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val lowBatteryActive: Boolean = false,
    val displayTarget: DisplayTarget = DisplayTarget.MAIN,
    val eventTimestamp: Instant = Instant.now(),
)

data class RefreshPolicyDecision(
    val mode: RefreshMode,
    val range: RefreshRange?,
    val reason: String,
    val reasonCode: RefreshReason = RefreshReason.NORMAL,
    val display: DisplayTarget = DisplayTarget.MAIN,
    val confidence: String = "confirmed",
    val shouldApply: Boolean = true,
)

data class SystemProfile(
    val id: String,
    val title: String,
    val mode: RefreshMode = RefreshMode.ADAPTIVE,
    val minHz: Float? = null,
    val maxHz: Float? = null,
    val enabled: Boolean = true,
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

fun formatHz(value: Float?): String {
    if (value == null || !value.isFinite()) return "— Hz"
    val rounded = kotlin.math.round(value)
    return if (kotlin.math.abs(value - rounded) < 0.05f) {
        "${rounded.toInt()} Hz"
    } else {
        String.format(java.util.Locale.US, "%.1f Hz", value)
    }
}

fun nearestSupportedHz(value: Float, supportedHz: List<Float>): Float =
    supportedHz.minByOrNull { kotlin.math.abs(it - value) } ?: value

fun supportedHzValues(modes: List<DisplayModeInfo>): List<Float> =
    modes.map { it.refreshRateHz }
        .filter { it.isFinite() && it > 0f }
        .distinct()
        .sorted()
