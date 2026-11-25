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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsketch.engineer.core.schematic.ArtBounds
import com.wellsketch.engineer.core.schematic.CalloutKind
import com.wellsketch.engineer.core.schematic.CalloutLayout
import com.wellsketch.engineer.core.schematic.SchematicScene
import com.wellsketch.engineer.core.schematic.SurfaceArtwork
import com.wellsketch.engineer.core.schematic.SurfaceBlock
import kotlin.math.max
import kotlin.math.min

/**
 * Large surface view of one operating state: wellhead in the cellar, drilling BOP under the
 * rig floor, or the Christmas Tree with the CT pressure-control stack and injector, all on
 * the well centerline, with labelled callouts. Elevations come from the scene layout (entered
 * data); widths and detail parts are drawing sizes only. No animation: the picture is a pure
 * function of [scene].
 */
@Composable
fun SurfaceDetailCanvas(
    scene: SchematicScene,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    onSurfaceBlockSelected: (SurfaceBlock) -> Unit = {},
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val bounds = remember(scene) { SurfaceArtwork.bounds(scene) }
    val hasLeftLabels = remember(scene) {
        SurfaceArtwork.build(scene, pxPerMeter = 1.0).callouts.any { it.kind == CalloutKind.SIDE && it.x < 0.0 }
    }
    // A left label column only on wide canvases; on phones every label goes to the right.
    val leftColumn = hasLeftLabels && with(density) { canvasSize.width >= 560.dp.toPx() }
    val layout = remember(bounds, canvasSize, density, leftColumn) {
        bounds?.takeIf { canvasSize.width > 0 && canvasSize.height > 0 }?.let { detailLayout(it, canvasSize, density, leftColumn) }
    }
    val artwork = remember(scene, layout) { layout?.let { SurfaceArtwork.build(scene, it.transform.scale) } }
    val onBlock by rememberUpdatedState(onSurfaceBlockSelected)

    Canvas(
        modifier = modifier
            .background(SchematicPalette.paper)
            .onSizeChanged { canvasSize = it }
            .pointerInput(layout, scene) {
                detectTapGestures { offset ->
                    val t = layout?.transform ?: return@detectTapGestures
                    fun tapped(b: SurfaceBlock): Boolean {
                        val half = b.width / 2
                        return offset.x >= t.x(b.centerOffset - half) && offset.x <= t.x(b.centerOffset + half) &&
                            offset.y >= t.y(b.topAboveGl) && offset.y <= t.y(b.bottomAboveGl)
                    }
                    val block = (scene.surfaceBlocks + scene.surfaceSite.sideBlocks).lastOrNull { tapped(it) }
                        ?: scene.surfaceSite.structureBlocks.firstOrNull { tapped(it) }
                    if (block != null) onBlock(block)
                }
            }
            .semantics { contentDescription = "Surface equipment of ${scene.wellName}" },
    ) {
        val l = layout ?: return@Canvas
        val art = artwork ?: return@Canvas
        val t = l.transform
        val groundY = t.y(0.0)
        drawGroundSection(0f, size.width, groundY, size.height)
        drawLine(SchematicPalette.groundLine, Offset(0f, groundY), Offset(size.width, groundY), strokeWidth = 2.dp.toPx())
        drawSurfaceArtwork(art, t, selectedId)

        val lineHeight = 14.sp.toPx()
        val placed = CalloutLayout.place(
            art.callouts, { t.x(it).toDouble() }, { t.y(it).toDouble() },
            rightColumnX = l.rightColumnX.toDouble(),
            leftColumnX = if (leftColumn) l.leftColumnX.toDouble() else null,
            top = 4.dp.toPx().toDouble(), bottom = (size.height - 30.dp.toPx()).toDouble(),
            lineHeight = lineHeight.toDouble(),
        )
        drawCallouts(textMeasurer, placed, 11.sp, lineHeight, l.labelWidth)

        val note = textMeasurer.measure(
            AnnotatedString("Elevations from entered data; widths and fittings are drawing sizes. Dashed = size not provided."),
            style = TextStyle(fontSize = 9.sp, color = SchematicPalette.paper),
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            constraints = Constraints(maxWidth = max(1, (size.width - 16.dp.toPx()).toInt())),
        )
        drawText(note, topLeft = Offset(8.dp.toPx(), size.height - note.size.height - 4.dp.toPx()))
        val gl = textMeasurer.measure(AnnotatedString("GL"), style = TextStyle(fontSize = 10.sp, color = SchematicPalette.paper))
        drawText(gl, topLeft = Offset(4.dp.toPx(), groundY + 2.dp.toPx()))
    }
}

private class DetailLayout(
    val transform: SurfaceTransform,
    val leftColumnX: Float,
    val rightColumnX: Float,
    val labelWidth: Float,
)

private fun detailLayout(b: ArtBounds, size: IntSize, density: Density, leftLabels: Boolean): DetailLayout = with(density) {
    val margin = 12.dp.toPx()
    val gap = 12.dp.toPx()
    val labelWidth = (size.width * 0.28f).coerceIn(90.dp.toPx(), 190.dp.toPx())
    val columns = if (leftLabels) 2 else 1
    val availW = max(1f, size.width - 2 * margin - columns * (labelWidth + gap))
    val topRoom = 18.dp.toPx() // labels of remote units sit above them
    val availH = max(1f, size.height - 2 * margin - topRoom - 26.dp.toPx())
    val soilBelow = 0.3
    val scale = min(availW / b.width, availH / (b.height + soilBelow)).toDouble()
    val drawingLeft = margin + if (leftLabels) labelWidth + gap else 0f
    val axisX = drawingLeft + (availW - b.width * scale) / 2.0 - b.minX * scale
    val groundY = margin + topRoom + b.maxY * scale
    val t = SurfaceTransform(axisX, groundY, scale)
    DetailLayout(
        transform = t,
        leftColumnX = t.x(b.minX) - gap,
        rightColumnX = t.x(b.maxX) + gap,
        labelWidth = labelWidth,
    )
}
