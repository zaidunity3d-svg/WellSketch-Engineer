package com.wellsketch.engineer.core.ui.format

import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.model.units.UnitSystem
import java.util.Locale

/**
 * Formats and parses quantities for display. Always uses Locale.US so the English-only UI
 * shows Western digits and a dot decimal separator regardless of the device locale.
 */
class UnitFormatter(val system: UnitSystem) {

    val depthUnit: String get() = if (system == UnitSystem.OILFIELD) "ft" else "m"
    val diameterUnit: String get() = if (system == UnitSystem.OILFIELD) "in" else "mm"
    val pressureUnit: String get() = if (system == UnitSystem.OILFIELD) "psi" else "bar"

    fun depthValue(length: Length): Double = if (system == UnitSystem.OILFIELD) length.feet else length.meters
    fun depthFromValue(value: Double): Length = if (system == UnitSystem.OILFIELD) Length.feet(value) else Length.meters(value)

    fun depth(length: Length): String = "${number(depthValue(length), 1)} $depthUnit"

    fun diameter(length: Length): String = if (system == UnitSystem.OILFIELD) {
        "${number(length.inches, 3)} in"
    } else {
        "${number(length.millimeters, 1)} mm"
    }

    fun pressure(pressure: Pressure): String = if (system == UnitSystem.OILFIELD) {
        "${number(pressure.psi, 0)} psi"
    } else {
        "${number(pressure.bar, 1)} bar"
    }

    /** Text for an editable depth field. */
    fun depthInput(length: Length): String = number(depthValue(length), 2)

    /** Parses user input (accepts "," or "." as decimal separator); null if invalid. */
    fun parseDepth(text: String): Length? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::depthFromValue)

    companion object {
        fun number(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)
    }
}
