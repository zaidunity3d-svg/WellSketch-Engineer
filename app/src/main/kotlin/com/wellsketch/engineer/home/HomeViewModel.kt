package com.wellsketch.engineer.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wellsketch.engineer.core.data.repository.WellProjectRepository
import com.wellsketch.engineer.core.model.project.WellProjectSummary
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.WellLocationType
import com.wellsketch.engineer.domain.welldesign.WellProjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val projects: List<WellProjectSummary> = emptyList(),
    val activeProject: WellProjectSummary? = null,
)

class HomeViewModel(
    private val repository: WellProjectRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val selectedId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> =
        combine(repository.observeProjectSummaries(), selectedId) { projects, id ->
            HomeUiState(
                loading = false,
                projects = projects,
                activeProject = projects.firstOrNull { it.id == id } ?: projects.firstOrNull(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectProject(id: String) {
        selectedId.value = id
    }

    /**
     * Creates a blank user well (datums and one formation only; no casing, no demo data),
     * stores it, makes it active and reports its id once saved.
     */
    fun createWell(
        name: String,
        rkbAboveGround: Length,
        groundElevation: Length,
        locationType: WellLocationType,
        onCreated: (String) -> Unit,
    ) {
        val project = WellProjects.newWell(name, rkbAboveGround, groundElevation, locationType, nowMillis = clock())
        viewModelScope.launch {
            repository.saveProject(project)
            selectedId.value = project.id
            onCreated(project.id)
        }
    }

    /** Saves an independent copy of the project and makes the copy active. */
    fun duplicate(id: String) {
        viewModelScope.launch {
            val source = repository.getProject(id) ?: return@launch
            val copy = WellProjects.duplicate(source, clock())
            repository.saveProject(copy)
            selectedId.value = copy.id
        }
    }

    /** Deletes the project from this device (the UI asks for confirmation first). */
    fun delete(id: String) {
        viewModelScope.launch {
            repository.deleteProject(id)
            if (selectedId.value == id) selectedId.value = null
        }
    }
}
