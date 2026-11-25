package com.wellsketch.engineer.core.model.surface

import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import kotlinx.serialization.Serializable

/** Type of a pressure-containing surface component (Christmas Tree or CT stack). */
@Serializable
enum class StackComponentType(val label: String, val group: StackGroup) {
    // Christmas Tree
    TREE_CAP("Tree cap / top connection", StackGroup.CHRISTMAS_TREE),
    SWAB_VALVE("Swab valve", StackGroup.CHRISTMAS_TREE),
    FLOW_TEE("Flow tee / cross", StackGroup.CHRISTMAS_TREE),
    WING_VALVE("Wing valve", StackGroup.CHRISTMAS_TREE),
    UPPER_MASTER_VALVE("Upper master valve", StackGroup.CHRISTMAS_TREE),
    LOWER_MASTER_VALVE("Lower master valve", StackGroup.CHRISTMAS_TREE),
    TUBING_HEAD_ADAPTER("Tubing head adapter", StackGroup.CHRISTMAS_TREE),

    // Coiled-tubing pressure control equipment
    STRIPPER_PACKOFF("Stripper / packoff", StackGroup.CT_PRESSURE_CONTROL),
    CT_BOP("CT BOP", StackGroup.CT_PRESSURE_CONTROL),
    SHEAR_SEAL_BOP("Shear-seal BOP", StackGroup.CT_PRESSURE_CONTROL),
    RISER_LUBRICATOR("Riser / lubricator", StackGroup.CT_PRESSURE_CONTROL),
    FLOW_CROSS("Flow cross / kill-line spool", StackGroup.CT_PRESSURE_CONTROL),
    CROSSOVER_ADAPTER("Crossover / adapter", StackGroup.CT_PRESSURE_CONTROL),
    OTHER("Other", StackGroup.CT_PRESSURE_CONTROL),
}

@Serializable
enum class StackGroup { CHRISTMAS_TREE, CT_PRESSURE_CONTROL }

/**
 * End connection of a stack component. Compatibility is currently evaluated as exact
 * equality of [designation]; detailed flange/union compatibility rules (size, rating,
 * ring gasket) require engineering specification and are pending review.
 */
@Serializable
data class EndConnection(
    /** Free-text designation as written on the data sheet, e.g. "Flange size/rating (demo)". */
    val designation: String,
)

/**
 * One pressure-containing component in a vertical surface stack. Values must come from
 * the component data sheet; [height] is optional and, when absent, the schematic uses a
 * clearly flagged placeholder height for drawing only.
 */
@Serializable
data class StackComponent(
    val id: String,
    val type: StackComponentType,
    val name: String,
    val bore: Length,
    val ratedWorkingPressure: Pressure,
    val topConnection: EndConnection,
    val bottomConnection: EndConnection,
    val height: Length? = null,
    val catalogItemId: String? = null,
    val provenance: DataProvenance,
)

/** Christmas Tree, components ordered from top to bottom. */
@Serializable
data class ChristmasTree(
    val id: String,
    val name: String,
    val components: List<StackComponent>,
    /** Height of the wellhead/tubing head top above ground level, if known. */
    val wellheadTopAboveGroundLevel: Length? = null,
    val provenance: DataProvenance,
)

/** Configured CT pressure-control stack, components ordered from top to bottom. */
@Serializable
data class CtPressureControlStack(
    val id: String,
    val name: String,
    val components: List<StackComponent>,
    val provenance: DataProvenance,
)

