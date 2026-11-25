package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.volumes.VolumeBreakdown
import com.wellsketch.engineer.core.model.well.CompletionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** End-to-end audit of the Well Calculations results for every demo well and state. */
class WellCalculationOutcomeAuditTest {

    private fun audit(b: VolumeBreakdown, where: String) {
        for ((x, y) in b.segments.zipWithNext()) {
            assertEquals(x.bottomMd.meters, y.topMd.meters, 1e-9, "$where ${b.title}: segments not contiguous")
        }
        for (s in b.segments) {
            assertTrue(s.volume.cubicMeters >= 0.0 && s.volume.cubicMeters.isFinite(), "$where ${b.title}: bad volume")
            assertTrue(s.bottomMd > s.topMd, "$where ${b.title}: empty segment")
        }
    }

    @Test
    fun everyDemoWellAndStateGivesCompleteErrorFreeResults() {
        val calc = WellCalculations()
        for (project in SyntheticDemoProjects.all()) {
            val well = project.well
            for (state in listOf<CompletionState?>(null) + CompletionState.entries.filter { well.completion(it) != null }) {
                val where = "${project.id} $state"
                val r = calc.report(well, state)
                val errors = (r.issues + r.flowPath.issues + r.tubingAnnulus.issues + r.cementJobs.flatMap { it.issues })
                    .filter { it.severity == Severity.ERROR }
                if (errors.isNotEmpty()) fail("$where: $errors")
                assertTrue(r.flowPath.total.cubicMeters > 0.0, "$where: flow path volume")
                audit(r.flowPath, where)
                audit(r.tubingAnnulus, where)
                // inside tubing + below tubing = whole flow path
                r.tubingInside?.let { inside ->
                    audit(inside, where)
                    val below = r.belowTubing?.total?.cubicMeters ?: 0.0
                    assertEquals(r.flowPath.total.cubicMeters, inside.total.cubicMeters + below, 1e-9, where)
                }
                if (state == null) assertTrue(r.tubingInside == null, "$where: no tubing without a completion")
                for (t in r.tubulars) {
                    assertTrue(t.innerDiameter < t.outerDiameter && t.bottomMd > t.topMd, "$where ${t.name}")
                    assertTrue(t.insideCapacity.cubicMetersPerMeter > 0.0, "$where ${t.name}")
                }
                assertEquals(well.casingStrings.count { it.cement != null }, r.cementJobs.size, "$where: one cement job per cemented string")
                for (job in r.cementJobs) {
                    audit(job.annulus, "$where ${job.string.name}")
                    assertTrue(job.slurryVolume.cubicMeters > 0.0, "$where ${job.string.name}: slurry")
                    assertTrue(job.displacement.total.cubicMeters > 0.0, "$where ${job.string.name}: displacement")
                }
            }
        }
    }
}
