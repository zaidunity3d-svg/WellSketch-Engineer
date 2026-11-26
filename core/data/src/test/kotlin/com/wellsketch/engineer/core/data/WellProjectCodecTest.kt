package com.wellsketch.engineer.core.data

import com.wellsketch.engineer.core.data.codec.UnsupportedSchemaVersionException
import com.wellsketch.engineer.core.data.codec.WellProjectCodec
import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.well.TubingConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class WellProjectCodecTest {

    @Test
    fun everyDemoProjectRoundTripsLosslessly() {
        for (project in SyntheticDemoProjects.all(nowEpochMillis = 1234L)) {
            val decoded = WellProjectCodec.decode(WellProjectCodec.encode(project))
            assertEquals(project, decoded, "Round trip changed ${project.id}")
        }
    }

    @Test
    fun polymorphicDatumsAndTubingConfigurationSurviveRoundTrip() {
        val project = SyntheticDemoProjects.sShaped()
        val decoded = WellProjectCodec.decode(WellProjectCodec.encode(project))
        assertIs<TubingConfiguration.ExplicitlyNone>(decoded.well.completions.single().tubing)
        assertIs<DepthReference.Operation>(decoded.ctOperations.single().depthReference)
    }

    @Test
    fun cementTailTopRoundTripsAndOldDocumentsWithoutItDecode() {
        val project = SyntheticDemoProjects.vertical()
        val cemented = project.well.casingStrings.first { it.cement != null }
        val c = cemented.cement!!
        val tail = c.top.copy(value = c.top.value + (c.bottom.value - c.top.value) * 0.5)
        val withTail = project.copy(
            well = project.well.copy(casingStrings = project.well.casingStrings.map { if (it.id == cemented.id) it.copy(cement = c.copy(tailTop = tail)) else it }),
        )
        val decoded = WellProjectCodec.decode(WellProjectCodec.encode(withTail))
        assertEquals(tail, decoded.well.casingStrings.single { it.id == cemented.id }.cement!!.tailTop)
        // a document written before the lead/tail split has no "tailTop" key
        assertTrue("tailTop" !in WellProjectCodec.encode(project))
        assertNull(WellProjectCodec.decode(WellProjectCodec.encode(project)).well.casingStrings.single { it.id == cemented.id }.cement!!.tailTop)
    }

    @Test
    fun newerSchemaVersionIsRejectedInsteadOfTruncated() {
        val payload = WellProjectCodec.encode(SyntheticDemoProjects.vertical())
            .replace("\"schemaVersion\":1", "\"schemaVersion\":99")
        assertFailsWith<UnsupportedSchemaVersionException> { WellProjectCodec.decode(payload) }
    }

    @Test
    fun demoProjectsAreClearlyLabelledSynthetic() {
        for (project in SyntheticDemoProjects.all()) {
            assertTrue(project.isSyntheticDemo)
            assertTrue(project.name.startsWith(SyntheticDemoProjects.NAME_PREFIX))
            assertTrue(project.well.casingStrings.all { it.provenance.isSynthetic && it.spec.provenance.isSynthetic })
        }
    }

    @Test
    fun documentsWrittenBeforeSurfaceEquipmentExistedStillDecode() {
        // Simulate a document from the previous version: no wellhead, cellar, drilling
        // rig-up, tree wings or valve operators.
        val json = WellProjectCodec.json
        val doc = json.parseToJsonElement(WellProjectCodec.encode(SyntheticDemoProjects.vertical())).jsonObject
        fun JsonObject.without(vararg keys: String) = JsonObject(filterKeys { it !in keys })
        fun strip(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement = when (e) {
            is JsonObject -> JsonObject(e.filterKeys { it != "operator" && it != "wingOutlets" }.mapValues { strip(it.value) })
            is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map(::strip))
            else -> e
        }
        val well = doc.getValue("well").jsonObject.without("wellhead", "cellar", "drillingRigUp")
        val old = strip(JsonObject(doc + ("well" to well))).toString()
        val decoded = WellProjectCodec.decode(old)
        assertNull(decoded.well.wellhead)
        assertNull(decoded.well.drillingRigUp)
        assertTrue(decoded.well.christmasTree!!.wingOutlets.isEmpty())
    }
}
