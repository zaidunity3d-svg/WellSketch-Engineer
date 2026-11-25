package com.wellsketch.engineer.core.model.well

import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lifecycle state of a completion. As-designed, as-built and current configurations are
 * kept as separate records and are never merged implicitly.
 */
@Serializable
enum class CompletionState(val label: String) {
    AS_DESIGNED("As-designed"),
    AS_BUILT("As-built"),
    CURRENT("Current"),
}

/**
 * Whether production tubing is present. Production tubing is the default; "no tubing"
 * must be stated explicitly (with a reason) so that a missing record is never confused
 * with a tubingless completion.
 */
@Serializable
sealed interface TubingConfiguration {
    @Serializable
    @SerialName("INSTALLED")
    data class Installed(val tubing: TubingString) : TubingConfiguration

    @Serializable
    @SerialName("EXPLICITLY_NONE")
    data class ExplicitlyNone(val reason: String) : TubingConfiguration
}

@Serializable
enum class CompletionComponentType(val label: String) {
    TUBING_HANGER("Tubing hanger"),
    SAFETY_VALVE("Subsurface safety valve"),
    GAS_LIFT_MANDREL("Gas lift mandrel"),
    SLIDING_SLEEVE("Sliding sleeve"),
    LANDING_NIPPLE("Landing nipple"),
    PACKER("Packer"),
    ESP("Electric submersible pump"),
    BRIDGE_PLUG("Bridge plug"),
    CEMENT_RETAINER("Cement retainer"),
    WIRELINE_ENTRY_GUIDE("Wireline entry guide"),
    OTHER("Other"),
}

/** Where a component is mounted. */
@Serializable
enum class ComponentMounting(val label: String) {
    /** Run as part of the tubing string (packer, nipple, sleeve, ESP...). */
    ON_TUBING("On tubing"),

    /** Set directly in casing/liner (e.g. bridge plug below the tubing). */
    IN_CASING("In casing/liner"),
}

/**
 * A completion component placed at a depth. Dimensions are optional because they must
 * come from validated sources; missing dimensions are reported by validators instead of
 * being guessed. [minimumInnerDiameter] = null with [isFullBoreBlocking] = true models a
 * plug that blocks the bore.
 */
@Serializable
data class CompletionComponent(
    val id: String,
    val type: CompletionComponentType,
    val name: String,
    val top: MeasuredDepth,
    val length: Length,
    val mounting: ComponentMounting,
    val outerDiameter: Length? = null,
    val minimumInnerDiameter: Length? = null,
    val isFullBoreBlocking: Boolean = false,
    val catalogItemId: String? = null,
    val provenance: DataProvenance,
)

@Serializable
enum class PerforationStatus(val label: String) {
    OPEN("Open"),
    SQUEEZED("Squeezed"),
    ISOLATED("Isolated"),
    PLANNED("Planned"),
}

@Serializable
data class PerforationInterval(
    val id: String,
    val top: MeasuredDepth,
    val bottom: MeasuredDepth,
    /** Casing/liner string that is perforated. */
    val hostStringId: String,
    val status: PerforationStatus,
    val provenance: DataProvenance,
)

/** One completion configuration for a given [state]. */
@Serializable
data class Completion(
    val id: String,
    val state: CompletionState,
    val name: String,
    val tubing: TubingConfiguration,
    val components: List<CompletionComponent> = emptyList(),
    val perforations: List<PerforationInterval> = emptyList(),
    val provenance: DataProvenance,
) {
    val tubingString: TubingString? get() = (tubing as? TubingConfiguration.Installed)?.tubing
}
