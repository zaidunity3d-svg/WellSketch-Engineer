package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingString
import com.wellsketch.engineer.core.model.well.Completion
import com.wellsketch.engineer.core.model.well.TubingConfiguration

enum class ConduitType(val label: String) {
    PRODUCTION_TUBING("Production tubing"),
    CASING("Casing"),
    LINER("Liner"),
    OPEN_HOLE("Open hole"),
}

/** One conduit interval of the CT flow path (canonical MD). */
data class FlowPathSegment(
    val topMd: Length,
    val bottomMd: Length,
    val conduitType: ConduitType,
    val conduitId: String?,
    val conduitName: String,
    /** Nominal ID (or bit size for open hole). */
    val innerDiameter: Length,
    val driftDiameter: Length?,
)

data class FlowPath(val segments: List<FlowPathSegment>) {
    val totalDepth: Length? get() = segments.lastOrNull()?.bottomMd
    fun segmentAt(md: Length): FlowPathSegment? = segments.firstOrNull { md >= it.topMd && md <= it.bottomMd }
}

/**
 * Determines the conduits CT runs through.
 *
 * Rule (CLAUDE.md): CT runs inside the production tubing by default. Below the end of
 * tubing — or from surface when the completion explicitly has no tubing — CT runs in the
 * innermost casing or liner at each depth, and in open hole below the deepest shoe.
 */
class CtFlowPathResolver {

    fun resolve(ctx: WellGeometryContext, completion: Completion?): FlowPath {
        val glMd = ctx.groundLevelCanonicalMd
        val segments = mutableListOf<FlowPathSegment>()
        var start = glMd
        val tubing = (completion?.tubing as? TubingConfiguration.Installed)?.tubing
        if (tubing != null) {
            val top = maxOf(ctx.canonical(tubing.hangerDepth), glMd)
            val end = ctx.canonical(tubing.endOfTubing)
            segments += FlowPathSegment(
                top, end, ConduitType.PRODUCTION_TUBING, tubing.id, tubing.name,
                tubing.spec.innerDiameter, tubing.spec.driftDiameter,
            )
            start = end
        }
        segments += casingSegmentsBelow(ctx, start)
        return FlowPath(segments)
    }

    private fun casingSegmentsBelow(ctx: WellGeometryContext, start: Length): List<FlowPathSegment> {
        val strings = ctx.well.casingStrings.map { Triple(it, ctx.canonical(it.top), ctx.canonical(it.shoe.depth)) }
        val holes = ctx.well.holeSections.map { Triple(it, ctx.canonical(it.top), ctx.canonical(it.bottom)) }
        val td = (holes.map { it.third } + strings.map { it.third }).maxOrNull() ?: return emptyList()
        if (td <= start) return emptyList()
        val breaks = (listOf(start, td) + strings.flatMap { listOf(it.second, it.third) } + holes.flatMap { listOf(it.second, it.third) })
            .filter { it >= start && it <= td }
            .distinctBy { it.meters }
            .sorted()
        val out = mutableListOf<FlowPathSegment>()
        for ((a, b) in breaks.zipWithNext()) {
            val mid = Length((a.meters + b.meters) / 2.0)
            val host: CasingString? = strings.filter { mid >= it.second && mid <= it.third }
                .minByOrNull { it.first.spec.innerDiameter }?.first
            val segment = if (host != null) {
                FlowPathSegment(
                    a, b, if (host.isLiner) ConduitType.LINER else ConduitType.CASING, host.id, host.name,
                    host.spec.innerDiameter, host.spec.driftDiameter,
                )
            } else {
                val hole = holes.firstOrNull { mid >= it.second && mid <= it.third }?.first ?: continue
                FlowPathSegment(a, b, ConduitType.OPEN_HOLE, hole.id, hole.name, hole.bitDiameter, null)
            }
            val last = out.lastOrNull()
            val continuesLast = last != null && last.conduitId == segment.conduitId &&
                last.conduitType == segment.conduitType && last.bottomMd.isApproximately(segment.topMd)
            if (continuesLast) out[out.lastIndex] = last!!.copy(bottomMd = segment.bottomMd) else out += segment
        }
        return out
    }
}
