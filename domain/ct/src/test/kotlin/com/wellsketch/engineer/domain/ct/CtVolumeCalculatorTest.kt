package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.model.ct.CtStringSection
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.FlowRate
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.schematic.CtSurfaceViewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Expected values computed independently (Python): harness verify/expected_ct.py. */
class CtVolumeCalculatorTest {
    private val project = SyntheticDemoProjects.vertical()
    private val op = project.ctOperations.single().copy(currentDepth = Length.meters(2000.0))

    private fun analyze(o: com.wellsketch.engineer.core.model.ct.CtOperation = op) =
        CtOperationAnalyzer().analyze(project.well, o, CtSurfaceViewMode.COMPACT).volumes

    @Test
    fun ctInternalAndSteelVolumes() {
        val v = analyze()
        // nose 2006 m MD RKB, BHA 1.85 m, CT from the tubing hanger (9 m) to the BHA top (2004.15 m)
        assertEquals(1995.15, v.ctLengthInWell.meters, 1e-9)
        assertEquals(5.008022087945996, v.ctInternalVolumeTotal.cubicMeters, 1e-12)
        assertEquals(31.499511267324607, v.ctInternalVolumeTotal.barrels, 1e-9)
        assertEquals(2.2203900597256565, v.ctInternalVolumeInWell.cubicMeters, 1e-12)
        assertEquals(0.8756670602210468, v.ctSteelDisplacementInWell.cubicMeters, 1e-12)
        assertEquals(0.004167669015576919, v.bhaDisplacementUpperBound.cubicMeters, 1e-15)
    }

    @Test
    fun annulusVolumeTimesAndVelocities() {
        val v = analyze()
        assertEquals(5.409810779759998, v.annulus.total.cubicMeters, 1e-9)
        assertEquals(2006.0, v.annulus.bottomMd!!.meters, 1e-9)
        assertEquals(1.5, v.pumpRate!!.barrelsPerMinute, 1e-12)
        assertEquals(20.999674178216406, v.timeToNozzleMinutes!!, 1e-9)
        assertEquals(22.684457405689432, v.bottomsUpMinutes!!, 1e-9)
        assertEquals(88.01274603105807, v.slowestAnnularVelocity!!.metersPerMinute, 1e-9)
        assertEquals(120.85332290831856, v.fastestAnnularVelocity!!.metersPerMinute, 1e-9)
        // segments are contiguous from the conduit top to the nose
        v.annulus.segments.zipWithNext().forEach { (a, b) -> assertEquals(a.bottomMd.meters, b.topMd.meters, 1e-9) }
        assertEquals(9.0, v.annulus.topMd!!.meters, 1e-9)
    }

    @Test
    fun noRateMeansNoTimes() {
        val v = analyze(op.copy(pumpRate = null))
        assertNull(v.timeToNozzleMinutes)
        assertNull(v.bottomsUpMinutes)
        assertTrue(v.annularVelocities.isEmpty())
        assertTrue(v.issues.any { it.code == "CT_RATE_NOT_SPECIFIED" })
        val zero = analyze(op.copy(pumpRate = FlowRate.ZERO))
        assertTrue(zero.issues.any { it.code == "CT_RATE_NOT_POSITIVE" })
    }

    @Test
    fun taperedStringIsLaidOutFromTheDownholeEnd() {
        val p = DataProvenance.syntheticDemo()
        val tapered = op.ctString.copy(
            sections = listOf(
                CtStringSection(Length.meters(1000.0), Length.inches(1.75), Length.inches(0.156), "DEMO", p),
                CtStringSection(Length.meters(3500.0), Length.inches(1.75), Length.inches(0.134), "DEMO", p),
            ),
        )
        val v = analyze(op.copy(ctString = tapered))
        assertEquals(1000.0, v.sections[0].lengthInWell.meters, 1e-9)
        assertEquals(995.15, v.sections[1].lengthInWell.meters, 1e-9)
        val a = Math.PI / 4 * Math.pow((1.75 - 0.312) * 0.0254, 2.0)
        val b = Math.PI / 4 * Math.pow((1.75 - 0.268) * 0.0254, 2.0)
        assertEquals(a * 1000.0 + b * 995.15, v.ctInternalVolumeInWell.cubicMeters, 1e-12)
        assertEquals(a * 1000.0 + b * 3500.0, v.ctInternalVolumeTotal.cubicMeters, 1e-12)
    }

    @Test
    fun ctDeeperThanTheTubingUsesTheCasingBelowTheEndOfTubing() {
        val v = analyze(op.copy(currentDepth = Length.meters(2300.0))) // nose 2306 m, EOT 2250 m
        val outer = v.annulus.segments.map { Math.round(it.outerDiameter.inches * 1000) / 1000.0 }.distinct()
        assertEquals(listOf(2.9, 6.2), outer)
    }
}
