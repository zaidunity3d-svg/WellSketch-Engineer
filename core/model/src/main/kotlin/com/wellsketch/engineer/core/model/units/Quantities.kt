package com.wellsketch.engineer.core.model.units

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs

/*
 * Strongly typed physical quantities. Each is an inline value class over an SI Double so
 * that, for example, a diameter can never be passed where a pressure is expected, and the
 * unit of a stored number is never ambiguous. Use the companion factories to create
 * values from oilfield or metric inputs.
 */

/** A length (depth, diameter, wall thickness, height...). Stored in metres. */
@Serializable
@JvmInline
value class Length(val meters: Double) : Comparable<Length> {
    val feet: Double get() = meters / UnitDefinitions.METERS_PER_FOOT
    val inches: Double get() = meters / UnitDefinitions.METERS_PER_INCH
    val millimeters: Double get() = meters * 1000.0

    operator fun plus(other: Length): Length = Length(meters + other.meters)
    operator fun minus(other: Length): Length = Length(meters - other.meters)
    operator fun unaryMinus(): Length = Length(-meters)
    operator fun times(factor: Double): Length = Length(meters * factor)
    operator fun div(divisor: Double): Length = Length(meters / divisor)
    operator fun div(other: Length): Double = meters / other.meters
    override operator fun compareTo(other: Length): Int = meters.compareTo(other.meters)

    fun isApproximately(other: Length, tolerance: Length = TOLERANCE): Boolean =
        abs(meters - other.meters) <= tolerance.meters

    override fun toString(): String = "${meters} m"

    companion object {
        val ZERO = Length(0.0)

        /** Geometric comparison tolerance (1 micrometre); not an engineering tolerance. */
        val TOLERANCE = Length(1e-6)

        fun meters(value: Double) = Length(value)
        fun feet(value: Double) = Length(value * UnitDefinitions.METERS_PER_FOOT)
        fun inches(value: Double) = Length(value * UnitDefinitions.METERS_PER_INCH)
        fun millimeters(value: Double) = Length(value / 1000.0)
    }
}

/** Pressure. Stored in pascals. */
@Serializable
@JvmInline
value class Pressure(val pascals: Double) : Comparable<Pressure> {
    val psi: Double get() = pascals / UnitDefinitions.PASCALS_PER_PSI
    val bar: Double get() = pascals / UnitDefinitions.PASCALS_PER_BAR
    val kilopascals: Double get() = pascals / 1000.0
    val megapascals: Double get() = pascals / 1.0e6

    operator fun plus(other: Pressure) = Pressure(pascals + other.pascals)
    operator fun minus(other: Pressure) = Pressure(pascals - other.pascals)
    override operator fun compareTo(other: Pressure): Int = pascals.compareTo(other.pascals)

    companion object {
        val ZERO = Pressure(0.0)
        fun pascals(value: Double) = Pressure(value)
        fun psi(value: Double) = Pressure(value * UnitDefinitions.PASCALS_PER_PSI)
        fun bar(value: Double) = Pressure(value * UnitDefinitions.PASCALS_PER_BAR)
        fun kilopascals(value: Double) = Pressure(value * 1000.0)
        fun megapascals(value: Double) = Pressure(value * 1.0e6)
    }
}

/** Mass density (e.g. fluid density). Stored in kg/m³. */
@Serializable
@JvmInline
value class Density(val kilogramsPerCubicMeter: Double) : Comparable<Density> {
    val poundsPerGallon: Double get() = kilogramsPerCubicMeter / UnitDefinitions.KG_PER_M3_PER_PPG
    val gramsPerCubicCentimeter: Double get() = kilogramsPerCubicMeter / 1000.0
    override operator fun compareTo(other: Density): Int =
        kilogramsPerCubicMeter.compareTo(other.kilogramsPerCubicMeter)

    companion object {
        fun kilogramsPerCubicMeter(value: Double) = Density(value)
        fun poundsPerGallon(value: Double) = Density(value * UnitDefinitions.KG_PER_M3_PER_PPG)
        fun gramsPerCubicCentimeter(value: Double) = Density(value * 1000.0)
    }
}

/** Mass per unit length (nominal tubular weight). Stored in kg/m. */
@Serializable
@JvmInline
value class LinearMass(val kilogramsPerMeter: Double) : Comparable<LinearMass> {
    val poundsPerFoot: Double get() = kilogramsPerMeter / UnitDefinitions.KG_PER_M_PER_LB_PER_FT
    override operator fun compareTo(other: LinearMass): Int =
        kilogramsPerMeter.compareTo(other.kilogramsPerMeter)

    companion object {
        fun kilogramsPerMeter(value: Double) = LinearMass(value)
        fun poundsPerFoot(value: Double) = LinearMass(value * UnitDefinitions.KG_PER_M_PER_LB_PER_FT)
    }
}

/** Volume. Stored in m³. */
@Serializable
@JvmInline
value class Volume(val cubicMeters: Double) : Comparable<Volume> {
    val barrels: Double get() = cubicMeters / UnitDefinitions.CUBIC_METERS_PER_US_BARREL
    val usGallons: Double get() = cubicMeters / UnitDefinitions.CUBIC_METERS_PER_US_GALLON
    val liters: Double get() = cubicMeters * 1000.0
    operator fun plus(other: Volume) = Volume(cubicMeters + other.cubicMeters)
    operator fun minus(other: Volume) = Volume(cubicMeters - other.cubicMeters)
    override operator fun compareTo(other: Volume): Int = cubicMeters.compareTo(other.cubicMeters)

    companion object {
        val ZERO = Volume(0.0)
        fun cubicMeters(value: Double) = Volume(value)
        fun barrels(value: Double) = Volume(value * UnitDefinitions.CUBIC_METERS_PER_US_BARREL)
        fun usGallons(value: Double) = Volume(value * UnitDefinitions.CUBIC_METERS_PER_US_GALLON)
        fun liters(value: Double) = Volume(value / 1000.0)
    }
}

/** Plane angle (inclination, azimuth). Stored in degrees, as surveys are reported. */
@Serializable
@JvmInline
value class Angle(val degrees: Double) : Comparable<Angle> {
    val radians: Double get() = degrees * PI / 180.0
    override operator fun compareTo(other: Angle): Int = degrees.compareTo(other.degrees)

    companion object {
        val ZERO = Angle(0.0)
        fun degrees(value: Double) = Angle(value)
        fun radians(value: Double) = Angle(value * 180.0 / PI)
    }
}
