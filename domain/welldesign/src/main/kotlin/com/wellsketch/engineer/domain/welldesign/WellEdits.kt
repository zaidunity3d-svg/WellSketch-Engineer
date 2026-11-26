package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.ComponentMounting
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.TubingConfiguration
import com.wellsketch.engineer.core.model.well.Well

/** Result of applying an edit. Rejected edits leave the well unchanged. */
sealed interface EditOutcome {
    data class Applied(val well: Well) : EditOutcome
    data class Rejected(val reason: String) : EditOutcome
}

/**
 * Pure editing operations on a [Well]. Each returns a new well (immutable model), so the
 * UI can re-derive the schematic immediately after every keystroke. Edits only reject
 * values that cannot be represented (unknown ids, negative depths); geometric consistency
 * is reported by the validator instead of being silently "fixed".
 */
object WellEdits {

    fun setCasingShoe(well: Well, stringId: String, shoe: MeasuredDepth): EditOutcome {
        val target = well.casingStrings.firstOrNull { it.id == stringId } ?: return EditOutcome.Rejected("Unknown casing string")
        if (shoe.value < Length.ZERO) return EditOutcome.Rejected("Shoe depth must not be negative")
        val oldShoe = target.shoe.depth
        val updated = target.copy(
            shoe = target.shoe.copy(depth = shoe),
            // keep cement bottom attached to the shoe when it was attached before
            cement = target.cement?.let { c -> if (c.bottom == oldShoe) c.copy(bottom = shoe) else c },
        )
        return EditOutcome.Applied(well.copy(casingStrings = well.casingStrings.map { if (it.id == stringId) updated else it }))
    }

    fun setCasingTop(well: Well, stringId: String, top: MeasuredDepth): EditOutcome {
        val target = well.casingStrings.firstOrNull { it.id == stringId } ?: return EditOutcome.Rejected("Unknown casing string")
        if (top.value < Length.ZERO) return EditOutcome.Rejected("Top depth must not be negative")
        val updated = target.copy(top = top)
        return EditOutcome.Applied(well.copy(casingStrings = well.casingStrings.map { if (it.id == stringId) updated else it }))
    }

    fun setTopOfCement(well: Well, stringId: String, toc: MeasuredDepth): EditOutcome {
        val target = well.casingStrings.firstOrNull { it.id == stringId } ?: return EditOutcome.Rejected("Unknown casing string")
        val cement = target.cement ?: return EditOutcome.Rejected("String has no cement interval")
        if (toc.value < Length.ZERO) return EditOutcome.Rejected("Top of cement must not be negative")
        // A lead/tail split that would end up above the new TOC is dropped.
        val ctx = com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext(well)
        val tail = cement.tailTop?.takeIf { ctx.canonical(it) > ctx.canonical(toc) }
        val updated = target.copy(cement = cement.copy(top = toc, tailTop = tail))
        return EditOutcome.Applied(well.copy(casingStrings = well.casingStrings.map { if (it.id == stringId) updated else it }))
    }

    fun setHoleSectionBottom(well: Well, holeId: String, bottom: MeasuredDepth): EditOutcome {
        val target = well.holeSections.firstOrNull { it.id == holeId } ?: return EditOutcome.Rejected("Unknown hole section")
        if (bottom.value < Length.ZERO) return EditOutcome.Rejected("Depth must not be negative")
        val oldBottom = target.bottom
        // The section that started at the old bottom follows it, so the hole stays continuous.
        return EditOutcome.Applied(
            well.copy(
                holeSections = well.holeSections.map {
                    when {
                        it.id == holeId -> it.copy(bottom = bottom)
                        it.top == oldBottom && bottom.reference == oldBottom.reference -> it.copy(top = bottom)
                        else -> it
                    }
                },
            ),
        )
    }

    fun setFormationTop(well: Well, formationId: String, top: TrueVerticalDepth): EditOutcome {
        if (well.formations.none { it.id == formationId }) return EditOutcome.Rejected("Unknown formation")
        return EditOutcome.Applied(
            well.copy(formations = well.formations.map { if (it.id == formationId) it.copy(top = top) else it }),
        )
    }

    fun setDatumElevations(well: Well, rkbElevation: Length, groundLevelElevation: Length): EditOutcome {
        if (rkbElevation < groundLevelElevation) return EditOutcome.Rejected("RKB must not be below ground level")
        return EditOutcome.Applied(
            well.copy(datums = well.datums.copy(rkbElevation = rkbElevation, groundLevelElevation = groundLevelElevation)),
        )
    }

    fun setEndOfTubing(well: Well, state: CompletionState, endOfTubing: MeasuredDepth): EditOutcome {
        val completion = well.completion(state) ?: return EditOutcome.Rejected("No ${state.label} completion")
        val installed = completion.tubing as? TubingConfiguration.Installed
            ?: return EditOutcome.Rejected("Completion has no tubing")
        if (endOfTubing.value < Length.ZERO) return EditOutcome.Rejected("Depth must not be negative")
        val oldEnd = installed.tubing.endOfTubing
        // Components hanging at the end of the tubing (e.g. the wireline entry guide) move
        // with the end of tubing; components at other depths stay where they are.
        val components = if (oldEnd.reference == endOfTubing.reference) {
            val shift = endOfTubing.value - oldEnd.value
            completion.components.map { c ->
                val attachedToEnd = c.mounting == ComponentMounting.ON_TUBING &&
                    c.top.reference == oldEnd.reference &&
                    (c.top.value + c.length).isApproximately(oldEnd.value, Length.meters(0.001))
                if (attachedToEnd) c.copy(top = MeasuredDepth(c.top.value + shift, c.top.reference)) else c
            }
        } else {
            completion.components
        }
        val updated = completion.copy(
            tubing = installed.copy(tubing = installed.tubing.copy(endOfTubing = endOfTubing)),
            components = components,
        )
        return EditOutcome.Applied(well.copy(completions = well.completions.map { if (it.state == state) updated else it }))
    }

    /** Sets the cellar depth below GL (positive downward); the cellar must already exist. */
    fun setCellarDepth(well: Well, depthBelowGroundLevel: Length): EditOutcome {
        val cellar = well.cellar ?: return EditOutcome.Rejected("No cellar defined")
        if (depthBelowGroundLevel < Length.ZERO) return EditOutcome.Rejected("Cellar depth must not be negative")
        return EditOutcome.Applied(well.copy(cellar = cellar.copy(depthBelowGroundLevel = depthBelowGroundLevel)))
    }

    /**
     * Sets the elevation of the wellhead base relative to GL (negative = below GL, inside the
     * cellar). A base below the cellar floor is accepted and reported by the stack-up check.
     */
    fun setWellheadBase(well: Well, baseRelativeToGroundLevel: Length): EditOutcome {
        val wellhead = well.wellhead ?: return EditOutcome.Rejected("No wellhead defined")
        return EditOutcome.Applied(well.copy(wellhead = wellhead.copy(baseRelativeToGroundLevel = baseRelativeToGroundLevel)))
    }

    /** Sets the data-sheet height of one wellhead section. */
    fun setWellheadSectionHeight(well: Well, sectionId: String, height: Length): EditOutcome {
        val wellhead = well.wellhead ?: return EditOutcome.Rejected("No wellhead defined")
        if (wellhead.sections.none { it.id == sectionId }) return EditOutcome.Rejected("Unknown wellhead section")
        if (height < Length.ZERO) return EditOutcome.Rejected("Section height must not be negative")
        return EditOutcome.Applied(
            well.copy(wellhead = wellhead.copy(sections = wellhead.sections.map { if (it.id == sectionId) it.copy(height = height) else it })),
        )
    }

    /**
     * Sets the wellhead section the drilling BOP is nippled up on (sections above it are not
     * installed in the drilling state); null = decided from the BOP bottom connection.
     */
    fun setBopNippleUpSection(well: Well, sectionId: String?): EditOutcome {
        val rigUp = well.drillingRigUp ?: return EditOutcome.Rejected("No drilling rig-up defined")
        if (sectionId != null && well.wellhead?.sections?.none { it.id == sectionId } != false) {
            return EditOutcome.Rejected("Unknown wellhead section")
        }
        return EditOutcome.Applied(well.copy(drillingRigUp = rigUp.copy(nippledUpOnSectionId = sectionId)))
    }
}
