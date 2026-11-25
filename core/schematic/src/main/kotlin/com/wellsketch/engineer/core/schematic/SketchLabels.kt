package com.wellsketch.engineer.core.schematic

import com.wellsketch.engineer.core.model.well.CompletionComponentType
import com.wellsketch.engineer.core.model.well.PerforationStatus

enum class SketchLabelKind { CASING_SHOE, LINER_TOP, END_OF_TUBING, PACKER, PLUG, PERFORATIONS }

/**
 * A depth label of the live sketch, derived from scene data only (never typed text):
 * casing shoes with their OD, liner tops, end of tubing, packers, plugs and perforation
 * intervals. Depths are canonical MD in metres; [diameter] is the OD in metres when known.
 */
data class SketchLabel(
    val kind: SketchLabelKind,
    val sourceId: String,
    val md: Double,
    val mdBottom: Double? = null,
    /** Radius at which the label's leader starts (metres from the axis). */
    val radius: Double,
    val diameter: Double? = null,
    val name: String,
    val perforationStatus: PerforationStatus? = null,
)

object SketchLabels {

    fun collect(scene: SchematicScene): List<SketchLabel> {
        val out = mutableListOf<SketchLabel>()
        val namesBySource = scene.elements.associate { it.sourceId to it.label }
        for (m in scene.markers) {
            when (m.kind) {
                MarkerKind.CASING_SHOE -> out += SketchLabel(
                    SketchLabelKind.CASING_SHOE, m.sourceId, m.md, radius = m.radius, diameter = 2 * m.radius,
                    name = namesBySource[m.sourceId] ?: m.label,
                )
                MarkerKind.LINER_TOP -> out += SketchLabel(
                    SketchLabelKind.LINER_TOP, m.sourceId, m.md, radius = m.radius, diameter = 2 * m.radius,
                    name = namesBySource[m.sourceId] ?: m.label,
                )
                MarkerKind.END_OF_TUBING -> out += SketchLabel(SketchLabelKind.END_OF_TUBING, m.sourceId, m.md, radius = m.radius, name = m.label)
                MarkerKind.TOP_OF_CEMENT -> Unit // drawn as a tick; omitted from labels to keep the sketch legible
            }
        }
        for (e in scene.elements) {
            val detail = e.detail
            when {
                e.kind == ElementKind.COMPLETION_COMPONENT && detail is ElementDetail.Component && detail.type == CompletionComponentType.PACKER ->
                    out += SketchLabel(SketchLabelKind.PACKER, e.sourceId, e.mdTop, radius = e.outerRadius, name = e.label)
                e.kind == ElementKind.COMPLETION_COMPONENT && detail is ElementDetail.Component &&
                    (detail.type == CompletionComponentType.BRIDGE_PLUG || detail.type == CompletionComponentType.CEMENT_RETAINER) ->
                    out += SketchLabel(SketchLabelKind.PLUG, e.sourceId, e.mdTop, radius = e.outerRadius, name = e.label)
                e.kind == ElementKind.PERFORATION && detail is ElementDetail.Perforation ->
                    out += SketchLabel(
                        SketchLabelKind.PERFORATIONS, e.sourceId, e.mdTop, e.mdBottom, radius = e.outerRadius,
                        name = e.label, perforationStatus = detail.status,
                    )
            }
        }
        return out.sortedBy { it.md }
    }
}

/**
 * Places label boxes (screen pixels) next to their anchors without overlaps: each label goes
 * to the right of its anchor; when there is no room it goes left of the mirrored anchor on
 * the other side of the well (so it never covers the well itself), and only when neither
 * side fits is it clamped to the right edge. A label is pushed down below any label it would
 * overlap; labels that cannot fit above maxY are dropped.
 */
object LabelPlacer {
    /**
     * [anchorX]/[anchorY]: anchor on the right-hand side of the well; [leftAnchorX]/
     * [leftAnchorY]: the mirrored anchor on the left-hand side (defaults to the right anchor).
     */
    data class Request(
        val id: String,
        val anchorX: Double,
        val anchorY: Double,
        val width: Double,
        val height: Double,
        val leftAnchorX: Double = anchorX,
        val leftAnchorY: Double = anchorY,
    )

    data class Placed(
        val id: String,
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val anchorX: Double,
        val anchorY: Double,
    ) {
        val right: Double get() = x + width
        val bottom: Double get() = y + height

        /** True when the label is not level with its anchor and needs a leader line. */
        val displaced: Boolean get() = anchorY < y - 1.0 || anchorY > bottom + 1.0

        fun overlaps(o: Placed, gap: Double) =
            x < o.right + gap && o.x < right + gap && y < o.bottom + gap && o.y < bottom + gap
    }

    private data class Sided(val request: Request, val x: Double, val anchorX: Double, val anchorY: Double)

    fun place(
        requests: List<Request>,
        offset: Double,
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        gap: Double = 2.0,
    ): List<Placed> {
        val placed = mutableListOf<Placed>()
        // Choose the side first, then stack labels in the order of the anchors they point to,
        // so leaders do not cross (the mirrored side of an inclined well sits at other heights).
        val sided = requests.map { r ->
            val rightX = r.anchorX + offset
            val leftX = r.leftAnchorX - offset - r.width
            when {
                rightX + r.width <= maxX -> Sided(r, rightX, r.anchorX, r.anchorY)
                leftX >= minX -> Sided(r, leftX, r.leftAnchorX, r.leftAnchorY)
                else -> Sided(r, minOf(rightX, maxX - r.width).coerceAtLeast(minX), r.anchorX, r.anchorY)
            }
        }
        for ((r, x, ax, ay) in sided.sortedBy { it.anchorY }) {
            var candidate = Placed(r.id, x, maxOf(minY, ay - r.height / 2), r.width, r.height, ax, ay)
            var guard = 0
            while (guard++ < 200) {
                val blocker = placed.filter { candidate.overlaps(it, gap) }.maxByOrNull { it.bottom } ?: break
                candidate = candidate.copy(y = blocker.bottom + gap)
            }
            if (candidate.bottom <= maxY) placed += candidate
        }
        return placed
    }
}
