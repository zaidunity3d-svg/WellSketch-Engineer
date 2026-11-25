package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.surface.ChristmasTree
import com.wellsketch.engineer.core.model.surface.CtPressureControlStack
import com.wellsketch.engineer.core.model.surface.StackComponentType
import com.wellsketch.engineer.core.model.surface.WellheadSection
import com.wellsketch.engineer.core.model.units.Length

/** Where an item of the CT path is. */
enum class CtPathPart(val label: String) {
    CT_STACK("CT pressure-control stack"),
    TREE("Christmas Tree"),
    WELLHEAD("Wellhead"),
    DOWNHOLE("Downhole"),
}

/**
 * One diameter limit along the whole CT path, from the stripper down to the CT nose.
 * [diameter] is null when not entered. [bhaPasses] is false for items the BHA does not
 * pass through (the stripper/packoff: the BHA is assumed deployed below it, provisional).
 * [depth] is the canonical MD of downhole items; surface items have none.
 */
data class CtPathItem(
    val part: CtPathPart,
    val sourceId: String,
    val name: String,
    val diameter: Length?,
    val basisLabel: String,
    val bhaPasses: Boolean = true,
    val depth: Length? = null,
)

/**
 * Smallest restrictions of the whole CT path. [smallestForBha] / [smallestForCt] are the
 * smallest known diameters the BHA / CT must pass, surface and downhole together. The
 * clearances are diameter minus OD: geometric differences only, no criterion applied.
 */
data class CtPathSummary(
    val items: List<CtPathItem>,
    val smallestForCt: CtPathItem?,
    val smallestForBha: CtPathItem?,
    val ctDiametralClearance: Length?,
    val bhaDiametralClearance: Length?,
    val issues: List<ValidationIssue>,
)

/**
 * Combines the surface bore path (CT stack, tree without cap and, when CT enters the casing
 * directly because there is no production tubing, the wellhead sections) with the downhole
 * restrictions above the nose, and finds the smallest diameter the CT and the BHA must pass.
 *
 * With production tubing the CT goes from the tree into the tubing hanger and tubing (a
 * downhole item), so the wellhead section bores around the hanger are not in its path.
 */
class CtPathRestrictionAnalyzer {

    fun summarize(
        stack: CtPressureControlStack?,
        tree: ChristmasTree?,
        wellheadSections: List<WellheadSection>,
        flowPath: FlowPath,
        downholeAbove: List<Restriction>,
        ctMaxOd: Length,
        bhaMaxOd: Length?,
    ): CtPathSummary {
        val issues = mutableListOf<ValidationIssue>()
        val items = mutableListOf<CtPathItem>()
        stack?.components?.forEach { c ->
            items += CtPathItem(
                CtPathPart.CT_STACK, c.id, c.name, c.bore, "Bore",
                bhaPasses = c.type != StackComponentType.STRIPPER_PACKOFF,
            )
        }
        tree?.components?.filterNot { it.type == StackComponentType.TREE_CAP }?.forEach { c ->
            items += CtPathItem(CtPathPart.TREE, c.id, c.name, c.bore, "Bore")
        }
        val tubingFromSurface = flowPath.segments.firstOrNull()?.conduitType == ConduitType.PRODUCTION_TUBING
        if (!tubingFromSurface && tree != null) {
            for (s in wellheadSections) {
                items += CtPathItem(CtPathPart.WELLHEAD, s.id, s.name, s.bore, "Bore")
                when {
                    s.bore == null -> issues += ValidationIssue(
                        Severity.WARNING, "CT_WELLHEAD_BORE_UNKNOWN",
                        "No production tubing: CT passes through '${s.name}', but its bore is not entered.", listOf(s.id),
                    )
                    s.bore!! <= ctMaxOd -> issues += ValidationIssue(
                        Severity.ERROR, "CT_WELLHEAD_BORE_CT", "'${s.name}' bore is not larger than the CT OD.", listOf(s.id),
                    )
                    bhaMaxOd != null && s.bore!! <= bhaMaxOd -> issues += ValidationIssue(
                        Severity.ERROR, "CT_WELLHEAD_BORE_BHA", "'${s.name}' bore is not larger than the BHA max OD.", listOf(s.id),
                    )
                }
            }
        }
        for (r in downholeAbove) {
            items += CtPathItem(CtPathPart.DOWNHOLE, r.sourceId, r.label, r.innerDiameter, r.basis.label, depth = r.topMd)
        }
        val known = items.filter { it.diameter != null }
        val smallestForCt = known.minByOrNull { it.diameter!! }
        val smallestForBha = known.filter { it.bhaPasses }.minByOrNull { it.diameter!! }
        return CtPathSummary(
            items = items,
            smallestForCt = smallestForCt,
            smallestForBha = smallestForBha,
            ctDiametralClearance = smallestForCt?.let { it.diameter!! - ctMaxOd },
            bhaDiametralClearance = if (bhaMaxOd != null) smallestForBha?.let { it.diameter!! - bhaMaxOd } else null,
            issues = issues,
        )
    }
}
