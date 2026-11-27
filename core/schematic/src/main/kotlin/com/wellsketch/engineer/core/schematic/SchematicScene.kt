package com.wellsketch.engineer.core.schematic

import com.wellsketch.engineer.core.engineering.trajectory.TrajectorySource
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.model.ct.CtBhaComponentType
import com.wellsketch.engineer.core.model.workover.WorkoverToolType
import com.wellsketch.engineer.core.model.geology.Lithology
import com.wellsketch.engineer.core.model.surface.DrillingBopComponentType
import com.wellsketch.engineer.core.model.surface.OutletFitting
import com.wellsketch.engineer.core.model.surface.RamType
import com.wellsketch.engineer.core.model.surface.StackComponentType
import com.wellsketch.engineer.core.model.surface.ValveOperator
import com.wellsketch.engineer.core.model.surface.WellheadSectionType
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CementStatus
import com.wellsketch.engineer.core.model.well.CompletionComponentType
import com.wellsketch.engineer.core.model.well.PerforationStatus

/*
 * Renderer-agnostic description of a well schematic.
 *
 * Coordinates are physical and in SI metres:
 *  - "md" values are measured depths on the scene's canonical MD reference;
 *  - radii are distances from the well axis (the half-cut is drawn mirrored on both sides);
 *  - path vertices use x = vertical-section displacement and y = TVD below ground level,
 *    so the stratigraphic column (also in TVD below GL) aligns exactly with the well.
 *
 * Radial and surface dimensions are exaggerated by the renderer through separate scales;
 * the scene itself never distorts geometry.
 */

/** Element kinds with the order in which they are drawn and their hit-test priority. */
enum class ElementKind(val drawOrder: Int, val hitPriority: Int) {
    OPEN_HOLE(0, 0),
    CEMENT(1, 2),
    PERFORATION(2, 1),
    CASING_WALL(3, 3),
    TUBING_WALL(4, 4),
    COMPLETION_COMPONENT(5, 6),
    CT_WALL(6, 7),
    BHA_COMPONENT(7, 8),
    WORK_STRING_WALL(6, 7),
    WORKOVER_TOOL(7, 8),
}

/** Extra information a renderer needs for styling, keyed to the source model type. */
sealed interface ElementDetail {
    data object None : ElementDetail
    data class Casing(val role: CasingRole) : ElementDetail
    data class Cement(val status: CementStatus) : ElementDetail
    data class Component(val type: CompletionComponentType) : ElementDetail
    data class Perforation(val status: PerforationStatus) : ElementDetail
    data class Bha(val type: CtBhaComponentType) : ElementDetail
    data class WorkoverTool(val type: WorkoverToolType) : ElementDetail
}

/**
 * An annular band along the well path between [mdTop] and [mdBottom], occupying radii
 * [innerRadius]..[outerRadius] (metres) on both sides of the axis.
 */
data class SchematicElement(
    val id: String,
    /** Id of the model object (casing string, component...) for selection/detail views. */
    val sourceId: String,
    val kind: ElementKind,
    val label: String,
    val mdTop: Double,
    val mdBottom: Double,
    val innerRadius: Double,
    val outerRadius: Double,
    val detail: ElementDetail = ElementDetail.None,
    /** True when the radial extent is a drawing symbol (e.g. perforation tunnels), not geometry. */
    val isSymbolic: Boolean = false,
) {
    init {
        require(mdBottom >= mdTop) { "Element $id: mdBottom < mdTop" }
        require(outerRadius >= innerRadius) { "Element $id: outerRadius < innerRadius" }
    }
}

enum class MarkerKind { CASING_SHOE, TOP_OF_CEMENT, LINER_TOP, END_OF_TUBING }

/** A point marker at a depth (e.g. shoe triangle), drawn at [radius] on both sides. */
data class SchematicMarker(
    val id: String,
    val sourceId: String,
    val kind: MarkerKind,
    val md: Double,
    val radius: Double,
    val label: String,
)

/** A formation interval in TVD below ground level (metres). */
data class FormationBand(
    val id: String,
    val name: String,
    val lithology: Lithology,
    val topTvdBelowGl: Double,
    val bottomTvdBelowGl: Double,
    /** True for the gap between GL and the first entered top (no data). */
    val isUndefined: Boolean = false,
)

data class PathVertex(val md: Double, val x: Double, val y: Double)

/** Datum elevations relative to ground level, for datum annotations (metres, + = above GL). */
data class SceneDatums(
    val rkbAboveGl: Double,
    /** MSL elevation relative to GL (negative when GL is above sea level). */
    val mslRelativeToGl: Double,
    val canonicalReferenceLabel: String,
)

enum class SurfaceBlockKind {
    WELLHEAD, TREE_CAP, TREE_VALVE, TREE_TEE, TREE_ADAPTER,
    STRIPPER, BOP, RISER, FLOW_CROSS, CROSSOVER,
    INJECTOR, GOOSENECK, REEL, CONTROL_CABIN, POWER_PACK, OTHER,

    // Christmas Tree wing branch (off the well axis)
    TREE_WING_VALVE, TREE_CHOKE,

    // Drilling BOP stack (separate from the CT stack kinds above)
    DRILLING_BELL_NIPPLE, DRILLING_ANNULAR, DRILLING_RAM, DRILLING_SPOOL,

    // Site structures
    CELLAR, SUBSTRUCTURE, RIG_FLOOR,
}

/** Orientation of a surface block's main axis. */
enum class BlockOrientation { VERTICAL, HORIZONTAL }

/** A side outlet drawn on a wellhead section or drilling BOP body. */
data class OutletDrawing(
    val id: String,
    /** -1 = left of the axis, +1 = right. */
    val side: Int,
    val fittings: List<OutletFitting>,
    val label: String,
)

/**
 * Visual hints for the surface artwork. Purely presentational: nothing here is an
 * equipment dimension and nothing here may be used in calculations.
 */
data class BlockDrawing(
    val orientation: BlockOrientation = BlockOrientation.VERTICAL,
    /** For horizontal (off-axis) blocks: -1 = left of the axis, +1 = right. */
    val side: Int = 0,
    val wellheadSection: WellheadSectionType? = null,
    val stackType: StackComponentType? = null,
    val bopType: DrillingBopComponentType? = null,
    val operator: ValveOperator? = null,
    val rams: List<RamType> = emptyList(),
    val outlets: List<OutletDrawing> = emptyList(),
    /** Sides (-1/+1) where a flow tee/cross has flanged branch outlets. */
    val branchSides: List<Int> = emptyList(),
    /** True for the last component of a wing branch (gets an open outlet flange). */
    val isBranchEnd: Boolean = false,
)

/**
 * Surface equipment block in metres: [centerOffset] is the horizontal offset of the block
 * centre from the well axis, [bottomAboveGl] its base height above ground level.
 */
data class SurfaceBlock(
    val id: String,
    val sourceId: String,
    val kind: SurfaceBlockKind,
    val label: String,
    val centerOffset: Double,
    val bottomAboveGl: Double,
    val height: Double,
    val width: Double,
    /** True when height/width are drawing placeholders because no data-sheet size exists. */
    val sizeIsSchematic: Boolean,
    val drawing: BlockDrawing = BlockDrawing(),
) {
    val topAboveGl: Double get() = bottomAboveGl + height
}

/** Concrete cellar pit in metres relative to GL (drawing geometry). */
data class CellarDrawing(
    val sourceId: String,
    val label: String,
    /** Cellar floor elevation (negative = below GL). */
    val floorAboveGl: Double,
    val insideHalfWidth: Double,
    val wallThickness: Double,
    val slabThickness: Double,
    val sizeIsSchematic: Boolean,
)

/** Drill floor and substructure for the drilling state, metres relative to GL. */
data class RigDrawing(
    val sourceId: String,
    val label: String,
    /** Drill-floor walking surface elevation. */
    val floorTopAboveGl: Double,
    /** RKB datum elevation (top of the rotary/kelly bushing). */
    val rkbAboveGl: Double,
    /** Underside of the rotary support beams. */
    val beamsUndersideAboveGl: Double,
    val halfWidth: Double,
    /** True when the floor is drawn at RKB because the RKB-to-floor distance is missing. */
    val floorPositionAssumed: Boolean,
    val sizeIsSchematic: Boolean,
)

/** Casing string reaching the surface, drawn as a stub below the wellhead / through the cellar. */
data class CasingStubDrawing(
    val sourceId: String,
    val label: String,
    val outerRadius: Double,
    val topAboveGl: Double,
    val bottomAboveGl: Double,
)

/**
 * Surface context of the scene: the cellar, rig structure, casing stubs and off-axis
 * equipment (wing valves, chokes). Kept separate from [SchematicScene.surfaceBlocks],
 * which remain the contiguous pressure stack on the well axis.
 */
/** Landing base plate under the lowest wellhead section, carried by the conductor. Drawing sizes only. */
data class LandingBaseDrawing(
    val sourceId: String,
    val topAboveGl: Double,
    val thickness: Double,
    val halfWidth: Double,
)

data class SurfaceSite(
    val cellar: CellarDrawing? = null,
    val landingBase: LandingBaseDrawing? = null,
    val rig: RigDrawing? = null,
    val casingStubs: List<CasingStubDrawing> = emptyList(),
    /** Off-axis pressure equipment, e.g. tree wing valves and chokes. */
    val sideBlocks: List<SurfaceBlock> = emptyList(),
    /** Blocks used for tapping site structures (cellar, substructure, rig floor). */
    val structureBlocks: List<SurfaceBlock> = emptyList(),
    /** True when coiled tubing is rigged up (CT is drawn through the injector). */
    val showsCoiledTubing: Boolean = false,
) {
    val isEmpty: Boolean
        get() = cellar == null && rig == null && casingStubs.isEmpty() && sideBlocks.isEmpty() && structureBlocks.isEmpty()

    companion object {
        val NONE = SurfaceSite()
    }
}

data class SchematicScene(
    val wellName: String,
    val path: List<PathVertex>,
    val elements: List<SchematicElement>,
    val markers: List<SchematicMarker>,
    val formations: List<FormationBand>,
    val datums: SceneDatums,
    val surfaceBlocks: List<SurfaceBlock>,
    val trajectorySource: TrajectorySource,
    val groundLevelMd: Double,
    val totalDepthMd: Double,
    val issues: List<ValidationIssue>,
    val surfaceSite: SurfaceSite = SurfaceSite.NONE,
) {
    init {
        require(path.size >= 2) { "A scene path needs at least two vertices." }
    }

    /** True when there is any surface equipment or site structure to draw. */
    val hasSurface: Boolean get() = surfaceBlocks.isNotEmpty() || !surfaceSite.isEmpty

    /** Every tappable surface block: axis stack, off-axis equipment, then site structures. */
    val allSurfaceBlocks: List<SurfaceBlock>
        get() = surfaceBlocks + surfaceSite.sideBlocks + surfaceSite.structureBlocks

    /** Largest radius that is real geometry (used for radial scaling). */
    val maxRadius: Double =
        elements.filterNot { it.isSymbolic }.maxOfOrNull { it.outerRadius }?.takeIf { it > 0 } ?: 0.25

    val maxTvdBelowGl: Double = maxOf(path.maxOf { it.y }, formations.maxOfOrNull { it.bottomTvdBelowGl } ?: 0.0)
    val minX: Double = path.minOf { it.x }
    val maxX: Double = path.maxOf { it.x }
    val surfaceHeight: Double = surfaceBlocks.maxOfOrNull { it.topAboveGl } ?: 0.0

    val elementsInDrawOrder: List<SchematicElement> get() = elements.sortedBy { it.kind.drawOrder }
}
