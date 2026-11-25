package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.volumes.TubularGeometry
import com.wellsketch.engineer.core.engineering.volumes.VolumeBreakdown
import com.wellsketch.engineer.core.engineering.volumes.VolumeSegment
import com.wellsketch.engineer.core.engineering.volumes.WellboreGeometry
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.VolumePerLength
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.TubularSpec
import com.wellsketch.engineer.core.model.well.Well

enum class TubularKind(val label: String) { CASING("Casing"), LINER("Liner"), TUBING("Tubing") }

/** Per-length geometry of one string, from its stated OD and ID. */
data class TubularRow(
    val sourceId: String,
    val name: String,
    val kind: TubularKind,
    val outerDiameter: Length,
    val innerDiameter: Length,
    /** Stated wall if entered, otherwise (OD − ID)/2. */
    val wallThickness: Length,
    val wallIsStated: Boolean,
    val driftDiameter: Length?,
    val topMd: Length,
    val bottomMd: Length,
    val insideCapacity: VolumePerLength,
    val openEndDisplacement: VolumePerLength,
    val closedEndDisplacement: VolumePerLength,
    val provenance: DataProvenance,
) {
    val length: Length get() = bottomMd - topMd
}

data class WellCalculationReport(
    val completionState: CompletionState?,
    val geometry: WellboreGeometry,
    val tubulars: List<TubularRow>,
    /** Whole accessible flow path from the conduit top. */
    val flowPath: VolumeBreakdown,
    /** Inside the production tubing (hanger to end of tubing); null when there is no tubing. */
    val tubingInside: VolumeBreakdown?,
    /** Inside casing/liner/open hole from the end of tubing to the accessible bottom. */
    val belowTubing: VolumeBreakdown?,
    val tubingAnnulus: VolumeBreakdown,
    val cementJobs: List<CementJobVolumes>,
    val issues: List<ValidationIssue>,
)

/**
 * Builds every Well Calculations result for one well and completion state. Pure and
 * deterministic: call again after each edit.
 */
class WellCalculations(
    private val volumes: WellVolumeCalculator = WellVolumeCalculator(),
    private val cement: CementVolumeCalculator = CementVolumeCalculator(volumes),
) {

    fun report(well: Well, completionState: CompletionState?, cementPolicy: CementExcessPolicy = CementExcessPolicy()): WellCalculationReport {
        val ctx = WellGeometryContext(well)
        val completion = completionState?.let(well::completion)
        val geometry = WellboreGeometry(ctx, completion)
        val issues = mutableListOf<ValidationIssue>()

        val tubulars = geometry.strings.map { span ->
            row(span.item.id, span.item.name, if (span.item.isLiner) TubularKind.LINER else TubularKind.CASING, span.item.spec, span.top, span.bottom, issues)
        } + listOfNotNull(geometry.tubing?.let { t -> row(t.item.id, t.item.name, TubularKind.TUBING, t.item.spec, t.top, t.bottom, issues) })

        val tubing = geometry.tubing
        val accessible = volumes.accessibleBottom(geometry)
        val tubingInside = tubing?.let { volumes.flowPathVolume(geometry, it.top, it.bottom).copy(title = "Inside tubing") }
        val belowTubing = if (tubing != null && accessible != null && accessible > tubing.bottom) {
            volumes.flowPathVolume(geometry, tubing.bottom, accessible).copy(title = "Below end of tubing")
        } else {
            null
        }
        if (ctx.trajectorySource != com.wellsketch.engineer.core.engineering.trajectory.TrajectorySource.SURVEY) {
            issues += ValidationIssue(Severity.INFO, "CALC_NO_SURVEY", "No survey: TVD-based results assume a vertical well.")
        }
        val glMd = ctx.groundLevelCanonicalMd
        geometry.strings.filter { it.top < glMd - Length.meters(0.01) }.forEach {
            issues += ValidationIssue(
                Severity.WARNING, "CALC_STRING_ABOVE_GL",
                "${it.item.name} top is above ground level; volumes include that part. Check the hanger depth.", listOf(it.item.id),
            )
        }
        val cementJobs = geometry.strings.filter { it.item.cement != null }.mapNotNull { cement.calculate(geometry, it.item.id, cementPolicy) }

        return WellCalculationReport(
            completionState = completion?.state,
            geometry = geometry,
            tubulars = tubulars,
            flowPath = volumes.flowPathVolume(geometry),
            tubingInside = tubingInside,
            belowTubing = belowTubing,
            tubingAnnulus = volumes.tubingAnnulusVolume(geometry),
            cementJobs = cementJobs,
            issues = issues,
        )
    }

    private fun row(id: String, name: String, kind: TubularKind, spec: TubularSpec, top: Length, bottom: Length, issues: MutableList<ValidationIssue>): TubularRow {
        val derivedWall = TubularGeometry.wallThickness(spec.outerDiameter, spec.innerDiameter)
        val stated = spec.wallThickness
        if (stated != null && !stated.isApproximately(derivedWall, WALL_TOLERANCE)) {
            issues += ValidationIssue(
                Severity.WARNING, "CALC_WALL_MISMATCH",
                "$name: stated wall ${fmtIn(stated)} differs from (OD − ID)/2 = ${fmtIn(derivedWall)}; check the ID.", listOf(id),
            )
        }
        return TubularRow(
            sourceId = id, name = name, kind = kind,
            outerDiameter = spec.outerDiameter, innerDiameter = spec.innerDiameter,
            wallThickness = stated ?: derivedWall, wallIsStated = stated != null,
            driftDiameter = spec.driftDiameter, topMd = top, bottomMd = bottom,
            insideCapacity = TubularGeometry.insideCapacity(spec.innerDiameter),
            openEndDisplacement = TubularGeometry.openEndDisplacement(spec.outerDiameter, spec.innerDiameter),
            closedEndDisplacement = TubularGeometry.closedEndDisplacement(spec.outerDiameter),
            provenance = spec.provenance,
        )
    }

    private companion object {
        /** 0.001 in: the precision of stated tubular dimensions. */
        val WALL_TOLERANCE: Length = Length.inches(0.001)
        fun fmtIn(l: Length) = String.format(java.util.Locale.US, "%.3f in", l.inches)
    }
}
