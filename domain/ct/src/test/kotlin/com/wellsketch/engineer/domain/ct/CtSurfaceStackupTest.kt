package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.schematic.CtSurfaceViewMode
import com.wellsketch.engineer.core.schematic.SurfaceBlockKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CtSurfaceStackupTest {

    @Test
    fun demoCtZeroDatumIsTheCalculatedTopOfTheTree() {
        for (project in SyntheticDemoProjects.all()) {
            val op = project.ctOperations.single()
            val analysis = CtOperationAnalyzer().analyze(project.well, op, CtSurfaceViewMode.COMPACT)
            val datum = assertIs<DepthReference.Operation>(op.depthReference)
            val treeTop = assertNotNull(analysis.surfaceStackup.treeTopWithoutCap)
            assertEquals(datum.heightAboveGroundLevel.meters, treeTop.meters, 1e-9, project.id)
            // injector base = tree top + CT stack heights (1.0 + 3.0 + 1.4 + 0.6 + 0.4 m)
            assertEquals(treeTop.meters + 6.4, analysis.surfaceStackup.ctStackTop!!.meters, 1e-9)
        }
    }

    @Test
    fun ctSceneHasTheWellheadInTheCellarAndNoDrillingEquipment() {
        val project = SyntheticDemoProjects.jShaped()
        val scene = CtOperationAnalyzer().analyze(project.well, project.ctOperations.single(), CtSurfaceViewMode.FULL).scene
        assertTrue(scene.surfaceBlocks.first().bottomAboveGl < 0.0)
        assertTrue(scene.surfaceSite.cellar != null && scene.surfaceSite.rig == null)
        assertTrue(scene.surfaceBlocks.none { it.kind.name.startsWith("DRILLING_") })
        assertTrue(scene.surfaceBlocks.any { it.kind == SurfaceBlockKind.REEL })
    }
}
