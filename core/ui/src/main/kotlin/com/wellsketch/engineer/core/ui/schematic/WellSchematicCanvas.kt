package com.wellsketch.engineer.core.ui.schematic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsketch.engineer.core.engineering.trajectory.TrajectorySource
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.schematic.ElementKind
import com.wellsketch.engineer.core.schematic.FormationBand
import com.wellsketch.engineer.core.schematic.MarkerKind
import com.wellsketch.engineer.core.schematic.ProjectionConfig
import com.wellsketch.engineer.core.schematic.SchematicElement
import com.wellsketch.engineer.core.schematic.SchematicHitTester
import com.wellsketch.engineer.core.schematic.SchematicProjection
import com.wellsketch.engineer.core.schematic.SchematicScene
import com.wellsketch.engineer.core.schematic.SurfaceBlock
import com.wellsketch.engineer.core.schematic.SurfaceBlockKind
import com.wellsketch.engineer.core.ui.format.UnitFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Live well schematic: full longitudinal half-cut of the well along its trajectory, with
 * the stratigraphic column on the left sharing the well's vertical (TVD below GL) scale,
 * and surface equipment above ground level.
 *
 * The drawing is a pure function of [scene]: there is no animation, so geometry and CT/BHA
 * positions change instantly when the scene changes.
 */
@Composable
fun WellSchematicCanvas(
    scene: SchematicScene,
    formatter: UnitFormatter,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    onElementSelected: (SchematicElement?) -> Unit = {},
    onSurfaceBlockSelected: (SurfaceBlock) -> Unit = {},
    surfaceBandHeight: Dp = 120.dp,
    columnWidth: Dp = 88.dp,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val hasSurface = scene.surfaceBlocks.isNotEmpty()
    val config = remember(density, surfaceBandHeight, columnWidth, hasSurface) {
        with(density) {
            ProjectionConfig(
                columnWidth = columnWidth.toPx().toDouble(),
                surfaceBandHeight = (if (hasSurface) surfaceBandHeight else 28.dp).toPx().toDouble(),
                margin = 8.dp.toPx().toDouble(),
                radialFraction = 0.14,
                minRadialPx = 12.dp.toPx().toDouble(),
                maxRadialPx = 46.dp.toPx().toDouble(),
            )
        }
    }
    val projection = remember(scene, canvasSize, config) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            SchematicProjection(scene, canvasSize.width.toDouble(), canvasSize.height.toDouble(), config)
        } else {
            null
        }
    }
    val minWallPx = with(density) { MIN_WALL_DP.dp.toPx().toDouble() }
    val shapes = remember(projection, minWallPx) { projection?.let { buildShapes(it, minWallPx) }.orEmpty() }
    val onElement by rememberUpdatedState(onElementSelected)
    val onBlock by rememberUpdatedState(onSurfaceBlockSelected)

    Canvas(
        modifier = modifier
            .background(SchematicPalette.paper)
            .onSizeChanged { canvasSize = it }
            .pointerInput(projection) {
                detectTapGestures { offset ->
                    val p = projection ?: return@detectTapGestures
                    val block = p.scene.surfaceBlocks.lastOrNull { b ->
                        val r = p.surfaceRect(b)
                        offset.x >= r[0] && offset.x <= r[2] && offset.y >= r[1] && offset.y <= r[3]
                    }
                    if (block != null) {
                        onBlock(block)
                        return@detectTapGestures
                    }
                    val located = p.locate(offset.x.toDouble(), offset.y.toDouble())
                    val hit = if (located.distanceFromAxisPx <= p.radialPx * 1.5) {
                        SchematicHitTester.hitTest(p.scene, located.md, located.radius, radialTolerance = 6.0 / p.radialScale)
                    } else {
                        null
                    }
                    onElement(hit)
                }
            }
            .semantics { contentDescription = "Well schematic of ${scene.wellName}" },
    ) {
        val p = projection ?: return@Canvas
        drawFormations(p, textMeasurer, formatter)
        drawDatums(p, textMeasurer)
        clipRect(left = p.columnRight.toFloat(), top = p.groundY.toFloat(), right = size.width, bottom = size.height) {
            shapes.forEach { drawElement(p, it, it.element.id == selectedId) }
            drawMarkers(p, textMeasurer, formatter)
        }
        drawSurface(p, textMeasurer, selectedId)
        if (p.scene.trajectorySource == TrajectorySource.ASSUMED_VERTICAL) {
            label(textMeasurer, "No survey: vertical path assumed", p.wellLeft.toFloat(), size.height - 14.dp.toPx(), 220.dp.toPx(), Color(0xFF8A5A00))
        }
    }
}

// ---------------------------------------------------------------------------------------
// Geometry -> paths (built once per scene/size)
// ---------------------------------------------------------------------------------------

private class ElementShape(
    val element: SchematicElement,
    val paths: List<Path>,
    val gradientStart: Offset,
    val gradientEnd: Offset,
)

/** Minimum on-screen wall thickness for casing, tubing and CT walls (drawing only). */
private const val MIN_WALL_DP = 2.5f

private val thinWallKinds = setOf(ElementKind.CASING_WALL, ElementKind.TUBING_WALL, ElementKind.CT_WALL)

private fun buildShapes(p: SchematicProjection, minWallPx: Double): List<ElementShape> =
    p.scene.elementsInDrawOrder
        .filter { it.kind != ElementKind.PERFORATION }
        .map { e ->
            val mds = sampleMds(p, e.mdTop, e.mdBottom)
            // Real walls can be thinner than a pixel at schematic scale; draw them at least
            // MIN_WALL_DP thick so strings and their ends stay visible. Geometry is unchanged.
            val outer = if (e.kind in thinWallKinds && e.innerRadius > 0.0) {
                maxOf(e.outerRadius, e.innerRadius + minWallPx / p.radialScale)
            } else {
                e.outerRadius
            }
            val paths = if (e.innerRadius <= 0.0) {
                listOf(polygon(p, mds, outer, -outer))
            } else {
                listOf(polygon(p, mds, outer, e.innerRadius), polygon(p, mds, -e.innerRadius, -outer))
            }
            val mid = (mds.first() + mds.last()) / 2.0
            val a = p.point(mid, -outer)
            val b = p.point(mid, outer)
            ElementShape(e, paths, Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()))
        }

private fun sampleMds(p: SchematicProjection, top: Double, bottom: Double): List<Double> {
    val t = max(top, p.scene.path.first().md)
    val b = max(bottom, t)
    val inner = p.scene.path.asSequence().map { it.md }.filter { it > t && it < b }.toList()
    return listOf(t) + inner + listOf(b)
}

/** Polygon along [mds] at radius r1 (downward), returning at radius r2 (upward). */
private fun polygon(p: SchematicProjection, mds: List<Double>, r1: Double, r2: Double): Path {
    val path = Path()
    mds.forEachIndexed { i, md ->
        val s = p.point(md, r1)
        if (i == 0) path.moveTo(s.x.toFloat(), s.y.toFloat()) else path.lineTo(s.x.toFloat(), s.y.toFloat())
    }
    for (md in mds.asReversed()) {
        val s = p.point(md, r2)
        path.lineTo(s.x.toFloat(), s.y.toFloat())
    }
    path.close()
    return path
}

// ---------------------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------------------

private fun DrawScope.drawElement(p: SchematicProjection, shape: ElementShape, selected: Boolean) {
    val e = shape.element
    val base = SchematicPalette.elementColor(e)
    val shaded = e.kind == ElementKind.COMPLETION_COMPONENT || e.kind == ElementKind.BHA_COMPONENT ||
        e.kind == ElementKind.CT_WALL || e.kind == ElementKind.TUBING_WALL
    shape.paths.forEach { path ->
        if (shaded) {
            // Cylindrical shading gives tools an intact, 3D-like external appearance.
            val brush = Brush.linearGradient(
                colors = listOf(base.darken(0.55f), base.lighten(0.45f), base, base.darken(0.6f)),
                start = shape.gradientStart,
                end = shape.gradientEnd,
            )
            drawPath(path, brush)
        } else {
            drawPath(path, base)
        }
        if (e.kind == ElementKind.CEMENT) drawPath(path, base.darken(0.8f), style = Stroke(width = 0.6.dp.toPx()))
        if (selected) drawPath(path, SchematicPalette.selection, style = Stroke(width = 2.5.dp.toPx()))
    }
}

private fun DrawScope.drawMarkers(p: SchematicProjection, tm: TextMeasurer, formatter: UnitFormatter) {
    // Perforation tunnels as spikes through casing and cement into the formation (symbolic length).
    p.scene.elements.filter { it.kind == ElementKind.PERFORATION }.forEach { e ->
        val color = SchematicPalette.elementColor(e)
        val stepMd = max(4.dp.toPx() / p.pathScale, 1e-3)
        val count = min(200, ceil((e.mdBottom - e.mdTop) / stepMd).toInt() + 1)
        for (i in 0 until count) {
            val md = min(e.mdBottom, e.mdTop + i * stepMd)
            for (side in listOf(1.0, -1.0)) {
                val a = p.point(md, side * e.innerRadius)
                val b = p.point(md, side * e.outerRadius)
                drawLine(color, Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
    val heightMd = 7.dp.toPx() / p.pathScale
    val widthR = 6.dp.toPx() / p.radialScale
    p.scene.markers.forEach { m ->
        for (side in listOf(1.0, -1.0)) {
            when (m.kind) {
                MarkerKind.CASING_SHOE -> {
                    val a = p.point(m.md - heightMd, side * m.radius)
                    val b = p.point(m.md, side * m.radius)
                    val c = p.point(m.md, side * (m.radius + widthR))
                    val path = Path().apply {
                        moveTo(a.x.toFloat(), a.y.toFloat()); lineTo(b.x.toFloat(), b.y.toFloat())
                        lineTo(c.x.toFloat(), c.y.toFloat()); close()
                    }
                    drawPath(path, SchematicPalette.ink)
                }
                MarkerKind.TOP_OF_CEMENT -> {
                    val a = p.point(m.md, side * m.radius)
                    val b = p.point(m.md, side * (m.radius + widthR))
                    drawLine(Color(0xFF6E675A), Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()), strokeWidth = 2.dp.toPx())
                }
                MarkerKind.LINER_TOP -> {
                    val a = p.point(m.md, side * m.radius)
                    val b = p.point(m.md, side * (m.radius + widthR * 0.8))
                    drawLine(SchematicPalette.ink, Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()), strokeWidth = 3.dp.toPx())
                }
                MarkerKind.END_OF_TUBING -> {
                    val a = p.point(m.md, 0.0)
                    val b = p.point(m.md, side * (m.radius + widthR))
                    drawLine(Color(0xFF2E6F9E), Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()), strokeWidth = 2.dp.toPx())
                }
            }
        }
        // Depth labels on the right-hand side so edits are easy to see on the sketch.
        val labelText = when (m.kind) {
            MarkerKind.CASING_SHOE -> "Shoe ${formatter.depth(Length.meters(m.md))}"
            MarkerKind.END_OF_TUBING -> "EOT ${formatter.depth(Length.meters(m.md))}"
            else -> null
        }
        if (labelText != null) {
            val anchor = p.point(m.md, m.radius + widthR)
            label(tm, labelText, anchor.x.toFloat() + 3.dp.toPx(), anchor.y.toFloat() - 6.sp.toPx(), 110.dp.toPx(), SchematicPalette.ink)
        }
    }
}

private fun DrawScope.drawFormations(p: SchematicProjection, tm: TextMeasurer, formatter: UnitFormatter) {
    val left = p.columnLeft.toFloat()
    val right = p.columnRight.toFloat()
    val groundY = p.groundY.toFloat()
    for (band in p.scene.formations) {
        val top = max(groundY, p.tvdToY(band.topTvdBelowGl).toFloat())
        val bottom = min(size.height, p.tvdToY(band.bottomTvdBelowGl).toFloat())
        if (bottom <= top) continue
        val color = SchematicPalette.lithologyColor(band.lithology)
        // faint band across the well area keeps boundaries visually aligned with the well
        drawRect(color.copy(alpha = 0.22f), topLeft = Offset(right, top), size = Size(size.width - right, bottom - top))
        drawRect(color, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
        drawPattern(band, left, top, right, bottom)
        drawLine(SchematicPalette.grid, Offset(left, top), Offset(size.width, top), strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        val textHeight = 11.sp.toPx()
        if (bottom - top > textHeight) {
            label(tm, band.name, left + 2.dp.toPx(), top + 1.dp.toPx(), right - left - 4.dp.toPx(), SchematicPalette.ink)
        }
    }
    drawRect(SchematicPalette.ink, topLeft = Offset(left, groundY), size = Size(right - left, size.height - groundY), style = Stroke(1f))
    drawDepthTicks(p, tm, formatter)
}

private fun DrawScope.drawPattern(band: FormationBand, left: Float, top: Float, right: Float, bottom: Float) {
    val ink = SchematicPalette.ink.copy(alpha = 0.35f)
    val step = 8.dp.toPx()
    val rows = min(400, ((bottom - top) / step).toInt())
    if (band.isUndefined) {
        var x = left - (bottom - top)
        while (x < right) {
            drawLine(ink, Offset(x, bottom), Offset(x + (bottom - top), top), strokeWidth = 0.8f)
            x += step
        }
        return
    }
    val name = band.lithology.name
    for (r in 0 until rows) {
        val y = top + (r + 0.5f) * step
        var x = left + if (r % 2 == 0) step / 2 else step
        while (x < right - 2f) {
            when {
                name in setOf("SANDSTONE", "CONGLOMERATE", "UNCONSOLIDATED", "SILTSTONE") -> drawCircle(ink, 1.dp.toPx(), Offset(x, y))
                name in setOf("SHALE", "CLAYSTONE", "MARL") -> drawLine(ink, Offset(x - 2.dp.toPx(), y), Offset(x + 2.dp.toPx(), y), strokeWidth = 1f)
                name in setOf("LIMESTONE", "DOLOMITE", "CHALK") -> drawLine(ink, Offset(x, y - step / 2), Offset(x, y + step / 2), strokeWidth = 1f)
                else -> Unit
            }
            x += step * 1.5f
        }
        if (name in setOf("LIMESTONE", "DOLOMITE", "CHALK")) drawLine(ink, Offset(left, y + step / 2), Offset(right, y + step / 2), strokeWidth = 1f)
    }
}

private fun DrawScope.drawDepthTicks(p: SchematicProjection, tm: TextMeasurer, formatter: UnitFormatter) {
    val maxDisplay = formatter.depthValue(Length.meters(p.scene.maxTvdBelowGl))
    val candidates = listOf(50.0, 100.0, 200.0, 250.0, 500.0, 1000.0, 2000.0, 2500.0, 5000.0, 10000.0)
    val interval = candidates.firstOrNull { maxDisplay / it <= 8 } ?: candidates.last()
    var v = 0.0
    val x = p.columnRight.toFloat()
    while (v <= maxDisplay) {
        val meters = formatter.depthFromValue(v).meters
        val y = p.tvdToY(meters).toFloat()
        drawLine(SchematicPalette.ink, Offset(x, y), Offset(x + 5.dp.toPx(), y), strokeWidth = 1f)
        label(tm, UnitFormatter.number(v, 0), x + 6.dp.toPx(), y - 6.sp.toPx(), 48.dp.toPx(), SchematicPalette.ink)
        v += interval
    }
    label(tm, "TVD ${formatter.depthUnit} below GL", p.columnLeft.toFloat(), p.groundY.toFloat() - 14.sp.toPx(), 140.dp.toPx(), SchematicPalette.ink)
}

private fun DrawScope.drawDatums(p: SchematicProjection, tm: TextMeasurer) {
    val groundY = p.groundY.toFloat()
    drawLine(SchematicPalette.groundLine, Offset(0f, groundY), Offset(size.width, groundY), strokeWidth = 2.dp.toPx())
    label(tm, "GL", size.width - 24.dp.toPx(), groundY - 13.sp.toPx(), 22.dp.toPx(), SchematicPalette.groundLine)
    val msl = -p.scene.datums.mslRelativeToGl // depth of MSL below GL (if positive)
    if (msl > 0 && msl <= p.scene.maxTvdBelowGl) {
        val y = p.tvdToY(msl).toFloat()
        drawLine(SchematicPalette.mslLine, Offset(p.columnRight.toFloat(), y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f)))
        label(tm, "MSL", size.width - 30.dp.toPx(), y + 2.dp.toPx(), 28.dp.toPx(), SchematicPalette.mslLine)
    }
    if (p.surfaceScale > 0) {
        val y = groundY - (p.scene.datums.rkbAboveGl * p.surfaceScale).toFloat()
        drawLine(SchematicPalette.ink, Offset(size.width - 70.dp.toPx(), y), Offset(size.width, y), strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        label(tm, "RKB datum", size.width - 68.dp.toPx(), y - 13.sp.toPx(), 66.dp.toPx(), SchematicPalette.ink)
    }
}

private fun DrawScope.drawSurface(p: SchematicProjection, tm: TextMeasurer, selectedId: String?) {
    if (p.surfaceScale <= 0) return
    val blocks = p.scene.surfaceBlocks
    val hasCt = p.scene.elements.any { it.kind == ElementKind.CT_WALL }
    val axisX = p.axisXAtSurface.toFloat()
    val injector = blocks.firstOrNull { it.kind == SurfaceBlockKind.INJECTOR }
    for (b in blocks) {
        val r = p.surfaceRect(b)
        val left = r[0].toFloat()
        val top = r[1].toFloat()
        val right = r[2].toFloat()
        val bottom = r[3].toFloat()
        val w = right - left
        val h = bottom - top
        if (w <= 0f || h <= 0f) continue
        val color = SchematicPalette.surfaceColor(b.kind)
        when (b.kind) {
            SurfaceBlockKind.REEL -> {
                val c = Offset((left + right) / 2, (top + bottom) / 2)
                val radius = min(w, h) / 2
                drawCircle(Brush.radialGradient(listOf(color.lighten(0.3f), color.darken(0.6f)), center = c, radius = radius), radius, c)
                drawCircle(SchematicPalette.ctLine, radius * 0.55f, c, style = Stroke(radius * 0.35f))
                drawCircle(color.darken(0.4f), radius * 0.18f, c)
            }
            SurfaceBlockKind.GOOSENECK -> {
                drawArc(color, 180f, 180f, false, topLeft = Offset(left, top), size = Size(w, h * 2), style = Stroke(4.dp.toPx()))
            }
            else -> {
                val brush = Brush.horizontalGradient(listOf(color.darken(0.55f), color.lighten(0.35f), color, color.darken(0.6f)), startX = left, endX = right)
                drawRoundRect(brush, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(2.dp.toPx()))
            }
        }
        val outline = if (b.sourceId == selectedId) SchematicPalette.selection else SchematicPalette.ink.copy(alpha = 0.7f)
        drawRect(
            outline, topLeft = Offset(left, top), size = Size(w, h),
            style = Stroke(
                width = if (b.sourceId == selectedId) 2.5.dp.toPx() else 0.8.dp.toPx(),
                pathEffect = if (b.sizeIsSchematic) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null,
            ),
        )
        if (b.centerOffset == 0.0 && h >= 9.sp.toPx()) {
            label(tm, b.label, right + 4.dp.toPx(), top + (h - 11.sp.toPx()) / 2, 150.dp.toPx(), SchematicPalette.ink)
        } else if (b.centerOffset != 0.0) {
            label(tm, b.label, left, bottom + 1.dp.toPx(), max(w, 60.dp.toPx()), SchematicPalette.ink)
        }
    }
    if (hasCt && injector != null) {
        val r = p.surfaceRect(injector)
        drawLine(SchematicPalette.ctLine, Offset(axisX, p.groundY.toFloat()), Offset(axisX, r[1].toFloat()), strokeWidth = 2.dp.toPx())
        val reel = blocks.firstOrNull { it.kind == SurfaceBlockKind.REEL }
        val goose = blocks.firstOrNull { it.kind == SurfaceBlockKind.GOOSENECK }
        if (reel != null && goose != null) {
            val rr = p.surfaceRect(reel)
            val gr = p.surfaceRect(goose)
            val path = Path().apply {
                moveTo(axisX, gr[1].toFloat())
                quadraticTo(((rr[0] + rr[2]) / 2).toFloat(), gr[1].toFloat(), ((rr[0] + rr[2]) / 2).toFloat(), rr[1].toFloat())
            }
            drawPath(path, SchematicPalette.ctLine, style = Stroke(2.dp.toPx()))
        }
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, x: Float, y: Float, maxWidth: Float, color: Color) {
    if (maxWidth < 4f) return
    val layout = tm.measure(
        AnnotatedString(text),
        style = TextStyle(fontSize = 9.sp, color = color),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidth.toInt()),
    )
    drawText(layout, topLeft = Offset(x, y))
}

private fun Color.darken(factor: Float) = Color(red * factor, green * factor, blue * factor, alpha)
private fun Color.lighten(amount: Float) =
    Color(red + (1f - red) * amount, green + (1f - green) * amount, blue + (1f - blue) * amount, alpha)
