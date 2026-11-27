package com.wellsketch.engineer.core.engineering.surface

import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.surface.Wellhead
import com.wellsketch.engineer.core.model.surface.WellheadSection

/** How the wellhead sections installed in an operating state were decided. */
enum class InstalledSectionsBasis(val label: String) {
    /** Completed / CT states: the whole wellhead is installed under the tree. */
    ALL_SECTIONS("All wellhead sections"),
    /** Drilling: the section the BOP is nippled up on was entered. */
    ENTERED("Entered nipple-up section"),
    /** Drilling: uppermost section whose top connection matches the drilling BOP bottom connection. */
    MATCHING_BOP_CONNECTION("Matched to the BOP bottom connection"),
}

/**
 * Wellhead sections present in one operating state, ordered top to bottom like
 * [Wellhead.sections]. [notInstalled] are the sections above the installed top (not on the
 * well yet in this state). [unknownNippleUpSectionId] is set when an entered nipple-up
 * section id does not exist in the wellhead (the default rule was used instead).
 */
data class InstalledWellhead(
    val sections: List<WellheadSection>,
    val notInstalled: List<WellheadSection>,
    val basis: InstalledSectionsBasis,
    val unknownNippleUpSectionId: String? = null,
) {
    val top: WellheadSection? get() = sections.firstOrNull()
}

/**
 * Decides which wellhead sections are on the well in an operating state, so the drawing and
 * the stack-up show the same equipment and a drilling BOP is never drawn on a tubing head
 * spool that is not installed yet.
 *
 * Completed and CT states use every section (the tree sits on the top section). In the
 * drilling state the BOP sits on one section and everything above it is not installed yet:
 * 1. the section entered as [com.wellsketch.engineer.core.model.surface.DrillingRigUp.nippledUpOnSectionId];
 * 2. otherwise the uppermost section whose top connection matches the BOP bottom connection;
 * 3. otherwise every section (nothing is assumed; the connection mismatch is reported).
 * Practice differs between operators (e.g. a BOP on the tubing head spool through a DSA while
 * drilling the last hole section), so no section type is assumed missing.
 */
object WellheadInstallation {

    fun installed(wellhead: Wellhead, state: OperatingState): InstalledWellhead {
        val sections = wellhead.sections
        // Workover on a completed well: every section is on the well unless a section is entered.
        if (state is OperatingState.Workover) {
            val i = sections.indexOfFirst { it.id == state.rigUp.nippledUpOnSectionId }
            return if (i >= 0) split(sections, i, InstalledSectionsBasis.ENTERED) else InstalledWellhead(sections, emptyList(), InstalledSectionsBasis.ALL_SECTIONS)
        }
        val rigUp = (state as? OperatingState.Drilling)?.rigUp
        if (rigUp == null || sections.isEmpty()) return InstalledWellhead(sections, emptyList(), InstalledSectionsBasis.ALL_SECTIONS)

        val entered = rigUp.nippledUpOnSectionId
        if (entered != null) {
            val i = sections.indexOfFirst { it.id == entered }
            if (i >= 0) return split(sections, i, InstalledSectionsBasis.ENTERED)
        }
        val unknown = entered // reaching here with an entered id means it was not found

        val bopBottom = rigUp.bopStack.components.lastOrNull()?.bottomConnection?.designation?.trim()
        if (!bopBottom.isNullOrEmpty()) {
            val pick = sections.indexOfFirst { it.topConnection.designation.trim() == bopBottom }
            if (pick >= 0) return split(sections, pick, InstalledSectionsBasis.MATCHING_BOP_CONNECTION, unknown)
        }
        return InstalledWellhead(sections, emptyList(), InstalledSectionsBasis.ALL_SECTIONS, unknown)
    }

    private fun split(sections: List<WellheadSection>, topIndex: Int, basis: InstalledSectionsBasis, unknown: String? = null) =
        InstalledWellhead(sections.drop(topIndex), sections.take(topIndex), basis, unknown)
}
