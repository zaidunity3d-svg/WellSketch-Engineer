package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.schematic.ElementKind
import com.wellsketch.engineer.core.schematic.SurfaceBlockKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurfaceModeTest {
    private val project = SyntheticDemoProjects.vertical()
    private val well = project.well
    private val ctRigUp = project.ctOperations.single().rigUp
    private val session = WellDesignSession()

    @Test
    fun completionIsTheDefaultAndAllModesAreOfferedWhenDataExists() {
        val s = session.snapshot(well, CompletionState.CURRENT, null, ctRigUp = ctRigUp)
        assertEquals(SurfaceMode.COMPLETION, s.surfaceMode)
        assertEquals(listOf(SurfaceMode.DRILLING, SurfaceMode.COMPLETION, SurfaceMode.COILED_TUBING), s.availableSurfaceModes)
        assertTrue(s.scene.surfaceBlocks.any { it.kind == SurfaceBlockKind.TREE_CAP })
    }

    @Test
    fun drillingModeShowsRigAndBopButNoTreeAndNoCompletion() {
        val s = session.snapshot(well, CompletionState.CURRENT, null, SurfaceMode.DRILLING, ctRigUp)
        assertEquals(SurfaceMode.DRILLING, s.surfaceMode)
        assertTrue(s.scene.surfaceBlocks.any { it.kind == SurfaceBlockKind.DRILLING_RAM })
        assertTrue(s.scene.surfaceBlocks.none { it.kind == SurfaceBlockKind.TREE_VALVE || it.kind == SurfaceBlockKind.INJECTOR })
        assertNotNull(s.scene.surfaceSite.rig)
        assertTrue(s.scene.elements.none { it.kind == ElementKind.TUBING_WALL || it.kind == ElementKind.COMPLETION_COMPONENT })
        assertEquals(0.6, s.surfaceStackup!!.clearanceUnderRotaryBeams!!.meters, 1e-9)
    }

    @Test
    fun ctModeNeedsACtRigUpAndNeverShowsTheDrillingBop() {
        val without = session.snapshot(well, CompletionState.CURRENT, null, SurfaceMode.COILED_TUBING, ctRigUp = null)
        assertEquals(SurfaceMode.COMPLETION, without.surfaceMode)
        val ct = session.snapshot(well, CompletionState.CURRENT, null, SurfaceMode.COILED_TUBING, ctRigUp)
        assertEquals(SurfaceMode.COILED_TUBING, ct.surfaceMode)
        assertTrue(ct.scene.surfaceBlocks.any { it.kind == SurfaceBlockKind.INJECTOR })
        assertTrue(ct.scene.surfaceBlocks.none { it.kind.name.startsWith("DRILLING_") })
        assertNull(ct.scene.surfaceSite.rig)
    }

    @Test
    fun cellarAndWellheadEditsMoveTheWellheadImmediately() {
        val deeper = assertIs<EditOutcome.Applied>(WellEdits.setCellarDepth(well, Length.meters(3.0))).well
        val lowered = assertIs<EditOutcome.Applied>(WellEdits.setWellheadBase(deeper, Length.meters(-2.8))).well
        val s = session.snapshot(lowered, CompletionState.CURRENT, null)
        assertEquals(-3.0, s.scene.surfaceSite.cellar!!.floorAboveGl, 1e-9)
        assertEquals(-2.8, s.scene.surfaceBlocks.first().bottomAboveGl, 1e-9)
        assertEquals(-0.7, s.surfaceStackup!!.wellheadTop!!.meters, 1e-9)
        val rejected = WellEdits.setCellarDepth(well, Length.meters(-1.0))
        assertIs<EditOutcome.Rejected>(rejected)
    }
}
