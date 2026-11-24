package com.wellsketch.engineer.core.database

import com.wellsketch.engineer.core.data.codec.WellProjectCodec
import com.wellsketch.engineer.core.data.repository.WellProjectRepository
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.project.WellProjectSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Room-backed [WellProjectRepository]. Decoding errors propagate to the caller. */
class RoomWellProjectRepository(
    private val dao: WellProjectDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WellProjectRepository {

    override fun observeProjectSummaries(): Flow<List<WellProjectSummary>> =
        dao.observeSummaries().map { rows ->
            rows.map { WellProjectSummary(it.id, it.name, it.wellName, it.lastModifiedEpochMillis, it.isSyntheticDemo) }
        }

    override fun observeProject(id: String): Flow<WellProject?> =
        dao.observe(id).map { entity -> entity?.let { WellProjectCodec.decode(it.payloadJson) } }.flowOn(dispatcher)

    override suspend fun getProject(id: String): WellProject? = withContext(dispatcher) {
        dao.get(id)?.let { WellProjectCodec.decode(it.payloadJson) }
    }

    override suspend fun saveProject(project: WellProject) = withContext(dispatcher) {
        dao.upsert(project.toEntity())
    }

    override suspend fun deleteProject(id: String) = withContext(dispatcher) {
        dao.delete(id)
    }
}

fun WellProject.toEntity() = WellProjectEntity(
    id = id,
    name = name,
    wellName = well.name,
    isSyntheticDemo = isSyntheticDemo,
    schemaVersion = schemaVersion,
    lastModifiedEpochMillis = lastModifiedEpochMillis,
    payloadJson = WellProjectCodec.encode(this),
)
