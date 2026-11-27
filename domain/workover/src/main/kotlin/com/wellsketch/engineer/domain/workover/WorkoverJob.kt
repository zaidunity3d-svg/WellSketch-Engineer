package com.wellsketch.engineer.domain.workover

import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.surface.DrillingRig
import com.wellsketch.engineer.core.model.surface.DrillingRigUp
import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.ComponentMounting
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.TubingConfiguration
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.model.workover.WorkoverOperation
import com.wellsketch.engineer.core.schematic.ElementDetail
import com.wellsketch.engineer.core.schematic.ElementKind
import com.wellsketch.engineer.core.schematic.SchematicElement
import com.wellsketch.engineer.core.schematic.SchematicScene
import com.wellsketch.engineer.core.schematic.WellSchematicBuilder

/** Calculations plus the workover sketch for one job. */
data class WorkoverJobResult(
    val analysis: WorkoverAnalysis,
    val scene: SchematicScene,
    val workoverWell: Well,
    val issues: List<ValidationIssue>,
)

/**
 * Builds the workover picture of a completed well: the current completion with the production
 * tubing and the tubing-mounted equipment pulled (equipment set in the casing / liner and the
 * perforations stay), the Christmas Tree replaced by the workover BOP and rig floor, and the
 * work string and tools at their depth.
 */
class WorkoverJob(
    private val calculator: WorkoverCalculator = WorkoverCalculator(),
    private val schematicBuilder: WellSchematicBuilder = WellSchematicBuilder(),
) {

    fun run(well: Well, op: WorkoverOperation, baseState: CompletionState = CompletionState.CURRENT): WorkoverJobResult {
        val woWell = withTubingPulled(well, baseState)
        val analysis = calculator.analyze(woWell, op)
        val issues = analysis.issues.toMutableList()

        val rkb = (op.depthReference as? DepthReference.Operation)?.heightAboveGroundLevel
        if (rkb == null) {
            issues += ValidationIssue(
                Severity.WARNING, "WO_DATUM_NOT_RIG",
                "Depths are entered from ${op.depthReference.shortLabel}, not from the workover rig's own RKB; the rig floor is drawn at the drilling RKB.",
            )
        }
        val state = OperatingState.Workover(drawingRigUp(op), rkb ?: well.datums.rkbAboveGroundLevel)
        val base = schematicBuilder.build(woWell, baseState, state)
        val glMd = base.groundLevelMd
        val elements = base.elements + analysis.items.filter { it.bottom.meters > glMd }.map { item ->
            val top = maxOf(glMd, item.top.meters)
            if (item.isTool) {
                SchematicElement(
                    id = "wo-tool:${item.id}", sourceId = item.id, kind = ElementKind.WORKOVER_TOOL, label = item.name,
                    mdTop = top, mdBottom = item.bottom.meters, innerRadius = 0.0, outerRadius = item.outerDiameter.meters / 2.0,
                    detail = ElementDetail.WorkoverTool(item.toolType!!),
                )
            } else {
                SchematicElement(
                    id = "wo-string:${item.id}", sourceId = item.id, kind = ElementKind.WORK_STRING_WALL, label = item.name,
                    mdTop = top, mdBottom = item.bottom.meters,
                    innerRadius = (item.innerDiameter ?: Length.ZERO).meters / 2.0, outerRadius = item.outerDiameter.meters / 2.0,
                )
            }
        }
        // Equipment set in the casing above the string bottom blocks the job.
        val blocking = woWell.completion(baseState)?.components.orEmpty().filter { it.isFullBoreBlocking }
        val ctx = com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext(woWell)
        blocking.firstOrNull { ctx.canonical(it.top) < analysis.bottomMd }?.let {
            issues += ValidationIssue(Severity.ERROR, "WO_BELOW_PLUG", "'${it.name}' blocks the bore above the bottom depth; mill or retrieve it first.", listOf(it.id))
        }
        return WorkoverJobResult(analysis, base.copy(elements = elements, issues = base.issues + issues), woWell, (issues + base.issues).distinct())
    }

    private fun drawingRigUp(op: WorkoverOperation) = DrillingRigUp(
        rig = DrillingRig(op.rigUp.rig.id, op.rigUp.rig.name, floorBelowRkb = op.rigUp.rig.floorBelowRkb, provenance = op.rigUp.rig.provenance),
        bopStack = op.rigUp.bopStack,
        nippledUpOnSectionId = op.rigUp.nippledUpOnSectionId,
    )

    companion object {
        /** The well with the production tubing and tubing-mounted equipment pulled from [state]. */
        fun withTubingPulled(well: Well, state: CompletionState = CompletionState.CURRENT): Well = well.copy(
            completions = well.completions.map { c ->
                if (c.state != state) {
                    c
                } else {
                    c.copy(
                        tubing = TubingConfiguration.ExplicitlyNone("Pulled for workover"),
                        components = c.components.filter { it.mounting == ComponentMounting.IN_CASING },
                    )
                }
            },
        )
    }
}
