package com.wellsketch.engineer.core.model.units

/**
 * Exact unit-conversion constants used throughout WellSketch Engineer.
 *
 * Every value below is an exact definition (not a measured or empirical constant):
 * - International foot (1959 agreement): 1 ft = 0.3048 m exactly. The US survey foot
 *   (1200/3937 m) is NOT used; it was retired by NIST/NGS at the end of 2022.
 * - Inch: 1 in = 0.0254 m exactly.
 * - Avoirdupois pound (mass): 1 lb = 0.45359237 kg exactly.
 * - Standard gravity: g_n = 9.80665 m/s² exactly, so 1 lbf = 0.45359237 × 9.80665 N.
 * - US liquid gallon: 231 in³ exactly. US oil barrel: 42 US gallons exactly.
 *
 * Internal storage is SI (metre, pascal, kilogram, cubic metre). Oilfield units are a
 * display/input concern handled by formatters in the UI layer.
 */
object UnitDefinitions {
    const val METERS_PER_FOOT: Double = 0.3048
    const val METERS_PER_INCH: Double = 0.0254
    const val KILOGRAMS_PER_POUND: Double = 0.45359237
    const val STANDARD_GRAVITY_M_PER_S2: Double = 9.80665

    /** 1 lbf in newtons = lb (kg) × g_n. */
    const val NEWTONS_PER_POUND_FORCE: Double = KILOGRAMS_PER_POUND * STANDARD_GRAVITY_M_PER_S2

    /** 1 in² in m². */
    const val SQUARE_METERS_PER_SQUARE_INCH: Double = METERS_PER_INCH * METERS_PER_INCH

    /** 1 psi = 1 lbf/in² in pascals (≈ 6894.757 Pa). */
    const val PASCALS_PER_PSI: Double = NEWTONS_PER_POUND_FORCE / SQUARE_METERS_PER_SQUARE_INCH

    const val PASCALS_PER_BAR: Double = 100_000.0

    /** 1 US gallon = 231 in³ in m³ (= 3.785411784e-3 m³). */
    const val CUBIC_METERS_PER_US_GALLON: Double =
        231.0 * METERS_PER_INCH * METERS_PER_INCH * METERS_PER_INCH

    /** 1 US oil barrel = 42 US gallons in m³ (= 0.158987294928 m³). */
    const val CUBIC_METERS_PER_US_BARREL: Double = 42.0 * CUBIC_METERS_PER_US_GALLON

    /** 1 lb/ft in kg/m. */
    const val KG_PER_M_PER_LB_PER_FT: Double = KILOGRAMS_PER_POUND / METERS_PER_FOOT

    /** 1 lb/US gal (ppg) in kg/m³. */
    const val KG_PER_M3_PER_PPG: Double = KILOGRAMS_PER_POUND / CUBIC_METERS_PER_US_GALLON
}
