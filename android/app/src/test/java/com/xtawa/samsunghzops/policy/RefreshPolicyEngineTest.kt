package com.xtawa.samsunghzops.policy

import com.xtawa.samsunghzops.core.model.AppProfile
import com.xtawa.samsunghzops.core.model.DeviceState
import com.xtawa.samsunghzops.core.model.DisplayModeInfo
import com.xtawa.samsunghzops.core.model.RefreshMode
import com.xtawa.samsunghzops.core.model.RefreshReason
import com.xtawa.samsunghzops.core.model.SystemProfile
import com.xtawa.samsunghzops.core.model.formatHz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyEngineTest {
    private val modes = listOf(
        DisplayModeInfo(1, 1080, 2400, 60f),
        DisplayModeInfo(2, 1080, 2400, 120f),
    )

    @Test
    fun standardChoosesNearestSupportedRate() {
        val result = RefreshPolicyEngine.decide(
            PolicyConfiguration(defaultMode = RefreshMode.STANDARD),
            modes,
            DeviceState(),
            emptyList(),
        )
        assertEquals(RefreshMode.STANDARD, result.mode)
        assertEquals(RefreshReason.NORMAL, result.reasonCode)
        assertEquals(60f, result.range?.minHz)
        assertEquals(60f, result.range?.maxHz)
    }

    @Test
    fun adaptiveUsesTheFullReportedRange() {
        val result = RefreshPolicyEngine.decide(
            PolicyConfiguration(defaultMode = RefreshMode.ADAPTIVE),
            modes,
            DeviceState(),
            emptyList(),
        )
        assertEquals(60f, result.range?.minHz)
        assertEquals(120f, result.range?.maxHz)
        assertEquals(RefreshReason.NORMAL, result.reasonCode)
    }

    @Test
    fun keyguardFallsBackToStandard() {
        val result = RefreshPolicyEngine.decide(
            PolicyConfiguration(defaultMode = RefreshMode.MAXIMUM),
            modes,
            DeviceState(isKeyguardShowing = true),
            emptyList(),
        )
        assertEquals(RefreshMode.STANDARD, result.mode)
        assertEquals(RefreshReason.SCREEN_OFF_AOD, result.reasonCode)
        assertTrue(result.reason.contains("锁屏"))
    }

    @Test
    fun thermalDoesNotTryToOverrideSystemLimit() {
        val result = RefreshPolicyEngine.decide(
            PolicyConfiguration(defaultMode = RefreshMode.MAXIMUM),
            modes,
            DeviceState(thermalRestricted = true),
            emptyList(),
        )
        assertEquals(RefreshReason.THERMAL, result.reasonCode)
        assertFalse(result.shouldApply)
        assertEquals(RefreshMode.STANDARD, result.mode)
    }

    @Test
    fun masterOffStopsAutomation() {
        val result = RefreshPolicyEngine.decide(
            PolicyConfiguration(defaultMode = RefreshMode.MAXIMUM, masterEnabled = false),
            modes,
            DeviceState(),
            emptyList(),
        )
        assertEquals(RefreshReason.EMERGENCY_OR_MASTER_OFF, result.reasonCode)
        assertFalse(result.shouldApply)
    }

    @Test
    fun cameraUsesSafeRangeInsteadOfMaximum() {
        val result = RefreshPolicyEngine.decide(
            PolicyConfiguration(defaultMode = RefreshMode.MAXIMUM),
            modes,
            DeviceState(isCameraActive = true),
            emptyList(),
        )
        assertEquals(RefreshReason.CAMERA_CAST_EXTERNAL, result.reasonCode)
        assertEquals(RefreshMode.STANDARD, result.mode)
        assertEquals(60f, result.range?.maxHz)
    }

    @Test
    fun psmProfileBeatsPerApp() {
        val result = RefreshPolicyEngine.decide(
            PolicyConfiguration(
                defaultMode = RefreshMode.MAXIMUM,
                psmHighRefreshEnabled = true,
                psmProfile = SystemProfile("psm", "省电模式", RefreshMode.ADAPTIVE, 60f, 120f, enabled = true),
            ),
            modes,
            DeviceState(isPowerSaveMode = true, foregroundPackage = "com.example.game"),
            listOf(
                AppProfile(
                    packageName = "com.example.game",
                    appLabel = "Game",
                    preferredMode = RefreshMode.MAXIMUM,
                ),
            ),
        )
        assertEquals(RefreshReason.POWER_SAVE_OR_LOW_BATTERY, result.reasonCode)
        assertEquals(RefreshMode.ADAPTIVE, result.mode)
        assertEquals(60f, result.range?.minHz)
        assertEquals(120f, result.range?.maxHz)
    }

    @Test
    fun appProfileOverridesDefaultMode() {
        val result = RefreshPolicyEngine.decide(
            PolicyConfiguration(defaultMode = RefreshMode.STANDARD),
            modes,
            DeviceState(foregroundPackage = "com.example.game"),
            listOf(
                AppProfile(
                    packageName = "com.example.game",
                    appLabel = "Game",
                    preferredMode = RefreshMode.MAXIMUM,
                ),
            ),
        )
        assertEquals(RefreshMode.MAXIMUM, result.mode)
        assertEquals(RefreshReason.PER_APP, result.reasonCode)
        assertEquals(120f, result.range?.minHz)
    }

    @Test
    fun lowBatteryUsesHysteresis() {
        val configuration = PolicyConfiguration(
            lowBatteryThreshold = 15,
            lowBatteryResumeThreshold = 18,
            lowBatteryProfile = SystemProfile("low_battery", "低电量", RefreshMode.STANDARD, enabled = true),
        )
        val entering = RefreshPolicyEngine.decide(
            configuration,
            modes,
            DeviceState(batteryPercent = 14, lowBatteryActive = false),
            emptyList(),
        )
        assertEquals(RefreshReason.POWER_SAVE_OR_LOW_BATTERY, entering.reasonCode)

        val holding = RefreshPolicyEngine.isLowBatteryActive(
            configuration,
            DeviceState(batteryPercent = 16, lowBatteryActive = true),
        )
        assertTrue(holding)

        val exiting = RefreshPolicyEngine.isLowBatteryActive(
            configuration,
            DeviceState(batteryPercent = 18, lowBatteryActive = true),
        )
        assertFalse(exiting)
    }

    @Test
    fun formatHzKeepsOneDecimalForNonIntegerRates() {
        assertEquals("120 Hz", formatHz(120f))
        assertEquals("59.9 Hz", formatHz(59.9f))
        assertEquals("— Hz", formatHz(null))
    }
}
