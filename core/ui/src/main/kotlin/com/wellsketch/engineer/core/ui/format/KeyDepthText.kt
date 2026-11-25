package com.wellsketch.engineer.core.ui.format

import com.wellsketch.engineer.core.engineering.summary.KeyDepth
import com.wellsketch.engineer.core.engineering.summary.KeyDepthKind
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.PerforationStatus

/** Label and value text of the "Key depths" summary rows. */
object KeyDepthText {

    fun label(row: KeyDepth, f: UnitFormatter): String = when (row.kind) {
        KeyDepthKind.CASING_SHOE -> "${size(row, f)}shoe"
        KeyDepthKind.LINER_TOP -> "${size(row, f)}liner top"
        KeyDepthKind.PACKER, KeyDepthKind.PLUG -> row.name
        KeyDepthKind.END_OF_TUBING -> "End of tubing"
        KeyDepthKind.PERFORATIONS -> "Perforations" +
            (row.perforationStatus?.takeIf { it != PerforationStatus.OPEN }?.let { " (${it.label.lowercase()})" } ?: "")
        KeyDepthKind.TOTAL_DEPTH -> "Total depth"
    }

    /** "595.0 m MD · 594.9 m TVD", or a range for intervals. */
    fun value(row: KeyDepth, f: UnitFormatter): String {
        val md = range(row.md, row.mdBottom, f)
        val tvd = range(row.tvd, row.tvdBottom, f)
        return "$md ${f.depthUnit} MD · $tvd ${f.depthUnit} TVD"
    }

    private fun size(row: KeyDepth, f: UnitFormatter) = row.outerDiameter?.let { "${f.nominalSize(it)} " } ?: ""

    private fun range(top: Length, bottom: Length?, f: UnitFormatter): String {
        val a = UnitFormatter.number(f.depthValue(top), 1)
        return if (bottom == null) a else "$a–${UnitFormatter.number(f.depthValue(bottom), 1)}"
    }
}
