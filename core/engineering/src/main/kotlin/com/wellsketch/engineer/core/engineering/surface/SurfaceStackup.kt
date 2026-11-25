package com.wellsketch.engineer.core.engineering.surface

import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.surface.ChristmasTree
import com.wellsketch.engineer.core.model.surface.EndConnection
import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.surface.StackComponentType
import com.wellsketch.engineer.core.model.surface.StackGroup
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.Well

/** How the elevation of the wellhead was obtained. */
enum class WellheadPositionBasis(val label: String) {
    /** Base elevation entered for the wellhead. */
    ENTERED_BASE("Wellhead base elevation entered"),

    /** No base entered: the lowest section is assumed to sit on the cellar floor. */
    CELLAR_FLOOR_ASSUMED("Assumed on the cellar floor"),

    /** No base and no cellar: the top flange is assumed to be at ground level. */
    TOP_AT_GROUND_LEVEL_ASSUMED("Top flange assumed at ground level"),

    /** No wellhead definition: the tree's recorded wellhead-top elevation is used. */
    TREE_RECORD("From the Christmas Tree record"),

    /** Not enough data to place the wellhead. */
    UNKNOWN("Unknown"),
}

/**
 * Elevations of the surface stack-up for one operating state, in metres relative to ground
 * level (positive up). Every value is calculated only from entered component heights and
 * datums; when a needed height is missing the value is null and the reason is reported.
 * Drawing placeholders are never used here.
 */
data class SurfaceStackup(
    val cellarFloor: Length?,
    val wellheadBase: Length?,
    val wellheadTop: Length?,
    val wellheadBasis: WellheadPositionBasis,
    /** Top of the Christmas Tree including the tree cap (completed state). */
    val treeTopWithCap: Length?,
    /** Top connection of the tree without its cap, where CT equipment lands. */
    val treeTopWithoutCap: Length?,
    /** Top of the CT pressure-control stack (base of the injector). */
    val ctStackTop: Length?,
    /** Top of the drilling BOP stack (including any bell nipple). */
    val drillingStackTop: Length?,
    /** Drill-floor elevation = RKB above GL − floor-below-RKB distance. */
    val drillFloor: Length?,
    /** Rotary-beam clear height above GL minus drilling stack top (negative = does not fit). */
    val clearanceUnderRotaryBeams: Length?,
    /** Distance from the RKB datum down to the wellhead top (landing reference). */
    val rkbToWellheadTop: Length?,
    val issues: List<ValidationIssue>,
    /** Wellhead sections on the well in this state (null when no wellhead is defined). */
    val installedWellhead: InstalledWellhead? = null,
)

/**
 * Calculates surface elevations and checks connection continuity of the surface stack
 * (wellhead → tree / drilling BOP). CT stack connection and rating checks remain in the CT
 * module's stack validator; this calculator adds the wellhead side.
 */
class SurfaceStackupCalculator {

    fun calculate(well: Well, state: OperatingState): SurfaceStackup {
        val issues = mutableListOf<ValidationIssue>()
        val rkbAboveGl = well.datums.rkbAboveGroundLevel
        val tree = when (state) {
            is OperatingState.CompletedWithTree -> state.tree
            is OperatingState.CoiledTubingIntervention -> state.tree
            else -> null
        }

        // --- Cellar ---------------------------------------------------------------------
        val cellar = well.cellar
        val cellarFloor = cellar?.let { -it.depthBelowGroundLevel }
        if (cellar != null && cellar.depthBelowGroundLevel < Length.ZERO) {
            issues += ValidationIssue(Severity.ERROR, "CELLAR_DEPTH_NEGATIVE", "Cellar depth must be entered as a positive depth below GL.")
        }

        // --- Wellhead -------------------------------------------------------------------
        val fullWellhead = well.wellhead?.takeIf { it.sections.isNotEmpty() }
        val installed = fullWellhead?.let { WellheadInstallation.installed(it, state) }
        // Only the sections on the well in this state count (e.g. no tubing head spool while drilling).
        val wellhead = fullWellhead?.copy(sections = installed!!.sections)
        if (installed != null) installationIssues(installed, fullWellhead.id, issues)
        var base: Length? = null
        var top: Length? = null
        var basis = WellheadPositionBasis.UNKNOWN
        if (wellhead != null) {
            val missing = wellhead.sections.filter { it.height == null }
            val stackHeight = if (missing.isEmpty()) Length(wellhead.sections.sumOf { it.height!!.meters }) else null
            if (missing.isNotEmpty()) {
                issues += ValidationIssue(
                    Severity.WARNING, "WELLHEAD_HEIGHT_MISSING",
                    "Height not entered for ${missing.joinToString { "'${it.name}'" }}; elevations above the wellhead are not calculated.",
                    missing.map { it.id },
                )
            }
            when {
                wellhead.baseRelativeToGroundLevel != null -> {
                    base = wellhead.baseRelativeToGroundLevel
                    basis = WellheadPositionBasis.ENTERED_BASE
                }
                cellarFloor != null -> {
                    base = cellarFloor
                    basis = WellheadPositionBasis.CELLAR_FLOOR_ASSUMED
                    issues += ValidationIssue(
                        Severity.INFO, "WELLHEAD_BASE_ASSUMED",
                        "Wellhead base elevation not entered; assumed to sit on the cellar floor.", listOf(wellhead.id),
                    )
                }
                stackHeight != null -> {
                    base = -stackHeight
                    basis = WellheadPositionBasis.TOP_AT_GROUND_LEVEL_ASSUMED
                    issues += ValidationIssue(
                        Severity.INFO, "WELLHEAD_TOP_ASSUMED_AT_GL",
                        "No wellhead base elevation or cellar entered; the wellhead top is assumed at ground level.", listOf(wellhead.id),
                    )
                }
            }
            top = if (base != null && stackHeight != null) base + stackHeight else null
            if (base != null && cellarFloor != null && base < cellarFloor - Length.TOLERANCE) {
                issues += ValidationIssue(
                    Severity.ERROR, "WELLHEAD_BELOW_CELLAR_FLOOR",
                    "Wellhead base is below the cellar floor.", listOf(wellhead.id),
                )
            }
            for ((upper, lower) in wellhead.sections.zipWithNext()) {
                if (!connects(upper.bottomConnection, lower.topConnection)) {
                    issues += ValidationIssue(
                        Severity.ERROR, "WELLHEAD_CONNECTION_MISMATCH",
                        "Connection mismatch between '${upper.name}' (${upper.bottomConnection.designation}) " +
                            "and '${lower.name}' (${lower.topConnection.designation}).",
                        listOf(upper.id, lower.id),
                    )
                }
            }
            val recorded = well.christmasTree?.wellheadTopAboveGroundLevel
            if (tree != null && recorded != null && top != null && !recorded.isApproximately(top, Length.meters(1e-3))) {
                issues += ValidationIssue(
                    Severity.WARNING, "WELLHEAD_TOP_MISMATCH",
                    "The tree record gives the wellhead top at ${fmt(recorded)} m from GL, but the wellhead sections give ${fmt(top)} m.",
                    listOf(wellhead.id, tree.id),
                )
            }
        } else if (tree?.wellheadTopAboveGroundLevel != null) {
            top = tree.wellheadTopAboveGroundLevel
            basis = WellheadPositionBasis.TREE_RECORD
        }
        if (wellhead == null && state !is OperatingState.SurfaceNotDefined) {
            issues += ValidationIssue(
                Severity.INFO, "WELLHEAD_NOT_DEFINED",
                "Wellhead sections not defined; the wellhead is drawn as a placeholder.",
            )
        }

        // --- Christmas Tree -------------------------------------------------------------
        var treeTopWithCap: Length? = null
        var treeTopWithoutCap: Length? = null
        if (tree != null) {
            if (top != null) {
                treeTopWithoutCap = stackTop(top, tree.components.filterNot { it.type == StackComponentType.TREE_CAP }.map { it.height })
                treeTopWithCap = stackTop(top, tree.components.map { it.height })
            }
            if (tree.components.any { it.height == null }) {
                issues += ValidationIssue(
                    Severity.WARNING, "TREE_HEIGHT_MISSING",
                    "Height not entered for some tree components; the tree top elevation is not calculated.",
                    tree.components.filter { it.height == null }.map { it.id },
                )
            }
            val lowest = tree.components.lastOrNull()
            val topSection = wellhead?.sections?.firstOrNull()
            if (lowest != null && topSection != null && !connects(lowest.bottomConnection, topSection.topConnection)) {
                issues += ValidationIssue(
                    Severity.ERROR, "TREE_WELLHEAD_CONNECTION_MISMATCH",
                    "Tree bottom '${lowest.name}' (${lowest.bottomConnection.designation}) does not match the wellhead top " +
                        "'${topSection.name}' (${topSection.topConnection.designation}).",
                    listOf(lowest.id, topSection.id),
                )
            }
            if (state is OperatingState.CompletedWithTree) {
                // In CT state the CT stack validator already checks the tree bore path.
                for ((upper, lower) in tree.components.zipWithNext()) {
                    if (!connects(upper.bottomConnection, lower.topConnection)) {
                        issues += ValidationIssue(
                            Severity.ERROR, "TREE_CONNECTION_MISMATCH",
                            "Connection mismatch between '${upper.name}' and '${lower.name}'.", listOf(upper.id, lower.id),
                        )
                    }
                }
            }
            treeWingIssues(tree, issues)
        }

        // --- CT stack -------------------------------------------------------------------
        val ctStackTop = (state as? OperatingState.CoiledTubingIntervention)?.let { ct ->
            treeTopWithoutCap?.let { stackTop(it, ct.rigUp.pressureControlStack.components.map { c -> c.height }) }
        }

        // --- Drilling -------------------------------------------------------------------
        var drillingStackTop: Length? = null
        var drillFloor: Length? = null
        var clearance: Length? = null
        if (state is OperatingState.Drilling) {
            val rig = state.rigUp.rig
            val bop = state.rigUp.bopStack.components
            drillingStackTop = top?.let { stackTop(it, bop.map { c -> c.height }) }
            if (bop.any { it.height == null }) {
                issues += ValidationIssue(
                    Severity.WARNING, "BOP_HEIGHT_MISSING",
                    "Height not entered for some drilling BOP components; the stack-up height is not calculated.",
                    bop.filter { it.height == null }.map { it.id },
                )
            }
            for ((upper, lower) in bop.zipWithNext()) {
                if (!connects(upper.bottomConnection, lower.topConnection)) {
                    issues += ValidationIssue(
                        Severity.ERROR, "BOP_CONNECTION_MISMATCH",
                        "Connection mismatch between '${upper.name}' (${upper.bottomConnection.designation}) " +
                            "and '${lower.name}' (${lower.topConnection.designation}).",
                        listOf(upper.id, lower.id),
                    )
                }
            }
            val lowest = bop.lastOrNull()
            val topSection = wellhead?.sections?.firstOrNull()
            if (lowest != null && topSection != null && !connects(lowest.bottomConnection, topSection.topConnection)) {
                issues += ValidationIssue(
                    Severity.ERROR, "BOP_WELLHEAD_CONNECTION_MISMATCH",
                    "Drilling BOP bottom '${lowest.name}' (${lowest.bottomConnection.designation}) does not match the wellhead top " +
                        "'${topSection.name}' (${topSection.topConnection.designation}).",
                    listOf(lowest.id, topSection.id),
                )
            }
            drillFloor = rig.floorBelowRkb?.let { rkbAboveGl - it }
            if (rig.floorBelowRkb == null) {
                issues += ValidationIssue(
                    Severity.INFO, "RIG_FLOOR_BELOW_RKB_MISSING",
                    "RKB-to-drill-floor distance not entered; the floor is drawn at the RKB datum.", listOf(rig.id),
                )
            }
            val clearHeight = rig.clearHeightUnderRotaryBeams
            if (clearHeight != null && drillingStackTop != null) {
                clearance = clearHeight - drillingStackTop
                if (clearance < Length.ZERO) {
                    issues += ValidationIssue(
                        Severity.ERROR, "BOP_EXCEEDS_SUBSTRUCTURE_CLEARANCE",
                        "Drilling stack top is ${fmt(-clearance)} m above the clear height under the rotary beams.",
                        listOf(rig.id, state.rigUp.bopStack.id),
                    )
                }
            } else if (clearHeight == null) {
                issues += ValidationIssue(
                    Severity.INFO, "RIG_CLEAR_HEIGHT_MISSING",
                    "Clear height under the rotary beams not entered; substructure clearance check skipped.", listOf(rig.id),
                )
            }
            if (drillFloor != null && drillingStackTop != null && drillingStackTop > drillFloor) {
                issues += ValidationIssue(
                    Severity.ERROR, "BOP_ABOVE_DRILL_FLOOR",
                    "Drilling stack top is above the drill floor.", listOf(rig.id, state.rigUp.bopStack.id),
                )
            }
        }

        return SurfaceStackup(
            cellarFloor = cellarFloor,
            wellheadBase = base,
            wellheadTop = top,
            wellheadBasis = basis,
            treeTopWithCap = treeTopWithCap,
            treeTopWithoutCap = treeTopWithoutCap,
            ctStackTop = ctStackTop,
            drillingStackTop = drillingStackTop,
            drillFloor = drillFloor,
            clearanceUnderRotaryBeams = clearance,
            rkbToWellheadTop = top?.let { rkbAboveGl - it },
            issues = issues,
            installedWellhead = installed,
        )
    }

    private fun treeWingIssues(tree: ChristmasTree, issues: MutableList<ValidationIssue>) {
        for (wing in tree.wingOutlets) {
            val branch = tree.components.firstOrNull { it.id == wing.branchComponentId }
            if (branch == null || branch.type != StackComponentType.FLOW_TEE) {
                issues += ValidationIssue(
                    Severity.ERROR, "TREE_WING_BRANCH_INVALID",
                    "Wing outlet '${wing.role.label}' must branch from a flow tee/cross in the tree.", listOf(wing.id),
                )
            }
            if (wing.components.any { it.type.group != StackGroup.CHRISTMAS_TREE }) {
                issues += ValidationIssue(
                    Severity.ERROR, "TREE_WING_COMPONENT_INVALID",
                    "Wing outlet '${wing.role.label}' contains non-tree equipment.", listOf(wing.id),
                )
            }
        }
    }

    /** Base + sum of heights, or null when any height is missing. */
    private fun stackTop(base: Length, heights: List<Length?>): Length? =
        if (heights.any { it == null }) null else base + Length(heights.sumOf { it!!.meters })

    private fun installationIssues(installed: InstalledWellhead, wellheadId: String, issues: MutableList<ValidationIssue>) {
        installed.unknownNippleUpSectionId?.let { id ->
            issues += ValidationIssue(
                Severity.ERROR, "BOP_NIPPLE_UP_SECTION_NOT_FOUND",
                "The wellhead section '$id' entered for the drilling BOP does not exist; the BOP is placed by its bottom connection instead.",
                listOf(wellheadId),
            )
        }
        if (installed.notInstalled.isEmpty()) return
        val top = installed.top ?: return
        val names = installed.notInstalled.joinToString { "'${it.name}'" }
        val reason = when (installed.basis) {
            InstalledSectionsBasis.ENTERED -> "as entered"
            InstalledSectionsBasis.MATCHING_BOP_CONNECTION -> "its bottom connection matches that section"
            InstalledSectionsBasis.ALL_SECTIONS -> return
        }
        issues += ValidationIssue(
            Severity.INFO, "WELLHEAD_SECTIONS_NOT_INSTALLED",
            "Drilling state: BOP nippled up on '${top.name}' ($reason). Not installed yet: $names.",
            installed.notInstalled.map { it.id },
        )
    }

    private fun connects(a: EndConnection, b: EndConnection) = a.designation.trim() == b.designation.trim()

    private fun fmt(l: Length) = String.format(java.util.Locale.US, "%.2f", l.meters)
}
