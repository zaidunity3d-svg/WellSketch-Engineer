package com.wellsketch.engineer.core.ui.format

import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.PerforationStatus
import com.wellsketch.engineer.core.schematic.SketchLabel
import com.wellsketch.engineer.core.schematic.SketchLabelKind

/**
 * Text of the live-sketch depth labels, built from scene data only. Depths are measured
 * depths from the canonical datum (RKB) and always say "MD", so they are never mistaken for
 * TVD or GL depths. Example: "13-3/8 in shoe @ 595.0 m MD".
 */
object SketchLabelText {

    fun of(label: SketchLabel, f: UnitFormatter): String = when (label.kind) {
        SketchLabelKind.CASING_SHOE -> "${sizePrefix(label, f)}shoe @ ${md(label.md, f)}"
        SketchLabelKind.LINER_TOP -> "${sizePrefix(label, f)}liner top @ ${md(label.md, f)}"
        SketchLabelKind.END_OF_TUBING -> "EOT @ ${md(label.md, f)}"
        SketchLabelKind.PACKER -> "Packer @ ${md(label.md, f)}"
        SketchLabelKind.PLUG -> "${label.name} @ ${md(label.md, f)}"
        SketchLabelKind.PERFORATIONS -> {
            val bottom = label.mdBottom ?: label.md
            val status = label.perforationStatus?.takeIf { it != PerforationStatus.OPEN }?.let { " (${it.label.lowercase()})" } ?: ""
            "Perfs ${value(label.md, f)}–${value(bottom, f)} ${f.depthUnit} MD$status"
        }
    }

    private fun sizePrefix(label: SketchLabel, f: UnitFormatter): String =
        label.diameter?.takeIf { it > 0.0 }?.let { "${f.nominalSize(Length.meters(it))} " } ?: ""

    private fun value(mdMeters: Double, f: UnitFormatter) = UnitFormatter.number(f.depthValue(Length.meters(mdMeters)), 1)

    private fun md(mdMeters: Double, f: UnitFormatter) = "${value(mdMeters, f)} ${f.depthUnit} MD"
}
