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
)
