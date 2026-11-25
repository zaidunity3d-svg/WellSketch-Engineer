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
import kotlin.test.assertTrue

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
}
