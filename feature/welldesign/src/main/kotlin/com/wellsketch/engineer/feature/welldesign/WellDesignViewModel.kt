package com.wellsketch.engineer.feature.welldesign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wellsketch.engineer.core.data.repository.ProjectAutosaver
import com.wellsketch.engineer.core.data.repository.WellProjectRepository
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.schematic.SurfaceBlock
import com.wellsketch.engineer.domain.welldesign.EditOutcome
import com.wellsketch.engineer.domain.welldesign.SurfaceMode
import com.wellsketch.engineer.domain.welldesign.WellDesignSession
import com.wellsketch.engineer.domain.welldesign.WellDesignSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WellDesignUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val project: WellProject? = null,
    val snapshot: WellDesignSnapshot? = null,
    val selectedSurfaceBlock: SurfaceBlock? = null,
    val message: String? = null,
)

sealed interface WellDesignEvent {
    data class SelectCompletionState(val state: CompletionState) : WellDesignEvent
    data class SelectSurfaceMode(val mode: SurfaceMode) : WellDesignEvent
    data class SelectElement(val elementId: String?) : WellDesignEvent
    data class SelectSurfaceBlock(val block: SurfaceBlock) : WellDesignEvent
    data object ClearSelection : WellDesignEvent
    data object DismissMessage : WellDesignEvent

    /** A pure edit of the well (see WellEdits); applied immediately and autosaved. */
    data class Edit(val transform: (Well) -> EditOutcome) : WellDesignEvent
}

/** What the user asked the sketch to show: completion state and surface configuration. */
private data class SketchRequest(
    val completionState: CompletionState? = null,
    val surfaceMode: SurfaceMode? = null,
)

/**
 * Holds the edited project and derives the live schematic on every change. The scene is
 * rebuilt synchronously from the latest inputs; no animation or interpolation is used.
 */
class WellDesignViewModel(
    private val projectId: String,
    private val repository: WellProjectRepository,
    private val autosaver: ProjectAutosaver,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val session = WellDesignSession()
    private val project = MutableStateFlow<WellProject?>(null)
    private val requested = MutableStateFlow(SketchRequest())
    private val selectedElementId = MutableStateFlow<String?>(null)
    private val selectedBlock = MutableStateFlow<SurfaceBlock?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val loadError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WellDesignUiState> =
        combine(project, requested, selectedElementId, selectedBlock, message) { p, request, elementId, block, msg ->
            if (p == null) {
                WellDesignUiState(loading = true)
            } else {
                WellDesignUiState(
                    loading = false,
                    project = p,
                    snapshot = session.snapshot(
                        p.well, request.completionState, elementId, request.surfaceMode,
                        ctRigUp = p.ctOperations.firstOrNull()?.rigUp,
                    ),
                    selectedSurfaceBlock = block,
                    message = msg,
                )
            }
        }
            .combine(loadError) { state, error -> if (error != null) state.copy(loading = false, error = error) else state }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WellDesignUiState())

    init {
        viewModelScope.launch {
            runCatching { repository.getProject(projectId) }
                .onSuccess { loaded -> if (loaded == null) loadError.value = "Project not found." else project.value = loaded }
                .onFailure { loadError.value = "Could not load project: ${it.message}" }
        }
    }

    fun onEvent(event: WellDesignEvent) {
        when (event) {
            is WellDesignEvent.SelectCompletionState -> requested.value = requested.value.copy(completionState = event.state)
            is WellDesignEvent.SelectSurfaceMode -> {
                requested.value = requested.value.copy(surfaceMode = event.mode)
                selectedBlock.value = null
            }
            is WellDesignEvent.SelectElement -> {
                selectedElementId.value = event.elementId
                selectedBlock.value = null
            }
            is WellDesignEvent.SelectSurfaceBlock -> {
                selectedBlock.value = event.block
                selectedElementId.value = null
            }
            WellDesignEvent.ClearSelection -> {
                selectedElementId.value = null
                selectedBlock.value = null
            }
            WellDesignEvent.DismissMessage -> message.value = null
            is WellDesignEvent.Edit -> applyEdit(event.transform)
        }
    }

    private fun applyEdit(transform: (Well) -> EditOutcome) {
        val current = project.value ?: return
        when (val outcome = transform(current.well)) {
            is EditOutcome.Applied -> {
                val updated = current.copy(well = outcome.well, lastModifiedEpochMillis = clock())
                project.value = updated
                autosaver.submit(updated)
            }
            is EditOutcome.Rejected -> message.value = outcome.reason
        }
    }
}
