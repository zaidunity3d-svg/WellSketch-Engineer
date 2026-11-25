package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.schematic.CtSurfaceViewMode
import com.wellsketch.engineer.core.schematic.ElementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CtRestrictionAndPlacementTest {
    private val analyzer = CtOperationAnalyzer()

    @Test
    fun smallestRestrictionIsTheLandingNippleAndBhaPasses() {
        val project = SyntheticDemoProjects.jShaped()
        val op = project.ctOperations.single().copy(currentDepth = Length.meters(2950.0))
        val result = analyzer.analyze(project.well, op, CtSurfaceViewMode.COMPACT)
        val smallest = assertNotNull(result.passability.smallestRestriction)
        assertEquals(Length.inches(2.65).meters, smallest.innerDiameter!!.meters, 1e-12)
        assertEquals(Length.inches(2.65 - 2.125).meters, result.passability.bhaDiametralClearance!!.meters, 1e-12)
        assertTrue(result.passability.issues.none { it.code == "CT_TOOL_TOO_LARGE" })
    }

    @Test
    fun oversizeBhaIsReportedAtTheRestriction() {
        val project = SyntheticDemoProjects.jShaped()
        val op = project.ctOperations.single().let { o ->
            o.copy(
                currentDepth = Length.meters(2950.0),
                bha = o.bha.copy(components = o.bha.components.map { it.copy(maxOuterDiameter = Length.inches(2.7)) }),
            )
        }
        val result = analyzer.analyze(project.well, op, CtSurfaceViewMode.COMPACT)
        assertTrue(result.passability.issues.any { it.code == "CT_TOOL_TOO_LARGE" })
    }

    @Test
    fun bridgePlugBlocksPathOnlyBelowIt() {
        val project = SyntheticDemoProjects.sShaped()
        val above = project.ctOperations.single().copy(currentDepth = Length.meters(2650.0))
        val below = above.copy(currentDepth = Length.meters(2760.0))
        assertFalse(analyzer.analyze(project.well, above, CtSurfaceViewMode.COMPACT).passability.issues.any { it.code == "CT_PATH_BLOCKED" })
        assertTrue(analyzer.analyze(project.well, below, CtSurfaceViewMode.COMPACT).passability.issues.any { it.code == "CT_PATH_BLOCKED" })
    }

    @Test
    fun depthOnOperationDatumIsConvertedToWellMd() {
        // Demo datums: RKB 11.10 m above GL; CT zero (tree top) 3.98 m above GL. MD(RKB) = MD(op) + 7.12 m.
        val project = SyntheticDemoProjects.vertical()
        val op = project.ctOperations.single().copy(currentDepth = Length.meters(2000.0))
        val placement = analyzer.analyze(project.well, op, CtSurfaceViewMode.COMPACT).placement
        assertEquals(2007.12, placement.noseMd.meters, 1e-9)
        assertEquals(1996.02, placement.noseMdBelowGl.meters, 1e-9)
        assertEquals(2007.12 - op.bha.totalLength.meters, placement.bhaTopMd.meters, 1e-9)
        assertEquals(placement.bhaTopMd.meters, placement.bhaComponents.first().topMd.meters, 1e-12)
        assertEquals(placement.noseMd.meters, placement.bhaComponents.last().bottomMd.meters, 1e-9)
    }

    @Test
    fun changingDepthReferenceKeepsThePhysicalPosition() {
        val project = SyntheticDemoProjects.vertical()
        val ctx = WellGeometryContext(project.well)
        val op = project.ctOperations.single().copy(currentDepth = Length.meters(2000.0))
        val onRkb = CtOperationEdits.changeDepthReference(ctx, op, DepthReference.Rkb)
        assertEquals(2007.12, onRkb.currentDepth.meters, 1e-9)
        val p1 = CtPlacementCalculator().place(ctx, op)
        val p2 = CtPlacementCalculator().place(ctx, onRkb)
        assertEquals(p1.noseMd.meters, p2.noseMd.meters, 1e-9)
    }

    @Test
    fun sceneContainsCtAndBhaEndingAtNoseAndMovesImmediatelyWithDepth() {
        val project = SyntheticDemoProjects.jShaped()
        val op = project.ctOperations.single()
        val a = analyzer.analyze(project.well, op.copy(currentDepth = Length.meters(1000.0)), CtSurfaceViewMode.COMPACT)
        val b = analyzer.analyze(project.well, op.copy(currentDepth = Length.meters(2500.0)), CtSurfaceViewMode.COMPACT)
        fun noseOf(scene: com.wellsketch.engineer.core.schematic.SchematicScene) =
            scene.elements.filter { it.kind == ElementKind.BHA_COMPONENT }.maxOf { it.mdBottom }
        assertEquals(a.placement.noseMd.meters, noseOf(a.scene), 1e-9)
        assertEquals(b.placement.noseMd.meters, noseOf(b.scene), 1e-9)
        assertTrue(a.scene.elements.any { it.kind == ElementKind.CT_WALL })
        assertEquals(CompletionState.CURRENT, a.completionState)
    }

    @Test
    fun ctLongerThanStringIsReported() {
        val project = SyntheticDemoProjects.jShaped()
        val op = project.ctOperations.single().copy(currentDepth = Length.meters(5000.0))
        val issues = analyzer.analyze(project.well, op, CtSurfaceViewMode.COMPACT).issues
        assertTrue(issues.any { it.code == "CT_STRING_TOO_SHORT" })
        assertTrue(issues.any { it.code == "CT_BELOW_TD" })
    }
}
