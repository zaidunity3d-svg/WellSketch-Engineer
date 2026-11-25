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

class UnitFormatterFieldMetricTest {
    private val f = UnitFormatter(com.wellsketch.engineer.core.model.units.UnitSystem.FIELD_METRIC)

    @Test
    fun fieldMetricUsesMetresInchesPsiAndBarrels() {
        assertEquals("1000.0 m", f.depth(Length.meters(1000.0)))
        assertEquals("7.000 in", f.diameter(Length.inches(7.0)))
        assertEquals("5000 psi", f.pressure(com.wellsketch.engineer.core.model.units.Pressure.psi(5000.0)))
        assertEquals("1.00 bbl", f.volume(com.wellsketch.engineer.core.model.units.Volume.barrels(1.0)))
        assertEquals("1.100 SG", f.density(com.wellsketch.engineer.core.model.units.Density.kilogramsPerCubicMeter(1100.0)))
        assertEquals(1100.0, f.densityFromValue(1.1).kilogramsPerCubicMeter, 1e-9)
        assertEquals("1.50 bbl/min", f.flowRate(com.wellsketch.engineer.core.model.units.FlowRate.barrelsPerMinute(1.5)))
    }

    @Test
    fun capacityAndGradientPerUnitSystem() {
        val cap = com.wellsketch.engineer.core.model.units.VolumePerLength.barrelsPerFoot(0.0373416)
        assertEquals("0.03734 bbl/ft", UnitFormatter(com.wellsketch.engineer.core.model.units.UnitSystem.OILFIELD).capacity(cap))
        assertEquals("0.12251 bbl/m", f.capacity(cap))
        assertEquals("19.478 L/m", UnitFormatter(com.wellsketch.engineer.core.model.units.UnitSystem.METRIC).capacity(cap))
        val g = com.wellsketch.engineer.core.model.units.PressureGradient.psiPerFoot(0.4330)
        assertEquals("0.4330 psi/ft", UnitFormatter(com.wellsketch.engineer.core.model.units.UnitSystem.OILFIELD).gradient(g))
        assertEquals("1.4206 psi/m", f.gradient(g))
    }

    @Test
    fun ppgRoundTripsInOilfield() {
        val o = UnitFormatter(com.wellsketch.engineer.core.model.units.UnitSystem.OILFIELD)
        assertEquals(9.5, o.densityValue(o.densityFromValue(9.5)), 1e-12)
        assertEquals(null, UnitFormatter.parseNumber("x"))
        assertEquals(1.25, UnitFormatter.parseNumber(" 1,25 "))
    }
}

class NominalSizeTest {
    private val o = UnitFormatter(com.wellsketch.engineer.core.model.units.UnitSystem.OILFIELD)

    @Test
    fun fractionalInchSizes() {
        assertEquals("13-3/8 in", o.nominalSize(Length.inches(13.375)))
        assertEquals("9-5/8 in", o.nominalSize(Length.inches(9.625)))
        assertEquals("7 in", o.nominalSize(Length.inches(7.0)))
        assertEquals("20 in", o.nominalSize(Length.inches(20.0)))
        assertEquals("2-7/8 in", o.nominalSize(Length.inches(2.875)))
        assertEquals("4-1/2 in", o.nominalSize(Length.inches(4.5)))
        assertEquals("1-3/4 in", o.nominalSize(Length.inches(1.75)))
        // not a 1/16 multiple: decimal fallback
        assertEquals("6.184 in", o.nominalSize(Length.inches(6.184)))
        assertEquals("177.8 mm", UnitFormatter(com.wellsketch.engineer.core.model.units.UnitSystem.METRIC).nominalSize(Length.inches(7.0)))
    }
}
