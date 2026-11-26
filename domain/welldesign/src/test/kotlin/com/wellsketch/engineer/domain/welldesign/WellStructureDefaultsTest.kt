package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.catalog.TubularKind
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.validation.WellGeometryValidator
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.TubingConfiguration
import com.wellsketch.engineer.core.model.well.Well
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WellStructureDefaultsTest {
    private fun md(m: Double) = MeasuredDepth.rkb(Length.meters(m))
    private fun Well.apply(edit: (Well) -> EditOutcome): Well = assertIs<EditOutcome.Applied>(edit(this)).well
    private fun near(expected: Double, actual: Double, tol: Double = 1e-6) =
        assertTrue(abs(expected - actual) <= tol, "expected $expected, was $actual")

    /** Synthetic blank well: GL MD 11.1 m (RKB 31.1 m, GL 20.0 m above MSL). */
    private val blank = WellProjects.newWell("Defaults test", Length.meters(11.1), Length.meters(20.0), nowMillis = 1L, id = "d1").well

    private fun withHoles(): Well = blank
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(26.0), md(60.0), id = "h1") }
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(17.5), md(600.0), id = "h2") }

    private fun fullHoles(): Well = withHoles()
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(12.25), md(1950.0), id = "h3") }
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(8.5), md(2800.0), id = "h4") }
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(6.125), md(3300.0), id = "h5") }

    @Test
    fun firstStringOnAnEmptyWellIsTheLargestCatalogueConductorFromGround() {
        val w = blank.apply(WellStructureDefaults::addNextString)
        val s = w.casingStrings.single()
        assertEquals(CasingRole.CONDUCTOR, s.role)
        val largest = TubularCatalog.outerDiameters(TubularKind.CASING).first()
        near(largest, s.spec.outerDiameter.inches)
        // lightest nominal weight of that OD, taken from the catalogue record
        near(TubularCatalog.weights(TubularKind.CASING, largest).first().nominalWeightLbFt, s.spec.nominalLinearMass!!.poundsPerFoot)
        val ctx = WellGeometryContext(w)
        near(11.1, ctx.canonical(s.top).meters)
        // no hole sections yet: shoe 500 m below the top
        near(511.1, ctx.canonical(s.shoe.depth).meters)
    }

    @Test
    fun shoeStopsAtTheLastHoleSectionTheStringFitsIn() {
        // 20 in fits the 26 in hole only, so the shoe is at the 26 in section bottom
        val w = withHoles().apply(WellStructureDefaults::addNextString)
        near(60.0, WellGeometryContext(w).canonical(w.casingStrings.single().shoe.depth).meters)
    }

    @Test
    fun withAHoleProgrammeTheSequenceGivesTheUsualSizesAndShoes() {
        var w = fullHoles()
        repeat(5) { w = w.apply(WellStructureDefaults::addNextString) }
        val ctx = WellGeometryContext(w)
        val rows = w.casingStrings.sortedByDescending { it.spec.outerDiameter.meters }
            .map { Triple(it.role, it.spec.outerDiameter.inches, ctx.canonical(it.shoe.depth).meters) }
        val expected = listOf(
            Triple(CasingRole.CONDUCTOR, 20.0, 60.0),
            Triple(CasingRole.SURFACE, 13.375, 600.0),
            Triple(CasingRole.INTERMEDIATE, 9.625, 1950.0),
            Triple(CasingRole.PRODUCTION, 7.0, 2800.0),
            Triple(CasingRole.PRODUCTION_LINER, 5.5, 3300.0),
        )
        expected.zip(rows).forEach { (e, r) ->
            assertEquals(e.first, r.first)
            near(e.second, r.second)
            near(e.third, r.third)
        }
        val errors = WellGeometryValidator().validate(ctx, null).filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), "unexpected geometry errors: $errors")
    }

    @Test
    fun repeatedAddsFollowTheUsualSequenceAndEveryStringPassesTheOneOutside() {
        var w = fullHoles()
        val roles = mutableListOf<CasingRole>()
        repeat(WellStructureDefaults.STRING_SEQUENCE.size) {
            w = w.apply(WellStructureDefaults::addNextString)
            roles += w.casingStrings.minBy { it.spec.outerDiameter.meters }.role
        }
        assertEquals(WellStructureDefaults.STRING_SEQUENCE, roles)
        val byOd = w.casingStrings.sortedByDescending { it.spec.outerDiameter.meters }
        byOd.zipWithNext().forEach { (outer, inner) ->
            val limit = outer.spec.driftDiameter ?: outer.spec.innerDiameter
            assertTrue(inner.spec.outerDiameter < limit, "${inner.name} OD must pass ${outer.name} drift/ID")
        }
        // the liner hangs 150 m above the production casing shoe
        val ctx = WellGeometryContext(w)
        val liner = w.casingStrings.single { it.role == CasingRole.PRODUCTION_LINER }
        val production = w.casingStrings.single { it.role == CasingRole.PRODUCTION }
        near(ctx.canonical(production.shoe.depth).meters - 150.0, ctx.canonical(liner.top).meters)
        val errors = WellGeometryValidator().validate(ctx, null).filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), "unexpected geometry errors: $errors")
    }

    @Test
    fun linerTopUsesTheCanonicalDatumWhenTheOuterShoeWasEnteredFromGround() {
        val surface = TubularCatalog.weights(TubularKind.CASING, 13.375).first().toSpec(null, null)
        val w = withHoles()
            .apply {
                WellStructureEdits.addCasingString(
                    it, CasingRole.PRODUCTION, surface, MeasuredDepth.gl(Length.ZERO), MeasuredDepth.gl(Length.meters(500.0)), id = "p1",
                )
            }
            .apply(WellStructureDefaults::addNextString)
        val liner = w.casingStrings.single { it.id != "p1" }
        assertEquals(CasingRole.PRODUCTION_LINER, liner.role)
        // shoe at 500 m below GL = 511.1 m MD RKB; liner top 150 m above it
        near(361.1, WellGeometryContext(w).canonical(liner.top).meters)
    }

    @Test
    fun rejectedWhenNoCatalogueCasingFitsInside() {
        val smallest = TubularCatalog.outerDiameters(TubularKind.CASING).last()
        val spec = TubularCatalog.weights(TubularKind.CASING, smallest).last().toSpec(null, null)
        val w = withHoles().apply { WellStructureEdits.addCasingString(it, CasingRole.PRODUCTION, spec, md(11.1), md(590.0), id = "p1") }
        assertIs<EditOutcome.Rejected>(WellStructureDefaults.addNextString(w))
        // and when the next hole is too small for every catalogue size
        val tiny = blank.apply { WellStructureEdits.addHoleSection(it, Length.inches(4.0), md(100.0), id = "h1") }
        assertIs<EditOutcome.Rejected>(WellStructureDefaults.addNextString(tiny))
    }

    @Test
    fun defaultTubingIsLightestThreeAndAHalfFromGroundToAboveTheDeepestShoe() {
        var cased = fullHoles()
        repeat(4) { cased = cased.apply(WellStructureDefaults::addNextString) } // conductor … production (shoe 2800 m)
        val w = cased.apply { WellStructureDefaults.installDefaultTubing(it, CompletionState.CURRENT) }
        val tubing = assertIs<TubingConfiguration.Installed>(w.completion(CompletionState.CURRENT)!!.tubing).tubing
        near(3.5, tubing.spec.outerDiameter.inches)
        near(TubularCatalog.weights(TubularKind.TUBING, 3.5).first().nominalWeightLbFt, tubing.spec.nominalLinearMass!!.poundsPerFoot)
        val ctx = WellGeometryContext(w)
        near(11.1, ctx.canonical(tubing.hangerDepth).meters)
        near(2700.0, ctx.canonical(tubing.endOfTubing).meters) // shoe 2800 − 100
    }

    @Test
    fun defaultTubingWithoutCasingRunsOneThousandMetresBelowGround() {
        val w = blank.apply { WellStructureDefaults.installDefaultTubing(it, CompletionState.AS_DESIGNED) }
        val tubing = assertIs<TubingConfiguration.Installed>(w.completion(CompletionState.AS_DESIGNED)!!.tubing).tubing
        near(1011.1, WellGeometryContext(w).canonical(tubing.endOfTubing).meters)
    }
}
