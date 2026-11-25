package com.wellsketch.engineer.core.ui.format

import com.wellsketch.engineer.core.model.units.Density
import com.wellsketch.engineer.core.model.units.FlowRate
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.model.units.PressureGradient
import com.wellsketch.engineer.core.model.units.UnitDefinitions
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.model.units.Volume
import com.wellsketch.engineer.core.model.units.VolumePerLength
import java.util.Locale

/**
 * Formats and parses quantities for display. Always uses Locale.US so the English-only UI
 * shows Western digits and a dot decimal separator regardless of the device locale.
 *
 * | Quantity  | Oilfield | Metric | Field metric |
 * |-----------|----------|--------|--------------|
 * | Depth     | ft       | m      | m            |
 * | Diameter  | in       | mm     | in           |
 * | Pressure  | psi      | bar    | psi          |
 * | Volume    | bbl      | m³     | bbl          |
 * | Capacity  | bbl/ft   | L/m    | bbl/m        |
 * | Density   | ppg      | kg/m³  | SG           |
 * | Gradient  | psi/ft   | bar/m  | psi/m        |
 * | Pump rate | bbl/min  | m³/min | bbl/min      |
 * | Velocity  | ft/min   | m/min  | m/min        |
 */
class UnitFormatter(val system: UnitSystem) {

    private val feetDepths get() = system == UnitSystem.OILFIELD
    private val inchDiameters get() = system != UnitSystem.METRIC
    private val psiPressures get() = system != UnitSystem.METRIC

    val depthUnit: String get() = if (feetDepths) "ft" else "m"
    val diameterUnit: String get() = if (inchDiameters) "in" else "mm"
    val pressureUnit: String get() = if (psiPressures) "psi" else "bar"
    val volumeUnit: String get() = if (system == UnitSystem.METRIC) "m³" else "bbl"
    val capacityUnit: String
        get() = when (system) {
            UnitSystem.OILFIELD -> "bbl/ft"
            UnitSystem.METRIC -> "L/m"
            UnitSystem.FIELD_METRIC -> "bbl/m"
        }
    val densityUnit: String
        get() = when (system) {
            UnitSystem.OILFIELD -> "ppg"
            UnitSystem.METRIC -> "kg/m³"
            UnitSystem.FIELD_METRIC -> "SG"
        }
    val gradientUnit: String
        get() = when (system) {
            UnitSystem.OILFIELD -> "psi/ft"
            UnitSystem.METRIC -> "bar/m"
            UnitSystem.FIELD_METRIC -> "psi/m"
        }
    val flowRateUnit: String get() = if (system == UnitSystem.METRIC) "m³/min" else "bbl/min"
    val velocityUnit: String get() = if (feetDepths) "ft/min" else "m/min"

    // --- Depth ---------------------------------------------------------------------------
    fun depthValue(length: Length): Double = if (feetDepths) length.feet else length.meters
    fun depthFromValue(value: Double): Length = if (feetDepths) Length.feet(value) else Length.meters(value)
    fun depth(length: Length): String = "${number(depthValue(length), 1)} $depthUnit"

    // --- Diameter ------------------------------------------------------------------------
    fun diameter(length: Length): String = if (inchDiameters) {
        "${number(length.inches, 3)} in"
    } else {
        "${number(length.millimeters, 1)} mm"
    }

    // --- Pressure ------------------------------------------------------------------------
    fun pressureValue(pressure: Pressure): Double = if (psiPressures) pressure.psi else pressure.bar
    fun pressureFromValue(value: Double): Pressure = if (psiPressures) Pressure.psi(value) else Pressure.bar(value)
    fun pressure(pressure: Pressure): String = if (psiPressures) {
        "${number(pressure.psi, 0)} psi"
    } else {
        "${number(pressure.bar, 1)} bar"
    }

    // --- Volume and capacity -------------------------------------------------------------
    fun volumeValue(volume: Volume): Double = if (system == UnitSystem.METRIC) volume.cubicMeters else volume.barrels
    fun volume(volume: Volume): String = if (system == UnitSystem.METRIC) {
        "${number(volume.cubicMeters, 3)} m³"
    } else {
        "${number(volume.barrels, 2)} bbl"
    }

    fun capacityValue(capacity: VolumePerLength): Double = when (system) {
        UnitSystem.OILFIELD -> capacity.barrelsPerFoot
        UnitSystem.METRIC -> capacity.litersPerMeter
        UnitSystem.FIELD_METRIC -> capacity.barrelsPerMeter
    }

    fun capacity(capacity: VolumePerLength): String = when (system) {
        UnitSystem.OILFIELD -> "${number(capacity.barrelsPerFoot, 5)} bbl/ft"
        UnitSystem.METRIC -> "${number(capacity.litersPerMeter, 3)} L/m"
        UnitSystem.FIELD_METRIC -> "${number(capacity.barrelsPerMeter, 5)} bbl/m"
    }

    // --- Density and gradient ------------------------------------------------------------
    fun densityValue(density: Density): Double = when (system) {
        UnitSystem.OILFIELD -> density.poundsPerGallon
        UnitSystem.METRIC -> density.kilogramsPerCubicMeter
        UnitSystem.FIELD_METRIC -> density.kilogramsPerCubicMeter / WATER_KG_PER_M3
    }

    fun densityFromValue(value: Double): Density = when (system) {
        UnitSystem.OILFIELD -> Density.poundsPerGallon(value)
        UnitSystem.METRIC -> Density.kilogramsPerCubicMeter(value)
        UnitSystem.FIELD_METRIC -> Density.kilogramsPerCubicMeter(value * WATER_KG_PER_M3)
    }

    fun density(density: Density): String = when (system) {
        UnitSystem.OILFIELD -> "${number(density.poundsPerGallon, 2)} ppg"
        UnitSystem.METRIC -> "${number(density.kilogramsPerCubicMeter, 0)} kg/m³"
        UnitSystem.FIELD_METRIC -> "${number(density.kilogramsPerCubicMeter / WATER_KG_PER_M3, 3)} SG"
    }

    fun gradient(gradient: PressureGradient): String = when (system) {
        UnitSystem.OILFIELD -> "${number(gradient.psiPerFoot, 4)} psi/ft"
        UnitSystem.METRIC -> "${number(gradient.barPerMeter, 5)} bar/m"
        UnitSystem.FIELD_METRIC -> "${number(gradient.psiPerMeter, 4)} psi/m"
    }

    // --- Rates, velocity, time -----------------------------------------------------------
    fun flowRateValue(rate: FlowRate): Double = if (system == UnitSystem.METRIC) rate.cubicMetersPerMinute else rate.barrelsPerMinute
    fun flowRateFromValue(value: Double): FlowRate =
        if (system == UnitSystem.METRIC) FlowRate.cubicMetersPerMinute(value) else FlowRate.barrelsPerMinute(value)
    fun flowRate(rate: FlowRate): String = "${number(flowRateValue(rate), 2)} $flowRateUnit"

    /** Velocity given in metres per minute. */
    fun velocity(metersPerMinute: Double): String =
        "${number(if (feetDepths) metersPerMinute / UnitDefinitions.METERS_PER_FOOT else metersPerMinute, 1)} $velocityUnit"

    fun minutes(value: Double): String =
        if (value >= 120.0) "${number(value, 0)} min (${number(value / 60.0, 1)} h)" else "${number(value, 1)} min"

    // --- Input ---------------------------------------------------------------------------
    /** Text for an editable depth field. */
    fun depthInput(length: Length): String = number(depthValue(length), 2)

    /** Parses user input (accepts "," or "." as decimal separator); null if invalid. */
    fun parseDepth(text: String): Length? = parseNumber(text)?.let(::depthFromValue)

    companion object {
        /** Reference water density for SG (specific gravity relative to 1000 kg/m³). */
        const val WATER_KG_PER_M3 = 1000.0

        fun number(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)

        /** Parses a decimal number typed by the user ("," or "."); null if invalid or not finite. */
        fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
