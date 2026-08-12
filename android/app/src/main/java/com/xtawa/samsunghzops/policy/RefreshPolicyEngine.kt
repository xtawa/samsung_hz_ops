package com.xtawa.samsunghzops.policy

import com.xtawa.samsunghzops.core.model.AppProfile
import com.xtawa.samsunghzops.core.model.DeviceState
import com.xtawa.samsunghzops.core.model.DisplayModeInfo
import com.xtawa.samsunghzops.core.model.RefreshMode
import com.xtawa.samsunghzops.core.model.RefreshPolicyDecision
import com.xtawa.samsunghzops.core.model.RefreshRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PolicyConfiguration(
    val defaultMode: RefreshMode = RefreshMode.ADAPTIVE,
    val standardTargetHz: Float = 60f,
    val adaptiveMinHz: Float? = null,
    val adaptiveMaxHz: Float? = null,
    val pauseOnKeyguard: Boolean = true,
    val pauseWhenScreenOff: Boolean = true,
    val cameraForcesMaximum: Boolean = true,
    val castForcesMaximum: Boolean = true,
)

/**
 * One policy engine consumes all signals. Accessibility, camera, cast, fold,
 * brightness and keyguard observers should only update DeviceState; they do
 * not write settings themselves.
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
        val normalized = normalizeModes(modes)
        _supportedModes.value = normalized
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
            val hzValues = modes.map { it.refreshRateHz }.distinct().sorted()
            if (hzValues.isEmpty()) {
                return RefreshPolicyDecision(
                    mode = configuration.defaultMode,
                    range = null,
                    reason = "等待系统报告支持的刷新率",
                    confidence = "confirmed",
                )
            }

            if (configuration.pauseWhenScreenOff && !deviceState.isInteractive) {
                return standardDecision(hzValues, configuration.standardTargetHz, "屏幕已熄灭")
            }
            if (configuration.pauseOnKeyguard && deviceState.isKeyguardShowing) {
                return standardDecision(hzValues, configuration.standardTargetHz, "锁屏状态")
            }
            if (configuration.cameraForcesMaximum && deviceState.isCameraActive) {
                return maximumDecision(hzValues, "相机活跃")
            }
            if (configuration.castForcesMaximum && deviceState.isCasting) {
                return maximumDecision(hzValues, "投屏活跃")
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
                )
            }

            return decisionForMode(
                mode = configuration.defaultMode,
                hzValues = hzValues,
                minOverride = configuration.adaptiveMinHz,
                maxOverride = configuration.adaptiveMaxHz,
                standardTargetHz = configuration.standardTargetHz,
                reason = when (configuration.defaultMode) {
                    RefreshMode.STANDARD -> "默认标准模式"
                    RefreshMode.ADAPTIVE -> "默认自适应模式"
                    RefreshMode.MAXIMUM -> "默认最高刷新率"
                },
            )
        }

        private fun decisionForMode(
            mode: RefreshMode,
            hzValues: List<Float>,
            minOverride: Float?,
            maxOverride: Float?,
            standardTargetHz: Float,
            reason: String,
        ): RefreshPolicyDecision = when (mode) {
            RefreshMode.STANDARD -> standardDecision(hzValues, standardTargetHz, reason)
            RefreshMode.MAXIMUM -> maximumDecision(hzValues, reason)
            RefreshMode.ADAPTIVE -> {
                val min = nearestAtOrAbove(hzValues, minOverride ?: hzValues.first())
                val max = nearestAtOrBelow(hzValues, maxOverride ?: hzValues.last())
                RefreshPolicyDecision(
                    mode = RefreshMode.ADAPTIVE,
                    range = RefreshRange(minHz = min, maxHz = max.coerceAtLeast(min)),
                    reason = reason,
                )
            }
        }

        private fun standardDecision(
            hzValues: List<Float>,
            target: Float,
            reason: String,
        ): RefreshPolicyDecision {
            val standard = hzValues.minBy { kotlin.math.abs(it - target) }
            return RefreshPolicyDecision(
                mode = RefreshMode.STANDARD,
                range = RefreshRange(standard, standard),
                reason = reason,
            )
        }

        private fun maximumDecision(hzValues: List<Float>, reason: String) =
            RefreshPolicyDecision(
                mode = RefreshMode.MAXIMUM,
                range = RefreshRange(hzValues.last(), hzValues.last()),
                reason = reason,
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
