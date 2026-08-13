package com.xtawa.samsunghzops.data.refresh

import com.xtawa.samsunghzops.core.model.RefreshMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SamsungModeMappingTest {
    @Test
    fun oneUiStandardAndAdaptiveMappingsAreApplied() {
        val mapping = SamsungModeMapping()

        assertEquals("0", mapping.valueFor(RefreshMode.STANDARD))
        assertEquals("1", mapping.valueFor(RefreshMode.ADAPTIVE))
        assertEquals("1", mapping.valueFor(RefreshMode.MAXIMUM))
    }

    @Test
    fun maximumReusesSamsungHighModeInsteadOfInventingAnOemMode() {
        val mapping = SamsungModeMapping(standard = "10", adaptive = "20", maximum = "20")

        assertEquals(mapping.valueFor(RefreshMode.ADAPTIVE), mapping.valueFor(RefreshMode.MAXIMUM))
    }
}
