package com.wellsketch.engineer.core.data.demo

import com.wellsketch.engineer.core.model.trajectory.SurveyStation
import com.wellsketch.engineer.core.model.units.Angle
import com.wellsketch.engineer.core.model.units.Length
import kotlin.math.abs
import kotlin.math.min

/**
 * Generates SYNTHETIC survey stations from a simple geometric profile description, used
 * only to build demonstration wells. Real wells must use their actual survey data.
 *
 * Curves change inclination linearly with MD at constant azimuth, which is a circular arc
 * in the vertical plane (consistent with the minimum curvature model).
 */
object SyntheticSurveyGenerator {

    sealed interface Segment {
        /** Straight section at the current inclination down to [toMdMeters]. */
        data class Straight(val toMdMeters: Double) : Segment

        /** Constant-rate inclination change (build if increasing, drop if decreasing). */
        data class Curve(val rateDegPer30m: Double, val toInclinationDeg: Double) : Segment
    }

    fun generate(
        azimuthDeg: Double,
        segments: List<Segment>,
        stationSpacingMeters: Double = 30.0,
    ): List<SurveyStation> {
        val stations = mutableListOf<SurveyStation>()
        var md = 0.0
        var inc = 0.0
        fun emit(m: Double, i: Double) {
            stations += SurveyStation(Length.meters(m), Angle.degrees(i), Angle.degrees(azimuthDeg))
        }
        for (segment in segments) {
            when (segment) {
                is Segment.Straight -> {
                    while (md < segment.toMdMeters - 1e-9) {
                        md = min(md + stationSpacingMeters, segment.toMdMeters)
                        emit(md, inc)
                    }
                }
                is Segment.Curve -> {
                    val startInc = inc
                    val startMd = md
                    val length = abs(segment.toInclinationDeg - startInc) / segment.rateDegPer30m * 30.0
                    val endMd = startMd + length
                    while (md < endMd - 1e-9) {
                        md = min(md + stationSpacingMeters, endMd)
                        val f = (md - startMd) / length
                        inc = startInc + (segment.toInclinationDeg - startInc) * f
                        emit(md, inc)
                    }
                    inc = segment.toInclinationDeg
                }
            }
        }
        return stations
    }
}
