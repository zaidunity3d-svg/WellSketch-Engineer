package com.wellsketch.engineer.core.engineering.surface

import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.surface.Wellhead
import com.wellsketch.engineer.core.model.surface.WellheadSection
import com.wellsketch.engineer.core.model.surface.WellheadSectionType

/** How the wellhead sections installed in an operating state were decided. */
enum class InstalledSectionsBasis(val label: String) {
    /** Completed / CT states: the whole wellhead is installed under the tree. */
    ALL_SECTIONS("All wellhead sections"),
    /** Drilling: the section the BOP is nippled up on was entered. */
    ENTERED("Entered nipple-up section"),
    /** Drilling: uppermost section whose top connection matches the drilling BOP bottom connection. */
    MATCHING_BOP_CONNECTION("Matched to the BOP bottom connection"),
    /** Drilling, no match: tubing head spools are assumed not installed yet. */
    TUBING_HEAD_NOT_YET_INSTALLED("Assumed: tubing head spool not yet installed"),
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
 * 2. otherwise the uppermost section whose top connection matches the BOP bottom connection,
 *    preferring a casing head or casing spool over a tubing head spool (the tubing head spool
 *    is normally installed after the production casing);
 * 3. otherwise the uppermost section below any tubing head spools at the top.
 */
object WellheadInstallation {

    fun installed(wellhead: Wellhead, state: OperatingState): InstalledWellhead {
        val sections = wellhead.sections
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
            val matching = sections.indices.filter { sections[it].topConnection.designation.trim() == bopBottom }
            val pick = matching.firstOrNull { sections[it].type != WellheadSectionType.TUBING_HEAD_SPOOL } ?: matching.firstOrNull()
            if (pick != null) return split(sections, pick, InstalledSectionsBasis.MATCHING_BOP_CONNECTION, unknown)
        }
        val leadingTubingHeads = sections.takeWhile { it.type == WellheadSectionType.TUBING_HEAD_SPOOL }.size
        return if (leadingTubingHeads in 1 until sections.size) {
            split(sections, leadingTubingHeads, InstalledSectionsBasis.TUBING_HEAD_NOT_YET_INSTALLED, unknown)
        } else {
            InstalledWellhead(sections, emptyList(), InstalledSectionsBasis.ALL_SECTIONS, unknown)
        }
    }

    private fun split(sections: List<WellheadSection>, topIndex: Int, basis: InstalledSectionsBasis, unknown: String? = null) =
        InstalledWellhead(sections.drop(topIndex), sections.take(topIndex), basis, unknown)
}
