package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.volumes.DiameterBasis
import com.wellsketch.engineer.core.engineering.volumes.TubularGeometry
import com.wellsketch.engineer.core.engineering.volumes.VolumeBreakdown
import com.wellsketch.engineer.core.engineering.volumes.VolumeSegment
import com.wellsketch.engineer.core.model.ct.CtOperation
import com.wellsketch.engineer.core.model.ct.CtStringSection
import com.wellsketch.engineer.core.model.units.FlowRate
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Volume
import com.wellsketch.engineer.core.model.units.VolumePerLength

/** CT string section with its internal capacity and the part of it inside the well. */
data class CtSectionVolume(
    val section: CtStringSection,
    val internalCapacity: VolumePerLength,
    val lengthInWell: Length,
)

/** Annular velocity at a given pump rate in one annulus segment. */
data class AnnularVelocity(val segment: VolumeSegment, val metersPerMinute: Double)

data class CtVolumes(
    val sections: List<CtSectionVolume>,
    /** Internal volume of the whole string (reel and well), from nominal ID = OD − 2 × wall. */
    val ctInternalVolumeTotal: Volume,
    /** CT length from the top of the well conduit to the BHA top. */
    val ctLengthInWell: Length,
    val ctInternalVolumeInWell: Volume,
    /** Steel volume of the CT in the well (open-end displacement). */
    val ctSteelDisplacementInWell: Volume,
    /** BHA displacement from each component's max OD × length (an upper bound). */
    val bhaDisplacementUpperBound: Volume,
    /** Annulus between the conduit (tubing/casing/liner/open hole) and CT/BHA, from the conduit top to the nose. */
    val annulus: VolumeBreakdown,
    val pumpRate: FlowRate?,
    /** Time to pump the whole CT internal volume (reel + well) at [pumpRate]: fluid at the nozzle. */
    val timeToNozzleMinutes: Double?,
    /** Bottoms-up: time to circulate the annulus volume from the nose to surface at [pumpRate]. */
    val bottomsUpMinutes: Double?,
    val annularVelocities: List<AnnularVelocity>,
    val issues: List<ValidationIssue>,
) {
    val slowestAnnularVelocity: AnnularVelocity? get() = annularVelocities.minByOrNull { it.metersPerMinute }
    val fastestAnnularVelocity: AnnularVelocity? get() = annularVelocities.maxByOrNull { it.metersPerMinute }
}

/**
 * CT volumes and pumping times (calculation version [VERSION]).
 *
 * - CT internal capacity per section from the nominal ID implied by the data sheet
 *   (OD − 2 × wall); the CT in the well is laid out from the BHA top upward, section 0
 *   (downhole end) first.
 * - Annulus: conduit bore from the flow path (tubing by default, else casing/liner/open
 *   hole) around the CT OD above the BHA and around each BHA component's max OD.
 * - Times: volume ÷ rate. Annular velocity: rate ÷ annular area per segment.
 *
 * Not included: the wellhead/tree/CT-stack bore above the conduit top, surface lines,
 * reel-to-datum CT length on surface, fluid compressibility, CT lying eccentric against the
 * low side (volumes are unaffected; velocities assume uniform flow in the annulus).
 */
class CtVolumeCalculator {

    fun calculate(flowPath: FlowPath, placement: CtPlacement, operation: CtOperation): CtVolumes {
        val issues = mutableListOf<ValidationIssue>()
        val top = flowPath.segments.firstOrNull()?.topMd
        val nose = placement.noseMd
        val bhaTop = placement.bhaTopMd
        val ctString = operation.ctString

        // --- CT string -------------------------------------------------------------------
        val ctLengthInWell = if (top != null) maxOf(Length.ZERO, bhaTop - top) else Length.ZERO
        var remaining = ctLengthInWell
        val sections = ctString.sections.map { s ->
            val inWell = minOf(remaining, s.length)
            remaining -= inWell
            CtSectionVolume(s, TubularGeometry.insideCapacity(s.nominalInnerDiameter), inWell)
        }
        if (remaining > Length.TOLERANCE) {
            issues += ValidationIssue(Severity.ERROR, "CT_VOLUME_STRING_TOO_SHORT", "CT string is shorter than the CT length in the well.", listOf(ctString.id))
        }
        val totalInternal = Volume(sections.sumOf { (it.internalCapacity * it.section.length).cubicMeters })
        val inWellInternal = Volume(sections.sumOf { (it.internalCapacity * it.lengthInWell).cubicMeters })
        val steel = Volume(sections.sumOf { (TubularGeometry.openEndDisplacement(it.section.outerDiameter, it.section.nominalInnerDiameter) * it.lengthInWell).cubicMeters })
        val bha = Volume(placement.bhaComponents.sumOf { (TubularGeometry.closedEndDisplacement(it.component.maxOuterDiameter) * it.component.length).cubicMeters })

        // --- Annulus ---------------------------------------------------------------------
        val annulusSegments = mutableListOf<VolumeSegment>()
        if (top == null) {
            issues += ValidationIssue(Severity.ERROR, "CT_VOLUME_NO_PATH", "No flow path: annulus volume not calculated.")
        } else if (nose > top) {
            // Pipe OD along the string: CT sections above the BHA, then each BHA component.
            val pipes = mutableListOf<Triple<Length, Length, Pair<String, Length>>>() // top, bottom, (name, OD)
            var bottom = bhaTop
            for (s in sections) {
                if (s.lengthInWell <= Length.ZERO) break
                pipes += Triple(bottom - s.lengthInWell, bottom, "CT" to s.section.outerDiameter)
                bottom -= s.lengthInWell
            }
            placement.bhaComponents.forEach { pipes += Triple(it.topMd, it.bottomMd, it.component.name to it.component.maxOuterDiameter) }
            val breaks = (listOf(top, nose) + flowPath.segments.flatMap { listOf(it.topMd, it.bottomMd) } + pipes.flatMap { listOf(it.first, it.second) })
                .filter { it >= top && it <= nose }
                .sorted()
                .fold(mutableListOf<Length>()) { acc, d -> if (acc.isEmpty() || (d - acc.last()).meters > 1e-3) acc += d; acc }
            if (breaks.last() != nose) breaks[breaks.lastIndex] = nose
            for ((a, b) in breaks.zipWithNext()) {
                val mid = Length((a.meters + b.meters) / 2.0)
                val conduit = flowPath.segmentAt(mid)
                val pipe = pipes.firstOrNull { mid >= it.first && mid <= it.second }
                if (conduit == null || pipe == null) {
                    issues += ValidationIssue(Severity.ERROR, "CT_VOLUME_GAP", "No conduit or pipe recorded between ${fmt(a)} and ${fmt(b)} m MD.")
                    continue
                }
                val (pipeName, pipeOd) = pipe.third
                if (conduit.innerDiameter <= pipeOd) {
                    issues += ValidationIssue(
                        Severity.ERROR, "CT_VOLUME_NO_CLEARANCE",
                        "$pipeName OD is not smaller than the ${conduit.conduitName} bore between ${fmt(a)} and ${fmt(b)} m MD.",
                    )
                    continue
                }
                val segment = VolumeSegment(
                    a, b, "${conduit.conduitName} × $pipeName", conduit.innerDiameter, pipeOd,
                    if (conduit.conduitType == ConduitType.OPEN_HOLE) DiameterBasis.BIT_SIZE else DiameterBasis.STATED_ID,
                    TubularGeometry.annularCapacity(conduit.innerDiameter, pipeOd), sourceId = conduit.conduitId,
                )
                val last = annulusSegments.lastOrNull()
                if (last != null && last.sourceId == segment.sourceId && last.outerDiameter == segment.outerDiameter &&
                    last.innerDiameter == segment.innerDiameter && last.description == segment.description
                ) {
                    annulusSegments[annulusSegments.lastIndex] = last.copy(bottomMd = segment.bottomMd)
                } else {
                    annulusSegments += segment
                }
            }
        }
        val annulus = VolumeBreakdown("CT annulus (conduit top to nose)", annulusSegments)

        // --- Rate-dependent results ------------------------------------------------------
        val rate = operation.pumpRate
        val ratePerMin = rate?.cubicMetersPerMinute?.takeIf { it > 0.0 }
        if (rate == null) {
            issues += ValidationIssue(Severity.INFO, "CT_RATE_NOT_SPECIFIED", "Pump rate not entered: times and annular velocities are not calculated.")
        } else if (ratePerMin == null) {
            issues += ValidationIssue(Severity.ERROR, "CT_RATE_NOT_POSITIVE", "Pump rate must be positive.")
        }
        val velocities = if (ratePerMin != null) {
            annulusSegments.map { AnnularVelocity(it, ratePerMin / it.capacity.cubicMetersPerMeter) }
        } else {
            emptyList()
        }
        return CtVolumes(
            sections = sections,
            ctInternalVolumeTotal = totalInternal,
            ctLengthInWell = ctLengthInWell,
            ctInternalVolumeInWell = inWellInternal,
            ctSteelDisplacementInWell = steel,
            bhaDisplacementUpperBound = bha,
            annulus = annulus,
            pumpRate = rate,
            timeToNozzleMinutes = ratePerMin?.let { totalInternal.cubicMeters / it },
            bottomsUpMinutes = ratePerMin?.let { annulus.total.cubicMeters / it },
            annularVelocities = velocities,
            issues = issues,
        )
    }

    companion object {
        const val VERSION = "1.0.0"
        private fun fmt(l: Length) = String.format(java.util.Locale.US, "%.1f", l.meters)
    }
}
