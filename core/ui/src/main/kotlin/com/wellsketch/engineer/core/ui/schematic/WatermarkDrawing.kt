package com.wellsketch.engineer.core.ui.schematic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import com.wellsketch.engineer.core.schematic.Branding
import com.wellsketch.engineer.core.schematic.Watermarks

/**
 * Faint diagonal "WellSketch Engineer · Designed by Zaid Fawzi" watermark over a drawing area
 * (the whole canvas by default). Drawn last at [Branding.WATERMARK_ALPHA] so it marks every
 * screenshot of the sketch while all lines and labels stay legible.
 */
internal fun DrawScope.drawWatermark(textMeasurer: TextMeasurer, topLeft: Offset = Offset.Zero, area: Size = size) {
    val probeSize = 20f
    val probeStyle = TextStyle(fontSize = probeSize.toSp(), fontWeight = FontWeight.SemiBold)
    val probe = textMeasurer.measure(AnnotatedString(Branding.WATERMARK_TEXT), style = probeStyle, softWrap = false)
    if (probe.size.width <= 0 || probe.size.height <= 0) return
    val placement = Watermarks.place(
        area.width.toDouble(), area.height.toDouble(),
        textWidthPerPx = probe.size.width / probeSize.toDouble(),
        textHeightPerPx = probe.size.height / probeSize.toDouble(),
    ) ?: return
    val text = textMeasurer.measure(
        AnnotatedString(Branding.WATERMARK_TEXT),
        style = probeStyle.copy(
            fontSize = placement.textSizePx.toFloat().toSp(),
            color = SchematicPalette.ink.copy(alpha = Branding.WATERMARK_ALPHA),
        ),
        softWrap = false,
    )
    val center = Offset(topLeft.x + placement.centerX.toFloat(), topLeft.y + placement.centerY.toFloat())
    rotate(placement.angleDegrees.toFloat(), pivot = center) {
        drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
    }
}
