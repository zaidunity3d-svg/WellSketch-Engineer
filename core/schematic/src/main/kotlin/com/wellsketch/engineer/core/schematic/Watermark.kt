package com.wellsketch.engineer.core.schematic

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** App identity shown on the intro screen and in the drawing watermark. */
object Branding {
    const val APP_NAME = "WellSketch Engineer"
    const val DESIGNER_CREDIT = "Designed by Zaid Fawzi"
    const val WATERMARK_TEXT = "$APP_NAME · $DESIGNER_CREDIT"
    const val TAGLINE = "Well design · Well calculations · Coiled tubing"

    /** Watermark opacity: faint enough that every line and label of the sketch stays legible. */
    const val WATERMARK_ALPHA = 0.07f
}

/**
 * Faint diagonal watermark across a drawing area: centred, along the area's diagonal (20°-60°,
 * rising to the right) and sized so the rotated text stays inside the area with a margin.
 * [angleDegrees] is negative (counter-clockwise on screen, where y grows downwards).
 */
data class WatermarkPlacement(
    val centerX: Double,
    val centerY: Double,
    val angleDegrees: Double,
    val textSizePx: Double,
)

object Watermarks {
    private const val MIN_TEXT_PX = 8.0
    private const val FILL = 0.70

    /**
     * [textWidthPerPx]/[textHeightPerPx]: width and line height of the watermark text per pixel
     * of text size (measure the text at size S, divide by S). Null when the area is unusable
     * or too small for a readable watermark.
     */
    fun place(width: Double, height: Double, textWidthPerPx: Double, textHeightPerPx: Double = 1.2): WatermarkPlacement? {
        if (!(width > 0.0) || !(height > 0.0) || !(textWidthPerPx > 0.0) || !(textHeightPerPx > 0.0)) return null
        val angle = Math.toDegrees(atan2(height, width)).coerceIn(20.0, 60.0)
        val rad = angle * PI / 180.0
        val c = abs(cos(rad))
        val s = abs(sin(rad))
        // rotated bounding box of a w x h text box: (w c + h s) x (w s + h c), per pixel of size
        val perPxX = textWidthPerPx * c + textHeightPerPx * s
        val perPxY = textWidthPerPx * s + textHeightPerPx * c
        val size = min(width * FILL / perPxX, height * FILL / perPxY)
        if (size < MIN_TEXT_PX) return null
        return WatermarkPlacement(width / 2, height / 2, -angle, size)
    }
}
