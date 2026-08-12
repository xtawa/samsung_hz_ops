package com.xtawa.samsunghzops.data.settings

import com.xtawa.samsunghzops.core.model.SettingNamespace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFieldRegistryTest {
    @Test
    fun coreRateKeysUseTheExpectedNamespaces() {
        assertEquals(SettingNamespace.SYSTEM, SettingsFieldRegistry.minRefreshRate.namespace)
        assertEquals(SettingNamespace.SYSTEM, SettingsFieldRegistry.peakRefreshRate.namespace)
        assertEquals(SettingNamespace.SYSTEM, SettingsFieldRegistry.userRefreshRate.namespace)
        assertEquals(SettingNamespace.SECURE, SettingsFieldRegistry.refreshRateMode.namespace)
    }

    @Test
    fun coverPsmKeysAreExplicitlyNamed() {
        assertEquals("pms_settings_refresh_rate_cover_enabled", SettingsFieldRegistry.psmRefreshRateEnabledCover.key)
        assertEquals("psm_refresh_rate_cover_tag", SettingsFieldRegistry.psmRefreshRateTagCover.key)
        assertTrue(SettingsFieldRegistry.all.contains(SettingsFieldRegistry.psmRefreshRateEnabledCover))
    }
}
