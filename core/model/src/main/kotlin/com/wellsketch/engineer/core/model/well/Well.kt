package com.wellsketch.engineer.core.model.well

import com.wellsketch.engineer.core.model.datum.WellDatums
import com.wellsketch.engineer.core.model.geology.FormationTop
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.surface.Cellar
import com.wellsketch.engineer.core.model.surface.ChristmasTree
import com.wellsketch.engineer.core.model.surface.DrillingRigUp
import com.wellsketch.engineer.core.model.surface.Wellhead
import com.wellsketch.engineer.core.model.trajectory.DirectionalSurvey
import com.wellsketch.engineer.core.model.trajectory.SurveyKind
import com.wellsketch.engineer.core.model.trajectory.TrajectoryType
import kotlinx.serialization.Serializable

@Serializable
enum class WellLocationType(val label: String) {
    ONSHORE("Onshore"),
    OFFSHORE("Offshore"),
}

/**
 * A well: datums, geology, drilled geometry, casing/cement, surveys and completion states.
 * All depth values inside carry their own [com.wellsketch.engineer.core.model.datum.DepthReference].
 */
@Serializable
data class Well(
    val id: String,
    val name: String,
    val locationType: WellLocationType,
    val datums: WellDatums,
    val declaredTrajectoryType: TrajectoryType,
    val formations: List<FormationTop> = emptyList(),
    val holeSections: List<HoleSection> = emptyList(),
    val casingStrings: List<CasingString> = emptyList(),
    val surveys: List<DirectionalSurvey> = emptyList(),
    /** Survey used for trajectory geometry; null selects the best available survey. */
    val activeSurveyId: String? = null,
    val completions: List<Completion> = emptyList(),
    /** Christmas Tree currently installed, if the well is completed. */
    val christmasTree: ChristmasTree? = null,
    val provenance: DataProvenance,
    /** Spool-type wellhead (casing head housing, casing spools, tubing head), if defined. */
    val wellhead: Wellhead? = null,
    /** Concrete cellar pit around the wellhead, if the location has one. */
    val cellar: Cellar? = null,
    /** Rig and drilling BOP stack used to show the drilling operating state, if defined. */
    val drillingRigUp: DrillingRigUp? = null,
) {
    /**
     * The survey that drives trajectory geometry: the explicitly selected one, otherwise
     * the first definitive (actual) survey, otherwise the first planned survey.
     */
    val activeSurvey: DirectionalSurvey?
        get() = surveys.firstOrNull { it.id == activeSurveyId }
            ?: surveys.firstOrNull { it.kind == SurveyKind.DEFINITIVE_ACTUAL }
            ?: surveys.firstOrNull()

    fun completion(state: CompletionState): Completion? = completions.firstOrNull { it.state == state }
}
