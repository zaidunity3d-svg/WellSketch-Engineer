package com.wellsketch.engineer.feature.workover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wellsketch.engineer.core.data.repository.ProjectAutosaver
import com.wellsketch.engineer.core.data.repository.WellProjectRepository
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.model.workover.WorkoverOperation
import com.wellsketch.engineer.core.model.workover.WorkoverOperationType
import com.wellsketch.engineer.core.schematic.SchematicElement
import com.wellsketch.engineer.core.schematic.SurfaceBlock
import com.wellsketch.engineer.domain.workover.WorkoverEdits
import com.wellsketch.engineer.domain.workover.WorkoverJob
import com.wellsketch.engineer.domain.workover.WorkoverJobResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoverUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val project: WellProject? = null,
    val job: WorkoverOperation? = null,
    val result: WorkoverJobResult? = null,
    val selectedElement: SchematicElement? = null,
    val selectedBlock: SurfaceBlock? = null,
)

sealed interface WorkoverEvent {
    data class SelectJob(val jobId: String) : WorkoverEvent
    data class NewJob(val type: WorkoverOperationType) : WorkoverEvent
    data object DeleteJob : WorkoverEvent

    /** Any change to the selected job; [transform] receives the well for edits that need it. */
    data class Edit(val transform: (Well, WorkoverOperation) -> WorkoverOperation) : WorkoverEvent
    data class SelectElement(val element: SchematicElement?) : WorkoverEvent
    data class SelectBlock(val block: SurfaceBlock) : WorkoverEvent
    data object ClearSelection : WorkoverEvent
}

/**
 * Workover module state. Every edit produces a new calculation and sketch synchronously; the
 * string and tools move to their new depth at once (no animation). Changes are autosaved.
 */
class WorkoverViewModel(
    private val projectId: String,
    private val repository: WellProjectRepository,
    private val autosaver: ProjectAutosaver,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val runner = WorkoverJob()
    private val project = MutableStateFlow<WellProject?>(null)
    private val selectedJobId = MutableStateFlow<String?>(null)
    private val selectedElement = MutableStateFlow<SchematicElement?>(null)
    private val selectedBlock = MutableStateFlow<SurfaceBlock?>(null)
    private val loadError = MutableStateFlow<String?>(null)
    private var idCounter = 0

    val uiState: StateFlow<WorkoverUiState> =
        combine(project, selectedJobId, selectedElement, selectedBlock, loadError) { p, jobId, element, block, error ->
            when {
                error != null -> WorkoverUiState(loading = false, error = error)
                p == null -> WorkoverUiState(loading = true)
                else -> {
                    val job = p.workoverOperations.firstOrNull { it.id == jobId } ?: p.workoverOperations.firstOrNull()
                    WorkoverUiState(
                        loading = false,
                        project = p,
                        job = job,
                        result = job?.let { runner.run(p.well, it) },
                        selectedElement = element,
                        selectedBlock = block,
                    )
                }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoverUiState())

    init {
        viewModelScope.launch {
            runCatching { repository.getProject(projectId) }
                .onSuccess { loaded -> if (loaded == null) loadError.value = "Project not found." else project.value = loaded }
                .onFailure { loadError.value = "Could not load project: ${it.message}" }
        }
    }

    fun onEvent(event: WorkoverEvent) {
        when (event) {
            is WorkoverEvent.SelectJob -> {
                selectedJobId.value = event.jobId
                clearSelection()
            }
            is WorkoverEvent.NewJob -> {
                val current = project.value ?: return
                val job = WorkoverEdits.newJob(current.well, event.type, newId("wo"))
                save(WorkoverEdits.addJob(current, job))
                selectedJobId.value = job.id
                clearSelection()
            }
            WorkoverEvent.DeleteJob -> {
                val current = project.value ?: return
                val job = currentJob(current) ?: return
                save(WorkoverEdits.removeJob(current, job.id))
                selectedJobId.value = null
                clearSelection()
            }
            is WorkoverEvent.Edit -> {
                val current = project.value ?: return
                val job = currentJob(current) ?: return
                val updated = event.transform(current.well, job)
                if (updated != job) save(WorkoverEdits.replaceJob(current, updated))
            }
            is WorkoverEvent.SelectElement -> {
                selectedElement.value = event.element
                selectedBlock.value = null
            }
            is WorkoverEvent.SelectBlock -> {
                selectedBlock.value = event.block
                selectedElement.value = null
            }
            WorkoverEvent.ClearSelection -> clearSelection()
        }
    }

    /** Unique ids for new sections, tools and BOP components. */
    fun newId(prefix: String): String = "$prefix-${clock()}-${idCounter++}"

    private fun currentJob(p: WellProject) =
        p.workoverOperations.firstOrNull { it.id == selectedJobId.value } ?: p.workoverOperations.firstOrNull()

    private fun clearSelection() {
        selectedElement.value = null
        selectedBlock.value = null
    }

    private fun save(updated: WellProject) {
        val stamped = updated.copy(lastModifiedEpochMillis = clock())
        project.value = stamped
        autosaver.submit(stamped)
    }
}
