package com.wellsketch.engineer.core.engineering

import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.datum.WellDatums
import com.wellsketch.engineer.core.model.geology.FormationTop
import com.wellsketch.engineer.core.model.geology.Lithology
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.trajectory.TrajectoryType
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CasingShoe
import com.wellsketch.engineer.core.model.well.CasingString
import com.wellsketch.engineer.core.model.well.CementSheath
import com.wellsketch.engineer.core.model.well.CementStatus
import com.wellsketch.engineer.core.model.well.HoleSection
import com.wellsketch.engineer.core.model.well.TubularSpec
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.model.well.WellLocationType

/** Minimal synthetic vertical well for unit tests. All values are synthetic. */
object TestWells {
    val synthetic = DataProvenance.syntheticDemo()

    fun m(v: Double) = Length.meters(v)
    fun rkb(v: Double) = MeasuredDepth.rkb(Length.meters(v))

    fun spec(odIn: Double, idIn: Double) = TubularSpec(
        outerDiameter = Length.inches(odIn), innerDiameter = Length.inches(idIn), provenance = synthetic,
    )

    fun casing(
        id: String, role: CasingRole, odIn: Double, idIn: Double, top: Double, shoe: Double, toc: Double?,
    ) = CasingString(
        id = id, name = id, role = role, spec = spec(odIn, idIn), top = rkb(top),
        shoe = CasingShoe(rkb(shoe)),
        cement = toc?.let { CementSheath(rkb(it), rkb(shoe), CementStatus.DESIGNED, provenance = synthetic) },
        provenance = synthetic,
    )

    fun verticalWell(
        casings: List<CasingString> = listOf(
            casing("surface", CasingRole.SURFACE, 13.375, 12.4, 10.0, 500.0, 10.0),
            casing("production", CasingRole.PRODUCTION, 7.0, 6.2, 10.0, 2000.0, 1200.0),
        ),
    ) = Well(
        id = "w", name = "Test well", locationType = WellLocationType.ONSHORE,
        datums = WellDatums(Length.meters(310.0), Length.meters(300.0), provenance = synthetic),
        declaredTrajectoryType = TrajectoryType.VERTICAL,
        formations = listOf(
            FormationTop("f1", "Upper", TrueVerticalDepth.gl(Length.ZERO), Lithology.SANDSTONE, provenance = synthetic),
            FormationTop("f2", "Lower", TrueVerticalDepth.gl(m(800.0)), Lithology.SHALE, provenance = synthetic),
        ),
        holeSections = listOf(
            HoleSection("h1", "17-1/2 in", Length.inches(17.5), rkb(10.0), rkb(500.0), synthetic),
            HoleSection("h2", "8-1/2 in", Length.inches(8.5), rkb(500.0), rkb(2000.0), synthetic),
        ),
        casingStrings = casings,
        provenance = synthetic,
    )
}
