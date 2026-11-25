package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.volumes.DiameterBasis
import com.wellsketch.engineer.core.engineering.volumes.WellboreGeometry
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.TubularSpec
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Expected values computed independently (Python): harness verify/expected_vertical.py. */
class CementVolumeCalculatorTest {
    private val well = SyntheticDemoProjects.vertical().well
    private val calc = CementVolumeCalculator()
    private fun geometry(w: com.wellsketch.engineer.core.model.well.Well = well) =
        WellboreGeometry(WellGeometryContext(w), w.completion(CompletionState.CURRENT))
    private val production = "demo-vertical-c4"

    @Test
    fun productionCasingAnnulusSwitchesFromPreviousCasingToOpenHole() {
        val job = assertNotNull(calc.calculate(geometry(), production))
        // 9-5/8 in ID 8.7 (1500–1695), 12-1/4 in hole (1695–1700), 8-1/2 in hole (1700–2390)
        assertEquals(listOf(8.7, 12.25, 8.5), job.annulus.segments.map { Math.round(it.outerDiameter.inches * 1000) / 1000.0 })
        assertEquals(listOf(DiameterBasis.STATED_ID, DiameterBasis.BIT_SIZE, DiameterBasis.BIT_SIZE), job.annulus.segments.map { it.outerBasis })
        assertEquals(11.022084766789941, job.annulus.total.cubicMeters, 1e-9)
        assertEquals(2.637184410336881, job.annulus.segments.first().volume.cubicMeters, 1e-9)
        // displacement to the shoe when no float collar is recorded
        assertEquals(46.37672631718511, job.displacement.total.cubicMeters, 1e-9)
        assertNull(job.shoeTrack)
        assertTrue(job.issues.any { it.code == "CEMENT_NO_FLOAT_COLLAR" })
        // rathole: 10 m of 8-1/2 in hole below the shoe, reported but not included by default
        assertEquals(0.36609615364794207, job.rathole!!.geometricVolume.cubicMeters, 1e-12)
        assertEquals(job.annulus.total.cubicMeters, job.slurryVolume.cubicMeters, 1e-12)
    }

    @Test
    fun openHoleExcessAppliesOnlyToOpenHole() {
        val job = calc.calculate(geometry(), production, CementExcessPolicy(openHoleExcessFraction = 0.30))!!
        assertEquals(13.53755487372586, job.annulus.total.cubicMeters, 1e-9)
        assertEquals(0.0, job.annulus.segments.first().excessFraction, 0.0)
        assertEquals(11.022084766789941, job.annulus.geometricTotal.cubicMeters, 1e-9)
    }

    @Test
    fun floatCollarGivesShoeTrackAndDisplacementToTheCollar() {
        val w = well.copy(casingStrings = well.casingStrings.map {
            if (it.id == production) it.copy(shoe = it.shoe.copy(floatCollarDepth = MeasuredDepth.rkb(Length.meters(2365.0)))) else it
        })
        val job = calc.calculate(geometry(w), production, CementExcessPolicy(openHoleExcessFraction = 0.30, includeRathole = true))!!
        assertEquals(0.4869458874126954, job.shoeTrack!!.volume.cubicMeters, 1e-12)
        assertEquals(45.889780429772415, job.displacement.total.cubicMeters, 1e-9)
        val expected = 13.53755487372586 + 0.4869458874126954 + 0.36609615364794207 * 1.3
        assertEquals(expected, job.slurryVolume.cubicMeters, 1e-9)
        assertTrue(job.issues.none { it.code == "CEMENT_NO_FLOAT_COLLAR" })
    }

    @Test
    fun intermediateCasingAnnulusUsesSurfaceCasingIdThenOpenHole() {
        val job = calc.calculate(geometry(), "demo-vertical-c3")!!
        assertEquals(38.44049041479109, job.annulus.total.cubicMeters, 1e-9)
        // the rathole is the 12-1/4 in section below the 1695 m shoe, not the next 8-1/2 in section
        assertEquals(5.0, job.rathole!!.length.meters, 1e-9)
        assertEquals(12.25, job.rathole!!.outerDiameter.inches, 1e-12)
    }

    @Test
    fun shoeAtTheBottomOfItsHoleSectionHasNoRathole() {
        val w = well.copy(casingStrings = well.casingStrings.map {
            if (it.id == "demo-vertical-c3") it.copy(shoe = it.shoe.copy(depth = MeasuredDepth.rkb(Length.meters(1700.0)))) else it
        })
        assertNull(calc.calculate(geometry(w), "demo-vertical-c3")!!.rathole)
    }

    @Test
    fun excessOutsideTheAllowedRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> { CementExcessPolicy(openHoleExcessFraction = -0.1) }
        assertFailsWith<IllegalArgumentException> { CementExcessPolicy(openHoleExcessFraction = 3.5) }
    }

    @Test
    fun wallMismatchIsReportedInTheTubularTable() {
        val bad = well.copy(casingStrings = well.casingStrings.map {
            if (it.id == production) it.copy(spec = TubularSpec(it.spec.outerDiameter, it.spec.innerDiameter, wallThickness = Length.inches(0.362), provenance = it.spec.provenance)) else it
        })
        val report = WellCalculations().report(bad, CompletionState.CURRENT)
        assertTrue(report.issues.any { it.code == "CALC_WALL_MISMATCH" })
        val row = report.tubulars.first { it.sourceId == production }
        assertEquals(0.362, row.wallThickness.inches, 1e-12)
        assertEquals(PI / 4 * Math.pow(6.2 * 0.0254, 2.0), row.insideCapacity.cubicMetersPerMeter, 1e-15)
        // consistent data: wall derived from OD and ID, no warning
        val ok = WellCalculations().report(well, CompletionState.CURRENT)
        assertTrue(ok.issues.none { it.code == "CALC_WALL_MISMATCH" })
        assertEquals(0.4, ok.tubulars.first { it.sourceId == production }.wallThickness.inches, 1e-12)
        assertEquals(4, ok.cementJobs.size)
    }
}
