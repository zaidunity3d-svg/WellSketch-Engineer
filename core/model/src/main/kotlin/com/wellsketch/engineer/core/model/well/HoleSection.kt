package com.wellsketch.engineer.core.model.well

import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import kotlinx.serialization.Serializable

/**
 * A drilled hole section. [bitDiameter] is the nominal bit size; the actual hole may be
 * enlarged (washout). Measured caliper data, when added, must be stored separately and
 * never silently replace the nominal bit size.
 */
@Serializable
data class HoleSection(
    val id: String,
    val name: String,
    val bitDiameter: Length,
    val top: MeasuredDepth,
    val bottom: MeasuredDepth,
    val provenance: DataProvenance,
    /**
     * Average hole diameter over the section from a caliper log (or an agreed value). When
     * entered, open-hole volumes use it instead of the bit size; excess still applies on top.
     */
    val averageDiameter: Length? = null,
) {
    /** Diameter used for open-hole volumes: the caliper average when entered, else the bit size. */
    val volumeDiameter: Length get() = averageDiameter ?: bitDiameter
}
