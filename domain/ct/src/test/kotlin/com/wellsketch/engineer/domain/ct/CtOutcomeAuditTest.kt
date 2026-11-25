package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.units.FlowRate
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.schematic.CtSurfaceViewMode
import com.wellsketch.engineer.core.schematic.SurfaceBlockKind
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** End-to-end audit of the CT Calculations results for every demo operation and view. */
class CtOutcomeAuditTest {

    @Test
    fun everyDemoOperationIsErrorFreeAtEveryDepthAndView() {
        for (project in SyntheticDemoProjects.all()) {
            for (op in project.ctOperations) {
                for (view in CtSurfaceViewMode.entries) {
                    val base = CtOperationAnalyzer().analyze(project.well, op, view)
                    val td = base.flowPath.totalDepth ?: fail("${project.id}: no flow path")
                    // Reachable bottom: above the first full-bore blockage (e.g. a bridge plug),
                    // else the bottom of the path. Running into a plug is an expected error.
                    val plug = base.restrictions.firstOrNull { it.basis == RestrictionBasis.FULL_BORE_BLOCKAGE }?.topMd
                    val reachable = plug ?: td
                    // nose depth on the operation datum = canonical MD − (RKB to CT zero)
                    val datumOffset = base.placement.noseMd.meters - op.currentDepth.meters
                    for (fraction in listOf(0.05, 0.5, 0.95)) {
                        val where = "${project.id} $view nose ${(fraction * 100).toInt()}%"
                        val depthOp = op.copy(currentDepth = Length((reachable.meters - 20.0) * fraction - datumOffset))
                        val a = CtOperationAnalyzer().analyze(project.well, depthOp, view)
                        val errors = a.issues.filter { it.severity == Severity.ERROR }
                        if (errors.isNotEmpty()) fail("$where: $errors")
                        assertTrue(a.placement.noseMd <= reachable, where)
                        val kinds = a.scene.surfaceBlocks.map { it.kind }
                        assertTrue(kinds.none { it.name.startsWith("DRILLING_") } && a.scene.surfaceSite.rig == null, where)
                        assertTrue(SurfaceBlockKind.INJECTOR in kinds, where)
                        assertTrue(a.volumes.issues.none { it.severity == Severity.ERROR }, where)
                        val bha = op.bha.maxOuterDiameter
                        if (bha != null) assertTrue(a.pathSummary.bhaDiametralClearance!!.meters > 0.0, "$where: BHA does not pass")
                    }
                }
            }
        }
    }

    @Test
    fun runningIntoABridgePlugIsReportedAsBlocked() {
        val p = SyntheticDemoProjects.sShaped()
        val op = p.ctOperations.single()
        val base = CtOperationAnalyzer().analyze(p.well, op, CtSurfaceViewMode.COMPACT)
        val plug = base.restrictions.first { it.basis == RestrictionBasis.FULL_BORE_BLOCKAGE }
        val offset = base.placement.noseMd.meters - op.currentDepth.meters
        val below = op.copy(currentDepth = Length(plug.bottomMd.meters + 5.0 - offset))
        val a = CtOperationAnalyzer().analyze(p.well, below, CtSurfaceViewMode.COMPACT)
        assertTrue(a.issues.any { it.code == "CT_PATH_BLOCKED" && it.severity == Severity.ERROR })
    }

    @Test
    fun pumpRateChangesTimesButNotVolumes() {
        val p = SyntheticDemoProjects.horizontal()
        val op = p.ctOperations.single()
        val slow = CtOperationAnalyzer().analyze(p.well, op.copy(pumpRate = FlowRate.barrelsPerMinute(0.5)), CtSurfaceViewMode.COMPACT).volumes
        val fast = CtOperationAnalyzer().analyze(p.well, op.copy(pumpRate = FlowRate.barrelsPerMinute(2.0)), CtSurfaceViewMode.COMPACT).volumes
        kotlin.test.assertEquals(slow.ctInternalVolumeInWell.cubicMeters, fast.ctInternalVolumeInWell.cubicMeters, 1e-12)
        kotlin.test.assertEquals(slow.annulus.total.cubicMeters, fast.annulus.total.cubicMeters, 1e-12)
        // 4x the rate = 1/4 of the time
        kotlin.test.assertEquals(slow.timeToNozzleMinutes!! / 4.0, fast.timeToNozzleMinutes!!, 1e-9)
        kotlin.test.assertEquals(slow.bottomsUpMinutes!! / 4.0, fast.bottomsUpMinutes!!, 1e-9)
    }
}
