package com.wellsketch.engineer.core.schematic

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.surface.WellheadInstallation
import com.wellsketch.engineer.core.model.ct.CtRigUp
import com.wellsketch.engineer.core.model.ct.CtSurfaceUnit
import com.wellsketch.engineer.core.model.surface.Cellar
import com.wellsketch.engineer.core.model.surface.ChristmasTree
import com.wellsketch.engineer.core.model.surface.DrillingBopComponent
import com.wellsketch.engineer.core.model.surface.DrillingBopComponentType
import com.wellsketch.engineer.core.model.surface.DrillingRigUp
import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.surface.SideOutlet
import com.wellsketch.engineer.core.model.surface.StackComponent
import com.wellsketch.engineer.core.model.surface.StackComponentType
import com.wellsketch.engineer.core.model.surface.Wellhead
import com.wellsketch.engineer.core.model.surface.WellheadSectionType
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.Well
import kotlin.math.max
import kotlin.math.min

/** Full: complete CT spread on location. Compact: injector + pressure control + tree only. */
enum class CtSurfaceViewMode(val label: String) {
    FULL("Full surface view"),
    COMPACT("Compact view"),
}

/** Casing string that reaches the surface (drawn as a stub under the wellhead). */
data class SurfaceCasing(val id: String, val name: String, val outerDiameter: Double, val isConductor: Boolean)

/** Well-level inputs of the surface layout (the operating state supplies the rest). */
data class SurfaceInputs(
    val wellhead: Wellhead? = null,
    val cellar: Cellar? = null,
    /** RKB height above GL (metres), for the drill floor in the drilling state. */
    val rkbAboveGl: Double? = null,
    /** Casing strings whose top is at the surface, largest OD first. */
    val surfaceCasings: List<SurfaceCasing> = emptyList(),
) {
    companion object {
        val NONE = SurfaceInputs()

        /** Strings whose top lies within this distance below GL are treated as reaching the wellhead. */
        private const val SURFACE_TOP_TOLERANCE_M = 1.0

        fun of(well: Well, ctx: WellGeometryContext = WellGeometryContext(well)): SurfaceInputs {
            val glMd = ctx.groundLevelCanonicalMd.meters
            val casings = well.casingStrings
                .filter { !it.isLiner && ctx.canonical(it.top).meters - glMd <= SURFACE_TOP_TOLERANCE_M }
                .sortedByDescending { it.spec.outerDiameter.meters }
                .map { SurfaceCasing(it.id, it.name, it.spec.outerDiameter.meters, it.role == CasingRole.CONDUCTOR) }
            return SurfaceInputs(well.wellhead, well.cellar, well.datums.rkbAboveGroundLevel.meters, casings)
        }
    }
}

/** Result of a surface layout: the contiguous stack on the well axis plus the site context. */
data class SurfaceLayout(val blocks: List<SurfaceBlock>, val site: SurfaceSite)

/**
 * Lays out surface equipment on the well centerline, in metres relative to ground level.
 *
 * The wellhead sits in the cellar (below GL when the data says so), the Christmas Tree or
 * the drilling BOP stack lands on the wellhead top, and the CT pressure-control stack and
 * injector land on the tree. Every item shares the well axis (centre offset 0) except
 * off-axis equipment (tree wings) and the remote CT units of the full view.
 *
 * Heights come from component data when available. Where data sheets give no size, a
 * placeholder drawing size is used and the block is flagged [SurfaceBlock.sizeIsSchematic];
 * these sizes are for drawing only and must never be used in calculations. Widths are
 * always drawing sizes.
 */
object SurfaceLayouts {

    // Drawing placeholders (metres) – NOT equipment dimensions.
    private const val PLACEHOLDER_WELLHEAD_HEIGHT = 0.8
    private const val PLACEHOLDER_SECTION_HEIGHT = 0.7
    private const val PLACEHOLDER_COMPONENT_HEIGHT = 0.5
    private const val PLACEHOLDER_WING_LENGTH = 0.55
    private const val PLACEHOLDER_INJECTOR_HEIGHT = 2.2
    private const val PLACEHOLDER_INJECTOR_WIDTH = 1.6
    private const val PLACEHOLDER_GOOSENECK_HEIGHT = 1.6
    private const val PLACEHOLDER_REEL_DIAMETER = 3.6
    private const val PLACEHOLDER_REEL_OFFSET = -9.0
    private const val PLACEHOLDER_CABIN_SIZE = 2.6
    private const val PLACEHOLDER_CABIN_OFFSET = 5.5
    private const val PLACEHOLDER_POWER_PACK_WIDTH = 2.2
    private const val PLACEHOLDER_POWER_PACK_HEIGHT = 1.8
    private const val PLACEHOLDER_POWER_PACK_OFFSET = 9.0
    private const val PLACEHOLDER_CELLAR_HALF_WIDTH = 1.2
    private const val CELLAR_WALL = 0.25
    private const val CELLAR_SLAB = 0.25
    private const val PLACEHOLDER_SUBSTRUCTURE_HALF_WIDTH = 3.6
    private const val FLOOR_DECK = 0.3
    private const val ROTARY_BEAM_DEPTH = 0.45
    private const val STUB_BELOW = 0.35

    /**
     * Drawing widths (metres) used by the layout and the artwork. Visual only. The wellhead
     * top flange and the drilling BOP flanges share one width because they mate at the same
     * connection; the artwork also draws any two mating flanges at a common width.
     */
    object Widths {
        const val WELLHEAD_BODY = 0.62
        const val WELLHEAD_FLANGE = 0.90
        const val TREE_BODY = 0.40
        const val TREE_FLANGE = 0.46
        const val TREE_BRANCH_END = 0.36
        const val WING_HEIGHT = 0.42
        const val BOP_FLANGE = 0.90
        const val RAM_BODY = 1.02
        const val ANNULAR_BODY = 1.12
        const val SPOOL_BODY = 0.62
        const val BELL_NIPPLE = 0.50
        const val CT_FLANGE = 0.36
        const val CT_BODY = 0.34
        const val RISER = 0.20
    }

    fun forOperatingState(
        state: OperatingState,
        ctViewMode: CtSurfaceViewMode = CtSurfaceViewMode.COMPACT,
        inputs: SurfaceInputs = SurfaceInputs.NONE,
    ): List<SurfaceBlock> = layout(state, ctViewMode, inputs).blocks

    fun layout(
        state: OperatingState,
        ctViewMode: CtSurfaceViewMode = CtSurfaceViewMode.COMPACT,
        inputs: SurfaceInputs = SurfaceInputs.NONE,
    ): SurfaceLayout {
        if (state == OperatingState.SurfaceNotDefined) return SurfaceLayout(emptyList(), SurfaceSite.NONE)
        val tree = when (state) {
            is OperatingState.CompletedWithTree -> state.tree
            is OperatingState.CoiledTubingIntervention -> state.tree
            else -> null
        }
        val blocks = mutableListOf<SurfaceBlock>()
        val side = mutableListOf<SurfaceBlock>()
        val structures = mutableListOf<SurfaceBlock>()

        val wellhead = wellheadBlocks(inputs, tree, state)
        blocks += wellhead
        var y = wellhead.last().topAboveGl
        var rig: RigDrawing? = null

        when (state) {
            is OperatingState.CompletedWithTree -> y = stackTree(state.tree, removeCap = false, y, blocks, side)
            is OperatingState.CoiledTubingIntervention -> y = stackCt(state.tree, state.rigUp, ctViewMode, y, blocks, side)
            is OperatingState.Drilling -> {
                y = stackBop(state.rigUp, y, blocks)
                rig = rigDrawing(state.rigUp, inputs, y)
                structures += SurfaceBlock(
                    id = "rig-floor:${rig.sourceId}", sourceId = rig.sourceId, kind = SurfaceBlockKind.RIG_FLOOR,
                    label = "Rig floor", centerOffset = 0.0, bottomAboveGl = rig.beamsUndersideAboveGl,
                    height = rig.floorTopAboveGl - rig.beamsUndersideAboveGl + 1.1, width = rig.halfWidth * 2,
                    sizeIsSchematic = rig.sizeIsSchematic,
                )
                structures += SurfaceBlock(
                    id = "substructure:${rig.sourceId}", sourceId = rig.sourceId, kind = SurfaceBlockKind.SUBSTRUCTURE,
                    label = "Substructure", centerOffset = 0.0, bottomAboveGl = 0.0,
                    height = rig.beamsUndersideAboveGl, width = rig.halfWidth * 2, sizeIsSchematic = true,
                )
            }
            OperatingState.SurfaceNotDefined -> Unit
        }

        val cellar = inputs.cellar?.let { c ->
            val halfWidth = c.insideWidth?.meters?.div(2.0) ?: PLACEHOLDER_CELLAR_HALF_WIDTH
            CellarDrawing(
                sourceId = "cellar", label = "Cellar", floorAboveGl = -c.depthBelowGroundLevel.meters,
                insideHalfWidth = halfWidth, wallThickness = CELLAR_WALL, slabThickness = CELLAR_SLAB,
                sizeIsSchematic = c.insideWidth == null,
            )
        }
        if (cellar != null) {
            structures += SurfaceBlock(
                id = "cellar", sourceId = cellar.sourceId, kind = SurfaceBlockKind.CELLAR, label = cellar.label,
                centerOffset = 0.0, bottomAboveGl = cellar.floorAboveGl - cellar.slabThickness,
                height = -cellar.floorAboveGl + cellar.slabThickness,
                width = 2 * (cellar.insideHalfWidth + cellar.wallThickness), sizeIsSchematic = cellar.sizeIsSchematic,
            )
        }

        val site = SurfaceSite(
            cellar = cellar,
            rig = rig,
            casingStubs = casingStubs(inputs, wellhead.first().bottomAboveGl, cellar),
            sideBlocks = side,
            structureBlocks = structures,
            showsCoiledTubing = state is OperatingState.CoiledTubingIntervention,
        )
        return SurfaceLayout(blocks, site)
    }

    // ---------------------------------------------------------------------------------
    // Wellhead
    // ---------------------------------------------------------------------------------

    private fun wellheadBlocks(inputs: SurfaceInputs, tree: ChristmasTree?, state: OperatingState): List<SurfaceBlock> {
        // Only the sections on the well in this state (same rule as the stack-up calculator).
        val wellhead = inputs.wellhead?.takeIf { it.sections.isNotEmpty() }
            ?.let { it.copy(sections = WellheadInstallation.installed(it, state).sections) }
        if (wellhead == null) {
            // No wellhead definition: one placeholder block. A recorded top above GL keeps the
            // previous behaviour (block from GL to that top); otherwise a placeholder height is
            // hung below the recorded top (or below GL when nothing is recorded and a cellar exists).
            val recordedTop = tree?.wellheadTopAboveGroundLevel?.meters
            val defaultTop = if (inputs.cellar != null) 0.0 else PLACEHOLDER_WELLHEAD_HEIGHT
            val top = recordedTop ?: defaultTop
            val fromGl = recordedTop != null && recordedTop > 0.0
            val height = if (fromGl) recordedTop!! else PLACEHOLDER_WELLHEAD_HEIGHT
            val sourceId = tree?.id ?: (state as? OperatingState.Drilling)?.rigUp?.bopStack?.id ?: "wellhead"
            return listOf(
                SurfaceBlock(
                    id = "wellhead:$sourceId", sourceId = sourceId, kind = SurfaceBlockKind.WELLHEAD, label = "Wellhead",
                    centerOffset = 0.0, bottomAboveGl = top - height, height = height, width = Widths.WELLHEAD_FLANGE,
                    sizeIsSchematic = !fromGl,
                    drawing = BlockDrawing(wellheadSection = WellheadSectionType.OTHER),
                ),
            )
        }
        val heights = wellhead.sections.map { it.height?.meters ?: PLACEHOLDER_SECTION_HEIGHT }
        val base = wellhead.baseRelativeToGroundLevel?.meters
            ?: inputs.cellar?.let { -it.depthBelowGroundLevel.meters }
            ?: -heights.sum()
        var y = base
        val out = mutableListOf<SurfaceBlock>()
        for ((section, h) in wellhead.sections.zip(heights).asReversed()) { // stored top-down; stack bottom-up
            out += SurfaceBlock(
                id = "wellhead:${section.id}", sourceId = section.id, kind = SurfaceBlockKind.WELLHEAD, label = section.name,
                centerOffset = 0.0, bottomAboveGl = y, height = h, width = Widths.WELLHEAD_FLANGE,
                sizeIsSchematic = section.height == null,
                drawing = BlockDrawing(wellheadSection = section.type, outlets = section.sideOutlets.map(::outletDrawing)),
            )
            y += h
        }
        return out
    }

    private fun outletDrawing(o: SideOutlet) = OutletDrawing(
        id = o.id, side = o.side.sign, fittings = o.fittings, label = o.label ?: o.fittings.joinToString { it.label },
    )

    private fun casingStubs(inputs: SurfaceInputs, wellheadBase: Double, cellar: CellarDrawing?): List<CasingStubDrawing> {
        if (inputs.surfaceCasings.isEmpty()) return emptyList()
        val floor = cellar?.let { it.floorAboveGl - it.slabThickness }
        val bottom = min(floor ?: wellheadBase, wellheadBase) - STUB_BELOW
        val out = mutableListOf<CasingStubDrawing>()
        inputs.surfaceCasings.filter { it.isConductor }.forEach { c ->
            val top = cellar?.let { it.floorAboveGl + 0.12 } ?: (wellheadBase - 0.2)
            out += CasingStubDrawing(c.id, c.name, c.outerDiameter / 2.0, min(top, wellheadBase), bottom)
        }
        inputs.surfaceCasings.firstOrNull { !it.isConductor }?.let { c ->
            out += CasingStubDrawing(c.id, c.name, c.outerDiameter / 2.0, wellheadBase, bottom)
        }
        return out
    }

    // ---------------------------------------------------------------------------------
    // Christmas Tree and CT
    // ---------------------------------------------------------------------------------

    private fun stackTree(
        tree: ChristmasTree,
        removeCap: Boolean,
        start: Double,
        blocks: MutableList<SurfaceBlock>,
        side: MutableList<SurfaceBlock>,
    ): Double {
        var y = start
        val components = tree.components.filterNot { removeCap && it.type == StackComponentType.TREE_CAP }
        for (c in components.asReversed()) { // components are stored top-down; stack bottom-up
            val branchSides = tree.wingOutlets.filter { it.branchComponentId == c.id }.map { it.side.sign }.distinct().sorted()
            blocks += stackBlock(c, y, BlockDrawing(stackType = c.type, operator = c.operator, branchSides = branchSides))
            y = blocks.last().topAboveGl
        }
        for (wing in tree.wingOutlets) {
            val branch = blocks.firstOrNull { it.sourceId == wing.branchComponentId } ?: continue
            val centerY = branch.bottomAboveGl + branch.height / 2.0
            var x = Widths.TREE_BRANCH_END
            wing.components.forEachIndexed { i, c ->
                val length = c.height?.meters ?: PLACEHOLDER_WING_LENGTH
                side += SurfaceBlock(
                    id = "wing:${wing.id}:${c.id}", sourceId = c.id,
                    kind = if (c.type == StackComponentType.PRODUCTION_CHOKE) SurfaceBlockKind.TREE_CHOKE else SurfaceBlockKind.TREE_WING_VALVE,
                    label = c.name, centerOffset = wing.side.sign * (x + length / 2.0),
                    bottomAboveGl = centerY - Widths.WING_HEIGHT / 2.0, height = Widths.WING_HEIGHT, width = length,
                    sizeIsSchematic = c.height == null,
                    drawing = BlockDrawing(
                        orientation = BlockOrientation.HORIZONTAL, side = wing.side.sign, stackType = c.type,
                        operator = c.operator, isBranchEnd = i == wing.components.lastIndex,
                    ),
                )
                x += length
            }
        }
        return y
    }

    private fun stackCt(
        tree: ChristmasTree,
        rigUp: CtRigUp,
        mode: CtSurfaceViewMode,
        start: Double,
        blocks: MutableList<SurfaceBlock>,
        side: MutableList<SurfaceBlock>,
    ): Double {
        // Tree cap removed; the CT stack crossover lands on the top tree connection
        // (provisional assumption, pending review).
        var y = stackTree(tree, removeCap = true, start, blocks, side)
        for (c in rigUp.pressureControlStack.components.asReversed()) {
            val branchSides = if (c.type == StackComponentType.FLOW_CROSS) listOf(-1, 1) else emptyList()
            blocks += stackBlock(c, y, BlockDrawing(stackType = c.type, operator = c.operator, branchSides = branchSides))
            y = blocks.last().topAboveGl
        }
        val equipment = rigUp.surfaceEquipment
        blocks += unitBlock(equipment.injectorHead, "injector", SurfaceBlockKind.INJECTOR, "Injector head", 0.0, y,
            PLACEHOLDER_INJECTOR_HEIGHT, PLACEHOLDER_INJECTOR_WIDTH)
        y = blocks.last().topAboveGl
        if (mode == CtSurfaceViewMode.FULL) {
            blocks += unitBlock(equipment.guideArch, "gooseneck", SurfaceBlockKind.GOOSENECK, "Guide arch / gooseneck",
                0.0, y, PLACEHOLDER_GOOSENECK_HEIGHT, PLACEHOLDER_INJECTOR_WIDTH * 1.6)
            blocks += unitBlock(equipment.reel, "reel", SurfaceBlockKind.REEL, "CT reel",
                PLACEHOLDER_REEL_OFFSET, 0.0, PLACEHOLDER_REEL_DIAMETER, PLACEHOLDER_REEL_DIAMETER)
            blocks += unitBlock(equipment.controlCabin, "cabin", SurfaceBlockKind.CONTROL_CABIN, "Control cabin",
                PLACEHOLDER_CABIN_OFFSET, 0.0, PLACEHOLDER_CABIN_SIZE, PLACEHOLDER_CABIN_SIZE)
            blocks += unitBlock(equipment.powerPack, "powerpack", SurfaceBlockKind.POWER_PACK, "Power pack",
                PLACEHOLDER_POWER_PACK_OFFSET, 0.0, PLACEHOLDER_POWER_PACK_HEIGHT, PLACEHOLDER_POWER_PACK_WIDTH)
        }
        return y
    }

    // ---------------------------------------------------------------------------------
    // Drilling
    // ---------------------------------------------------------------------------------

    private fun stackBop(rigUp: DrillingRigUp, start: Double, blocks: MutableList<SurfaceBlock>): Double {
        var y = start
        for (c in rigUp.bopStack.components.asReversed()) {
            val h = c.height?.meters ?: bopPlaceholderHeight(c)
            blocks += SurfaceBlock(
                id = "bop:${c.id}", sourceId = c.id, kind = bopKind(c.type), label = c.name,
                centerOffset = 0.0, bottomAboveGl = y, height = h, width = bopWidth(c.type),
                sizeIsSchematic = c.height == null,
                drawing = BlockDrawing(bopType = c.type, rams = c.rams, outlets = c.sideOutlets.map(::outletDrawing)),
            )
            y += h
        }
        return y
    }

    private fun rigDrawing(rigUp: DrillingRigUp, inputs: SurfaceInputs, stackTop: Double): RigDrawing {
        val rig = rigUp.rig
        val rkb = inputs.rkbAboveGl ?: 0.0
        val floorTop = rkb - (rig.floorBelowRkb?.meters ?: 0.0)
        val beams = rig.clearHeightUnderRotaryBeams?.meters ?: (floorTop - FLOOR_DECK - ROTARY_BEAM_DEPTH)
        val halfWidth = rig.substructureWidth?.meters?.div(2.0) ?: PLACEHOLDER_SUBSTRUCTURE_HALF_WIDTH
        return RigDrawing(
            sourceId = rig.id, label = rig.name,
            floorTopAboveGl = floorTop, rkbAboveGl = rkb,
            beamsUndersideAboveGl = max(0.0, min(beams, floorTop - FLOOR_DECK)),
            halfWidth = halfWidth,
            floorPositionAssumed = rig.floorBelowRkb == null,
            sizeIsSchematic = rig.substructureWidth == null || rig.clearHeightUnderRotaryBeams == null,
        )
    }

    private fun bopKind(type: DrillingBopComponentType) = when (type) {
        DrillingBopComponentType.BELL_NIPPLE -> SurfaceBlockKind.DRILLING_BELL_NIPPLE
        DrillingBopComponentType.ANNULAR_PREVENTER -> SurfaceBlockKind.DRILLING_ANNULAR
        DrillingBopComponentType.SINGLE_RAM_PREVENTER, DrillingBopComponentType.DOUBLE_RAM_PREVENTER -> SurfaceBlockKind.DRILLING_RAM
        DrillingBopComponentType.DRILLING_SPOOL, DrillingBopComponentType.SPACER_SPOOL -> SurfaceBlockKind.DRILLING_SPOOL
    }

    private fun bopWidth(type: DrillingBopComponentType) = when (type) {
        DrillingBopComponentType.BELL_NIPPLE -> Widths.BOP_FLANGE
        DrillingBopComponentType.ANNULAR_PREVENTER -> Widths.ANNULAR_BODY
        DrillingBopComponentType.SINGLE_RAM_PREVENTER, DrillingBopComponentType.DOUBLE_RAM_PREVENTER -> Widths.RAM_BODY
        DrillingBopComponentType.DRILLING_SPOOL, DrillingBopComponentType.SPACER_SPOOL -> Widths.BOP_FLANGE
    }

    private fun bopPlaceholderHeight(c: DrillingBopComponent) = when (c.type) {
        DrillingBopComponentType.BELL_NIPPLE -> 1.2
        DrillingBopComponentType.ANNULAR_PREVENTER -> 1.3
        DrillingBopComponentType.SINGLE_RAM_PREVENTER -> 0.8
        DrillingBopComponentType.DOUBLE_RAM_PREVENTER -> 1.5
        DrillingBopComponentType.DRILLING_SPOOL, DrillingBopComponentType.SPACER_SPOOL -> 0.6
    }

    // ---------------------------------------------------------------------------------

    private fun stackBlock(c: StackComponent, bottom: Double, drawing: BlockDrawing): SurfaceBlock {
        val height = c.height?.meters
        return SurfaceBlock(
            id = "stack:${c.id}", sourceId = c.id, kind = kindOf(c.type), label = c.name,
            centerOffset = 0.0, bottomAboveGl = bottom,
            height = height ?: PLACEHOLDER_COMPONENT_HEIGHT, width = widthOf(c.type),
            sizeIsSchematic = height == null,
            drawing = drawing,
        )
    }

    private fun unitBlock(
        unit: CtSurfaceUnit?, key: String, kind: SurfaceBlockKind, fallbackLabel: String,
        offset: Double, bottom: Double, height: Double, width: Double,
    ) = SurfaceBlock(
        id = "ct-surface:$key", sourceId = unit?.catalogItemId ?: "ct-surface:$key", kind = kind,
        label = unit?.name ?: "$fallbackLabel (not specified)",
        centerOffset = offset, bottomAboveGl = bottom, height = height, width = width,
        sizeIsSchematic = true,
    )

    private fun kindOf(type: StackComponentType): SurfaceBlockKind = when (type) {
        StackComponentType.TREE_CAP -> SurfaceBlockKind.TREE_CAP
        StackComponentType.SWAB_VALVE, StackComponentType.UPPER_MASTER_VALVE,
        StackComponentType.LOWER_MASTER_VALVE, StackComponentType.WING_VALVE -> SurfaceBlockKind.TREE_VALVE
        StackComponentType.FLOW_TEE -> SurfaceBlockKind.TREE_TEE
        StackComponentType.TUBING_HEAD_ADAPTER -> SurfaceBlockKind.TREE_ADAPTER
        StackComponentType.PRODUCTION_CHOKE -> SurfaceBlockKind.TREE_CHOKE
        StackComponentType.STRIPPER_PACKOFF -> SurfaceBlockKind.STRIPPER
        StackComponentType.CT_BOP, StackComponentType.SHEAR_SEAL_BOP -> SurfaceBlockKind.BOP
        StackComponentType.RISER_LUBRICATOR -> SurfaceBlockKind.RISER
        StackComponentType.FLOW_CROSS -> SurfaceBlockKind.FLOW_CROSS
        StackComponentType.CROSSOVER_ADAPTER -> SurfaceBlockKind.CROSSOVER
        StackComponentType.OTHER -> SurfaceBlockKind.OTHER
    }

    /** Drawing widths (metres) by component type – visual placeholders only. */
    private fun widthOf(type: StackComponentType): Double = when (type) {
        StackComponentType.TREE_CAP -> 0.34
        StackComponentType.FLOW_TEE -> Widths.TREE_FLANGE
        StackComponentType.TUBING_HEAD_ADAPTER -> Widths.WELLHEAD_FLANGE
        StackComponentType.FLOW_CROSS -> Widths.CT_FLANGE
        StackComponentType.CT_BOP, StackComponentType.SHEAR_SEAL_BOP -> 0.5
        StackComponentType.RISER_LUBRICATOR -> Widths.RISER + 0.12
        StackComponentType.STRIPPER_PACKOFF -> Widths.CT_FLANGE
        StackComponentType.CROSSOVER_ADAPTER -> Widths.TREE_FLANGE
        else -> Widths.TREE_FLANGE
    }
}
