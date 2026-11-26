package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.geology.FormationTop
import com.wellsketch.engineer.core.model.geology.Lithology
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CasingShoe
import com.wellsketch.engineer.core.model.well.CasingString
import com.wellsketch.engineer.core.model.well.CementSheath
import com.wellsketch.engineer.core.model.well.CementStatus
import com.wellsketch.engineer.core.model.well.Completion
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.HoleSection
import com.wellsketch.engineer.core.model.well.TubingConfiguration
import com.wellsketch.engineer.core.model.well.TubingString
import com.wellsketch.engineer.core.model.well.TubularSpec
import com.wellsketch.engineer.core.model.well.Well
import java.util.UUID

/**
 * Structural edits of a well: add, remove and re-type formations, hole sections, casing
 * strings, cement and tubing (the editors of the HTML prototype v0.9, on the Android model).
 * Like [WellEdits], every operation returns a new well or a rejection; only values that
 * cannot be represented are rejected, and geometric consistency (overlaps, OD fitting inside
 * the outer string, liner tops) is left to the validator so nothing is silently "fixed".
 * All new depths are measured from RKB.
 */
object WellStructureEdits {

    internal fun newId(prefix: String) = "$prefix-${UUID.randomUUID().toString().take(8)}"

    /** Ground level as a measured depth from RKB (RKB height above GL). */
    fun groundLevelMd(well: Well): MeasuredDepth {
        val h = (well.datums.rkbElevation - well.datums.groundLevelElevation).meters
        // rounded to the micrometre so 31.1 − 20.0 gives 11.1, not 11.100000000000001
        return MeasuredDepth.rkb(Length.meters(Math.round(h * 1e6) / 1e6))
    }

    private fun user() = DataProvenance.userEntered()

    // --- Formations -------------------------------------------------------------------

    fun addFormation(well: Well, name: String, lithology: Lithology, top: TrueVerticalDepth, id: String = newId("fm")): EditOutcome {
        if (name.isBlank()) return EditOutcome.Rejected("Enter a formation name")
        if (top.value < Length.ZERO) return EditOutcome.Rejected("Top depth must not be negative")
        if (well.formations.any { it.id == id }) return EditOutcome.Rejected("Duplicate formation id")
        val added = FormationTop(id = id, name = name.trim(), top = top, lithology = lithology, provenance = user())
        return EditOutcome.Applied(well.copy(formations = (well.formations + added).sortedBy { tvdFromRkb(well, it.top) }))
    }

    /** TVD in metres below RKB, so tops stored from GL and from RKB sort together. */
    private fun tvdFromRkb(well: Well, tvd: TrueVerticalDepth): Double = when (tvd.reference) {
        com.wellsketch.engineer.core.model.datum.DepthReference.GroundLevel -> tvd.value.meters + groundLevelMd(well).value.meters
        else -> tvd.value.meters
    }

    fun removeFormation(well: Well, formationId: String): EditOutcome {
        if (well.formations.none { it.id == formationId }) return EditOutcome.Rejected("Unknown formation")
        return EditOutcome.Applied(well.copy(formations = well.formations.filterNot { it.id == formationId }))
    }

    fun setFormationName(well: Well, formationId: String, name: String): EditOutcome {
        if (name.isBlank()) return EditOutcome.Rejected("Enter a formation name")
        if (well.formations.none { it.id == formationId }) return EditOutcome.Rejected("Unknown formation")
        return EditOutcome.Applied(well.copy(formations = well.formations.map { if (it.id == formationId) it.copy(name = name.trim()) else it }))
    }

    fun setFormationLithology(well: Well, formationId: String, lithology: Lithology): EditOutcome {
        if (well.formations.none { it.id == formationId }) return EditOutcome.Rejected("Unknown formation")
        return EditOutcome.Applied(well.copy(formations = well.formations.map { if (it.id == formationId) it.copy(lithology = lithology) else it }))
    }

    // --- Hole sections ----------------------------------------------------------------

    /** Adds the next hole section: its top is the deepest existing bottom (GL for the first). */
    fun addHoleSection(well: Well, bitDiameter: Length, bottom: MeasuredDepth, name: String? = null, id: String = newId("hole")): EditOutcome {
        if (!(bitDiameter > Length.ZERO)) return EditOutcome.Rejected("Bit size must be positive")
        val top = well.holeSections.maxByOrNull { it.bottom.value.meters }?.bottom ?: groundLevelMd(well)
        if (bottom.reference != top.reference) return EditOutcome.Rejected("Use the same depth reference as the hole above")
        if (!(bottom.value > top.value)) return EditOutcome.Rejected("Bottom must be deeper than the section top (${"%.1f".format(top.value.meters)} m)")
        val section = HoleSection(
            id = id,
            name = name?.takeIf { it.isNotBlank() } ?: "${plainInches(bitDiameter)} in hole",
            bitDiameter = bitDiameter, top = top, bottom = bottom, provenance = user(),
        )
        return EditOutcome.Applied(well.copy(holeSections = well.holeSections + section))
    }

    /** Removes a hole section; the section above takes over its interval so the hole stays continuous. */
    fun removeHoleSection(well: Well, holeId: String): EditOutcome {
        val sorted = well.holeSections.sortedBy { it.top.value.meters }
        val index = sorted.indexOfFirst { it.id == holeId }
        if (index < 0) return EditOutcome.Rejected("Unknown hole section")
        val removed = sorted[index]
        val rest = sorted.toMutableList().apply { removeAt(index) }
        if (index > 0) rest[index - 1] = rest[index - 1].copy(bottom = removed.bottom)
        else if (rest.isNotEmpty()) rest[0] = rest[0].copy(top = removed.top)
        return EditOutcome.Applied(well.copy(holeSections = rest))
    }

    fun setHoleBitSize(well: Well, holeId: String, bitDiameter: Length): EditOutcome {
        if (!(bitDiameter > Length.ZERO)) return EditOutcome.Rejected("Bit size must be positive")
        if (well.holeSections.none { it.id == holeId }) return EditOutcome.Rejected("Unknown hole section")
        return EditOutcome.Applied(
            well.copy(holeSections = well.holeSections.map { h ->
                if (h.id != holeId) return@map h
                val defaultName = h.name == "${plainInches(h.bitDiameter)} in hole"
                h.copy(bitDiameter = bitDiameter, name = if (defaultName) "${plainInches(bitDiameter)} in hole" else h.name)
            }),
        )
    }

    // --- Casing and liners --------------------------------------------------------------

    fun addCasingString(
        well: Well,
        role: CasingRole,
        spec: TubularSpec,
        top: MeasuredDepth,
        shoe: MeasuredDepth,
        name: String? = null,
        id: String = newId("csg"),
    ): EditOutcome {
        if (top.value < Length.ZERO) return EditOutcome.Rejected("Top depth must not be negative")
        if (top.reference != shoe.reference) return EditOutcome.Rejected("Top and shoe must use the same depth reference")
        if (!(shoe.value > top.value)) return EditOutcome.Rejected("Shoe must be deeper than the top")
        if (well.casingStrings.any { it.id == id }) return EditOutcome.Rejected("Duplicate string id")
        val string = CasingString(
            id = id, name = name?.takeIf { it.isNotBlank() } ?: role.label, role = role, spec = spec,
            top = top, shoe = CasingShoe(shoe), provenance = user(),
        )
        return EditOutcome.Applied(well.copy(casingStrings = well.casingStrings + string))
    }

    /** Removes a string and any wellhead reference to it. */
    fun removeCasingString(well: Well, stringId: String): EditOutcome {
        if (well.casingStrings.none { it.id == stringId }) return EditOutcome.Rejected("Unknown casing string")
        val wellhead = well.wellhead?.let { wh ->
            wh.copy(sections = wh.sections.map { if (it.suspendedStringId == stringId) it.copy(suspendedStringId = null) else it })
        }
        return EditOutcome.Applied(well.copy(casingStrings = well.casingStrings.filterNot { it.id == stringId }, wellhead = wellhead))
    }

    /** Changes the string type; a name still equal to the old type's default follows the new type. */
    fun setCasingRole(well: Well, stringId: String, role: CasingRole): EditOutcome =
        updateString(well, stringId) { s -> s.copy(role = role, name = if (s.name == s.role.label) role.label else s.name) }

    fun setCasingName(well: Well, stringId: String, name: String): EditOutcome {
        if (name.isBlank()) return EditOutcome.Rejected("Enter a string name")
        return updateString(well, stringId) { it.copy(name = name.trim()) }
    }

    /** Replaces the pipe body specification (e.g. a catalogue OD + weight with grade and connection). */
    fun setCasingSpec(well: Well, stringId: String, spec: TubularSpec): EditOutcome =
        updateString(well, stringId) { it.copy(spec = spec) }

    fun setFloatCollar(well: Well, stringId: String, depth: MeasuredDepth?): EditOutcome {
        val s = well.casingStrings.firstOrNull { it.id == stringId } ?: return EditOutcome.Rejected("Unknown casing string")
        if (depth != null) {
            if (depth.reference != s.shoe.depth.reference) return EditOutcome.Rejected("Use the shoe's depth reference")
            if (!(depth.value < s.shoe.depth.value)) return EditOutcome.Rejected("Float collar must be above the shoe")
        }
        return updateString(well, stringId) { it.copy(shoe = it.shoe.copy(floatCollarDepth = depth)) }
    }

    /** Adds a designed cement sheath from [top] to the shoe, or removes it when [top] is null. */
    fun setCement(well: Well, stringId: String, top: MeasuredDepth?): EditOutcome {
        val s = well.casingStrings.firstOrNull { it.id == stringId } ?: return EditOutcome.Rejected("Unknown casing string")
        if (top == null) return updateString(well, stringId) { it.copy(cement = null) }
        if (top.reference != s.shoe.depth.reference) return EditOutcome.Rejected("Use the shoe's depth reference")
        if (!(top.value < s.shoe.depth.value)) return EditOutcome.Rejected("Top of cement must be above the shoe")
        // A tail top that would end up above the new TOC is dropped (the whole column is then lead).
        val sheath = s.cement?.copy(top = top, bottom = s.shoe.depth)?.let { c -> c.copy(tailTop = c.tailTop?.takeIf { it.value > top.value }) }
            ?: CementSheath(top = top, bottom = s.shoe.depth, status = CementStatus.DESIGNED, provenance = user())
        return updateString(well, stringId) { it.copy(cement = sheath) }
    }

    /** Sets where the tail slurry starts (between TOC and the cement bottom), or removes the lead/tail split. */
    fun setCementTailTop(well: Well, stringId: String, tailTop: MeasuredDepth?): EditOutcome {
        val s = well.casingStrings.firstOrNull { it.id == stringId } ?: return EditOutcome.Rejected("Unknown casing string")
        val c = s.cement ?: return EditOutcome.Rejected("Add cement to ${s.name} first")
        if (tailTop != null) {
            // Compared in the canonical MD datum, so a tail top entered from GL works with a TOC from RKB.
            val ctx = WellGeometryContext(well)
            val tail = ctx.canonical(tailTop)
            if (!(tail > ctx.canonical(c.top) && tail < ctx.canonical(c.bottom))) {
                return EditOutcome.Rejected("Tail top must be between the top of cement and the cement bottom")
            }
        }
        return updateString(well, stringId) { it.copy(cement = c.copy(tailTop = tailTop)) }
    }

    private fun updateString(well: Well, stringId: String, change: (CasingString) -> CasingString): EditOutcome {
        if (well.casingStrings.none { it.id == stringId }) return EditOutcome.Rejected("Unknown casing string")
        return EditOutcome.Applied(well.copy(casingStrings = well.casingStrings.map { if (it.id == stringId) change(it) else it }))
    }

    // --- Tubing ------------------------------------------------------------------------

    /**
     * Installs (or replaces) the production tubing of a completion state, creating that
     * completion when the well has none yet. Existing components and perforations are kept.
     */
    fun setTubing(
        well: Well,
        state: CompletionState,
        spec: TubularSpec,
        hanger: MeasuredDepth,
        endOfTubing: MeasuredDepth,
        name: String = "Production tubing",
    ): EditOutcome {
        if (hanger.reference != endOfTubing.reference) return EditOutcome.Rejected("Hanger and end of tubing must use the same depth reference")
        if (!(endOfTubing.value > hanger.value)) return EditOutcome.Rejected("End of tubing must be deeper than the hanger")
        val existing = well.completion(state)
        val old = existing?.tubingString
        val tubing = TubingString(
            id = old?.id ?: newId("tbg"), name = old?.name ?: name, spec = spec,
            hangerDepth = hanger, endOfTubing = endOfTubing, provenance = user(),
        )
        val completion = existing?.copy(tubing = TubingConfiguration.Installed(tubing))
            ?: Completion(
                id = newId("cmp"), state = state, name = "${state.label} completion",
                tubing = TubingConfiguration.Installed(tubing), provenance = user(),
            )
        val completions = if (existing != null) well.completions.map { if (it.state == state) completion else it } else well.completions + completion
        return EditOutcome.Applied(well.copy(completions = completions))
    }

    /** Records that the completion has no tubing (CT then runs in the casing or liner). */
    fun removeTubing(well: Well, state: CompletionState, reason: String = "No tubing installed"): EditOutcome {
        val existing = well.completion(state) ?: return EditOutcome.Rejected("No ${state.label} completion")
        val updated = existing.copy(tubing = TubingConfiguration.ExplicitlyNone(reason))
        return EditOutcome.Applied(well.copy(completions = well.completions.map { if (it.state == state) updated else it }))
    }

    fun setTubingSpec(well: Well, state: CompletionState, spec: TubularSpec): EditOutcome {
        val existing = well.completion(state) ?: return EditOutcome.Rejected("No ${state.label} completion")
        val t = existing.tubingString ?: return EditOutcome.Rejected("Completion has no tubing")
        val updated = existing.copy(tubing = TubingConfiguration.Installed(t.copy(spec = spec)))
        return EditOutcome.Applied(well.copy(completions = well.completions.map { if (it.state == state) updated else it }))
    }

    /** 8.5 -> "8.5", 12.25 -> "12.25", 26.0 -> "26". */
    internal fun plainInches(d: Length): String {
        val v = Math.round(d.inches * 1000) / 1000.0
        return if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    }
}
