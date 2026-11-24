package com.wellsketch.engineer.core.model.equipment

import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.LinearMass
import com.wellsketch.engineer.core.model.units.Pressure
import kotlinx.serialization.Serializable

@Serializable
enum class EquipmentCategory(val label: String) {
    CASING("Casing"),
    LINER("Liners"),
    TUBING("Tubing"),
    CASING_SHOE("Casing shoes & float equipment"),
    PACKER("Packers"),
    ESP("ESP systems"),
    PLUG("Bridge plugs & retainers"),
    COMPLETION_ACCESSORY("Completion accessories"),
    CHRISTMAS_TREE("Christmas Tree components"),
    CT_PRESSURE_CONTROL("CT pressure control"),
    CT_STRING("Coiled tubing strings"),
    CT_BHA("CT BHA tools"),
    CT_SURFACE("CT surface equipment"),
}

/**
 * Dimensional/rating data of an equipment item. All fields are optional; a library entry
 * only states what its source actually provides.
 */
@Serializable
data class EquipmentDimensions(
    val outerDiameter: Length? = null,
    val innerDiameter: Length? = null,
    val driftDiameter: Length? = null,
    val wallThickness: Length? = null,
    val bore: Length? = null,
    val length: Length? = null,
    val nominalLinearMass: LinearMass? = null,
    val ratedWorkingPressure: Pressure? = null,
)

/**
 * A reference image for a component breakdown view. Only images supplied and approved by
 * the owner may be used; the app never substitutes arbitrary internals.
 */
@Serializable
data class ReferenceImage(
    val id: String,
    val title: String,
    /** Asset path inside the app or project storage. */
    val assetPath: String,
    val approvedByOwner: Boolean,
)

@Serializable
data class EquipmentItem(
    val id: String,
    val category: EquipmentCategory,
    val name: String,
    val manufacturer: String? = null,
    val partNumber: String? = null,
    val grade: String? = null,
    val connection: String? = null,
    val dimensions: EquipmentDimensions = EquipmentDimensions(),
    val referenceImages: List<ReferenceImage> = emptyList(),
    val provenance: DataProvenance,
)
