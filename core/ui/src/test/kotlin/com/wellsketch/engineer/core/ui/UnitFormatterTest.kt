package com.wellsketch.engineer.core.ui

import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.ui.format.UnitFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class UnitFormatterTest {
    @Test
    fun oilfieldDepthRoundTrips() {
        val f = UnitFormatter(UnitSystem.OILFIELD)
        assertEquals("1000.0 ft", f.depth(Length.feet(1000.0)))
        assertEquals(304.8, f.parseDepth("1000")!!.meters, 1e-9)
        assertEquals(304.8, f.parseDepth("1000,0")!!.meters, 1e-9)
        assertEquals(null, f.parseDepth("abc"))
    }

    @Test
    fun metricDiameterInMillimetres() {
        assertEquals("177.8 mm", UnitFormatter(UnitSystem.METRIC).diameter(Length.inches(7.0)))
    }
}
