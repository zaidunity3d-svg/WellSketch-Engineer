package com.wellsketch.engineer.domain.workover

import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.surface.DrillingBopComponent
import com.wellsketch.engineer.core.model.surface.DrillingBopComponentType
import com.wellsketch.engineer.core.model.surface.DrillingBopStack
import com.wellsketch.engineer.core.model.surface.EndConnection
import com.wellsketch.engineer.core.model.units.Density
import com.wellsketch.engineer.core.model.units.FlowRate
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.model.units.PressureGradient
import com.wellsketch.engineer.core.model.units.Volume
import com.wellsketch.engineer.core.model.units.VolumePerLength
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.model.workover.WorkString
import com.wellsketch.engineer.core.model.workover.WorkStringPipeKind
import com.wellsketch.engineer.core.model.workover.WorkStringSection
import com.wellsketch.engineer.core.model.workover.WorkoverOperation
import com.wellsketch.engineer.core.model.workover.WorkoverOperationType
import com.wellsketch.engineer.core.model.workover.WorkoverRig
import com.wellsketch.engineer.core.model.workover.WorkoverRigUp
import com.wellsketch.engineer.core.model.workover.WorkoverTool
import com.wellsketch.engineer.core.model.workover.WorkoverToolType
import com.wellsketch.engineer.domain.welldesign.EditOutcome
import com.wellsketch.engineer.domain.welldesign.WellProjects
import com.wellsketch.engineer.domain.welldesign.WellStructureEdits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Workover calculations on a synthetic completed well (not real data). Expected values are
 * closed-form results computed independently (Python, π/4·D² geometry):
 * GL MD 11.1 m (drilling RKB 11.1 m above GL); workover RKB 6.0 m above GL, so
 * well MD = workover depth + 5.1 m. 9-5/8 in 47 lb/ft to 1950 m, 7 in 26 lb/ft to 2800 m,
 * 5-1/2 in 17 lb/ft liner 2650–3300 m.
 */
class WorkoverCalculatorTest {
    private val calc = WorkoverCalculator()
    private val p = DataProvenance.userEntered("synthetic test")
    private fun md(m: Double) = MeasuredDepth.rkb(Length.meters(m))
    private fun Well.apply(edit: (Well) -> EditOutcome): Well = assertIs<EditOutcome.Applied>(edit(this)).well
    private fun spec(id: String, grade: String? = null) = TubularCatalog.byId(id)!!.toSpec(grade, null)
    private fun near(expected: Double, actual: Double, tol: Double = 1e-9, what: String = "") =
        assertTrue(kotlin.math.abs(expected - actual) <= tol, "$what expected $expected, was $actual")

    private val well: Well = WellProjects.newWell("WO test", Length.meters(11.1), Length.meters(20.0), nowMillis = 1L, id = "wo").well
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(12.25), md(1950.0), id = "h1") }
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(8.5), md(2800.0), id = "h2") }
        .apply { WellStructureEdits.addHoleSection(it, Length.inches(6.125), md(3300.0), id = "h3") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.INTERMEDIATE, spec("c-9p625-47-api"), md(11.1), md(1950.0), id = "int") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.PRODUCTION, spec("c-7-26-api"), md(11.1), md(2800.0), id = "prod") }
        .apply { WellStructureEdits.addCasingString(it, CasingRole.PRODUCTION_LINER, spec("c-5p5-17-api"), md(2650.0), md(3300.0), id = "lin") }

    private val woRkb = DepthReference.Operation("wo-rkb", "Workover RKB", Length.meters(6.0))
    private fun op(m: Double) = Length.meters(m - 5.1) // well MD → workover depth

    private val bop = DrillingBopStack(
        "bop", "Workover BOP (synthetic)",
        listOf(
            DrillingBopComponent(
                "ann", DrillingBopComponentType.ANNULAR_PREVENTER, "Annular", Length.inches(7.0625), null,
                EndConnection("7-1/16 in (synthetic)"), EndConnection("7-1/16 in (synthetic)"), provenance = p,
            ),
        ),
        p,
    )
    private val rigUp = WorkoverRigUp(WorkoverRig("rig", "Workover rig (synthetic)", provenance = p), bop)

    private fun section(id: String, kind: WorkStringPipeKind, catalogue: String, length: Double = 0.0, joint: Double? = null) =
        WorkStringSection(id, kind, catalogue, spec(catalogue), Length.meters(length), joint?.let { Length.inches(it) })

    private fun operation(
        type: WorkoverOperationType,
        bottomWellMd: Double,
        sections: List<WorkStringSection>,
        tools: List<WorkoverTool> = emptyList(),
    ) = WorkoverOperation(
        id = "op", name = type.label, type = type, depthReference = woRkb, bottomDepth = op(bottomWellMd),
        workString = WorkString("ws", "Work string", sections, p), tools = tools, rigUp = rigUp, provenance = p,
    )

    private fun tool(id: String, type: WorkoverToolType, length: Double, od: Double, id_: Double?) =
        WorkoverTool(id, type, type.label, Length.meters(length), Length.inches(od), id_?.let { Length.inches(it) }, provenance = p)

    @Test
    fun balancedCementPlugOnDrillPipeMatchesTheClosedForm() {
        val o = operation(
            WorkoverOperationType.CEMENT_PLUG, 2500.0,
            listOf(section("dp", WorkStringPipeKind.DRILL_PIPE, "dp-3p5-13p3-eu", joint = 4.75)),
        ).copy(
            plugLength = Length.meters(150.0), spacerAheadVolume = Volume.barrels(10.0), wellFluidDensity = Density(1100.0),
            pumpRate = FlowRate.cubicMetersPerMinute(0.5),
        )
        val a = calc.analyze(well, o)
        near(5.1, a.rigFloorMd.meters, 1e-9, "rig floor")
        near(2500.0, a.bottomMd.meters, 1e-9, "bottom")
        near(9.657985188461813, a.stringVolume.total.cubicMeters, what = "string")
        near(34.23148662956179, a.annulus.total.cubicMeters, what = "annulus")
        near(5.814258356036159, a.openEndDisplacement.cubicMeters, what = "open end")
        near(15.449016997890585, a.closedEndDisplacement.cubicMeters, what = "closed end")
        near(19.315970376923627, a.surfaceToBottomMinutes!!, 1e-9, "to bottom")
        near(68.46297325912359, a.bottomsUpMinutes!!, 1e-9, "bottoms up")
        near(26913272.1935, a.hydrostaticAtTarget!!.pascals, 1e-3, "hydrostatic")
        val plug = assertNotNull(a.cementPlug)
        near(2.994124128778921, plug.slurryVolume.cubicMeters, what = "slurry")
        near(2330.118226485782, plug.topWithPipeIn.meters, 1e-9, "top with pipe in")
        near(0.44748398757235497, plug.spacerBehind.cubicMeters, what = "spacer behind")
        near(8.55287337917719, plug.displacement.cubicMeters, what = "displacement")
        near(2350.0, plug.plugTop.meters, 1e-9, "plug top")
        // tool joint 4.75 in against the 7 in drift 6.1512 in is the tightest passage
        val t = assertNotNull(a.tightest)
        assertEquals("drift", t.basis)
        near(6.1512 - 4.75, t.clearance.inches, 1e-9, "clearance")
        assertTrue(a.issues.none { it.severity == Severity.ERROR }, "${a.issues}")
    }

    @Test
    fun acidDisplacementRunsThroughTubingPackerAndCasingIntoTheLiner() {
        val o = operation(
            WorkoverOperationType.ACIDIZING, 2600.0,
            listOf(section("tbg", WorkStringPipeKind.TUBING, "t-2p875-6p5-eu")),
            listOf(tool("pk", WorkoverToolType.PACKER, 2.0, 5.875, 2.0)),
        ).copy(
            targetTop = op(2900.0), targetBottom = op(2950.0), treatmentVolumePerLength = VolumePerLength.cubicMetersPerMeter(0.15),
            fractureGradient = PressureGradient.psiPerFoot(0.8), pumpedFluidDensity = Density(1070.0),
        )
        val a = calc.analyze(well, o)
        val acid = assertNotNull(a.acid)
        near(7.5, acid.treatmentVolume.cubicMeters, 1e-12, "acid")
        near(11.861667394262188, acid.displacementToTop.cubicMeters, what = "displacement")
        near(22010967.83404224, acid.maxSurfacePressure!!.pascals, 1e-3, "MASP")
    }

    @Test
    fun perforationCountsShotsRunsAndBalance() {
        val o = operation(
            WorkoverOperationType.PERFORATION, 2960.0,
            listOf(section("tbg", WorkStringPipeKind.TUBING, "t-2p875-6p5-eu")),
            listOf(tool("gun", WorkoverToolType.PERFORATING_GUN, 10.0, 3.375, null)),
        ).copy(
            targetTop = op(2950.0), targetBottom = op(2960.0), shotDensityPerMeter = 20.0, gunLengthPerRun = Length.meters(10.0),
            wellFluidDensity = Density(1050.0), reservoirPressure = Pressure(30e6), killOverbalance = Pressure(1.5e6),
        )
        val a = calc.analyze(well, o)
        val perf = assertNotNull(a.perforation)
        assertEquals(200, perf.shots)
        assertEquals(1, perf.gunRuns)
        near(375068.67675, a.balance!!.pascals, 1e-3, "overbalance")
        near(1088.8864269570067, a.killDensity!!.kilogramsPerCubicMeter, 1e-9, "kill density")
        assertTrue(a.issues.none { it.code == "WO_GUN_COVERAGE" })
        // a closed gun: no inside volume below its top
        near(2950.0, a.stringVolume.segments.last().bottomMd.meters, 1e-9, "string bore ends at the gun")
    }

    @Test
    fun packerChecksHostAndLinerTop() {
        val base = operation(
            WorkoverOperationType.SET_PACKER, 2651.0,
            listOf(section("tbg", WorkStringPipeKind.TUBING, "t-2p875-6p5-eu")),
            listOf(tool("pk", WorkoverToolType.PACKER, 2.0, 4.5, 2.0)),
        )
        val across = calc.analyze(well, base)
        assertTrue(across.issues.any { it.code == "WO_PACKER_ACROSS" })
        val inCasing = calc.analyze(well, base.copy(bottomDepth = op(2400.0)))
        val pk = assertNotNull(inCasing.packer)
        assertEquals("Production casing", pk.hostName)
        assertTrue(inCasing.issues.none { it.code == "WO_PACKER_ACROSS" || it.code == "WO_PACKER_OPEN_HOLE" })
        // packer fluid = annulus from the well top to the packer top
        val expected = inCasing.annulus.segments.filter { it.bottomMd <= pk.packerTop }.sumOf { it.volume.cubicMeters }
        near(expected, pk.packerFluidVolume.cubicMeters, 1e-12, "packer fluid")
    }

    @Test
    fun espMotorBelowPerforationsIsFlagged() {
        val esp = listOf(
            tool("pump", WorkoverToolType.ESP_PUMP, 6.0, 4.0, 1.5),
            tool("intake", WorkoverToolType.ESP_INTAKE, 1.0, 4.0, null),
            tool("seal", WorkoverToolType.ESP_SEAL, 2.0, 4.0, null),
            tool("motor", WorkoverToolType.ESP_MOTOR, 8.0, 4.56, null),
        )
        val o = operation(WorkoverOperationType.ESP_CHANGE, 2620.0, listOf(section("tbg", WorkStringPipeKind.TUBING, "t-2p875-6p5-eu")), esp)
            .copy(targetTop = op(2615.0))
        val a = calc.analyze(well, o)
        val r = assertNotNull(a.esp)
        near(2620.0 - 11.0, r.intakeDepth!!.meters, 1e-9, "intake")
        near(4.56, r.maxOuterDiameter.inches, 1e-12, "max OD")
        assertTrue(a.issues.any { it.code == "WO_ESP_BELOW_PERFS" })
        assertTrue(calc.analyze(well, o.copy(targetTop = op(2900.0))).issues.none { it.code == "WO_ESP_BELOW_PERFS" })
    }

    @Test
    fun clearanceWarnsOnDriftAndFailsOnId() {
        val s = listOf(section("tbg", WorkStringPipeKind.TUBING, "t-2p875-6p5-eu"))
        val drift = calc.analyze(well, operation(WorkoverOperationType.OTHER, 2500.0, s, listOf(tool("x", WorkoverToolType.SCRAPER, 2.0, 6.2, 2.0))))
        assertEquals(Severity.WARNING, drift.issues.single { it.code == "WO_CLEARANCE" }.severity)
        val id = calc.analyze(well, operation(WorkoverOperationType.OTHER, 2500.0, s, listOf(tool("x", WorkoverToolType.SCRAPER, 2.0, 6.3, 2.0))))
        assertTrue(id.issues.any { it.code == "WO_CLEARANCE" && it.severity == Severity.ERROR })
        // going into the liner, the liner drift governs
        val deep = calc.analyze(well, operation(WorkoverOperationType.OTHER, 3000.0, s, listOf(tool("x", WorkoverToolType.SCRAPER, 2.0, 4.8, 2.0))))
        assertTrue(deep.issues.any { it.code == "WO_CLEARANCE" && it.message.contains("Production liner") }, "${deep.issues} ${deep.clearances}")
    }

    @Test
    fun taperedStringLayoutAndStringTooLong() {
        val tapered = listOf(
            section("dp", WorkStringPipeKind.DRILL_PIPE, "dp-3p5-13p3-eu"),
            section("tbg", WorkStringPipeKind.TUBING, "t-2p875-6p5-eu", length = 500.0),
        )
        val a = calc.analyze(well, operation(WorkoverOperationType.CIRCULATION, 3000.0, tapered))
        val dp = a.items.single { it.id == "dp" }
        val tbg = a.items.single { it.id == "tbg" }
        near(5.1, dp.top.meters, 1e-9, "dp top")
        near(2500.0, dp.bottom.meters, 1e-9, "dp bottom")
        near(3000.0, tbg.bottom.meters, 1e-9, "tubing bottom")
        // 3-1/2 in DP does not need to pass the liner: only the 2-7/8 in tubing goes below 2650 m
        assertTrue(a.issues.none { it.code == "WO_CLEARANCE" })
        val tooLong = calc.analyze(well, operation(WorkoverOperationType.CIRCULATION, 400.0, tapered))
        assertTrue(tooLong.issues.any { it.code == "WO_STRING_TOO_LONG" })
        assertTrue(calc.analyze(well, operation(WorkoverOperationType.CIRCULATION, 3400.0, tapered)).issues.any { it.code == "WO_BELOW_TD" })
    }
}
