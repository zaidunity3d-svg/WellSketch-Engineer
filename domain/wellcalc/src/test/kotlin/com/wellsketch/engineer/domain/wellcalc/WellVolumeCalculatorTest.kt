package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.volumes.Bore
import com.wellsketch.engineer.core.engineering.volumes.DiameterBasis
import com.wellsketch.engineer.core.engineering.volumes.WellboreGeometry
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.Well
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Expected values computed independently in Python from the synthetic demo data and exact
 * unit definitions (harness: verify/expected_vertical.py, expected_sshape.py).
 */
class WellVolumeCalculatorTest {
    private val calc = WellVolumeCalculator()
    private fun m(v: Double) = Length.meters(v)
    private fun geometry(well: Well, state: CompletionState? = CompletionState.CURRENT) =
        WellboreGeometry(WellGeometryContext(well), state?.let(well::completion))

    private val vertical = geometry(SyntheticDemoProjects.vertical().well)

    @Test
    fun boreIsTubingThenInnermostCasingThenOpenHole() {
        assertIs<Bore.Tubing>(vertical.boreAt(m(1000.0)))
        val casing = assertIs<Bore.Casing>(vertical.boreAt(m(2300.0)))
        assertEquals(6.2, casing.diameter.inches, 1e-12)
        val hole = assertIs<Bore.OpenHole>(vertical.boreAt(m(2395.0)))
        assertEquals(DiameterBasis.BIT_SIZE, hole.basis)
        // annulus outside the 9-5/8 in casing: 13-3/8 in ID above its shoe, then open hole
        assertEquals(12.4, vertical.annulusBoundaryAt(m(500.0), Length.inches(9.625), exceptStringId = "demo-vertical-c3")!!.diameter.inches, 1e-12)
        assertIs<Bore.OpenHole>(vertical.annulusBoundaryAt(m(1000.0), Length.inches(9.625), exceptStringId = "demo-vertical-c3"))
    }

    @Test
    fun flowPathVolumeIsSplitAtEveryGeometryChangeAndSumsCorrectly() {
        val v = calc.flowPathVolume(vertical)
        // tubing 11.1–2250 (ID 2.9 in), 7 in casing 2250–2390 (ID 6.2 in), 8-1/2 in hole 2390–2400;
        // values from verify/expected_vertical.py (GL MD 11.1)
        assertEquals(12.633863746500204, v.total.cubicMeters, 1e-9)
        assertEquals(79.4646122649087, v.total.barrels, 1e-8)
        assertEquals(listOf(11.1, 2250.0, 2390.0), v.segments.map { it.topMd.meters })
        assertEquals(listOf(2250.0, 2390.0, 2400.0), v.segments.map { it.bottomMd.meters })
        assertEquals(listOf(2.9, 6.2, 8.5), v.segments.map { it.outerDiameter.inches }.map { Math.round(it * 1000) / 1000.0 })
        // segments are contiguous and their lengths add up to the interval
        v.segments.zipWithNext().forEach { (a, b) -> assertEquals(a.bottomMd.meters, b.topMd.meters, 1e-9) }
        assertEquals(2400.0 - 11.1, v.length.meters, 1e-9)
        assertTrue(v.usesBitSize && v.issues.any { it.code == "VOLUME_BIT_SIZE" })
    }

    @Test
    fun tubingInsideAndBelowTubingAddUpToTheFlowPath() {
        val report = WellCalculations().report(SyntheticDemoProjects.vertical().well, CompletionState.CURRENT)
        assertEquals(9.540870623341167, report.tubingInside!!.total.cubicMeters, 1e-9)
        assertEquals(3.0929931231590366, report.belowTubing!!.total.cubicMeters, 1e-9)
        assertEquals(report.flowPath.total.cubicMeters, report.tubingInside!!.total.cubicMeters + report.belowTubing!!.total.cubicMeters, 1e-9)
    }

    @Test
    fun tubingAnnulusStopsAtThePackerTop() {
        val a = calc.tubingAnnulusVolume(vertical)
        assertEquals(29.048167106098678, a.total.cubicMeters, 1e-9)
        assertEquals(182.70747432525107, a.total.barrels, 1e-8)
        assertEquals(2200.0, a.bottomMd!!.meters, 1e-9)
        assertEquals(1, a.segments.size)
    }

    @Test
    fun flowPathStopsAtABridgePlugInADeviatedTubinglessWell() {
        val s = geometry(SyntheticDemoProjects.sShaped().well)
        val v = calc.flowPathVolume(s)
        // 7 in casing ID 6.2 in from GL (11.1 m) to the plug top at 2700 m MD; MD-based, independent of TVD
        assertEquals(52.37395186655987, v.total.cubicMeters, 1e-9)
        assertEquals(2700.0, v.bottomMd!!.meters, 1e-9)
        // an explicit interval through the plug is cut and reported
        val through = calc.flowPathVolume(s, m(2600.0), m(2850.0))
        assertEquals(2700.0, through.bottomMd!!.meters, 1e-9)
        assertTrue(through.issues.any { it.code == "VOLUME_STOPPED_AT_PLUG" })
        // no tubing: annulus result is empty with an explanation
        val annulus = calc.tubingAnnulusVolume(s)
        assertTrue(annulus.segments.isEmpty() && annulus.issues.any { it.code == "ANNULUS_NO_TUBING" })
    }

    @Test
    fun explicitIntervalVolumeAndInvalidInterval() {
        val v = calc.flowPathVolume(vertical, m(1000.0), m(1100.0))
        assertEquals(Math.PI / 4 * Math.pow(2.9 * 0.0254, 2.0) * 100.0, v.total.cubicMeters, 1e-12)
        val empty = calc.flowPathVolume(vertical, m(1100.0), m(1000.0))
        assertTrue(empty.segments.isEmpty() && empty.issues.any { it.code == "VOLUME_INTERVAL_EMPTY" })
    }

    @Test
    fun missingHoleDataIsReportedAsAGapNotGuessed() {
        val well = SyntheticDemoProjects.vertical().well.let { w -> w.copy(holeSections = w.holeSections.dropLast(1)) }
        val v = calc.flowPathVolume(geometry(well), m(2300.0), m(2400.0))
        // casing to 2390 is still computed; 2390–2400 has no hole data
        assertEquals(2390.0, v.bottomMd!!.meters, 1e-9)
        assertTrue(v.issues.any { it.code == "VOLUME_GEOMETRY_GAP" })
    }

    @Test
    fun noCompletionMeansTheCasingIsTheFlowPath() {
        val g = geometry(SyntheticDemoProjects.vertical().well, state = null)
        assertNull(g.tubing)
        assertIs<Bore.Casing>(g.boreAt(m(1000.0)))
    }
}
