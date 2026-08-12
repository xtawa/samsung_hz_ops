package com.xtawa.samsunghzops.policy

import com.xtawa.samsunghzops.core.model.AppProfile
import com.xtawa.samsunghzops.core.model.DeviceState
import com.xtawa.samsunghzops.core.model.DisplayModeInfo
import com.xtawa.samsunghzops.core.model.RefreshMode
import org.junit.Assert.assertEquals
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
        assertTrue(result.reason.contains("锁屏"))
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
        assertEquals(120f, result.range?.minHz)
    }
}
