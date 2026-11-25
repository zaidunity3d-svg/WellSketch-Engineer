package com.wellsketch.engineer.core.data

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DemoUpgradeTest {

    @Test
    fun storedDemoFromAnEarlierVersionGetsSurfaceEquipmentAndKeepsUserEdits() {
        val current = SyntheticDemoProjects.vertical()
        val edited = current.well.casingStrings.last().let { it.copy(name = "edited by user") }
        val stored = current.copy(
            well = current.well.copy(
                wellhead = null, cellar = null, drillingRigUp = null,
                christmasTree = current.well.christmasTree!!.copy(wingOutlets = emptyList(), wellheadTopAboveGroundLevel = Length.meters(0.8)),
                casingStrings = current.well.casingStrings.dropLast(1) + edited,
            ),
        )
        val upgraded = assertNotNull(SyntheticDemoProjects.withCurrentSurfaceEquipment(stored))
        assertEquals(current.well.wellhead, upgraded.well.wellhead)
        assertEquals(current.well.cellar, upgraded.well.cellar)
        assertEquals(current.well.drillingRigUp, upgraded.well.drillingRigUp)
        assertEquals(current.well.christmasTree, upgraded.well.christmasTree)
        assertEquals("edited by user", upgraded.well.casingStrings.last().name)
        // already upgraded: nothing to do
        assertNull(SyntheticDemoProjects.withCurrentSurfaceEquipment(upgraded))
    }

    @Test
    fun realProjectsAreNeverUpgraded() {
        val demo = SyntheticDemoProjects.vertical()
        val real = demo.copy(provenance = DataProvenance.userEntered(), well = demo.well.copy(wellhead = null))
        assertNull(SyntheticDemoProjects.withCurrentSurfaceEquipment(real))
    }
}
