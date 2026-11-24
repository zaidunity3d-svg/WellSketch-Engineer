package com.wellsketch.engineer.core.model.trajectory

import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Angle
import com.wellsketch.engineer.core.model.units.Length
import kotlinx.serialization.Serializable

/** Declared design profile. The drawn shape always comes from survey stations. */
@Serializable
enum class TrajectoryType(val label: String) {
    VERTICAL("Vertical"),
    J_SHAPED("J-shaped (build and hold)"),
    S_SHAPED("S-shaped (build, hold, drop)"),
    HORIZONTAL("Horizontal"),
}

@Serializable
enum class SurveyKind(val label: String) {
    PLANNED("Planned"),
    DEFINITIVE_ACTUAL("Definitive (actual)"),
}

@Serializable
enum class AzimuthReference(val label: String) {
    TRUE_NORTH("True north"),
    GRID_NORTH("Grid north"),
    MAGNETIC_NORTH("Magnetic north"),
}

/** A survey station: MD (relative to the survey's MD reference), inclination, azimuth. */
@Serializable
data class SurveyStation(
    val md: Length,
    val inclination: Angle,
    val azimuth: Angle,
) {
    init {
        require(inclination.degrees in 0.0..180.0) { "Inclination must be within 0..180 degrees." }
    }
}

/** Tie-in point of a survey (default: the MD reference at surface, on the well axis). */
@Serializable
data class SurveyTieIn(
    val md: Length = Length.ZERO,
    val tvd: Length = Length.ZERO,
    val north: Length = Length.ZERO,
    val east: Length = Length.ZERO,
    val inclination: Angle = Angle.ZERO,
    val azimuth: Angle = Angle.ZERO,
)

@Serializable
data class DirectionalSurvey(
    val id: String,
    val name: String,
    val kind: SurveyKind,
    /** Datum for both MD and TVD of this survey (normally RKB). */
    val mdReference: DepthReference,
    val azimuthReference: AzimuthReference,
    /** Azimuth of the vertical-section plane used for 2D projection of the path. */
    val verticalSectionAzimuth: Angle,
    val tieIn: SurveyTieIn = SurveyTieIn(),
    val stations: List<SurveyStation>,
    val provenance: DataProvenance,
) {
    init {
        require(mdReference !is DepthReference.MeanSeaLevel) { "Survey MD cannot be referenced to MSL." }
        require(stations.zipWithNext().all { (a, b) -> b.md > a.md }) {
            "Survey stations must be strictly increasing in MD."
        }
    }
}
