package com.wellsketch.engineer.core.schematic

import com.wellsketch.engineer.core.engineering.trajectory.TrajectorySource
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.model.ct.CtBhaComponentType
import com.wellsketch.engineer.core.model.geology.Lithology
import com.wellsketch.engineer.core.model.surface.StackComponentType
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
}

/** Extra information a renderer needs for styling, keyed to the source model type. */
sealed interface ElementDetail {
    data object None : ElementDetail
    data class Casing(val role: CasingRole) : ElementDetail
    data class Cement(val status: CementStatus) : ElementDetail
    data class Component(val type: CompletionComponentType) : ElementDetail
    data class Perforation(val status: PerforationStatus) : ElementDetail
    data class Bha(val type: CtBhaComponentType) : ElementDetail
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

enum class MarkerKind { CASING_SHOE, TOP_OF_CEMENT, LINER_TOP }

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
}

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
) {
    val topAboveGl: Double get() = bottomAboveGl + height
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
) {
    init {
        require(path.size >= 2) { "A scene path needs at least two vertices." }
    }

    /** Largest radius that is real geometry (used for radial scaling). */
    val maxRadius: Double =
        elements.filterNot { it.isSymbolic }.maxOfOrNull { it.outerRadius }?.takeIf { it > 0 } ?: 0.25

    val maxTvdBelowGl: Double = maxOf(path.maxOf { it.y }, formations.maxOfOrNull { it.bottomTvdBelowGl } ?: 0.0)
    val minX: Double = path.minOf { it.x }
    val maxX: Double = path.maxOf { it.x }
    val surfaceHeight: Double = surfaceBlocks.maxOfOrNull { it.topAboveGl } ?: 0.0

    val elementsInDrawOrder: List<SchematicElement> get() = elements.sortedBy { it.kind.drawOrder }
}
