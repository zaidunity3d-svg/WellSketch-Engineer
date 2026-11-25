package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.volumes.DiameterBasis
import com.wellsketch.engineer.core.engineering.volumes.TubularGeometry
import com.wellsketch.engineer.core.engineering.volumes.VolumeBreakdown
import com.wellsketch.engineer.core.engineering.volumes.VolumeSegment
import com.wellsketch.engineer.core.engineering.volumes.WellboreGeometry
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Volume
import com.wellsketch.engineer.core.model.well.CasingString

/**
 * Excess policy for a cement job. Fractions, e.g. 0.30 = 30 %. Open-hole excess applies to
 * open-hole annulus (and the rathole when included); cased-hole excess to annulus inside
 * a previous casing (normally 0).
 */
data class CementExcessPolicy(
    val openHoleExcessFraction: Double = 0.0,
    val casedHoleExcessFraction: Double = 0.0,
    /** Include the open hole below the shoe (rathole) in the slurry total. */
    val includeRathole: Boolean = false,
) {
    init {
        require(openHoleExcessFraction in 0.0..MAX_EXCESS) { "Open-hole excess must be between 0 and ${(MAX_EXCESS * 100).toInt()} %." }
        require(casedHoleExcessFraction in 0.0..MAX_EXCESS) { "Cased-hole excess must be between 0 and ${(MAX_EXCESS * 100).toInt()} %." }
    }

    companion object {
        const val MAX_EXCESS = 3.0
    }
}

data class CementJobVolumes(
    val string: CasingString,
    /** Annulus from top of cement to the bottom of the cement interval, with excess. */
    val annulus: VolumeBreakdown,
    /** Inside the string from the float collar to the shoe; null if the float collar is not recorded. */
    val shoeTrack: VolumeSegment?,
    /** Open hole below the shoe to the bottom of its hole section; null if there is none. */
    val rathole: VolumeSegment?,
    val ratholeIncluded: Boolean,
    /** Inside the string from its top to the float collar (to the shoe when no float collar is recorded). */
    val displacement: VolumeBreakdown,
    val issues: List<ValidationIssue>,
) {
    val slurryVolume: Volume
        get() = Volume(
            annulus.total.cubicMeters + (shoeTrack?.volume?.cubicMeters ?: 0.0) +
                (if (ratholeIncluded) rathole?.volume?.cubicMeters ?: 0.0 else 0.0),
        )
}

/**
 * Cement slurry and displacement volumes for one casing string or liner (calculation
 * version [VERSION]).
 *
 * - Annulus: string OD to the previous casing ID where covered, otherwise to the bit size,
 *   from the recorded top of cement to the bottom of the cement interval; excess per
 *   [CementExcessPolicy].
 * - Shoe track: string ID × (shoe − float collar).
 * - Rathole: bit size × (hole-section bottom − shoe), optional.
 * - Displacement: string ID from the string top to the float collar. For a liner the
 *   running string from surface to the liner top is not included (not modelled yet).
 *
 * Not included: spacers and preflush, surface lines, compressibility, cement shrinkage,
 * tapered strings (one ID per string).
 */
class CementVolumeCalculator(private val volumes: WellVolumeCalculator = WellVolumeCalculator()) {

    fun calculate(geometry: WellboreGeometry, stringId: String, policy: CementExcessPolicy = CementExcessPolicy()): CementJobVolumes? {
        val span = geometry.strings.firstOrNull { it.item.id == stringId } ?: return null
        val string = span.item
        val ctx = geometry.ctx
        val issues = mutableListOf<ValidationIssue>()
        val id = string.spec.innerDiameter
        val od = string.spec.outerDiameter

        // --- Annulus ---------------------------------------------------------------------
        val cement = string.cement
        val annulus = if (cement == null) {
            issues += ValidationIssue(Severity.INFO, "CEMENT_NOT_RECORDED", "No cement interval recorded for ${string.name}.", listOf(string.id))
            VolumeBreakdown("Annulus", emptyList())
        } else {
            val toc = ctx.canonical(cement.top)
            val bottom = ctx.canonical(cement.bottom)
            if (toc < span.top) {
                issues += ValidationIssue(Severity.WARNING, "CEMENT_ABOVE_STRING", "Top of cement is above the top of ${string.name}; the annulus is taken from the string top.", listOf(string.id))
            }
            if (bottom > span.bottom + Length.meters(WellboreGeometry.BREAK_TOLERANCE_M)) {
                issues += ValidationIssue(Severity.WARNING, "CEMENT_BELOW_SHOE", "Bottom of cement is below the shoe of ${string.name}; the annulus is taken to the shoe.", listOf(string.id))
            }
            val top = maxOf(toc, span.top)
            val end = minOf(bottom, span.bottom)
            val raw = volumes.annulusOutside(geometry, string, top, end)
            issues += raw.issues
            VolumeBreakdown(
                "Annulus",
                raw.segments.map {
                    it.copy(excessFraction = if (it.outerBasis == DiameterBasis.BIT_SIZE) policy.openHoleExcessFraction else policy.casedHoleExcessFraction)
                },
            )
        }

        // --- Shoe track and displacement -------------------------------------------------
        val floatCollar = string.shoe.floatCollarDepth?.let(ctx::canonical)
        val shoeTrack = when {
            floatCollar == null -> {
                issues += ValidationIssue(
                    Severity.WARNING, "CEMENT_NO_FLOAT_COLLAR",
                    "Float collar depth not recorded for ${string.name}: shoe track not included and displacement taken to the shoe.",
                    listOf(string.id),
                )
                null
            }
            floatCollar >= span.bottom || floatCollar <= span.top -> {
                issues += ValidationIssue(Severity.ERROR, "CEMENT_FLOAT_COLLAR_OUTSIDE", "Float collar must be between the top and the shoe of ${string.name}.", listOf(string.id))
                null
            }
            else -> VolumeSegment(floatCollar, span.bottom, "Shoe track inside ${string.name}", id, null, DiameterBasis.STATED_ID, TubularGeometry.insideCapacity(id), sourceId = string.id)
        }
        val displacementBottom = shoeTrack?.topMd ?: span.bottom
        val displacement = VolumeBreakdown(
            "Displacement",
            listOf(VolumeSegment(span.top, displacementBottom, "Inside ${string.name}", id, null, DiameterBasis.STATED_ID, TubularGeometry.insideCapacity(id), sourceId = string.id)),
        )
        if (string.isLiner) {
            issues += ValidationIssue(
                Severity.WARNING, "CEMENT_LINER_RUNNING_STRING",
                "Liner job: the running-string volume from surface to the liner top is not included in the displacement.",
                listOf(string.id),
            )
        }

        // --- Rathole ---------------------------------------------------------------------
        // The rathole is the part of the hole section drilled for this string (bit larger than
        // the string OD) that lies below the shoe; the next, smaller section is not rathole.
        val shoe = span.bottom
        val tol = Length.meters(WellboreGeometry.BREAK_TOLERANCE_M)
        val ratholeHole = geometry.holes.firstOrNull { it.item.bitDiameter > od && shoe > it.top + tol && shoe < it.bottom - tol }
        val rathole = ratholeHole?.let { h ->
            VolumeSegment(
                shoe, h.bottom, "Rathole – ${h.item.name}", h.item.bitDiameter, null, DiameterBasis.BIT_SIZE,
                TubularGeometry.insideCapacity(h.item.bitDiameter), excessFraction = policy.openHoleExcessFraction, sourceId = h.item.id,
            )
        }

        return CementJobVolumes(string, annulus, shoeTrack, rathole, policy.includeRathole && rathole != null, displacement, issues)
    }

    companion object {
        const val VERSION = "1.0.0"
    }
}
