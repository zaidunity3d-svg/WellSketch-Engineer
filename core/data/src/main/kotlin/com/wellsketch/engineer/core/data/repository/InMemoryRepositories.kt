package com.wellsketch.engineer.core.data.repository

import com.wellsketch.engineer.core.model.equipment.EquipmentItem
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.project.WellProjectSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory repository for tests, previews and JVM-side tooling. */
class InMemoryWellProjectRepository(initial: List<WellProject> = emptyList()) : WellProjectRepository {
    private val state = MutableStateFlow(initial.associateBy { it.id })

    val projects: StateFlow<Map<String, WellProject>> = state.asStateFlow()

    override fun observeProjectSummaries(): Flow<List<WellProjectSummary>> =
        state.map { map -> map.values.sortedBy { it.name }.map { it.toSummary() } }

    override fun observeProject(id: String): Flow<WellProject?> = state.map { it[id] }

    override suspend fun getProject(id: String): WellProject? = state.value[id]

    override suspend fun saveProject(project: WellProject) {
        state.update { it + (project.id to project) }
    }

    override suspend fun deleteProject(id: String) {
        state.update { it - id }
    }
}

class InMemoryEquipmentCatalogRepository(items: List<EquipmentItem>) : EquipmentCatalogRepository {
    private val state = MutableStateFlow(items)
    override fun observeItems(): Flow<List<EquipmentItem>> = state
    override suspend fun findById(id: String): EquipmentItem? = state.value.firstOrNull { it.id == id }
}
