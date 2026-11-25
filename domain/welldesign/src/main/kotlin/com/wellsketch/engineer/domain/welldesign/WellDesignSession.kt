package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.engineering.surface.SurfaceStackup
import com.wellsketch.engineer.core.engineering.surface.SurfaceStackupCalculator
import com.wellsketch.engineer.core.model.ct.CtRigUp
import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.schematic.CtSurfaceViewMode
import com.wellsketch.engineer.core.schematic.SchematicElement
import com.wellsketch.engineer.core.schematic.SchematicScene
import com.wellsketch.engineer.core.schematic.WellSchematicBuilder

/**
 * Surface configuration shown on the Well Design sketch. Each mode maps to exactly one
 * [OperatingState], so a drilling rig/BOP and a Christmas Tree are never shown together.
 */
enum class SurfaceMode(val label: String) {
    /** Rig floor and substructure, drilling BOP stack on the wellhead in the cellar; no tree. */
    DRILLING("Drilling"),

    /** Christmas Tree on the wellhead; no rig and no drilling BOP. */
    COMPLETION("Completion – tree"),

    /** Christmas Tree with the CT pressure-control stack and injector; never the drilling BOP. */
    COILED_TUBING("CT intervention"),
}

/** Snapshot of the Well Design module for one well and selected completion state. */
data class WellDesignSnapshot(
    val well: Well,
    val completionState: CompletionState,
    val availableStates: List<CompletionState>,
    val scene: SchematicScene,
    val selected: SchematicElement?,
    val surfaceMode: SurfaceMode? = null,
    val availableSurfaceModes: List<SurfaceMode> = emptyList(),
    val surfaceStackup: SurfaceStackup? = null,
) {
    /** True when the completion is drawn (not in the drilling state). */
    val showsCompletion: Boolean get() = surfaceMode != SurfaceMode.DRILLING
}

/**
 * Derives the live schematic for the Well Design screen. Stateless: call [snapshot] after
 * every edit; there is no caching or animation between snapshots.
 */
class WellDesignSession(
    private val builder: WellSchematicBuilder = WellSchematicBuilder(),
    private val stackupCalculator: SurfaceStackupCalculator = SurfaceStackupCalculator(),
) {

    /**
     * @param requestedSurfaceMode surface configuration to show; falls back to the completed
     *   tree (previous behaviour) or the first available mode.
     * @param ctRigUp CT rig-up of the project's CT operation, enabling [SurfaceMode.COILED_TUBING].
     */
    fun snapshot(
        well: Well,
        requestedState: CompletionState?,
        selectedElementId: String?,
        requestedSurfaceMode: SurfaceMode? = null,
        ctRigUp: CtRigUp? = null,
        ctViewMode: CtSurfaceViewMode = CtSurfaceViewMode.COMPACT,
    ): WellDesignSnapshot {
        val available = CompletionState.entries.filter { well.completion(it) != null }
        val state = requestedState?.takeIf { it in available } ?: available.lastOrNull() ?: CompletionState.CURRENT
        val modes = availableSurfaceModes(well, ctRigUp)
        val mode = requestedSurfaceMode?.takeIf { it in modes }
            ?: SurfaceMode.COMPLETION.takeIf { it in modes }
            ?: modes.firstOrNull()
        val operatingState = operatingState(well, mode, ctRigUp)
        // The drilling state has no completion installed: draw casing and hole only.
        val drawnState = if (mode == SurfaceMode.DRILLING) null else state
        val scene = builder.build(well, drawnState, operatingState, ctViewMode)
        return WellDesignSnapshot(
            well = well,
            completionState = state,
            availableStates = available,
            scene = scene,
            selected = selectedElementId?.let { id -> scene.elements.firstOrNull { it.id == id } },
            surfaceMode = mode,
            availableSurfaceModes = modes,
            surfaceStackup = if (operatingState == OperatingState.SurfaceNotDefined) null else stackupCalculator.calculate(well, operatingState),
        )
    }

    companion object {
        fun availableSurfaceModes(well: Well, ctRigUp: CtRigUp?): List<SurfaceMode> = buildList {
            if (well.drillingRigUp != null) add(SurfaceMode.DRILLING)
            if (well.christmasTree != null) add(SurfaceMode.COMPLETION)
            if (well.christmasTree != null && ctRigUp != null) add(SurfaceMode.COILED_TUBING)
        }

        fun operatingState(well: Well, mode: SurfaceMode?, ctRigUp: CtRigUp?): OperatingState {
            val tree = well.christmasTree
            val rig = well.drillingRigUp
            return when {
                mode == SurfaceMode.DRILLING && rig != null -> OperatingState.Drilling(rig)
                mode == SurfaceMode.COILED_TUBING && tree != null && ctRigUp != null -> OperatingState.CoiledTubingIntervention(tree, ctRigUp)
                mode == SurfaceMode.COMPLETION && tree != null -> OperatingState.CompletedWithTree(tree)
                else -> OperatingState.SurfaceNotDefined
            }
        }
    }
}
