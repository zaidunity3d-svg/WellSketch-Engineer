package com.wellsketch.engineer.core.engineering.validation

import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.catalog.TubularKind
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.trajectory.TrajectorySource
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingString
import com.wellsketch.engineer.core.model.well.ComponentMounting
import com.wellsketch.engineer.core.model.well.Completion
import com.wellsketch.engineer.core.model.well.TubingConfiguration
import com.wellsketch.engineer.core.model.well.TubularSpec

/**
 * Geometric consistency checks on well input data. These checks compare entered geometry
 * (depth ordering, nesting of diameters, overlaps); they do not apply engineering design
 * criteria such as clearances, burst/collapse or cement design rules.
 */
class WellGeometryValidator {

    fun validate(context: WellGeometryContext, completion: Completion?): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        checkTrajectory(context, issues)
        checkFormations(context, issues)
        checkHoleSections(context, issues)
        checkCasing(context, issues)
        if (completion != null) checkCompletion(context, completion, issues)
        return issues
    }

    private fun checkTrajectory(ctx: WellGeometryContext, issues: MutableList<ValidationIssue>) {
        if (ctx.trajectorySource == TrajectorySource.ASSUMED_VERTICAL) {
            issues += ValidationIssue(
                Severity.WARNING, "TRAJ_NO_SURVEY",
                "No directional survey: the well path is drawn as vertical (assumption, not data).",
            )
        }
        val deepest = deepestMd(ctx)
        if (ctx.trajectorySource == TrajectorySource.SURVEY && deepest != null &&
            ctx.trajectory.isExtrapolated(deepest)
        ) {
            issues += ValidationIssue(
                Severity.WARNING, "TRAJ_EXTRAPOLATED",
                "Well data extends below the last survey station; the path below it is projected straight.",
            )
        }
    }

    private fun checkFormations(ctx: WellGeometryContext, issues: MutableList<ValidationIssue>) {
        val tops = ctx.well.formations.map { it to ctx.tvdBelowGroundLevel(it.top) }
        tops.zipWithNext().forEach { (a, b) ->
            if (b.second <= a.second) {
                issues += ValidationIssue(
                    Severity.ERROR, "FORMATION_ORDER",
                    "Formation '${b.first.name}' top is not deeper than '${a.first.name}'.",
                    listOf(a.first.id, b.first.id),
                )
            }
        }
        val first = tops.firstOrNull()
        if (first != null && !first.second.isApproximately(Length.ZERO, Length.meters(0.001))) {
            issues += ValidationIssue(
                Severity.INFO, "FORMATION_FIRST_TOP",
                "First formation top is not at GL = 0; the interval above it is shown as 'Not defined'.",
                listOf(first.first.id),
            )
        }
    }

    private fun checkHoleSections(ctx: WellGeometryContext, issues: MutableList<ValidationIssue>) {
        val sections = ctx.well.holeSections.map { Triple(it, ctx.canonical(it.top), ctx.canonical(it.bottom)) }
        sections.forEach { (s, top, bottom) ->
            if (bottom <= top) {
                issues += ValidationIssue(
                    Severity.ERROR, "HOLE_INTERVAL", "Hole section '${s.name}' bottom must be below its top.",
                    listOf(s.id),
                )
            }
        }
        val sorted = sections.sortedBy { it.second }
        // First hole section should start at ground level (HTML v0.9 QA rule); offshore, GL is the seabed.
        val gl = ctx.canonical(groundLevelMd(ctx))
        sorted.firstOrNull()?.let { (s, top, _) ->
            if (top > gl + Length.meters(0.001)) {
                issues += ValidationIssue(
                    Severity.WARNING, "HOLE_FIRST_TOP",
                    "First hole section '${s.name}' starts below ground level: no hole is recorded from GL to its top.",
                    listOf(s.id),
                )
            }
        }
        sorted.zipWithNext().forEach { (a, b) ->
            if (b.second > a.third + Length.meters(0.001)) {
                issues += ValidationIssue(
                    Severity.ERROR, "HOLE_GAP",
                    "Gap between hole sections '${a.first.name}' and '${b.first.name}': no hole is recorded in between.",
                    listOf(a.first.id, b.first.id),
                )
            }
            if (b.second < a.third - Length.meters(0.001)) {
                issues += ValidationIssue(
                    Severity.ERROR, "HOLE_OVERLAP",
                    "Hole sections '${a.first.name}' and '${b.first.name}' overlap.",
                    listOf(a.first.id, b.first.id),
                )
            }
            if (b.first.bitDiameter >= a.first.bitDiameter) {
                issues += ValidationIssue(
                    Severity.WARNING, "HOLE_SIZE_ORDER",
                    "Hole section '${b.first.name}' is not smaller than the section above it.",
                    listOf(b.first.id),
                )
            }
        }
    }

    private fun checkCasing(ctx: WellGeometryContext, issues: MutableList<ValidationIssue>) {
        val strings = ctx.well.casingStrings
        val totalDepth = ctx.well.holeSections.maxOfOrNull { ctx.canonical(it.bottom) }
        for (s in strings) {
            val top = ctx.canonical(s.top)
            val shoe = ctx.canonical(s.shoe.depth)
            if (shoe <= top) {
                issues += ValidationIssue(
                    Severity.ERROR, "CASING_INTERVAL", "'${s.name}': shoe must be below the string top.", listOf(s.id),
                )
            }
            if (totalDepth != null && shoe > totalDepth + Length.meters(0.001)) {
                issues += ValidationIssue(
                    Severity.ERROR, "CASING_BELOW_TD", "'${s.name}': shoe is deeper than the drilled hole.", listOf(s.id),
                )
            }
            val holeAtShoe = ctx.well.holeSections.firstOrNull {
                shoe > ctx.canonical(it.top) && shoe <= ctx.canonical(it.bottom) + Length.meters(0.001)
            }
            if (holeAtShoe != null && s.spec.outerDiameter >= holeAtShoe.bitDiameter) {
                issues += ValidationIssue(
                    Severity.ERROR, "CASING_OD_VS_HOLE",
                    "'${s.name}': OD is not smaller than the hole size at the shoe ('${holeAtShoe.name}').",
                    listOf(s.id, holeAtShoe.id),
                )
            }
            checkTubularRecord(s.name, s.id, s.spec, TubularKind.CASING, issues)
            s.cement?.let { c ->
                val toc = ctx.canonical(c.top)
                val boc = ctx.canonical(c.bottom)
                if (boc <= toc) {
                    issues += ValidationIssue(
                        Severity.ERROR, "CEMENT_INTERVAL", "'${s.name}': cement bottom must be below the top of cement.",
                        listOf(s.id),
                    )
                }
                if (boc > shoe + Length.meters(0.001)) {
                    issues += ValidationIssue(
                        Severity.WARNING, "CEMENT_BELOW_SHOE", "'${s.name}': cement extends below the shoe.", listOf(s.id),
                    )
                }
                if (toc < top - Length.meters(0.001)) {
                    issues += ValidationIssue(
                        Severity.WARNING, "CEMENT_ABOVE_TOP",
                        "'${s.name}': top of cement is above the top of the string.", listOf(s.id),
                    )
                }
                c.tailTop?.let(ctx::canonical)?.let { tail ->
                    if (!(tail > toc && tail < boc)) {
                        issues += ValidationIssue(
                            Severity.WARNING, "CEMENT_TAIL_OUTSIDE",
                            "'${s.name}': tail slurry top is not between the top and bottom of cement.", listOf(s.id),
                        )
                    }
                }
            }
            if (s.isLiner) checkLinerOverlap(ctx, s, strings, issues)
        }
        checkNesting(ctx, strings, issues)
    }

    private fun checkLinerOverlap(
        ctx: WellGeometryContext,
        liner: CasingString,
        strings: List<CasingString>,
        issues: MutableList<ValidationIssue>,
    ) {
        val linerTop = ctx.canonical(liner.top)
        val host = strings.filter { it.id != liner.id && it.spec.innerDiameter > liner.spec.outerDiameter }
            .firstOrNull { ctx.canonical(it.top) <= linerTop && ctx.canonical(it.shoe.depth) > linerTop }
        if (host == null) {
            issues += ValidationIssue(
                Severity.ERROR, "LINER_NO_HOST",
                "'${liner.name}': liner top is not inside a larger casing string (no overlap).",
                listOf(liner.id),
            )
        }
    }

    /** Where two strings overlap in depth, the smaller OD must fit inside the larger ID. */
    private fun checkNesting(
        ctx: WellGeometryContext,
        strings: List<CasingString>,
        issues: MutableList<ValidationIssue>,
    ) {
        for (i in strings.indices) for (j in strings.indices) {
            if (i >= j) continue
            val a = strings[i]
            val b = strings[j]
            val overlapTop = maxOf(ctx.canonical(a.top), ctx.canonical(b.top))
            val overlapBottom = minOf(ctx.canonical(a.shoe.depth), ctx.canonical(b.shoe.depth))
            if (overlapBottom <= overlapTop) continue
            val (outer, inner) = if (a.spec.outerDiameter >= b.spec.outerDiameter) a to b else b to a
            val drift = outer.spec.driftDiameter
            if (inner.spec.outerDiameter >= outer.spec.innerDiameter) {
                issues += ValidationIssue(
                    Severity.ERROR, "CASING_NESTING",
                    "'${inner.name}' OD does not fit inside '${outer.name}' ID where they overlap.",
                    listOf(inner.id, outer.id),
                )
            } else if (drift != null && inner.spec.outerDiameter >= drift) {
                issues += ValidationIssue(
                    Severity.WARNING, "CASING_DRIFT",
                    "'${inner.name}' OD is not smaller than the drift of '${outer.name}': it may not pass. Check connection ODs.",
                    listOf(inner.id, outer.id),
                )
            }
        }
    }

    private fun checkCompletion(ctx: WellGeometryContext, completion: Completion, issues: MutableList<ValidationIssue>) {
        val tubing = (completion.tubing as? TubingConfiguration.Installed)?.tubing
        val deepestShoe = ctx.well.casingStrings.maxOfOrNull { ctx.canonical(it.shoe.depth) }
        if (tubing != null) {
            val top = ctx.canonical(tubing.hangerDepth)
            val end = ctx.canonical(tubing.endOfTubing)
            if (end <= top) {
                issues += ValidationIssue(
                    Severity.ERROR, "TUBING_INTERVAL", "Tubing end must be below the tubing hanger.", listOf(tubing.id),
                )
            }
            if (deepestShoe != null && end > deepestShoe) {
                issues += ValidationIssue(
                    Severity.WARNING, "TUBING_IN_OPEN_HOLE", "End of tubing is below the deepest casing/liner shoe.",
                    listOf(tubing.id),
                )
            }
            val host = ctx.well.casingStrings
                .filter { ctx.canonical(it.top) <= end && ctx.canonical(it.shoe.depth) >= end }
                .minByOrNull { it.spec.innerDiameter }
            if (host != null && tubing.spec.outerDiameter >= host.spec.innerDiameter) {
                issues += ValidationIssue(
                    Severity.ERROR, "TUBING_OD_VS_CASING",
                    "Tubing OD does not fit inside '${host.name}' ID.", listOf(tubing.id, host.id),
                )
            }
            checkTubularRecord(tubing.name, tubing.id, tubing.spec, TubularKind.TUBING, issues)
        }
        for (c in completion.components) {
            val top = ctx.canonical(c.top)
            if (c.mounting == ComponentMounting.ON_TUBING) {
                if (tubing == null) {
                    issues += ValidationIssue(
                        Severity.ERROR, "COMPONENT_NO_TUBING",
                        "'${c.name}' is mounted on tubing but the completion has no tubing.", listOf(c.id),
                    )
                } else if (top < ctx.canonical(tubing.hangerDepth) || top + c.length > ctx.canonical(tubing.endOfTubing) + Length.meters(0.001)) {
                    issues += ValidationIssue(
                        Severity.ERROR, "COMPONENT_OUTSIDE_TUBING",
                        "'${c.name}' lies outside the tubing interval.", listOf(c.id),
                    )
                }
            }
            if (c.outerDiameter == null || (c.minimumInnerDiameter == null && !c.isFullBoreBlocking)) {
                issues += ValidationIssue(
                    Severity.INFO, "COMPONENT_DIMENSIONS_MISSING",
                    "'${c.name}': OD/ID not provided; restriction checks for this component are incomplete.",
                    listOf(c.id),
                )
            }
        }
        for (p in completion.perforations) {
            val host = ctx.well.casingStrings.firstOrNull { it.id == p.hostStringId }
            if (host == null) {
                issues += ValidationIssue(
                    Severity.ERROR, "PERF_HOST", "Perforation interval refers to an unknown string.", listOf(p.id),
                )
            } else if (ctx.canonical(p.top) < ctx.canonical(host.top) || ctx.canonical(p.bottom) > ctx.canonical(host.shoe.depth)) {
                issues += ValidationIssue(
                    Severity.ERROR, "PERF_OUTSIDE_HOST",
                    "Perforation interval lies outside '${host.name}'.", listOf(p.id, host.id),
                )
            }
        }
    }

    /**
     * Catalogue checks from the HTML v0.9 QA list: a tubing record must not be used for casing
     * (or the reverse), and grade / connection should be chosen. A pipe with no catalogue
     * record is allowed (field-measured or user dimensions) and only noted.
     */
    private fun checkTubularRecord(name: String, id: String, spec: TubularSpec, expected: TubularKind, issues: MutableList<ValidationIssue>) {
        val record = spec.catalogItemId?.let(TubularCatalog::byId)
        if (spec.catalogItemId != null && record != null && record.kind != expected) {
            issues += ValidationIssue(
                Severity.ERROR, "TUBULAR_CATALOGUE_KIND",
                "'$name' uses a ${record.kind.name.lowercase()} catalogue record; choose a ${expected.name.lowercase()} size.",
                listOf(id),
            )
        }
        if (record != null && (spec.grade == null || spec.connection == null)) {
            issues += ValidationIssue(
                Severity.INFO, "TUBULAR_GRADE_CONNECTION",
                "'$name': ${listOfNotNull("grade".takeIf { spec.grade == null }, "connection".takeIf { spec.connection == null }).joinToString(" and ")} not selected.",
                listOf(id),
            )
        }
    }

    private fun groundLevelMd(ctx: WellGeometryContext) = MeasuredDepth(ctx.well.datums.rkbAboveGroundLevel, DepthReference.Rkb)

    private fun deepestMd(ctx: WellGeometryContext): Length? =
        (ctx.well.holeSections.map { ctx.canonical(it.bottom) } +
            ctx.well.casingStrings.map { ctx.canonical(it.shoe.depth) }).maxOrNull()
}
