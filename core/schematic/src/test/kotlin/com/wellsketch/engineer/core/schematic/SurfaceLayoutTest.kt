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
    fun threeSectionWellheadStandsOnTheLandingBaseInTheCellar() {
        // Reference stack-up: landing base 1.29 m below GL, cellar 2.50 m deep, casing spools
        // 1.55 m + tubing head spool 0.72 m, so the wellhead top is 0.98 m above GL.
        val layout = SurfaceLayouts.layout(OperatingState.CompletedWithTree(well.christmasTree!!), inputs = inputs)
        val wellhead = layout.blocks.filter { it.kind == SurfaceBlockKind.WELLHEAD }
        assertEquals(3, wellhead.size)
        assertEquals(-1.29, wellhead.first().bottomAboveGl, 1e-9)
        assertEquals(0.98, wellhead.last().topAboveGl, 1e-9)
        assertEquals(0.72, wellhead.last().height, 1e-9)
        assertEquals(1.55, wellhead[0].height + wellhead[1].height, 1e-9)
        val cellar = assertNotNull(layout.site.cellar)
        assertEquals(-2.5, cellar.floorAboveGl, 1e-9)
        assertTrue(wellhead.all { it.bottomAboveGl >= cellar.floorAboveGl })
        assertTrue(wellhead.all { it.centerOffset == 0.0 })
        // landing base on the conductor; the lowest section stands on the surface casing stub
        val base = assertNotNull(layout.site.landingBase)
        assertEquals(-1.29, base.topAboveGl, 1e-9)
        val stubs = layout.site.casingStubs
        assertEquals(2, stubs.size)
        assertEquals(-1.29, stubs.last().topAboveGl, 1e-9)
        assertEquals(base.topAboveGl - base.thickness, stubs.first().topAboveGl, 1e-9) // conductor carries the base
        assertTrue(base.halfWidth > stubs.first().outerRadius)
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
        // Reference stack-up: DSA (11 in 5K) on the tubing head spool top, 0.98 m above GL.
        assertEquals(0.98, firstBop.bottomAboveGl, 1e-9)
        val wellheadIds = layout.blocks.filter { it.kind == SurfaceBlockKind.WELLHEAD }.map { it.sourceId }
        assertEquals(well.wellhead!!.sections.map { it.id }.reversed(), wellheadIds)
        // annular top = 0.98 + 0.18 + 0.60 + 1.27 + 0.80 + 1.78 + 1.22 = 6.83 m above GL
        val annular = layout.blocks.single { it.kind == SurfaceBlockKind.DRILLING_ANNULAR }
        assertEquals(6.83, annular.topAboveGl, 1e-9)
        val rig = assertNotNull(layout.site.rig)
        assertEquals(11.10, rig.floorTopAboveGl, 1e-9) // reference: drill floor 11.10 m above GL
        assertEquals(10.0, rig.beamsUndersideAboveGl, 1e-9)
        assertTrue(layout.blocks.last().topAboveGl <= rig.beamsUndersideAboveGl)
        assertTrue(layout.site.structureBlocks.any { it.kind == SurfaceBlockKind.SUBSTRUCTURE })
        assertTrue(layout.site.structureBlocks.any { it.kind == SurfaceBlockKind.RIG_FLOOR })
    }

    @Test
    fun enteredNippleUpSectionDecidesTheInstalledWellheadInTheDrawing() {
        val rigUp = well.drillingRigUp!!
        val ths = well.wellhead!!.sections.first()
        val onThs = SurfaceLayouts.layout(OperatingState.Drilling(rigUp.copy(nippledUpOnSectionId = ths.id)), inputs = inputs)
        assertEquals(3, onThs.blocks.count { it.kind == SurfaceBlockKind.WELLHEAD })
        assertEquals(0.98, onThs.blocks.first { it.kind in drillingKinds }.bottomAboveGl, 1e-9)
        val chh = well.wellhead!!.sections.last()
        val onChh = SurfaceLayouts.layout(OperatingState.Drilling(rigUp.copy(nippledUpOnSectionId = chh.id)), inputs = inputs)
        assertEquals(listOf(chh.id), onChh.blocks.filter { it.kind == SurfaceBlockKind.WELLHEAD }.map { it.sourceId })
        assertEquals(-1.29 + 0.775, onChh.blocks.first { it.kind in drillingKinds }.bottomAboveGl, 1e-9)
        assertContiguousOnAxis(onChh.blocks)
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
