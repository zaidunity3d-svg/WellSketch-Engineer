package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.volumes.DiameterBasis
import com.wellsketch.engineer.core.engineering.volumes.WellboreGeometry
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.domain.wellcalc.AnnulusBoundary
import com.wellsketch.engineer.domain.wellcalc.CementExcessPolicy
import com.wellsketch.engineer.domain.wellcalc.CementVolumeCalculator
import com.wellsketch.engineer.domain.wellcalc.ObjectVolumeCalculator
import com.wellsketch.engineer.domain.wellcalc.ObjectVolumeMode
import com.wellsketch.engineer.domain.wellcalc.WellVolumeCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * End-to-end cross-check of the editor path (blank well → catalogue sizes → structure edits →
 * calculators) against a second, independent implementation: the owner's v0.9 HTML prototype.
 * Its own `volume()` and `cementVolume()` functions were run in Node on the same synthetic
 * well (script: docs/crosscheck/v09-scenario.md). The HTML is a reference prototype, not a
 * validated system: agreement shows both implement the same geometry, not that either is
 * field-approved.
 *
 * Synthetic well (not real data): GL MD 11.1 m; holes 26 / 17-1/2 / 12-1/4 / 8-1/2 /
 * 6-1/8 in to 60 / 600 / 1950 / 2800 / 3300 m; 20 in 94, 13-3/8 in 54.5, 9-5/8 in 47 and
 * 7 in 26 lb/ft from GL; 5-1/2 in 17 lb/ft liner 2650–3300 m; 3-1/2 in 9.2 lb/ft tubing to 2600 m.
 */
class HtmlV09EditorCrossCheckTest {
    private fun md(m: Double) = MeasuredDepth.rkb(Length.meters(m))
    private fun m(v: Double) = Length.meters(v)
    private fun Well.apply(edit: (Well) -> EditOutcome): Well = assertIs<EditOutcome.Applied>(edit(this)).well
    private fun spec(id: String) = TubularCatalog.byId(id)!!.toSpec(null, null)

    private val well: Well = WellProjects.newWell("Cross-check", m(11.1), m(20.0), nowMillis = 1L, id = "x").well
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(26.0), md(60.0), id = "h1") }
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(17.5), md(600.0), id = "h2") }
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(12.25), md(1950.0), id = "h3") }
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(8.5), md(2800.0), id = "h4") }
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(6.125), md(3300.0), id = "h5") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.CONDUCTOR, spec("c-20-94-api"), md(11.1), md(60.0), id = "cond") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.SURFACE, spec("c-13p375-54p5-api"), md(11.1), md(600.0), id = "surf") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.INTERMEDIATE, spec("c-9p625-47-api"), md(11.1), md(1950.0), id = "int") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.PRODUCTION, spec("c-7-26-api"), md(11.1), md(2800.0), id = "prod") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.PRODUCTION_LINER, spec("c-5p5-17-api"), md(2650.0), md(3300.0), id = "lin") }
        .apply { WellStructureEdits.setTubing(it, CompletionState.CURRENT, spec("t-3p5-9p2-nu"), md(11.1), md(2600.0)) }
        .apply { WellStructureEdits.setCement(it, "surf", md(11.1)) }
        .apply { WellStructureEdits.setFloatCollar(it, "surf", md(575.0)) }
        .apply { WellStructureEdits.setCement(it, "int", md(1200.0)) }
        .apply { WellStructureEdits.setFloatCollar(it, "int", md(1925.0)) }
        .apply { WellStructureEdits.setCement(it, "prod", md(1800.0)) }
        .apply { WellStructureEdits.setFloatCollar(it, "prod", md(2775.0)) }
        .apply { WellStructureEdits.setCement(it, "lin", md(2650.0)) }
        .apply { WellStructureEdits.setFloatCollar(it, "lin", md(3275.0)) }

    private val geometry = WellboreGeometry(WellGeometryContext(well), well.completion(CompletionState.CURRENT))
    private val volumes = WellVolumeCalculator()
    private val cement = CementVolumeCalculator()

    @Test
    fun flowPathAndTubingAnnulusAgreeWithV09() {
        // v0.9 volume('internal', tubing, 11.1, 2600) and ctGeometry(11.1, 3000, 'active')
        assertEquals(11.744236773442374, volumes.flowPathVolume(geometry, m(11.1), m(2600.0)).total.cubicMeters, 1e-9)
        assertEquals(16.98667526600155, volumes.flowPathVolume(geometry, m(11.1), m(3000.0)).total.cubicMeters, 1e-9)
        // v0.9 volume('annulus', tubing, 7 in casing, 11.1, 2600); no packer → to end of tubing in both
        assertEquals(35.60685272018664, volumes.tubingAnnulusVolume(geometry).total.cubicMeters, 1e-9)
    }

    @Test
    fun annulusOutsideEachStringAgreesWithV09() {
        val s = well.casingStrings.associateBy { it.id }
        fun outside(id: String, top: Double, bottom: Double) =
            volumes.annulusOutside(geometry, s.getValue(id), m(top), m(bottom)).total.cubicMeters
        // v0.9 sums: inside 13-3/8 then 12-1/4 hole; inside 9-5/8 then 8-1/2 hole; inside 7 in then 6-1/8 hole
        assertEquals(19.84288613021169 + 39.27972626050719, outside("int", 11.1, 1950.0), 1e-9)
        assertEquals(25.898988200662593 + 10.013806555664297, outside("prod", 11.1, 2800.0), 1e-9)
        assertEquals(0.6949389423740255 + 1.8407732639088796, outside("lin", 2650.0, 3300.0), 1e-9)
    }

    private val objects = ObjectVolumeCalculator()
    private fun inside(pipe: String, top: Double, bottom: Double) =
        objects.calculate(geometry, pipe, ObjectVolumeMode.INSIDE, AnnulusBoundary.Automatic, m(top), m(bottom))
    private fun between(pipe: String, outer: AnnulusBoundary, top: Double, bottom: Double) =
        objects.calculate(geometry, pipe, ObjectVolumeMode.ANNULUS, outer, m(top), m(bottom))
    private fun casing(id: String) = AnnulusBoundary.Casing(id, well.casingStrings.single { it.id == id }.name)
    private val tubingId get() = well.completion(CompletionState.CURRENT)!!.tubingString!!.id

    @Test
    fun namedObjectVolumesAgreeWithV09VolumeCalculator() {
        // v0.9 volume('internal', …): inside a chosen string, whatever is inside it
        assertEquals(74.03928963054737, inside("int", 11.1, 1950.0).total.cubicMeters, 1e-9)
        assertEquals(11.744236773442374, inside(tubingId, 11.1, 2600.0).total.cubicMeters, 1e-9)
        assertEquals(55.66875188501022, inside("prod", 11.1, 2800.0).total.cubicMeters, 1e-9)
        assertEquals(7.882451787413426, inside("lin", 2650.0, 3300.0).total.cubicMeters, 1e-9)
        // v0.9 volume('annulus', pipe, chosen outer string or hole section, …)
        assertEquals(35.60685272018664, between(tubingId, casing("prod"), 11.1, 2600.0).total.cubicMeters, 1e-9)
        assertEquals(19.84288613021169, between("int", casing("surf"), 11.1, 600.0).total.cubicMeters, 1e-9)
        assertEquals(39.27972626050719, between("int", AnnulusBoundary.OpenHole, 600.0, 1950.0).total.cubicMeters, 1e-9)
        assertEquals(25.898988200662593, between("prod", casing("int"), 11.1, 1950.0).total.cubicMeters, 1e-9)
        assertEquals(10.013806555664297, between("prod", AnnulusBoundary.OpenHole, 1950.0, 2800.0).total.cubicMeters, 1e-9)
        assertEquals(0.6949389423740255, between("lin", casing("prod"), 2650.0, 2800.0).total.cubicMeters, 1e-9)
        assertEquals(1.8407732639088796, between("lin", AnnulusBoundary.OpenHole, 2800.0, 3300.0).total.cubicMeters, 1e-9)
        // automatic outer wall = the cementing rule: next casing, else open hole
        assertEquals(19.84288613021169 + 39.27972626050719, between("int", AnnulusBoundary.Automatic, 11.1, 1950.0).total.cubicMeters, 1e-9)
    }

    @Test
    fun namedObjectVolumesRefuseIntervalsOutsideTheObjects() {
        // like v0.9: no partial total when the interval leaves the pipe or the outer string
        val outsidePipe = inside("lin", 2000.0, 3300.0)
        assertTrue(outsidePipe.segments.isEmpty() && outsidePipe.issues.single().code == "OBJ_VOLUME_OUTSIDE_PIPE")
        val outsideOuter = between("int", casing("surf"), 11.1, 1000.0)
        assertTrue(outsideOuter.segments.isEmpty() && outsideOuter.issues.single().code == "OBJ_VOLUME_OUTSIDE_BOUNDARY")
        val noClearance = between("surf", casing("int"), 11.1, 600.0)
        assertTrue(noClearance.segments.isEmpty())
        // open hole across a cased interval is allowed but flagged
        val ignoresCasing = between("int", AnnulusBoundary.OpenHole, 11.1, 1950.0)
        assertTrue(ignoresCasing.issues.any { it.code == "OBJ_VOLUME_IGNORES_CASING" })
    }

    @Test
    fun boundaryListOffersOnlyLargerOverlappingStrings() {
        val prod = objects.pipes(geometry).single { it.id == "prod" }
        val labels = objects.boundaries(geometry, prod)
        assertEquals(
            listOf(AnnulusBoundary.Automatic, casing("int"), casing("surf"), casing("cond"), AnnulusBoundary.OpenHole),
            labels,
        )
        val tubing = objects.pipes(geometry).first()
        assertTrue(tubing.isTubing)
        assertEquals(m(11.1) to m(1950.0), objects.defaultInterval(geometry, prod, ObjectVolumeMode.ANNULUS, casing("int")))
    }

    @Test
    fun leadAndTailSplitAgreesWithV09() {
        // v0.9 cementVolume() lead / tail with the same TOC, tail top and excess
        val cases = listOf(
            Triple(Job("surf", 1.00, 0.0), 400.0, 48.510629434701805 to 25.813579138273152),
            Triple(Job("int", 0.30, 0.0), 1700.0, 18.912460792096056 to 9.456230396048028),
            Triple(Job("prod", 0.20, 0.05), 2500.0, 9.879243194702187 to 4.241141600046055),
            Triple(Job("lin", 0.25, 0.0), 3000.0, 1.6153255743284651 to 1.3805799479316596),
        )
        for ((job, tailTop, expected) in cases) {
            val w = well.apply { WellStructureEdits.setCementTailTop(it, job.id, md(tailTop)) }
            val g = WellboreGeometry(WellGeometryContext(w), w.completion(CompletionState.CURRENT))
            val r = cement.calculate(g, job.id, CementExcessPolicy(openHoleExcessFraction = job.open, casedHoleExcessFraction = job.cased))!!
            assertEquals(expected.first, r.lead!!.total.cubicMeters, 1e-9, "${job.id} lead")
            assertEquals(expected.second, r.tail!!.total.cubicMeters, 1e-9, "${job.id} tail")
            assertEquals(expected.first + expected.second, r.annulus.total.cubicMeters, 1e-9, "${job.id} lead + tail = annulus")
        }
    }

    @Test
    fun tailTopMustLieInsideTheCementColumn() {
        assertIs<EditOutcome.Rejected>(WellStructureEdits.setCementTailTop(well, "int", md(1100.0))) // above TOC 1200
        assertIs<EditOutcome.Rejected>(WellStructureEdits.setCementTailTop(well, "int", md(1950.0))) // at the shoe
        val split = well.apply { WellStructureEdits.setCementTailTop(it, "int", md(1700.0)) }
        // moving TOC below the tail top drops the split
        val moved = split.apply { WellStructureEdits.setCement(it, "int", md(1800.0)) }
        assertEquals(null, moved.casingStrings.single { it.id == "int" }.cement!!.tailTop)
        val noSplit = split.apply { WellStructureEdits.setCementTailTop(it, "int", null) }
        val g = WellboreGeometry(WellGeometryContext(noSplit), noSplit.completion(CompletionState.CURRENT))
        assertEquals(null, cement.calculate(g, "int")!!.lead)
    }

    private data class Job(val id: String, val open: Double, val cased: Double)

    @Test
    fun cementJobsAgreeWithV09() {
        // v0.9 cementVolume(): lead + tail (Android has no lead/tail split yet), open/cased split,
        // shoe track from the float collar, total = annulus with excess + shoe track, displacement to FC.
        val expected = mapOf(
            Job("surf", 1.00, 0.0) to doubleArrayOf(69.69666367333751, 4.627544899637452, 2.01591322044501, 76.34012179341997, 45.47093860035764),
            Job("int", 0.30, 0.0) to doubleArrayOf(28.368691188144084, 0.0, 0.9546558568073052, 29.32334704495139, 73.08463377374007),
            Job("prod", 0.20, 0.05) to doubleArrayOf(12.016567866797159, 2.1038169279510845, 0.49902068812982014, 14.619405482878063, 55.1697311968804),
            Job("lin", 0.25, 0.0) to doubleArrayOf(2.3009665798860994, 0.6949389423740255, 0.3031712225928241, 3.2990767448529486, 7.579280564820602),
        )
        for ((job, e) in expected) {
            val r = cement.calculate(geometry, job.id, CementExcessPolicy(openHoleExcessFraction = job.open, casedHoleExcessFraction = job.cased))!!
            val open = r.annulus.segments.filter { it.outerBasis == DiameterBasis.BIT_SIZE }.sumOf { it.volume.cubicMeters }
            val cased = r.annulus.segments.filter { it.outerBasis != DiameterBasis.BIT_SIZE }.sumOf { it.volume.cubicMeters }
            assertEquals(e[0], open, 1e-9, "${job.id} open-hole annulus")
            assertEquals(e[1], cased, 1e-9, "${job.id} cased-hole annulus")
            assertEquals(e[2], r.shoeTrack!!.volume.cubicMeters, 1e-9, "${job.id} shoe track")
            assertEquals(e[3], r.slurryVolume.cubicMeters, 1e-9, "${job.id} slurry total")
            assertEquals(e[4], r.displacement.total.cubicMeters, 1e-9, "${job.id} displacement to float collar")
        }
    }
}
