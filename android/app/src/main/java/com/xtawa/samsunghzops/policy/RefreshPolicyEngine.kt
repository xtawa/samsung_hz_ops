package com.xtawa.samsunghzops.policy

import com.xtawa.samsunghzops.core.model.AppProfile
import com.xtawa.samsunghzops.core.model.DeviceState
import com.xtawa.samsunghzops.core.model.DisplayModeInfo
import com.xtawa.samsunghzops.core.model.DisplayTarget
import com.xtawa.samsunghzops.core.model.RefreshMode
import com.xtawa.samsunghzops.core.model.RefreshPolicyDecision
import com.xtawa.samsunghzops.core.model.RefreshRange
import com.xtawa.samsunghzops.core.model.RefreshReason
import com.xtawa.samsunghzops.core.model.SystemProfile
import com.xtawa.samsunghzops.core.model.nearestSupportedHz
import com.xtawa.samsunghzops.core.model.supportedHzValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PolicyConfiguration(
    val defaultMode: RefreshMode = RefreshMode.ADAPTIVE,
    val standardTargetHz: Float = 60f,
    val adaptiveMinHz: Float? = null,
    val adaptiveMaxHz: Float? = null,
    val masterEnabled: Boolean = true,
    val pauseOnKeyguard: Boolean = true,
    val pauseWhenScreenOff: Boolean = true,
    val cameraUsesSafeRange: Boolean = true,
    val castUsesSafeRange: Boolean = true,
    val psmHighRefreshEnabled: Boolean = false,
    val lowBatteryThreshold: Int = 15,
    val lowBatteryResumeThreshold: Int = 18,
    val ignoreLowBatteryWhenCharging: Boolean = true,
    val foldAutoSwitch: Boolean = true,
    val normalProfile: SystemProfile? = null,
    val psmProfile: SystemProfile? = null,
    val lowBatteryProfile: SystemProfile? = null,
    val aodProfile: SystemProfile? = null,
    val coverProfile: SystemProfile? = null,
)

/**
 * One policy engine consumes all signals. Accessibility, camera, cast, fold,
 * brightness and keyguard observers should only update DeviceState; they do
 * not write settings themselves.
 *
 * Priority matches the product spec:
 * 100 thermal, 90 master/emergency, 80 camera/cast, 70 screen-off/AOD,
 * 60 PSM/low battery, 50 fold, 40 per-app, 20 normal.
 */
class RefreshPolicyEngine(
    initialSupportedModes: List<DisplayModeInfo> = emptyList(),
    initialConfiguration: PolicyConfiguration = PolicyConfiguration(),
) {
    private val _supportedModes = MutableStateFlow(normalizeModes(initialSupportedModes))
    private val _configuration = MutableStateFlow(initialConfiguration)
    private val _deviceState = MutableStateFlow(DeviceState())
    private val _profiles = MutableStateFlow<List<AppProfile>>(emptyList())
    private val _decision = MutableStateFlow(
        decide(initialConfiguration, initialSupportedModes, DeviceState(), emptyList()),
    )

    val supportedModes: StateFlow<List<DisplayModeInfo>> = _supportedModes.asStateFlow()
    val configuration: StateFlow<PolicyConfiguration> = _configuration.asStateFlow()
    val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()
    val decision: StateFlow<RefreshPolicyDecision> = _decision.asStateFlow()

    fun updateSupportedModes(modes: List<DisplayModeInfo>) {
        _supportedModes.value = normalizeModes(modes)
        recompute()
    }

    fun updateConfiguration(configuration: PolicyConfiguration) {
        _configuration.value = configuration
        recompute()
    }

    fun updateDeviceState(state: DeviceState) {
        _deviceState.value = state
        recompute()
    }

    fun updateProfiles(profiles: List<AppProfile>) {
        _profiles.value = profiles
        recompute()
    }

    private fun recompute() {
        _decision.value = decide(
            _configuration.value,
            _supportedModes.value,
            _deviceState.value,
            _profiles.value,
        )
    }

    companion object {
        fun decide(
            configuration: PolicyConfiguration,
            supportedModes: List<DisplayModeInfo>,
            deviceState: DeviceState,
            profiles: List<AppProfile>,
        ): RefreshPolicyDecision {
            val modes = normalizeModes(supportedModes)
            val hzValues = supportedHzValues(modes)
            val display = resolveDisplay(configuration, deviceState)
            if (hzValues.isEmpty()) {
                return RefreshPolicyDecision(
                    mode = configuration.defaultMode,
                    range = null,
                    reason = "等待系统报告支持的刷新率",
                    reasonCode = RefreshReason.WAITING,
                    display = display,
                    shouldApply = false,
                )
            }

            if (deviceState.thermalRestricted) {
                return standardDecision(
                    hzValues,
                    configuration.standardTargetHz,
                    "系统当前限制为 ${nearestSupportedHz(configuration.standardTargetHz, hzValues).toInt()} Hz",
                    RefreshReason.THERMAL,
                    display,
                    shouldApply = false,
                )
            }

            if (!configuration.masterEnabled) {
                return standardDecision(
                    hzValues,
                    configuration.standardTargetHz,
                    "主开关已关闭，已停止自动化",
                    RefreshReason.EMERGENCY_OR_MASTER_OFF,
                    display,
                    shouldApply = false,
                )
            }

            if ((configuration.cameraUsesSafeRange && deviceState.isCameraActive) ||
                (configuration.castUsesSafeRange && deviceState.isCasting) ||
                display == DisplayTarget.EXTERNAL
            ) {
                val reason = when {
                    deviceState.isCameraActive -> "相机活跃，使用兼容安全范围"
                    deviceState.isCasting -> "投屏活跃，使用兼容安全范围"
                    else -> "外接显示，使用兼容安全范围"
                }
                return standardDecision(
                    hzValues,
                    configuration.standardTargetHz,
                    reason,
                    RefreshReason.CAMERA_CAST_EXTERNAL,
                    display,
                )
            }

            if (configuration.pauseWhenScreenOff && !deviceState.isInteractive) {
                val profile = configuration.aodProfile?.takeIf { it.enabled }
                return decisionForProfile(
                    profile = profile,
                    fallbackMode = RefreshMode.STANDARD,
                    hzValues = hzValues,
                    standardTargetHz = configuration.standardTargetHz,
                    reason = if (deviceState.isAod) "息屏显示 Profile" else "屏幕已熄灭",
                    reasonCode = RefreshReason.SCREEN_OFF_AOD,
                    display = display,
                )
            }

            if (configuration.pauseOnKeyguard && deviceState.isKeyguardShowing) {
                return standardDecision(
                    hzValues,
                    configuration.standardTargetHz,
                    "锁屏状态",
                    RefreshReason.SCREEN_OFF_AOD,
                    display,
                )
            }

            val lowBattery = isLowBatteryActive(configuration, deviceState)
            if (deviceState.isPowerSaveMode && configuration.psmProfile?.enabled == true) {
                return decisionForProfile(
                    profile = configuration.psmProfile,
                    fallbackMode = if (configuration.psmHighRefreshEnabled) {
                        configuration.defaultMode
                    } else {
                        RefreshMode.STANDARD
                    },
                    hzValues = hzValues,
                    standardTargetHz = configuration.standardTargetHz,
                    reason = "省电模式 Profile",
                    reasonCode = RefreshReason.POWER_SAVE_OR_LOW_BATTERY,
                    display = display,
                )
            }
            if (deviceState.isPowerSaveMode && !configuration.psmHighRefreshEnabled) {
                return standardDecision(
                    hzValues,
                    configuration.standardTargetHz,
                    "省电模式未启用高刷",
                    RefreshReason.POWER_SAVE_OR_LOW_BATTERY,
                    display,
                )
            }
            if (lowBattery && configuration.lowBatteryProfile?.enabled == true) {
                return decisionForProfile(
                    profile = configuration.lowBatteryProfile,
                    fallbackMode = RefreshMode.STANDARD,
                    hzValues = hzValues,
                    standardTargetHz = configuration.standardTargetHz,
                    reason = "低电量 Profile",
                    reasonCode = RefreshReason.POWER_SAVE_OR_LOW_BATTERY,
                    display = display,
                )
            }

            if (display == DisplayTarget.COVER && configuration.coverProfile?.enabled == true) {
                return decisionForProfile(
                    profile = configuration.coverProfile,
                    fallbackMode = configuration.defaultMode,
                    hzValues = hzValues,
                    standardTargetHz = configuration.standardTargetHz,
                    reason = "折叠外屏 Profile",
                    reasonCode = RefreshReason.FOLD,
                    display = display,
                )
            }

            val profile = profiles.firstOrNull {
                it.enabled && it.packageName == deviceState.foregroundPackage
            }
            if (profile != null) {
                return decisionForMode(
                    mode = profile.preferredMode,
                    hzValues = hzValues,
                    minOverride = profile.minHz,
                    maxOverride = profile.maxHz,
                    standardTargetHz = configuration.standardTargetHz,
                    reason = "应用规则：${profile.appLabel}",
                    reasonCode = RefreshReason.PER_APP,
                    display = display,
                )
            }

            val normal = configuration.normalProfile?.takeIf { it.enabled }
            return decisionForProfile(
                profile = normal,
                fallbackMode = configuration.defaultMode,
                hzValues = hzValues,
                standardTargetHz = configuration.standardTargetHz,
                minOverride = configuration.adaptiveMinHz,
                maxOverride = configuration.adaptiveMaxHz,
                reason = when (normal?.mode ?: configuration.defaultMode) {
                    RefreshMode.STANDARD -> "默认标准模式"
                    RefreshMode.ADAPTIVE -> "默认自适应模式"
                    RefreshMode.MAXIMUM -> "默认最高刷新率"
                },
                reasonCode = RefreshReason.NORMAL,
                display = display,
            )
        }

        fun isLowBatteryActive(
            configuration: PolicyConfiguration,
            deviceState: DeviceState,
        ): Boolean {
            val percent = deviceState.batteryPercent ?: return deviceState.lowBatteryActive
            if (configuration.ignoreLowBatteryWhenCharging && deviceState.isCharging) return false
            val trigger = configuration.lowBatteryThreshold.coerceIn(5, 50)
            val resume = (trigger + (configuration.lowBatteryResumeThreshold - configuration.lowBatteryThreshold))
                .coerceAtLeast(trigger + 1)
            return when {
                percent <= trigger -> true
                percent >= resume -> false
                else -> deviceState.lowBatteryActive
            }
        }

        private fun resolveDisplay(
            configuration: PolicyConfiguration,
            deviceState: DeviceState,
        ): DisplayTarget = when {
            !configuration.foldAutoSwitch -> deviceState.displayTarget
            deviceState.displayTarget == DisplayTarget.EXTERNAL -> DisplayTarget.EXTERNAL
            deviceState.isFolded -> DisplayTarget.COVER
            else -> DisplayTarget.MAIN
        }

        private fun decisionForProfile(
            profile: SystemProfile?,
            fallbackMode: RefreshMode,
            hzValues: List<Float>,
            standardTargetHz: Float,
            reason: String,
            reasonCode: RefreshReason,
            display: DisplayTarget,
            minOverride: Float? = null,
            maxOverride: Float? = null,
        ): RefreshPolicyDecision = decisionForMode(
            mode = profile?.mode ?: fallbackMode,
            hzValues = hzValues,
            minOverride = profile?.minHz ?: minOverride,
            maxOverride = profile?.maxHz ?: maxOverride,
            standardTargetHz = standardTargetHz,
            reason = reason,
            reasonCode = reasonCode,
            display = display,
        )

        private fun decisionForMode(
            mode: RefreshMode,
            hzValues: List<Float>,
            minOverride: Float?,
            maxOverride: Float?,
            standardTargetHz: Float,
            reason: String,
            reasonCode: RefreshReason,
            display: DisplayTarget,
        ): RefreshPolicyDecision = when (mode) {
            RefreshMode.STANDARD -> standardDecision(
                hzValues,
                standardTargetHz,
                reason,
                reasonCode,
                display,
            )
            RefreshMode.MAXIMUM -> maximumDecision(hzValues, reason, reasonCode, display)
            RefreshMode.ADAPTIVE -> {
                val min = nearestAtOrAbove(hzValues, minOverride ?: hzValues.first())
                val max = nearestAtOrBelow(hzValues, maxOverride ?: hzValues.last())
                RefreshPolicyDecision(
                    mode = RefreshMode.ADAPTIVE,
                    range = RefreshRange(minHz = min, maxHz = max.coerceAtLeast(min)),
                    reason = reason,
                    reasonCode = reasonCode,
                    display = display,
                )
            }
        }

        private fun standardDecision(
            hzValues: List<Float>,
            target: Float,
            reason: String,
            reasonCode: RefreshReason,
            display: DisplayTarget,
            shouldApply: Boolean = true,
        ): RefreshPolicyDecision {
            val standard = nearestSupportedHz(target, hzValues)
            return RefreshPolicyDecision(
                mode = RefreshMode.STANDARD,
                range = RefreshRange(standard, standard),
                reason = reason,
                reasonCode = reasonCode,
                display = display,
                shouldApply = shouldApply,
            )
        }

        private fun maximumDecision(
            hzValues: List<Float>,
            reason: String,
            reasonCode: RefreshReason,
            display: DisplayTarget,
        ) = RefreshPolicyDecision(
            mode = RefreshMode.MAXIMUM,
            range = RefreshRange(hzValues.last(), hzValues.last()),
            reason = reason,
            reasonCode = reasonCode,
            display = display,
        )

        private fun nearestAtOrAbove(values: List<Float>, target: Float): Float =
            values.firstOrNull { it >= target } ?: values.last()

        private fun nearestAtOrBelow(values: List<Float>, target: Float): Float =
            values.lastOrNull { it <= target } ?: values.first()

        private fun normalizeModes(modes: List<DisplayModeInfo>): List<DisplayModeInfo> =
            modes.filter { it.refreshRateHz.isFinite() && it.refreshRateHz > 0f }
                .sortedWith(compareBy<DisplayModeInfo> { it.refreshRateHz }.thenBy { it.id })
    }
}
