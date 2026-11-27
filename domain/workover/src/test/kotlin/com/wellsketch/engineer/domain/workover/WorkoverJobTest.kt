package com.wellsketch.engineer.domain.workover

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.surface.SurfaceStackupCalculator
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.schematic.ElementKind
import com.wellsketch.engineer.core.schematic.SketchLabelKind
import com.wellsketch.engineer.core.schematic.SketchLabels
import com.wellsketch.engineer.core.schematic.SurfaceBlockKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Workover picture of the synthetic vertical demo (tubing work string + packer, acid job). */
class WorkoverJobTest {
    private val project = SyntheticDemoProjects.vertical()
    private val op = project.workoverOperations.single()
    private val job = WorkoverJob().run(project.well, op)

    @Test
    fun productionTubingIsPulledAndPerforationsStay() {
        val c = job.workoverWell.completion(CompletionState.CURRENT)!!
        assertNull(c.tubingString)
        assertEquals(project.well.completion(CompletionState.CURRENT)!!.perforations, c.perforations)
        assertTrue(job.scene.elements.none { it.kind == ElementKind.TUBING_WALL })
        assertTrue(job.scene.elements.any { it.kind == ElementKind.PERFORATION })
    }

    @Test
    fun workStringAndToolsAreDrawnBelowGround() {
        val string = job.scene.elements.single { it.kind == ElementKind.WORK_STRING_WALL }
        val packer = job.scene.elements.single { it.kind == ElementKind.WORKOVER_TOOL }
        assertEquals(job.scene.groundLevelMd, string.mdTop, 1e-9)
        assertEquals(2250.0, packer.mdTop, 1e-9) // workover depth + 6.1 m
        assertEquals(2252.0, packer.mdBottom, 1e-9)
        assertTrue(SketchLabels.collect(job.scene).any { it.kind == SketchLabelKind.PACKER && it.md == 2250.0 })
    }

    @Test
    fun treeIsReplacedByTheWorkoverBopAndRigFloorAtTheWorkoverRkb() {
        val kinds = job.scene.surfaceBlocks.map { it.kind }.toSet()
        assertTrue(SurfaceBlockKind.DRILLING_ANNULAR in kinds && SurfaceBlockKind.DRILLING_RAM in kinds)
        assertTrue(kinds.none { it.name.startsWith("TREE") }, "no tree in the workover state: $kinds")
        val rig = job.scene.surfaceSite.rig!!
        assertEquals(5.0, rig.rkbAboveGl, 1e-9)
        val woRkb = (op.depthReference as DepthReference.Operation).heightAboveGroundLevel
        val stackup = SurfaceStackupCalculator().calculate(job.workoverWell, OperatingState.Workover(project.well.drillingRigUp!!.copy(bopStack = op.rigUp.bopStack), woRkb))
        assertEquals(5.0, stackup.drillFloor!!.meters, 1e-9)
        // every wellhead section stays on a completed well
        assertEquals(project.well.wellhead!!.sections.size, stackup.installedWellhead!!.sections.size)
    }

    @Test
    fun demoJobHasNoErrors() {
        assertTrue(job.issues.none { it.severity == Severity.ERROR }, "${job.issues}")
        val acid = job.analysis.acid!!
        assertEquals(5.5, acid.treatmentVolume.cubicMeters, 1e-9) // 0.1 m3/m × 55 m
    }
}
