package com.wellsketch.engineer.core.schematic

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.well.CompletionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurfaceLayoutTest {
    private val project = SyntheticDemoProjects.vertical()
    private val well = project.well
    private val inputs = SurfaceInputs.of(well)
    private val drillingKinds = setOf(
        SurfaceBlockKind.DRILLING_BELL_NIPPLE, SurfaceBlockKind.DRILLING_ANNULAR,
        SurfaceBlockKind.DRILLING_RAM, SurfaceBlockKind.DRILLING_SPOOL,
    )
    private val treeKinds = setOf(
        SurfaceBlockKind.TREE_CAP, SurfaceBlockKind.TREE_VALVE, SurfaceBlockKind.TREE_TEE,
        SurfaceBlockKind.TREE_ADAPTER, SurfaceBlockKind.TREE_CHOKE, SurfaceBlockKind.TREE_WING_VALVE,
    )

    private fun assertContiguousOnAxis(blocks: List<SurfaceBlock>) {
        val axis = blocks.filter { it.centerOffset == 0.0 }
        axis.zipWithNext().forEach { (a, b) -> assertEquals(a.topAboveGl, b.bottomAboveGl, 1e-9, "${a.label} -> ${b.label}") }
    }

    @Test
    fun threeSectionWellheadSitsInTheCellarBelowGroundLevel() {
        val layout = SurfaceLayouts.layout(OperatingState.CompletedWithTree(well.christmasTree!!), inputs = inputs)
        val wellhead = layout.blocks.filter { it.kind == SurfaceBlockKind.WELLHEAD }
        assertEquals(3, wellhead.size)
        assertEquals(-2.1, wellhead.first().bottomAboveGl, 1e-9)
        assertEquals(0.0, wellhead.last().topAboveGl, 1e-9)
        val cellar = assertNotNull(layout.site.cellar)
        assertEquals(-2.4, cellar.floorAboveGl, 1e-9)
        assertTrue(wellhead.all { it.bottomAboveGl >= cellar.floorAboveGl && it.topAboveGl <= 0.0 + 1e-9 })
        assertTrue(wellhead.all { it.centerOffset == 0.0 })
        // the casing head housing stands on the surface casing stub; the conductor stops at the cellar floor
        val stubs = layout.site.casingStubs
        assertEquals(2, stubs.size)
        assertEquals(-2.1, stubs.last().topAboveGl, 1e-9)
        assertTrue(stubs.first().topAboveGl < -2.1)
    }

    @Test
    fun drillingModeShowsRigFloorSubstructureAndBopOnTheWellheadWithoutATree() {
        val layout = SurfaceLayouts.layout(OperatingState.Drilling(well.drillingRigUp!!), inputs = inputs)
        val kinds = layout.blocks.map { it.kind }
        assertTrue(drillingKinds.all { it in kinds })
        assertTrue(kinds.none { it in treeKinds })
        assertTrue(layout.site.sideBlocks.isEmpty())
        assertTrue(layout.blocks.all { it.centerOffset == 0.0 }, "all equipment on the well centerline")
        assertContiguousOnAxis(layout.blocks)
        val firstBop = layout.blocks.first { it.kind in drillingKinds }
        assertEquals(0.0, firstBop.bottomAboveGl, 1e-9) // nippled up on the wellhead top flange
        val rig = assertNotNull(layout.site.rig)
        assertEquals(8.5, rig.floorTopAboveGl, 1e-9)
        assertEquals(7.6, rig.beamsUndersideAboveGl, 1e-9)
        assertTrue(layout.blocks.last().topAboveGl <= rig.beamsUndersideAboveGl)
        assertTrue(layout.site.structureBlocks.any { it.kind == SurfaceBlockKind.SUBSTRUCTURE })
        assertTrue(layout.site.structureBlocks.any { it.kind == SurfaceBlockKind.RIG_FLOOR })
    }

    @Test
    fun completionModeShowsTreeWithWingsOnTheWellheadAndNoDrillingEquipment() {
        val layout = SurfaceLayouts.layout(OperatingState.CompletedWithTree(well.christmasTree!!), inputs = inputs)
        val kinds = layout.blocks.map { it.kind }
        assertTrue(kinds.none { it in drillingKinds })
        assertNull(layout.site.rig)
        assertContiguousOnAxis(layout.blocks)
        assertEquals(SurfaceBlockKind.TREE_ADAPTER, layout.blocks.first { it.kind != SurfaceBlockKind.WELLHEAD }.kind)
        assertEquals(SurfaceBlockKind.TREE_CAP, layout.blocks.last().kind)
        // wings branch from the flow cross at its mid-height, left (kill) and right (production + choke)
        val tee = layout.blocks.single { it.kind == SurfaceBlockKind.TREE_TEE }
        assertEquals(listOf(-1, 1), tee.drawing.branchSides)
        val wings = layout.site.sideBlocks
        assertEquals(3, wings.size)
        wings.forEach { assertEquals(tee.bottomAboveGl + tee.height / 2, it.bottomAboveGl + it.height / 2, 1e-9) }
        assertTrue(wings.any { it.kind == SurfaceBlockKind.TREE_CHOKE && it.centerOffset > 0 })
        assertTrue(wings.any { it.kind == SurfaceBlockKind.TREE_WING_VALVE && it.centerOffset < 0 })
    }

    @Test
    fun ctModeStacksPressureControlAndInjectorOnTheTreeNotTheDrillingBop() {
        val op = project.ctOperations.single()
        val layout = SurfaceLayouts.layout(OperatingState.CoiledTubingIntervention(well.christmasTree!!, op.rigUp), inputs = inputs)
        val kinds = layout.blocks.map { it.kind }
        assertTrue(kinds.none { it in drillingKinds }, "drilling BOP must not be reused as CT BOP")
        assertTrue(SurfaceBlockKind.BOP in kinds && SurfaceBlockKind.INJECTOR in kinds)
        assertFalse(SurfaceBlockKind.TREE_CAP in kinds)
        assertContiguousOnAxis(layout.blocks)
        val treeTop = layout.blocks.last { it.kind in treeKinds }.topAboveGl
        assertEquals(op.depthReference.let { (it as com.wellsketch.engineer.core.model.datum.DepthReference.Operation).heightAboveGroundLevel.meters }, treeTop, 1e-9)
        assertTrue(layout.site.showsCoiledTubing)
    }

    @Test
    fun wellWithoutWellheadDataKeepsThePreviousPlaceholder() {
        val legacy = well.copy(wellhead = null, cellar = null)
        val tree = well.christmasTree!!.copy(wellheadTopAboveGroundLevel = com.wellsketch.engineer.core.model.units.Length.meters(0.8))
        val blocks = SurfaceLayouts.forOperatingState(OperatingState.CompletedWithTree(tree), inputs = SurfaceInputs.of(legacy))
        val wellhead = blocks.first()
        assertEquals(SurfaceBlockKind.WELLHEAD, wellhead.kind)
        assertEquals(0.0, wellhead.bottomAboveGl, 1e-9)
        assertEquals(0.8, wellhead.topAboveGl, 1e-9)
    }

    @Test
    fun artworkCoversCellarAndHasHandwheelsForManualValves() {
        val scene = WellSchematicBuilder().build(well, CompletionState.CURRENT)
        val art = SurfaceArtwork.build(scene)
        val bounds = assertNotNull(art.bounds)
        assertTrue(bounds.minY <= -2.4 - 0.25 + 1e-9, "cellar slab included")
        assertTrue(bounds.maxY >= 3.3 - 1e-9, "tree cap included")
        val wheels = art.primitives.filterIsInstance<ArtCircle>().filter { it.stroke == ArtColors.HANDWHEEL }
        // swab, lower master, production wing, kill wing + 3 annulus valves in the wellhead
        assertEquals(7, wheels.size)
        assertTrue(art.callouts.any { it.text == "Cellar" })
        // level of detail: a coarse scale drops studs but keeps the main bodies
        val coarse = SurfaceArtwork.build(scene, pxPerMeter = 6.0)
        assertTrue(coarse.primitives.size < art.primitives.size)
        assertEquals(bounds.minY, coarse.bounds!!.minY, 1e-9)
    }

    @Test
    fun drillingArtworkFitsUnderTheRigFloorAndIncludesRails() {
        val scene = WellSchematicBuilder().build(well, null, OperatingState.Drilling(well.drillingRigUp!!))
        assertTrue(scene.elements.none { it.kind == ElementKind.TUBING_WALL }, "no completion in the drilling state")
        val bounds = assertNotNull(SurfaceArtwork.bounds(scene))
        assertTrue(bounds.maxY >= 8.5 + 1.0, "handrails above the drill floor")
        assertTrue(bounds.maxX >= 3.6 - 1e-9 && bounds.minX <= -3.6 + 1e-9, "substructure width")
    }
}

class CalloutLayoutTest {
    @Test
    fun labelsDoNotOverlapAndStayInsideTheColumn() {
        val project = SyntheticDemoProjects.jShaped()
        val state = OperatingState.CoiledTubingIntervention(project.well.christmasTree!!, project.ctOperations.single().rigUp)
        val scene = WellSchematicBuilder().build(project.well, CompletionState.CURRENT, state, CtSurfaceViewMode.FULL)
        val art = SurfaceArtwork.build(scene)
        val scale = 40.0
        val lh = 30.0
        val placed = CalloutLayout.place(art.callouts, { 600 + it * scale }, { 700 - it * scale }, 1100.0, 100.0, 0.0, 1200.0, lh)
        val columns = placed.filter { it.hasLeader }.groupBy { it.align }
        for ((_, column) in columns) {
            column.sortedBy { it.labelTop }.zipWithNext().forEach { (a, b) -> assertTrue(b.labelTop >= a.labelTop + lh - 1e-9, "${a.callout.text} / ${b.callout.text}") }
            column.forEach { assertTrue(it.labelTop >= 0.0 && it.labelTop + lh <= 1200.0 + 1e-9) }
        }
        // left-side wing labels go to the left column, remote units are labelled below GL
        assertTrue(placed.any { it.align == LabelAlign.END && it.callout.kind == CalloutKind.SIDE })
        placed.filter { it.callout.kind == CalloutKind.REMOTE }.forEach { assertTrue(it.labelTop > 700.0 && !it.hasLeader) }
        assertEquals(art.callouts.size, placed.size)
    }

    @Test
    fun overflowingColumnKeepsTheLabelsThatFit() {
        val callouts = (0 until 20).map { ArtCallout("c$it", 0.0, it * 0.1, "s$it") }
        val placed = CalloutLayout.place(callouts, { it }, { 100 - it * 10 }, 10.0, null, 0.0, 100.0, 20.0)
        assertTrue(placed.size <= 4)
        assertTrue(placed.all { it.labelTop >= 0.0 && it.labelTop + 20.0 <= 100.0 + 1e-9 })
    }
}
