package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.ComponentMounting
import com.wellsketch.engineer.core.model.well.Completion

enum class RestrictionBasis(val label: String) {
    DRIFT("Drift diameter"),
    NOMINAL_ID_NO_DRIFT("Nominal ID (drift not provided)"),
    COMPONENT_MIN_ID("Component minimum ID"),
    OPEN_HOLE_BIT_SIZE("Open hole (nominal bit size)"),
    FULL_BORE_BLOCKAGE("Full-bore blockage"),
    UNKNOWN_DIMENSION("Dimension not provided"),
}

/** A diameter limit in the CT path. [innerDiameter] is null only for unknown dimensions. */
data class Restriction(
    val topMd: Length,
    val bottomMd: Length,
    val innerDiameter: Length?,
    val basis: RestrictionBasis,
    val sourceId: String,
    val label: String,
)

data class PassabilityResult(
    val restrictionsAbove: List<Restriction>,
    /** Smallest known restriction above the target depth. */
    val smallestRestriction: Restriction?,
    /** Smallest restriction ID minus BHA max OD (geometric difference only; no criterion applied). */
    val bhaDiametralClearance: Length?,
    val issues: List<ValidationIssue>,
)

/**
 * Collects diameter restrictions along the CT path and checks whether the CT string and
 * BHA physically fit through them to a target depth. This is a strict geometric
 * comparison (OD must be smaller than the restriction); no clearance margin or
 * operational criterion is applied until one is specified and approved.
 */
class CtRestrictionAnalyzer {

    fun restrictions(ctx: WellGeometryContext, path: FlowPath, completion: Completion?): List<Restriction> {
        val result = mutableListOf<Restriction>()
        for (s in path.segments) {
            val (id, basis) = when {
                s.conduitType == ConduitType.OPEN_HOLE -> s.innerDiameter to RestrictionBasis.OPEN_HOLE_BIT_SIZE
                s.driftDiameter != null -> s.driftDiameter to RestrictionBasis.DRIFT
                else -> s.innerDiameter to RestrictionBasis.NOMINAL_ID_NO_DRIFT
            }
            result += Restriction(s.topMd, s.bottomMd, id, basis, s.conduitId ?: "path", "${s.conduitType.label}: ${s.conduitName}")
        }
        val tubingSegment = path.segments.firstOrNull { it.conduitType == ConduitType.PRODUCTION_TUBING }
        completion?.components?.forEach { c ->
            val top = ctx.canonical(c.top)
            val bottom = top + c.length
            val inPath = when (c.mounting) {
                ComponentMounting.ON_TUBING -> tubingSegment != null && top >= tubingSegment.topMd && top <= tubingSegment.bottomMd
                // components set in casing are in the CT path only below the tubing (or with no tubing)
                ComponentMounting.IN_CASING -> tubingSegment == null || top >= tubingSegment.bottomMd
            }
            if (!inPath) return@forEach
            result += when {
                c.isFullBoreBlocking -> Restriction(top, bottom, Length.ZERO, RestrictionBasis.FULL_BORE_BLOCKAGE, c.id, c.name)
                c.minimumInnerDiameter != null ->
                    Restriction(top, bottom, c.minimumInnerDiameter, RestrictionBasis.COMPONENT_MIN_ID, c.id, c.name)
                else -> Restriction(top, bottom, null, RestrictionBasis.UNKNOWN_DIMENSION, c.id, c.name)
            }
        }
        return result.sortedBy { it.topMd }
    }

    fun checkPassage(
        restrictions: List<Restriction>,
        targetMd: Length,
        ctMaxOd: Length,
        bhaMaxOd: Length?,
        totalDepth: Length?,
    ): PassabilityResult {
        val issues = mutableListOf<ValidationIssue>()
        if (totalDepth != null && targetMd > totalDepth) {
            issues += ValidationIssue(Severity.ERROR, "CT_BELOW_TD", "Target depth is below the deepest hole/shoe depth in the well data.")
        }
        val above = restrictions.filter { it.topMd < targetMd }
        val toolOd = listOfNotNull(bhaMaxOd, ctMaxOd).maxOrNull() ?: ctMaxOd
        for (r in above) {
            when {
                r.basis == RestrictionBasis.FULL_BORE_BLOCKAGE -> issues += ValidationIssue(
                    Severity.ERROR, "CT_PATH_BLOCKED", "${r.label} blocks the bore above the target depth.", listOf(r.sourceId),
                )
                r.innerDiameter == null -> issues += ValidationIssue(
                    Severity.WARNING, "CT_RESTRICTION_UNKNOWN",
                    "${r.label}: minimum ID not provided; passage cannot be verified.", listOf(r.sourceId),
                )
                toolOd >= r.innerDiameter -> issues += ValidationIssue(
                    Severity.ERROR, "CT_TOOL_TOO_LARGE",
                    "${r.label}: CT/BHA max OD is not smaller than the restriction (${r.basis.label}).",
                    listOf(r.sourceId),
                )
            }
            if (r.basis == RestrictionBasis.NOMINAL_ID_NO_DRIFT) {
                issues += ValidationIssue(
                    Severity.WARNING, "CT_DRIFT_MISSING",
                    "${r.label}: drift diameter not provided; nominal ID used for the passage check.",
                    listOf(r.sourceId),
                )
            }
        }
        val smallest = above.filter { it.innerDiameter != null }.minByOrNull { it.innerDiameter!! }
        val clearance = if (smallest?.innerDiameter != null && bhaMaxOd != null) smallest.innerDiameter - bhaMaxOd else null
        return PassabilityResult(above, smallest, clearance, issues.distinctBy { it.code to it.subjectIds })
    }
}
