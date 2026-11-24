package com.wellsketch.engineer.core.schematic

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.model.well.CompletionState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchematicProjectionTest {
    private val builder = WellSchematicBuilder()

    private fun projection(id: String, w: Double = 1080.0, h: Double = 1800.0): SchematicProjection {
        val project = SyntheticDemoProjects.all().single { it.id == id }
        val scene = builder.build(project.well, CompletionState.CURRENT)
        return SchematicProjection(scene, w, h, ProjectionConfig(columnWidth = 160.0, surfaceBandHeight = 200.0))
    }

    @Test
    fun formationBoundariesAlignWithWellAtSameTvd() {
        for (id in listOf(SyntheticDemoProjects.J_SHAPED_ID, SyntheticDemoProjects.S_SHAPED_ID, SyntheticDemoProjects.HORIZONTAL_ID)) {
            val p = projection(id)
            for (band in p.scene.formations.filterNot { it.isUndefined }) {
                // find the path vertex closest in TVD to the band top and compare screen y
                val v = p.scene.path.minBy { abs(it.y - band.topTvdBelowGl) }
                if (abs(v.y - band.topTvdBelowGl) > 1.0) continue // band below TD
                val wellY = p.map(v.x, v.y).y
                val columnY = p.tvdToY(band.topTvdBelowGl)
                assertEquals(columnY, wellY, abs(v.y - band.topTvdBelowGl) * p.pathScale + 1e-9, "$id ${band.name}")
            }
        }
    }

    @Test
    fun groundLevelIsColumnTopAndPathStart() {
        val p = projection(SyntheticDemoProjects.VERTICAL_ID)
        assertEquals(p.groundY, p.tvdToY(0.0), 0.0)
        assertEquals(p.groundY, p.axisAt(p.scene.groundLevelMd).first.y, 1e-9)
    }

    @Test
    fun drawingFitsInsideViewport() {
        for (id in listOf(SyntheticDemoProjects.VERTICAL_ID, SyntheticDemoProjects.HORIZONTAL_ID)) {
            val p = projection(id)
            for (v in p.scene.path) {
                val s = p.map(v.x, v.y)
                assertTrue(s.x >= p.wellLeft - 1e-6 && s.x <= p.wellRight + 1e-6, "$id x=${s.x}")
                assertTrue(s.y >= p.groundY - 1e-6 && s.y <= p.height + 1e-6, "$id y=${s.y}")
            }
        }
    }

    @Test
    fun locateInvertsPoint() {
        val p = projection(SyntheticDemoProjects.J_SHAPED_ID)
        for (md in listOf(300.0, 1000.0, 1150.0, 2500.0)) {
            for (offset in listOf(-0.05, 0.08)) {
                val s = p.point(md, offset)
                val located = p.locate(s.x, s.y)
                assertEquals(md, located.md, 0.5)
                assertEquals(abs(offset), located.radius, 0.002)
            }
        }
    }

    @Test
    fun tapOnCasingWallSelectsCasing() {
        val p = projection(SyntheticDemoProjects.VERTICAL_ID)
        val casing = p.scene.elements.single { it.id.startsWith("casing:") && it.sourceId.endsWith("-c4") }
        val radius = (casing.innerRadius + casing.outerRadius) / 2
        val s = p.point(2350.0, radius)
        val located = p.locate(s.x, s.y)
        val hit = assertNotNull(SchematicHitTester.hitTest(p.scene, located.md, located.radius))
        assertEquals(casing.id, hit.id)
    }
}

class SurfaceProjectionTest {
    @Test
    fun fullCtSpreadFitsInsideNarrowViewport() {
        val project = SyntheticDemoProjects.jShaped()
        val state = com.wellsketch.engineer.core.model.surface.OperatingState.CoiledTubingIntervention(
            project.well.christmasTree!!, project.ctOperations.single().rigUp,
        )
        val base = WellSchematicBuilder().build(project.well, CompletionState.CURRENT, state)
        val scene = base.copy(surfaceBlocks = SurfaceLayouts.forOperatingState(state, CtSurfaceViewMode.FULL))
        val p = SchematicProjection(scene, 720.0, 1400.0, ProjectionConfig(columnWidth = 160.0, surfaceBandHeight = 320.0))
        assertTrue(p.surfaceScale > 0.0)
        for (b in scene.surfaceBlocks) {
            val r = p.surfaceRect(b)
            assertTrue(r[0] >= 0.0 && r[2] <= 720.0, "${b.kind} x ${r[0]}..${r[2]}")
            assertTrue(r[1] >= 0.0 && r[3] <= p.groundY + 1e-9, "${b.kind} y ${r[1]}..${r[3]}")
        }
    }
}
