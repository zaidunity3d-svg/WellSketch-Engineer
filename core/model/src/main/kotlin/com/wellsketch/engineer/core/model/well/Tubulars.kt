package com.wellsketch.engineer.core.model.well

import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.LinearMass
import kotlinx.serialization.Serializable

/**
 * Geometry of a tubular (casing, liner or tubing) as stated by its data source.
 *
 * ID, wall and weight are stored explicitly as provided by a validated catalog or a
 * measurement; they are never derived from nominal size by this app. [driftDiameter] is
 * optional and, when missing, restriction checks must report that ID was used instead.
 */
@Serializable
data class TubularSpec(
    val outerDiameter: Length,
    val innerDiameter: Length,
    val wallThickness: Length? = null,
    val driftDiameter: Length? = null,
    val nominalLinearMass: LinearMass? = null,
    val grade: String? = null,
    val connection: String? = null,
    /** Identifier of the equipment-library entry this spec was taken from, if any. */
    val catalogItemId: String? = null,
    val provenance: DataProvenance,
) {
    init {
        require(outerDiameter.meters > 0.0) { "Outer diameter must be positive." }
        require(innerDiameter.meters > 0.0) { "Inner diameter must be positive." }
        require(innerDiameter < outerDiameter) { "Inner diameter must be smaller than outer diameter." }
        driftDiameter?.let { require(it <= innerDiameter) { "Drift diameter cannot exceed inner diameter." } }
    }
}

@Serializable
enum class CasingRole(val label: String, val isLiner: Boolean) {
    CONDUCTOR("Conductor", false),
    SURFACE("Surface casing", false),
    INTERMEDIATE("Intermediate casing", false),
    PRODUCTION("Production casing", false),
    INTERMEDIATE_LINER("Intermediate liner", true),
    PRODUCTION_LINER("Production liner", true),
    TIEBACK("Tie-back", false),
}

@Serializable
enum class ShoeType(val label: String) {
    GUIDE_SHOE("Guide shoe"),
    FLOAT_SHOE("Float shoe"),
    REAMER_SHOE("Reamer shoe"),
    UNSPECIFIED("Unspecified"),
}

@Serializable
data class CasingShoe(
    val depth: MeasuredDepth,
    val type: ShoeType = ShoeType.UNSPECIFIED,
    val floatCollarDepth: MeasuredDepth? = null,
    val catalogItemId: String? = null,
)

/** Reliability level of a cement interval. */
@Serializable
enum class CementStatus(val label: String) {
    DESIGNED("Designed"),
    REPORTED_AS_BUILT("As-built (job report)"),
    LOG_EVALUATED("Log evaluated (e.g. CBL/USIT)"),
}

/**
 * Cement in the annulus outside its casing string between [top] (top of cement) and
 * [bottom] (normally the shoe). The outer boundary (open hole or the previous casing) is
 * resolved geometrically by the schematic and calculation layers, not stored here.
 *
 * [tailTop], when recorded, splits the column into a lead slurry ([top] to [tailTop]) and a
 * tail slurry ([tailTop] to [bottom]), as in the HTML v0.9 cementing program.
 */
@Serializable
data class CementSheath(
    val top: MeasuredDepth,
    val bottom: MeasuredDepth,
    val status: CementStatus,
    val description: String? = null,
    val provenance: DataProvenance,
    val tailTop: MeasuredDepth? = null,
)

/**
 * A casing string or liner. A casing string normally hangs from the wellhead ([top] near
 * surface); a liner hangs from a liner hanger inside the previous string, so its [top] is
 * deep and overlaps the previous string.
 *
 * Tapered strings (multiple specs along the string) are a planned extension.
 */
@Serializable
data class CasingString(
    val id: String,
    val name: String,
    val role: CasingRole,
    val spec: TubularSpec,
    /** Top of the string: wellhead hanger for casing, liner hanger/top for liners. */
    val top: MeasuredDepth,
    val shoe: CasingShoe,
    val cement: CementSheath? = null,
    val provenance: DataProvenance,
    /**
     * Liners: the drill pipe or tubing the liner is run and cemented on. Used for cement placed
     * above the liner top (around this string, inside the host casing) and for the displacement
     * from surface to the liner top. Null = not entered.
     */
    val runningString: TubularSpec? = null,
) {
    val isLiner: Boolean get() = role.isLiner
}

/** Production tubing string, from the tubing hanger to the end of tubing. */
@Serializable
data class TubingString(
    val id: String,
    val name: String,
    val spec: TubularSpec,
    val hangerDepth: MeasuredDepth,
    val endOfTubing: MeasuredDepth,
    val provenance: DataProvenance,
)
