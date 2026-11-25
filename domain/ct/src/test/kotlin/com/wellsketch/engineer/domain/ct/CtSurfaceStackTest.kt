package com.wellsketch.engineer.domain.ct

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.validation.hasErrors
import com.wellsketch.engineer.core.model.surface.EndConnection
import com.wellsketch.engineer.core.model.surface.StackComponentType
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.schematic.CtSurfaceViewMode
import com.wellsketch.engineer.core.schematic.SurfaceBlockKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CtSurfaceStackTest {
    private val validator = CtSurfaceStackValidator()
    private val project = SyntheticDemoProjects.jShaped()
    private val tree = project.well.christmasTree!!
    private val op = project.ctOperations.single()
    private val stack = op.rigUp.pressureControlStack

    private fun validate(s: com.wellsketch.engineer.core.model.surface.CtPressureControlStack = stack, pressure: Pressure? = op.maxExpectedWellheadPressure) =
        validator.validate(tree, s, op.ctString.maxOuterDiameter, op.bha.maxOuterDiameter, pressure)

    @Test
    fun demoStackIsConsistent() {
        assertFalse(validate().hasErrors(), validate().toString())
    }

    @Test
    fun connectionMismatchIsReported() {
        val broken = stack.copy(components = stack.components.mapIndexed { i, c ->
            if (i == 2) c.copy(topConnection = EndConnection("OTHER")) else c
        })
        assertTrue(validate(broken).any { it.code == "PCE_CONNECTION_MISMATCH" })
    }

    @Test
    fun boreSmallerThanBhaIsReported() {
        val narrow = stack.copy(components = stack.components.map {
            if (it.type == StackComponentType.CT_BOP) it.copy(bore = Length.inches(2.0)) else it
        })
        assertTrue(validate(narrow).any { it.code == "PCE_BORE_BHA" })
    }

    @Test
    fun ratingBelowRequiredPressureIsReportedAndMissingRequirementIsExplicit() {
        assertTrue(validate(pressure = Pressure.psi(6000.0)).any { it.code == "PCE_RATING" })
        assertTrue(validate(pressure = null).any { it.code == "PCE_PRESSURE_NOT_SPECIFIED" })
    }

    @Test
    fun compactViewShowsTreeStackAndInjectorButNoReel() {
        val result = CtOperationAnalyzer().analyze(project.well, op, CtSurfaceViewMode.COMPACT)
        val kinds = result.scene.surfaceBlocks.map { it.kind }
        assertTrue(SurfaceBlockKind.INJECTOR in kinds && SurfaceBlockKind.BOP in kinds && SurfaceBlockKind.TREE_VALVE in kinds)
        assertFalse(SurfaceBlockKind.REEL in kinds)
        // tree cap removed for the CT rig-up
        assertFalse(SurfaceBlockKind.TREE_CAP in kinds)
        val full = CtOperationAnalyzer().analyze(project.well, op, CtSurfaceViewMode.FULL).scene.surfaceBlocks.map { it.kind }
        assertTrue(listOf(SurfaceBlockKind.REEL, SurfaceBlockKind.GOOSENECK, SurfaceBlockKind.CONTROL_CABIN, SurfaceBlockKind.POWER_PACK).all { it in full })
    }

    @Test
    fun stackBlocksAreContiguousFromWellheadToInjector() {
        val blocks = CtOperationAnalyzer().analyze(project.well, op, CtSurfaceViewMode.COMPACT).scene.surfaceBlocks
            .filter { it.centerOffset == 0.0 }
        blocks.zipWithNext().forEach { (a, b) -> assertEquals(a.topAboveGl, b.bottomAboveGl, 1e-9) }
        assertEquals(SurfaceBlockKind.INJECTOR, blocks.last().kind)
    }
}
