package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.units.Density
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.model.units.PressureGradient
import com.wellsketch.engineer.core.model.units.UnitDefinitions

/** A fluid of constant density filling the conduit between two measured depths (canonical MD). */
data class FluidColumn(
    val name: String,
    val density: Density,
    val topMd: Length,
    val bottomMd: Length,
)

data class ColumnContribution(
    val column: FluidColumn,
    /** Vertical height of the part of the column above the target: ΔTVD from the trajectory. */
    val verticalHeight: Length,
    val pressure: Pressure,
)

data class HydrostaticResult(
    val targetMd: Length,
    val targetTvdBelowGl: Length,
    val surfacePressure: Pressure,
    val contributions: List<ColumnContribution>,
    val issues: List<ValidationIssue>,
) {
    val hydrostaticPressure: Pressure get() = Pressure(contributions.sumOf { it.pressure.pascals })
    val totalPressure: Pressure get() = surfacePressure + hydrostaticPressure
}

/**
 * Static hydrostatic pressure (calculation version [VERSION]).
 *
 * P = P_surface + Σ ρᵢ · g · ΔTVDᵢ, with g = 9.80665 m/s² (standard gravity, exact) and
 * ΔTVD taken from the well trajectory (minimum curvature), so deviated and horizontal
 * wells are handled correctly: MD is never used as a vertical height.
 *
 * Assumptions: static, incompressible fluids of constant density, no temperature or
 * pressure effect on density, no friction (not ECD), no gas.
 */
object HydrostaticCalculator {
    const val VERSION = "1.0.0"

    /** Hydrostatic gradient of a fluid: ρ · g. */
    fun gradient(density: Density): PressureGradient =
        PressureGradient(density.kilogramsPerCubicMeter * UnitDefinitions.STANDARD_GRAVITY_M_PER_S2)

    /** Pressure of a fluid column of vertical height [verticalHeight]. */
    fun pressure(density: Density, verticalHeight: Length): Pressure = gradient(density) * verticalHeight

    /** Density whose column of height [verticalDepth] produces [pressure]: P / (g · TVD). */
    fun equivalentDensity(pressure: Pressure, verticalDepth: Length): Density {
        require(verticalDepth.meters > 0.0) { "Vertical depth must be positive." }
        return Density(pressure.pascals / (UnitDefinitions.STANDARD_GRAVITY_M_PER_S2 * verticalDepth.meters))
    }

    /**
     * Pressure at [targetMd] under the fluid [columns] (any order; they must be contiguous)
     * plus [surfacePressure] applied at the top of the uppermost column.
     */
    fun pressureAt(
        ctx: WellGeometryContext,
        columns: List<FluidColumn>,
        targetMd: Length,
        surfacePressure: Pressure = Pressure.ZERO,
    ): HydrostaticResult {
        val issues = mutableListOf<ValidationIssue>()
        val sorted = columns.sortedBy { it.topMd }
        for (c in sorted) {
            if (c.bottomMd <= c.topMd) issues += ValidationIssue(Severity.ERROR, "HYDRO_COLUMN_INVERTED", "Fluid column '${c.name}' has its bottom above its top.")
            if (c.density.kilogramsPerCubicMeter <= 0.0) issues += ValidationIssue(Severity.ERROR, "HYDRO_DENSITY", "Fluid '${c.name}' density must be positive.")
        }
        for ((a, b) in sorted.zipWithNext()) {
            if (!a.bottomMd.isApproximately(b.topMd, Length.meters(TOLERANCE_M))) {
                issues += ValidationIssue(
                    Severity.ERROR, "HYDRO_COLUMNS_NOT_CONTIGUOUS",
                    "Fluid columns '${a.name}' and '${b.name}' leave a gap or overlap between ${fmt(a.bottomMd)} and ${fmt(b.topMd)} m MD.",
                )
            }
        }
        val first = sorted.firstOrNull()
        val last = sorted.lastOrNull()
        if (first == null || last == null) {
            issues += ValidationIssue(Severity.ERROR, "HYDRO_NO_FLUID", "No fluid column entered.")
        } else if (targetMd < first.topMd || targetMd > last.bottomMd + Length.meters(TOLERANCE_M)) {
            issues += ValidationIssue(
                Severity.ERROR, "HYDRO_TARGET_OUTSIDE",
                "Target depth ${fmt(targetMd)} m MD is outside the fluid columns (${fmt(first.topMd)}–${fmt(last.bottomMd)} m MD).",
            )
        }
        if (surfacePressure < Pressure.ZERO) issues += ValidationIssue(Severity.ERROR, "HYDRO_SURFACE_NEGATIVE", "Surface pressure must not be negative.")
        if (ctx.trajectory.source != com.wellsketch.engineer.core.engineering.trajectory.TrajectorySource.SURVEY) {
            issues += ValidationIssue(Severity.WARNING, "HYDRO_TVD_ASSUMED", "No survey: TVD assumes a vertical well.")
        }

        val contributions = sorted.mapNotNull { c ->
            val bottom = minOf(c.bottomMd, targetMd)
            if (bottom <= c.topMd) return@mapNotNull null
            val height = ctx.trajectory.tvdAt(bottom) - ctx.trajectory.tvdAt(c.topMd)
            ColumnContribution(c, height, pressure(c.density, height))
        }
        return HydrostaticResult(targetMd, ctx.tvdBelowGroundLevel(targetMd), surfacePressure, contributions, issues)
    }

    /**
     * Equivalent density at [targetMd] for [pressure], referenced to the vertical depth
     * below [datum] (RKB by convention). Returns null when the target is not below the datum.
     */
    fun equivalentDensityAt(ctx: WellGeometryContext, pressure: Pressure, targetMd: Length, datum: DepthReference = DepthReference.Rkb): Density? {
        val tvd = ctx.tvd(MeasuredDepth(targetMd, ctx.canonicalReference), datum).value
        return if (tvd.meters > 0.0) equivalentDensity(pressure, tvd) else null
    }

    private const val TOLERANCE_M = 1e-3
    private fun fmt(l: Length) = String.format(java.util.Locale.US, "%.1f", l.meters)
}
