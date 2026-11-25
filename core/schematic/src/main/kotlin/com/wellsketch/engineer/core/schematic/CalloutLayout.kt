package com.wellsketch.engineer.core.schematic

import kotlin.math.max
import kotlin.math.min

/** How a placed label is aligned at ([PlacedCallout.labelX], [PlacedCallout.labelTop]). */
enum class LabelAlign { START, END, CENTER }

/** A callout label placed in screen pixels, with its leader-line anchor. */
data class PlacedCallout(
    val callout: ArtCallout,
    val anchorX: Double,
    val anchorY: Double,
    val labelX: Double,
    val labelTop: Double,
    val align: LabelAlign,
    /** False for labels placed directly above a unit (no leader line). */
    val hasLeader: Boolean = true,
)

/**
 * Places surface callout labels in label columns without overlaps, keeping each label as
 * close as possible to its anchor height. Pure screen-space geometry, shared by renderers.
 */
object CalloutLayout {

    /**
     * @param sx, sy map artwork metres to screen pixels.
     * @param rightColumnX x where right-column labels start.
     * @param leftColumnX x where left-column labels end; null puts every label on the right.
     * @param kinds callout kinds to place; others are skipped.
     */
    fun place(
        callouts: List<ArtCallout>,
        sx: (Double) -> Double,
        sy: (Double) -> Double,
        rightColumnX: Double,
        leftColumnX: Double?,
        top: Double,
        bottom: Double,
        lineHeight: Double,
        gap: Double = 2.0,
        kinds: Set<CalloutKind> = CalloutKind.entries.toSet(),
    ): List<PlacedCallout> {
        val wanted = callouts.filter { it.kind in kinds }
        val remote = remoteLabels(
            wanted.filter { it.kind == CalloutKind.REMOTE }, sx, sy, lineHeight, gap,
            minX = (leftColumnX ?: 0.0) + gap, maxX = rightColumnX - gap,
        )
        val columnar = wanted.filter { it.kind != CalloutKind.REMOTE }
        val (left, right) = if (leftColumnX == null) {
            emptyList<ArtCallout>() to columnar
        } else {
            columnar.partition { it.kind == CalloutKind.SIDE && it.x < 0.0 }
        }
        return column(right, sx, sy, rightColumnX, LabelAlign.START, top, bottom, lineHeight, gap) +
            (leftColumnX?.let { column(left, sx, sy, it, LabelAlign.END, top, bottom, lineHeight, gap) } ?: emptyList()) +
            remote
    }

    /**
     * Remote units (reel, cabin, power pack) stand on the ground away from the well: their
     * labels go just below GL under each unit, stacked in rows when estimated widths overlap.
     */
    private fun remoteLabels(
        items: List<ArtCallout>,
        sx: (Double) -> Double,
        sy: (Double) -> Double,
        lh: Double,
        gap: Double,
        minX: Double,
        maxX: Double,
    ): List<PlacedCallout> {
        val rows = mutableListOf<MutableList<Pair<Double, Double>>>() // per row: centre x, half width
        return items.sortedBy { it.x }.map { c ->
            val half = c.text.length * lh * ESTIMATED_CHAR_WIDTH / 2
            // keep the label between the label columns (estimated width)
            val x = if (maxX - minX > 2 * half) sx(c.x).coerceIn(minX + half, maxX - half) else (minX + maxX) / 2
            var row = rows.indexOfFirst { r -> r.none { (cx, hw) -> kotlin.math.abs(cx - x) < hw + half + gap } }
            if (row < 0) {
                rows += mutableListOf<Pair<Double, Double>>()
                row = rows.lastIndex
            }
            rows[row] += x to half
            PlacedCallout(c, x, sy(c.y), x, sy(0.0) + 2 * gap + row * (lh + gap), LabelAlign.CENTER, hasLeader = false)
        }
    }

    /** Rough label width per character as a fraction of the line height (layout estimate only). */
    private const val ESTIMATED_CHAR_WIDTH = 0.45

    private fun column(
        items: List<ArtCallout>,
        sx: (Double) -> Double,
        sy: (Double) -> Double,
        x: Double,
        align: LabelAlign,
        top: Double,
        bottom: Double,
        lh: Double,
        gap: Double,
    ): List<PlacedCallout> {
        if (items.isEmpty() || bottom - top < lh) return emptyList()
        val sorted = items.sortedBy { sy(it.y) }
        val capacity = ((bottom - top + gap) / (lh + gap)).toInt()
        val kept = if (sorted.size <= capacity) sorted else sorted.take(capacity)
        val ys = DoubleArray(kept.size)
        // forward pass: push labels down so they do not overlap
        var prevBottom = top - gap
        kept.forEachIndexed { i, c ->
            ys[i] = max(sy(c.y) - lh / 2, prevBottom + gap)
            prevBottom = ys[i] + lh
        }
        // backward pass: pull labels up if the last ones overflow the bottom
        var nextTop = bottom + gap
        for (i in kept.indices.reversed()) {
            ys[i] = min(ys[i], nextTop - gap - lh)
            nextTop = ys[i]
        }
        return kept.mapIndexed { i, c -> PlacedCallout(c, sx(c.x), sy(c.y), x, max(top, ys[i]), align) }
    }
}
