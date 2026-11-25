package com.wellsketch.engineer.core.engineering

import com.wellsketch.engineer.core.engineering.trajectory.Trajectory
import com.wellsketch.engineer.core.engineering.trajectory.TrajectorySource
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.trajectory.AzimuthReference
import com.wellsketch.engineer.core.model.trajectory.DirectionalSurvey
import com.wellsketch.engineer.core.model.trajectory.SurveyKind
import com.wellsketch.engineer.core.model.trajectory.SurveyStation
import com.wellsketch.engineer.core.model.units.Angle
import com.wellsketch.engineer.core.model.units.Length
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Minimum curvature is exact for straight lines and circular arcs, so these tests compare
 * against closed-form geometry rather than against any external data set.
 */
class MinimumCurvatureTest {

    private fun survey(vararg stations: Triple<Double, Double, Double>, vsAz: Double = 0.0) = DirectionalSurvey(
        id = "s", name = "test", kind = SurveyKind.DEFINITIVE_ACTUAL,
        mdReference = DepthReference.Rkb, azimuthReference = AzimuthReference.TRUE_NORTH,
        verticalSectionAzimuth = Angle(vsAz),
        stations = stations.map { (md, inc, az) -> SurveyStation(Length(md), Angle(inc), Angle(az)) },
        provenance = DataProvenance.syntheticDemo(),
    )

    @Test
    fun verticalWellTvdEqualsMd() {
        val t = Trajectory.fromSurvey(survey(Triple(500.0, 0.0, 0.0), Triple(1500.0, 0.0, 0.0)))
        assertEquals(1500.0, t.tvdAt(Length(1500.0)).meters, 1e-9)
        assertEquals(800.0, t.tvdAt(Length(800.0)).meters, 1e-9)
        assertEquals(0.0, t.positionAt(Length(1500.0)).verticalSection.meters, 1e-9)
    }

    @Test
    fun straightInclinedHoldMatchesTrigonometry() {
        val t = Trajectory.fromSurvey(survey(Triple(0.001, 30.0, 45.0), Triple(100.001, 30.0, 45.0)))
        val p0 = t.positionAt(Length(0.001))
        val p1 = t.positionAt(Length(100.001))
        val inc = 30.0 * PI / 180
        val az = 45.0 * PI / 180
        assertEquals(100 * cos(inc), p1.tvd.meters - p0.tvd.meters, 1e-6)
        assertEquals(100 * sin(inc) * cos(az), p1.north.meters - p0.north.meters, 1e-6)
        assertEquals(100 * sin(inc) * sin(az), p1.east.meters - p0.east.meters, 1e-6)
    }

    @Test
    fun quarterCircleBuildEndsAtRadius() {
        // Build 0° → 90° northward over 300 m of arc: radius R = 300 / (π/2).
        val arc = 300.0
        val r = arc / (PI / 2)
        val t = Trajectory.fromSurvey(survey(Triple(1000.0, 0.0, 0.0), Triple(1000.0 + arc, 90.0, 0.0)))
        val end = t.positionAt(Length(1000.0 + arc))
        assertEquals(1000.0 + r, end.tvd.meters, 1e-6)
        assertEquals(r, end.north.meters, 1e-6)
        assertEquals(0.0, end.east.meters, 1e-9)
        assertEquals(r, end.verticalSection.meters, 1e-6)
        // 90° over 300 m = 9°/30 m
        assertEquals(9.0, t.stations.last().doglegSeverityDegPer30m, 1e-9)
    }

    @Test
    fun interpolationFollowsTheCircularArc() {
        val arc = 300.0
        val r = arc / (PI / 2)
        val t = Trajectory.fromSurvey(survey(Triple(1000.0, 0.0, 0.0), Triple(1000.0 + arc, 90.0, 0.0)))
        val mid = t.positionAt(Length(1000.0 + arc / 2))
        val theta = PI / 4
        assertEquals(1000.0 + r * sin(theta), mid.tvd.meters, 1e-6)
        assertEquals(r * (1 - cos(theta)), mid.north.meters, 1e-6)
        assertEquals(45.0, mid.inclination.degrees, 1e-9)
        // every interpolated point lies on the circle centred at (TVD = 1000, N = R)
        for (k in 1..9) {
            val p = t.positionAt(Length(1000.0 + arc * k / 10))
            val dz = p.tvd.meters - 1000.0
            val dn = p.north.meters - r
            assertEquals(r, sqrt(dz * dz + dn * dn), 1e-6)
        }
    }

    @Test
    fun horizontalTurnStaysAtConstantTvd() {
        val arc = 200.0
        val r = arc / (PI / 2)
        val t = Trajectory.fromSurvey(
            survey(Triple(0.001, 90.0, 0.0), Triple(arc + 0.001, 90.0, 90.0)),
        )
        val start = t.positionAt(Length(0.001))
        val end = t.positionAt(Length(arc + 0.001))
        assertEquals(start.tvd.meters, end.tvd.meters, 1e-6)
        assertEquals(r, end.north.meters - start.north.meters, 1e-6)
        assertEquals(r, end.east.meters - start.east.meters, 1e-6)
    }

    @Test
    fun beyondLastStationIsFlaggedAndProjectedStraight() {
        val t = Trajectory.fromSurvey(survey(Triple(1000.0, 0.0, 0.0), Triple(1100.0, 10.0, 0.0)))
        assertTrue(t.isExtrapolated(Length(1200.0)))
        val a = t.positionAt(Length(1100.0))
        val b = t.positionAt(Length(1200.0))
        assertEquals(100 * cos(10.0 * PI / 180), b.tvd.meters - a.tvd.meters, 1e-6)
    }

    @Test
    fun assumedVerticalWithoutSurvey() {
        val t = Trajectory.assumedVertical(DepthReference.Rkb)
        assertEquals(TrajectorySource.ASSUMED_VERTICAL, t.source)
        assertEquals(2500.0, t.tvdAt(Length(2500.0)).meters, 1e-9)
    }
}
