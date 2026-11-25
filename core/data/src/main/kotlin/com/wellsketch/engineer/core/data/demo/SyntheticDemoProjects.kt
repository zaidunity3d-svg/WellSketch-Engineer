package com.wellsketch.engineer.core.data.demo

import com.wellsketch.engineer.core.data.demo.Demo.casing
import com.wellsketch.engineer.core.data.demo.Demo.component
import com.wellsketch.engineer.core.data.demo.Demo.formation
import com.wellsketch.engineer.core.data.demo.Demo.hole
import com.wellsketch.engineer.core.data.demo.Demo.inch
import com.wellsketch.engineer.core.data.demo.Demo.rkb
import com.wellsketch.engineer.core.data.demo.Demo.spec
import com.wellsketch.engineer.core.data.demo.Demo.stackComponent
import com.wellsketch.engineer.core.data.demo.Demo.synthetic
import com.wellsketch.engineer.core.data.demo.SyntheticSurveyGenerator.Segment.Curve
import com.wellsketch.engineer.core.data.demo.SyntheticSurveyGenerator.Segment.Straight
import com.wellsketch.engineer.core.model.ct.CtBha
import com.wellsketch.engineer.core.model.ct.CtBhaComponent
import com.wellsketch.engineer.core.model.ct.CtBhaComponentType
import com.wellsketch.engineer.core.model.ct.CtOperation
import com.wellsketch.engineer.core.model.ct.CtRigUp
import com.wellsketch.engineer.core.model.ct.CtString
import com.wellsketch.engineer.core.model.ct.CtStringSection
import com.wellsketch.engineer.core.model.ct.CtSurfaceEquipment
import com.wellsketch.engineer.core.model.ct.CtSurfaceUnit
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.WellDatums
import com.wellsketch.engineer.core.model.geology.Lithology
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.surface.Cellar
import com.wellsketch.engineer.core.model.surface.ChristmasTree
import com.wellsketch.engineer.core.model.surface.CtPressureControlStack
import com.wellsketch.engineer.core.model.surface.DrillingBopComponent
import com.wellsketch.engineer.core.model.surface.DrillingBopComponentType
import com.wellsketch.engineer.core.model.surface.DrillingBopStack
import com.wellsketch.engineer.core.model.surface.DrillingRig
import com.wellsketch.engineer.core.model.surface.DrillingRigUp
import com.wellsketch.engineer.core.model.surface.EndConnection
import com.wellsketch.engineer.core.model.surface.OutletFitting
import com.wellsketch.engineer.core.model.surface.OutletSide
import com.wellsketch.engineer.core.model.surface.RamType
import com.wellsketch.engineer.core.model.surface.SideOutlet
import com.wellsketch.engineer.core.model.surface.StackComponentType
import com.wellsketch.engineer.core.model.surface.TreeWingOutlet
import com.wellsketch.engineer.core.model.surface.TreeWingRole
import com.wellsketch.engineer.core.model.surface.ValveOperator
import com.wellsketch.engineer.core.model.surface.Wellhead
import com.wellsketch.engineer.core.model.surface.WellheadSection
import com.wellsketch.engineer.core.model.surface.WellheadSectionType
import com.wellsketch.engineer.core.model.trajectory.AzimuthReference
import com.wellsketch.engineer.core.model.trajectory.DirectionalSurvey
import com.wellsketch.engineer.core.model.trajectory.SurveyKind
import com.wellsketch.engineer.core.model.trajectory.TrajectoryType
import com.wellsketch.engineer.core.model.units.Angle
import com.wellsketch.engineer.core.model.units.FlowRate
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.ComponentMounting
import com.wellsketch.engineer.core.model.well.Completion
import com.wellsketch.engineer.core.model.well.CompletionComponentType
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.PerforationInterval
import com.wellsketch.engineer.core.model.well.PerforationStatus
import com.wellsketch.engineer.core.model.well.TubingConfiguration
import com.wellsketch.engineer.core.model.well.TubingString
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.model.well.WellLocationType

/**
 * SYNTHETIC DEMONSTRATION PROJECTS.
 *
 * These wells are invented for demonstrating the UI and testing geometry handling. Names,
 * depths, diameters, ratings and formations are illustrative only and do not describe any
 * real well, field, operator, standard table or manufacturer product.
 */
object SyntheticDemoProjects {

    const val NAME_PREFIX = "[SYNTHETIC DEMO]"
    const val VERTICAL_ID = "demo-vertical"
    const val J_SHAPED_ID = "demo-j-shaped"
    const val S_SHAPED_ID = "demo-s-shaped"
    const val HORIZONTAL_ID = "demo-horizontal"

    private val projectProvenance = DataProvenance.syntheticDemo(
        "Entire project is synthetic demonstration data. Not for engineering use.",
    )

    // Synthetic datums shared by all demo wells: GL 250 m above MSL, RKB 9 m above GL.
    private val datums = WellDatums(
        rkbElevation = Length.meters(259.0),
        groundLevelElevation = Length.meters(250.0),
        provenance = synthetic,
    )
    private const val GL_MD = 9.0

    /** Elevation of the wellhead top flange (tree landing) relative to GL in the demos. */
    private const val TREE_LANDING_ABOVE_GL = 0.0

    // Synthetic tubular "specs" (round illustrative numbers, NOT catalog values).
    private val conductorSpec = spec(20.0, 19.0, SyntheticEquipmentCatalog.CONDUCTOR_20, "DEMO")
    private val surfaceSpec = spec(13.375, 12.4, SyntheticEquipmentCatalog.CASING_13_375, "DEMO")
    private val intermediateSpec = spec(9.625, 8.7, SyntheticEquipmentCatalog.CASING_9_625, "DEMO")
    private val productionSpec = spec(7.0, 6.2, SyntheticEquipmentCatalog.CASING_7, "DEMO")
    private val linerSpec = spec(4.5, 3.9, SyntheticEquipmentCatalog.LINER_4_5, "DEMO")
    private val tubingSpec = spec(3.5, 2.9, SyntheticEquipmentCatalog.TUBING_3_5, "DEMO")

    /**
     * Upgrades a stored SYNTHETIC demo project created by an earlier app version (before
     * wellhead, cellar and drilling rig-up data existed) with the current demo surface
     * equipment. Everything else in the stored project, including user edits, is kept.
     * Returns null when [stored] is not a demo project or is already up to date; real
     * (non-synthetic) projects are never touched.
     */
    fun withCurrentSurfaceEquipment(stored: WellProject): WellProject? {
        if (!stored.isSyntheticDemo || stored.well.wellhead != null) return null
        val fresh = all().firstOrNull { it.id == stored.id } ?: return null
        return stored.copy(
            well = stored.well.copy(
                wellhead = fresh.well.wellhead,
                cellar = fresh.well.cellar,
                drillingRigUp = fresh.well.drillingRigUp,
                christmasTree = fresh.well.christmasTree,
            ),
        )
    }

    fun all(nowEpochMillis: Long = 0L): List<WellProject> = listOf(
        vertical(nowEpochMillis),
        jShaped(nowEpochMillis),
        sShaped(nowEpochMillis),
        horizontal(nowEpochMillis),
    )

    fun vertical(now: Long = 0L): WellProject {
        val id = VERTICAL_ID
        val well = Well(
            id = "$id-well",
            name = "DEMO-V1",
            locationType = WellLocationType.ONSHORE,
            datums = datums,
            declaredTrajectoryType = TrajectoryType.VERTICAL,
            formations = listOf(
                formation("$id-f1", "Formation A (demo)", 0.0, Lithology.UNCONSOLIDATED),
                formation("$id-f2", "Formation B (demo)", 150.0, Lithology.SANDSTONE),
                formation("$id-f3", "Formation C (demo)", 700.0, Lithology.SHALE),
                formation("$id-f4", "Formation D (demo)", 1250.0, Lithology.LIMESTONE),
                formation("$id-f5", "Formation E (demo)", 1800.0, Lithology.ANHYDRITE),
                formation("$id-f6", "Formation F (demo)", 2050.0, Lithology.SHALE),
                formation("$id-f7", "Reservoir G (demo)", 2280.0, Lithology.SANDSTONE),
                formation("$id-f8", "Formation H (demo)", 2380.0, Lithology.DOLOMITE),
            ),
            holeSections = listOf(
                hole("$id-h1", 26.0, "26 in hole (demo)", GL_MD, 60.0),
                hole("$id-h2", 17.5, "17-1/2 in hole (demo)", 60.0, 600.0),
                hole("$id-h3", 12.25, "12-1/4 in hole (demo)", 600.0, 1700.0),
                hole("$id-h4", 8.5, "8-1/2 in hole (demo)", 1700.0, 2400.0),
            ),
            casingStrings = listOf(
                casing("$id-c1", "20 in conductor (demo)", CasingRole.CONDUCTOR, conductorSpec, GL_MD, 58.0, GL_MD),
                casing("$id-c2", "13-3/8 in surface casing (demo)", CasingRole.SURFACE, surfaceSpec, GL_MD, 595.0, GL_MD),
                casing("$id-c3", "9-5/8 in intermediate casing (demo)", CasingRole.INTERMEDIATE, intermediateSpec, GL_MD, 1695.0, 400.0),
                casing("$id-c4", "7 in production casing (demo)", CasingRole.PRODUCTION, productionSpec, GL_MD, 2390.0, 1500.0),
            ),
            surveys = listOf(survey(id, azimuthDeg = 0.0, listOf(Straight(2400.0)))),
            completions = listOf(
                tubingCompletion(
                    id, CompletionState.AS_DESIGNED, eot = 2250.0, packerTop = 2200.0,
                    perforations = listOf(2300.0 to 2320.0, 2340.0 to 2355.0), hostId = "$id-c4",
                ),
                tubingCompletion(
                    id, CompletionState.CURRENT, eot = 2250.0, packerTop = 2200.0,
                    perforations = listOf(2300.0 to 2320.0, 2340.0 to 2355.0), hostId = "$id-c4",
                ),
            ),
            christmasTree = christmasTree(id),
            provenance = synthetic,
            wellhead = wellhead(id),
            cellar = cellar,
            drillingRigUp = drillingRigUp(id),
        )
        return project(id, "Vertical producer", well, ctOperation(id, currentDepth = 2000.0), now)
    }

    fun jShaped(now: Long = 0L): WellProject {
        val id = J_SHAPED_ID
        val well = Well(
            id = "$id-well",
            name = "DEMO-J1",
            locationType = WellLocationType.ONSHORE,
            datums = datums,
            declaredTrajectoryType = TrajectoryType.J_SHAPED,
            formations = listOf(
                formation("$id-f1", "Formation A (demo)", 0.0, Lithology.UNCONSOLIDATED),
                formation("$id-f2", "Formation B (demo)", 140.0, Lithology.SANDSTONE),
                formation("$id-f3", "Formation C (demo)", 650.0, Lithology.SHALE),
                formation("$id-f4", "Formation D (demo)", 1100.0, Lithology.LIMESTONE),
                formation("$id-f5", "Formation E (demo)", 1700.0, Lithology.MARL),
                formation("$id-f6", "Formation F (demo)", 2200.0, Lithology.SHALE),
                formation("$id-f7", "Reservoir G (demo)", 2520.0, Lithology.SANDSTONE),
                formation("$id-f8", "Formation H (demo)", 2700.0, Lithology.SHALE),
            ),
            holeSections = listOf(
                hole("$id-h1", 26.0, "26 in hole (demo)", GL_MD, 60.0),
                hole("$id-h2", 17.5, "17-1/2 in hole (demo)", 60.0, 600.0),
                hole("$id-h3", 12.25, "12-1/4 in hole (demo)", 600.0, 1810.0),
                hole("$id-h4", 8.5, "8-1/2 in hole (demo)", 1810.0, 3100.0),
            ),
            casingStrings = listOf(
                casing("$id-c1", "20 in conductor (demo)", CasingRole.CONDUCTOR, conductorSpec, GL_MD, 58.0, GL_MD),
                casing("$id-c2", "13-3/8 in surface casing (demo)", CasingRole.SURFACE, surfaceSpec, GL_MD, 595.0, GL_MD),
                casing("$id-c3", "9-5/8 in intermediate casing (demo)", CasingRole.INTERMEDIATE, intermediateSpec, GL_MD, 1800.0, 500.0),
                casing("$id-c4", "7 in production casing (demo)", CasingRole.PRODUCTION, productionSpec, GL_MD, 3090.0, 1600.0),
            ),
            surveys = listOf(
                survey(id, azimuthDeg = 60.0, listOf(Straight(900.0), Curve(3.0, 40.0), Straight(3100.0))),
            ),
            completions = listOf(
                tubingCompletion(
                    id, CompletionState.AS_DESIGNED, eot = 2900.0, packerTop = 2850.0,
                    perforations = listOf(2960.0 to 2990.0, 3010.0 to 3040.0), hostId = "$id-c4",
                ),
                tubingCompletion(
                    id, CompletionState.CURRENT, eot = 2900.0, packerTop = 2850.0,
                    perforations = listOf(2960.0 to 2990.0, 3010.0 to 3040.0), hostId = "$id-c4",
                ),
            ),
            christmasTree = christmasTree(id),
            provenance = synthetic,
            wellhead = wellhead(id),
            cellar = cellar,
            drillingRigUp = drillingRigUp(id),
        )
        return project(id, "J-shaped (build and hold)", well, ctOperation(id, currentDepth = 2500.0), now)
    }

    /** S-shaped demo; its CURRENT completion is explicitly tubingless to demonstrate the casing flow path. */
    fun sShaped(now: Long = 0L): WellProject {
        val id = S_SHAPED_ID
        val perforations = listOf(
            PerforationInterval("$id-p1", rkb(2600.0), rkb(2630.0), "$id-c4", PerforationStatus.OPEN, synthetic),
            PerforationInterval("$id-p2", rkb(2750.0), rkb(2780.0), "$id-c4", PerforationStatus.ISOLATED, synthetic),
        )
        val bridgePlug = component(
            "$id-bp", CompletionComponentType.BRIDGE_PLUG, "Bridge plug (demo)", 2700.0, 0.8,
            mounting = ComponentMounting.IN_CASING, odIn = 5.9, blocking = true,
        )
        val well = Well(
            id = "$id-well",
            name = "DEMO-S1",
            locationType = WellLocationType.ONSHORE,
            datums = datums,
            declaredTrajectoryType = TrajectoryType.S_SHAPED,
            formations = listOf(
                formation("$id-f1", "Formation A (demo)", 0.0, Lithology.UNCONSOLIDATED),
                formation("$id-f2", "Formation B (demo)", 160.0, Lithology.CLAYSTONE),
                formation("$id-f3", "Formation C (demo)", 720.0, Lithology.SANDSTONE),
                formation("$id-f4", "Formation D (demo)", 1300.0, Lithology.LIMESTONE),
                formation("$id-f5", "Formation E (demo)", 1900.0, Lithology.SALT),
                formation("$id-f6", "Formation F (demo)", 2350.0, Lithology.SHALE),
                formation("$id-f7", "Reservoir G upper (demo)", 2450.0, Lithology.SANDSTONE),
                formation("$id-f8", "Reservoir G lower (demo)", 2620.0, Lithology.SANDSTONE),
                formation("$id-f9", "Formation H (demo)", 2760.0, Lithology.SHALE),
            ),
            holeSections = listOf(
                hole("$id-h1", 26.0, "26 in hole (demo)", GL_MD, 60.0),
                hole("$id-h2", 17.5, "17-1/2 in hole (demo)", 60.0, 600.0),
                hole("$id-h3", 12.25, "12-1/4 in hole (demo)", 600.0, 1910.0),
                hole("$id-h4", 8.5, "8-1/2 in hole (demo)", 1910.0, 2900.0),
            ),
            casingStrings = listOf(
                casing("$id-c1", "20 in conductor (demo)", CasingRole.CONDUCTOR, conductorSpec, GL_MD, 58.0, GL_MD),
                casing("$id-c2", "13-3/8 in surface casing (demo)", CasingRole.SURFACE, surfaceSpec, GL_MD, 595.0, GL_MD),
                casing("$id-c3", "9-5/8 in intermediate casing (demo)", CasingRole.INTERMEDIATE, intermediateSpec, GL_MD, 1900.0, 450.0),
                casing("$id-c4", "7 in production casing (demo)", CasingRole.PRODUCTION, productionSpec, GL_MD, 2890.0, 1700.0),
            ),
            surveys = listOf(
                survey(
                    id, azimuthDeg = 135.0,
                    listOf(Straight(700.0), Curve(2.5, 25.0), Straight(1800.0), Curve(2.0, 0.0), Straight(2900.0)),
                ),
            ),
            completions = listOf(
                Completion(
                    id = "$id-cmp-current",
                    state = CompletionState.CURRENT,
                    name = "Current (tubingless, demo)",
                    tubing = TubingConfiguration.ExplicitlyNone(
                        "Synthetic demo: tubingless completion used to demonstrate the casing flow path.",
                    ),
                    components = listOf(bridgePlug),
                    perforations = perforations,
                    provenance = synthetic,
                ),
            ),
            christmasTree = christmasTree(id),
            provenance = synthetic,
            wellhead = wellhead(id),
            cellar = cellar,
            drillingRigUp = drillingRigUp(id),
        )
        return project(id, "S-shaped (tubingless current state)", well, ctOperation(id, currentDepth = 2400.0), now)
    }

    fun horizontal(now: Long = 0L): WellProject {
        val id = HORIZONTAL_ID
        val well = Well(
            id = "$id-well",
            name = "DEMO-H1",
            locationType = WellLocationType.ONSHORE,
            datums = datums,
            declaredTrajectoryType = TrajectoryType.HORIZONTAL,
            formations = listOf(
                formation("$id-f1", "Formation A (demo)", 0.0, Lithology.UNCONSOLIDATED),
                formation("$id-f2", "Formation B (demo)", 180.0, Lithology.SANDSTONE),
                formation("$id-f3", "Formation C (demo)", 760.0, Lithology.SHALE),
                formation("$id-f4", "Formation D (demo)", 1200.0, Lithology.LIMESTONE),
                formation("$id-f5", "Formation E (demo)", 1550.0, Lithology.SHALE),
                formation("$id-f6", "Reservoir G (demo)", 1690.0, Lithology.CHALK),
                formation("$id-f7", "Formation H (demo)", 1740.0, Lithology.SHALE),
            ),
            holeSections = listOf(
                hole("$id-h1", 26.0, "26 in hole (demo)", GL_MD, 60.0),
                hole("$id-h2", 17.5, "17-1/2 in hole (demo)", 60.0, 600.0),
                hole("$id-h3", 12.25, "12-1/4 in hole (demo)", 600.0, 1410.0),
                hole("$id-h4", 8.5, "8-1/2 in hole (demo)", 1410.0, 1840.0),
                hole("$id-h5", 6.0, "6 in hole (demo)", 1840.0, 2700.0),
            ),
            casingStrings = listOf(
                casing("$id-c1", "20 in conductor (demo)", CasingRole.CONDUCTOR, conductorSpec, GL_MD, 58.0, GL_MD),
                casing("$id-c2", "13-3/8 in surface casing (demo)", CasingRole.SURFACE, surfaceSpec, GL_MD, 595.0, GL_MD),
                casing("$id-c3", "9-5/8 in intermediate casing (demo)", CasingRole.INTERMEDIATE, intermediateSpec, GL_MD, 1400.0, 450.0),
                casing("$id-c4", "7 in production casing (demo)", CasingRole.PRODUCTION, productionSpec, GL_MD, 1830.0, 1200.0),
                casing("$id-c5", "4-1/2 in production liner (demo)", CasingRole.PRODUCTION_LINER, linerSpec, 1750.0, 2690.0, 1750.0),
            ),
            surveys = listOf(
                survey(id, azimuthDeg = 0.0, listOf(Straight(1500.0), Curve(8.0, 90.0), Straight(2700.0))),
            ),
            completions = listOf(
                tubingCompletion(
                    id, CompletionState.CURRENT, eot = 1700.0, packerTop = 1650.0,
                    perforations = listOf(2100.0 to 2200.0, 2400.0 to 2500.0), hostId = "$id-c5",
                ),
            ),
            christmasTree = christmasTree(id),
            provenance = synthetic,
            wellhead = wellhead(id),
            cellar = cellar,
            drillingRigUp = drillingRigUp(id),
        )
        return project(id, "Horizontal with liner", well, ctOperation(id, currentDepth = 2200.0), now)
    }

    // ---------------------------------------------------------------------------------

    private fun project(id: String, title: String, well: Well, ct: CtOperation, now: Long) = WellProject(
        id = id,
        name = "$NAME_PREFIX $title",
        description = "Synthetic demonstration project. All values are illustrative and not real well data.",
        lastModifiedEpochMillis = now,
        well = well,
        ctOperations = listOf(ct),
        provenance = projectProvenance,
    )

    private fun survey(id: String, azimuthDeg: Double, segments: List<SyntheticSurveyGenerator.Segment>) =
        DirectionalSurvey(
            id = "$id-survey",
            name = "Synthetic survey (demo)",
            kind = SurveyKind.DEFINITIVE_ACTUAL,
            mdReference = DepthReference.Rkb,
            azimuthReference = AzimuthReference.TRUE_NORTH,
            verticalSectionAzimuth = Angle.degrees(azimuthDeg),
            stations = SyntheticSurveyGenerator.generate(azimuthDeg, segments),
            provenance = DataProvenance.syntheticDemo("Stations generated from a geometric profile; not a real survey."),
        )

    private fun tubingCompletion(
        id: String,
        state: CompletionState,
        eot: Double,
        packerTop: Double,
        perforations: List<Pair<Double, Double>>,
        hostId: String,
    ): Completion {
        val prefix = "$id-${state.name.lowercase()}"
        return Completion(
            id = "$prefix-cmp",
            state = state,
            name = "${state.label} completion (demo)",
            tubing = TubingConfiguration.Installed(
                TubingString(
                    id = "$prefix-tbg",
                    name = "3-1/2 in production tubing (demo)",
                    spec = tubingSpec,
                    hangerDepth = rkb(GL_MD),
                    endOfTubing = rkb(eot),
                    provenance = synthetic,
                ),
            ),
            components = listOf(
                component("$prefix-hgr", CompletionComponentType.TUBING_HANGER, "Tubing hanger (demo)", GL_MD, 0.5, odIn = 5.5, minIdIn = 2.9),
                component("$prefix-sssv", CompletionComponentType.SAFETY_VALVE, "Safety valve (demo)", 300.0, 1.5, odIn = 5.0, minIdIn = 2.75),
                component("$prefix-pkr", CompletionComponentType.PACKER, "Production packer (demo)", packerTop, 2.0, odIn = 5.9, minIdIn = 2.9),
                component("$prefix-xn", CompletionComponentType.LANDING_NIPPLE, "Landing nipple (demo)", packerTop + 20.0, 0.5, odIn = 4.3, minIdIn = 2.65),
                component("$prefix-weg", CompletionComponentType.WIRELINE_ENTRY_GUIDE, "Wireline entry guide (demo)", eot - 0.5, 0.5, odIn = 4.3, minIdIn = 2.9),
            ),
            perforations = perforations.mapIndexed { i, (top, bottom) ->
                PerforationInterval("$prefix-perf${i + 1}", rkb(top), rkb(bottom), hostId, PerforationStatus.OPEN, synthetic)
            },
            provenance = synthetic,
        )
    }

    /**
     * Synthetic tree; top connection of the swab valve is where the CT adapter lands. It sits
     * on the wellhead top flange at GL; without the cap the tree is 3.0 m tall.
     */
    private fun christmasTree(id: String) = ChristmasTree(
        id = "$id-tree",
        name = "Christmas Tree (demo)",
        components = listOf(
            stackComponent("$id-t-cap", StackComponentType.TREE_CAP, "Tree cap (demo)", 3.0, 5000.0, "DEMO-CAP", "DEMO-TREE-TOP", 0.3),
            stackComponent("$id-t-swab", StackComponentType.SWAB_VALVE, "Swab valve (demo)", 3.0, 5000.0, "DEMO-TREE-TOP", "DEMO-T1", 0.6, ValveOperator.MANUAL),
            stackComponent("$id-t-tee", StackComponentType.FLOW_TEE, "Flow cross (demo)", 3.0, 5000.0, "DEMO-T1", "DEMO-T2", 0.5),
            stackComponent("$id-t-umv", StackComponentType.UPPER_MASTER_VALVE, "Upper master valve (demo)", 3.0, 5000.0, "DEMO-T2", "DEMO-T3", 0.7, ValveOperator.HYDRAULIC),
            stackComponent("$id-t-lmv", StackComponentType.LOWER_MASTER_VALVE, "Lower master valve (demo)", 3.0, 5000.0, "DEMO-T3", "DEMO-T4", 0.7, ValveOperator.MANUAL),
            stackComponent("$id-t-tha", StackComponentType.TUBING_HEAD_ADAPTER, "Tubing head adapter (demo)", 3.0, 5000.0, "DEMO-T4", "DEMO-WELLHEAD", 0.5),
        ),
        wellheadTopAboveGroundLevel = Length.meters(TREE_LANDING_ABOVE_GL),
        provenance = synthetic,
        wingOutlets = listOf(
            TreeWingOutlet(
                id = "$id-wing-prod", side = OutletSide.RIGHT, role = TreeWingRole.PRODUCTION, branchComponentId = "$id-t-tee",
                components = listOf(
                    stackComponent("$id-t-pwv", StackComponentType.WING_VALVE, "Production wing valve (demo)", 2.0, 5000.0, "DEMO-W", "DEMO-W", 0.5, ValveOperator.MANUAL),
                    stackComponent("$id-t-choke", StackComponentType.PRODUCTION_CHOKE, "Production choke (demo)", 2.0, 5000.0, "DEMO-W", "DEMO-W", 0.45),
                ),
                provenance = synthetic,
            ),
            TreeWingOutlet(
                id = "$id-wing-kill", side = OutletSide.LEFT, role = TreeWingRole.KILL_INJECTION, branchComponentId = "$id-t-tee",
                components = listOf(
                    stackComponent("$id-t-kwv", StackComponentType.WING_VALVE, "Kill / injection wing valve (demo)", 2.0, 5000.0, "DEMO-W", "DEMO-W", 0.5, ValveOperator.MANUAL),
                ),
                provenance = synthetic,
            ),
        ),
    )

    // Synthetic cellar: 2.4 m deep, 2.4 m clear width.
    private val cellar = Cellar(Length.meters(2.4), Length.meters(2.4), synthetic)

    private fun annulusOutlets(prefix: String) = listOf(
        SideOutlet("$prefix-l", OutletSide.LEFT, listOf(OutletFitting.BULL_PLUG), label = "Annulus outlet, plugged (demo)", provenance = synthetic),
        SideOutlet(
            "$prefix-r", OutletSide.RIGHT, listOf(OutletFitting.GATE_VALVE, OutletFitting.NEEDLE_VALVE_GAUGE),
            label = "Annulus valve and gauge (demo)", provenance = synthetic,
        ),
    )

    /**
     * Synthetic three-section wellhead in the cellar. Base 2.1 m below GL (0.3 m above the
     * cellar floor); section heights 0.75 + 0.75 + 0.6 m put the top flange at GL.
     */
    private fun wellhead(id: String) = Wellhead(
        id = "$id-wellhead",
        name = "Wellhead (demo)",
        sections = listOf(
            WellheadSection(
                id = "$id-wh-ths", type = WellheadSectionType.TUBING_HEAD_SPOOL, name = "Tubing head spool (demo)",
                topConnection = EndConnection("DEMO-WELLHEAD"), bottomConnection = EndConnection("DEMO-WH-2"),
                ratedWorkingPressure = Pressure.psi(5000.0), height = Length.meters(0.6),
                sideOutlets = annulusOutlets("$id-wh-ths-o"), provenance = synthetic,
            ),
            WellheadSection(
                id = "$id-wh-cs", type = WellheadSectionType.CASING_SPOOL, name = "Casing spool (demo)",
                topConnection = EndConnection("DEMO-WH-2"), bottomConnection = EndConnection("DEMO-WH-1"),
                ratedWorkingPressure = Pressure.psi(5000.0), height = Length.meters(0.75), suspendedStringId = "$id-c4",
                sideOutlets = annulusOutlets("$id-wh-cs-o"), provenance = synthetic,
            ),
            WellheadSection(
                id = "$id-wh-chh", type = WellheadSectionType.CASING_HEAD_HOUSING, name = "Casing head housing (demo)",
                topConnection = EndConnection("DEMO-WH-1"), bottomConnection = EndConnection("DEMO-SURFACE-CASING"),
                ratedWorkingPressure = Pressure.psi(3000.0), height = Length.meters(0.75), suspendedStringId = "$id-c3",
                sideOutlets = annulusOutlets("$id-wh-chh-o"), provenance = synthetic,
            ),
        ),
        baseRelativeToGroundLevel = Length.meters(-2.1),
        provenance = synthetic,
    )

    private fun bopComponent(
        id: String, type: DrillingBopComponentType, name: String, ratingPsi: Double?, top: String, bottom: String,
        heightM: Double, rams: List<RamType> = emptyList(), outlets: List<SideOutlet> = emptyList(),
    ) = DrillingBopComponent(
        id = id, type = type, name = name, bore = inch(13.625), ratedWorkingPressure = ratingPsi?.let(Pressure::psi),
        topConnection = EndConnection(top), bottomConnection = EndConnection(bottom), height = Length.meters(heightM),
        rams = rams, sideOutlets = outlets, provenance = synthetic,
    )

    /**
     * Synthetic drilling rig-up: drill floor 0.5 m below RKB (8.5 m above GL), 7.6 m clear
     * under the rotary beams; BOP stack 7.0 m tall on the wellhead top at GL.
     */
    private fun drillingRigUp(id: String) = DrillingRigUp(
        rig = DrillingRig(
            id = "$id-rig", name = "Drilling rig (demo)",
            floorBelowRkb = Length.meters(0.5), clearHeightUnderRotaryBeams = Length.meters(7.6),
            substructureWidth = Length.meters(7.2), provenance = synthetic,
        ),
        bopStack = DrillingBopStack(
            id = "$id-bop", name = "Drilling BOP stack (demo)", provenance = synthetic,
            components = listOf(
                bopComponent("$id-bop-bn", DrillingBopComponentType.BELL_NIPPLE, "Bell nipple (demo)", null, "DEMO-FLOWLINE", "DEMO-BOP-A", 2.75),
                bopComponent("$id-bop-ann", DrillingBopComponentType.ANNULAR_PREVENTER, "Annular preventer (demo)", 5000.0, "DEMO-BOP-A", "DEMO-BOP-B", 1.35),
                bopComponent(
                    "$id-bop-dr", DrillingBopComponentType.DOUBLE_RAM_PREVENTER, "Double ram preventer (demo)", 5000.0, "DEMO-BOP-B", "DEMO-BOP-C", 1.5,
                    rams = listOf(RamType.PIPE, RamType.BLIND_SHEAR),
                ),
                bopComponent(
                    "$id-bop-sr", DrillingBopComponentType.SINGLE_RAM_PREVENTER, "Single ram preventer (demo)", 5000.0, "DEMO-BOP-C", "DEMO-BOP-D", 0.8,
                    rams = listOf(RamType.PIPE),
                ),
                bopComponent(
                    "$id-bop-ds", DrillingBopComponentType.DRILLING_SPOOL, "Drilling spool (demo)", 5000.0, "DEMO-BOP-D", "DEMO-WELLHEAD", 0.6,
                    outlets = listOf(
                        SideOutlet(
                            "$id-bop-kill", OutletSide.LEFT,
                            listOf(OutletFitting.GATE_VALVE, OutletFitting.GATE_VALVE, OutletFitting.LINE_CONNECTION),
                            label = "Kill line (demo)", provenance = synthetic,
                        ),
                        SideOutlet(
                            "$id-bop-choke", OutletSide.RIGHT,
                            listOf(OutletFitting.GATE_VALVE, OutletFitting.ACTUATED_GATE_VALVE, OutletFitting.LINE_CONNECTION),
                            label = "Choke line (demo)", provenance = synthetic,
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun ctOperation(id: String, currentDepth: Double): CtOperation {
        val stack = CtPressureControlStack(
            id = "$id-ct-stack",
            name = "CT pressure-control stack (demo)",
            components = listOf(
                stackComponent("$id-s-strip", StackComponentType.STRIPPER_PACKOFF, "Stripper / packoff (demo)", 3.0, 5000.0, "DEMO-INJ", "DEMO-S1", 1.0),
                stackComponent("$id-s-riser", StackComponentType.RISER_LUBRICATOR, "Riser / lubricator (demo)", 3.0, 5000.0, "DEMO-S1", "DEMO-S2", 3.0),
                stackComponent("$id-s-bop", StackComponentType.CT_BOP, "CT BOP (demo)", 3.0, 5000.0, "DEMO-S2", "DEMO-S3", 1.4),
                stackComponent("$id-s-cross", StackComponentType.FLOW_CROSS, "Flow cross (demo)", 3.0, 5000.0, "DEMO-S3", "DEMO-S4", 0.6),
                stackComponent("$id-s-xo", StackComponentType.CROSSOVER_ADAPTER, "Crossover to tree (demo)", 3.0, 5000.0, "DEMO-S4", "DEMO-TREE-TOP", 0.4),
            ),
            provenance = synthetic,
        )
        return CtOperation(
            id = "$id-ct-op",
            name = "CT cleanout (demo)",
            objective = "Synthetic demonstration of CT placement and restriction checks.",
            // Counter zero at the top of the tree: wellhead top flange at GL + tree without cap
            // 3.0 m = 3.0 m above GL (checked against the stack-up calculation in tests).
            depthReference = DepthReference.Operation("$id-ct-zero", "CT zero (top of tree)", Length.meters(3.0)),
            currentDepth = Length.meters(currentDepth),
            ctString = CtString(
                id = "$id-ct-string",
                name = "1.75 in CT string (demo)",
                sections = listOf(
                    CtStringSection(Length.meters(4500.0), inch(1.75), inch(0.134), "DEMO", synthetic),
                ),
                catalogItemId = SyntheticEquipmentCatalog.CT_STRING_175,
                provenance = synthetic,
            ),
            bha = CtBha(
                id = "$id-bha",
                name = "Cleanout BHA (demo)",
                components = listOf(
                    bha("$id-b1", CtBhaComponentType.CT_CONNECTOR, "CT connector (demo)", 0.30, 2.125),
                    bha("$id-b2", CtBhaComponentType.CHECK_VALVE, "Dual check valve (demo)", 0.50, 2.125),
                    bha("$id-b3", CtBhaComponentType.HYDRAULIC_DISCONNECT, "Hydraulic disconnect (demo)", 0.40, 2.125),
                    bha("$id-b4", CtBhaComponentType.CIRCULATING_SUB, "Circulating sub (demo)", 0.40, 2.125),
                    bha("$id-b5", CtBhaComponentType.JETTING_NOZZLE, "Jetting nozzle (demo)", 0.25, 2.0),
                ),
                provenance = synthetic,
            ),
            rigUp = CtRigUp(
                pressureControlStack = stack,
                surfaceEquipment = CtSurfaceEquipment(
                    reel = CtSurfaceUnit("CT reel (demo)", provenance = synthetic),
                    guideArch = CtSurfaceUnit("Guide arch / gooseneck (demo)", provenance = synthetic),
                    injectorHead = CtSurfaceUnit("Injector head (demo)", provenance = synthetic),
                    controlCabin = CtSurfaceUnit("Control cabin (demo)", provenance = synthetic),
                    powerPack = CtSurfaceUnit("Power pack (demo)", provenance = synthetic),
                ),
            ),
            maxExpectedWellheadPressure = Pressure.psi(2000.0),
            provenance = synthetic,
            pumpRate = FlowRate.barrelsPerMinute(1.5),
        )
    }

    private fun bha(id: String, type: CtBhaComponentType, name: String, lengthM: Double, odIn: Double) =
        CtBhaComponent(id, type, name, Length.meters(lengthM), inch(odIn), provenance = synthetic)
}
