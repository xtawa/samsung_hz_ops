package com.xtawa.samsunghzops.data.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceIdleConstantsTest {
    @Test
    fun mergePreservesUnknownKeysAndOriginalOrder() {
        val existing = "inactive_to=300000,oem_secret=keep,sensing_to=1"
        val merged = DeviceIdleConstants.merge(
            existing,
            mapOf(
                "sensing_to" to "0",
                "motion_inactive_to" to "60000",
            ),
        )
        assertEquals(
            "inactive_to=300000,oem_secret=keep,sensing_to=0,motion_inactive_to=60000",
            merged,
        )
    }

    @Test
    fun parseKeepsBareTokensWithoutDroppingThem() {
        val parsed = DeviceIdleConstants.parse("foo,bar=1")
        assertEquals("", parsed["foo"])
        assertEquals("1", parsed["bar"])
    }

    @Test
    fun removeKeysLeavesRemainingMapIntact() {
        val remaining = DeviceIdleConstants.removeKeys(
            "inactive_to=60000,oem_secret=keep,sensing_to=0",
            setOf("inactive_to", "sensing_to"),
        )
        assertEquals("oem_secret=keep", remaining)
    }

    @Test
    fun removeAllKeysReturnsNullInsteadOfEmptyString() {
        assertNull(DeviceIdleConstants.removeKeys("a=1", setOf("a")))
    }
}
