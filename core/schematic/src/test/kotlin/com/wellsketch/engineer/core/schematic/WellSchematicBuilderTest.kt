package com.wellsketch.engineer.core.schematic

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.model.well.CompletionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WellSchematicBuilderTest {
    private val builder = WellSchematicBuilder()
    private val inch = 0.0254

    @Test
    fun casingElementsEndExactlyAtTheirShoes() {
        val well = SyntheticDemoProjects.jShaped().well
        val scene = builder.build(well, CompletionState.CURRENT)
        for (s in well.casingStrings) {
            val element = scene.elements.single { it.id == "casing:${s.id}" }
            assertEquals(s.shoe.depth.value.meters, element.mdBottom, 1e-9)
            assertEquals(s.spec.outerDiameter.meters / 2, element.outerRadius, 1e-12)
            assertEquals(s.spec.innerDiameter.meters / 2, element.innerRadius, 1e-12)
            assertTrue(scene.markers.any { it.id == "shoe:${s.id}" && it.md == element.mdBottom })
        }
    }

    @Test
    fun cementOuterBoundarySwitchesFromPreviousCasingToOpenHoleAtShoe() {
        // Demo J1: 7 in casing cemented 1600–3090 m; 9-5/8 in shoe at 1800 m; the 12-1/4 in
        // hole continues to 1810 m (rathole), then 8-1/2 in hole to TD.
        val well = SyntheticDemoProjects.jShaped().well
        val scene = builder.build(well, CompletionState.CURRENT)
        val cement = scene.elements.filter { it.kind == ElementKind.CEMENT && it.sourceId.endsWith("-c4") }
            .sortedBy { it.mdTop }
        assertEquals(3, cement.size, cement.toString())
        assertEquals(1600.0, cement[0].mdTop, 1e-9)
        assertEquals(1800.0, cement[0].mdBottom, 1e-9)
        assertEquals(8.7 * inch / 2, cement[0].outerRadius, 1e-12) // inside 9-5/8 in casing ID
        assertEquals(1800.0, cement[1].mdTop, 1e-9)
        assertEquals(1810.0, cement[1].mdBottom, 1e-9)
        assertEquals(12.25 * inch / 2, cement[1].outerRadius, 1e-12) // rathole below the shoe
        assertEquals(1810.0, cement[2].mdTop, 1e-9)
        assertEquals(3090.0, cement[2].mdBottom, 1e-9)
        assertEquals(8.5 * inch / 2, cement[2].outerRadius, 1e-12) // 8-1/2 in open hole
        cement.forEach { assertEquals(7.0 * inch / 2, it.innerRadius, 1e-12) }
    }

    @Test
    fun stratigraphicColumnStartsAtGroundLevel() {
        val scene = builder.build(SyntheticDemoProjects.vertical().well, CompletionState.CURRENT)
        val first = scene.formations.first()
        assertEquals(0.0, first.topTvdBelowGl, 0.0)
        assertFalse(first.isUndefined)
        scene.formations.zipWithNext().forEach { (a, b) -> assertEquals(a.bottomTvdBelowGl, b.topTvdBelowGl, 1e-9) }
        // path starts at GL (y = 0) at MD = RKB height above GL
        assertEquals(0.0, scene.path.first().y, 1e-9)
        assertEquals(9.0, scene.path.first().md, 1e-9)
    }

    @Test
    fun undefinedIntervalIsInsertedWhenFirstTopIsBelowGroundLevel() {
        val well = SyntheticDemoProjects.vertical().well.let { w ->
            w.copy(formations = w.formations.drop(1))
        }
        val scene = builder.build(well, CompletionState.CURRENT)
        val first = scene.formations.first()
        assertTrue(first.isUndefined)
        assertEquals(0.0, first.topTvdBelowGl, 0.0)
        assertEquals(150.0, first.bottomTvdBelowGl, 1e-9)
    }

    @Test
    fun tubingIsDrawnByDefaultAndOmittedForExplicitlyTubinglessCompletion() {
        val withTubing = builder.build(SyntheticDemoProjects.vertical().well, CompletionState.CURRENT)
        assertTrue(withTubing.elements.any { it.kind == ElementKind.TUBING_WALL })
        val tubingless = builder.build(SyntheticDemoProjects.sShaped().well, CompletionState.CURRENT)
        assertTrue(tubingless.elements.none { it.kind == ElementKind.TUBING_WALL })
        val plug = tubingless.elements.single { it.kind == ElementKind.COMPLETION_COMPONENT }
        assertEquals(0.0, plug.innerRadius, 0.0)
        assertEquals(6.2 * inch / 2, plug.outerRadius, 1e-12) // set in 7 in casing
    }

    @Test
    fun setPackerSpansAnnulusToCasingWall() {
        val scene = builder.build(SyntheticDemoProjects.vertical().well, CompletionState.CURRENT)
        val packer = scene.elements.single { it.id.startsWith("component:") && it.label.startsWith("Production packer") }
        assertEquals(6.2 * inch / 2, packer.outerRadius, 1e-12)
    }

    @Test
    fun linerGetsLinerTopMarkerAndTvdFollowsSurvey() {
        val well = SyntheticDemoProjects.horizontal().well
        val scene = builder.build(well, CompletionState.CURRENT)
        assertNotNull(scene.markers.singleOrNull { it.kind == MarkerKind.LINER_TOP })
        // horizontal section: TVD at end barely changes while vertical section grows
        val last = scene.path.last()
        val earlier = scene.path.first { it.md >= 2300.0 }
        assertEquals(earlier.y, last.y, 1e-6)
        assertTrue(last.x > earlier.x + 300.0)
    }

    @Test
    fun treeIsShownForCompletedWellWithStackedComponents() {
        val scene = builder.build(SyntheticDemoProjects.vertical().well, CompletionState.CURRENT)
        val blocks = scene.surfaceBlocks
        assertEquals(SurfaceBlockKind.WELLHEAD, blocks.first().kind)
        blocks.zipWithNext().forEach { (a, b) -> assertEquals(a.topAboveGl, b.bottomAboveGl, 1e-9) }
        assertEquals(SurfaceBlockKind.TREE_CAP, blocks.last().kind)
    }

    @Test
    fun formationTopTvdMatchesPathTvdAtSameDepth() {
        val well = SyntheticDemoProjects.jShaped().well
        val scene = builder.build(well, CompletionState.CURRENT)
        val reservoir = well.formations.first { it.name.startsWith("Reservoir") }
        val band = scene.formations.first { it.id == reservoir.id }
        assertEquals(reservoir.top.value.meters, band.topTvdBelowGl, 1e-9)
    }
}
