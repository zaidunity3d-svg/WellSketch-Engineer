package com.wellsketch.engineer.core.schematic

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.model.well.CompletionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SketchLabelsTest {
    @Test
    fun labelsComeFromSceneDataInDepthOrder() {
        val scene = WellSchematicBuilder().build(SyntheticDemoProjects.vertical().well, CompletionState.CURRENT)
        val labels = SketchLabels.collect(scene)
        val shoes = labels.filter { it.kind == SketchLabelKind.CASING_SHOE }
        assertEquals(listOf(58.0, 595.0, 1695.0, 2390.0), shoes.map { it.md })
        assertEquals(listOf(20.0, 13.375, 9.625, 7.0), shoes.map { Math.round(it.diameter!! / 0.0254 * 1000) / 1000.0 })
        assertTrue(labels.any { it.kind == SketchLabelKind.PACKER && it.md == 2200.0 })
        assertTrue(labels.any { it.kind == SketchLabelKind.END_OF_TUBING && it.md == 2250.0 })
        assertEquals(2, labels.count { it.kind == SketchLabelKind.PERFORATIONS })
        labels.zipWithNext().forEach { (a, b) -> assertTrue(a.md <= b.md) }
    }

    @Test
    fun tubinglessWellLabelsThePlugAndNoTubing() {
        val labels = SketchLabels.collect(WellSchematicBuilder().build(SyntheticDemoProjects.sShaped().well, CompletionState.CURRENT))
        assertTrue(labels.any { it.kind == SketchLabelKind.PLUG && it.md == 2700.0 })
        assertTrue(labels.none { it.kind == SketchLabelKind.END_OF_TUBING || it.kind == SketchLabelKind.PACKER })
    }

    @Test
    fun placerAvoidsOverlapsAndKeepsLabelsInside() {
        val requests = (0 until 12).map { LabelPlacer.Request("l$it", 100.0, 200.0 + it * 3.0, 80.0, 12.0) }
        val placed = LabelPlacer.place(requests, offset = 6.0, minX = 0.0, maxX = 300.0, minY = 0.0, maxY = 400.0)
        assertEquals(12, placed.size)
        for (a in placed) for (b in placed) if (a !== b) assertTrue(!a.overlaps(b, 0.0), "${a.id} overlaps ${b.id}")
        assertTrue(placed.all { it.x >= 106.0 && it.right <= 300.0 && it.bottom <= 400.0 })
        // a label near the right edge flips to the left of its anchor
        val edge = LabelPlacer.place(listOf(LabelPlacer.Request("e", 290.0, 50.0, 80.0, 12.0)), 6.0, 0.0, 300.0, 0.0, 400.0).single()
        assertTrue(edge.right <= 284.0 + 1e-9)
        // labels that cannot fit are dropped, not drawn outside
        val tight = LabelPlacer.place(requests, 6.0, 0.0, 300.0, 190.0, 240.0)
        assertTrue(tight.size < 12 && tight.all { it.bottom <= 240.0 })
    }

    @Test
    fun labelWithoutRoomOnTheRightGoesBesideTheMirroredAnchorNotOverTheWell() {
        // Well spans x = 160..240; right anchor at 240, mirrored anchor at 160.
        val r = LabelPlacer.Request("shoe", 240.0, 100.0, 120.0, 12.0, leftAnchorX = 160.0, leftAnchorY = 100.0)
        val placed = LabelPlacer.place(listOf(r), offset = 6.0, minX = 0.0, maxX = 300.0, minY = 0.0, maxY = 400.0).single()
        assertEquals(160.0 - 6.0 - 120.0, placed.x, 1e-9)
        assertEquals(160.0, placed.anchorX, 1e-9)
        assertTrue(placed.right <= 160.0, "label must not cover the well")
    }

    @Test
    fun labelThatFitsNowhereIsClampedToTheRightEdge() {
        val r = LabelPlacer.Request("wide", 240.0, 100.0, 200.0, 12.0, leftAnchorX = 160.0, leftAnchorY = 100.0)
        val placed = LabelPlacer.place(listOf(r), offset = 6.0, minX = 0.0, maxX = 300.0, minY = 0.0, maxY = 400.0).single()
        assertEquals(100.0, placed.x, 1e-9)
        assertEquals(240.0, placed.anchorX, 1e-9)
    }

    @Test
    fun leftPlacementUsesTheMirroredAnchorHeight() {
        // In an inclined section the mirrored side sits at another screen height.
        val r = LabelPlacer.Request("p", 280.0, 100.0, 60.0, 10.0, leftAnchorX = 200.0, leftAnchorY = 130.0)
        val placed = LabelPlacer.place(listOf(r), offset = 6.0, minX = 0.0, maxX = 300.0, minY = 0.0, maxY = 400.0).single()
        assertEquals(125.0, placed.y, 1e-9)
        assertEquals(130.0, placed.anchorY, 1e-9)
    }
}
