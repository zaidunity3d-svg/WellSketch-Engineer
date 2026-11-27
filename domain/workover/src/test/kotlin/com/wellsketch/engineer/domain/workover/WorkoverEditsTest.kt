package com.wellsketch.engineer.domain.workover

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.surface.DrillingBopComponentType
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.workover.WorkStringPipeKind
import com.wellsketch.engineer.core.model.workover.WorkoverOperationType
import com.wellsketch.engineer.core.model.workover.WorkoverToolType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkoverEditsTest {
    private val project = SyntheticDemoProjects.vertical()
    private val well = project.well
    private val calc = WorkoverCalculator()

    @Test
    fun newJobStartsAboveThePerforationsWithAStringThatPassesTheCasing() {
        for (type in WorkoverOperationType.entries) {
            val job = WorkoverEdits.newJob(well, type, "j-${type.name}")
            val a = calc.analyze(WorkoverJob.withTubingPulled(well), job)
            assertTrue(a.issues.none { it.severity == Severity.ERROR && it.code == "WO_CLEARANCE" }, "$type: ${a.issues}")
            assertTrue(a.issues.none { it.code == "WO_STRING_TOO_LONG" || it.code == "WO_BELOW_TD" })
            // the workover RKB starts at the drilling RKB height until the user enters it
            assertEquals(well.datums.rkbAboveGroundLevel, (job.depthReference as DepthReference.Operation).heightAboveGroundLevel)
            // targets default to the current perforations (2300–2355 m MD)
            assertEquals(2300.0, a.bottomMd.meters.let { if (type == WorkoverOperationType.PERFORATION) 2300.0 else it + 30.0 }, 1e-9)
        }
        val dp = WorkoverEdits.newJob(well, WorkoverOperationType.CIRCULATION, "j").workString.sections.single()
        assertEquals(WorkStringPipeKind.DRILL_PIPE, dp.kind)
        assertEquals(TubularCatalog.byId(dp.spec.catalogItemId)!!.toolJointOdIn!!, dp.connectionOuterDiameter!!.inches, 1e-12)
    }

    @Test
    fun changingTheWorkoverRkbKeepsTheStringInPlace() {
        val job = WorkoverEdits.newJob(well, WorkoverOperationType.ACIDIZING, "j")
        val before = calc.analyze(well, job)
        val moved = WorkoverEdits.setRkbHeight(job, Length.meters(4.0))
        val after = calc.analyze(well, moved)
        assertEquals(before.bottomMd.meters, after.bottomMd.meters, 1e-9)
        assertEquals(job.bottomDepth.meters + (4.0 - well.datums.rkbAboveGroundLevel.meters), moved.bottomDepth.meters, 1e-9)
        // rig floor follows the new height: 11.1 − 4.0 m below the drilling RKB
        assertEquals(well.datums.rkbAboveGroundLevel.meters - 4.0, after.rigFloorMd.meters, 1e-9)
    }

    @Test
    fun sectionsToolsAndBopAreEditable() {
        var job = WorkoverEdits.newJob(well, WorkoverOperationType.SET_PACKER, "j")
        val topOd = job.workString.sections.single().spec.outerDiameter
        job = WorkoverEdits.addSection(job, WorkStringPipeKind.DRILL_PIPE, "s2")
        val second = job.workString.sections[1]
        assertTrue(second.spec.outerDiameter < topOd, "tapered: the lower section is smaller")
        job = WorkoverEdits.setSectionKind(job, "s2", WorkStringPipeKind.TUBING)
        assertEquals(WorkStringPipeKind.TUBING, job.workString.sections[1].kind)
        assertEquals(null, job.workString.sections[1].connectionOuterDiameter)
        // a typed tool-joint OD survives a size change; a catalogue one follows the size
        val first = job.workString.sections[0]
        val bigger = TubularCatalog.byId("dp-3p5-13p3-eu")!!.toSpec(null, null)
        val followed = WorkoverEdits.setSectionSpec(job, first.id, bigger)
        assertEquals(4.75, followed.workString.sections[0].connectionOuterDiameter!!.inches, 1e-12)
        val typed = WorkoverEdits.setSectionConnectionOd(job, first.id, Length.inches(5.5))
        assertEquals(5.5, WorkoverEdits.setSectionSpec(typed, first.id, bigger).workString.sections[0].connectionOuterDiameter!!.inches, 1e-12)

        job = WorkoverEdits.addTool(job, WorkoverToolType.PACKER, "pk")
        job = WorkoverEdits.addTool(job, WorkoverToolType.CEMENT_STINGER, "st")
        job = WorkoverEdits.moveTool(job, "st", up = true)
        assertEquals(listOf("st", "pk"), job.tools.map { it.id })
        job = WorkoverEdits.updateTool(job, "pk") { it.copy(maxOuterDiameter = Length.inches(5.8), length = Length.meters(2.0)) }
        val packer = calc.analyze(well, job).packer!!
        assertEquals(2.0, (packer.packerBottom - packer.packerTop).meters, 1e-9)

        job = WorkoverEdits.addBopComponent(job, well, DrillingBopComponentType.DOUBLE_RAM_PREVENTER, "dr")
        assertEquals(well.wellhead!!.sections.first().topConnection, job.rigUp.bopStack.components.single().bottomConnection)
        job = WorkoverEdits.removeBopComponent(job, "dr")
        assertTrue(job.rigUp.bopStack.components.isEmpty())

        val p = WorkoverEdits.addJob(project, job)
        assertEquals(2, p.workoverOperations.size)
        assertEquals(1, WorkoverEdits.removeJob(p, job.id).workoverOperations.size)
    }
}
