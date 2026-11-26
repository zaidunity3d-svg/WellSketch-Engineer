package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.validation.WellGeometryValidator
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.TubularSpec
import com.wellsketch.engineer.core.model.well.Well
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** QA rules taken over from the HTML v0.9 `qa()` checks that the Android validator lacked. Synthetic data. */
class HtmlV09QaRulesTest {
    private fun md(m: Double) = MeasuredDepth.rkb(Length.meters(m))
    private fun Well.apply(edit: (Well) -> EditOutcome): Well = assertIs<EditOutcome.Applied>(edit(this)).well
    private fun spec(id: String, grade: String? = "L80", connection: String? = "BTC") = TubularCatalog.byId(id)!!.toSpec(grade, connection)
    private fun issues(w: Well, state: CompletionState? = null): List<ValidationIssue> =
        WellGeometryValidator().validate(WellGeometryContext(w), state?.let(w::completion))
    private fun List<ValidationIssue>.codes() = map { it.code }.toSet()

    private val base: Well = WellProjects.newWell("QA", Length.meters(11.1), Length.meters(20.0), nowMillis = 1L, id = "qa").well
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(17.5), md(600.0), id = "h1") }
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(12.25), md(1950.0), id = "h2") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.SURFACE, spec("c-13p375-54p5-api"), md(11.1), md(600.0), id = "s") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.INTERMEDIATE, spec("c-9p625-47-api"), md(11.1), md(1950.0), id = "i") }

    @Test
    fun cleanEditorWellRaisesNoNewRule() {
        val codes = issues(base).codes()
        listOf("HOLE_GAP", "HOLE_FIRST_TOP", "CASING_DRIFT", "TUBULAR_CATALOGUE_KIND", "TUBULAR_GRADE_CONNECTION").forEach {
            assertTrue(it !in codes, "unexpected $it")
        }
    }

    @Test
    fun gapBetweenHoleSectionsIsAnError() {
        val w = base.copy(holeSections = base.holeSections.map { if (it.id == "h2") it.copy(top = md(650.0)) else it })
        val gap = issues(w).single { it.code == "HOLE_GAP" }
        assertEquals(Severity.ERROR, gap.severity)
    }

    @Test
    fun firstHoleSectionBelowGroundIsAWarning() {
        val w = base.copy(holeSections = base.holeSections.map { if (it.id == "h1") it.copy(top = md(40.0)) else it })
        assertEquals(Severity.WARNING, issues(w).single { it.code == "HOLE_FIRST_TOP" }.severity)
    }

    @Test
    fun innerOdBetweenDriftAndIdIsADriftWarning() {
        // 13-3/8 in 68 lb/ft: ID 12.415 in, drift 12.2587 in. A synthetic 12.3 in pipe fits the ID but not the drift.
        val p = DataProvenance.userEntered("synthetic test pipe")
        val tight = TubularSpec(Length.inches(12.3), Length.inches(11.5), provenance = p)
        val w = base
            .apply { WellStructureEdits.setCasingSpec(it, "s", spec("c-13p375-68-api")) }
            .apply { WellStructureEdits.setCasingSpec(it, "i", tight) }
        val found = issues(w)
        assertEquals(Severity.WARNING, found.single { it.code == "CASING_DRIFT" }.severity)
        assertTrue("CASING_NESTING" !in found.codes())
    }

    @Test
    fun casingRecordUsedAsTubingIsAnError() {
        val w = base.apply { WellStructureEdits.setTubing(it, CompletionState.CURRENT, spec("c-4p5-13p5-api"), md(11.1), md(1900.0)) }
        assertEquals(Severity.ERROR, issues(w, CompletionState.CURRENT).single { it.code == "TUBULAR_CATALOGUE_KIND" }.severity)
        val ok = base.apply { WellStructureEdits.setTubing(it, CompletionState.CURRENT, spec("t-4p5-12p6-nu", connection = "NU"), md(11.1), md(1900.0)) }
        assertTrue("TUBULAR_CATALOGUE_KIND" !in issues(ok, CompletionState.CURRENT).codes())
    }

    @Test
    fun missingGradeOrConnectionIsOnlyANote() {
        val w = base.apply { WellStructureEdits.setCasingSpec(it, "i", spec("c-9p625-47-api", grade = null, connection = null)) }
        val note = issues(w).single { it.code == "TUBULAR_GRADE_CONNECTION" }
        assertEquals(Severity.INFO, note.severity)
        assertTrue(note.message.contains("grade and connection"))
    }
}
