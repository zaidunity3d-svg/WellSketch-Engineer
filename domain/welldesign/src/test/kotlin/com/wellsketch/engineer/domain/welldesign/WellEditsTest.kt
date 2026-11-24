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
