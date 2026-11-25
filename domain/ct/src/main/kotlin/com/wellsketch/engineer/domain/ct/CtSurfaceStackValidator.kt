package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.surface.ChristmasTree
import com.wellsketch.engineer.core.model.surface.CtPressureControlStack
import com.wellsketch.engineer.core.model.surface.StackComponent
import com.wellsketch.engineer.core.model.surface.StackComponentType
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure

/**
 * Structural checks of the CT pressure-control stack rigged up on the Christmas Tree:
 * connection continuity, bore versus CT/BHA OD, and pressure rating versus the specified
 * maximum expected wellhead pressure.
 *
 * Rules marked "provisional" encode common practice and need owner engineering review
 * before they are treated as requirements.
 */
class CtSurfaceStackValidator {

    fun validate(
        tree: ChristmasTree,
        stack: CtPressureControlStack,
        ctMaxOd: Length,
        bhaMaxOd: Length?,
        requiredPressure: Pressure?,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val stackComponents = stack.components
        if (stackComponents.isEmpty()) {
            issues += ValidationIssue(Severity.ERROR, "PCE_EMPTY", "No CT pressure-control components configured.")
            return issues
        }
        if (stackComponents.first().type != StackComponentType.STRIPPER_PACKOFF) {
            issues += ValidationIssue(
                Severity.WARNING, "PCE_ORDER_STRIPPER",
                "Provisional rule: the top component is expected to be a stripper/packoff.",
                listOf(stackComponents.first().id),
            )
        }
        if (stackComponents.none { it.type == StackComponentType.CT_BOP || it.type == StackComponentType.SHEAR_SEAL_BOP }) {
            issues += ValidationIssue(Severity.WARNING, "PCE_NO_BOP", "Provisional rule: no CT BOP in the stack.")
        }

        // Full vertical bore path, top to bottom: CT stack, then tree without its cap.
        val treeBelow = tree.components.filterNot { it.type == StackComponentType.TREE_CAP }
        val chain: List<StackComponent> = stackComponents + treeBelow
        for ((upper, lower) in chain.zipWithNext()) {
            if (upper.bottomConnection.designation.trim() != lower.topConnection.designation.trim()) {
                issues += ValidationIssue(
                    Severity.ERROR, "PCE_CONNECTION_MISMATCH",
                    "Connection mismatch between '${upper.name}' (${upper.bottomConnection.designation}) " +
                        "and '${lower.name}' (${lower.topConnection.designation}).",
                    listOf(upper.id, lower.id),
                )
            }
        }

        for (c in chain) {
            if (c.bore <= ctMaxOd) {
                issues += ValidationIssue(
                    Severity.ERROR, "PCE_BORE_CT", "'${c.name}' bore is not larger than the CT OD.", listOf(c.id),
                )
            }
            // Provisional: the BHA is assumed to be deployed below the stripper, so every other
            // component in the bore path must pass the BHA.
            if (bhaMaxOd != null && c.type != StackComponentType.STRIPPER_PACKOFF && c.bore <= bhaMaxOd) {
                issues += ValidationIssue(
                    Severity.ERROR, "PCE_BORE_BHA", "'${c.name}' bore is not larger than the BHA max OD (provisional rule).",
                    listOf(c.id),
                )
            }
        }

        if (requiredPressure == null) {
            issues += ValidationIssue(
                Severity.INFO, "PCE_PRESSURE_NOT_SPECIFIED",
                "Maximum expected wellhead pressure not specified; rating check skipped.",
            )
        } else {
            for (c in chain) {
                if (c.ratedWorkingPressure < requiredPressure) {
                    issues += ValidationIssue(
                        Severity.ERROR, "PCE_RATING",
                        "'${c.name}' rated working pressure is below the specified maximum expected wellhead pressure.",
                        listOf(c.id),
                    )
                }
            }
        }
        return issues
    }

    /** The lowest rated working pressure in the bore path (the stack's limiting rating). */
    fun limitingRating(tree: ChristmasTree, stack: CtPressureControlStack): StackComponent? =
        (stack.components + tree.components.filterNot { it.type == StackComponentType.TREE_CAP })
            .minByOrNull { it.ratedWorkingPressure }
}
