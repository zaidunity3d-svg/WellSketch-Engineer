package com.wellsketch.engineer.domain.workover

import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.catalog.TubularKind
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.surface.DrillingBopComponent
import com.wellsketch.engineer.core.model.surface.DrillingBopComponentType
import com.wellsketch.engineer.core.model.surface.DrillingBopStack
import com.wellsketch.engineer.core.model.surface.EndConnection
import com.wellsketch.engineer.core.model.surface.RamType
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.TubularSpec
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.model.workover.WorkString
import com.wellsketch.engineer.core.model.workover.WorkStringPipeKind
import com.wellsketch.engineer.core.model.workover.WorkStringSection
import com.wellsketch.engineer.core.model.workover.WorkoverOperation
import com.wellsketch.engineer.core.model.workover.WorkoverOperationType
import com.wellsketch.engineer.core.model.workover.WorkoverRig
import com.wellsketch.engineer.core.model.workover.WorkoverRigUp
import com.wellsketch.engineer.core.model.workover.WorkoverTool
import com.wellsketch.engineer.core.model.workover.WorkoverToolType

/**
 * Pure edits of workover jobs. The user controls every value; [newJob] only fills starting
 * values that make a valid picture (workover RKB = the drilling RKB height until entered,
 * a catalogue string that passes the casing, bottom above the perforations).
 */
object WorkoverEdits {
    private fun user() = DataProvenance.userEntered("Entered in the workover editor")

    /** A new job of [type] on [well]; every value is a starting point for the user. */
    fun newJob(well: Well, type: WorkoverOperationType, id: String): WorkoverOperation {
        val ctx = WellGeometryContext(well)
        val rkb = well.datums.rkbAboveGroundLevel
        val datum = DepthReference.Operation("$id-rkb", "Workover RKB", rkb)
        fun opDepth(canonical: Length) = Length(canonical.meters - (ctx.canonical(MeasuredDepth(Length.ZERO, datum)).meters))
        // Narrowest drift / ID of casing and liners: the default string must pass it with its tool joint.
        val narrowest = well.casingStrings.minOfOrNull { (it.spec.driftDiameter ?: it.spec.innerDiameter).inches } ?: Double.MAX_VALUE
        val dp = TubularCatalog.entries
            .filter { it.kind == TubularKind.DRILL_PIPE && (it.toolJointOdIn ?: it.outerDiameterIn) < narrowest - 0.25 }
            .maxByOrNull { it.outerDiameterIn * 100 + it.nominalWeightLbFt / 100 }
        val tubing = TubularCatalog.weights(TubularKind.TUBING, 2.875).first()
        val section = if (dp != null) {
            WorkStringSection("$id-s1", WorkStringPipeKind.DRILL_PIPE, "Drill pipe", dp.toSpec(null, null), Length.ZERO, dp.toolJointOdIn?.let(Length::inches))
        } else {
            WorkStringSection("$id-s1", WorkStringPipeKind.TUBING, "Tubing", tubing.toSpec(null, null), Length.ZERO)
        }
        val perfs = well.completion(CompletionState.CURRENT)?.perforations.orEmpty()
        val perfTop = perfs.minOfOrNull { ctx.canonical(it.top) }
        val perfBottom = perfs.maxOfOrNull { ctx.canonical(it.bottom) }
        val deepest = (well.casingStrings.map { ctx.canonical(it.shoe.depth) } + well.holeSections.map { ctx.canonical(it.bottom) }).maxOrNull()
            ?: (ctx.groundLevelCanonicalMd + Length.meters(1000.0))
        val bottom = when {
            perfTop != null && type == WorkoverOperationType.PERFORATION -> perfBottom!!
            perfTop != null -> Length.meters(maxOf(perfTop.meters - 30.0, ctx.groundLevelCanonicalMd.meters + 10.0))
            else -> Length.meters(maxOf(deepest.meters - 50.0, ctx.groundLevelCanonicalMd.meters + 10.0))
        }
        return WorkoverOperation(
            id = id, name = type.label, type = type, depthReference = datum,
            bottomDepth = opDepth(bottom),
            workString = WorkString("$id-ws", "Work string", listOf(section), user()),
            rigUp = WorkoverRigUp(WorkoverRig("$id-rig", "Workover rig", provenance = user()), DrillingBopStack("$id-bop", "Workover BOP", emptyList(), user())),
            targetTop = perfTop?.let(::opDepth), targetBottom = perfBottom?.let(::opDepth),
            plugLength = if (type == WorkoverOperationType.CEMENT_PLUG) Length.meters(100.0) else null,
            provenance = user(),
        )
    }

    // --- Project level ------------------------------------------------------------------------

    fun addJob(project: WellProject, job: WorkoverOperation): WellProject = project.copy(workoverOperations = project.workoverOperations + job)

    fun removeJob(project: WellProject, jobId: String): WellProject =
        project.copy(workoverOperations = project.workoverOperations.filterNot { it.id == jobId })

    fun replaceJob(project: WellProject, job: WorkoverOperation): WellProject =
        project.copy(workoverOperations = project.workoverOperations.map { if (it.id == job.id) job else it })

    // --- Datum ---------------------------------------------------------------------------------

    /**
     * Redefines the workover RKB height above GL. The string stays where it is in the well:
     * every depth entered from this datum is shifted by the change of height.
     */
    fun setRkbHeight(op: WorkoverOperation, heightAboveGl: Length): WorkoverOperation {
        val datum = op.depthReference as? DepthReference.Operation ?: return op
        val shift = heightAboveGl - datum.heightAboveGroundLevel
        return op.copy(
            depthReference = datum.copy(heightAboveGroundLevel = heightAboveGl),
            bottomDepth = op.bottomDepth + shift,
            targetTop = op.targetTop?.let { it + shift },
            targetBottom = op.targetBottom?.let { it + shift },
        )
    }

    // --- Work string ---------------------------------------------------------------------------

    fun addSection(op: WorkoverOperation, kind: WorkStringPipeKind, id: String): WorkoverOperation {
        val last = op.workString.sections.lastOrNull()
        val catalogueKind = if (kind == WorkStringPipeKind.DRILL_PIPE) TubularKind.DRILL_PIPE else TubularKind.TUBING
        // Next section below: one size smaller than the last one when the catalogue has it.
        val ods = TubularCatalog.outerDiameters(catalogueKind)
        val od = last?.spec?.outerDiameter?.inches?.let { lastOd -> ods.firstOrNull { it < lastOd - 1e-6 } } ?: ods.last()
        val entry = TubularCatalog.weights(catalogueKind, od).first()
        val section = WorkStringSection(id, kind, kind.label, entry.toSpec(null, null), Length.meters(300.0), entry.toolJointOdIn?.let(Length::inches))
        return withSections(op, op.workString.sections + section)
    }

    fun removeSection(op: WorkoverOperation, sectionId: String): WorkoverOperation =
        withSections(op, op.workString.sections.filterNot { it.id == sectionId })

    fun setSectionKind(op: WorkoverOperation, sectionId: String, kind: WorkStringPipeKind): WorkoverOperation = updateSection(op, sectionId) { s ->
        if (s.kind == kind) return@updateSection s
        val catalogueKind = if (kind == WorkStringPipeKind.DRILL_PIPE) TubularKind.DRILL_PIPE else TubularKind.TUBING
        val ods = TubularCatalog.outerDiameters(catalogueKind)
        val od = ods.minByOrNull { kotlin.math.abs(it - s.spec.outerDiameter.inches) }!!
        val entry = TubularCatalog.weights(catalogueKind, od).first()
        s.copy(kind = kind, name = if (s.name == s.kind.label) kind.label else s.name, spec = entry.toSpec(null, null), connectionOuterDiameter = entry.toolJointOdIn?.let(Length::inches))
    }

    /** New pipe for a section; the tool-joint OD follows the catalogue unless the user typed one. */
    fun setSectionSpec(op: WorkoverOperation, sectionId: String, spec: TubularSpec): WorkoverOperation = updateSection(op, sectionId) { s ->
        val oldEntry = TubularCatalog.byId(s.spec.catalogItemId)
        val newEntry = TubularCatalog.byId(spec.catalogItemId)
        val joint = if (s.connectionOuterDiameter == null || s.connectionOuterDiameter == oldEntry?.toolJointOdIn?.let(Length::inches)) {
            newEntry?.toolJointOdIn?.let(Length::inches)
        } else {
            s.connectionOuterDiameter
        }
        s.copy(spec = spec, connectionOuterDiameter = joint)
    }

    fun setSectionLength(op: WorkoverOperation, sectionId: String, length: Length): WorkoverOperation =
        updateSection(op, sectionId) { it.copy(length = Length(maxOf(0.0, length.meters))) }

    fun setSectionName(op: WorkoverOperation, sectionId: String, name: String): WorkoverOperation = updateSection(op, sectionId) { it.copy(name = name) }

    fun setSectionConnectionOd(op: WorkoverOperation, sectionId: String, od: Length?): WorkoverOperation =
        updateSection(op, sectionId) { it.copy(connectionOuterDiameter = od?.takeIf { d -> d.meters > 0.0 }) }

    // --- Tools ---------------------------------------------------------------------------------

    /** Adds a tool at the bottom; its size is entered by the user (starts at the string OD, 1 m long). */
    fun addTool(op: WorkoverOperation, type: WorkoverToolType, id: String): WorkoverOperation {
        val od = op.workString.sections.lastOrNull()?.spec?.outerDiameter ?: Length.inches(3.5)
        val tool = WorkoverTool(id, type, type.label, Length.meters(1.0), od, null, provenance = user())
        return op.copy(tools = op.tools + tool)
    }

    fun removeTool(op: WorkoverOperation, toolId: String) = op.copy(tools = op.tools.filterNot { it.id == toolId })

    fun moveTool(op: WorkoverOperation, toolId: String, up: Boolean): WorkoverOperation {
        val i = op.tools.indexOfFirst { it.id == toolId }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in op.tools.indices) return op
        val list = op.tools.toMutableList()
        list[i] = list[j].also { list[j] = list[i] }
        return op.copy(tools = list)
    }

    fun updateTool(op: WorkoverOperation, toolId: String, transform: (WorkoverTool) -> WorkoverTool): WorkoverOperation =
        op.copy(tools = op.tools.map { if (it.id == toolId) transform(it) else it })

    // --- Workover BOP --------------------------------------------------------------------------

    /**
     * Adds a BOP component at the bottom of the stack. Bore and height are entered by the user
     * (start: the wellhead top bore if recorded, else unset at 0); no rating is assumed.
     * Connections take the wellhead top connection designation so the stack-up matches until
     * the user edits them.
     */
    fun addBopComponent(op: WorkoverOperation, well: Well, type: DrillingBopComponentType, id: String): WorkoverOperation {
        val top = well.wellhead?.sections?.firstOrNull()
        val conn = EndConnection(top?.topConnection?.designation ?: "Not entered")
        val c = DrillingBopComponent(
            id = id, type = type, name = type.label, bore = top?.bore ?: Length.ZERO, ratedWorkingPressure = null,
            topConnection = conn, bottomConnection = conn, height = Length.meters(1.0),
            rams = when (type) {
                DrillingBopComponentType.SINGLE_RAM_PREVENTER -> listOf(RamType.PIPE)
                DrillingBopComponentType.DOUBLE_RAM_PREVENTER -> listOf(RamType.PIPE, RamType.BLIND)
                else -> emptyList()
            },
            provenance = user(),
        )
        val stack = op.rigUp.bopStack
        return op.copy(rigUp = op.rigUp.copy(bopStack = stack.copy(components = stack.components + c)))
    }

    fun removeBopComponent(op: WorkoverOperation, componentId: String): WorkoverOperation {
        val stack = op.rigUp.bopStack
        return op.copy(rigUp = op.rigUp.copy(bopStack = stack.copy(components = stack.components.filterNot { it.id == componentId })))
    }

    fun updateBopComponent(op: WorkoverOperation, componentId: String, transform: (DrillingBopComponent) -> DrillingBopComponent): WorkoverOperation {
        val stack = op.rigUp.bopStack
        return op.copy(rigUp = op.rigUp.copy(bopStack = stack.copy(components = stack.components.map { if (it.id == componentId) transform(it) else it })))
    }

    fun setRigFloorBelowRkb(op: WorkoverOperation, floorBelowRkb: Length?): WorkoverOperation =
        op.copy(rigUp = op.rigUp.copy(rig = op.rigUp.rig.copy(floorBelowRkb = floorBelowRkb)))

    // --- helpers -------------------------------------------------------------------------------

    private fun withSections(op: WorkoverOperation, sections: List<WorkStringSection>) =
        op.copy(workString = op.workString.copy(sections = sections))

    private fun updateSection(op: WorkoverOperation, sectionId: String, transform: (WorkStringSection) -> WorkStringSection) =
        withSections(op, op.workString.sections.map { if (it.id == sectionId) transform(it) else it })
}
