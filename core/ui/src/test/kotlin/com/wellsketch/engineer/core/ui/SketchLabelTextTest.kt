package com.wellsketch.engineer.core.ui

import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.model.well.PerforationStatus
import com.wellsketch.engineer.core.schematic.SketchLabel
import com.wellsketch.engineer.core.schematic.SketchLabelKind
import com.wellsketch.engineer.core.ui.format.SketchLabelText
import com.wellsketch.engineer.core.ui.format.UnitFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class SketchLabelTextTest {
    private val fieldMetric = UnitFormatter(UnitSystem.FIELD_METRIC)
    private val oilfield = UnitFormatter(UnitSystem.OILFIELD)
    private val metric = UnitFormatter(UnitSystem.METRIC)

    private fun label(kind: SketchLabelKind, md: Double, diameter: Double? = null, bottom: Double? = null, status: PerforationStatus? = null) =
        SketchLabel(kind, "id", md, bottom, radius = (diameter ?: 0.1) / 2, diameter = diameter, name = "Bridge plug (demo)", perforationStatus = status)

    @Test
    fun shoeShowsNominalSizeAndMeasuredDepth() {
        val shoe = label(SketchLabelKind.CASING_SHOE, 595.0, Length.inches(13.375).meters)
        assertEquals("13-3/8 in shoe @ 595.0 m MD", SketchLabelText.of(shoe, fieldMetric))
        assertEquals("13-3/8 in shoe @ 1952.1 ft MD", SketchLabelText.of(shoe, oilfield))
        assertEquals("339.7 mm shoe @ 595.0 m MD", SketchLabelText.of(shoe, metric))
    }

    @Test
    fun linerTopAndEndOfTubing() {
        assertEquals("7 in liner top @ 2450.0 m MD", SketchLabelText.of(label(SketchLabelKind.LINER_TOP, 2450.0, Length.inches(7.0).meters), fieldMetric))
        assertEquals("EOT @ 2930.5 m MD", SketchLabelText.of(label(SketchLabelKind.END_OF_TUBING, 2930.5), fieldMetric))
    }

    @Test
    fun missingDiameterDropsTheSize() {
        assertEquals("shoe @ 100.0 m MD", SketchLabelText.of(label(SketchLabelKind.CASING_SHOE, 100.0), fieldMetric))
    }

    @Test
    fun packerAndPlug() {
        assertEquals("Packer @ 2900.0 m MD", SketchLabelText.of(label(SketchLabelKind.PACKER, 2900.0), fieldMetric))
        assertEquals("Bridge plug (demo) @ 3000.0 m MD", SketchLabelText.of(label(SketchLabelKind.PLUG, 3000.0), fieldMetric))
    }

    @Test
    fun perforationIntervalWithStatus() {
        val open = label(SketchLabelKind.PERFORATIONS, 2950.0, bottom = 2960.0, status = PerforationStatus.OPEN)
        assertEquals("Perfs 2950.0–2960.0 m MD", SketchLabelText.of(open, fieldMetric))
        val squeezed = open.copy(perforationStatus = PerforationStatus.SQUEEZED)
        assertEquals("Perfs 2950.0–2960.0 m MD (squeezed)", SketchLabelText.of(squeezed, fieldMetric))
        assertEquals("Perfs 9678.5–9711.3 ft MD", SketchLabelText.of(open, oilfield))
    }
}
