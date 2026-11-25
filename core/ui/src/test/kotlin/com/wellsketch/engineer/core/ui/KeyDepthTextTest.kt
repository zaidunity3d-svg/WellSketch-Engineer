package com.wellsketch.engineer.core.ui

import com.wellsketch.engineer.core.engineering.summary.KeyDepth
import com.wellsketch.engineer.core.engineering.summary.KeyDepthKind
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.model.well.PerforationStatus
import com.wellsketch.engineer.core.ui.format.KeyDepthText
import com.wellsketch.engineer.core.ui.format.UnitFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyDepthTextTest {
    private val fm = UnitFormatter(UnitSystem.FIELD_METRIC)
    private val oil = UnitFormatter(UnitSystem.OILFIELD)
    private fun m(v: Double) = Length.meters(v)

    @Test
    fun shoeRow() {
        val row = KeyDepth(KeyDepthKind.CASING_SHOE, "c", "Surface casing", m(595.0), m(594.25), outerDiameter = Length.inches(13.375))
        assertEquals("13-3/8 in shoe", KeyDepthText.label(row, fm))
        assertEquals("595.0 m MD · 594.3 m TVD", KeyDepthText.value(row, fm))
        assertEquals("1952.1 ft MD · 1949.6 ft TVD", KeyDepthText.value(row, oil))
    }

    @Test
    fun perforationInterval() {
        val row = KeyDepth(
            KeyDepthKind.PERFORATIONS, "p", "Perforations", m(2300.0), m(2250.0), m(2320.0), m(2266.0),
            perforationStatus = PerforationStatus.ISOLATED,
        )
        assertEquals("Perforations (isolated)", KeyDepthText.label(row, fm))
        assertEquals("2300.0–2320.0 m MD · 2250.0–2266.0 m TVD", KeyDepthText.value(row, fm))
    }

    @Test
    fun namedAndFixedLabels() {
        assertEquals("Production packer (demo)", KeyDepthText.label(KeyDepth(KeyDepthKind.PACKER, "k", "Production packer (demo)", m(1.0), m(1.0)), fm))
        assertEquals("End of tubing", KeyDepthText.label(KeyDepth(KeyDepthKind.END_OF_TUBING, "t", "Tubing", m(1.0), m(1.0)), fm))
        assertEquals("Total depth", KeyDepthText.label(KeyDepth(KeyDepthKind.TOTAL_DEPTH, "h", "Total depth", m(1.0), m(1.0)), fm))
        assertEquals("7 in liner top", KeyDepthText.label(KeyDepth(KeyDepthKind.LINER_TOP, "l", "Liner", m(1.0), m(1.0), outerDiameter = Length.inches(7.0)), fm))
    }
}
