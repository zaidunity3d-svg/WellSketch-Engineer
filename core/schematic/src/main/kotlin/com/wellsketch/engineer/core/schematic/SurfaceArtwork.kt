package com.wellsketch.engineer.core.schematic

import com.wellsketch.engineer.core.model.surface.DrillingBopComponentType
import com.wellsketch.engineer.core.model.surface.OutletFitting
import com.wellsketch.engineer.core.model.surface.StackComponentType
import com.wellsketch.engineer.core.model.surface.ValveOperator
import com.wellsketch.engineer.core.model.surface.WellheadSectionType
import com.wellsketch.engineer.core.schematic.SurfaceLayouts.Widths
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Renderer-agnostic vector artwork of the surface equipment.
 *
 * Coordinates are metres: x = horizontal offset from the well axis (+ = right), y = elevation
 * relative to ground level (+ = up). Renderers only map coordinates and paint fills, so the
 * same artwork drives the Compose canvas and any export. Every size introduced here is a
 * drawing size (flange outlines, studs, handwheels, operators); none is an equipment
 * dimension and none may be used in calculations. Vertical positions come from the layout.
 */

/** Fill styles; colours are 0xAARRGGBB. */
sealed interface ArtFill {
    val argb: Long

    data class Solid(override val argb: Long) : ArtFill

    /** Metallic shading across the horizontal extent (vertical cylinders and bodies). */
    data class CylinderX(override val argb: Long) : ArtFill

    /** Metallic shading across the vertical extent (horizontal pipes and operators). */
    data class CylinderY(override val argb: Long) : ArtFill

    /** Radial highlight (drums, motors, gauges). */
    data class Radial(override val argb: Long) : ArtFill

    /** Lighter at the top, darker at the bottom (pit interiors, soil). */
    data class Depth(override val argb: Long) : ArtFill
}

data class ArtPoint(val x: Double, val y: Double)

sealed interface ArtPrimitive {
    /** Model object this primitive belongs to (for selection highlighting). */
    val sourceId: String?
}

data class ArtRect(
    val x0: Double, val y0: Double, val x1: Double, val y1: Double,
    val fill: ArtFill?, val stroke: Long?, val corner: Double = 0.0,
    override val sourceId: String? = null, val dashed: Boolean = false,
) : ArtPrimitive

data class ArtPolygon(
    val points: List<ArtPoint>, val fill: ArtFill?, val stroke: Long?,
    /** Stroke width in metres; 0 = hairline. */
    val strokeWidth: Double = 0.0, val closed: Boolean = true,
    override val sourceId: String? = null,
) : ArtPrimitive

data class ArtCircle(
    val cx: Double, val cy: Double, val r: Double,
    val fill: ArtFill?, val stroke: Long?, val strokeWidth: Double = 0.0,
    override val sourceId: String? = null,
) : ArtPrimitive

data class ArtLine(
    val x0: Double, val y0: Double, val x1: Double, val y1: Double,
    val color: Long, val width: Double = 0.0, override val sourceId: String? = null, val dashed: Boolean = false,
) : ArtPrimitive

enum class CalloutKind {
    /** Equipment on the well axis (wellhead, tree, BOP, CT stack). */
    AXIS,

    /** Off-axis pressure equipment (tree wings, chokes). */
    SIDE,

    /** Site structures (cellar, rig floor, substructure). */
    SITE,

    /** Remote CT units of the full view (reel, cabin, power pack); labelled above the unit. */
    REMOTE,
}

/** A label to place in a callout column, anchored at a point of the drawing. */
data class ArtCallout(
    val text: String,
    val x: Double,
    val y: Double,
    val sourceId: String,
    val kind: CalloutKind = CalloutKind.AXIS,
)

data class ArtBounds(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
}

data class SurfaceArtworkResult(
    val primitives: List<ArtPrimitive>,
    val callouts: List<ArtCallout>,
    val bounds: ArtBounds?,
)

/** Palette of the surface artwork (0xAARRGGBB). */
object ArtColors {
    const val OUTLINE = 0xB31E2A33
    const val STEEL = 0xFFB9C3CC
    const val STEEL_DARK = 0xFF7D8994
    const val FORGED = 0xFFA3AEB8
    const val STUD = 0xFF4E5660
    const val NUT = 0xFF2F353C
    const val TREE_RED = 0xFFC0392B
    const val BOP_RED = 0xFFC62828
    const val HANDWHEEL = 0xFFD7322A
    const val OPERATOR_BLUE = 0xFF2F6DB5
    const val CT_BLUE = 0xFF4C7CA6
    const val CT_ORANGE = 0xFFD08C12
    const val INJECTOR_RED = 0xFFB3261E
    const val CHAIN = 0xFF3A3F45
    const val CONCRETE = 0xFFC2BDB1
    const val CONCRETE_DARK = 0xFF8F8A7F
    const val CELLAR_AIR = 0xFF9AA4AC
    const val RIG_STEEL = 0xFF6E7A85
    const val RIG_YELLOW = 0xFFF2C230
    const val GAUGE_FACE = 0xFFF7F7F2
    const val PIPE_DARK = 0xFF59636D
    const val ACTUATOR = 0xFF37474F
    const val HOSE = 0xFF26292D
    const val REEL_BLUE = 0xFF365F8C
    const val CABIN = 0xFFDCE3E8
    const val WINDOW = 0xFF8FB8D8
    const val POWER_PACK = 0xFF5E6E3F
}

/**
 * Builds the surface artwork for a scene. [pxPerMeter] is the drawing scale, used only to
 * drop details that would be smaller than a pixel (level of detail); pass
 * [Double.POSITIVE_INFINITY] for full detail.
 */
object SurfaceArtwork {

    fun build(scene: SchematicScene, pxPerMeter: Double = Double.POSITIVE_INFINITY): SurfaceArtworkResult {
        if (!scene.hasSurface) return SurfaceArtworkResult(emptyList(), emptyList(), null)
        val pen = Pen(pxPerMeter)
        val site = scene.surfaceSite
        val axis = scene.surfaceBlocks.filter { it.centerOffset == 0.0 }.sortedBy { it.bottomAboveGl }
        val remote = scene.surfaceBlocks.filter { it.centerOffset != 0.0 }

        site.cellar?.let { pen.cellar(it) }
        site.rig?.let { pen.rigBack(it) }
        remote.forEach { pen.remoteUnit(it) }
        val stubRadius = site.casingStubs.lastOrNull()?.outerRadius ?: 0.17
        site.casingStubs.forEach { pen.casingStub(it) }

        // Hydraulic hoses run behind the BOP stack to the accumulator side.
        if (site.rig != null) axis.filter { it.kind == SurfaceBlockKind.DRILLING_RAM || it.kind == SurfaceBlockKind.DRILLING_ANNULAR }.forEach { pen.hoses(it, site.rig) }

        val flanges = axis.mapIndexed { i, b -> flangesOf(b, axis.getOrNull(i - 1)) }
        axis.forEachIndexed { i, b -> pen.body(b, flanges[i], stubRadius) }
        // Flanged joints: mating flanges with studs through both and nuts on the outer faces.
        axis.forEachIndexed { i, b ->
            val f = flanges[i]
            val below = axis.getOrNull(i - 1)
            val fBelow = flanges.getOrNull(i - 1)
            val joined = below != null && fBelow?.top != null && f.bottom != null && abs(below.topAboveGl - b.bottomAboveGl) < 1e-6
            if (f.bottom != null && !joined) pen.flangeJoint(null, null, b.bottomAboveGl, f.bottom, f.thickness, b.sourceId, b.sourceId)
            if (joined) pen.flangeJoint(fBelow!!.top, fBelow.thickness, b.bottomAboveGl, f.bottom!!, f.thickness, below!!.sourceId, b.sourceId)
            val above = axis.getOrNull(i + 1)
            val aboveJoins = above != null && flanges[i + 1].bottom != null && abs(above.bottomAboveGl - b.topAboveGl) < 1e-6
            if (f.top != null && !aboveJoins) pen.flangeJoint(f.top, f.thickness, b.topAboveGl, null, 0.0, b.sourceId, b.sourceId)
        }
        axis.forEach { pen.decorations(it, flanges[axis.indexOf(it)]) }
        site.sideBlocks.forEach { pen.sideBlock(it) }
        site.rig?.let { pen.rigFront(it) }
        if (site.showsCoiledTubing) pen.coiledTubingPath(axis, remote)

        // Callouts: axis stack, off-axis equipment and site structures.
        axis.forEach { b -> pen.callouts += ArtCallout(b.label, calloutX(b, flanges[axis.indexOf(b)]), b.bottomAboveGl + b.height / 2, b.sourceId) }
        site.sideBlocks.forEach { b ->
            pen.callouts += ArtCallout(b.label, b.centerOffset + b.drawing.side * b.width / 2, b.topAboveGl, b.sourceId, CalloutKind.SIDE)
        }
        remote.forEach { b -> pen.callouts += ArtCallout(b.label, b.centerOffset, b.topAboveGl, b.sourceId, CalloutKind.REMOTE) }
        site.cellar?.let { c ->
            pen.callouts += ArtCallout(c.label, c.insideHalfWidth + c.wallThickness, c.floorAboveGl / 2, c.sourceId, CalloutKind.SITE)
        }
        site.rig?.let { r ->
            pen.callouts += ArtCallout("Rig floor", r.halfWidth, r.floorTopAboveGl, r.sourceId, CalloutKind.SITE)
            pen.callouts += ArtCallout("Substructure", r.halfWidth, r.beamsUndersideAboveGl * 0.45, r.sourceId, CalloutKind.SITE)
        }
        return SurfaceArtworkResult(pen.prims, pen.callouts, boundsOf(pen.prims))
    }

    /** Extent of the artwork in metres, or null when there is nothing to draw. */
    fun bounds(scene: SchematicScene): ArtBounds? = build(scene).bounds

    private fun calloutX(b: SurfaceBlock, f: Flanges) = max(b.width, max(f.top ?: 0.0, f.bottom ?: 0.0)) / 2

    // ---------------------------------------------------------------------------------

    /** Flange widths (null = no flange at that end) and thickness of an axis block. */
    internal data class Flanges(val bottom: Double?, val top: Double?, val thickness: Double)

    private fun flangesOf(b: SurfaceBlock, below: SurfaceBlock?): Flanges {
        val belowTop = below?.let { flangesOf(it, null).top }
        val big = (b.height * 0.14).coerceIn(0.045, 0.10)
        return when (b.kind) {
            SurfaceBlockKind.WELLHEAD -> if (b.drawing.wellheadSection == WellheadSectionType.CASING_HEAD_HOUSING) {
                Flanges(null, Widths.WELLHEAD_FLANGE, big)
            } else {
                Flanges(Widths.WELLHEAD_FLANGE, Widths.WELLHEAD_FLANGE, big)
            }
            SurfaceBlockKind.TREE_ADAPTER -> Flanges(belowTop ?: Widths.WELLHEAD_FLANGE, Widths.TREE_FLANGE, 0.065)
            SurfaceBlockKind.TREE_VALVE, SurfaceBlockKind.TREE_TEE, SurfaceBlockKind.TREE_CHOKE ->
                Flanges(Widths.TREE_FLANGE, Widths.TREE_FLANGE, 0.06)
            SurfaceBlockKind.TREE_CAP -> Flanges(Widths.TREE_FLANGE, null, 0.06)
            SurfaceBlockKind.CROSSOVER -> Flanges(belowTop ?: Widths.TREE_FLANGE, Widths.CT_FLANGE, 0.05)
            SurfaceBlockKind.FLOW_CROSS, SurfaceBlockKind.BOP -> Flanges(Widths.CT_FLANGE, Widths.CT_FLANGE, 0.05)
            SurfaceBlockKind.STRIPPER -> Flanges(Widths.CT_FLANGE, null, 0.05)
            SurfaceBlockKind.DRILLING_BELL_NIPPLE -> Flanges(Widths.BOP_FLANGE, null, big)
            SurfaceBlockKind.DRILLING_ANNULAR, SurfaceBlockKind.DRILLING_RAM, SurfaceBlockKind.DRILLING_SPOOL ->
                Flanges(Widths.BOP_FLANGE, Widths.BOP_FLANGE, big)
            SurfaceBlockKind.OTHER -> Flanges(Widths.TREE_FLANGE, Widths.TREE_FLANGE, 0.06)
            else -> Flanges(null, null, 0.0)
        }
    }

    private fun boundsOf(prims: List<ArtPrimitive>): ArtBounds? {
        if (prims.isEmpty()) return null
        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        fun add(x: Double, y: Double, pad: Double = 0.0) {
            minX = min(minX, x - pad); maxX = max(maxX, x + pad); minY = min(minY, y - pad); maxY = max(maxY, y + pad)
        }
        for (p in prims) when (p) {
            is ArtRect -> { add(p.x0, p.y0); add(p.x1, p.y1) }
            is ArtPolygon -> p.points.forEach { add(it.x, it.y, p.strokeWidth / 2) }
            is ArtCircle -> add(p.cx, p.cy, p.r + p.strokeWidth / 2)
            is ArtLine -> { add(p.x0, p.y0, p.width / 2); add(p.x1, p.y1, p.width / 2) }
        }
        return ArtBounds(minX, maxX, minY, maxY)
    }
}

// =======================================================================================
// Drawing pen
// =======================================================================================

private class Pen(val px: Double) {
    val prims = ArrayList<ArtPrimitive>()
    val callouts = ArrayList<ArtCallout>()

    /** True when a detail of [m] metres is at least ~1 px on screen. */
    fun shows(m: Double) = m * px >= 1.2

    fun rect(
        x0: Double, y0: Double, x1: Double, y1: Double, fill: ArtFill?, src: String?,
        stroke: Long? = ArtColors.OUTLINE, corner: Double = 0.0, dashed: Boolean = false,
    ) {
        prims += ArtRect(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1), fill, stroke, corner, src, dashed)
    }

    /** Rectangle given by distances [d0, d1] from the axis on side [s] (-1 or +1). */
    fun srect(s: Int, d0: Double, d1: Double, y0: Double, y1: Double, fill: ArtFill?, src: String?, stroke: Long? = ArtColors.OUTLINE, corner: Double = 0.0) =
        rect(s * d0, y0, s * d1, y1, fill, src, stroke, corner)

    fun poly(points: List<ArtPoint>, fill: ArtFill?, src: String?, stroke: Long? = ArtColors.OUTLINE) {
        prims += ArtPolygon(points, fill, stroke, 0.0, true, src)
    }

    fun polyline(points: List<ArtPoint>, color: Long, width: Double, src: String? = null) {
        prims += ArtPolygon(points, null, color, width, false, src)
    }

    fun circle(cx: Double, cy: Double, r: Double, fill: ArtFill?, src: String?, stroke: Long? = ArtColors.OUTLINE, strokeWidth: Double = 0.0) {
        prims += ArtCircle(cx, cy, r, fill, stroke, strokeWidth, src)
    }

    fun line(x0: Double, y0: Double, x1: Double, y1: Double, color: Long, width: Double = 0.0, src: String? = null, dashed: Boolean = false) {
        prims += ArtLine(x0, y0, x1, y1, color, width, src, dashed)
    }

    // -----------------------------------------------------------------------------------
    // Site: cellar, casing stubs, rig
    // -----------------------------------------------------------------------------------

    fun cellar(c: CellarDrawing) {
        val hw = c.insideHalfWidth
        val w = c.wallThickness
        val f = c.floorAboveGl
        val concrete = ArtFill.Solid(ArtColors.CONCRETE)
        rect(-hw, f, hw, 0.0, ArtFill.Depth(ArtColors.CELLAR_AIR), c.sourceId, stroke = null)
        rect(-hw - w, f - c.slabThickness, hw + w, f, concrete, c.sourceId, dashed = c.sizeIsSchematic)
        rect(-hw - w, f, -hw, 0.12, concrete, c.sourceId, dashed = c.sizeIsSchematic)
        rect(hw, f, hw + w, 0.12, concrete, c.sourceId, dashed = c.sizeIsSchematic)
        // inside shadow lines where walls meet the floor
        line(-hw, f, hw, f, ArtColors.CONCRETE_DARK, 0.02)
        if (shows(0.015)) {
            // deterministic aggregate speckles in the concrete
            var seed = 7
            fun next(): Double { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return seed / 2147483647.0 }
            repeat(70) {
                val inWall = next()
                val (x, y) = if (inWall < 0.6) {
                    val sx = if (next() < 0.5) -1 else 1
                    (sx * (hw + w * next())) to (f + (0.12 - f) * next())
                } else {
                    ((-hw - w) + (2 * (hw + w)) * next()) to (f - c.slabThickness * next())
                }
                circle(x, y, 0.012 + 0.01 * next(), ArtFill.Solid(ArtColors.CONCRETE_DARK), c.sourceId, stroke = null)
            }
        }
    }

    fun casingStub(s: CasingStubDrawing) {
        if (s.topAboveGl <= s.bottomAboveGl) return
        rect(-s.outerRadius, s.bottomAboveGl, s.outerRadius, s.topAboveGl, ArtFill.CylinderX(ArtColors.PIPE_DARK), s.sourceId)
        if (shows(0.02)) line(-s.outerRadius, s.topAboveGl, s.outerRadius, s.topAboveGl, ArtColors.NUT, 0.015)
    }

    fun rigBack(r: RigDrawing) {
        val beams = r.beamsUndersideAboveGl
        for (s in listOf(-1, 1)) {
            val outer = r.halfWidth - 0.22
            val inner = r.halfWidth - 1.5
            srect(s, outer - 0.32, outer + 0.32, -0.05, 0.12, ArtFill.Solid(ArtColors.CONCRETE), r.sourceId)
            srect(s, inner - 0.3, inner + 0.3, -0.05, 0.12, ArtFill.Solid(ArtColors.CONCRETE), r.sourceId)
            val panels = max(2, (beams / 2.2).roundToInt())
            val ph = (beams - 0.12) / panels
            for (j in 0 until panels) {
                val ya = 0.12 + j * ph
                val yb = ya + ph
                line(s * inner, ya, s * outer, yb, ArtColors.RIG_STEEL, 0.07, r.sourceId)
                line(s * outer, ya, s * inner, yb, ArtColors.RIG_STEEL, 0.07, r.sourceId)
                srect(s, inner, outer, yb - 0.05, yb + 0.03, ArtFill.CylinderY(ArtColors.RIG_STEEL), r.sourceId)
            }
            srect(s, outer - 0.18, outer + 0.18, 0.12, beams, ArtFill.CylinderX(ArtColors.RIG_STEEL), r.sourceId)
            srect(s, inner - 0.15, inner + 0.15, 0.12, beams, ArtFill.CylinderX(ArtColors.RIG_STEEL), r.sourceId)
        }
    }

    fun rigFront(r: RigDrawing) {
        val half = r.halfWidth
        val floor = r.floorTopAboveGl
        val deckBottom = floor - 0.30
        val steel = ArtFill.CylinderY(ArtColors.RIG_STEEL)
        if (deckBottom > r.beamsUndersideAboveGl) {
            rect(-(half - 1.35), r.beamsUndersideAboveGl, half - 1.35, deckBottom, steel, r.sourceId)
            if (shows(0.03)) {
                line(-(half - 1.35), r.beamsUndersideAboveGl + 0.05, half - 1.35, r.beamsUndersideAboveGl + 0.05, ArtColors.STUD, 0.0, r.sourceId)
                line(-(half - 1.35), deckBottom - 0.05, half - 1.35, deckBottom - 0.05, ArtColors.STUD, 0.0, r.sourceId)
            }
        }
        rect(-half, deckBottom, half, floor, steel, r.sourceId)
        rect(-half, floor - 0.08, half, floor, ArtFill.Solid(ArtColors.RIG_YELLOW), r.sourceId)
        // rotary table, master bushing and kelly bushing up to the RKB datum
        val rtTop = floor + max(0.25, (r.rkbAboveGl - floor) * 0.7)
        rect(-0.75, floor, 0.75, rtTop, ArtFill.CylinderX(0xFF2F3439), r.sourceId, corner = 0.03)
        rect(-0.30, rtTop, 0.30, max(r.rkbAboveGl, rtTop + 0.12), ArtFill.CylinderX(ArtColors.STEEL_DARK), r.sourceId)
        // handrails
        val yellow = ArtFill.Solid(ArtColors.RIG_YELLOW)
        val posts = max(2, (2 * half / 1.1).roundToInt())
        for (i in 0..posts) {
            val x = -half + 0.05 + i * (2 * half - 0.1) / posts
            rect(x - 0.025, floor, x + 0.025, floor + 1.05, yellow, r.sourceId, stroke = null)
        }
        rect(-half, floor + 1.0, half, floor + 1.06, yellow, r.sourceId)
        rect(-half, floor + 0.5, half, floor + 0.55, yellow, r.sourceId)
    }

    fun hoses(b: SurfaceBlock, r: RigDrawing) {
        if (!shows(0.03)) return
        val y = b.bottomAboveGl + b.height * 0.5
        val x0 = -(b.width / 2 + 0.2)
        val x1 = -(r.halfWidth - 1.5)
        val pts = (0..10).map { i ->
            val t = i / 10.0
            ArtPoint(x0 + (x1 - x0) * t, y - (y - 0.4) * t * t)
        }
        polyline(pts, ArtColors.HOSE, 0.04, b.sourceId)
        polyline(pts.map { ArtPoint(it.x - 0.08, it.y - 0.08) }, ArtColors.HOSE, 0.04, b.sourceId)
    }

    // -----------------------------------------------------------------------------------
    // Flanges and studs
    // -----------------------------------------------------------------------------------

    /**
     * Draws a flanged joint at elevation [y]: the lower flange (top of the item below, width
     * [lowerWidth]) and the upper flange (bottom of the item above, width [upperWidth]), with
     * studs through both and nuts on the outer faces. Either flange may be absent.
     */
    fun flangeJoint(
        lowerWidth: Double?, lowerThickness: Double?, y: Double,
        upperWidth: Double?, upperThickness: Double,
        lowerSrc: String, upperSrc: String,
    ) {
        val forged = ArtColors.FORGED
        var bottom = y
        var top = y
        if (lowerWidth != null && lowerThickness != null) {
            rect(-lowerWidth / 2, y - lowerThickness, lowerWidth / 2, y, ArtFill.CylinderX(forged), lowerSrc, corner = 0.008)
            bottom = y - lowerThickness
        }
        if (upperWidth != null) {
            rect(-upperWidth / 2, y, upperWidth / 2, y + upperThickness, ArtFill.CylinderX(forged), upperSrc, corner = 0.008)
            top = y + upperThickness
        }
        val width = listOfNotNull(lowerWidth, upperWidth).minOrNull() ?: return
        if (lowerWidth != null && upperWidth != null && shows(0.01)) line(-width / 2, y, width / 2, y, ArtColors.STUD, 0.0, upperSrc)
        studs(-width / 2 + 0.035, width / 2 - 0.035, bottom, top, upperSrc)
    }

    /** Visible studs with nuts across a flange (side elevation). */
    fun studs(x0: Double, x1: Double, yBottom: Double, yTop: Double, src: String) {
        val count = ((x1 - x0) / 0.105).roundToInt().coerceIn(3, 10)
        val spacing = (x1 - x0) / count
        if (!shows(spacing * 0.35)) return
        for (i in 0 until count) {
            val x = x0 + (i + 0.5) * spacing
            rect(x - 0.011, yBottom - 0.045, x + 0.011, yTop + 0.045, ArtFill.Solid(ArtColors.STUD), src, stroke = null)
            rect(x - 0.026, yTop, x + 0.026, yTop + 0.032, ArtFill.CylinderX(ArtColors.NUT), src, stroke = null)
            rect(x - 0.026, yBottom - 0.032, x + 0.026, yBottom, ArtFill.CylinderX(ArtColors.NUT), src, stroke = null)
        }
    }

    /** Vertical flange on a horizontal branch, between distances d..d+thick from the axis. */
    fun flangeV(s: Int, d: Double, thick: Double, y: Double, height: Double, src: String) {
        srect(s, d, d + thick, y - height / 2, y + height / 2, ArtFill.CylinderY(ArtColors.FORGED), src, corner = 0.006)
        val count = (height / 0.1).roundToInt().coerceIn(2, 5)
        val spacing = (height - 0.06) / count
        if (!shows(spacing * 0.35)) return
        for (i in 0 until count) {
            val yy = y - height / 2 + 0.03 + (i + 0.5) * spacing
            srect(s, d - 0.03, d + thick + 0.03, yy - 0.009, yy + 0.009, ArtFill.Solid(ArtColors.STUD), src, stroke = null)
        }
    }

    // -----------------------------------------------------------------------------------
    // Axis stack bodies
    // -----------------------------------------------------------------------------------

    fun body(b: SurfaceBlock, f: SurfaceArtwork.Flanges, stubRadius: Double) {
        val yb = b.bottomAboveGl + (if (f.bottom != null) f.thickness else 0.0)
        val yt = b.topAboveGl - (if (f.top != null) f.thickness else 0.0)
        val src = b.sourceId
        when (b.kind) {
            SurfaceBlockKind.WELLHEAD -> wellheadSection(b, yb, yt, stubRadius)
            SurfaceBlockKind.TREE_ADAPTER -> adapter(b, yb, yt, Widths.WELLHEAD_BODY / 2 * 0.95, Widths.TREE_BODY / 2, ArtColors.STEEL)
            SurfaceBlockKind.TREE_VALVE -> verticalValve(b, yb, yt, ArtColors.TREE_RED)
            SurfaceBlockKind.TREE_TEE -> flowTee(b, yb, yt)
            SurfaceBlockKind.TREE_CHOKE -> verticalValve(b, yb, yt, ArtColors.TREE_RED)
            SurfaceBlockKind.TREE_CAP -> treeCap(b, yb, yt)
            SurfaceBlockKind.CROSSOVER -> adapter(b, yb, yt, Widths.TREE_BODY / 2, Widths.CT_BODY / 2, ArtColors.STEEL)
            SurfaceBlockKind.FLOW_CROSS -> ctFlowCross(b, yb, yt)
            SurfaceBlockKind.BOP -> ctBop(b, yb, yt)
            SurfaceBlockKind.RISER -> riser(b, yb, yt)
            SurfaceBlockKind.STRIPPER -> stripper(b, yb, yt)
            SurfaceBlockKind.INJECTOR -> injector(b)
            SurfaceBlockKind.GOOSENECK -> gooseneck(b)
            SurfaceBlockKind.DRILLING_SPOOL -> {
                rect(-Widths.SPOOL_BODY / 2, yb, Widths.SPOOL_BODY / 2, yt, ArtFill.CylinderX(ArtColors.BOP_RED), src, dashed = b.sizeIsSchematic)
                outlets(b, Widths.SPOOL_BODY / 2, (yb + yt) / 2, ArtColors.STEEL)
            }
            SurfaceBlockKind.DRILLING_RAM -> ramPreventer(b, yb, yt)
            SurfaceBlockKind.DRILLING_ANNULAR -> annular(b, yb, yt)
            SurfaceBlockKind.DRILLING_BELL_NIPPLE -> bellNipple(b, yb, yt)
            else -> rect(-b.width / 2, yb, b.width / 2, yt, ArtFill.CylinderX(ArtColors.STEEL), src, dashed = b.sizeIsSchematic)
        }
    }

    /** Parts drawn over the flanges (e.g. tubing-head lock screws). */
    fun decorations(b: SurfaceBlock, f: SurfaceArtwork.Flanges) {
        if (b.kind != SurfaceBlockKind.WELLHEAD || b.drawing.wellheadSection != WellheadSectionType.TUBING_HEAD_SPOOL) return
        if (!shows(0.02)) return
        val y = b.topAboveGl - f.thickness / 2
        val edge = (f.top ?: Widths.WELLHEAD_FLANGE) / 2
        for (s in listOf(-1, 1)) {
            srect(s, edge, edge + 0.07, y - 0.012, y + 0.012, ArtFill.CylinderY(ArtColors.STUD), b.sourceId)
            srect(s, edge + 0.07, edge + 0.10, y - 0.024, y + 0.024, ArtFill.CylinderY(ArtColors.NUT), b.sourceId)
        }
    }

    private fun wellheadSection(b: SurfaceBlock, yb: Double, yt: Double, stubRadius: Double) {
        val w = Widths.WELLHEAD_BODY / 2
        val src = b.sourceId
        val metal = ArtFill.CylinderX(ArtColors.STEEL)
        val dashed = b.sizeIsSchematic
        var bodyBottom = yb
        if (b.drawing.wellheadSection == WellheadSectionType.CASING_HEAD_HOUSING) {
            // slip-on / threaded bottom on the surface casing, then the housing body
            val neck = b.bottomAboveGl + (yt - b.bottomAboveGl) * 0.22
            val r = stubRadius + 0.035
            poly(listOf(ArtPoint(-r, b.bottomAboveGl), ArtPoint(r, b.bottomAboveGl), ArtPoint(w, neck), ArtPoint(-w, neck)), metal, src)
            rect(-r - 0.01, b.bottomAboveGl, r + 0.01, b.bottomAboveGl + 0.03, ArtFill.Solid(ArtColors.STEEL_DARK), src)
            bodyBottom = neck
        }
        rect(-w, bodyBottom, w, yt, metal, src, dashed = dashed)
        if (shows(0.02)) {
            // test / injection ports on the body
            circle(w * 0.45, bodyBottom + (yt - bodyBottom) * 0.72, 0.02, ArtFill.Solid(ArtColors.STEEL_DARK), src)
            circle(-w * 0.45, bodyBottom + (yt - bodyBottom) * 0.72, 0.02, ArtFill.Solid(ArtColors.STEEL_DARK), src)
        }
        val outletY = if (b.drawing.wellheadSection == WellheadSectionType.CASING_HEAD_HOUSING) {
            bodyBottom + (yt - bodyBottom) * 0.45
        } else {
            (yb + yt) / 2
        }
        outlets(b, w, outletY, ArtColors.STEEL)
    }

    /** Side outlets with their fittings, from the body edge [edge] outward. */
    private fun outlets(b: SurfaceBlock, edge: Double, y: Double, valveColor: Long) {
        for (o in b.drawing.outlets) {
            val s = if (o.side < 0) -1 else 1
            val src = b.sourceId
            var d = edge
            srect(s, d, d + 0.10, y - 0.08, y + 0.08, ArtFill.CylinderY(ArtColors.STEEL), src)
            d += 0.10
            loop@ for (fit in o.fittings) {
                when (fit) {
                    OutletFitting.COMPANION_FLANGE -> { flangeV(s, d, 0.05, y, 0.30, src); d += 0.05 }
                    OutletFitting.GATE_VALVE, OutletFitting.ACTUATED_GATE_VALVE -> {
                        flangeV(s, d, 0.045, y, 0.30, src)
                        srect(s, d + 0.045, d + 0.345, y - 0.12, y + 0.12, ArtFill.CylinderY(valveColor), src, corner = 0.02)
                        flangeV(s, d + 0.345, 0.045, y, 0.30, src)
                        val xc = s * (d + 0.195)
                        if (fit == OutletFitting.ACTUATED_GATE_VALVE) {
                            actuatorUp(xc, y + 0.12, 0.11, 0.28, src)
                        } else {
                            rect(xc - 0.06, y + 0.12, xc + 0.06, y + 0.22, ArtFill.CylinderX(valveColor), src)
                            handwheel(xc, y, 0.15, src)
                        }
                        d += 0.39
                    }
                    OutletFitting.NEEDLE_VALVE_GAUGE -> {
                        srect(s, d, d + 0.10, y - 0.05, y + 0.05, ArtFill.CylinderY(ArtColors.STEEL), src)
                        val xc = s * (d + 0.05)
                        rect(xc - 0.015, y + 0.05, xc + 0.015, y + 0.17, ArtFill.Solid(ArtColors.STEEL_DARK), src)
                        gauge(xc, y + 0.24, 0.07, src)
                        d += 0.10
                    }
                    OutletFitting.BLIND_FLANGE -> { flangeV(s, d, 0.06, y, 0.30, src); d += 0.06; break@loop }
                    OutletFitting.BULL_PLUG -> {
                        srect(s, d, d + 0.06, y - 0.075, y + 0.075, ArtFill.CylinderY(ArtColors.STEEL_DARK), src)
                        val pts = (0..8).map { i ->
                            val a = -PI / 2 + PI * i / 8
                            ArtPoint(s * (d + 0.06 + 0.06 * cos(a)), y + 0.075 * sin(a))
                        }
                        poly(pts, ArtFill.CylinderY(ArtColors.STEEL_DARK), src)
                        d += 0.12
                        break@loop
                    }
                    OutletFitting.LINE_CONNECTION -> {
                        srect(s, d, d + 0.22, y - 0.05, y + 0.05, ArtFill.CylinderY(ArtColors.PIPE_DARK), src)
                        srect(s, d + 0.22, d + 0.28, y - 0.08, y + 0.08, ArtFill.CylinderY(ArtColors.STEEL_DARK), src)
                        d += 0.28
                    }
                }
            }
        }
    }

    private fun adapter(b: SurfaceBlock, yb: Double, yt: Double, rBottom: Double, rTop: Double, color: Long) {
        poly(
            listOf(ArtPoint(-rBottom, yb), ArtPoint(rBottom, yb), ArtPoint(rTop, yt), ArtPoint(-rTop, yt)),
            ArtFill.CylinderX(color), b.sourceId,
        )
    }

    private fun verticalValve(b: SurfaceBlock, yb: Double, yt: Double, color: Long) {
        val src = b.sourceId
        val w = Widths.TREE_BODY / 2
        rect(-w, yb, w, yt, ArtFill.CylinderX(color), src, corner = 0.03, dashed = b.sizeIsSchematic)
        val cy = (yb + yt) / 2
        when (b.drawing.operator) {
            ValveOperator.HYDRAULIC, ValveOperator.PNEUMATIC -> {
                srect(-1, w, w + 0.08, cy - 0.08, cy + 0.08, ArtFill.CylinderY(color), src)
                val h = min(0.36, (yt - yb) * 0.75)
                srect(-1, w + 0.08, w + 0.44, cy - h / 2, cy + h / 2, ArtFill.CylinderY(ArtColors.ACTUATOR), src, corner = 0.02)
                val pts = (0..8).map { i ->
                    val a = PI / 2 + PI * i / 8
                    ArtPoint(-(w + 0.44) + 0.09 * cos(a), cy + h / 2 * sin(a))
                }
                poly(pts, ArtFill.CylinderY(ArtColors.ACTUATOR), src)
                srect(-1, w + 0.53, w + 0.60, cy - 0.015, cy + 0.015, ArtFill.Solid(ArtColors.STEEL), src)
            }
            else -> {
                rect(-0.09, cy - 0.09, 0.09, cy + 0.09, ArtFill.Radial(color), src)
                handwheel(0.0, cy, min(0.27, (yt - yb) * 0.48), src)
            }
        }
    }

    private fun flowTee(b: SurfaceBlock, yb: Double, yt: Double) {
        val src = b.sourceId
        val w = Widths.TREE_FLANGE / 2
        rect(-w, yb, w, yt, ArtFill.CylinderX(ArtColors.TREE_RED), src, corner = 0.02, dashed = b.sizeIsSchematic)
        val cy = (yb + yt) / 2
        for (s in b.drawing.branchSides) {
            srect(s, w, Widths.TREE_BRANCH_END - 0.05, cy - 0.11, cy + 0.11, ArtFill.CylinderY(ArtColors.TREE_RED), src)
            flangeV(s, Widths.TREE_BRANCH_END - 0.05, 0.05, cy, Widths.WING_HEIGHT - 0.02, src)
        }
        if (shows(0.02)) circle(0.0, cy, 0.05, ArtFill.Radial(ArtColors.TREE_RED), src)
    }

    private fun treeCap(b: SurfaceBlock, yb: Double, yt: Double) {
        val src = b.sourceId
        val nub = min(0.07, (yt - yb) * 0.3)
        rect(-0.15, yb, 0.15, yt - nub, ArtFill.CylinderX(ArtColors.TREE_RED), src, corner = 0.02, dashed = b.sizeIsSchematic)
        rect(-0.06, yt - nub, 0.06, yt, ArtFill.CylinderX(ArtColors.STEEL_DARK), src, corner = 0.01)
        if (shows(0.02)) for (i in 1..3) {
            val y = yb + (yt - nub - yb) * i / 4
            line(-0.15, y, 0.15, y, ArtColors.OUTLINE, 0.0, src)
        }
    }

    // ---- CT pressure control ------------------------------------------------------------

    private fun ctFlowCross(b: SurfaceBlock, yb: Double, yt: Double) {
        val src = b.sourceId
        val w = Widths.CT_BODY / 2
        rect(-w, yb, w, yt, ArtFill.CylinderX(ArtColors.CT_BLUE), src, corner = 0.02, dashed = b.sizeIsSchematic)
        val cy = (yb + yt) / 2
        for (s in b.drawing.branchSides) {
            srect(s, w, w + 0.09, cy - 0.07, cy + 0.07, ArtFill.CylinderY(ArtColors.STEEL), src)
            flangeV(s, w + 0.09, 0.04, cy, 0.26, src)
            srect(s, w + 0.13, w + 0.29, cy - 0.08, cy + 0.08, ArtFill.CylinderY(ArtColors.STEEL_DARK), src, corner = 0.015)
            val xc = s * (w + 0.21)
            rect(xc - 0.012, cy + 0.08, xc + 0.012, cy + 0.18, ArtFill.Solid(ArtColors.NUT), src, stroke = null)
            rect(xc - 0.09, cy + 0.18, xc + 0.09, cy + 0.21, ArtFill.Solid(ArtColors.NUT), src, stroke = null)
            srect(s, w + 0.29, w + 0.45, cy - 0.04, cy + 0.04, ArtFill.CylinderY(ArtColors.PIPE_DARK), src)
            srect(s, w + 0.45, w + 0.50, cy - 0.07, cy + 0.07, ArtFill.CylinderY(ArtColors.STEEL_DARK), src)
        }
    }

    private fun ctBop(b: SurfaceBlock, yb: Double, yt: Double) {
        val src = b.sourceId
        val w = 0.25
        rect(-w, yb, w, yt, ArtFill.CylinderX(ArtColors.CT_BLUE), src, corner = 0.03, dashed = b.sizeIsSchematic)
        val shear = b.drawing.stackType == StackComponentType.SHEAR_SEAL_BOP
        val n = if (shear) 1 else ((yt - yb) / 0.34).roundToInt().coerceIn(1, 4)
        val ch = (yt - yb) / n
        for (i in 0 until n) {
            val cy = yt - (i + 0.5) * ch
            val len = if (shear) 0.40 else 0.26
            for (s in listOf(-1, 1)) {
                srect(s, w, w + 0.05, cy - ch * 0.32, cy + ch * 0.32, ArtFill.CylinderY(ArtColors.CT_BLUE), src)
                srect(s, w + 0.05, w + 0.05 + len, cy - ch * 0.24, cy + ch * 0.24, ArtFill.CylinderY(ArtColors.STEEL), src, corner = 0.01)
                srect(s, w + 0.05 + len, w + 0.11 + len, cy - ch * 0.28, cy + ch * 0.28, ArtFill.CylinderY(ArtColors.NUT), src, corner = 0.01)
            }
            if (i > 0 && shows(0.015)) circle(0.0, yt - i * ch, 0.025, ArtFill.Radial(ArtColors.STEEL), src)
        }
    }

    private fun riser(b: SurfaceBlock, yb: Double, yt: Double) {
        val src = b.sourceId
        val r = Widths.RISER / 2
        rect(-r, yb + 0.08, r, yt - 0.08, ArtFill.CylinderX(ArtColors.STEEL), src, dashed = b.sizeIsSchematic)
        union(yb, src)
        union(yt - 0.08, src)
        if (yt - yb > 2.0) union((yb + yt) / 2 - 0.04, src)
    }

    private fun union(y: Double, src: String) {
        rect(-0.16, y, 0.16, y + 0.08, ArtFill.CylinderX(ArtColors.STEEL_DARK), src, corner = 0.01)
        if (shows(0.02)) for (x in listOf(-0.16, 0.0, 0.16)) {
            rect(x - 0.018, y + 0.015, x + 0.018, y + 0.065, ArtFill.Solid(ArtColors.NUT), src, stroke = null)
        }
    }

    private fun stripper(b: SurfaceBlock, yb: Double, yt: Double) {
        val src = b.sourceId
        val w = Widths.CT_BODY / 2
        val shoulder = yb + (yt - yb) * 0.62
        rect(-w, yb, w, shoulder, ArtFill.CylinderX(ArtColors.STEEL), src, dashed = b.sizeIsSchematic)
        poly(listOf(ArtPoint(-w, shoulder), ArtPoint(w, shoulder), ArtPoint(0.09, yt), ArtPoint(-0.09, yt)), ArtFill.CylinderX(ArtColors.STEEL), src)
        // side door window and hydraulic ports
        rect(-0.08, yb + (shoulder - yb) * 0.3, 0.08, yb + (shoulder - yb) * 0.75, ArtFill.Solid(ArtColors.NUT), src)
        if (shows(0.02)) {
            srect(1, w, w + 0.06, yb + (shoulder - yb) * 0.25, yb + (shoulder - yb) * 0.33, ArtFill.Solid(ArtColors.STEEL_DARK), src)
            srect(1, w, w + 0.06, yb + (shoulder - yb) * 0.6, yb + (shoulder - yb) * 0.68, ArtFill.Solid(ArtColors.STEEL_DARK), src)
        }
    }

    private fun injector(b: SurfaceBlock) {
        val src = b.sourceId
        val y0 = b.bottomAboveGl
        val h = b.height
        val hw = b.width / 2
        val red = ArtFill.CylinderX(ArtColors.INJECTOR_RED)
        rect(-hw, y0, hw, y0 + 0.10, ArtFill.CylinderY(ArtColors.NUT), src)
        // chains and sprockets
        for (s in listOf(-1, 1)) {
            srect(s, 0.10, 0.42, y0 + 0.25, y0 + h - 0.25, ArtFill.Solid(ArtColors.CHAIN), src, corner = 0.14)
            circle(s * 0.26, y0 + 0.40, 0.13, ArtFill.Radial(ArtColors.STEEL), src)
            circle(s * 0.26, y0 + h - 0.40, 0.13, ArtFill.Radial(ArtColors.STEEL), src)
            circle(s * 0.26, y0 + 0.40, 0.04, ArtFill.Solid(ArtColors.NUT), src, stroke = null)
            circle(s * 0.26, y0 + h - 0.40, 0.04, ArtFill.Solid(ArtColors.NUT), src, stroke = null)
            if (shows(0.03)) {
                var y = y0 + 0.55
                while (y < y0 + h - 0.55) {
                    srect(s, 0.10, 0.155, y, y + 0.06, ArtFill.Solid(ArtColors.STEEL_DARK), src, stroke = null)
                    y += 0.09
                }
            }
            // frame posts and hydraulic motors
            srect(s, hw - 0.08, hw, y0 + 0.10, y0 + h, red, src)
            circle(s * (hw - 0.28), y0 + h - 0.42, 0.17, ArtFill.Radial(ArtColors.STEEL_DARK), src)
            circle(s * (hw - 0.28), y0 + h - 0.42, 0.06, ArtFill.Solid(ArtColors.NUT), src, stroke = null)
        }
        rect(-hw, y0 + h - 0.10, hw, y0 + h, red, src)
        rect(-hw, y0 + 0.10, hw, y0 + 0.18, red, src)
        if (b.sizeIsSchematic) rect(-hw, y0, hw, y0 + h, null, src, dashed = true)
    }

    private fun gooseneck(b: SurfaceBlock) {
        val src = b.sourceId
        val r = b.width / 2
        val y0 = b.bottomAboveGl
        val arch = (0..24).map { i ->
            val t = PI * i / 24
            ArtPoint(-r + r * cos(t), y0 + b.height * sin(t))
        }
        polyline(arch, ArtColors.STEEL_DARK, 0.14, src)
        if (shows(0.03)) arch.filterIndexed { i, _ -> i % 3 == 1 }.forEach { p ->
            circle(p.x, p.y, 0.05, ArtFill.Radial(ArtColors.STEEL), src)
        }
    }

    // ---- Drilling BOP --------------------------------------------------------------------

    private fun ramPreventer(b: SurfaceBlock, yb: Double, yt: Double) {
        val src = b.sourceId
        val w = Widths.RAM_BODY / 2
        rect(-w, yb, w, yt, ArtFill.CylinderX(ArtColors.BOP_RED), src, corner = 0.05, dashed = b.sizeIsSchematic)
        val n = b.drawing.rams.size.takeIf { it > 0 } ?: if (b.drawing.bopType == DrillingBopComponentType.DOUBLE_RAM_PREVENTER) 2 else 1
        val ch = (yt - yb) / n
        for (i in 0 until n) {
            val cy = yt - (i + 0.5) * ch
            for (s in listOf(-1, 1)) {
                srect(s, w, w + 0.07, cy - ch * 0.32, cy + ch * 0.32, ArtFill.CylinderY(ArtColors.BOP_RED), src, corner = 0.01)
                srect(s, w + 0.07, w + 0.41, cy - ch * 0.22, cy + ch * 0.22, ArtFill.CylinderY(ArtColors.BOP_RED), src, corner = 0.01)
                srect(s, w + 0.41, w + 0.49, cy - ch * 0.26, cy + ch * 0.26, ArtFill.CylinderY(ArtColors.OPERATOR_BLUE), src, corner = 0.01)
                srect(s, w + 0.49, w + 0.57, cy - 0.03, cy + 0.03, ArtFill.CylinderY(ArtColors.STEEL), src)
                srect(s, w + 0.57, w + 0.60, cy - ch * 0.18, cy + ch * 0.18, ArtFill.Solid(ArtColors.NUT), src)
                if (shows(0.015)) {
                    circle(s * (w + 0.035), cy + ch * 0.22, 0.016, ArtFill.Solid(ArtColors.NUT), src, stroke = null)
                    circle(s * (w + 0.035), cy - ch * 0.22, 0.016, ArtFill.Solid(ArtColors.NUT), src, stroke = null)
                }
            }
            if (i > 0) line(-w, yt - i * ch, w, yt - i * ch, ArtColors.OUTLINE, 0.0, src)
        }
        if (shows(0.02)) circle(0.0, (yb + yt) / 2, 0.06, ArtFill.Radial(ArtColors.BOP_RED), src)
        outlets(b, w, (yb + yt) / 2, ArtColors.STEEL)
    }

    private fun annular(b: SurfaceBlock, yb: Double, yt: Double) {
        val src = b.sourceId
        val rBody = Widths.ANNULAR_BODY / 2
        val rNeck = 0.31
        val shoulder = yb + (yt - yb) * 0.45
        val right = (0..12).map { i ->
            val t = i / 12.0
            ArtPoint(rNeck + (rBody - rNeck) * cos(t * PI / 2), shoulder + (yt - shoulder) * sin(t * PI / 2))
        }
        val points = listOf(ArtPoint(-rBody, yb), ArtPoint(rBody, yb)) + right + right.asReversed().map { ArtPoint(-it.x, it.y) }
        poly(points, ArtFill.CylinderX(ArtColors.BOP_RED), src)
        if (b.sizeIsSchematic) rect(-rBody, yb, rBody, yt, null, src, dashed = true)
        if (shows(0.02)) {
            for (s in listOf(-1, 1)) circle(s * rBody * 0.78, shoulder + (yt - shoulder) * 0.45, 0.04, null, src, ArtColors.NUT, 0.018)
            srect(-1, rBody, rBody + 0.06, yb + (shoulder - yb) * 0.3, yb + (shoulder - yb) * 0.45, ArtFill.Solid(ArtColors.STEEL_DARK), src)
            srect(-1, rBody, rBody + 0.06, yb + (shoulder - yb) * 0.65, yb + (shoulder - yb) * 0.8, ArtFill.Solid(ArtColors.STEEL_DARK), src)
        }
    }

    private fun bellNipple(b: SurfaceBlock, yb: Double, yt: Double) {
        val src = b.sourceId
        val r = Widths.BELL_NIPPLE / 2
        val flare = min(0.25, (yt - yb) * 0.2)
        rect(-r, yb, r, yt - flare, ArtFill.CylinderX(ArtColors.STEEL), src, dashed = b.sizeIsSchematic)
        poly(listOf(ArtPoint(-r, yt - flare), ArtPoint(r, yt - flare), ArtPoint(r + 0.11, yt), ArtPoint(-r - 0.11, yt)), ArtFill.CylinderX(ArtColors.STEEL), src)
        val fy = yb + (yt - yb) * 0.7
        poly(
            listOf(ArtPoint(r, fy + 0.11), ArtPoint(r + 0.95, fy + 0.02), ArtPoint(r + 0.95, fy - 0.20), ArtPoint(r, fy - 0.11)),
            ArtFill.CylinderY(ArtColors.STEEL_DARK), src,
        )
        val ly = yb + (yt - yb) * 0.5
        srect(-1, r, r + 0.30, ly - 0.035, ly + 0.035, ArtFill.CylinderY(ArtColors.STEEL_DARK), src)
    }

    // -----------------------------------------------------------------------------------
    // Off-axis equipment
    // -----------------------------------------------------------------------------------

    fun sideBlock(b: SurfaceBlock) {
        val s = if (b.drawing.side < 0) -1 else 1
        val src = b.sourceId
        val d0 = abs(b.centerOffset) - b.width / 2
        val d1 = d0 + b.width
        val cy = b.bottomAboveGl + b.height / 2
        val bodyColor = ArtColors.TREE_RED
        flangeV(s, d0, 0.045, cy, Widths.WING_HEIGHT - 0.02, src)
        flangeV(s, d1 - 0.045, 0.045, cy, Widths.WING_HEIGHT - 0.02, src)
        val xc = s * (d0 + d1) / 2
        when (b.kind) {
            SurfaceBlockKind.TREE_CHOKE -> {
                srect(s, d0 + 0.045, d1 - 0.045, cy - 0.13, cy + 0.13, ArtFill.CylinderY(bodyColor), src, corner = 0.02)
                rect(xc - 0.07, cy + 0.13, xc + 0.07, cy + 0.36, ArtFill.CylinderX(ArtColors.STEEL), src)
                rect(xc - 0.16, cy + 0.36, xc + 0.16, cy + 0.39, ArtFill.Solid(ArtColors.HANDWHEEL), src)
                circle(xc, cy + 0.40, 0.03, ArtFill.Solid(ArtColors.NUT), src, stroke = null)
            }
            else -> {
                srect(s, d0 + 0.045, d1 - 0.045, cy - 0.15, cy + 0.15, ArtFill.CylinderY(bodyColor), src, corner = 0.02)
                when (b.drawing.operator) {
                    ValveOperator.HYDRAULIC, ValveOperator.PNEUMATIC -> actuatorUp(xc, cy + 0.15, 0.13, 0.36, src)
                    else -> {
                        rect(xc - 0.08, cy - 0.08, xc + 0.08, cy + 0.08, ArtFill.Radial(bodyColor), src)
                        handwheel(xc, cy, min(0.22, (d1 - d0) / 2 + 0.03), src)
                    }
                }
            }
        }
        if (b.sizeIsSchematic) rect(s * d0, b.bottomAboveGl, s * d1, b.topAboveGl, null, src, dashed = true)
        if (b.drawing.isBranchEnd) {
            flangeV(s, d1, 0.05, cy, 0.34, src)
            if (b.kind == SurfaceBlockKind.TREE_CHOKE) {
                srect(s, d1 + 0.05, d1 + 0.45, cy - 0.07, cy + 0.07, ArtFill.CylinderY(ArtColors.PIPE_DARK), src)
                srect(s, d1 + 0.45, d1 + 0.51, cy - 0.10, cy + 0.10, ArtFill.CylinderY(ArtColors.STEEL_DARK), src)
            } else {
                srect(s, d1 + 0.05, d1 + 0.11, cy - 0.08, cy + 0.08, ArtFill.CylinderY(ArtColors.STEEL_DARK), src)
                gauge(s * (d1 + 0.08), cy + 0.26, 0.08, src)
                rect(s * (d1 + 0.08) - 0.015, cy + 0.08, s * (d1 + 0.08) + 0.015, cy + 0.18, ArtFill.Solid(ArtColors.STEEL_DARK), src)
            }
        }
    }

    /** CT surface units of the full view (reel, control cabin, power pack). */
    fun remoteUnit(b: SurfaceBlock) {
        val src = b.sourceId
        val cx = b.centerOffset
        val y0 = b.bottomAboveGl
        when (b.kind) {
            SurfaceBlockKind.REEL -> {
                val r = min(b.width, b.height) / 2 - 0.1
                val cy = y0 + 0.1 + r
                rect(cx - r - 0.3, y0, cx + r + 0.3, y0 + 0.22, ArtFill.CylinderY(ArtColors.RIG_STEEL), src)
                poly(listOf(ArtPoint(cx - r * 0.7, y0 + 0.22), ArtPoint(cx - 0.15, cy), ArtPoint(cx + 0.15, cy), ArtPoint(cx + r * 0.7, y0 + 0.22)), ArtFill.Solid(ArtColors.RIG_STEEL), src)
                circle(cx, cy, r, ArtFill.Radial(ArtColors.REEL_BLUE), src)
                circle(cx, cy, r * 0.72, null, src, ArtColors.CT_ORANGE, r * 0.34)
                circle(cx, cy, r * 0.5, ArtFill.Radial(ArtColors.STEEL_DARK), src)
                circle(cx, cy, r * 0.12, ArtFill.Solid(ArtColors.NUT), src)
                rect(cx - r, cy + r + 0.05, cx + r, cy + r + 0.14, ArtFill.CylinderY(ArtColors.STEEL_DARK), src)
            }
            SurfaceBlockKind.CONTROL_CABIN -> {
                val hw = b.width / 2
                rect(cx - hw, y0, cx + hw, y0 + 0.2, ArtFill.CylinderY(ArtColors.RIG_STEEL), src)
                rect(cx - hw, y0 + 0.2, cx + hw, y0 + b.height, ArtFill.CylinderX(ArtColors.CABIN), src, corner = 0.05)
                rect(cx - hw * 0.8, y0 + b.height * 0.5, cx + hw * 0.2, y0 + b.height * 0.85, ArtFill.Solid(ArtColors.WINDOW), src)
                rect(cx + hw * 0.4, y0 + 0.25, cx + hw * 0.85, y0 + b.height * 0.8, ArtFill.CylinderX(ArtColors.STEEL), src)
                rect(cx - hw - 0.05, y0 + b.height - 0.08, cx + hw + 0.05, y0 + b.height, ArtFill.Solid(ArtColors.RIG_STEEL), src)
            }
            SurfaceBlockKind.POWER_PACK -> {
                val hw = b.width / 2
                rect(cx - hw, y0, cx + hw, y0 + 0.18, ArtFill.CylinderY(ArtColors.RIG_STEEL), src)
                rect(cx - hw, y0 + 0.18, cx + hw, y0 + b.height, ArtFill.CylinderX(ArtColors.POWER_PACK), src, corner = 0.05)
                if (shows(0.03)) {
                    var y = y0 + 0.4
                    while (y < y0 + b.height - 0.3) {
                        line(cx - hw * 0.75, y, cx - hw * 0.05, y, ArtColors.NUT, 0.02, src)
                        y += 0.12
                    }
                }
                rect(cx + hw * 0.5, y0 + b.height, cx + hw * 0.62, y0 + b.height + 0.45, ArtFill.CylinderX(ArtColors.STEEL_DARK), src)
            }
            else -> rect(cx - b.width / 2, y0, cx + b.width / 2, y0 + b.height, ArtFill.CylinderX(ArtColors.STEEL), src, dashed = true)
        }
    }

    /** CT where it is exposed: through the injector, over the gooseneck and to the reel. */
    fun coiledTubingPath(axis: List<SurfaceBlock>, remote: List<SurfaceBlock>) {
        val injector = axis.firstOrNull { it.kind == SurfaceBlockKind.INJECTOR } ?: return
        val stripper = axis.firstOrNull { it.kind == SurfaceBlockKind.STRIPPER }
        val from = stripper?.topAboveGl ?: injector.bottomAboveGl
        line(0.0, from, 0.0, injector.topAboveGl, ArtColors.CT_ORANGE, 0.045, injector.sourceId)
        val goose = axis.firstOrNull { it.kind == SurfaceBlockKind.GOOSENECK } ?: return
        val r = goose.width / 2
        val arch = (0..24).map { i ->
            val t = PI * i / 24
            ArtPoint(-r + (r - 0.08) * cos(t), goose.bottomAboveGl + (goose.height - 0.08) * sin(t))
        }
        polyline(arch, ArtColors.CT_ORANGE, 0.045, goose.sourceId)
        val reel = remote.firstOrNull { it.kind == SurfaceBlockKind.REEL } ?: return
        val start = arch.last()
        val endX = reel.centerOffset + reel.width * 0.3
        val endY = reel.topAboveGl - 0.15
        val span = (0..16).map { i ->
            val t = i / 16.0
            ArtPoint(start.x + (endX - start.x) * t, start.y + (endY - start.y) * t - 0.6 * sin(PI * t))
        }
        polyline(span, ArtColors.CT_ORANGE, 0.045, goose.sourceId)
    }

    // -----------------------------------------------------------------------------------
    // Small parts
    // -----------------------------------------------------------------------------------

    /** Handwheel facing the viewer: rim, five spokes and hub, with a slight shadow. */
    fun handwheel(cx: Double, cy: Double, r: Double, src: String) {
        val rim = max(0.022, r * 0.13)
        circle(cx + 0.012, cy - 0.012, r, null, src, 0x55000000, rim)
        if (shows(0.012)) {
            for (k in 0 until 5) {
                val a = PI / 2 + k * 2 * PI / 5
                line(cx, cy, cx + (r - rim / 2) * cos(a), cy + (r - rim / 2) * sin(a), 0xFFA82520, rim * 0.5, src)
            }
        }
        circle(cx, cy, r, null, src, ArtColors.HANDWHEEL, rim)
        if (shows(0.008)) circle(cx - rim * 0.15, cy + rim * 0.15, r - rim * 0.2, null, src, 0x66FFFFFF, rim * 0.22)
        circle(cx, cy, max(0.03, r * 0.2), ArtFill.Radial(ArtColors.HANDWHEEL), src)
        circle(cx, cy, max(0.012, r * 0.07), ArtFill.Solid(ArtColors.NUT), src, stroke = null)
    }

    fun gauge(cx: Double, cy: Double, r: Double, src: String) {
        circle(cx, cy, r, ArtFill.Solid(ArtColors.GAUGE_FACE), src, ArtColors.STEEL_DARK, r * 0.2)
        if (shows(0.01)) line(cx, cy, cx + r * 0.55, cy + r * 0.4, ArtColors.HANDWHEEL, r * 0.1, src)
    }

    /** Actuator standing on a horizontal valve body, centred at [xc]. */
    fun actuatorUp(xc: Double, yBase: Double, halfWidth: Double, height: Double, src: String) {
        rect(xc - 0.05, yBase, xc + 0.05, yBase + 0.06, ArtFill.CylinderX(ArtColors.STEEL), src)
        rect(xc - halfWidth, yBase + 0.06, xc + halfWidth, yBase + height, ArtFill.CylinderX(ArtColors.ACTUATOR), src, corner = 0.02)
        val pts = (0..8).map { i ->
            val a = PI * i / 8
            ArtPoint(xc + halfWidth * cos(a), yBase + height + 0.07 * sin(a))
        }
        poly(pts, ArtFill.CylinderX(ArtColors.ACTUATOR), src)
    }
}
