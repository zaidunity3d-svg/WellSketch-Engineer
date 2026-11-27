package com.wellsketch.engineer.core.model.surface

import com.wellsketch.engineer.core.model.ct.CtRigUp
import com.wellsketch.engineer.core.model.units.Length

/**
 * The surface configuration for one operating state of a well.
 *
 * Each variant carries only the surface equipment that may exist in that state, so a
 * drilling rig/BOP and a Christmas Tree can never be combined incorrectly: the invalid
 * combination is not representable.
 */
sealed interface OperatingState {
    val label: String

    /**
     * Drilling with a rig: RKB exists physically, the drilling BOP stack is nippled up on the
     * wellhead under the rig floor, and no Christmas Tree is installed.
     */
    data class Drilling(override val rigUp: DrillingRigUp) : RigOnWell {
        override val label = "Drilling (rig + drilling BOP)"
        override val rigRkbAboveGroundLevel: Length? get() = null
    }

    /**
     * A rig with a BOP stack nippled up on the wellhead: drilling, or a workover. No Christmas
     * Tree is installed in either.
     */
    sealed interface RigOnWell : OperatingState {
        val rigUp: DrillingRigUp

        /** RKB of this rig above GL; null = the well's (drilling) RKB. */
        val rigRkbAboveGroundLevel: Length?
    }

    /**
     * Workover with a workover rig on a completed well: the tree is removed, a workover BOP
     * is nippled up on the wellhead (normally the top section) and the work string runs from
     * the workover rig floor, whose RKB is [rigRkbAboveGroundLevel] above GL (not the drilling RKB).
     */
    data class Workover(override val rigUp: DrillingRigUp, override val rigRkbAboveGroundLevel: Length) : RigOnWell {
        override val label = "Workover (workover rig + BOP, tree removed)"
    }

    /** Completed well with a Christmas Tree and no intervention equipment rigged up. */
    data class CompletedWithTree(val tree: ChristmasTree) : OperatingState {
        override val label = "Completed (Christmas Tree)"
    }

    /** Coiled-tubing intervention: CT stack and surface spread rigged up on the tree. */
    data class CoiledTubingIntervention(
        val tree: ChristmasTree,
        val rigUp: CtRigUp,
    ) : OperatingState {
        override val label = "Coiled tubing intervention (tree + CT stack)"
    }

    /** No surface equipment defined in the project yet. */
    data object SurfaceNotDefined : OperatingState {
        override val label = "Surface equipment not defined"
    }
}
