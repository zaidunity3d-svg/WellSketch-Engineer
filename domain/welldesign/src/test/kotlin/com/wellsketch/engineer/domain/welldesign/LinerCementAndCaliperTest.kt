package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.catalog.TubularKind
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.validation.WellGeometryValidator
import com.wellsketch.engineer.core.engineering.volumes.DiameterBasis
import com.wellsketch.engineer.core.engineering.volumes.WellboreGeometry
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.domain.wellcalc.CementExcessPolicy
import com.wellsketch.engineer.domain.wellcalc.CementVolumeCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Liner cementing as liner programmes compute it (synthetic well, not real data):
 * open-hole volumes from the caliper (average) hole diameter, cement above the liner top
 * around the running string inside the host casing, and displacement through the running
 * string from the rig floor to the liner top plus the liner to the float collar.
 * Expected values are closed-form π/4·D² results computed independently.
 */
class LinerCementAndCaliperTest {
    private fun md(m: Double) = MeasuredDepth.rkb(Length.meters(m))
    private fun Well.apply(edit: (Well) -> EditOutcome): Well = assertIs<EditOutcome.Applied>(edit(this)).well
    private fun spec(id: String) = TubularCatalog.byId(id)!!.toSpec(null, null)
    private val calc = CementVolumeCalculator()
    private val policy = CementExcessPolicy(openHoleExcessFraction = 0.25)

    // GL MD 11.1 m; 7 in 26 lb/ft (ID 6.2764 in) to 2800 m; 5-1/2 in 17 lb/ft liner (ID 4.8921 in) 2650–3300 m in 6-1/8 in hole.
    private val base: Well = WellProjects.newWell("Liner", Length.meters(11.1), Length.meters(20.0), nowMillis = 1L, id = "l").well
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(8.5), md(2800.0), id = "h4") }
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(6.125), md(3300.0), id = "h5") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.PRODUCTION, spec("c-7-26-api"), md(11.1), md(2800.0), id = "prod") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.PRODUCTION_LINER, spec("c-5p5-17-api"), md(2650.0), md(3300.0), id = "lin") }
        .apply { WellStructureEdits.setCement(it, "lin", md(2600.0)) } // 50 m above the liner top
        .apply { WellStructureEdits.setFloatCollar(it, "lin", md(3275.0)) }

    private val full = base
        .apply { WellStructureEdits.setHoleAverageDiameter(it, "h5", Length.inches(6.75)) }
        .apply { WellStructureEdits.setRunningString(it, "lin", spec("dp-3p5-13p3-eu")) }

    private fun job(w: Well) = calc.calculate(WellboreGeometry(WellGeometryContext(w), null), "lin", policy)!!

    @Test
    fun linerJobWithCaliperAndRunningStringMatchesTheClosedForm() {
        val r = job(full)
        val seg = r.annulus.segments
        assertEquals(0.687683045312423, seg.single { it.description.contains("running string") }.volume.cubicMeters, 1e-9) // 7 in × 3-1/2 in DP, 50 m
        assertEquals(0.6949389423740255, seg.single { it.description.startsWith("Production casing") && !it.description.contains("running") }.volume.cubicMeters, 1e-9)
        val oh = seg.single { it.outerBasis.isOpenHole }
        assertEquals(DiameterBasis.CALIPER, oh.outerBasis)
        assertEquals(4.849348921050271, oh.volume.cubicMeters, 1e-9) // 6.75 in caliper × 5-1/2 in, +25 %
        assertEquals(0.3031712225928241, r.shoeTrack!!.volume.cubicMeters, 1e-9)
        assertEquals(6.535142131329543, r.slurryVolume.cubicMeters, 1e-9)
        // displacement: 3-1/2 in DP from MD 0 to the liner top, then the liner to the float collar
        assertEquals(10.258391418262777, r.displacement.segments.first().volume.cubicMeters, 1e-9)
        assertEquals(17.83767198308338, r.displacement.total.cubicMeters, 1e-9)
        assertTrue(r.issues.none { it.code == "CEMENT_LINER_RUNNING_STRING" || it.code == "CEMENT_ABOVE_STRING" })
        assertTrue(r.issues.any { it.code == "CEMENT_ABOVE_LINER_TOP" && it.severity == Severity.INFO })
    }

    @Test
    fun withoutTheNewInputsTheOldBehaviourAndWarningsRemain() {
        val r = job(base)
        assertEquals(2.3009665798860994, r.annulus.segments.single { it.outerBasis.isOpenHole }.volume.cubicMeters, 1e-9) // bit size
        assertTrue(r.issues.any { it.code == "CEMENT_LINER_RUNNING_STRING" })
        assertTrue(r.issues.any { it.code == "CEMENT_ABOVE_STRING" })
        assertTrue(r.annulus.segments.none { it.description.contains("running string") })
    }

    @Test
    fun leadAndTailSplitKeepsTheAboveTopCementInTheLead() {
        val r = job(full.apply { WellStructureEdits.setCementTailTop(it, "lin", md(3000.0)) })
        assertTrue(r.lead!!.segments.first().description.contains("running string"))
        assertEquals(r.annulus.total.cubicMeters, r.lead!!.total.cubicMeters + r.tail!!.total.cubicMeters, 1e-9)
    }

    @Test
    fun validatorAcceptsCementAboveALinerTopOnItsRunningStringAndNotesUndergauge() {
        fun codes(w: Well) = WellGeometryValidator().validate(WellGeometryContext(w), null)
        assertEquals(Severity.WARNING, codes(base).single { it.code == "CEMENT_ABOVE_TOP" }.severity)
        assertEquals(Severity.INFO, codes(full).single { it.code == "CEMENT_ABOVE_TOP" }.severity)
        val under = full.apply { WellStructureEdits.setHoleAverageDiameter(it, "h5", Length.inches(6.0)) }
        assertTrue(codes(under).any { it.code == "HOLE_UNDERGAUGE" })
        // running strings are for liners only
        assertIs<EditOutcome.Rejected>(WellStructureEdits.setRunningString(full, "prod", spec("dp-3p5-13p3-eu")))
        assertIs<EditOutcome.Rejected>(WellStructureEdits.setHoleAverageDiameter(full, "h5", Length.ZERO))
    }

    @Test
    fun defaultRunningStringIsTheLargestCataloguePipeThatPassesTheHostDrift() {
        // 7 in 26 lb/ft drift 6.1512 in, 0.25 in clearance: drill pipe by tool-joint OD, tubing by body OD.
        val dp = base.apply { WellStructureDefaults.setDefaultRunningString(it, "lin", TubularKind.DRILL_PIPE) }
        assertEquals("dp-4p5-16p6-eu", dp.casingStrings.single { it.id == "lin" }.runningString!!.catalogItemId) // 5-7/8 in joint
        val tbg = base.apply { WellStructureDefaults.setDefaultRunningString(it, "lin", TubularKind.TUBING) }
        assertEquals("t-4p5-12p75-eu", tbg.casingStrings.single { it.id == "lin" }.runningString!!.catalogItemId)
        assertIs<EditOutcome.Rejected>(WellStructureDefaults.setDefaultRunningString(base, "lin", TubularKind.CASING))
        assertIs<EditOutcome.Rejected>(WellStructureDefaults.setDefaultRunningString(base, "prod", TubularKind.DRILL_PIPE))
    }
}
