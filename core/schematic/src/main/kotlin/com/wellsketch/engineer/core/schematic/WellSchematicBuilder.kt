package com.wellsketch.engineer.core.schematic

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.WellGeometryValidator
import com.wellsketch.engineer.core.model.geology.Lithology
import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingString
import com.wellsketch.engineer.core.model.well.ComponentMounting
import com.wellsketch.engineer.core.model.well.Completion
import com.wellsketch.engineer.core.model.well.CompletionComponentType
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.Well
import kotlin.math.max

/**
 * Builds the live, editable well schematic scene (longitudinal half-cut) from well data.
 *
 * Pure and deterministic: the same inputs always produce the same scene, so the UI can
 * rebuild it on every edit and the sketch updates immediately. Geometry is resolved here
 * (casing ends at its shoe, cement outer boundary switches from the previous casing ID to
 * the open hole at the previous shoe, etc.), so renderers only map coordinates.
 */
class WellSchematicBuilder(
    private val validator: WellGeometryValidator = WellGeometryValidator(),
) {

    fun build(
        well: Well,
        completionState: CompletionState?,
        operatingState: OperatingState = defaultOperatingState(well),
    ): SchematicScene {
        val ctx = WellGeometryContext(well)
        val completion = completionState?.let(well::completion)
        val glMd = ctx.groundLevelCanonicalMd.meters
        val totalDepth = max(totalDepthOf(ctx, completion), glMd + 1.0)

        val resolver = GeometryResolver(ctx)
        val elements = mutableListOf<SchematicElement>()
        val markers = mutableListOf<SchematicMarker>()

        addOpenHole(ctx, elements)
        well.casingStrings.forEach { addCasing(ctx, resolver, it, elements, markers) }
        completion?.let { addCompletion(ctx, resolver, it, elements) }

        val issues = validator.validate(ctx, completion).toMutableList()
        if (completionState != null && completion == null) {
            issues += ValidationIssue(
                ValidationIssue.Severity.INFO, "COMPLETION_STATE_MISSING",
                "No ${completionState.label.lowercase()} completion recorded; only casing is shown.",
            )
        }

        return SchematicScene(
            wellName = well.name,
            path = samplePath(ctx, glMd, totalDepth),
            elements = elements,
            markers = markers,
            formations = formationBands(ctx, totalDepth),
            datums = SceneDatums(
                rkbAboveGl = well.datums.rkbAboveGroundLevel.meters,
                mslRelativeToGl = -well.datums.groundLevelElevation.meters,
                canonicalReferenceLabel = ctx.canonicalReference.shortLabel,
            ),
            surfaceBlocks = SurfaceLayouts.forOperatingState(operatingState),
            trajectorySource = ctx.trajectorySource,
            groundLevelMd = glMd,
            totalDepthMd = totalDepth,
            issues = issues,
        )
    }

    private fun totalDepthOf(ctx: WellGeometryContext, completion: Completion?): Double {
        val depths = buildList {
            ctx.well.holeSections.forEach { add(ctx.canonical(it.bottom).meters) }
            ctx.well.casingStrings.forEach { add(ctx.canonical(it.shoe.depth).meters) }
            completion?.tubingString?.let { add(ctx.canonical(it.endOfTubing).meters) }
        }
        return depths.maxOrNull() ?: 0.0
    }

    private fun samplePath(ctx: WellGeometryContext, fromMd: Double, toMd: Double): List<PathVertex> {
        val step = max((toMd - fromMd) / PATH_SAMPLES, 0.5)
        val mds = generateSequence(fromMd) { it + step }.takeWhile { it < toMd }.toMutableList()
        mds += toMd
        return mds.map { md ->
            val p = ctx.positionAtCanonical(Length(md))
            PathVertex(md = md, x = p.verticalSection.meters, y = ctx.tvdBelowGroundLevel(Length(md)).meters)
        }
    }

    private fun addOpenHole(ctx: WellGeometryContext, out: MutableList<SchematicElement>) {
        ctx.well.holeSections.forEach { h ->
            out += SchematicElement(
                id = "hole:${h.id}", sourceId = h.id, kind = ElementKind.OPEN_HOLE, label = h.name,
                mdTop = ctx.canonical(h.top).meters, mdBottom = ctx.canonical(h.bottom).meters,
                innerRadius = 0.0, outerRadius = h.bitDiameter.meters / 2.0,
            )
        }
    }

    private fun addCasing(
        ctx: WellGeometryContext,
        resolver: GeometryResolver,
        s: CasingString,
        out: MutableList<SchematicElement>,
        markers: MutableList<SchematicMarker>,
    ) {
        val top = ctx.canonical(s.top).meters
        val shoe = ctx.canonical(s.shoe.depth).meters
        val od = s.spec.outerDiameter.meters
        out += SchematicElement(
            id = "casing:${s.id}", sourceId = s.id, kind = ElementKind.CASING_WALL, label = s.name,
            mdTop = top, mdBottom = shoe,
            innerRadius = s.spec.innerDiameter.meters / 2.0, outerRadius = od / 2.0,
            detail = ElementDetail.Casing(s.role),
        )
        markers += SchematicMarker("shoe:${s.id}", s.id, MarkerKind.CASING_SHOE, shoe, od / 2.0, "${s.name} shoe")
        if (s.isLiner) {
            markers += SchematicMarker("linertop:${s.id}", s.id, MarkerKind.LINER_TOP, top, od / 2.0, "${s.name} top")
        }
        s.cement?.let { cement ->
            val toc = ctx.canonical(cement.top).meters
            val boc = ctx.canonical(cement.bottom).meters
            resolver.annulusOutside(s, toc, boc).forEachIndexed { i, interval ->
                out += SchematicElement(
                    id = "cement:${s.id}:$i", sourceId = s.id, kind = ElementKind.CEMENT,
                    label = "Cement – ${s.name}",
                    mdTop = interval.top, mdBottom = interval.bottom,
                    innerRadius = od / 2.0, outerRadius = interval.outerRadius,
                    detail = ElementDetail.Cement(cement.status),
                )
            }
            markers += SchematicMarker("toc:${s.id}", s.id, MarkerKind.TOP_OF_CEMENT, toc, od / 2.0, "TOC – ${s.name}")
        }
    }

    private fun addCompletion(
        ctx: WellGeometryContext,
        resolver: GeometryResolver,
        completion: Completion,
        out: MutableList<SchematicElement>,
    ) {
        val tubing = completion.tubingString
        tubing?.let { t ->
            out += SchematicElement(
                id = "tubing:${t.id}", sourceId = t.id, kind = ElementKind.TUBING_WALL, label = t.name,
                mdTop = ctx.canonical(t.hangerDepth).meters, mdBottom = ctx.canonical(t.endOfTubing).meters,
                innerRadius = t.spec.innerDiameter.meters / 2.0, outerRadius = t.spec.outerDiameter.meters / 2.0,
            )
        }
        completion.components.forEach { c ->
            val top = ctx.canonical(c.top).meters
            val bottom = top + c.length.meters
            val host = resolver.innermostStringAt((top + bottom) / 2.0)
            val hostIdRadius = host?.spec?.innerDiameter?.meters?.div(2.0)
            val (inner, outer) = when (c.mounting) {
                ComponentMounting.ON_TUBING -> {
                    val inner = (c.minimumInnerDiameter ?: tubing?.spec?.innerDiameter)?.meters?.div(2.0) ?: 0.0
                    val outer = if (c.type == CompletionComponentType.PACKER && hostIdRadius != null) {
                        hostIdRadius // a set packer seals against the casing/liner wall
                    } else {
                        (c.outerDiameter ?: tubing?.spec?.outerDiameter)?.meters?.div(2.0) ?: inner
                    }
                    inner to max(outer, inner)
                }
                ComponentMounting.IN_CASING -> {
                    val inner = if (c.isFullBoreBlocking) 0.0 else c.minimumInnerDiameter?.meters?.div(2.0) ?: 0.0
                    val outer = hostIdRadius ?: c.outerDiameter?.meters?.div(2.0) ?: inner
                    inner to max(outer, inner)
                }
            }
            out += SchematicElement(
                id = "component:${c.id}", sourceId = c.id, kind = ElementKind.COMPLETION_COMPONENT, label = c.name,
                mdTop = top, mdBottom = bottom, innerRadius = inner, outerRadius = outer,
                detail = ElementDetail.Component(c.type),
            )
        }
        completion.perforations.forEach { p ->
            val host = ctx.well.casingStrings.firstOrNull { it.id == p.hostStringId } ?: return@forEach
            val top = ctx.canonical(p.top).meters
            val bottom = ctx.canonical(p.bottom).meters
            val hole = resolver.holeRadiusAt((top + bottom) / 2.0) ?: (host.spec.outerDiameter.meters / 2.0)
            out += SchematicElement(
                id = "perf:${p.id}", sourceId = p.id, kind = ElementKind.PERFORATION,
                label = "Perforations ${p.status.label.lowercase()}",
                mdTop = top, mdBottom = bottom,
                innerRadius = host.spec.innerDiameter.meters / 2.0,
                outerRadius = hole * PERFORATION_SYMBOL_FACTOR,
                detail = ElementDetail.Perforation(p.status),
                isSymbolic = true,
            )
        }
    }

    private fun formationBands(ctx: WellGeometryContext, totalDepthMd: Double): List<FormationBand> {
        val tdTvd = ctx.tvdBelowGroundLevel(Length(totalDepthMd)).meters
        val tops = ctx.well.formations
            .map { it to ctx.tvdBelowGroundLevel(it.top).meters }
            .sortedBy { it.second }
        if (tops.isEmpty()) {
            return listOf(FormationBand("formation:undefined", "Formations not defined", Lithology.UNKNOWN, 0.0, tdTvd, true))
        }
        val bands = mutableListOf<FormationBand>()
        val firstTop = tops.first().second
        if (firstTop > 0.0005) {
            bands += FormationBand("formation:undefined", "Not defined", Lithology.UNKNOWN, 0.0, firstTop, true)
        }
        tops.forEachIndexed { i, (f, top) ->
            val next = tops.getOrNull(i + 1)?.second ?: max(tdTvd, top)
            bands += FormationBand(f.id, f.name, f.lithology, max(top, 0.0), max(next, max(top, 0.0)))
        }
        return bands
    }

    companion object {
        private const val PATH_SAMPLES = 600.0

        /** Perforation tunnels are drawn as symbols extending this factor beyond the hole wall. */
        const val PERFORATION_SYMBOL_FACTOR = 1.35

        fun defaultOperatingState(well: Well): OperatingState =
            well.christmasTree?.let { OperatingState.CompletedWithTree(it) } ?: OperatingState.SurfaceNotDefined
    }
}

/** Geometric lookups shared by the scene builders (canonical MD in metres). */
class GeometryResolver(private val ctx: WellGeometryContext) {
    private data class Interval(val top: Double, val bottom: Double)

    private val strings = ctx.well.casingStrings.map {
        it to Interval(ctx.canonical(it.top).meters, ctx.canonical(it.shoe.depth).meters)
    }
    private val holes = ctx.well.holeSections.map {
        it to Interval(ctx.canonical(it.top).meters, ctx.canonical(it.bottom).meters)
    }

    fun holeRadiusAt(md: Double): Double? =
        holes.firstOrNull { (_, i) -> md >= i.top && md <= i.bottom }?.first?.bitDiameter?.meters?.div(2.0)

    /** Innermost (smallest ID) casing/liner covering [md]. */
    fun innermostStringAt(md: Double): CasingString? =
        strings.filter { (_, i) -> md >= i.top && md <= i.bottom }.minByOrNull { it.first.spec.innerDiameter }?.first

    data class AnnulusInterval(val top: Double, val bottom: Double, val outerRadius: Double)

    /**
     * Splits the annulus outside [string] between [top] and [bottom] into intervals with a
     * constant outer boundary: the ID of the innermost larger string covering the depth,
     * otherwise the open-hole radius. Intervals with no hole data are omitted.
     */
    fun annulusOutside(string: CasingString, top: Double, bottom: Double): List<AnnulusInterval> {
        if (bottom <= top) return emptyList()
        val breaks = sortedSetOf(top, bottom)
        strings.filter { it.first.id != string.id }.forEach { (_, i) -> breaks += i.top; breaks += i.bottom }
        holes.forEach { (_, i) -> breaks += i.top; breaks += i.bottom }
        val points = breaks.filter { it in top..bottom }
        val result = mutableListOf<AnnulusInterval>()
        for ((a, b) in points.zipWithNext()) {
            if (b - a < 1e-6) continue
            val mid = (a + b) / 2.0
            val outer = outerBoundaryAt(string, mid) ?: continue
            val last = result.lastOrNull()
            if (last != null && last.bottom == a && last.outerRadius == outer) {
                result[result.lastIndex] = last.copy(bottom = b)
            } else {
                result += AnnulusInterval(a, b, outer)
            }
        }
        return result
    }

    private fun outerBoundaryAt(string: CasingString, md: Double): Double? {
        val enclosing = strings
            .filter { (s, i) -> s.id != string.id && s.spec.innerDiameter > string.spec.outerDiameter && md >= i.top && md <= i.bottom }
            .minByOrNull { it.first.spec.innerDiameter }
        return enclosing?.first?.spec?.innerDiameter?.meters?.div(2.0) ?: holeRadiusAt(md)
    }
}
