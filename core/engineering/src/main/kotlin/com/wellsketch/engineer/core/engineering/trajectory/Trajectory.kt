package com.wellsketch.engineer.core.engineering.trajectory

import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.trajectory.DirectionalSurvey
import com.wellsketch.engineer.core.model.units.Angle
import com.wellsketch.engineer.core.model.units.Length
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** A computed point on the well path. MD and TVD are relative to the trajectory reference. */
data class TrajectoryPoint(
    val md: Length,
    val tvd: Length,
    val north: Length,
    val east: Length,
    val inclination: Angle,
    val azimuth: Angle,
    /** Horizontal displacement projected on the vertical-section azimuth. */
    val verticalSection: Length,
    /** Dogleg severity of the course ending at this point, in degrees per 30 m. */
    val doglegSeverityDegPer30m: Double = 0.0,
)

/** How the trajectory geometry was obtained. Shown to the user as a data-quality flag. */
enum class TrajectorySource(val label: String) {
    SURVEY("Computed from survey (minimum curvature)"),
    ASSUMED_VERTICAL("No survey: well assumed vertical"),
}

/**
 * Well path computed from a directional survey, with exact interpolation along the
 * minimum-curvature arcs. Beyond the last station the path is projected straight along
 * the last direction and flagged via [isExtrapolated].
 */
class Trajectory private constructor(
    val reference: DepthReference,
    val source: TrajectorySource,
    val verticalSectionAzimuth: Angle,
    val stations: List<TrajectoryPoint>,
) {
    val lastStationMd: Length get() = stations.last().md

    fun isExtrapolated(md: Length): Boolean =
        source == TrajectorySource.SURVEY && md > lastStationMd

    /** Position at [md] (relative to [reference]). MD above the first point is treated as vertical. */
    fun positionAt(md: Length): TrajectoryPoint {
        val first = stations.first()
        if (md <= first.md) {
            return first.copy(md = md, tvd = first.tvd - (first.md - md))
        }
        val last = stations.last()
        if (md >= last.md) return extrapolate(last, md)
        val index = segmentIndex(md)
        val a = stations[index]
        val b = stations[index + 1]
        val t1 = directionOf(a)
        val t2 = directionOf(b)
        val dogleg = MinimumCurvature.doglegAngle(
            a.inclination.radians, a.azimuth.radians, b.inclination.radians, b.azimuth.radians,
        )
        val course = (b.md - a.md).meters
        val f = (md - a.md).meters / course
        val tf = MinimumCurvature.interpolateDirection(t1, t2, dogleg, f)
        val d = MinimumCurvature.displacement(course * f, t1, tf, dogleg * f)
        return pointFrom(a, md, d, tf, b.doglegSeverityDegPer30m)
    }

    fun tvdAt(md: Length): Length = positionAt(md).tvd

    private fun extrapolate(last: TrajectoryPoint, md: Length): TrajectoryPoint {
        val t = directionOf(last)
        val d = t * (md - last.md).meters
        return pointFrom(last, md, d, t, 0.0)
    }

    private fun pointFrom(
        origin: TrajectoryPoint,
        md: Length,
        d: Vector3,
        direction: Vector3,
        dls: Double,
    ): TrajectoryPoint {
        val north = origin.north.meters + d.north
        val east = origin.east.meters + d.east
        return TrajectoryPoint(
            md = md,
            tvd = Length(origin.tvd.meters + d.down),
            north = Length(north),
            east = Length(east),
            inclination = Angle.radians(acos(direction.down.coerceIn(-1.0, 1.0))),
            azimuth = Angle.radians(normalizeAzimuth(atan2(direction.east, direction.north))),
            verticalSection = Length(verticalSection(north, east, verticalSectionAzimuth)),
            doglegSeverityDegPer30m = dls,
        )
    }

    private fun segmentIndex(md: Length): Int {
        var lo = 0
        var hi = stations.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (stations[mid].md <= md) lo = mid else hi = mid
        }
        return lo
    }

    private fun directionOf(p: TrajectoryPoint) =
        MinimumCurvature.direction(p.inclination.radians, p.azimuth.radians)

    companion object {
        private const val THIRTY_METERS = 30.0

        /** Computes station positions with the minimum curvature method. */
        fun fromSurvey(survey: DirectionalSurvey): Trajectory {
            val tie = survey.tieIn
            val vsAz = survey.verticalSectionAzimuth
            val points = ArrayList<TrajectoryPoint>(survey.stations.size + 1)
            points += TrajectoryPoint(
                md = tie.md, tvd = tie.tvd, north = tie.north, east = tie.east,
                inclination = tie.inclination, azimuth = tie.azimuth,
                verticalSection = Length(verticalSection(tie.north.meters, tie.east.meters, vsAz)),
            )
            for (station in survey.stations) {
                val prev = points.last()
                if (station.md <= prev.md) continue // stations at/above the tie-in are ignored
                val i1 = prev.inclination.radians
                val a1 = prev.azimuth.radians
                val i2 = station.inclination.radians
                val a2 = station.azimuth.radians
                val dogleg = MinimumCurvature.doglegAngle(i1, a1, i2, a2)
                val course = (station.md - prev.md).meters
                val d = MinimumCurvature.displacement(
                    course,
                    MinimumCurvature.direction(i1, a1),
                    MinimumCurvature.direction(i2, a2),
                    dogleg,
                )
                val north = prev.north.meters + d.north
                val east = prev.east.meters + d.east
                points += TrajectoryPoint(
                    md = station.md,
                    tvd = Length(prev.tvd.meters + d.down),
                    north = Length(north),
                    east = Length(east),
                    inclination = station.inclination,
                    azimuth = station.azimuth,
                    verticalSection = Length(verticalSection(north, east, vsAz)),
                    doglegSeverityDegPer30m = Math.toDegrees(dogleg) / course * THIRTY_METERS,
                )
            }
            return Trajectory(survey.mdReference, TrajectorySource.SURVEY, vsAz, points)
        }

        /**
         * Vertical path used only when no survey exists. Flagged as
         * [TrajectorySource.ASSUMED_VERTICAL] so the UI can warn the user.
         */
        fun assumedVertical(reference: DepthReference): Trajectory = Trajectory(
            reference = reference,
            source = TrajectorySource.ASSUMED_VERTICAL,
            verticalSectionAzimuth = Angle.ZERO,
            stations = listOf(
                TrajectoryPoint(
                    md = Length.ZERO, tvd = Length.ZERO, north = Length.ZERO, east = Length.ZERO,
                    inclination = Angle.ZERO, azimuth = Angle.ZERO, verticalSection = Length.ZERO,
                ),
            ),
        )

        fun verticalSection(north: Double, east: Double, vsAzimuth: Angle): Double =
            north * cos(vsAzimuth.radians) + east * sin(vsAzimuth.radians)

        private fun normalizeAzimuth(rad: Double): Double {
            val twoPi = 2.0 * PI
            val r = rad % twoPi
            return if (r < 0) r + twoPi else r
        }
    }
}
