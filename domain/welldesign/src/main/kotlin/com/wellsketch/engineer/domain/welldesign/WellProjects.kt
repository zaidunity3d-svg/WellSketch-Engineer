package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.datum.WellDatums
import com.wellsketch.engineer.core.model.geology.FormationTop
import com.wellsketch.engineer.core.model.geology.Lithology
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.trajectory.TrajectoryType
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.model.well.WellLocationType
import java.util.UUID

/**
 * Creating and copying user wells (the HTML prototype's "+ New well" and "Duplicate").
 * A new well starts empty apart from its datums and one formation at ground level, so no demo
 * value can leak into a real well; holes, strings and completion are added by the user.
 */
object WellProjects {

    /**
     * @param rkbAboveGround RKB (drill floor reference) height above ground level.
     * @param groundElevation ground level relative to MSL (negative below sea level).
     */
    fun newWell(
        name: String,
        rkbAboveGround: Length,
        groundElevation: Length,
        locationType: WellLocationType = WellLocationType.ONSHORE,
        nowMillis: Long,
        id: String = UUID.randomUUID().toString().take(12),
    ): WellProject {
        require(name.isNotBlank()) { "Enter a well name" }
        require(rkbAboveGround >= Length.ZERO) { "RKB must not be below ground level" }
        val user = DataProvenance.userEntered()
        val well = Well(
            id = "well-$id",
            name = name.trim(),
            locationType = locationType,
            datums = WellDatums(
                rkbElevation = groundElevation + rkbAboveGround,
                groundLevelElevation = groundElevation,
                provenance = user,
            ),
            declaredTrajectoryType = TrajectoryType.VERTICAL,
            formations = listOf(
                FormationTop(
                    id = "fm-$id-1", name = "Formation 1", top = TrueVerticalDepth.rkb(rkbAboveGround),
                    lithology = Lithology.UNKNOWN, provenance = user,
                ),
            ),
            provenance = user,
        )
        return WellProject(id = "project-$id", name = name.trim(), lastModifiedEpochMillis = nowMillis, well = well, provenance = user)
    }

    /**
     * Independent copy of a project (new project and well ids, " (copy)" name). A copy of a
     * demo becomes a user project; the data inside keeps its own provenance badges.
     */
    fun duplicate(project: WellProject, nowMillis: Long, id: String = UUID.randomUUID().toString().take(12)): WellProject {
        val name = "${project.name} (copy)"
        return project.copy(
            id = "project-$id",
            name = name,
            lastModifiedEpochMillis = nowMillis,
            well = project.well.copy(id = "well-$id", name = name),
            provenance = DataProvenance.userEntered("Copied from ${project.name}"),
        )
    }
}
