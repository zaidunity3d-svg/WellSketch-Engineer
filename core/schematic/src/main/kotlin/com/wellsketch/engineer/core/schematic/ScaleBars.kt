package com.wellsketch.engineer.core.schematic

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Scale bar of a uniformly scaled drawing (the surface detail view), so equipment sizes can
 * be compared by eye: wellhead, BOP stack and rig floor are drawn at one common scale.
 *
 * [meters] is the bar length in metres, [divisions] the number of alternating segments and
 * [label] the bar length in the display unit, e.g. "2 m" or "5 ft".
 */
data class ScaleBar(val meters: Double, val divisions: Int, val label: String)

object ScaleBars {
    private const val METERS_PER_FOOT = 0.3048

    /**
     * Longest "nice" bar (1, 2 or 5 × 10ⁿ display units) that fits in [maxPx] pixels at
     * [pxPerMeter]; null when the scale or the room is not usable.
     */
    fun choose(pxPerMeter: Double, maxPx: Double, feet: Boolean = false): ScaleBar? {
        if (!(pxPerMeter > 0.0) || !(maxPx > 0.0) || pxPerMeter.isInfinite() || maxPx.isInfinite()) return null
        val unitMeters = if (feet) METERS_PER_FOOT else 1.0
        val maxUnits = maxPx / (pxPerMeter * unitMeters)
        val base = 10.0.pow(floor(log10(maxUnits)))
        val step = listOf(5, 2, 1).first { it * base <= maxUnits * (1 + 1e-9) }
        val units = step * base
        val divisions = when (step) {
            5 -> 5
            else -> 2
        }
        return ScaleBar(units * unitMeters, divisions, "${plain(units)} ${if (feet) "ft" else "m"}")
    }

    /** Number without trailing zeros, e.g. 2.0 -> "2", 0.5 -> "0.5", 0.05 -> "0.05". */
    internal fun plain(value: Double): String {
        val rounded = value.roundToLong()
        if (abs(value - rounded) < 1e-9) return rounded.toString()
        return "%.6f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
    }
}
