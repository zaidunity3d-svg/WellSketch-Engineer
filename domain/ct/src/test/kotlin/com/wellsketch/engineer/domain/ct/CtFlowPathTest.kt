package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CompletionState
import kotlin.test.Test
import kotlin.test.assertEquals

class CtFlowPathTest {
    private val resolver = CtFlowPathResolver()

    private fun path(projectId: String): FlowPath {
        val well = SyntheticDemoProjects.all().single { it.id == projectId }.well
        return resolver.resolve(WellGeometryContext(well), well.completion(CompletionState.CURRENT))
    }

    @Test
    fun ctRunsInsideProductionTubingByDefault() {
        val segments = path(SyntheticDemoProjects.J_SHAPED_ID).segments
        assertEquals(ConduitType.PRODUCTION_TUBING, segments.first().conduitType)
        assertEquals(11.1, segments.first().topMd.meters, 1e-9) // starts at GL (tubing hanger)
        assertEquals(2900.0, segments.first().bottomMd.meters, 1e-9)
        // below the end of tubing CT runs in the 7 in production casing, then open hole
        assertEquals(ConduitType.CASING, segments[1].conduitType)
        assertEquals(2900.0, segments[1].topMd.meters, 1e-9)
        assertEquals(3090.0, segments[1].bottomMd.meters, 1e-9)
        assertEquals(ConduitType.OPEN_HOLE, segments.last().conduitType)
    }

    @Test
    fun tubinglessCompletionUsesInnermostCasingFromSurface() {
        val segments = path(SyntheticDemoProjects.S_SHAPED_ID).segments
        assertEquals(ConduitType.CASING, segments.first().conduitType)
        assertEquals(Length.inches(6.2).meters, segments.first().innerDiameter.meters, 1e-12)
        assertEquals(11.1, segments.first().topMd.meters, 1e-9)
        assertEquals(2890.0, segments.first().bottomMd.meters, 1e-9)
    }

    @Test
    fun horizontalPathGoesTubingCasingLinerOpenHole() {
        val types = path(SyntheticDemoProjects.HORIZONTAL_ID).segments.map { it.conduitType }
        assertEquals(
            listOf(ConduitType.PRODUCTION_TUBING, ConduitType.CASING, ConduitType.LINER, ConduitType.OPEN_HOLE),
            types,
        )
    }
}
