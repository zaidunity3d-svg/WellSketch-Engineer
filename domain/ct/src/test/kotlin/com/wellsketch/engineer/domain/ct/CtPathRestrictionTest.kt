package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.surface.StackComponentType
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.schematic.CtSurfaceViewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CtPathRestrictionTest {
    private val analyzer = CtPathRestrictionAnalyzer()

    private fun analysis(projectId: String) = SyntheticDemoProjects.all().single { it.id == projectId }.let { p ->
        CtOperationAnalyzer().analyze(p.well, p.ctOperations.single(), CtSurfaceViewMode.COMPACT)
    }

    @Test
    fun everyDemoPathHasConsistentSmallestRestrictions() {
        for (p in SyntheticDemoProjects.all()) {
            val op = p.ctOperations.single()
            val a = CtOperationAnalyzer().analyze(p.well, op, CtSurfaceViewMode.COMPACT)
            val s = a.pathSummary
            val known = s.items.filter { it.diameter != null }
            assertEquals(known.minOf { it.diameter!!.meters }, s.smallestForCt!!.diameter!!.meters, 1e-12, p.id)
            assertEquals(known.filter { it.bhaPasses }.minOf { it.diameter!!.meters }, s.smallestForBha!!.diameter!!.meters, 1e-12, p.id)
            // the overall limit is never larger than the downhole-only limit
            a.passability.smallestRestriction?.innerDiameter?.let { assertTrue(s.smallestForBha!!.diameter!! <= it, p.id) }
            assertEquals(s.smallestForCt!!.diameter!!.meters - op.ctString.maxOuterDiameter.meters, s.ctDiametralClearance!!.meters, 1e-12)
            val bha = op.bha.maxOuterDiameter
            if (bha != null) assertEquals(s.smallestForBha!!.diameter!!.meters - bha.meters, s.bhaDiametralClearance!!.meters, 1e-12)
            // order: CT stack, tree, wellhead, then downhole by depth
            val parts = s.items.map { it.part.ordinal }
            assertEquals(parts.sorted(), parts, p.id)
            assertTrue(s.items.none { it.part == CtPathPart.TREE && it.name.contains("cap", ignoreCase = true) })
            // demo data are complete: nothing unknown and no errors from the path check
            assertTrue(s.issues.isEmpty(), "${p.id}: ${s.issues}")
        }
    }

    @Test
    fun wellheadIsInThePathOnlyWithoutProductionTubing() {
        val withTubing = analysis(SyntheticDemoProjects.J_SHAPED_ID).pathSummary
        assertTrue(withTubing.items.none { it.part == CtPathPart.WELLHEAD })
        val tubingless = analysis(SyntheticDemoProjects.S_SHAPED_ID).pathSummary
        assertEquals(3, tubingless.items.count { it.part == CtPathPart.WELLHEAD })
    }

    @Test
    fun stripperLimitsTheCtButNotTheBha() {
        val p = SyntheticDemoProjects.jShaped()
        val op = p.ctOperations.single()
        val a = CtOperationAnalyzer().analyze(p.well, op, CtSurfaceViewMode.COMPACT)
        val narrowStripper = op.rigUp.pressureControlStack.let { st ->
            st.copy(components = st.components.map { if (it.type == StackComponentType.STRIPPER_PACKOFF) it.copy(bore = Length.inches(2.0)) else it })
        }
        val s = analyzer.summarize(narrowStripper, p.well.christmasTree, emptyList(), a.flowPath, a.passability.restrictionsAbove, op.ctString.maxOuterDiameter, op.bha.maxOuterDiameter)
        assertEquals(StackComponentType.STRIPPER_PACKOFF.name, narrowStripper.components.single { it.id == s.smallestForCt!!.sourceId }.type.name)
        assertTrue(s.smallestForBha!!.sourceId != s.smallestForCt!!.sourceId)
        assertEquals(2.0 * 0.0254 - op.ctString.maxOuterDiameter.meters, s.ctDiametralClearance!!.meters, 1e-12)
    }

    @Test
    fun tubinglessWellheadBoresAreCheckedAgainstCtAndBha() {
        val p = SyntheticDemoProjects.sShaped()
        val op = p.ctOperations.single()
        val a = CtOperationAnalyzer().analyze(p.well, op, CtSurfaceViewMode.COMPACT)
        val sections = p.well.wellhead!!.sections
        fun run(bores: List<Length?>, bhaOd: Length? = op.bha.maxOuterDiameter) = analyzer.summarize(
            op.rigUp.pressureControlStack, p.well.christmasTree, sections.zip(bores) { s, b -> s.copy(bore = b) },
            a.flowPath, a.passability.restrictionsAbove, op.ctString.maxOuterDiameter, bhaOd,
        )
        val unknown = run(listOf(null, Length.inches(11.0), Length.inches(13.625)))
        val w = unknown.issues.single()
        assertEquals("CT_WELLHEAD_BORE_UNKNOWN", w.code)
        assertEquals(Severity.WARNING, w.severity)

        val bhaOd = assertNotNull(op.bha.maxOuterDiameter)
        val tightForBha = run(listOf(bhaOd, Length.inches(11.0), Length.inches(13.625)))
        assertEquals(listOf("CT_WELLHEAD_BORE_BHA"), tightForBha.issues.map { it.code })
        assertEquals(sections.first().id, tightForBha.smallestForBha!!.sourceId)
        assertEquals(0.0, tightForBha.bhaDiametralClearance!!.meters, 1e-12)

        val tightForCt = run(listOf(op.ctString.maxOuterDiameter, Length.inches(11.0), Length.inches(13.625)))
        assertEquals(listOf("CT_WELLHEAD_BORE_CT"), tightForCt.issues.map { it.code })
        assertTrue(tightForCt.issues.all { it.severity == Severity.ERROR })

        val noBha = run(listOf(Length.inches(7.0625), Length.inches(11.0), Length.inches(13.625)), bhaOd = null)
        assertNull(noBha.bhaDiametralClearance)
    }

    @Test
    fun noTreeMeansNoSurfacePath() {
        val p = SyntheticDemoProjects.vertical()
        val op = p.ctOperations.single()
        val a = analysis(SyntheticDemoProjects.VERTICAL_ID)
        val s = analyzer.summarize(null, null, p.well.wellhead!!.sections, a.flowPath, a.passability.restrictionsAbove, op.ctString.maxOuterDiameter, op.bha.maxOuterDiameter)
        assertTrue(s.items.all { it.part == CtPathPart.DOWNHOLE })
    }
}
