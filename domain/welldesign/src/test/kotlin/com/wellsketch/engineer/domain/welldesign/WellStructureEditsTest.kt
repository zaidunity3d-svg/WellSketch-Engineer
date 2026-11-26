package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.catalog.TubularKind
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.summary.KeyDepthKind
import com.wellsketch.engineer.core.engineering.summary.KeyDepths
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.validation.WellGeometryValidator
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.geology.Lithology
import com.wellsketch.engineer.core.model.provenance.DataStatus
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.TubingConfiguration
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.schematic.ElementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WellStructureEditsTest {
    private fun md(m: Double) = MeasuredDepth.rkb(Length.meters(m))
    private fun inch(v: Double) = Length.inches(v)
    private fun casing(od: Double, ppf: Double) =
        TubularCatalog.weights(TubularKind.CASING, od).single { it.nominalWeightLbFt == ppf }.toSpec("L80", "BTC")
    private fun Well.apply(edit: (Well) -> EditOutcome): Well = assertIs<EditOutcome.Applied>(edit(this)).well

    private val blank = WellProjects.newWell("Field test 1", Length.meters(11.1), Length.meters(20.0), nowMillis = 1L, id = "t1").well

    /** A full well built only with the editor operations and the catalogue. */
    private fun builtWell(): Well = blank
        .apply { WellStructureEdits.addHoleSection(it, inch(26.0), md(60.0), id = "h1") }
        .apply { WellStructureEdits.addHoleSection(it, inch(17.5), md(600.0), id = "h2") }
        .apply { WellStructureEdits.addHoleSection(it, inch(12.25), md(1950.0), id = "h3") }
        .apply { WellStructureEdits.addHoleSection(it, inch(8.5), md(2800.0), id = "h4") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.CONDUCTOR, casing(20.0, 94.0), md(11.1), md(58.0), id = "c1") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.SURFACE, casing(13.375, 54.5), md(11.1), md(595.0), id = "c2") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.INTERMEDIATE, casing(9.625, 47.0), md(11.1), md(1945.0), id = "c3") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.PRODUCTION, casing(7.0, 26.0), md(11.1), md(2795.0), id = "c4") }
        .apply { WellStructureEdits.setCement(it, "c2", md(11.1)) }
        .apply { WellStructureEdits.setCement(it, "c3", md(800.0)) }
        .apply { WellStructureEdits.setCement(it, "c4", md(1800.0)) }
        .apply { WellStructureEdits.setFloatCollar(it, "c4", md(2770.0)) }
        .apply {
            val tubing = TubularCatalog.weights(TubularKind.TUBING, 3.5).first { e -> e.nominalWeightLbFt == 9.2 }.toSpec("L80", null)
            WellStructureEdits.setTubing(it, CompletionState.CURRENT, tubing, md(11.1), md(2600.0))
        }
        .apply { WellStructureEdits.addFormation(it, "Formation 2", Lithology.SHALE, TrueVerticalDepth.rkb(Length.meters(900.0))) }

    @Test
    fun newWellStartsEmptyApartFromDatumsAndOneFormationAtGround() {
        val p = WellProjects.newWell("  My well ", Length.meters(11.1), Length.meters(-3.0), nowMillis = 42L, id = "x")
        assertEquals("My well", p.name)
        assertTrue(!p.isSyntheticDemo)
        assertEquals(DataStatus.USER_ENTERED, p.well.provenance.status)
        assertEquals(8.1, p.well.datums.rkbElevation.meters, 1e-12)
        assertEquals(-3.0, p.well.datums.groundLevelElevation.meters, 1e-12)
        assertEquals(11.1, WellStructureEdits.groundLevelMd(p.well).value.meters, 1e-12)
        assertEquals(1, p.well.formations.size)
        assertEquals(11.1, p.well.formations.single().top.value.meters, 1e-12)
        assertTrue(p.well.holeSections.isEmpty() && p.well.casingStrings.isEmpty() && p.well.completions.isEmpty())
        assertTrue(p.ctOperations.isEmpty())
    }

    @Test
    fun blankWellOpensInTheDesignScreenWithoutErrors() {
        val s = WellDesignSession().snapshot(blank, CompletionState.CURRENT, null)
        assertTrue(s.scene.elements.none { it.kind == ElementKind.CASING_WALL })
        assertTrue(KeyDepths.build(blank, CompletionState.CURRENT).none { it.kind == KeyDepthKind.CASING_SHOE })
    }

    @Test
    fun wellBuiltWithTheEditorIsCompleteAndValid() {
        val w = builtWell()
        assertEquals(listOf(11.1, 60.0, 600.0, 1950.0), w.holeSections.map { it.top.value.meters })
        assertEquals(listOf("26 in hole", "17.5 in hole", "12.25 in hole", "8.5 in hole"), w.holeSections.map { it.name })
        assertEquals(listOf("Conductor", "Surface casing", "Intermediate casing", "Production casing"), w.casingStrings.map { it.name })
        val production = w.casingStrings.single { it.id == "c4" }
        assertEquals(6.2764, production.spec.innerDiameter.inches, 1e-9)
        assertEquals("L80", production.spec.grade)
        assertEquals(2770.0, production.shoe.floatCollarDepth!!.value.meters, 1e-12)
        assertEquals(1800.0, production.cement!!.top.value.meters, 1e-12)
        assertEquals(2795.0, production.cement!!.bottom.value.meters, 1e-12)
        val tubing = w.completion(CompletionState.CURRENT)!!.tubingString!!
        assertEquals(2.9921, tubing.spec.innerDiameter.inches, 1e-9)
        assertEquals("NU", tubing.spec.connection)

        val ctx = WellGeometryContext(w)
        val errors = WellGeometryValidator().validate(ctx, w.completion(CompletionState.CURRENT)).filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), errors.toString())
        val scene = WellDesignSession().snapshot(w, CompletionState.CURRENT, null).scene
        assertEquals(4, scene.elements.filter { it.kind == ElementKind.CASING_WALL }.map { it.sourceId }.distinct().size)
        val keys = KeyDepths.build(w, CompletionState.CURRENT)
        assertEquals(listOf(58.0, 595.0, 1945.0, 2795.0), keys.filter { it.kind == KeyDepthKind.CASING_SHOE }.map { it.md.meters })
        assertEquals(2800.0, keys.last().md.meters, 1e-9)
    }

    @Test
    fun stringTypeCanBeChangedAndDefaultNamesFollow() {
        val w = builtWell()
            .apply { WellStructureEdits.setCasingRole(it, "c4", CasingRole.PRODUCTION_LINER) }
            .apply { WellEdits.setCasingTop(it, "c4", md(1800.0)) }
            .apply { WellStructureEdits.setCasingName(it, "c3", "9-5/8 in intermediate") }
            .apply { WellStructureEdits.setCasingRole(it, "c3", CasingRole.INTERMEDIATE_LINER) }
        val liner = w.casingStrings.single { it.id == "c4" }
        assertTrue(liner.isLiner)
        assertEquals("Production liner", liner.name)
        assertEquals("9-5/8 in intermediate", w.casingStrings.single { it.id == "c3" }.name) // custom name kept
    }

    @Test
    fun catalogueSizeChangeReplacesTheWholePipeBody() {
        val w = builtWell().apply { WellStructureEdits.setCasingSpec(it, "c3", casing(9.625, 53.5)) }
        val s = w.casingStrings.single { it.id == "c3" }
        assertEquals(8.5354, s.spec.innerDiameter.inches, 1e-9)
        assertEquals(53.5, s.spec.nominalLinearMass!!.poundsPerFoot, 1e-9)
    }

    @Test
    fun holeEditsKeepTheHoleContinuous() {
        val w = builtWell()
        val moved = w.apply { WellEdits.setHoleSectionBottom(it, "h2", md(650.0)) }
        assertEquals(650.0, moved.holeSections.single { it.id == "h3" }.top.value.meters, 1e-12)
        val removedMiddle = w.apply { WellStructureEdits.removeHoleSection(it, "h3") }
        assertEquals(listOf("h1", "h2", "h4"), removedMiddle.holeSections.map { it.id })
        assertEquals(1950.0, removedMiddle.holeSections.single { it.id == "h2" }.bottom.value.meters, 1e-12)
        val removedFirst = w.apply { WellStructureEdits.removeHoleSection(it, "h1") }
        assertEquals(11.1, removedFirst.holeSections.first().top.value.meters, 1e-12)
        val resized = w.apply { WellStructureEdits.setHoleBitSize(it, "h4", inch(8.75)) }
        assertEquals("8.75 in hole", resized.holeSections.single { it.id == "h4" }.name)
    }

    @Test
    fun invalidInputsAreRejectedAndLeaveTheWellUnchanged() {
        val w = builtWell()
        assertIs<EditOutcome.Rejected>(WellStructureEdits.addHoleSection(w, inch(6.0), md(2700.0)))   // above current bottom
        assertIs<EditOutcome.Rejected>(WellStructureEdits.addHoleSection(w, inch(0.0), md(3000.0)))
        assertIs<EditOutcome.Rejected>(WellStructureEdits.addCasingString(w, CasingRole.SURFACE, casing(13.375, 48.0), md(500.0), md(400.0)))
        assertIs<EditOutcome.Rejected>(WellStructureEdits.setFloatCollar(w, "c4", md(2800.0)))
        assertIs<EditOutcome.Rejected>(WellStructureEdits.setCement(w, "c4", md(2795.0)))
        assertIs<EditOutcome.Rejected>(WellStructureEdits.setCasingName(w, "c4", "  "))
        assertIs<EditOutcome.Rejected>(WellStructureEdits.removeCasingString(w, "nope"))
        assertIs<EditOutcome.Rejected>(WellStructureEdits.addFormation(w, "", Lithology.SHALE, TrueVerticalDepth.rkb(Length.meters(1.0))))
        assertIs<EditOutcome.Rejected>(WellStructureEdits.setTubing(w, CompletionState.CURRENT, casing(7.0, 26.0), md(100.0), md(50.0)))
    }

    @Test
    fun cementAndStringsCanBeRemoved() {
        val w = builtWell()
            .apply { WellStructureEdits.setCement(it, "c3", null) }
            .apply { WellStructureEdits.removeCasingString(it, "c1") }
        assertNull(w.casingStrings.single { it.id == "c3" }.cement)
        assertEquals(listOf("c2", "c3", "c4"), w.casingStrings.map { it.id })
    }

    @Test
    fun tubingCanBeReplacedOrRemoved() {
        val w = builtWell()
        val bigger = TubularCatalog.weights(TubularKind.TUBING, 4.5).first().toSpec(null, null)
        val replaced = w.apply { WellStructureEdits.setTubingSpec(it, CompletionState.CURRENT, bigger) }
        assertEquals(4.5, replaced.completion(CompletionState.CURRENT)!!.tubingString!!.spec.outerDiameter.inches, 1e-9)
        val none = w.apply { WellStructureEdits.removeTubing(it, CompletionState.CURRENT) }
        assertIs<TubingConfiguration.ExplicitlyNone>(none.completion(CompletionState.CURRENT)!!.tubing)
        // setTubing on a state without a completion creates it
        val designed = w.apply { WellStructureEdits.setTubing(it, CompletionState.AS_DESIGNED, bigger, md(11.1), md(2500.0)) }
        assertEquals(2, designed.completions.size)
    }

    @Test
    fun formationsStaySortedAndCanBeEdited() {
        val w = builtWell()
            .apply { WellStructureEdits.addFormation(it, "Reservoir", Lithology.SANDSTONE, TrueVerticalDepth.rkb(Length.meters(2500.0)), id = "fr") }
            .apply { WellStructureEdits.setFormationLithology(it, "fr", Lithology.LIMESTONE) }
            .apply { WellStructureEdits.setFormationName(it, "fr", "Target") }
        assertEquals(listOf("Formation 1", "Formation 2", "Target"), w.formations.map { it.name })
        assertEquals(Lithology.LIMESTONE, w.formations.last().lithology)
        assertEquals(2, w.apply { WellStructureEdits.removeFormation(it, "fr") }.formations.size)
    }

    @Test
    fun duplicateIsAnIndependentUserProject() {
        val demo = com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects.vertical()
        val copy = WellProjects.duplicate(demo, nowMillis = 7L, id = "d1")
        assertEquals("project-d1", copy.id)
        assertEquals("${demo.name} (copy)", copy.name)
        assertTrue(!copy.isSyntheticDemo)
        assertEquals(demo.well.casingStrings, copy.well.casingStrings)
        assertTrue(copy.well.id != demo.well.id)
    }
}
