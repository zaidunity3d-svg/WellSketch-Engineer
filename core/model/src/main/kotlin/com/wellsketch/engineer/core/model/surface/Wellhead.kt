package com.wellsketch.engineer.core.model.surface

import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import kotlinx.serialization.Serializable

/** Side of the well centerline, as seen in the schematic elevation view. */
@Serializable
enum class OutletSide(val label: String, val sign: Int) {
    LEFT("Left", -1),
    RIGHT("Right", 1),
}

/** Fitting on a side outlet. Lists are ordered from the body outward. */
@Serializable
enum class OutletFitting(val label: String) {
    COMPANION_FLANGE("Companion flange"),
    GATE_VALVE("Gate valve (manual)"),
    ACTUATED_GATE_VALVE("Gate valve (actuated)"),
    NEEDLE_VALVE_GAUGE("Needle valve and gauge"),
    BLIND_FLANGE("Blind flange"),
    BULL_PLUG("Bull plug / VR plug"),
    LINE_CONNECTION("Line connection"),
}

/**
 * A side outlet on a wellhead section or a drilling BOP component (annulus access,
 * choke or kill line). Sizes and ratings must come from the equipment data sheet.
 */
@Serializable
data class SideOutlet(
    val id: String,
    val side: OutletSide,
    /** Fittings from the body outward, e.g. companion flange, gate valve, line connection. */
    val fittings: List<OutletFitting>,
    val nominalSize: Length? = null,
    val ratedWorkingPressure: Pressure? = null,
    val label: String? = null,
    val provenance: DataProvenance,
)

@Serializable
enum class WellheadSectionType(val label: String) {
    CASING_HEAD_HOUSING("Casing head housing"),
    CASING_SPOOL("Casing spool"),
    TUBING_HEAD_SPOOL("Tubing head spool"),
    OTHER("Wellhead section"),
}

/**
 * One section of a spool-type wellhead. [height] is the flange-to-flange (or base-to-top
 * flange) height from the data sheet; when absent, drawings use a flagged placeholder and
 * elevation calculations above this section are not computed.
 */
@Serializable
data class WellheadSection(
    val id: String,
    val type: WellheadSectionType,
    val name: String,
    val topConnection: EndConnection,
    /** Bottom end, e.g. the casing connection of a casing head housing or a flange. */
    val bottomConnection: EndConnection,
    val bore: Length? = null,
    val ratedWorkingPressure: Pressure? = null,
    val height: Length? = null,
    /** Id of the casing string (or tubing) hung in this section's bowl, if recorded. */
    val suspendedStringId: String? = null,
    val sideOutlets: List<SideOutlet> = emptyList(),
    val catalogItemId: String? = null,
    val provenance: DataProvenance,
)

/** Spool-type wellhead, sections ordered from top to bottom (same order as the tree). */
@Serializable
data class Wellhead(
    val id: String,
    val name: String,
    val sections: List<WellheadSection>,
    /**
     * Elevation of the base of the lowest section relative to ground level, as measured on
     * location (negative = below GL, e.g. inside the cellar). When null, the base is assumed
     * to sit on the cellar floor and the assumption is reported.
     */
    val baseRelativeToGroundLevel: Length? = null,
    /**
     * True when the lowest section sits on a landing base (base plate) carried by the
     * conductor; the base top is at [baseRelativeToGroundLevel].
     */
    val hasLandingBase: Boolean = false,
    val provenance: DataProvenance,
)

/** Concrete cellar pit around the wellhead. */
@Serializable
data class Cellar(
    /** Depth of the cellar floor below ground level (positive downward). */
    val depthBelowGroundLevel: Length,
    /** Clear inside width; drawings use a flagged placeholder when null. */
    val insideWidth: Length? = null,
    val provenance: DataProvenance,
)
