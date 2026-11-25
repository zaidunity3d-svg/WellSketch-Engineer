package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.schematic.SchematicElement
import com.wellsketch.engineer.core.schematic.SchematicScene
import com.wellsketch.engineer.core.schematic.WellSchematicBuilder

/** Snapshot of the Well Design module for one well and selected completion state. */
data class WellDesignSnapshot(
    val well: Well,
    val completionState: CompletionState,
    val availableStates: List<CompletionState>,
    val scene: SchematicScene,
    val selected: SchematicElement?,
)

/**
 * Derives the live schematic for the Well Design screen. Stateless: call [snapshot] after
 * every edit; there is no caching or animation between snapshots.
 */
class WellDesignSession(private val builder: WellSchematicBuilder = WellSchematicBuilder()) {

    fun snapshot(well: Well, requestedState: CompletionState?, selectedElementId: String?): WellDesignSnapshot {
        val available = CompletionState.entries.filter { well.completion(it) != null }
        val state = requestedState?.takeIf { it in available } ?: available.lastOrNull() ?: CompletionState.CURRENT
        val scene = builder.build(well, state)
        return WellDesignSnapshot(
            well = well,
            completionState = state,
            availableStates = available,
            scene = scene,
            selected = selectedElementId?.let { id -> scene.elements.firstOrNull { it.id == id } },
        )
    }
}
