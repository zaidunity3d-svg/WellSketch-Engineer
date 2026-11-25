package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.surface.SurfaceStackup
import com.wellsketch.engineer.core.engineering.surface.SurfaceStackupCalculator
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.ct.CtOperation
import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.schematic.CtSurfaceViewMode
import com.wellsketch.engineer.core.schematic.ElementDetail
import com.wellsketch.engineer.core.schematic.ElementKind
import com.wellsketch.engineer.core.schematic.SchematicElement
import com.wellsketch.engineer.core.schematic.SchematicScene
import com.wellsketch.engineer.core.schematic.WellSchematicBuilder
import kotlin.math.max

/** Everything the CT screen shows for one operation at its current depth. */
data class CtAnalysis(
    val scene: SchematicScene,
    val completionState: CompletionState,
    val flowPath: FlowPath,
    val restrictions: List<Restriction>,
    val passability: PassabilityResult,
    val placement: CtPlacement,
    val stackIssues: List<ValidationIssue>,
    val issues: List<ValidationIssue>,
    /** Surface elevations (wellhead, tree top, injector base) for this rig-up. */
    val surfaceStackup: SurfaceStackup,
    /** CT volumes, pumping times and annular velocities at the planned pump rate. */
    val volumes: CtVolumes,
)

/**
 * Facade that combines flow path, restriction, placement and surface-stack checks, and
 * builds the CT schematic scene (Christmas Tree + CT stack, never a drilling rig).
 *
 * The CT string is drawn concentric with the well axis; in reality CT lies against the
 * low side of the conduit. This is a drawing simplification only.
 */
class CtOperationAnalyzer(
    private val schematicBuilder: WellSchematicBuilder = WellSchematicBuilder(),
    private val flowPathResolver: CtFlowPathResolver = CtFlowPathResolver(),
    private val restrictionAnalyzer: CtRestrictionAnalyzer = CtRestrictionAnalyzer(),
    private val placementCalculator: CtPlacementCalculator = CtPlacementCalculator(),
    private val stackValidator: CtSurfaceStackValidator = CtSurfaceStackValidator(),
    private val stackupCalculator: SurfaceStackupCalculator = SurfaceStackupCalculator(),
    private val volumeCalculator: CtVolumeCalculator = CtVolumeCalculator(),
) {

    fun analyze(well: Well, operation: CtOperation, viewMode: CtSurfaceViewMode): CtAnalysis {
        val ctx = WellGeometryContext(well)
        val issues = mutableListOf<ValidationIssue>()

        // CT operations act on the current completion; fall back to as-built with a notice.
        val completionState = when {
            well.completion(CompletionState.CURRENT) != null -> CompletionState.CURRENT
            well.completion(CompletionState.AS_BUILT) != null -> CompletionState.AS_BUILT.also {
                issues += ValidationIssue(Severity.WARNING, "CT_NO_CURRENT_COMPLETION", "No current completion recorded; using as-built.")
            }
            else -> CompletionState.CURRENT.also {
                issues += ValidationIssue(Severity.WARNING, "CT_NO_COMPLETION", "No completion recorded; CT path uses casing/liner only.")
            }
        }
        val completion = well.completion(completionState)

        val tree = well.christmasTree
        val operatingState = if (tree != null) {
            OperatingState.CoiledTubingIntervention(tree, operation.rigUp)
        } else {
            issues += ValidationIssue(
                Severity.ERROR, "CT_NO_TREE", "No Christmas Tree defined; CT stack cannot be validated or drawn on the tree.",
            )
            OperatingState.SurfaceNotDefined
        }

        val baseScene = schematicBuilder.build(well, completionState, operatingState, viewMode)
        val stackup = stackupCalculator.calculate(well, operatingState)
        val flowPath = flowPathResolver.resolve(ctx, completion)
        val restrictions = restrictionAnalyzer.restrictions(ctx, flowPath, completion)
        val placement = placementCalculator.place(ctx, operation)
        val passability = restrictionAnalyzer.checkPassage(
            restrictions = restrictions,
            targetMd = placement.noseMd,
            ctMaxOd = operation.ctString.maxOuterDiameter,
            bhaMaxOd = operation.bha.maxOuterDiameter,
            totalDepth = flowPath.totalDepth,
        )
        val stackIssues = if (tree != null) {
            stackValidator.validate(
                tree, operation.rigUp.pressureControlStack,
                operation.ctString.maxOuterDiameter, operation.bha.maxOuterDiameter,
                operation.maxExpectedWellheadPressure,
            )
        } else {
            emptyList()
        }

        val volumes = volumeCalculator.calculate(flowPath, placement, operation)
        val ctElements = ctElements(baseScene.groundLevelMd, operation, placement)
        val scene = baseScene.copy(
            elements = baseScene.elements + ctElements,
            totalDepthMd = max(baseScene.totalDepthMd, placement.noseMd.meters),
        )
        return CtAnalysis(
            scene = scene,
            completionState = completionState,
            flowPath = flowPath,
            restrictions = restrictions,
            passability = passability,
            placement = placement,
            stackIssues = stackIssues,
            issues = issues + passability.issues + placement.issues + stackIssues + stackup.issues + volumes.issues,
            surfaceStackup = stackup,
            volumes = volumes,
        )
    }

    private fun ctElements(glMd: Double, operation: CtOperation, placement: CtPlacement): List<SchematicElement> {
        val out = mutableListOf<SchematicElement>()
        // CT string sections, from the BHA top upward (section 0 is the downhole end).
        var bottom = placement.bhaTopMd.meters
        for ((i, section) in operation.ctString.sections.withIndex()) {
            val top = max(glMd, bottom - section.length.meters)
            if (bottom <= glMd) break
            out += SchematicElement(
                id = "ct:${operation.ctString.id}:$i", sourceId = operation.ctString.id, kind = ElementKind.CT_WALL,
                label = operation.ctString.name, mdTop = top, mdBottom = bottom,
                innerRadius = section.nominalInnerDiameter.meters / 2.0,
                outerRadius = section.outerDiameter.meters / 2.0,
            )
            bottom = top
        }
        for (placed in placement.bhaComponents) {
            if (placed.bottomMd.meters <= glMd) continue
            out += SchematicElement(
                id = "bha:${placed.component.id}", sourceId = placed.component.id, kind = ElementKind.BHA_COMPONENT,
                label = placed.component.name,
                mdTop = max(glMd, placed.topMd.meters), mdBottom = placed.bottomMd.meters,
                innerRadius = 0.0, outerRadius = placed.component.maxOuterDiameter.meters / 2.0,
                detail = ElementDetail.Bha(placed.component.type),
            )
        }
        return out
    }
}
