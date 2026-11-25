package com.wellsketch.engineer.core.engineering.volumes

import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Volume
import com.wellsketch.engineer.core.model.units.VolumePerLength

/**
 * One interval of a volume calculation with constant geometry. For a bore,
 * [innerDiameter] is null; for an annulus it is the OD of the inner pipe.
 * [excessFraction] (e.g. 0.20 for 20 %) is applied on top of the geometric volume.
 */
data class VolumeSegment(
    val topMd: Length,
    val bottomMd: Length,
    val description: String,
    val outerDiameter: Length,
    val innerDiameter: Length?,
    val outerBasis: DiameterBasis,
    val capacity: VolumePerLength,
    val excessFraction: Double = 0.0,
    /** Model object the outer boundary belongs to (string, tubing or hole section). */
    val sourceId: String? = null,
) {
    init {
        require(bottomMd >= topMd) { "Segment bottom must not be above its top." }
        require(excessFraction >= 0.0) { "Excess must not be negative." }
    }

    val length: Length get() = bottomMd - topMd
    val geometricVolume: Volume get() = capacity * length
    val volume: Volume get() = Volume(geometricVolume.cubicMeters * (1.0 + excessFraction))
}

/** A volume made of contiguous segments, with the issues found while computing it. */
data class VolumeBreakdown(
    val title: String,
    val segments: List<VolumeSegment>,
    val issues: List<ValidationIssue> = emptyList(),
) {
    val total: Volume get() = Volume(segments.sumOf { it.volume.cubicMeters })
    val geometricTotal: Volume get() = Volume(segments.sumOf { it.geometricVolume.cubicMeters })
    val topMd: Length? get() = segments.firstOrNull()?.topMd
    val bottomMd: Length? get() = segments.lastOrNull()?.bottomMd
    val length: Length get() = Length(segments.sumOf { it.length.meters })

    /** True when at least one segment used the nominal bit size (open hole). */
    val usesBitSize: Boolean get() = segments.any { it.outerBasis == DiameterBasis.BIT_SIZE }
}
