package com.wellsketch.engineer.core.engineering.trajectory

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Minimum curvature method for survey calculations (the industry-standard method for
 * computing well position from MD/inclination/azimuth stations).
 *
 * Between two stations the wellbore is modelled as a circular arc tangent to both station
 * directions. With unit direction vectors t1, t2 (north, east, down) and dogleg angle β
 * between them:
 *
 *     sin²(β/2) = sin²(ΔI/2) + sin I1 · sin I2 · sin²(ΔA/2)   (numerically stable form)
 *     RF        = (2/β) · tan(β/2)                              (ratio factor, → 1 as β → 0)
 *     Δr        = (ΔMD/2) · (t1 + t2) · RF
 *
 * which expands to the familiar ΔN, ΔE, ΔTVD expressions. Status: implemented and
 * unit-tested against analytic straight-line and circular-arc geometry; pending owner
 * engineering review (see README "Engineering validation status").
 */
object MinimumCurvature {

    /** Unit direction vector (north, east, down) for inclination/azimuth in radians. */
    fun direction(inclinationRad: Double, azimuthRad: Double): Vector3 = Vector3(
        north = sin(inclinationRad) * cos(azimuthRad),
        east = sin(inclinationRad) * sin(azimuthRad),
        down = cos(inclinationRad),
    )

    /** Dogleg angle β (radians) between two survey directions. */
    fun doglegAngle(inc1: Double, azi1: Double, inc2: Double, azi2: Double): Double {
        val sHalfInc = sin((inc2 - inc1) / 2.0)
        val sHalfAzi = sin((azi2 - azi1) / 2.0)
        val s2 = sHalfInc * sHalfInc + sin(inc1) * sin(inc2) * sHalfAzi * sHalfAzi
        return 2.0 * asin(sqrt(min(1.0, s2.coerceAtLeast(0.0))))
    }

    /** Ratio factor RF = (2/β)·tan(β/2), using a series expansion for very small β. */
    fun ratioFactor(dogleg: Double): Double =
        if (dogleg < 1e-4) 1.0 + dogleg * dogleg / 12.0 else 2.0 / dogleg * tan(dogleg / 2.0)

    /** Position change over a course length between two unit direction vectors. */
    fun displacement(courseLength: Double, t1: Vector3, t2: Vector3, dogleg: Double): Vector3 =
        (t1 + t2) * (courseLength / 2.0 * ratioFactor(dogleg))

    /**
     * Direction at fraction [f] (0..1) along the circular arc from t1 to t2 (spherical
     * linear interpolation, exact for the minimum-curvature arc).
     */
    fun interpolateDirection(t1: Vector3, t2: Vector3, dogleg: Double, f: Double): Vector3 {
        if (dogleg < 1e-9) return (t1 * (1.0 - f) + t2 * f).normalized()
        val s = sin(dogleg)
        return (t1 * (sin((1.0 - f) * dogleg) / s) + t2 * (sin(f * dogleg) / s)).normalized()
    }
}

/** Simple 3D vector in the (north, east, down) frame. */
data class Vector3(val north: Double, val east: Double, val down: Double) {
    operator fun plus(o: Vector3) = Vector3(north + o.north, east + o.east, down + o.down)
    operator fun times(k: Double) = Vector3(north * k, east * k, down * k)
    fun dot(o: Vector3) = north * o.north + east * o.east + down * o.down
    fun length(): Double = sqrt(dot(this))
    fun normalized(): Vector3 = length().let { l -> if (l == 0.0) this else this * (1.0 / l) }
}
