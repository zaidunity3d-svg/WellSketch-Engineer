package com.wellsketch.engineer.core.model.workover

import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.surface.DrillingBopStack
import com.wellsketch.engineer.core.model.units.Density
import com.wellsketch.engineer.core.model.units.FlowRate
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.model.units.PressureGradient
import com.wellsketch.engineer.core.model.units.Volume
import com.wellsketch.engineer.core.model.units.VolumePerLength
import com.wellsketch.engineer.core.model.well.TubularSpec
import kotlinx.serialization.Serializable

/**
 * Workover on a completed well with a workover rig: the Christmas Tree is removed, a workover
 * BOP stack is nippled up on the wellhead and the work string (drill pipe or tubing) is run
 * from the rig floor. The production tubing is assumed pulled: the work string runs inside
 * the production casing / liner. All values are user or data-sheet entries; nothing here is
 * a default rating.
 */
@Serializable
enum class WorkoverOperationType(val label: String) {
    PERFORATION("Perforation"),
    CEMENT_PLUG("Cement plug (balanced)"),
    SET_PACKER("Set packer"),
    ESP_CHANGE("ESP change"),
    ACIDIZING("Acidizing / stimulation"),
    WELL_KILL("Well kill"),
    CIRCULATION("Circulation / clean-out"),
    OTHER("Other"),
}

@Serializable
enum class WorkStringPipeKind(val label: String) {
    DRILL_PIPE("Drill pipe"),
    TUBING("Tubing"),
}

/**
 * One section of the work string, top to bottom. The top section runs up to the rig floor and
 * its length is calculated from the bottom depth; the others use [length].
 */
@Serializable
data class WorkStringSection(
    val id: String,
    val kind: WorkStringPipeKind,
    val name: String,
    val spec: TubularSpec,
    val length: Length,
    /** Tool-joint / coupling OD for clearance checks, from the data sheet; null = pipe OD only. */
    val connectionOuterDiameter: Length? = null,
)

@Serializable
data class WorkString(
    val id: String,
    val name: String,
    val sections: List<WorkStringSection>,
    val provenance: DataProvenance,
)

@Serializable
enum class WorkoverToolType(val label: String) {
    PACKER("Packer"),
    BRIDGE_PLUG("Bridge plug / retainer"),
    PERFORATING_GUN("Perforating gun"),
    CEMENT_STINGER("Cement stinger / mule shoe"),
    ESP_PUMP("ESP pump"),
    ESP_INTAKE("ESP intake / gas separator"),
    ESP_SEAL("ESP seal / protector"),
    ESP_MOTOR("ESP motor"),
    ESP_SENSOR("ESP sensor"),
    SCRAPER("Casing scraper"),
    BIT_MILL("Bit / mill"),
    NIPPLE("Landing nipple"),
    CROSSOVER("Crossover"),
    OTHER("Other"),
}

/** A tool run below the work string; values from its data sheet. */
@Serializable
data class WorkoverTool(
    val id: String,
    val type: WorkoverToolType,
    val name: String,
    val length: Length,
    val maxOuterDiameter: Length,
    /** Through bore; null = closed (e.g. a bridge plug or a gun) for volume purposes. */
    val innerDiameter: Length? = null,
    val catalogItemId: String? = null,
    val provenance: DataProvenance,
)

/** Workover rig data used for the surface view. */
@Serializable
data class WorkoverRig(
    val id: String,
    val name: String,
    /** Distance from the workover RKB down to the rig floor walking surface (rig data). */
    val floorBelowRkb: Length? = null,
    val provenance: DataProvenance,
)

/** Workover rig and BOP stack nippled up on the wellhead (tree removed). */
@Serializable
data class WorkoverRigUp(
    val rig: WorkoverRig,
    /** Workover BOP stack, top to bottom (same component types as a drilling BOP). */
    val bopStack: DrillingBopStack,
    /** Wellhead section the BOP is nippled up on; null = top section. */
    val nippledUpOnSectionId: String? = null,
)

/**
 * A workover job. Depths are relative to [depthReference], normally the workover rig's own
 * RKB ([DepthReference.Operation] with its height above GL): the workover RKB is not the
 * drilling RKB, and the conversion to well MD uses the explicit offset.
 */
@Serializable
data class WorkoverOperation(
    val id: String,
    val name: String,
    val type: WorkoverOperationType,
    val objective: String = "",
    val depthReference: DepthReference,
    /** Depth of the bottom of the lowest tool (or of the string end if there is no tool). */
    val bottomDepth: Length,
    val workString: WorkString,
    /** Tools below the work string, top to bottom. */
    val tools: List<WorkoverTool> = emptyList(),
    val rigUp: WorkoverRigUp,
    /** Interval treated / perforated / plugged (relative to [depthReference]). */
    val targetTop: Length? = null,
    val targetBottom: Length? = null,
    val wellFluidDensity: Density? = null,
    val pumpedFluidDensity: Density? = null,
    val pumpRate: FlowRate? = null,
    val reservoirPressure: Pressure? = null,
    /** Overbalance wanted above reservoir pressure for a kill fluid. */
    val killOverbalance: Pressure? = null,
    val fractureGradient: PressureGradient? = null,
    // Perforation
    val shotDensityPerMeter: Double? = null,
    val gunLengthPerRun: Length? = null,
    // Cement plug
    val plugLength: Length? = null,
    val slurryExcessFraction: Double = 0.0,
    val spacerAheadVolume: Volume? = null,
    val underDisplacementVolume: Volume? = null,
    // Acidizing
    val treatmentVolumePerLength: VolumePerLength? = null,
    val overflushVolume: Volume? = null,
    val provenance: DataProvenance,
)
