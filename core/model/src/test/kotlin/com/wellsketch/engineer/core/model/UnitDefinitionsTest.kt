package com.wellsketch.engineer.core.model

import com.wellsketch.engineer.core.model.units.Density
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.LinearMass
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.model.units.UnitDefinitions
import com.wellsketch.engineer.core.model.units.Volume
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies the exact definitional conversion factors (no engineering data involved). */
class UnitDefinitionsTest {

    @Test
    fun footAndInchAreExactInternationalDefinitions() {
        assertEquals(0.3048, Length.feet(1.0).meters, 0.0)
        assertEquals(0.0254, Length.inches(1.0).meters, 0.0)
        assertEquals(12.0, Length.feet(1.0).inches, 1e-12)
        assertEquals(1000.0, Length.feet(1000.0).feet, 1e-9)
    }

    @Test
    fun psiMatchesDefinitionFromPoundForceAndSquareInch() {
        // 1 psi = 0.45359237 kg × 9.80665 m/s² / (0.0254 m)² = 6894.757293168361 Pa
        assertEquals(6894.757293168361, UnitDefinitions.PASCALS_PER_PSI, 1e-9)
        assertEquals(5000.0, Pressure.psi(5000.0).psi, 1e-9)
        assertEquals(1.0, Pressure.bar(1.0).bar, 1e-12)
    }

    @Test
    fun barrelIsFortyTwoUsGallonsOf231CubicInches() {
        assertEquals(3.785411784e-3, UnitDefinitions.CUBIC_METERS_PER_US_GALLON, 1e-15)
        assertEquals(0.158987294928, UnitDefinitions.CUBIC_METERS_PER_US_BARREL, 1e-12)
        assertEquals(1.0, Volume.barrels(1.0).barrels, 1e-12)
    }

    @Test
    fun linearMassAndDensityConversionsRoundTrip() {
        assertEquals(1.4881639435695537, UnitDefinitions.KG_PER_M_PER_LB_PER_FT, 1e-12)
        assertEquals(10.0, LinearMass.poundsPerFoot(10.0).poundsPerFoot, 1e-12)
        assertEquals(119.82642731689663, UnitDefinitions.KG_PER_M3_PER_PPG, 1e-9)
        assertEquals(9.5, Density.poundsPerGallon(9.5).poundsPerGallon, 1e-12)
    }
}
