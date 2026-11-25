package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.volumes.Bore
import com.wellsketch.engineer.core.engineering.volumes.DiameterBasis
import com.wellsketch.engineer.core.engineering.volumes.TubularGeometry
import com.wellsketch.engineer.core.engineering.volumes.VolumeBreakdown
import com.wellsketch.engineer.core.engineering.volumes.VolumeSegment
import com.wellsketch.engineer.core.engineering.volumes.WellboreGeometry
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingString

/**
 * Piecewise well volumes along measured depth (calculation version [VERSION]).
 *
 * Every interval is split wherever the geometry changes (string tops and shoes, hole
 * sections, tubing hanger and end, completion components), and each piece uses the exact
 * circular capacity of its stated diameters. Volumes are along MD: they do not depend on
 * TVD or inclination.
 *
 * Not included: couplings and upsets, completion component bores, washouts/enlargement
 * beyond the bit size, the wellhead and tree bore, surface lines.
 */
class WellVolumeCalculator {

    /** Top of the fluid conduit: tubing hanger, else the shallowest string or hole top. */
    fun flowPathTop(geometry: WellboreGeometry): Length? =
        geometry.tubing?.top
            ?: geometry.strings.minOfOrNull { it.top }
            ?: geometry.holes.minOfOrNull { it.top }

    /**
     * Deepest depth reachable from surface inside the bore: the top of the shallowest
     * full-bore plug if there is one, otherwise the deepest recorded depth.
     */
    fun accessibleBottom(geometry: WellboreGeometry): Length? {
        val deepest = geometry.deepestMd ?: return null
        return geometry.blockingTop?.top?.let { minOf(it, deepest) } ?: deepest
    }

    /**
     * Volume inside the fluid conduit between [top] and [bottom] (canonical MD): production
     * tubing where installed, else the innermost casing/liner, else open hole. Defaults to
     * the whole accessible flow path. An interval reaching below a full-bore plug is cut at
     * the plug and reported.
     */
    fun flowPathVolume(geometry: WellboreGeometry, top: Length? = null, bottom: Length? = null): VolumeBreakdown {
        val title = "Inside volume (flow path)"
        val issues = mutableListOf<ValidationIssue>()
        val start = top ?: flowPathTop(geometry) ?: return VolumeBreakdown(title, emptyList(), noGeometry())
        var end = bottom ?: accessibleBottom(geometry) ?: return VolumeBreakdown(title, emptyList(), noGeometry())
        geometry.blockingTop?.let { plug ->
            if (end > plug.top && start < plug.top) {
                issues += ValidationIssue(
                    Severity.WARNING, "VOLUME_STOPPED_AT_PLUG",
                    "Interval cut at the top of '${plug.item.name}', which blocks the bore.", listOf(plug.item.id),
                )
                end = plug.top
            }
        }
        if (end <= start) {
            issues += ValidationIssue(Severity.ERROR, "VOLUME_INTERVAL_EMPTY", "The bottom of the interval must be below its top.")
            return VolumeBreakdown(title, emptyList(), issues)
        }
        val segments = mutableListOf<VolumeSegment>()
        for ((a, b) in geometry.breakpoints(start, end).zipWithNext()) {
            val bore = geometry.boreAt(mid(a, b))
            if (bore == null) {
                issues += gap(a, b)
                continue
            }
            segments.append(
                VolumeSegment(a, b, describe(bore), bore.diameter, null, bore.basis, TubularGeometry.insideCapacity(bore.diameter), sourceId = bore.sourceId),
            )
        }
        if (segments.any { it.outerBasis == DiameterBasis.BIT_SIZE }) issues += bitSizeNote()
        return VolumeBreakdown(title, segments, issues)
    }

    /**
     * Annulus between the production tubing and the casing/liner around it, from the
     * tubing hanger down to the top of the shallowest packer (or to the end of tubing when
     * there is no packer).
     */
    fun tubingAnnulusVolume(geometry: WellboreGeometry): VolumeBreakdown {
        val title = "Tubing × casing annulus"
        val tubing = geometry.tubing
            ?: return VolumeBreakdown(title, emptyList(), listOf(ValidationIssue(Severity.INFO, "ANNULUS_NO_TUBING", "No production tubing installed.")))
        val issues = mutableListOf<ValidationIssue>()
        val packer = geometry.packerTop?.takeIf { it > tubing.top && it <= tubing.bottom }
        val bottom = packer ?: tubing.bottom
        if (packer == null) {
            issues += ValidationIssue(Severity.INFO, "ANNULUS_NO_PACKER", "No packer on the tubing: annulus taken to the end of tubing.")
        }
        val od = tubing.item.spec.outerDiameter
        return VolumeBreakdown(title, annulusSegments(geometry, tubing.top, bottom, od, tubing.item.name, null, issues), issues)
    }

    /**
     * Annulus outside [string] between [top] and [bottom]: from the string OD to the
     * innermost larger casing ID, or to the open hole where no larger string covers it.
     */
    fun annulusOutside(geometry: WellboreGeometry, string: CasingString, top: Length, bottom: Length): VolumeBreakdown {
        val issues = mutableListOf<ValidationIssue>()
        val segments = if (bottom > top) {
            annulusSegments(geometry, top, bottom, string.spec.outerDiameter, string.name, string.id, issues)
        } else {
            issues += ValidationIssue(Severity.ERROR, "VOLUME_INTERVAL_EMPTY", "The bottom of the interval must be below its top.")
            emptyList()
        }
        return VolumeBreakdown("Annulus outside ${string.name}", segments, issues)
    }

    internal fun annulusSegments(
        geometry: WellboreGeometry,
        top: Length,
        bottom: Length,
        pipeOd: Length,
        pipeName: String,
        pipeStringId: String?,
        issues: MutableList<ValidationIssue>,
    ): List<VolumeSegment> {
        val segments = mutableListOf<VolumeSegment>()
        for ((a, b) in geometry.breakpoints(top, bottom).zipWithNext()) {
            val boundary = geometry.annulusBoundaryAt(mid(a, b), pipeOd, pipeStringId)
            if (boundary == null) {
                issues += gap(a, b)
                continue
            }
            if (boundary.diameter <= pipeOd) {
                issues += ValidationIssue(
                    Severity.ERROR, "ANNULUS_NO_CLEARANCE",
                    "'${boundary.name}' (${fmtIn(boundary.diameter)}) is not larger than $pipeName OD (${fmtIn(pipeOd)}) " +
                        "between ${fmtM(a)} and ${fmtM(b)} m MD.",
                    listOfNotNull(boundary.sourceId, pipeStringId),
                )
                continue
            }
            segments.append(
                VolumeSegment(
                    a, b, "${describe(boundary)} × $pipeName", boundary.diameter, pipeOd, boundary.basis,
                    TubularGeometry.annularCapacity(boundary.diameter, pipeOd), sourceId = boundary.sourceId,
                ),
            )
        }
        if (segments.any { it.outerBasis == DiameterBasis.BIT_SIZE }) issues += bitSizeNote()
        return segments
    }

    /** Appends [s], merging it into the previous segment when the geometry is identical and contiguous. */
    private fun MutableList<VolumeSegment>.append(s: VolumeSegment) {
        val last = lastOrNull()
        if (last != null && last.sourceId == s.sourceId && last.outerDiameter == s.outerDiameter &&
            last.innerDiameter == s.innerDiameter && last.excessFraction == s.excessFraction &&
            last.bottomMd.isApproximately(s.topMd, Length.meters(WellboreGeometry.BREAK_TOLERANCE_M))
        ) {
            this[lastIndex] = last.copy(bottomMd = s.bottomMd)
        } else {
            add(s)
        }
    }

    private fun describe(bore: Bore) = when (bore) {
        is Bore.OpenHole -> "${bore.name} (open hole)"
        else -> bore.name
    }

    private fun gap(a: Length, b: Length) = ValidationIssue(
        Severity.ERROR, "VOLUME_GEOMETRY_GAP",
        "No casing, tubing or hole section recorded between ${fmtM(a)} and ${fmtM(b)} m MD; that interval is not included.",
    )

    private fun noGeometry() = listOf(ValidationIssue(Severity.ERROR, "VOLUME_NO_GEOMETRY", "No hole sections or strings recorded."))

    private fun bitSizeNote() = ValidationIssue(
        Severity.INFO, "VOLUME_BIT_SIZE",
        "Open-hole intervals use the nominal bit size; actual hole volume needs a caliper or an agreed excess.",
    )

    companion object {
        const val VERSION = "1.0.0"

        internal fun mid(a: Length, b: Length) = Length((a.meters + b.meters) / 2.0)
        internal fun fmtM(l: Length) = String.format(java.util.Locale.US, "%.1f", l.meters)
        internal fun fmtIn(l: Length) = String.format(java.util.Locale.US, "%.3f in", l.inches)
    }
}
