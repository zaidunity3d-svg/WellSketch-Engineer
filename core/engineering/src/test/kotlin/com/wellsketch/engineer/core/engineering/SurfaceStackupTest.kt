package com.wellsketch.engineer.core.engineering

import com.wellsketch.engineer.core.engineering.TestWells.m
import com.wellsketch.engineer.core.engineering.TestWells.synthetic
import com.wellsketch.engineer.core.engineering.surface.SurfaceStackupCalculator
import com.wellsketch.engineer.core.engineering.surface.WellheadPositionBasis
import com.wellsketch.engineer.core.model.ct.CtRigUp
import com.wellsketch.engineer.core.model.ct.CtSurfaceEquipment
import com.wellsketch.engineer.core.model.surface.Cellar
import com.wellsketch.engineer.core.model.surface.ChristmasTree
import com.wellsketch.engineer.core.model.surface.CtPressureControlStack
import com.wellsketch.engineer.core.model.surface.DrillingBopComponent
import com.wellsketch.engineer.core.model.surface.DrillingBopComponentType
import com.wellsketch.engineer.core.model.surface.DrillingBopStack
import com.wellsketch.engineer.core.model.surface.DrillingRig
import com.wellsketch.engineer.core.model.surface.DrillingRigUp
import com.wellsketch.engineer.core.model.surface.EndConnection
import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.surface.OutletSide
import com.wellsketch.engineer.core.model.surface.StackComponent
import com.wellsketch.engineer.core.model.surface.StackComponentType
import com.wellsketch.engineer.core.model.surface.TreeWingOutlet
import com.wellsketch.engineer.core.model.surface.TreeWingRole
import com.wellsketch.engineer.core.model.surface.Wellhead
import com.wellsketch.engineer.core.model.surface.WellheadSection
import com.wellsketch.engineer.core.model.surface.WellheadSectionType
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Surface stack-up elevations and connection checks. All values are synthetic. */
class SurfaceStackupTest {
    private val calc = SurfaceStackupCalculator()

    // TestWells.verticalWell: RKB 310 m, GL 300 m above MSL -> RKB 10 m above GL.
    private fun section(id: String, type: WellheadSectionType, top: String, bottom: String, h: Double?) = WellheadSection(
        id = id, type = type, name = id, topConnection = EndConnection(top), bottomConnection = EndConnection(bottom),
        height = h?.let(::m), provenance = synthetic,
    )

    private val wellhead = Wellhead(
        id = "wh", name = "wh",
        sections = listOf(
            section("ths", WellheadSectionType.TUBING_HEAD_SPOOL, "WH-TOP", "B", 0.6),
            section("cs", WellheadSectionType.CASING_SPOOL, "B", "A", 0.8),
            section("chh", WellheadSectionType.CASING_HEAD_HOUSING, "A", "CSG", 0.7),
        ),
        baseRelativeToGroundLevel = m(-2.0),
        provenance = synthetic,
    )

    private fun stack(id: String, type: StackComponentType, top: String, bottom: String, h: Double?) = StackComponent(
        id = id, type = type, name = id, bore = Length.inches(3.0), ratedWorkingPressure = Pressure.psi(5000.0),
        topConnection = EndConnection(top), bottomConnection = EndConnection(bottom), height = h?.let(::m), provenance = synthetic,
    )

    private val tree = ChristmasTree(
        id = "tree", name = "tree",
        components = listOf(
            stack("cap", StackComponentType.TREE_CAP, "CAP", "T0", 0.3),
            stack("swab", StackComponentType.SWAB_VALVE, "T0", "T1", 0.6),
            stack("tee", StackComponentType.FLOW_TEE, "T1", "T2", 0.5),
            stack("mv", StackComponentType.LOWER_MASTER_VALVE, "T2", "T3", 0.7),
            stack("tha", StackComponentType.TUBING_HEAD_ADAPTER, "T3", "WH-TOP", 0.4),
        ),
        provenance = synthetic,
    )

    private fun bop(id: String, type: DrillingBopComponentType, top: String, bottom: String, h: Double?) = DrillingBopComponent(
        id = id, type = type, name = id, bore = Length.inches(13.625), ratedWorkingPressure = Pressure.psi(5000.0),
        topConnection = EndConnection(top), bottomConnection = EndConnection(bottom), height = h?.let(::m), provenance = synthetic,
    )

    private fun rigUp(clearHeight: Double? = 6.0, floorBelowRkb: Double? = 0.5, bottomConnection: String = "WH-TOP") = DrillingRigUp(
        rig = DrillingRig("rig", "rig", floorBelowRkb?.let(::m), clearHeight?.let(::m), null, synthetic),
        bopStack = DrillingBopStack(
            "bop", "bop",
            listOf(
                bop("ann", DrillingBopComponentType.ANNULAR_PREVENTER, "X", "R", 1.3),
                bop("ram", DrillingBopComponentType.DOUBLE_RAM_PREVENTER, "R", "S", 1.5),
                bop("spool", DrillingBopComponentType.DRILLING_SPOOL, "S", bottomConnection, 0.6),
            ),
            synthetic,
        ),
    )

    private val well = TestWells.verticalWell().copy(
        wellhead = wellhead,
        cellar = Cellar(m(2.5), m(2.4), synthetic),
        christmasTree = tree,
    )

    @Test
    fun wellheadElevationsFollowEnteredBaseAndSectionHeights() {
        val r = calc.calculate(well, OperatingState.CompletedWithTree(tree))
        assertEquals(WellheadPositionBasis.ENTERED_BASE, r.wellheadBasis)
        assertEquals(-2.0, r.wellheadBase!!.meters, 1e-12)
        assertEquals(0.1, r.wellheadTop!!.meters, 1e-12) // -2.0 + 0.7 + 0.8 + 0.6
        assertEquals(-2.5, r.cellarFloor!!.meters, 1e-12)
        assertEquals(10.0 - 0.1, r.rkbToWellheadTop!!.meters, 1e-12)
        // tree: without cap 0.6 + 0.5 + 0.7 + 0.4 = 2.2 m; with cap + 0.3 m
        assertEquals(2.3, r.treeTopWithoutCap!!.meters, 1e-12)
        assertEquals(2.6, r.treeTopWithCap!!.meters, 1e-12)
        assertTrue(r.issues.none { it.severity == com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity.ERROR }, r.issues.toString())
    }

    @Test
    fun missingBaseIsAssumedOnCellarFloorAndReported() {
        val w = well.copy(wellhead = wellhead.copy(baseRelativeToGroundLevel = null))
        val r = calc.calculate(w, OperatingState.CompletedWithTree(tree))
        assertEquals(WellheadPositionBasis.CELLAR_FLOOR_ASSUMED, r.wellheadBasis)
        assertEquals(-2.5, r.wellheadBase!!.meters, 1e-12)
        assertTrue(r.issues.any { it.code == "WELLHEAD_BASE_ASSUMED" })
    }

    @Test
    fun noBaseAndNoCellarPutsTopAtGroundLevelAndReportsIt() {
        val w = well.copy(wellhead = wellhead.copy(baseRelativeToGroundLevel = null), cellar = null)
        val r = calc.calculate(w, OperatingState.CompletedWithTree(tree))
        assertEquals(WellheadPositionBasis.TOP_AT_GROUND_LEVEL_ASSUMED, r.wellheadBasis)
        assertEquals(0.0, r.wellheadTop!!.meters, 1e-12)
        assertTrue(r.issues.any { it.code == "WELLHEAD_TOP_ASSUMED_AT_GL" })
    }

    @Test
    fun missingSectionHeightLeavesElevationsAboveTheWellheadUncalculated() {
        val w = well.copy(wellhead = wellhead.copy(sections = wellhead.sections.mapIndexed { i, s -> if (i == 1) s.copy(height = null) else s }))
        val r = calc.calculate(w, OperatingState.CompletedWithTree(tree))
        assertNull(r.wellheadTop)
        assertNull(r.treeTopWithoutCap)
        assertTrue(r.issues.any { it.code == "WELLHEAD_HEIGHT_MISSING" && "cs" in it.subjectIds })
    }

    @Test
    fun connectionMismatchesAreReported() {
        val brokenWellhead = wellhead.copy(sections = wellhead.sections.mapIndexed { i, s -> if (i == 0) s.copy(bottomConnection = EndConnection("Z")) else s })
        val r1 = calc.calculate(well.copy(wellhead = brokenWellhead), OperatingState.CompletedWithTree(tree))
        assertTrue(r1.issues.any { it.code == "WELLHEAD_CONNECTION_MISMATCH" })
        val brokenTree = tree.copy(components = tree.components.map { if (it.id == "tha") it.copy(bottomConnection = EndConnection("OTHER")) else it })
        val r2 = calc.calculate(well.copy(christmasTree = brokenTree), OperatingState.CompletedWithTree(brokenTree))
        assertTrue(r2.issues.any { it.code == "TREE_WELLHEAD_CONNECTION_MISMATCH" })
    }

    @Test
    fun wellheadBelowCellarFloorIsAnError() {
        val w = well.copy(wellhead = wellhead.copy(baseRelativeToGroundLevel = m(-3.0)))
        assertTrue(calc.calculate(w, OperatingState.CompletedWithTree(tree)).issues.any { it.code == "WELLHEAD_BELOW_CELLAR_FLOOR" })
    }

    @Test
    fun recordedTreeLandingElevationIsCrossCheckedAgainstTheWellhead() {
        val t = tree.copy(wellheadTopAboveGroundLevel = m(0.8))
        val r = calc.calculate(well.copy(christmasTree = t), OperatingState.CompletedWithTree(t))
        assertTrue(r.issues.any { it.code == "WELLHEAD_TOP_MISMATCH" })
        // without a wellhead definition the recorded value is used as the wellhead top
        val legacy = calc.calculate(well.copy(wellhead = null, christmasTree = t), OperatingState.CompletedWithTree(t))
        assertEquals(WellheadPositionBasis.TREE_RECORD, legacy.wellheadBasis)
        assertEquals(0.8, legacy.wellheadTop!!.meters, 1e-12)
    }

    @Test
    fun ctStackTopIsTreeTopWithoutCapPlusStackHeights() {
        val stack = CtPressureControlStack(
            "pce", "pce",
            listOf(
                stack("strip", StackComponentType.STRIPPER_PACKOFF, "INJ", "S1", 1.0),
                stack("ctbop", StackComponentType.CT_BOP, "S1", "T0", 1.4),
            ),
            synthetic,
        )
        val r = calc.calculate(well, OperatingState.CoiledTubingIntervention(tree, CtRigUp(stack, CtSurfaceEquipment())))
        assertEquals(2.3 + 2.4, r.ctStackTop!!.meters, 1e-12)
        assertNull(r.drillingStackTop)
    }

    @Test
    fun drillingStackClearanceUnderRotaryBeams() {
        val r = calc.calculate(well, OperatingState.Drilling(rigUp()))
        // wellhead top 0.1 + 0.6 + 1.5 + 1.3 = 3.5 m
        assertEquals(3.5, r.drillingStackTop!!.meters, 1e-12)
        assertEquals(9.5, r.drillFloor!!.meters, 1e-12) // RKB 10 m above GL − 0.5 m
        assertEquals(2.5, r.clearanceUnderRotaryBeams!!.meters, 1e-12)
        assertNull(r.treeTopWithCap)
        assertFalse(r.issues.any { it.code == "BOP_EXCEEDS_SUBSTRUCTURE_CLEARANCE" })

        val tight = calc.calculate(well, OperatingState.Drilling(rigUp(clearHeight = 3.0)))
        assertEquals(-0.5, tight.clearanceUnderRotaryBeams!!.meters, 1e-12)
        assertTrue(tight.issues.any { it.code == "BOP_EXCEEDS_SUBSTRUCTURE_CLEARANCE" })
    }

    @Test
    fun drillingChecksReportMissingRigDataAndWellheadMismatch() {
        val r = calc.calculate(well, OperatingState.Drilling(rigUp(clearHeight = null, floorBelowRkb = null, bottomConnection = "OTHER")))
        assertNull(r.drillFloor)
        assertNull(r.clearanceUnderRotaryBeams)
        assertTrue(r.issues.any { it.code == "RIG_CLEAR_HEIGHT_MISSING" })
        assertTrue(r.issues.any { it.code == "RIG_FLOOR_BELOW_RKB_MISSING" })
        assertTrue(r.issues.any { it.code == "BOP_WELLHEAD_CONNECTION_MISMATCH" })
    }

    @Test
    fun drillingStackAboveTheFloorIsAnError() {
        val tall = rigUp(clearHeight = null).let { it.copy(bopStack = it.bopStack.copy(components = it.bopStack.components.map { c -> c.copy(height = m(4.0)) })) }
        val r = calc.calculate(well, OperatingState.Drilling(tall))
        assertNotNull(r.drillingStackTop)
        assertTrue(r.issues.any { it.code == "BOP_ABOVE_DRILL_FLOOR" })
    }

    @Test
    fun wingOutletMustBranchFromAFlowTeeAndHoldTreeEquipmentOnly() {
        val badWing = TreeWingOutlet(
            "w", OutletSide.RIGHT, TreeWingRole.PRODUCTION, branchComponentId = "swab",
            components = listOf(stack("x", StackComponentType.CT_BOP, "A", "B", 0.5)), provenance = synthetic,
        )
        val t = tree.copy(wingOutlets = listOf(badWing))
        val r = calc.calculate(well.copy(christmasTree = t), OperatingState.CompletedWithTree(t))
        assertTrue(r.issues.any { it.code == "TREE_WING_BRANCH_INVALID" })
        assertTrue(r.issues.any { it.code == "TREE_WING_COMPONENT_INVALID" })
    }
}
