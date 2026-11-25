package com.wellsketch.engineer.core.data.codec

import com.wellsketch.engineer.core.model.project.WellProject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Encodes a [WellProject] as a versioned JSON document for local persistence.
 *
 * Decoding is strict (unknown keys fail) so that data written by a newer app version is
 * never silently truncated. Migrations between schema versions are explicit functions
 * registered in [migrations].
 */
object WellProjectCodec {

    val json: Json = Json {
        classDiscriminator = "kind"
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    /** schemaVersion N -> function migrating a version-N document to version N+1. */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    fun encode(project: WellProject): String = json.encodeToString(WellProject.serializer(), project)

    fun decode(payload: String): WellProject {
        var document = json.parseToJsonElement(payload).jsonObject
        var version = document["schemaVersion"]?.jsonPrimitive?.int ?: 1
        if (version > WellProject.CURRENT_SCHEMA_VERSION) {
            throw UnsupportedSchemaVersionException(version)
        }
        while (version < WellProject.CURRENT_SCHEMA_VERSION) {
            val migrate = migrations[version] ?: error("No migration from schema version $version")
            document = migrate(document)
            version += 1
        }
        return json.decodeFromJsonElement(WellProject.serializer(), document)
    }
}

class UnsupportedSchemaVersionException(version: Int) : IllegalStateException(
    "Project document schema version $version is newer than supported version " +
        "${WellProject.CURRENT_SCHEMA_VERSION}. Update the app to open it.",
)
