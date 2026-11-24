package com.wellsketch.engineer.core.schematic

import com.wellsketch.engineer.core.model.ct.CtRigUp
import com.wellsketch.engineer.core.model.ct.CtSurfaceUnit
import com.wellsketch.engineer.core.model.surface.ChristmasTree
import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.surface.StackComponent
import com.wellsketch.engineer.core.model.surface.StackComponentType

/** Full: complete CT spread on location. Compact: injector + pressure control + tree only. */
enum class CtSurfaceViewMode(val label: String) {
    FULL("Full surface view"),
    COMPACT("Compact view"),
}

/**
 * Lays out surface equipment as stacked blocks above ground level.
 *
 * Heights come from component data when available. Where data sheets give no size, a
 * placeholder drawing size is used and the block is flagged [SurfaceBlock.sizeIsSchematic];
 * these sizes are for drawing only and must never be used in calculations.
 */
object SurfaceLayouts {

    // Drawing placeholders (metres) – NOT equipment dimensions.
    private const val PLACEHOLDER_WELLHEAD_HEIGHT = 0.8
    private const val PLACEHOLDER_COMPONENT_HEIGHT = 0.5
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

    fun forOperatingState(
        state: OperatingState,
        ctViewMode: CtSurfaceViewMode = CtSurfaceViewMode.COMPACT,
    ): List<SurfaceBlock> = when (state) {
        OperatingState.SurfaceNotDefined -> emptyList()
        is OperatingState.Drilling -> emptyList() // rig/drilling BOP drawing not implemented yet
        is OperatingState.CompletedWithTree -> treeBlocks(state.tree, removeCap = false).blocks
        is OperatingState.CoiledTubingIntervention -> coiledTubing(state.tree, state.rigUp, ctViewMode)
    }

    data class StackResult(val blocks: List<SurfaceBlock>, val topAboveGl: Double)

    /**
     * Wellhead + Christmas Tree. For CT rig-up the tree cap is removed and the CT stack
     * crossover lands on the top tree connection (provisional assumption, pending review).
     */
    fun treeBlocks(tree: ChristmasTree, removeCap: Boolean): StackResult {
        val blocks = mutableListOf<SurfaceBlock>()
        val wellheadHeight = tree.wellheadTopAboveGroundLevel?.meters
        blocks += SurfaceBlock(
            id = "wellhead:${tree.id}", sourceId = tree.id, kind = SurfaceBlockKind.WELLHEAD, label = "Wellhead",
            centerOffset = 0.0, bottomAboveGl = 0.0,
            height = wellheadHeight ?: PLACEHOLDER_WELLHEAD_HEIGHT, width = 1.0,
            sizeIsSchematic = wellheadHeight == null,
        )
        var y = blocks.last().topAboveGl
        val components = tree.components.filterNot { removeCap && it.type == StackComponentType.TREE_CAP }
        for (c in components.asReversed()) { // components are stored top-down; stack bottom-up
            blocks += stackBlock(c, y)
            y = blocks.last().topAboveGl
        }
        return StackResult(blocks, y)
    }

    fun coiledTubing(tree: ChristmasTree, rigUp: CtRigUp, mode: CtSurfaceViewMode): List<SurfaceBlock> {
        val treeStack = treeBlocks(tree, removeCap = true)
        val blocks = treeStack.blocks.toMutableList()
        var y = treeStack.topAboveGl
        for (c in rigUp.pressureControlStack.components.asReversed()) {
            blocks += stackBlock(c, y)
            y = blocks.last().topAboveGl
        }
        val equipment = rigUp.surfaceEquipment
        val injector = equipment.injectorHead
        blocks += unitBlock(injector, "injector", SurfaceBlockKind.INJECTOR, "Injector head", 0.0, y,
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
        return blocks
    }

    private fun stackBlock(c: StackComponent, bottom: Double): SurfaceBlock {
        val height = c.height?.meters
        return SurfaceBlock(
            id = "stack:${c.id}", sourceId = c.id, kind = kindOf(c.type), label = c.name,
            centerOffset = 0.0, bottomAboveGl = bottom,
            height = height ?: PLACEHOLDER_COMPONENT_HEIGHT, width = widthOf(c.type),
            sizeIsSchematic = height == null,
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
        StackComponentType.STRIPPER_PACKOFF -> SurfaceBlockKind.STRIPPER
        StackComponentType.CT_BOP, StackComponentType.SHEAR_SEAL_BOP -> SurfaceBlockKind.BOP
        StackComponentType.RISER_LUBRICATOR -> SurfaceBlockKind.RISER
        StackComponentType.FLOW_CROSS -> SurfaceBlockKind.FLOW_CROSS
        StackComponentType.CROSSOVER_ADAPTER -> SurfaceBlockKind.CROSSOVER
        StackComponentType.OTHER -> SurfaceBlockKind.OTHER
    }

    /** Drawing widths (metres) by component type – visual placeholders only. */
    private fun widthOf(type: StackComponentType): Double = when (type) {
        StackComponentType.TREE_CAP -> 0.45
        StackComponentType.FLOW_TEE, StackComponentType.FLOW_CROSS -> 0.9
        StackComponentType.CT_BOP, StackComponentType.SHEAR_SEAL_BOP -> 1.0
        StackComponentType.RISER_LUBRICATOR -> 0.35
        StackComponentType.STRIPPER_PACKOFF -> 0.55
        StackComponentType.CROSSOVER_ADAPTER, StackComponentType.TUBING_HEAD_ADAPTER -> 0.6
        else -> 0.7
    }
}
