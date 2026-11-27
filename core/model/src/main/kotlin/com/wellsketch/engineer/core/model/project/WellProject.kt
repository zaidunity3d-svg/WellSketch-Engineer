package com.wellsketch.engineer.core.model.project

import com.wellsketch.engineer.core.model.ct.CtOperation
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.workover.WorkoverOperation
import com.wellsketch.engineer.core.model.well.Well
import kotlinx.serialization.Serializable

/**
 * Top-level persisted aggregate: one well with its engineering operations.
 *
 * [schemaVersion] versions the persisted document format so that future migrations are
 * explicit; see `WellProjectCodec` in :core:data.
 */
@Serializable
data class WellProject(
    val id: String,
    val name: String,
    val description: String = "",
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val lastModifiedEpochMillis: Long,
    val well: Well,
    val ctOperations: List<CtOperation> = emptyList(),
    val provenance: DataProvenance,
    /** Workover jobs with a workover rig (optional; older documents have none). */
    val workoverOperations: List<WorkoverOperation> = emptyList(),
) {
    val isSyntheticDemo: Boolean get() = provenance.isSynthetic

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/** Lightweight listing entry for project pickers. */
data class WellProjectSummary(
    val id: String,
    val name: String,
    val wellName: String,
    val lastModifiedEpochMillis: Long,
    val isSyntheticDemo: Boolean,
)
