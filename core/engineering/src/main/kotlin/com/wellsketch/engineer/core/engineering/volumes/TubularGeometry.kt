package com.wellsketch.engineer.core.engineering.volumes

import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.VolumePerLength
import kotlin.math.PI

/**
 * Exact geometric capacities of circular tubulars and annuli (calculation version
 * [VERSION]).
 *
 * Every function is a geometric identity on the diameters it is given — area of a circle
 * A = π/4 · D² — so the only engineering assumption is that the bore is a smooth circular
 * cylinder of the stated diameter. Couplings, upsets, tool joints, washouts and hole
 * enlargement are not represented; callers pass the diameter they want to use (nominal
 * ID, drift, caliper or bit size) and report which one it was.
 */
object TubularGeometry {
    const val VERSION = "1.0.0"

    /** Cross-sectional area of a circle of diameter [d], in m². */
    fun circleArea(d: Length): Double {
        require(d.meters >= 0.0) { "Diameter must not be negative." }
        return PI / 4.0 * d.meters * d.meters
    }

    /** Internal capacity of a bore of inside diameter [id]: π/4 · ID². */
    fun insideCapacity(id: Length): VolumePerLength {
        require(id.meters > 0.0) { "Inside diameter must be positive." }
        return VolumePerLength(circleArea(id))
    }

    /**
     * Annular capacity between an outer boundary of diameter [outerBoundary] (casing ID or
     * hole size) and an inner pipe of outside diameter [innerPipeOd]: π/4 · (D² − d²).
     */
    fun annularCapacity(outerBoundary: Length, innerPipeOd: Length): VolumePerLength {
        require(innerPipeOd.meters >= 0.0) { "Inner pipe OD must not be negative." }
        require(outerBoundary > innerPipeOd) { "Outer boundary must be larger than the inner pipe OD." }
        return VolumePerLength(circleArea(outerBoundary) - circleArea(innerPipeOd))
    }

    /**
     * Open-end (steel) displacement of a pipe: the volume of the pipe wall per length,
     * π/4 · (OD² − ID²). Plain pipe body only; couplings are not included.
     */
    fun openEndDisplacement(od: Length, id: Length): VolumePerLength {
        require(id.meters >= 0.0 && od > id) { "Pipe OD must be larger than its ID." }
        return VolumePerLength(circleArea(od) - circleArea(id))
    }

    /** Closed-end displacement of a pipe (wall plus bore): π/4 · OD². */
    fun closedEndDisplacement(od: Length): VolumePerLength {
        require(od.meters > 0.0) { "Pipe OD must be positive." }
        return VolumePerLength(circleArea(od))
    }

    /** Wall thickness implied by OD and ID: (OD − ID) / 2. */
    fun wallThickness(od: Length, id: Length): Length {
        require(od > id) { "Pipe OD must be larger than its ID." }
        return (od - id) / 2.0
    }
}
