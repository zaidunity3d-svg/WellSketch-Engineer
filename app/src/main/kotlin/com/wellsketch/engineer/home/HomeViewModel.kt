package com.wellsketch.engineer.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wellsketch.engineer.core.data.repository.WellProjectRepository
import com.wellsketch.engineer.core.model.project.WellProjectSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val projects: List<WellProjectSummary> = emptyList(),
    val activeProject: WellProjectSummary? = null,
)

class HomeViewModel(repository: WellProjectRepository) : ViewModel() {
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
}
