package com.wellsketch.engineer.core.engineering

import com.wellsketch.engineer.core.engineering.volumes.TubularGeometry
import com.wellsketch.engineer.core.model.units.Density
import com.wellsketch.engineer.core.model.units.FlowRate
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.PressureGradient
import com.wellsketch.engineer.core.model.units.UnitDefinitions
import com.wellsketch.engineer.core.model.units.VolumePerLength
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Expected values were computed independently (Python, exact unit definitions) and are
 * cross-checked against well-known oilfield rules where those exist.
 */
class TubularGeometryTest {
    private fun inch(v: Double) = Length.inches(v)

    @Test
    fun insideCapacityMatchesExactCircleAreaAndTheOilfieldRule() {
        val cap = TubularGeometry.insideCapacity(inch(6.2))
        assertEquals(PI / 4 * (6.2 * 0.0254) * (6.2 * 0.0254), cap.cubicMetersPerMeter, 1e-15)
        // Independent value: 0.037341626964746966 bbl/ft
        assertEquals(0.037341626964746966, cap.barrelsPerFoot, 1e-15)
        // Oilfield rule of thumb: capacity (bbl/ft) = ID² / 1029.4 (1029.4 is a rounded constant)
        assertEquals(6.2 * 6.2 / 1029.4, cap.barrelsPerFoot, 1e-6)
        assertEquals(0.008169695181413158, TubularGeometry.insideCapacity(inch(2.9)).barrelsPerFoot, 1e-15)
    }

    @Test
    fun annulusPlusSteelPlusBoreEqualsOuterBore() {
        // Exact identity: π/4·D² = π/4·ID² + π/4·(OD² − ID²) + π/4·(D² − OD²)
        val bore = TubularGeometry.insideCapacity(inch(2.9))
        val steel = TubularGeometry.openEndDisplacement(inch(3.5), inch(2.9))
        val annulus = TubularGeometry.annularCapacity(inch(6.2), inch(3.5))
        val outer = TubularGeometry.insideCapacity(inch(6.2))
        assertEquals(outer.cubicMetersPerMeter, (bore + steel + annulus).cubicMetersPerMeter, 1e-15)
        assertEquals(
            TubularGeometry.closedEndDisplacement(inch(3.5)).cubicMetersPerMeter,
            (bore + steel).cubicMetersPerMeter, 1e-15,
        )
    }

    @Test
    fun wallThicknessIsHalfTheDiameterDifference() {
        assertEquals(0.4, TubularGeometry.wallThickness(inch(7.0), inch(6.2)).inches, 1e-12)
    }

    @Test
    fun invalidGeometryIsRejected() {
        assertFailsWith<IllegalArgumentException> { TubularGeometry.annularCapacity(inch(3.5), inch(3.5)) }
        assertFailsWith<IllegalArgumentException> { TubularGeometry.annularCapacity(inch(3.0), inch(3.5)) }
        assertFailsWith<IllegalArgumentException> { TubularGeometry.insideCapacity(inch(0.0)) }
        assertFailsWith<IllegalArgumentException> { TubularGeometry.openEndDisplacement(inch(2.9), inch(3.5)) }
    }

    @Test
    fun capacityRateAndGradientUnitsRoundTrip() {
        assertEquals(0.5, VolumePerLength.barrelsPerFoot(0.5).barrelsPerFoot, 1e-14)
        assertEquals(0.5, VolumePerLength.barrelsPerMeter(0.5).barrelsPerMeter, 1e-14)
        assertEquals(1.5, FlowRate.barrelsPerMinute(1.5).barrelsPerMinute, 1e-14)
        assertEquals(1.5 * 0.158987294928, FlowRate.barrelsPerMinute(1.5).cubicMetersPerMinute, 1e-14)
        assertEquals(0.433, PressureGradient.psiPerFoot(0.433).psiPerFoot, 1e-14)
    }

    @Test
    fun onePoundPerGallonGivesExactly12Over231PsiPerFoot() {
        // ρ = 1 lb / 231 in³; ρ·g·(1 ft) in psi = (12 in)/(231 in) = 0.051948... (the "0.052" factor)
        val ppg = Density.poundsPerGallon(1.0)
        val gradient = PressureGradient(ppg.kilogramsPerCubicMeter * UnitDefinitions.STANDARD_GRAVITY_M_PER_S2)
        assertEquals(12.0 / 231.0, gradient.psiPerFoot, 1e-14)
    }
}
