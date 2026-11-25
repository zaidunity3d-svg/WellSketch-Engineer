package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.summary.KeyDepthKind
import com.wellsketch.engineer.core.engineering.summary.KeyDepths
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CompletionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Key depths on the synthetic demo wells (integration of data, datums and trajectory). */
class KeyDepthsDemoTest {

    @Test
    fun everyDemoWellGivesSortedRowsWithTvdFromTheSameDatum() {
        for (project in SyntheticDemoProjects.all()) {
            val well = project.well
            val ctx = WellGeometryContext(well)
            val rows = KeyDepths.build(well, CompletionState.CURRENT, ctx)
            assertTrue(rows.isNotEmpty(), well.name)
            assertEquals(rows.sortedBy { it.md.meters }.map { it.md }, rows.map { it.md }, "${well.name}: sorted by MD")
            assertEquals(KeyDepthKind.TOTAL_DEPTH, rows.last().kind, "${well.name}: TD last")
            for (r in rows) {
                // TVD never exceeds MD on the same datum, and matches the trajectory
                assertTrue(r.tvd.meters <= r.md.meters + 1e-6, "${well.name} ${r.kind} TVD > MD")
                val expected = ctx.tvd(ctx.canonicalMd(r.md), ctx.canonicalReference).value.meters
                assertEquals(expected, r.tvd.meters, 1e-9)
                r.mdBottom?.let { b -> assertTrue(b.meters >= r.md.meters, "${well.name}: interval bottom above top") }
            }
            val completion = well.completion(CompletionState.CURRENT)
            completion?.tubingString?.let { t ->
                assertTrue(rows.any { it.kind == KeyDepthKind.END_OF_TUBING && it.md == ctx.canonical(t.endOfTubing) })
            }
            assertEquals(completion?.perforations?.size ?: 0, rows.count { it.kind == KeyDepthKind.PERFORATIONS })
        }
    }

    @Test
    fun deviatedWellHasTvdBelowMdAtTotalDepth() {
        val well = SyntheticDemoProjects.sShaped().well
        val td = KeyDepths.build(well, CompletionState.CURRENT).last()
        assertTrue(td.md.meters - td.tvd.meters > 10.0, "S-shaped well should have TVD clearly below MD at TD")
        assertTrue(td.tvd > Length.ZERO)
    }
}
