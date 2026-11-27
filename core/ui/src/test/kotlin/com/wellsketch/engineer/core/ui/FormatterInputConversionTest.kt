package com.wellsketch.engineer.core.ui

import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.PressureGradient
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.model.units.Volume
import com.wellsketch.engineer.core.model.units.VolumePerLength
import com.wellsketch.engineer.core.ui.format.UnitFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

/** Editable fields read a value in display units and write it back unchanged. */
class FormatterInputConversionTest {
    @Test
    fun valuesRoundTripInEveryUnitSystem() {
        val d = Length.inches(4.75)
        val g = PressureGradient.psiPerFoot(0.8)
        val v = Volume.cubicMeters(1.58987294928)
        val c = VolumePerLength.cubicMetersPerMeter(0.0185)
        for (system in UnitSystem.entries) {
            val f = UnitFormatter(system)
            assertEquals(d.meters, f.diameterFromValue(f.diameterValue(d)).meters, 1e-12, "$system diameter")
            assertEquals(g.pascalsPerMeter, f.gradientFromValue(f.gradientValue(g)).pascalsPerMeter, 1e-9, "$system gradient")
            assertEquals(v.cubicMeters, f.volumeFromValue(f.volumeValue(v)).cubicMeters, 1e-12, "$system volume")
            assertEquals(c.cubicMetersPerMeter, f.capacityFromValue(f.capacityValue(c)).cubicMetersPerMeter, 1e-12, "$system capacity")
        }
        // oilfield shows 10 bbl and 0.8 psi/ft
        val oil = UnitFormatter(UnitSystem.OILFIELD)
        assertEquals(10.0, oil.volumeValue(v), 1e-9)
        assertEquals(0.8, oil.gradientValue(g), 1e-12)
    }
}
