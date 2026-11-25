package com.wellsketch.engineer.core.model.datum

import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import kotlinx.serialization.Serializable

/**
 * Elevations of the permanent well datums, all expressed relative to mean sea level
 * (positive above MSL). MSL itself is elevation 0 by definition.
 *
 * Offshore wells (water depth, mudline, air gap) are not modelled yet; see README
 * "Remaining work".
 */
@Serializable
data class WellDatums(
    /** Elevation of the Rotary Kelly Bushing above MSL. */
    val rkbElevation: Length,
    /** Elevation of ground level above MSL (negative if below sea level). */
    val groundLevelElevation: Length,
    /** The reference used for MD in this well's records (normally RKB). */
    val primaryMdReference: DepthReference = DepthReference.Rkb,
    val provenance: DataProvenance,
) {
    init {
        require(primaryMdReference !is DepthReference.MeanSeaLevel) {
            "Primary MD reference cannot be MSL."
        }
    }

    /** RKB height above ground level (drill-floor height / "air gap" onshore). */
    val rkbAboveGroundLevel: Length get() = rkbElevation - groundLevelElevation
}
