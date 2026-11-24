package com.wellsketch.engineer.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WellProjectDao {

    @Query(
        "SELECT id, name, well_name, is_synthetic_demo, last_modified_epoch_millis " +
            "FROM well_projects ORDER BY name COLLATE NOCASE",
    )
    fun observeSummaries(): Flow<List<WellProjectSummaryRow>>

    @Query("SELECT * FROM well_projects WHERE id = :id")
    fun observe(id: String): Flow<WellProjectEntity?>

    @Query("SELECT * FROM well_projects WHERE id = :id")
    suspend fun get(id: String): WellProjectEntity?

    @Upsert
    suspend fun upsert(entity: WellProjectEntity)

    @Query("DELETE FROM well_projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM well_projects")
    suspend fun count(): Int
}
