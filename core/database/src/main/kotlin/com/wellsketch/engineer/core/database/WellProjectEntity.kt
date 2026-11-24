package com.wellsketch.engineer.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A well project stored as a versioned JSON document ([payloadJson], see WellProjectCodec)
 * plus a few indexed columns for listing. The engineering aggregate is deeply nested and
 * always loaded as a whole, so a document column keeps the schema small while the codec
 * handles explicit schema-version migrations.
 */
@Entity(tableName = "well_projects")
data class WellProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "well_name") val wellName: String,
    @ColumnInfo(name = "is_synthetic_demo") val isSyntheticDemo: Boolean,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "last_modified_epoch_millis") val lastModifiedEpochMillis: Long,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
)

/** Listing projection (no payload). */
data class WellProjectSummaryRow(
    val id: String,
    val name: String,
    @ColumnInfo(name = "well_name") val wellName: String,
    @ColumnInfo(name = "is_synthetic_demo") val isSyntheticDemo: Boolean,
    @ColumnInfo(name = "last_modified_epoch_millis") val lastModifiedEpochMillis: Long,
)
