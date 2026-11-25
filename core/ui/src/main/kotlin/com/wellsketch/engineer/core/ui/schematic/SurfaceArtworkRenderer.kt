package com.wellsketch.engineer.core.ui.schematic

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.wellsketch.engineer.core.schematic.ArtCircle
import com.wellsketch.engineer.core.schematic.ArtFill
import com.wellsketch.engineer.core.schematic.ArtLine
import com.wellsketch.engineer.core.schematic.ArtPolygon
import com.wellsketch.engineer.core.schematic.ArtPrimitive
import com.wellsketch.engineer.core.schematic.ArtRect
import com.wellsketch.engineer.core.schematic.LabelAlign
import com.wellsketch.engineer.core.schematic.PlacedCallout
import com.wellsketch.engineer.core.schematic.SurfaceArtworkResult
import kotlin.math.max
import kotlin.math.min

/** Maps surface artwork metres to screen pixels (x from the well axis, y up from GL). */
internal class SurfaceTransform(val axisX: Double, val groundY: Double, val scale: Double) {
    fun x(m: Double): Float = (axisX + m * scale).toFloat()
    fun y(m: Double): Float = (groundY - m * scale).toFloat()
}

/**
 * Paints the renderer-agnostic surface artwork. Fills reproduce the metallic shading of
 * the model (cylinders, pipes, wheels); the selected object gets one highlight outline.
 */
internal fun DrawScope.drawSurfaceArtwork(art: SurfaceArtworkResult, t: SurfaceTransform, selectedId: String?) {
    val hairline = 0.8.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
    for (p in art.primitives) drawPrimitive(p, t, hairline, dash)
    if (selectedId != null) {
        var l = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var r = Float.NEGATIVE_INFINITY
        var b = Float.NEGATIVE_INFINITY
        for (p in art.primitives) {
            if (p.sourceId != selectedId) continue
            val box = screenBox(p, t)
            l = min(l, box[0]); top = min(top, box[1]); r = max(r, box[2]); b = max(b, box[3])
        }
        if (l.isFinite() && r > l) {
            val pad = 3.dp.toPx()
            drawRoundRect(
                SchematicPalette.selection, Offset(l - pad, top - pad), Size(r - l + 2 * pad, b - top + 2 * pad),
                CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawPrimitive(p: ArtPrimitive, t: SurfaceTransform, hairline: Float, dash: PathEffect) {
    when (p) {
        is ArtRect -> {
            val l = t.x(p.x0)
            val r = t.x(p.x1)
            val top = t.y(p.y1)
            val bottom = t.y(p.y0)
            if (r - l <= 0f || bottom - top <= 0f) return
            val offset = Offset(l, top)
            val size = Size(r - l, bottom - top)
            val corner = CornerRadius((p.corner * t.scale).toFloat())
            p.fill?.let { drawRoundRect(brushOf(it, l, top, r, bottom), offset, size, corner) }
            p.stroke?.let {
                drawRoundRect(colorOf(it), offset, size, corner, style = Stroke(hairline, pathEffect = if (p.dashed) dash else null))
            }
        }
        is ArtPolygon -> {
            if (p.points.size < 2) return
            val path = Path()
            p.points.forEachIndexed { i, pt -> if (i == 0) path.moveTo(t.x(pt.x), t.y(pt.y)) else path.lineTo(t.x(pt.x), t.y(pt.y)) }
            if (p.closed) path.close()
            if (p.closed && p.fill != null) {
                val xs = p.points.map { t.x(it.x) }
                val ys = p.points.map { t.y(it.y) }
                drawPath(path, brushOf(p.fill!!, xs.min(), ys.min(), xs.max(), ys.max()))
            }
            p.stroke?.let {
                val width = if (p.strokeWidth > 0.0) max(1f, (p.strokeWidth * t.scale).toFloat()) else hairline
                drawPath(path, colorOf(it), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        is ArtCircle -> {
            val c = Offset(t.x(p.cx), t.y(p.cy))
            val r = (p.r * t.scale).toFloat()
            if (r <= 0f) return
            p.fill?.let { drawCircle(brushOf(it, c.x - r, c.y - r, c.x + r, c.y + r), r, c) }
            p.stroke?.let {
                val width = if (p.strokeWidth > 0.0) max(1f, (p.strokeWidth * t.scale).toFloat()) else hairline
                drawCircle(colorOf(it), r, c, style = Stroke(width))
            }
        }
        is ArtLine -> {
            val width = if (p.width > 0.0) max(1f, (p.width * t.scale).toFloat()) else hairline
            drawLine(
                colorOf(p.color), Offset(t.x(p.x0), t.y(p.y0)), Offset(t.x(p.x1), t.y(p.y1)),
                strokeWidth = width, cap = StrokeCap.Round, pathEffect = if (p.dashed) dash else null,
            )
        }
    }
}

/** Screen bounding box (left, top, right, bottom) of a primitive. */
private fun screenBox(p: ArtPrimitive, t: SurfaceTransform): FloatArray = when (p) {
    is ArtRect -> floatArrayOf(t.x(p.x0), t.y(p.y1), t.x(p.x1), t.y(p.y0))
    is ArtPolygon -> floatArrayOf(
        p.points.minOf { t.x(it.x) }, p.points.minOf { t.y(it.y) }, p.points.maxOf { t.x(it.x) }, p.points.maxOf { t.y(it.y) },
    )
    is ArtCircle -> floatArrayOf(t.x(p.cx - p.r), t.y(p.cy + p.r), t.x(p.cx + p.r), t.y(p.cy - p.r))
    is ArtLine -> floatArrayOf(min(t.x(p.x0), t.x(p.x1)), min(t.y(p.y0), t.y(p.y1)), max(t.x(p.x0), t.x(p.x1)), max(t.y(p.y0), t.y(p.y1)))
}

private fun colorOf(argb: Long) = Color(argb.toInt())

private fun brushOf(fill: ArtFill, l: Float, t: Float, r: Float, b: Float): Brush {
    val c = colorOf(fill.argb)
    return when (fill) {
        is ArtFill.Solid -> SolidColor(c)
        is ArtFill.CylinderX -> Brush.horizontalGradient(
            0f to c.darken(0.55f), 0.35f to c.lighten(0.35f), 0.65f to c, 1f to c.darken(0.6f),
            startX = l, endX = max(r, l + 1f),
        )
        is ArtFill.CylinderY -> Brush.verticalGradient(
            0f to c.lighten(0.35f), 0.45f to c, 1f to c.darken(0.55f),
            startY = t, endY = max(b, t + 1f),
        )
        is ArtFill.Radial -> Brush.radialGradient(
            0f to c.lighten(0.45f), 0.6f to c, 1f to c.darken(0.6f),
            center = Offset(l + (r - l) * 0.35f, t + (b - t) * 0.35f),
            radius = max(1f, max(r - l, b - t) * 0.75f),
        )
        is ArtFill.Depth -> Brush.verticalGradient(listOf(c.lighten(0.15f), c.darken(0.55f)), startY = t, endY = max(b, t + 1f))
    }
}

/** Ground cross-section (soil) between [top] and [bottom], with faint strata lines. */
internal fun DrawScope.drawGroundSection(left: Float, right: Float, top: Float, bottom: Float) {
    if (right <= left || bottom <= top) return
    drawRect(
        Brush.verticalGradient(listOf(SchematicPalette.soilTop, SchematicPalette.soilBottom), startY = top, endY = bottom),
        Offset(left, top), Size(right - left, bottom - top),
    )
    val h = bottom - top
    for (k in 1..3) {
        val y = top + h * (0.28f * k)
        if (y >= bottom) break
        drawLine(SchematicPalette.soilLine, Offset(left, y), Offset(right, y + 3f), strokeWidth = 1.5f)
    }
}

/** Zig-zag break line marking the change from the surface-detail scale to the well scale. */
internal fun DrawScope.drawScaleBreak(left: Float, right: Float, y: Float) {
    val step = 7.dp.toPx()
    val amp = 2.dp.toPx()
    val path = Path().apply {
        moveTo(left, y)
        var x = left
        var up = true
        while (x < right) {
            x = min(right, x + step)
            lineTo(x, y + if (up) -amp else amp)
            up = !up
        }
    }
    drawPath(path, SchematicPalette.paper, style = Stroke(4.dp.toPx()))
    drawPath(path, SchematicPalette.ink, style = Stroke(1.2.dp.toPx()))
}

/** Draws placed callouts: leader line and label text (ellipsized to [maxWidth]). */
internal fun DrawScope.drawCallouts(
    tm: TextMeasurer,
    placed: List<PlacedCallout>,
    fontSize: TextUnit,
    lineHeight: Float,
    maxWidth: Float,
) {
    val leader = SchematicPalette.ink.copy(alpha = 0.55f)
    for (pc in placed) {
        val mid = (pc.labelTop + lineHeight / 2).toFloat()
        val layout = tm.measure(
            AnnotatedString(pc.callout.text),
            style = TextStyle(fontSize = fontSize, color = SchematicPalette.ink),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            constraints = Constraints(maxWidth = max(1, maxWidth.toInt())),
        )
        val x = when (pc.align) {
            LabelAlign.START -> pc.labelX.toFloat()
            LabelAlign.END -> pc.labelX.toFloat() - layout.size.width
            LabelAlign.CENTER -> pc.labelX.toFloat() - layout.size.width / 2f
        }
        if (pc.hasLeader) {
            val elbow = if (pc.align == LabelAlign.END) pc.labelX.toFloat() + 6.dp.toPx() else pc.labelX.toFloat() - 6.dp.toPx()
            val end = if (pc.align == LabelAlign.END) pc.labelX.toFloat() + 2.dp.toPx() else pc.labelX.toFloat() - 2.dp.toPx()
            val path = Path().apply {
                moveTo(pc.anchorX.toFloat(), pc.anchorY.toFloat())
                lineTo(elbow, mid)
                lineTo(end, mid)
            }
            drawPath(path, leader, style = Stroke(1f))
        }
        val top = pc.labelTop.toFloat() + (lineHeight - layout.size.height) / 2f
        val pad = 2.dp.toPx()
        drawRoundRect(
            SchematicPalette.paper.copy(alpha = 0.82f), Offset(x - pad, top - pad / 2),
            Size(layout.size.width + 2 * pad, layout.size.height + pad), CornerRadius(3.dp.toPx()),
        )
        drawText(layout, topLeft = Offset(x, top))
    }
}

internal fun Color.darken(factor: Float) = Color(red * factor, green * factor, blue * factor, alpha)

internal fun Color.lighten(amount: Float) =
    Color(red + (1f - red) * amount, green + (1f - green) * amount, blue + (1f - blue) * amount, alpha)
