package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.volumes.WellboreGeometry
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.WellDatums
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.trajectory.TrajectoryType
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CasingShoe
import com.wellsketch.engineer.core.model.well.CasingString
import com.wellsketch.engineer.core.model.well.CementSheath
import com.wellsketch.engineer.core.model.well.CementStatus
import com.wellsketch.engineer.core.model.well.ComponentMounting
import com.wellsketch.engineer.core.model.well.Completion
import com.wellsketch.engineer.core.model.well.CompletionComponent
import com.wellsketch.engineer.core.model.well.CompletionComponentType
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.HoleSection
import com.wellsketch.engineer.core.model.well.TubingConfiguration
import com.wellsketch.engineer.core.model.well.TubingString
import com.wellsketch.engineer.core.model.well.TubularSpec
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.model.well.WellLocationType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-check against a second, independent implementation: the owner's v0.9 browser
 * prototype (Drive: 05_Legacy_Interactive_Prototype). Its own `volume()` and
 * `cementVolume()` functions were run on its FICTIONAL template "Example A · Vertical";
 * the same geometry is rebuilt here and the results must agree.
 *
 * Template data is fictional (labelled "FICTIONAL · Demo" in v0.9): not real well data.
 */
class LegacyCrossCheckTest {
    private val p = DataProvenance.syntheticDemo("Rebuilt from the v0.9 FICTIONAL template A for a cross-check.")
    private fun rkb(v: Double) = MeasuredDepth.rkb(Length.meters(v))
    private fun spec(od: Double, id: Double) = TubularSpec(Length.inches(od), Length.inches(id), provenance = p)
    private fun casing(id: String, role: CasingRole, od: Double, idIn: Double, shoe: Double, toc: Double?, floatCollar: Double? = null) =
        CasingString(
            id, id, role, spec(od, idIn), rkb(8.5), CasingShoe(rkb(shoe), floatCollarDepth = floatCollar?.let(::rkb)),
            toc?.let { CementSheath(rkb(it), rkb(shoe), CementStatus.DESIGNED, provenance = p) }, p,
        )

    // v0.9 template A: datum (RKB above GL) 8.5 m; IDs as resolved by v0.9 from its catalogue.
    private val well = Well(
        id = "legacy-a", name = "v0.9 template A (fictional)", locationType = WellLocationType.ONSHORE,
        datums = WellDatums(Length.meters(108.5), Length.meters(100.0), provenance = p),
        declaredTrajectoryType = TrajectoryType.VERTICAL,
        holeSections = listOf(
            HoleSection("ha1", "26 in", Length.inches(26.0), rkb(8.5), rkb(40.0), p),
            HoleSection("ha2", "17-1/2 in", Length.inches(17.5), rkb(40.0), rkb(600.0), p),
            HoleSection("ha3", "12-1/4 in", Length.inches(12.25), rkb(600.0), rkb(1950.0), p),
            HoleSection("ha4", "8-1/2 in", Length.inches(8.5), rkb(1950.0), rkb(2800.0), p),
        ),
        casingStrings = listOf(
            casing("sa2", CasingRole.SURFACE, 13.375, 12.615, 598.0, 8.5),
            casing("sa3", CasingRole.INTERMEDIATE, 9.625, 8.6811, 1945.0, 800.0),
            casing("sa4", CasingRole.PRODUCTION, 7.0, 6.1843, 2795.0, 1950.0, floatCollar = 2770.0),
        ),
        completions = listOf(
            Completion(
                "legacy-a-cmp", CompletionState.CURRENT, "current",
                TubingConfiguration.Installed(TubingString("sa5", "Production tubing", spec(3.5, 2.9921), rkb(8.5), rkb(2600.0), p)),
                components = listOf(
                    CompletionComponent("ea1", CompletionComponentType.PACKER, "Packer", rkb(2510.0), Length.ZERO, ComponentMounting.ON_TUBING, provenance = p),
                ),
                provenance = p,
            ),
        ),
        provenance = p,
    )
    private val geometry = WellboreGeometry(WellGeometryContext(well), well.completion(CompletionState.CURRENT))
    private val volumes = WellVolumeCalculator()
    private fun m(v: Double) = Length.meters(v)

    @Test
    fun internalAndAnnularVolumesAgreeWithV09() {
        assertEquals(11.756031364044928, volumes.flowPathVolume(geometry, m(8.5), m(2600.0)).total.cubicMeters, 1e-9)
        assertEquals(32.950128131594006, volumes.tubingAnnulusVolume(geometry).total.cubicMeters, 1e-9)
        assertEquals(3.7789663436467085, volumes.flowPathVolume(geometry, m(2600.0), m(2795.0)).total.cubicMeters, 1e-9)
    }

    @Test
    fun cementVolumesAgreeWithV09() {
        val calc = CementVolumeCalculator()
        val prod = calc.calculate(geometry, "sa4", CementExcessPolicy(openHoleExcessFraction = 0.30))!!
        assertEquals(12.94137235458498, prod.annulus.total.cubicMeters, 1e-9)
        assertEquals(0.4844828645700908, prod.shoeTrack!!.volume.cubicMeters, 1e-12)
        assertEquals(13.42585521915507, prod.slurryVolume.cubicMeters, 1e-9)
        assertEquals(53.51597722041223, prod.displacement.total.cubicMeters, 1e-9)
        val intermediate = calc.calculate(geometry, "sa3")!!
        assertEquals(33.31502708761536, intermediate.annulus.total.cubicMeters, 1e-9)
    }
}
