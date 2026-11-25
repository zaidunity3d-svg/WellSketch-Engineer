package com.wellsketch.engineer.core.engineering

import com.wellsketch.engineer.core.engineering.TestWells.casing
import com.wellsketch.engineer.core.engineering.summary.KeyDepthKind
import com.wellsketch.engineer.core.engineering.summary.KeyDepths
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CompletionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyDepthsTest {

    @Test
    fun verticalWellWithoutCompletionListsShoesLinerTopAndTotalDepth() {
        val well = TestWells.verticalWell(
            listOf(
                casing("surface", CasingRole.SURFACE, 13.375, 12.4, 10.0, 500.0, 10.0),
                casing("production", CasingRole.PRODUCTION, 7.0, 6.2, 10.0, 1800.0, 1200.0),
                casing("liner", CasingRole.PRODUCTION_LINER, 4.5, 3.9, 1700.0, 1990.0, 1700.0),
            ),
        )
        val rows = KeyDepths.build(well, CompletionState.CURRENT)
        assertEquals(
            listOf(
                KeyDepthKind.CASING_SHOE to 500.0,
                KeyDepthKind.LINER_TOP to 1700.0,
                KeyDepthKind.CASING_SHOE to 1800.0,
                KeyDepthKind.CASING_SHOE to 1990.0,
                KeyDepthKind.TOTAL_DEPTH to 2000.0,
            ),
            rows.map { it.kind to it.md.meters },
        )
        // vertical well: TVD equals MD on the same (RKB) datum
        rows.forEach { assertEquals(it.md.meters, it.tvd.meters, 1e-9) }
        assertEquals(Length.inches(13.375).meters, rows.first().outerDiameter!!.meters, 1e-12)
        assertEquals(Length.inches(4.5).meters, rows[1].outerDiameter!!.meters, 1e-12)
        assertNull(rows.last().outerDiameter)
    }

    @Test
    fun noStateMeansCasingAndTotalDepthOnly() {
        val rows = KeyDepths.build(TestWells.verticalWell(), null)
        assertEquals(listOf(KeyDepthKind.CASING_SHOE, KeyDepthKind.CASING_SHOE, KeyDepthKind.TOTAL_DEPTH), rows.map { it.kind })
    }

    @Test
    fun equalDepthsKeepAStableKindOrder() {
        // production shoe at TD: the shoe row comes before the TD row
        val well = TestWells.verticalWell(listOf(casing("production", CasingRole.PRODUCTION, 7.0, 6.2, 10.0, 2000.0, 1200.0)))
        assertEquals(listOf(KeyDepthKind.CASING_SHOE, KeyDepthKind.TOTAL_DEPTH), KeyDepths.build(well, null).map { it.kind })
    }
}
