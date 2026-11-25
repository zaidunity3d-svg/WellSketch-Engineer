package com.wellsketch.engineer.core.schematic

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class ScreenPoint(val x: Double, val y: Double)

/**
 * Layout parameters in screen pixels (the UI converts dp to px before constructing).
 *
 * @param columnWidth width of the stratigraphic column strip on the left.
 * @param surfaceBandHeight height reserved above ground level for surface equipment.
 * @param radialFraction share of the drawing width used for the largest radius (one side).
 */
data class ProjectionConfig(
    val columnWidth: Double = 90.0,
    val surfaceBandHeight: Double = 0.0,
    val margin: Double = 12.0,
    val radialFraction: Double = 0.16,
    val minRadialPx: Double = 14.0,
    val maxRadialPx: Double = 80.0,
)

/**
 * Maps scene coordinates to screen pixels.
 *
 * - The well path (vertical section, TVD below GL) uses one isotropic [pathScale], so
 *   trajectory shapes are not distorted.
 * - Radii use a separate exaggerated [radialScale] so that nested strings stay legible.
 * - The stratigraphic column uses the same vertical mapping as the path, so formation
 *   boundaries line up with the well at the same TVD, and GL = 0 is the column top.
 * - Surface equipment uses its own isotropic [surfaceScale] in the band above GL. When the
 *   surface artwork reaches below GL (cellar, wellhead in the cellar, casing stubs), a
 *   near-surface strip of [nearSurfacePx] is added below GL. Inside the strip, TVD is mapped
 *   with the surface scale (piecewise-linear vertical mapping applied to everything: path,
 *   column and surface), so GL stays a single line and boundaries stay aligned.
 */
class SchematicProjection(
    val scene: SchematicScene,
    val width: Double,
    val height: Double,
    val config: ProjectionConfig = ProjectionConfig(),
) {
    private val path = scene.path
    val wellLeft = config.columnWidth + config.margin
    val wellRight = width - config.margin
    val groundY = config.surfaceBandHeight + config.margin
    private val bottom = height - config.margin

    val radialPx: Double = ((wellRight - wellLeft) * config.radialFraction)
        .coerceIn(config.minRadialPx, max(config.minRadialPx, config.maxRadialPx))

    /** Pixels per metre of radius (exaggerated). */
    val radialScale: Double = radialPx / scene.maxRadius

    private val xRange = scene.maxX - scene.minX
    private val availableWidth = max(1.0, wellRight - wellLeft - 2 * radialPx)

    /** Extent of the surface artwork in metres (null when there is no surface equipment). */
    val surfaceBounds: ArtBounds? = if (scene.hasSurface) SurfaceArtwork.bounds(scene) else null

    private fun pathScaleFor(stripPx: Double): Double = min(
        if (xRange > 1e-6) availableWidth / xRange else Double.POSITIVE_INFINITY,
        max(1.0, bottom - groundY - stripPx - radialPx) / max(scene.maxTvdBelowGl, 1.0),
    )

    private fun x0For(pathScale: Double) =
        wellLeft + radialPx + (availableWidth - xRange * pathScale) / 2.0 - scene.minX * pathScale

    /**
     * Surface scale that fits the artwork above GL into the band height and its width into
     * the full screen width (the band lies above GL, so it may extend over the column).
     */
    private fun surfaceScaleFor(axisX: Double): Double {
        val b = surfaceBounds ?: return 0.0
        if (b.maxY <= 0.0 && b.minY >= 0.0) return 0.0
        var scale = if (b.maxY > 0.0) max(0.0, config.surfaceBandHeight - config.margin) / b.maxY else Double.POSITIVE_INFINITY
        if (b.minX < 0) scale = min(scale, max(0.0, axisX - config.margin) / -b.minX)
        if (b.maxX > 0) scale = min(scale, max(0.0, width - config.margin - axisX) / b.maxX)
        // keep the near-surface strip within a third of the drawing height
        if (b.minY < 0) scale = min(scale, max(0.0, (bottom - groundY) / 3.0) / -b.minY)
        return if (scale.isFinite()) scale else 0.0
    }

    /** Height in pixels of the near-surface strip below GL (0 when nothing is below GL). */
    val nearSurfacePx: Double

    /** Pixels per metre along the path (same horizontally and vertically). */
    val pathScale: Double

    private val x0: Double

    /**
     * Pixels per metre for surface equipment inside the band above GL and the near-surface
     * strip below it.
     */
    val surfaceScale: Double

    init {
        val first = surfaceScaleFor(x0For(pathScaleFor(0.0)) + path.first().x * pathScaleFor(0.0))
        val depth = max(0.0, -(surfaceBounds?.minY ?: 0.0))
        nearSurfacePx = depth * first
        pathScale = pathScaleFor(nearSurfacePx)
        x0 = x0For(pathScale)
        surfaceScale = min(first, surfaceScaleFor(x0 + path.first().x * pathScale))
    }

    /** TVD below GL drawn at the surface scale inside the near-surface strip. */
    val nearSurfaceDepth: Double = if (surfaceScale > 0.0) nearSurfacePx / surfaceScale else 0.0

    /** Screen y where the well schematic (at path scale) starts, below the near-surface strip. */
    val wellTopY: Double get() = groundY + nearSurfacePx

    val columnLeft: Double = config.margin
    val columnRight: Double = config.columnWidth

    fun map(x: Double, tvdBelowGl: Double) = ScreenPoint(x0 + x * pathScale, tvdToY(tvdBelowGl))

    fun tvdToY(tvdBelowGl: Double): Double = when {
        nearSurfacePx <= 0.0 || tvdBelowGl <= 0.0 -> groundY + tvdBelowGl * pathScale
        tvdBelowGl <= nearSurfaceDepth -> groundY + tvdBelowGl * surfaceScale
        else -> wellTopY + (tvdBelowGl - nearSurfaceDepth) * pathScale
    }

    /** Screen x of an artwork x coordinate (metres from the axis at the surface). */
    fun surfaceX(x: Double): Double = axisXAtSurface + x * surfaceScale

    /** Screen y of an artwork elevation (metres above GL; negative below GL). */
    fun surfaceY(elevationAboveGl: Double): Double = groundY - elevationAboveGl * surfaceScale

    /** Screen x of the well axis at ground level. */
    val axisXAtSurface: Double get() = map(path.first().x, 0.0).x

    /** Surface block rectangle: left, top, right, bottom in pixels. */
    fun surfaceRect(block: SurfaceBlock): DoubleArray {
        val cx = axisXAtSurface + block.centerOffset * surfaceScale
        val half = block.width * surfaceScale / 2.0
        return doubleArrayOf(cx - half, groundY - block.topAboveGl * surfaceScale, cx + half, groundY - block.bottomAboveGl * surfaceScale)
    }

    /** Axis point and unit tangent (screen space) at [md]; MD outside the path is clamped. */
    fun axisAt(md: Double): Pair<ScreenPoint, ScreenPoint> {
        val m = md.coerceIn(path.first().md, path.last().md)
        val i = segmentIndex(m)
        val a = path[i]
        val b = path[i + 1]
        val f = if (b.md > a.md) (m - a.md) / (b.md - a.md) else 0.0
        val pa = map(a.x, a.y)
        val pb = map(b.x, b.y)
        val dx = pb.x - pa.x
        val dy = pb.y - pa.y
        val len = hypot(dx, dy)
        val tangent = if (len > 1e-9) ScreenPoint(dx / len, dy / len) else ScreenPoint(0.0, 1.0)
        return ScreenPoint(pa.x + dx * f, pa.y + dy * f) to tangent
    }

    /**
     * Screen point at [md] offset [radialOffset] metres from the axis. Positive offsets lie
     * to the screen-right of a downward vertical path; the side rotates with the path.
     */
    fun point(md: Double, radialOffset: Double): ScreenPoint {
        val (p, t) = axisAt(md)
        val nx = -t.y
        val ny = t.x
        val px = radialOffset * radialScale
        return ScreenPoint(p.x - nx * px, p.y - ny * px)
    }

    data class Located(val md: Double, val radius: Double, val distanceFromAxisPx: Double)

    /** Inverse mapping for hit testing: nearest MD on the axis and radial distance in metres. */
    fun locate(x: Double, y: Double): Located {
        var best = Located(path.first().md, Double.MAX_VALUE, Double.MAX_VALUE)
        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            val pa = map(a.x, a.y)
            val pb = map(b.x, b.y)
            val dx = pb.x - pa.x
            val dy = pb.y - pa.y
            val len2 = dx * dx + dy * dy
            val f = if (len2 > 1e-12) (((x - pa.x) * dx + (y - pa.y) * dy) / len2).coerceIn(0.0, 1.0) else 0.0
            val cx = pa.x + dx * f
            val cy = pa.y + dy * f
            val d = hypot(x - cx, y - cy)
            if (d < best.distanceFromAxisPx) {
                best = Located(a.md + (b.md - a.md) * f, d / radialScale, d)
            }
        }
        return best
    }

    private fun segmentIndex(md: Double): Int {
        var lo = 0
        var hi = path.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (path[mid].md <= md) lo = mid else hi = mid
        }
        return min(lo, path.size - 2)
    }
}

/** Selects the element under a located point, preferring inner/foreground kinds. */
object SchematicHitTester {
    fun hitTest(scene: SchematicScene, md: Double, radius: Double, radialTolerance: Double = 0.0): SchematicElement? =
        scene.elements
            .filter { md >= it.mdTop && md <= it.mdBottom }
            .filter { radius >= it.innerRadius - radialTolerance && radius <= it.outerRadius + radialTolerance }
            .maxWithOrNull(compareBy<SchematicElement> { it.kind.hitPriority }.thenBy { -it.outerRadius })
}
