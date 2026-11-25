package com.wellsketch.engineer.core.model.geology

import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import kotlinx.serialization.Serializable

/**
 * A formation (stratigraphic unit) top. The top is a vertical depth because formation
 * boundaries are geological surfaces; the base of a formation is the top of the next
 * deeper formation. Layers are currently assumed horizontal (no dip) in the schematic.
 */
@Serializable
data class FormationTop(
    val id: String,
    val name: String,
    val top: TrueVerticalDepth,
    val lithology: Lithology,
    /** MD at which the well penetrated this top, if picked from logs. */
    val penetrationMd: MeasuredDepth? = null,
    val notes: String? = null,
    val provenance: DataProvenance,
)

/** Primary lithology classes used for the stratigraphic column legend. */
@Serializable
enum class Lithology(val label: String) {
    SANDSTONE("Sandstone"),
    SHALE("Shale"),
    CLAYSTONE("Claystone"),
    SILTSTONE("Siltstone"),
    LIMESTONE("Limestone"),
    DOLOMITE("Dolomite"),
    CHALK("Chalk"),
    MARL("Marl"),
    ANHYDRITE("Anhydrite"),
    SALT("Salt (halite)"),
    COAL("Coal"),
    CONGLOMERATE("Conglomerate"),
    IGNEOUS_METAMORPHIC("Igneous / metamorphic"),
    UNCONSOLIDATED("Unconsolidated sediments"),
    UNKNOWN("Unknown"),
}
