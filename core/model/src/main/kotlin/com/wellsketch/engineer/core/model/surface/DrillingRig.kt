package com.wellsketch.engineer.core.model.surface

import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import kotlinx.serialization.Serializable

/**
 * Component of a drilling BOP stack. These types are deliberately separate from
 * [StackComponentType]: a drilling BOP can never be configured as coiled-tubing
 * pressure-control equipment, and a CT BOP can never appear in a drilling stack.
 */
@Serializable
enum class DrillingBopComponentType(val label: String) {
    BELL_NIPPLE("Bell nipple / flow nipple"),
    ANNULAR_PREVENTER("Annular preventer"),
    SINGLE_RAM_PREVENTER("Single ram preventer"),
    DOUBLE_RAM_PREVENTER("Double ram preventer"),
    DRILLING_SPOOL("Drilling spool"),
    SPACER_SPOOL("Spacer spool / adapter"),
}

@Serializable
enum class RamType(val label: String) {
    PIPE("Pipe rams"),
    VARIABLE_BORE("Variable bore rams"),
    BLIND("Blind rams"),
    BLIND_SHEAR("Blind/shear rams"),
    CASING("Casing rams"),
}

/** One drilling BOP stack component. Values must come from the equipment data sheet. */
@Serializable
data class DrillingBopComponent(
    val id: String,
    val type: DrillingBopComponentType,
    val name: String,
    val bore: Length,
    /** Null where a rating does not apply or is not provided (e.g. a bell nipple). */
    val ratedWorkingPressure: Pressure? = null,
    val topConnection: EndConnection,
    val bottomConnection: EndConnection,
    val height: Length? = null,
    /** Ram cavities from top to bottom (ram preventers only). */
    val rams: List<RamType> = emptyList(),
    /** Choke/kill or other side outlets on this body. */
    val sideOutlets: List<SideOutlet> = emptyList(),
    val catalogItemId: String? = null,
    val provenance: DataProvenance,
)

/** Drilling BOP stack, components ordered from top to bottom. */
@Serializable
data class DrillingBopStack(
    val id: String,
    val name: String,
    val components: List<DrillingBopComponent>,
    val provenance: DataProvenance,
)

/** Drilling rig substructure data used for the drilling-state surface view and checks. */
@Serializable
data class DrillingRig(
    val id: String,
    val name: String,
    /** Distance from the RKB datum down to the drill-floor walking surface (rig data). */
    val floorBelowRkb: Length? = null,
    /** Clear height above GL under the rotary support beams (substructure data). */
    val clearHeightUnderRotaryBeams: Length? = null,
    /** Substructure width across the well in the elevation view (rig data). */
    val substructureWidth: Length? = null,
    val provenance: DataProvenance,
)

/** Rig and drilling BOP stack rigged up on the wellhead for the drilling state. */
@Serializable
data class DrillingRigUp(
    val rig: DrillingRig,
    val bopStack: DrillingBopStack,
)
