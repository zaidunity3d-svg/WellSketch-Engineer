package com.wellsketch.engineer.core.model.datum

import com.wellsketch.engineer.core.model.units.Length
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A depth reference (datum) from which depths are measured, positive downward.
 *
 * RKB, GL and MSL are kept distinct and are never interchangeable. Depth values are always
 * stored together with their reference (see [MeasuredDepth] and [TrueVerticalDepth]) so
 * that the compiler and the converters, not the user's memory, keep datums apart.
 */
@Serializable
sealed interface DepthReference {
    val shortLabel: String
    val description: String

    /** Rotary Kelly Bushing (drill floor) of the drilling rig; usual MD/TVD datum. */
    @Serializable
    @SerialName("RKB")
    data object Rkb : DepthReference {
        override val shortLabel = "RKB"
        override val description = "Rotary Kelly Bushing (drill floor)"
    }

    /** Ground level at the wellsite (onshore). */
    @Serializable
    @SerialName("GL")
    data object GroundLevel : DepthReference {
        override val shortLabel = "GL"
        override val description = "Ground level"
    }

    /**
     * Mean sea level. Valid for vertical depths (TVDSS). Measured depths may not be
     * referenced to MSL because the hole between MSL elevation and surface is not
     * guaranteed to be vertical for onshore wells.
     */
    @Serializable
    @SerialName("MSL")
    data object MeanSeaLevel : DepthReference {
        override val shortLabel = "MSL"
        override val description = "Mean sea level"
    }

    /**
     * An operation-specific zero point, e.g. the coiled-tubing depth counter zero at the
     * stripper, injector or top of the Christmas Tree. Defined by an explicit height above
     * ground level so that conversions to well MD are deterministic and auditable.
     */
    @Serializable
    @SerialName("OPERATION")
    data class Operation(
        val id: String,
        val name: String,
        val heightAboveGroundLevel: Length,
    ) : DepthReference {
        override val shortLabel: String get() = name
        override val description: String get() = "Operation datum '$name'"
    }
}
