package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.summary.KeyDepths
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.validation.WellGeometryValidator
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.schematic.SketchLabels
import com.wellsketch.engineer.core.schematic.SurfaceBlockKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * End-to-end audit of what the Well Design screen shows for every synthetic demo well, in
 * every completion state and surface mode: the outputs must be complete, consistent and
 * free of errors, so a demo never shows a broken or contradictory picture.
 */
class WellDesignOutcomeAuditTest {
    private val session = WellDesignSession()
    private val treeKinds = setOf(SurfaceBlockKind.TREE_VALVE, SurfaceBlockKind.TREE_CAP)

    private fun List<ValidationIssue>.errors() = filter { it.severity == Severity.ERROR }

    @Test
    fun everyDemoWellStateAndModeGivesAConsistentErrorFreePicture() {
        for (project in SyntheticDemoProjects.all()) {
            val well = project.well
            val ctx = WellGeometryContext(well)
            val ctRigUp = project.ctOperations.firstOrNull()?.rigUp
            val states = CompletionState.entries.filter { well.completion(it) != null }
            assertTrue(states.isNotEmpty(), "${project.id}: no completion")
            for (state in states) {
                val geometryErrors = WellGeometryValidator().validate(ctx, well.completion(state)).errors()
                if (geometryErrors.isNotEmpty()) fail("${project.id} $state geometry: $geometryErrors")
                val modes = session.snapshot(well, state, null, ctRigUp = ctRigUp).availableSurfaceModes
                assertEquals(SurfaceMode.entries.toList(), modes, "${project.id}: every demo offers every mode")
                for (mode in modes) {
                    val where = "${project.id} $state $mode"
                    val s = session.snapshot(well, state, null, mode, ctRigUp)
                    assertEquals(mode, s.surfaceMode, where)
                    val stackup = s.surfaceStackup ?: fail("$where: no stack-up")
                    if (stackup.issues.errors().isNotEmpty()) fail("$where stack-up: ${stackup.issues.errors()}")
                    // every elevation the mode shows is calculated from data
                    assertTrue(stackup.wellheadTop != null && stackup.rkbToWellheadTop != null, where)
                    when (mode) {
                        SurfaceMode.DRILLING -> assertTrue(stackup.drillingStackTop != null && stackup.clearanceUnderRotaryBeams!!.meters >= 0.0, where)
                        SurfaceMode.COMPLETION -> assertTrue(stackup.treeTopWithCap != null, where)
                        SurfaceMode.COILED_TUBING -> assertTrue(stackup.ctStackTop != null, where)
                    }
                    auditScene(s, where)
                }
            }
        }
    }

    private fun auditScene(s: WellDesignSnapshot, where: String) {
        val scene = s.scene
        val kinds = scene.surfaceBlocks.map { it.kind }
        // one operating state at a time: never a rig with a tree, never a tree under a drilling BOP
        when (s.surfaceMode) {
            SurfaceMode.DRILLING -> assertTrue(kinds.none { it in treeKinds } && scene.surfaceSite.rig != null, where)
            else -> assertTrue(kinds.none { it.name.startsWith("DRILLING_") } && scene.surfaceSite.rig == null, where)
        }
        // equipment on the axis is stacked without gaps or overlaps
        val axis = scene.surfaceBlocks.filter { it.centerOffset == 0.0 }.sortedBy { it.bottomAboveGl }
        for ((a, b) in axis.zipWithNext()) {
            assertEquals(a.topAboveGl, b.bottomAboveGl, 1e-6, "$where: gap/overlap between ${a.label} and ${b.label}")
        }
        // wellhead sections shown = sections installed in this state
        val shown = scene.surfaceBlocks.filter { it.kind == SurfaceBlockKind.WELLHEAD }.map { it.sourceId }.toSet()
        assertEquals(s.surfaceStackup!!.installedWellhead!!.sections.map { it.id }.toSet(), shown, where)
        // the top wellhead block is at the calculated wellhead top
        val topBlock = scene.surfaceBlocks.filter { it.kind == SurfaceBlockKind.WELLHEAD }.maxOf { it.topAboveGl }
        assertEquals(s.surfaceStackup!!.wellheadTop!!.meters, topBlock, 1e-6, where)
        // downhole elements are well formed and inside the drawn depth range
        for (e in scene.elements) {
            assertTrue(e.mdTop.isFinite() && e.mdBottom.isFinite() && e.mdTop <= e.mdBottom + 1e-9, "$where: ${e.id} interval")
            assertTrue(e.mdBottom <= scene.totalDepthMd + 1e-6, "$where: ${e.id} below TD")
            assertTrue(e.innerRadius >= 0.0 && e.outerRadius >= e.innerRadius, "$where: ${e.id} radii")
        }
        // labels point at depths inside the well
        for (l in SketchLabels.collect(scene)) {
            assertTrue(l.md >= 0.0 && l.md <= scene.totalDepthMd + 1e-6, "$where: label ${l.kind} at ${l.md}")
        }
        // key depths: sorted, TVD never deeper than MD on the same datum
        val keys = KeyDepths.build(s.well, s.completionState.takeIf { s.showsCompletion })
        assertEquals(keys.sortedBy { it.md.meters }, keys, where)
        assertTrue(keys.all { it.tvd.meters <= it.md.meters + 1e-6 }, where)
    }
}
