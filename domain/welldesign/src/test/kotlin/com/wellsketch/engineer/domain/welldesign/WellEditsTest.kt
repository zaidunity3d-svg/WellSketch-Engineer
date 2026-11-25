package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.schematic.ElementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WellEditsTest {
    private val well = SyntheticDemoProjects.vertical().well
    private val session = WellDesignSession()

    @Test
    fun movingShoeMovesCasingEndAndAttachedCementInTheNextSnapshot() {
        val id = well.casingStrings.last().id
        val edited = assertIs<EditOutcome.Applied>(WellEdits.setCasingShoe(well, id, MeasuredDepth.rkb(Length.meters(2300.0)))).well
        val scene = session.snapshot(edited, CompletionState.CURRENT, null).scene
        assertEquals(2300.0, scene.elements.single { it.id == "casing:$id" }.mdBottom, 1e-9)
        assertEquals(2300.0, scene.elements.filter { it.kind == ElementKind.CEMENT && it.sourceId == id }.maxOf { it.mdBottom }, 1e-9)
    }

    @Test
    fun formationEditUpdatesColumn() {
        val f = well.formations[3]
        val edited = assertIs<EditOutcome.Applied>(
            WellEdits.setFormationTop(well, f.id, TrueVerticalDepth.gl(Length.meters(1300.0))),
        ).well
        val band = session.snapshot(edited, null, null).scene.formations.single { it.id == f.id }
        assertEquals(1300.0, band.topTvdBelowGl, 1e-9)
    }

    @Test
    fun datumEditShiftsTvdBelowGroundLevel() {
        val edited = assertIs<EditOutcome.Applied>(
            WellEdits.setDatumElevations(well, Length.meters(262.0), Length.meters(250.0)),
        ).well
        // RKB now 12 m above GL: the path starts at MD 12 m RKB at GL
        assertEquals(12.0, session.snapshot(edited, null, null).scene.groundLevelMd, 1e-9)
    }

    @Test
    fun invalidEditsAreRejectedWithoutChangingTheWell() {
        assertIs<EditOutcome.Rejected>(WellEdits.setCasingShoe(well, "missing", MeasuredDepth.rkb(Length.meters(1.0))))
        assertIs<EditOutcome.Rejected>(WellEdits.setDatumElevations(well, Length.meters(240.0), Length.meters(250.0)))
    }

    @Test
    fun snapshotFallsBackToAvailableState() {
        val s = session.snapshot(SyntheticDemoProjects.sShaped().well, CompletionState.AS_DESIGNED, null)
        assertEquals(CompletionState.CURRENT, s.completionState)
        assertTrue(s.availableStates == listOf(CompletionState.CURRENT))
    }
}

class TransientInputTest {
    @Test
    fun shoeTypedAboveCasingTopDoesNotCrashAndIsReported() {
        // While typing "1500" the first keystroke gives a shoe at 1 m, above the casing top (9 m RKB).
        val well = SyntheticDemoProjects.vertical().well
        val id = well.casingStrings.last().id
        val edited = assertIs<EditOutcome.Applied>(WellEdits.setCasingShoe(well, id, MeasuredDepth.rkb(Length.meters(1.0)))).well
        val snapshot = WellDesignSession().snapshot(edited, CompletionState.CURRENT, null)
        assertTrue(snapshot.scene.elements.none { it.id == "casing:$id" })
        assertTrue(snapshot.scene.issues.any { it.code == "CASING_INTERVAL" })
    }

    @Test
    fun endOfTubingTypedAboveHangerDoesNotCrash() {
        val well = SyntheticDemoProjects.vertical().well
        val edited = assertIs<EditOutcome.Applied>(
            WellEdits.setEndOfTubing(well, CompletionState.CURRENT, MeasuredDepth.rkb(Length.meters(2.0))),
        ).well
        val snapshot = WellDesignSession().snapshot(edited, CompletionState.CURRENT, null)
        assertTrue(snapshot.scene.elements.none { it.kind == ElementKind.TUBING_WALL })
        assertTrue(snapshot.scene.issues.any { it.code == "TUBING_INTERVAL" })
    }
}

class EndOfTubingEditTest {
    @Test
    fun entryGuideAtEndOfTubingMovesWithItAndOtherComponentsStay() {
        val well = SyntheticDemoProjects.vertical().well
        val before = well.completion(CompletionState.CURRENT)!!
        val edited = assertIs<EditOutcome.Applied>(
            WellEdits.setEndOfTubing(well, CompletionState.CURRENT, MeasuredDepth.rkb(Length.meters(2150.0))),
        ).well
        val after = edited.completion(CompletionState.CURRENT)!!
        val guide = after.components.single { it.type == com.wellsketch.engineer.core.model.well.CompletionComponentType.WIRELINE_ENTRY_GUIDE }
        assertEquals(2150.0, (guide.top.value + guide.length).meters, 1e-9)
        val packerBefore = before.components.single { it.type == com.wellsketch.engineer.core.model.well.CompletionComponentType.PACKER }
        val packerAfter = after.components.single { it.id == packerBefore.id }
        assertEquals(packerBefore.top, packerAfter.top)
        val scene = WellDesignSession().snapshot(edited, CompletionState.CURRENT, null).scene
        assertEquals(2150.0, scene.markers.single { it.kind == com.wellsketch.engineer.core.schematic.MarkerKind.END_OF_TUBING }.md, 1e-9)
    }
}
