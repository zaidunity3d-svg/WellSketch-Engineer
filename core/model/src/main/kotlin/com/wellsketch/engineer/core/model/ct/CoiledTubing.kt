package com.wellsketch.engineer.core.model.ct

import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.surface.CtPressureControlStack
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import kotlinx.serialization.Serializable

/**
 * One section of a (possibly tapered) coiled-tubing string. OD and wall thickness are
 * stored as given by the CT string data sheet.
 */
@Serializable
data class CtStringSection(
    val length: Length,
    val outerDiameter: Length,
    val wallThickness: Length,
    val grade: String? = null,
    val provenance: DataProvenance,
) {
    init {
        require(length.meters > 0.0) { "CT section length must be positive." }
        require(wallThickness.meters > 0.0 && wallThickness * 2.0 < outerDiameter) {
            "CT wall thickness must be positive and less than half the OD."
        }
    }

    /**
     * Nominal ID from the geometric identity ID = OD − 2 × wall. This is the nominal
     * value implied by the data sheet, not a measured ID.
     */
    val nominalInnerDiameter: Length get() = outerDiameter - wallThickness * 2.0
}

/** Coiled-tubing string; [sections] are ordered from the downhole (free) end to the reel core. */
@Serializable
data class CtString(
    val id: String,
    val name: String,
    val sections: List<CtStringSection>,
    val catalogItemId: String? = null,
    val provenance: DataProvenance,
) {
    init {
        require(sections.isNotEmpty()) { "A CT string needs at least one section." }
    }

    val totalLength: Length get() = Length(sections.sumOf { it.length.meters })
    val maxOuterDiameter: Length get() = sections.maxOf { it.outerDiameter }
}

@Serializable
enum class CtBhaComponentType(val label: String) {
    CT_CONNECTOR("CT connector"),
    CHECK_VALVE("Check valve assembly"),
    HYDRAULIC_DISCONNECT("Hydraulic disconnect"),
    CIRCULATING_SUB("Circulating sub"),
    JAR("Jar"),
    MOTOR("Downhole motor"),
    MILL("Mill"),
    JETTING_NOZZLE("Jetting nozzle"),
    LOGGING_TOOL("Logging tool"),
    CENTRALIZER("Centralizer"),
    OTHER("Other"),
}

/** One BHA component. Dimensions must come from the tool data sheet. */
@Serializable
data class CtBhaComponent(
    val id: String,
    val type: CtBhaComponentType,
    val name: String,
    val length: Length,
    val maxOuterDiameter: Length,
    val innerDiameter: Length? = null,
    val catalogItemId: String? = null,
    val provenance: DataProvenance,
)

/** CT bottom-hole assembly; [components] are ordered from the top (CT connector) down to the nose. */
@Serializable
data class CtBha(
    val id: String,
    val name: String,
    val components: List<CtBhaComponent>,
    val provenance: DataProvenance,
) {
    val totalLength: Length get() = Length(components.sumOf { it.length.meters })
    val maxOuterDiameter: Length? get() = components.maxOfOrNull { it.maxOuterDiameter }
}

/** A named piece of CT surface equipment shown in the full surface view. */
@Serializable
data class CtSurfaceUnit(
    val name: String,
    val catalogItemId: String? = null,
    val provenance: DataProvenance,
)

/** CT surface spread. Units are optional so partial rig-ups can be described honestly. */
@Serializable
data class CtSurfaceEquipment(
    val reel: CtSurfaceUnit? = null,
    val guideArch: CtSurfaceUnit? = null,
    val injectorHead: CtSurfaceUnit? = null,
    val controlCabin: CtSurfaceUnit? = null,
    val powerPack: CtSurfaceUnit? = null,
)

/** Everything rigged up on the tree for a CT operation. */
@Serializable
data class CtRigUp(
    val pressureControlStack: CtPressureControlStack,
    val surfaceEquipment: CtSurfaceEquipment,
)

/** Convention for which point of the CT string the depth counter tracks. */
@Serializable
enum class CtDepthTrackingPoint(val label: String) {
    /** Counter zeroed with the BHA nose at the operation datum; depth = BHA nose depth. */
    BHA_NOSE("BHA nose (bottom of BHA)"),
}

/**
 * A coiled-tubing operation on a well. The depth reference is chosen per operation and
 * must be an MD-capable reference (RKB, GL or an explicit operation datum).
 */
@Serializable
data class CtOperation(
    val id: String,
    val name: String,
    val objective: String,
    val depthReference: DepthReference,
    val depthTrackingPoint: CtDepthTrackingPoint = CtDepthTrackingPoint.BHA_NOSE,
    /** Current CT depth (tracking point) relative to [depthReference]. */
    val currentDepth: Length,
    val ctString: CtString,
    val bha: CtBha,
    val rigUp: CtRigUp,
    /** Maximum expected wellhead pressure for rating checks; null if not yet specified. */
    val maxExpectedWellheadPressure: Pressure? = null,
    val provenance: DataProvenance,
) {
    init {
        require(depthReference !is DepthReference.MeanSeaLevel) { "CT depth cannot be referenced to MSL." }
    }
}
