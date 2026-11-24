package com.wellsketch.engineer.core.data.repository

import com.wellsketch.engineer.core.model.equipment.EquipmentItem
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.project.WellProjectSummary
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first store of well projects. The Android implementation persists to a local
 * Room database (:core:database); there is no network source in this milestone.
 */
interface WellProjectRepository {
    fun observeProjectSummaries(): Flow<List<WellProjectSummary>>
    fun observeProject(id: String): Flow<WellProject?>
    suspend fun getProject(id: String): WellProject?
    suspend fun saveProject(project: WellProject)
    suspend fun deleteProject(id: String)
}

/** Read access to the equipment reference library. */
interface EquipmentCatalogRepository {
    fun observeItems(): Flow<List<EquipmentItem>>
    suspend fun findById(id: String): EquipmentItem?
}

fun WellProject.toSummary() = WellProjectSummary(
    id = id,
    name = name,
    wellName = well.name,
    lastModifiedEpochMillis = lastModifiedEpochMillis,
    isSyntheticDemo = isSyntheticDemo,
)
