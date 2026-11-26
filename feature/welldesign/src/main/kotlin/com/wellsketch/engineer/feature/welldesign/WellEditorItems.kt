package com.wellsketch.engineer.feature.welldesign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.catalog.TubularKind
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.geology.Lithology
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.TubingConfiguration
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.ui.components.ChoiceField
import com.wellsketch.engineer.core.ui.components.DepthField
import com.wellsketch.engineer.core.ui.components.LabeledValue
import com.wellsketch.engineer.core.ui.components.SectionCard
import com.wellsketch.engineer.core.ui.components.TextEntryField
import com.wellsketch.engineer.core.ui.components.TubularPicker
import com.wellsketch.engineer.core.ui.format.UnitFormatter
import com.wellsketch.engineer.domain.welldesign.EditOutcome
import com.wellsketch.engineer.domain.welldesign.WellEdits
import com.wellsketch.engineer.domain.welldesign.WellStructureDefaults
import com.wellsketch.engineer.domain.welldesign.WellStructureEdits
import kotlin.math.abs

/**
 * Editors of the well structure, as in the HTML prototype's Formations, Holes and Strings
 * tabs: add, remove and change every formation, hole section, casing string / liner (type and
 * catalogue size) and the production tubing. The sketch and all calculations update from the
 * edited well immediately.
 */
internal fun LazyListScope.wellStructureEditorItems(
    well: Well,
    completionState: CompletionState,
    ctx: WellGeometryContext,
    f: UnitFormatter,
    onEvent: (WellDesignEvent) -> Unit,
    pad: Modifier,
    showsCompletion: Boolean = true,
) {
    val ref = ctx.canonicalReference
    val edit: ((Well) -> EditOutcome) -> Unit = { transform -> onEvent(WellDesignEvent.Edit(transform)) }
    fun md(v: Length) = MeasuredDepth(v, ref)

    item {
        SectionCard("Formations (TVD below GL)", pad) {
            well.formations.forEachIndexed { i, formation ->
                Text("Formation ${i + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                TextEntryField("Name", formation.name, { name -> edit { w -> WellStructureEdits.setFormationName(w, formation.id, name) } })
                ChoiceField(
                    "Lithology", Lithology.entries, formation.lithology, { it.label },
                    { lith -> edit { w -> WellStructureEdits.setFormationLithology(w, formation.id, lith) } },
                )
                DepthField("Top TVD below GL", ctx.tvdBelowGroundLevel(formation.top), f, { v ->
                    edit { w -> WellEdits.setFormationTop(w, formation.id, TrueVerticalDepth.gl(v)) }
                })
                TextButton(
                    onClick = { edit { w -> WellStructureEdits.removeFormation(w, formation.id) } },
                    enabled = well.formations.size > 1,
                ) { Text("Remove formation") }
                HorizontalDivider()
            }
            OutlinedButton(onClick = {
                val lastBelowGl = well.formations.maxOfOrNull { ctx.tvdBelowGroundLevel(it.top).meters } ?: 0.0
                edit { w ->
                    WellStructureEdits.addFormation(
                        w, "Formation ${w.formations.size + 1}", Lithology.UNKNOWN,
                        TrueVerticalDepth.gl(Length.meters(lastBelowGl + 100.0)),
                    )
                }
            }) { Text("+ Add formation") }
        }
    }

    item {
        SectionCard("Hole sections (MD ${ref.shortLabel})", pad) {
            val holes = well.holeSections.sortedBy { ctx.canonical(it.top).meters }
            if (holes.isEmpty()) Text("No hole sections yet. The first section starts at ground level.", style = MaterialTheme.typography.bodySmall)
            holes.forEach { h ->
                Text(h.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                LabeledValue("Top (follows the section above)", f.depth(ctx.canonical(h.top)))
                ChoiceField(
                    label = "Bit size",
                    options = TubularCatalog.BIT_SIZES_IN,
                    selected = TubularCatalog.BIT_SIZES_IN.firstOrNull { abs(it - h.bitDiameter.inches) < 1e-6 },
                    optionLabel = { f.nominalSize(Length.inches(it)) },
                    placeholder = f.nominalSize(h.bitDiameter),
                    onSelect = { bit -> edit { w -> WellStructureEdits.setHoleBitSize(w, h.id, Length.inches(bit)) } },
                )
                DepthField("Bottom MD", ctx.canonical(h.bottom), f, { v -> edit { w -> WellEdits.setHoleSectionBottom(w, h.id, md(v)) } })
                TextButton(onClick = { edit { w -> WellStructureEdits.removeHoleSection(w, h.id) } }) { Text("Remove hole section") }
                HorizontalDivider()
            }
            OutlinedButton(onClick = {
                val last = holes.lastOrNull()
                val bit = last?.let { l -> TubularCatalog.BIT_SIZES_IN.firstOrNull { it < l.bitDiameter.inches - 1e-6 } } ?: 26.0
                val bottom = last?.let { MeasuredDepth(it.bottom.value + Length.meters(500.0), it.bottom.reference) }
                    ?: MeasuredDepth(WellStructureEdits.groundLevelMd(well).value + Length.meters(50.0), DepthReference.Rkb)
                edit { w -> WellStructureEdits.addHoleSection(w, Length.inches(bit), bottom) }
            }) { Text("+ Add hole section") }
        }
    }

    item {
        SectionCard("Casing strings & liners (MD ${ref.shortLabel})", pad) {
            val strings = well.casingStrings.sortedByDescending { it.spec.outerDiameter.meters }
            if (strings.isEmpty()) Text("No casing yet. Add the conductor or surface casing first.", style = MaterialTheme.typography.bodySmall)
            strings.forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${s.name} · ${f.nominalSize(s.spec.outerDiameter)}",
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    )
                    ChoiceField("Type", CasingRole.entries, s.role, { it.label }, { role -> edit { w -> WellStructureEdits.setCasingRole(w, s.id, role) } })
                    TextEntryField("Name", s.name, { name -> edit { w -> WellStructureEdits.setCasingName(w, s.id, name) } })
                    TubularPicker(TubularKind.CASING, s.spec, f, { spec -> edit { w -> WellStructureEdits.setCasingSpec(w, s.id, spec) } })
                    DepthField(if (s.isLiner) "Liner top (hanger) MD" else "Top MD", ctx.canonical(s.top), f, { v ->
                        edit { w -> WellEdits.setCasingTop(w, s.id, md(v)) }
                    })
                    DepthField("Shoe MD", ctx.canonical(s.shoe.depth), f, { v -> edit { w -> WellEdits.setCasingShoe(w, s.id, md(v)) } })
                    val cement = s.cement
                    if (cement != null) {
                        DepthField("Top of cement MD", ctx.canonical(cement.top), f, { v -> edit { w -> WellEdits.setTopOfCement(w, s.id, md(v)) } })
                        val tailTop = cement.tailTop
                        if (tailTop != null) {
                            DepthField("Tail slurry top MD", ctx.canonical(tailTop), f, { v -> edit { w -> WellStructureEdits.setCementTailTop(w, s.id, md(v)) } })
                            TextButton(onClick = { edit { w -> WellStructureEdits.setCementTailTop(w, s.id, null) } }) { Text("One slurry (no lead / tail)") }
                        } else {
                            TextButton(onClick = {
                                // Default split: tail over the lower third of the column.
                                val toc = ctx.canonical(cement.top)
                                val boc = ctx.canonical(cement.bottom)
                                edit { w -> WellStructureEdits.setCementTailTop(w, s.id, md(toc + (boc - toc) * (2.0 / 3.0))) }
                            }) { Text("+ Split lead / tail") }
                        }
                        TextButton(onClick = { edit { w -> WellStructureEdits.setCement(w, s.id, null) } }) { Text("Remove cement") }
                    } else {
                        TextButton(onClick = {
                            // Default TOC 200 m above the shoe (never above the string top), in the canonical datum.
                            val toc = maxOf(ctx.canonical(s.top), ctx.canonical(s.shoe.depth) - Length.meters(200.0))
                            edit { w -> WellStructureEdits.setCement(w, s.id, md(toc)) }
                        }) { Text("+ Add cement") }
                    }
                    val floatCollar = s.shoe.floatCollarDepth
                    if (floatCollar != null) {
                        DepthField("Float collar MD", ctx.canonical(floatCollar), f, { v -> edit { w -> WellStructureEdits.setFloatCollar(w, s.id, md(v)) } })
                        TextButton(onClick = { edit { w -> WellStructureEdits.setFloatCollar(w, s.id, null) } }) { Text("Remove float collar") }
                    } else {
                        TextButton(onClick = {
                            val shoe = s.shoe.depth
                            edit { w -> WellStructureEdits.setFloatCollar(w, s.id, MeasuredDepth(shoe.value - Length.meters(25.0), shoe.reference)) }
                        }) { Text("+ Add float collar") }
                    }
                    TextButton(onClick = { edit { w -> WellStructureEdits.removeCasingString(w, s.id) } }) { Text("Remove this string") }
                    HorizontalDivider()
                }
            }
            OutlinedButton(onClick = { edit { w -> WellStructureDefaults.addNextString(w) } }) { Text("+ Add casing / liner") }
        }
    }

    // Drilling states show casing and open hole only; tubing belongs to a completion state.
    if (!showsCompletion) return

    item {
        SectionCard("Production tubing – ${completionState.label}", pad) {
            val completion = well.completion(completionState)
            when (val tubing = completion?.tubing) {
                is TubingConfiguration.Installed -> {
                    TubularPicker(TubularKind.TUBING, tubing.tubing.spec, f, { spec ->
                        edit { w -> WellStructureEdits.setTubingSpec(w, completionState, spec) }
                    })
                    DepthField("End of tubing MD", ctx.canonical(tubing.tubing.endOfTubing), f, { v ->
                        edit { w -> WellEdits.setEndOfTubing(w, completionState, md(v)) }
                    })
                    TextButton(onClick = { edit { w -> WellStructureEdits.removeTubing(w, completionState) } }) { Text("Remove tubing") }
                }
                else -> {
                    Text(
                        if (tubing is TubingConfiguration.ExplicitlyNone) "No tubing – ${tubing.reason}" else "No completion recorded for this state.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { edit { w -> WellStructureDefaults.installDefaultTubing(w, completionState) } }) { Text("+ Install tubing") }
                }
            }
        }
    }
}
