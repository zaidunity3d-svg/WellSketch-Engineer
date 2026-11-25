package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.units.Density
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Expected values computed independently (Python): exact units and analytic S-profile TVD. */
class HydrostaticCalculatorTest {
    private fun m(v: Double) = Length.meters(v)
    private fun kgm3(v: Double) = Density.kilogramsPerCubicMeter(v)

    @Test
    fun verticalWellSingleFluid() {
        val ctx = WellGeometryContext(SyntheticDemoProjects.vertical().well)
        val r = HydrostaticCalculator.pressureAt(ctx, listOf(FluidColumn("Brine", kgm3(1100.0), m(11.1), m(2400.0))), m(2000.0))
        assertTrue(r.issues.isEmpty(), r.issues.toString())
        // 1100 × 9.80665 × (2000 − 11.1) Pa (GL is 11.1 m below RKB); values from verify/expected_vertical.py
        assertEquals(21454890.8035, r.totalPressure.pascals, 1e-4)
        assertEquals(3111.7688253883102, r.totalPressure.psi, 1e-9)
        assertEquals(1988.9, r.targetTvdBelowGl.meters, 1e-9)
        // equivalent density referenced to RKB (2000 m TVD below RKB)
        val emw = HydrostaticCalculator.equivalentDensityAt(ctx, r.totalPressure, m(2000.0))!!
        assertEquals(1.093895, emw.gramsPerCubicCentimeter, 1e-12)
    }

    @Test
    fun deviatedWellUsesTvdNotMd() {
        val ctx = WellGeometryContext(SyntheticDemoProjects.sShaped().well)
        val r = HydrostaticCalculator.pressureAt(ctx, listOf(FluidColumn("Brine", kgm3(1030.0), m(11.1), m(2890.0))), m(2700.0))
        // analytic TVD(2700 m MD) = 2603.8308 m below RKB; ΔTVD from GL (11.1 m) = 2592.7308 m
        assertEquals(2592.730783687545, r.contributions.single().verticalHeight.meters, 1e-6)
        assertEquals(3798.361904050486, r.totalPressure.psi, 1e-5)
        // a calculation using MD as the height would be about 141 psi too high
        val mdBased = HydrostaticCalculator.pressure(kgm3(1030.0), m(2700.0 - 11.1)).psi
        assertTrue(mdBased - r.totalPressure.psi > 140.0)
    }

    @Test
    fun stackedColumnsWithSurfacePressure() {
        val ctx = WellGeometryContext(SyntheticDemoProjects.sShaped().well)
        val r = HydrostaticCalculator.pressureAt(
            ctx,
            listOf(
                FluidColumn("Brine", kgm3(1030.0), m(1500.0), m(2890.0)), // order does not matter
                FluidColumn("Diesel", kgm3(830.0), m(11.1), m(1500.0)),
            ),
            m(2700.0),
            surfacePressure = Pressure.psi(500.0),
        )
        assertTrue(r.issues.isEmpty(), r.issues.toString())
        assertEquals(3890.827615006614, r.totalPressure.psi, 1e-5)
        assertEquals(500.0, r.surfacePressure.psi, 1e-12)
        assertEquals(2, r.contributions.size)
    }

    @Test
    fun pressureDoesNotChangeAlongAHorizontalLateral() {
        val ctx = WellGeometryContext(SyntheticDemoProjects.horizontal().well)
        val column = listOf(FluidColumn("Brine", kgm3(1050.0), m(11.1), m(3000.0)))
        val heel = HydrostaticCalculator.pressureAt(ctx, column, m(2500.0)).totalPressure.pascals
        val toe = HydrostaticCalculator.pressureAt(ctx, column, m(2900.0)).totalPressure.pascals
        assertEquals(heel, toe, 1e-3)
    }

    @Test
    fun invalidColumnsAndTargetsAreReported() {
        val ctx = WellGeometryContext(SyntheticDemoProjects.vertical().well)
        val gap = HydrostaticCalculator.pressureAt(
            ctx, listOf(FluidColumn("A", kgm3(1000.0), m(11.1), m(1000.0)), FluidColumn("B", kgm3(1200.0), m(1100.0), m(2000.0))), m(1500.0),
        )
        assertTrue(gap.issues.any { it.code == "HYDRO_COLUMNS_NOT_CONTIGUOUS" })
        val outside = HydrostaticCalculator.pressureAt(ctx, listOf(FluidColumn("A", kgm3(1000.0), m(11.1), m(1000.0))), m(1500.0))
        assertTrue(outside.issues.any { it.code == "HYDRO_TARGET_OUTSIDE" })
        val none = HydrostaticCalculator.pressureAt(ctx, emptyList(), m(1500.0))
        assertTrue(none.issues.any { it.code == "HYDRO_NO_FLUID" })
    }

    @Test
    fun gradientAndEquivalentDensityAreInverse() {
        val d = Density.poundsPerGallon(10.0)
        val p = HydrostaticCalculator.pressure(d, Length.feet(10_000.0))
        // 10 ppg × 12/231 psi/ft per ppg × 10 000 ft = 5194.805 psi
        assertEquals(10.0 * 12.0 / 231.0 * 10_000.0, p.psi, 1e-8)
        assertEquals(10.0, HydrostaticCalculator.equivalentDensity(p, Length.feet(10_000.0)).poundsPerGallon, 1e-12)
    }
}
