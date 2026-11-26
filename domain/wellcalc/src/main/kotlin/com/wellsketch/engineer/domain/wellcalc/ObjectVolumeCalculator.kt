package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.volumes.DiameterBasis
import com.wellsketch.engineer.core.engineering.volumes.TubularGeometry
import com.wellsketch.engineer.core.engineering.volumes.VolumeBreakdown
import com.wellsketch.engineer.core.engineering.volumes.VolumeSegment
import com.wellsketch.engineer.core.engineering.volumes.WellboreGeometry
import com.wellsketch.engineer.core.model.units.Length

/** A pipe (production tubing or a casing string / liner) that a volume can be asked for. */
data class VolumePipe(
    val id: String,
    val name: String,
    val outerDiameter: Length,
    val innerDiameter: Length,
    val top: Length,
    val bottom: Length,
    val isTubing: Boolean,
)

/** Outer wall of an annulus chosen by the user. */
sealed interface AnnulusBoundary {
    val label: String

    /** Innermost casing/liner larger than the pipe, else open hole (the rule used by cementing). */
    data object Automatic : AnnulusBoundary {
        override val label = "Automatic (next casing, else open hole)"
    }

    /** A named casing string or liner, by its ID. */
    data class Casing(val stringId: String, val name: String) : AnnulusBoundary {
        override val label get() = name
    }

    /** The drilled hole (nominal bit sizes along the interval), ignoring any casing. */
    data object OpenHole : AnnulusBoundary {
        override val label = "Open hole (bit size)"
    }
}

enum class ObjectVolumeMode(val label: String) { INSIDE("Inside the pipe"), ANNULUS("Annulus outside the pipe") }

/**
 * Volume by named objects (calculation version [VERSION]), as in the HTML v0.9 "Volume
 * calculator": inside a chosen pipe, or in the annulus between a chosen pipe and a chosen
 * outer wall, over an exact MD interval (canonical datum).
 *
 * - Inside: Σ π/4 × ID² × length, using the chosen pipe's own ID (not the flow path).
 * - Annulus: Σ π/4 × (outer ID² − pipe OD²) × length, split wherever the geometry changes.
 * - The interval must lie within the pipe, and within the chosen outer string. A result is
 *   given only when the whole interval is covered: no partial totals.
 *
 * Not included: couplings and tool joints, tapered strings (one ID per string), washout
 * beyond the bit size, excess. Shared geometry rules: [WellboreGeometry].
 */
class ObjectVolumeCalculator(private val volumes: WellVolumeCalculator = WellVolumeCalculator()) {

    /** Pipes in the selected state: tubing first, then casing strings and liners from largest OD. */
    fun pipes(geometry: WellboreGeometry): List<VolumePipe> = buildList {
        geometry.tubing?.let { t ->
            add(VolumePipe(t.item.id, t.item.name, t.item.spec.outerDiameter, t.item.spec.innerDiameter, t.top, t.bottom, isTubing = true))
        }
        geometry.strings.sortedByDescending { it.item.spec.outerDiameter }.forEach { s ->
            add(VolumePipe(s.item.id, s.item.name, s.item.spec.outerDiameter, s.item.spec.innerDiameter, s.top, s.bottom, isTubing = false))
        }
    }

    /** Outer walls that make sense around [pipe]: strings that overlap it and are larger, and the open hole. */
    fun boundaries(geometry: WellboreGeometry, pipe: VolumePipe): List<AnnulusBoundary> = buildList {
        add(AnnulusBoundary.Automatic)
        geometry.strings
            .filter { it.item.id != pipe.id && it.item.spec.innerDiameter > pipe.outerDiameter && overlap(it.top, it.bottom, pipe.top, pipe.bottom) != null }
            .sortedBy { it.item.spec.innerDiameter }
            .forEach { add(AnnulusBoundary.Casing(it.item.id, it.item.name)) }
        if (geometry.holes.any { overlap(it.top, it.bottom, pipe.top, pipe.bottom) != null }) add(AnnulusBoundary.OpenHole)
    }

    /** Default interval: the whole pipe, limited to the chosen outer string where one is chosen. */
    fun defaultInterval(geometry: WellboreGeometry, pipe: VolumePipe, mode: ObjectVolumeMode, boundary: AnnulusBoundary): Pair<Length, Length>? {
        if (mode == ObjectVolumeMode.INSIDE) return pipe.top to pipe.bottom
        return when (boundary) {
            is AnnulusBoundary.Casing -> geometry.strings.firstOrNull { it.item.id == boundary.stringId }?.let { overlap(it.top, it.bottom, pipe.top, pipe.bottom) }
            AnnulusBoundary.OpenHole -> {
                val holeTop = geometry.holes.minOfOrNull { it.top } ?: return null
                val holeBottom = geometry.holes.maxOf { it.bottom }
                overlap(holeTop, holeBottom, pipe.top, pipe.bottom)
            }
            AnnulusBoundary.Automatic -> pipe.top to pipe.bottom
        }
    }

    fun calculate(
        geometry: WellboreGeometry,
        pipeId: String,
        mode: ObjectVolumeMode,
        boundary: AnnulusBoundary,
        top: Length,
        bottom: Length,
    ): VolumeBreakdown {
        val pipe = pipes(geometry).firstOrNull { it.id == pipeId }
        val title = when {
            pipe == null -> "Volume"
            mode == ObjectVolumeMode.INSIDE -> "Inside ${pipe.name}"
            else -> "${pipe.name} × ${boundary.label}"
        }
        val fail = { code: String, message: String -> VolumeBreakdown(title, emptyList(), listOf(ValidationIssue(Severity.ERROR, code, message))) }
        if (pipe == null) return fail("OBJ_VOLUME_NO_PIPE", "Select a tubing, casing or liner string.")
        if (bottom <= top) return fail("VOLUME_INTERVAL_EMPTY", "The bottom of the interval must be below its top.")
        if (top < pipe.top - TOL || bottom > pipe.bottom + TOL) {
            return fail("OBJ_VOLUME_OUTSIDE_PIPE", "The interval ${m(top)}–${m(bottom)} m MD is outside ${pipe.name} (${m(pipe.top)}–${m(pipe.bottom)} m MD).")
        }
        return when (mode) {
            ObjectVolumeMode.INSIDE -> VolumeBreakdown(
                title,
                listOf(VolumeSegment(top, bottom, "Inside ${pipe.name}", pipe.innerDiameter, null, DiameterBasis.STATED_ID, TubularGeometry.insideCapacity(pipe.innerDiameter), sourceId = pipe.id)),
            )
            ObjectVolumeMode.ANNULUS -> annulus(geometry, pipe, boundary, top, bottom, title, fail)
        }
    }

    private fun annulus(
        geometry: WellboreGeometry,
        pipe: VolumePipe,
        boundary: AnnulusBoundary,
        top: Length,
        bottom: Length,
        title: String,
        fail: (String, String) -> VolumeBreakdown,
    ): VolumeBreakdown {
        val issues = mutableListOf<ValidationIssue>()
        val exceptId = pipe.id.takeUnless { pipe.isTubing }
        val segments: List<VolumeSegment> = when (boundary) {
            AnnulusBoundary.Automatic -> volumes.annulusSegments(geometry, top, bottom, pipe.outerDiameter, pipe.name, exceptId, issues)
            is AnnulusBoundary.Casing -> {
                val outer = geometry.strings.firstOrNull { it.item.id == boundary.stringId }
                    ?: return fail("OBJ_VOLUME_NO_BOUNDARY", "The selected outer string is not in this well.")
                if (top < outer.top - TOL || bottom > outer.bottom + TOL) {
                    return fail(
                        "OBJ_VOLUME_OUTSIDE_BOUNDARY",
                        "The interval ${m(top)}–${m(bottom)} m MD is outside ${outer.item.name} (${m(outer.top)}–${m(outer.bottom)} m MD).",
                    )
                }
                val outerId = outer.item.spec.innerDiameter
                if (outerId <= pipe.outerDiameter) {
                    return fail("ANNULUS_NO_CLEARANCE", "${outer.item.name} ID is not larger than ${pipe.name} OD.")
                }
                listOf(
                    VolumeSegment(
                        top, bottom, "${outer.item.name} × ${pipe.name}", outerId, pipe.outerDiameter, DiameterBasis.STATED_ID,
                        TubularGeometry.annularCapacity(outerId, pipe.outerDiameter), sourceId = outer.item.id,
                    ),
                )
            }
            AnnulusBoundary.OpenHole -> {
                val out = mutableListOf<VolumeSegment>()
                for ((a, b) in geometry.breakpoints(top, bottom).zipWithNext()) {
                    val hole = geometry.holeAt(WellVolumeCalculator.mid(a, b))
                    if (hole == null) {
                        return fail("VOLUME_GEOMETRY_GAP", "No hole section recorded between ${m(a)} and ${m(b)} m MD.")
                    }
                    if (hole.bitDiameter <= pipe.outerDiameter) {
                        return fail("ANNULUS_NO_CLEARANCE", "'${hole.name}' bit size is not larger than ${pipe.name} OD between ${m(a)} and ${m(b)} m MD.")
                    }
                    val seg = VolumeSegment(
                        a, b, "${hole.name} (open hole) × ${pipe.name}", hole.bitDiameter, pipe.outerDiameter, DiameterBasis.BIT_SIZE,
                        TubularGeometry.annularCapacity(hole.bitDiameter, pipe.outerDiameter), sourceId = hole.id,
                    )
                    val last = out.lastOrNull()
                    if (last != null && last.sourceId == seg.sourceId) out[out.lastIndex] = last.copy(bottomMd = b) else out += seg
                }
                val cased = geometry.strings.filter {
                    it.item.id != pipe.id && it.item.spec.innerDiameter > pipe.outerDiameter && overlap(it.top, it.bottom, top, bottom) != null
                }
                if (cased.isNotEmpty()) {
                    issues += ValidationIssue(
                        Severity.WARNING, "OBJ_VOLUME_IGNORES_CASING",
                        "Open-hole annulus ignores ${cased.joinToString { it.item.name }} in this interval: the volume is to the bit size, not to the casing.",
                    )
                }
                issues += ValidationIssue(Severity.INFO, "VOLUME_BIT_SIZE", "Open-hole intervals use the nominal bit size; actual hole volume needs a caliper or an agreed excess.")
                out
            }
        }
        val covered = segments.sumOf { it.length.meters }
        if (kotlin.math.abs(covered - (bottom - top).meters) > TOL.meters) {
            return VolumeBreakdown(title, emptyList(), issues + ValidationIssue(Severity.ERROR, "OBJ_VOLUME_INCOMPLETE", "Part of the interval has no annulus geometry; no total is given."))
        }
        return VolumeBreakdown(title, segments, issues)
    }

    private fun overlap(aTop: Length, aBottom: Length, bTop: Length, bBottom: Length): Pair<Length, Length>? {
        val top = maxOf(aTop, bTop)
        val bottom = minOf(aBottom, bBottom)
        return if (bottom > top) top to bottom else null
    }

    private fun m(l: Length) = WellVolumeCalculator.fmtM(l)

    companion object {
        const val VERSION = "1.0.0"
        private val TOL = Length.meters(WellboreGeometry.BREAK_TOLERANCE_M)
    }
}
