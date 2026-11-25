package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.ct.CtBhaComponent
import com.wellsketch.engineer.core.model.ct.CtOperation
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.units.FlowRate
import com.wellsketch.engineer.core.model.units.Length

data class PlacedBhaComponent(val component: CtBhaComponent, val topMd: Length, val bottomMd: Length)

/**
 * Position of the CT string and BHA for the operation's current depth. All MDs are on the
 * well's canonical reference; [noseOnOperationReference] echoes the user's input datum.
 */
data class CtPlacement(
    val noseOnOperationReference: MeasuredDepth,
    val noseMd: Length,
    val noseMdBelowGl: Length,
    val noseTvdBelowGl: Length,
    val noseInclinationDeg: Double,
    val bhaTopMd: Length,
    val bhaComponents: List<PlacedBhaComponent>,
    /** CT length below the operation datum (depth to BHA top on the operation reference). */
    val ctLengthBelowDatum: Length,
    val issues: List<ValidationIssue>,
)

/**
 * Converts the operation depth (on its chosen datum) to well MD and places the BHA and CT.
 * Pure function of its inputs: positions change instantly with the depth, with no
 * intermediate states (the UI must not animate them).
 */
class CtPlacementCalculator {

    fun place(ctx: WellGeometryContext, operation: CtOperation): CtPlacement {
        val noseInput = MeasuredDepth(operation.currentDepth, operation.depthReference)
        val noseMd = ctx.canonical(noseInput)
        val glMd = ctx.groundLevelCanonicalMd
        val bhaLength = operation.bha.totalLength
        val bhaTop = noseMd - bhaLength
        val components = mutableListOf<PlacedBhaComponent>()
        var top = bhaTop
        for (c in operation.bha.components) {
            components += PlacedBhaComponent(c, top, top + c.length)
            top += c.length
        }
        val position = ctx.positionAtCanonical(noseMd)
        val issues = mutableListOf<ValidationIssue>()
        val ctBelowDatum = operation.currentDepth - bhaLength
        if (ctBelowDatum > operation.ctString.totalLength) {
            issues += ValidationIssue(
                Severity.ERROR, "CT_STRING_TOO_SHORT",
                "CT string length is less than the CT length required below the depth datum.",
                listOf(operation.ctString.id),
            )
        }
        if (noseMd < glMd) {
            issues += ValidationIssue(
                Severity.INFO, "CT_IN_SURFACE_STACK", "BHA nose is above ground level (inside the surface stack).",
            )
        }
        issues += ValidationIssue(
            Severity.INFO, "CT_SURFACE_LENGTH_EXCLUDED",
            "CT length between reel and depth datum is not included (surface geometry not specified).",
        )
        return CtPlacement(
            noseOnOperationReference = noseInput,
            noseMd = noseMd,
            noseMdBelowGl = ctx.converter.convert(noseInput, DepthReference.GroundLevel).value,
            noseTvdBelowGl = ctx.tvdBelowGroundLevel(noseMd),
            noseInclinationDeg = position.inclination.degrees,
            bhaTopMd = bhaTop,
            bhaComponents = components,
            ctLengthBelowDatum = ctBelowDatum,
            issues = issues,
        )
    }
}

/** Editing helpers for CT operations. */
object CtOperationEdits {

    /**
     * Changes the depth reference while keeping the physical BHA position: the displayed
     * number changes by the datum offset, the tool does not move.
     */
    fun changeDepthReference(ctx: WellGeometryContext, operation: CtOperation, newReference: DepthReference): CtOperation {
        val converted = ctx.converter.convert(MeasuredDepth(operation.currentDepth, operation.depthReference), newReference)
        return operation.copy(depthReference = newReference, currentDepth = converted.value)
    }

    fun setDepth(operation: CtOperation, depth: Length): CtOperation = operation.copy(currentDepth = depth)

    /** Sets the planned pump rate; a negative rate is not applied. */
    fun setPumpRate(operation: CtOperation, rate: FlowRate): CtOperation =
        if (rate < FlowRate.ZERO) operation else operation.copy(pumpRate = rate)
}
