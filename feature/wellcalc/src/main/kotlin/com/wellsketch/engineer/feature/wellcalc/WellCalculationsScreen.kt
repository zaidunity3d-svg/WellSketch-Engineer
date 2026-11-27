@file:OptIn(ExperimentalLayoutApi::class)

package com.wellsketch.engineer.feature.wellcalc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsketch.engineer.core.engineering.volumes.DiameterBasis
import com.wellsketch.engineer.core.engineering.volumes.VolumeBreakdown
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.model.units.VolumePerLength
import com.wellsketch.engineer.core.ui.components.ChoiceField
import com.wellsketch.engineer.core.ui.components.DepthField
import com.wellsketch.engineer.core.ui.components.IssueList
import com.wellsketch.engineer.core.ui.components.LabeledValue
import com.wellsketch.engineer.core.ui.components.ModuleTopBar
import com.wellsketch.engineer.core.ui.components.NoticeCard
import com.wellsketch.engineer.core.ui.components.NumberField
import com.wellsketch.engineer.core.ui.components.ProvenanceBadge
import com.wellsketch.engineer.core.ui.components.SectionCard
import com.wellsketch.engineer.core.ui.format.UnitFormatter
import com.wellsketch.engineer.domain.wellcalc.CalculationStatus
import com.wellsketch.engineer.domain.wellcalc.CementJobVolumes
import com.wellsketch.engineer.domain.wellcalc.HydrostaticCalculator
import com.wellsketch.engineer.domain.wellcalc.ObjectVolumeMode
import com.wellsketch.engineer.domain.wellcalc.WellCalculationCatalog

/**
 * Well Calculations: volumes, tubular geometry, hydrostatics and cementing for the selected
 * project, computed by versioned pure-Kotlin calculators. Every result shows the geometry
 * it used; nothing is estimated from drawings.
 */
@Composable
fun WellCalculationsScreen(viewModel: WellCalculationsViewModel, unitSystem: UnitSystem, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val f = remember(unitSystem) { UnitFormatter(unitSystem) }
    Scaffold(
        topBar = {
            ModuleTopBar(
                title = "Well Calculations",
                subtitle = state.project?.let { "${it.well.name} – ${it.name}" },
                onBack = onBack,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.error != null -> NoticeCard(state.error!!, Modifier.padding(16.dp), warning = true)
                state.loading || state.report == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> WellCalcContent(state, f, viewModel::onEvent)
            }
        }
    }
}

@Composable
private fun WellCalcContent(state: WellCalcUiState, f: UnitFormatter, onEvent: (WellCalcEvent) -> Unit) {
    val report = state.report ?: return
    val project = state.project ?: return
    val ref = report.geometry.ctx.canonicalReference.shortLabel
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            NoticeCard(
                "Calculations v1.0 – implemented and unit-tested, pending your engineering validation. " +
                    "Results use the stated tubular IDs and nominal bit sizes; the formula basis is listed at the bottom.",
            )
        }
        if (project.isSyntheticDemo) {
            item { NoticeCard("Synthetic demonstration data. Not real well, catalog or test data.", warning = true) }
        }
        if (state.availableStates.isNotEmpty()) {
            item {
                Column {
                    Text("Completion state", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.availableStates.forEach { s ->
                            FilterChip(
                                selected = s == report.completionState,
                                onClick = { onEvent(WellCalcEvent.SelectCompletionState(s)) },
                                label = { Text(s.label) },
                            )
                        }
                    }
                }
            }
        }

        // --- Well volumes ------------------------------------------------------------------
        item {
            SectionCard("Well volumes (MD $ref)") {
                VolumeSummary("Flow path to accessible bottom", report.flowPath, f)
                report.tubingInside?.let { VolumeSummary("Inside tubing", it, f) }
                report.belowTubing?.let { VolumeSummary("Below end of tubing", it, f) }
                if (report.tubingAnnulus.segments.isNotEmpty()) VolumeSummary("Tubing × casing annulus (to packer)", report.tubingAnnulus, f)
                Text(
                    "Couplings, component bores, wellhead/tree bore and surface lines are not included.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            SectionCard("Volume between two depths") {
                state.intervalTopMd?.let { top ->
                    DepthField("Top (MD $ref)", top, f, { onEvent(WellCalcEvent.SetIntervalTop(it)) })
                }
                state.intervalBottomMd?.let { bottom ->
                    DepthField("Bottom (MD $ref)", bottom, f, { onEvent(WellCalcEvent.SetIntervalBottom(it)) })
                }
                state.interval?.let { VolumeBreakdownDetails(it, f, showSegments = true) }
            }
        }

        state.objectVolume?.let { ov ->
            item { ObjectVolumeCard(ov, f, ref, onEvent) }
        }

        // --- Tubulars --------------------------------------------------------------------
        item { Text("Tubular geometry (per length)", style = MaterialTheme.typography.titleMedium) }
        items(report.tubulars, key = { it.sourceId }) { row ->
            SectionCard(row.name) {
                ProvenanceBadge(row.provenance)
                LabeledValue("OD / ID", "${f.diameter(row.outerDiameter)} / ${f.diameter(row.innerDiameter)}")
                LabeledValue("Wall", f.diameter(row.wallThickness) + if (row.wallIsStated) " (stated)" else " ((OD − ID)/2)")
                LabeledValue("Drift", row.driftDiameter?.let(f::diameter) ?: "Not provided")
                LabeledValue("Inside capacity", f.capacity(row.insideCapacity))
                LabeledValue("Open-end displacement", f.capacity(row.openEndDisplacement))
                LabeledValue("Closed-end displacement", f.capacity(row.closedEndDisplacement))
                LabeledValue("Interval", "${f.depth(row.topMd)} – ${f.depth(row.bottomMd)} MD $ref")
                LabeledValue("Inside volume over its length", f.volume(row.insideCapacity * row.length))
            }
        }

        // --- Hydrostatics ----------------------------------------------------------------
        item {
            SectionCard("Hydrostatic pressure (single fluid)") {
                NumberField(
                    "Fluid density", f.densityValue(state.inputs.fluidDensity), f.densityUnit, decimals = if (f.densityUnit == "kg/m³") 0 else 3,
                    onValueChange = { onEvent(WellCalcEvent.SetFluidDensity(f.densityFromValue(it))) }, isValid = { it > 0.0 },
                )
                state.hydrostaticTargetMd?.let { target ->
                    DepthField("Target depth (MD $ref)", target, f, { onEvent(WellCalcEvent.SetHydrostaticTarget(it)) })
                }
                NumberField(
                    "Surface pressure", f.pressureValue(state.inputs.surfacePressure), f.pressureUnit, decimals = 0,
                    onValueChange = { onEvent(WellCalcEvent.SetSurfacePressure(f.pressureFromValue(it))) }, isValid = { it >= 0.0 },
                )
                state.hydrostatic?.let { h ->
                    state.fluidTopMd?.let { LabeledValue("Fluid column from", "${f.depth(it)} MD $ref (conduit top)") }
                    LabeledValue("Target TVD below GL", f.depth(h.targetTvdBelowGl))
                    LabeledValue("Vertical height of fluid", f.depth(Length(h.contributions.sumOf { it.verticalHeight.meters })))
                    LabeledValue("Gradient", f.gradient(HydrostaticCalculator.gradient(state.inputs.fluidDensity)))
                    LabeledValue("Hydrostatic pressure", f.pressure(h.hydrostaticPressure))
                    LabeledValue("Pressure at target", f.pressure(h.totalPressure))
                    state.equivalentDensity?.let { LabeledValue("Equivalent density (TVD from RKB)", f.density(it)) }
                    IssueList(h.issues)
                }
                Text(
                    "Static, constant-density fluid using TVD from the survey (not MD). No ECD, temperature or gas effects.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // --- Cementing -------------------------------------------------------------------
        item {
            SectionCard("Cementing – excess policy") {
                NumberField(
                    "Open-hole excess", state.inputs.openHoleExcessPercent, "%", decimals = 0,
                    onValueChange = { onEvent(WellCalcEvent.SetOpenHoleExcess(it)) },
                    isValid = { it in 0.0..(com.wellsketch.engineer.domain.wellcalc.CementExcessPolicy.MAX_EXCESS * 100.0) },
                )
                NumberField(
                    "Cased-hole excess", state.inputs.casedHoleExcessPercent, "%", decimals = 0,
                    onValueChange = { onEvent(WellCalcEvent.SetCasedHoleExcess(it)) },
                    isValid = { it in 0.0..(com.wellsketch.engineer.domain.wellcalc.CementExcessPolicy.MAX_EXCESS * 100.0) },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.inputs.includeRathole,
                        onClick = { onEvent(WellCalcEvent.SetIncludeRathole(!state.inputs.includeRathole)) },
                        label = { Text("Include rathole") },
                    )
                }
                Text(
                    "Open-hole excess applies to annulus against the bit size; cased-hole excess to annulus inside a previous casing (normally 0 %).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(report.cementJobs, key = { "cement:" + it.string.id }) { job -> CementCard(job, f, ref) }

        // --- Checks and formula basis ----------------------------------------------------
        item { SectionCard("Checks") { IssueList(report.issues + report.flowPath.issues + report.tubingAnnulus.issues) } }
        item {
            SectionCard("Formula basis and status") {
                WellCalculationCatalog.specifications.forEach { spec ->
                    Text(spec.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    LabeledValue("Status", spec.status.label + (spec.version?.let { " · v$it" } ?: ""))
                    spec.references.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (spec.status != CalculationStatus.VALIDATED) HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Volume by named objects (HTML v0.9 "Volume calculator"): inside a chosen pipe, or the
 * annulus between it and a chosen outer string or the open hole, e.g. for a cement job
 * between the 9-5/8 in and 13-3/8 in casing.
 */
@Composable
private fun ObjectVolumeCard(ov: ObjectVolumeUi, f: UnitFormatter, ref: String, onEvent: (WellCalcEvent) -> Unit) {
    SectionCard("Volume by string (inside / between strings)") {
        ChoiceField(
            "Calculation", ObjectVolumeMode.entries, ov.mode, { it.label },
            { onEvent(WellCalcEvent.SelectObjectMode(it)) },
        )
        ChoiceField(
            "Pipe", ov.pipes, ov.pipe,
            { "${it.name} – ${f.nominalSize(it.outerDiameter)} (ID ${f.diameter(it.innerDiameter)})" },
            { onEvent(WellCalcEvent.SelectObjectPipe(it.id)) },
        )
        if (ov.mode == ObjectVolumeMode.ANNULUS) {
            ChoiceField(
                "Outer wall", ov.boundaries, ov.boundary, { it.label },
                { onEvent(WellCalcEvent.SelectObjectBoundary(it)) },
            )
        }
        DepthField("Top (MD $ref)", ov.topMd, f, { onEvent(WellCalcEvent.SetObjectTop(it)) })
        DepthField("Bottom (MD $ref)", ov.bottomMd, f, { onEvent(WellCalcEvent.SetObjectBottom(it)) })
        val length = ov.bottomMd - ov.topMd
        if (ov.result.segments.isNotEmpty() && length.meters > 0.0) {
            LabeledValue("Length", f.depth(length))
            LabeledValue("Average capacity", f.capacity(VolumePerLength(ov.result.total.cubicMeters / length.meters)))
        }
        VolumeBreakdownDetails(ov.result, f, showSegments = true)
        Text(
            "Σ π/4 × (outer ID² − pipe OD²) × length for the annulus, π/4 × ID² × length inside. Nominal body dimensions; " +
                "couplings and tapered strings are not included.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun VolumeSummary(title: String, v: VolumeBreakdown, f: UnitFormatter) {
    val interval = if (v.topMd != null && v.bottomMd != null) "${f.depth(v.topMd!!)} – ${f.depth(v.bottomMd!!)}" else "—"
    LabeledValue(title, "${f.volume(v.total)}  ($interval)")
}

@Composable
private fun VolumeBreakdownDetails(v: VolumeBreakdown, f: UnitFormatter, showSegments: Boolean) {
    LabeledValue("Volume", f.volume(v.total))
    if (showSegments) {
        v.segments.forEach { s ->
            val dia = s.innerDiameter?.let { "${f.diameter(s.outerDiameter)} × ${f.diameter(it)}" } ?: f.diameter(s.outerDiameter)
            val basis = when (s.outerBasis) {
                DiameterBasis.BIT_SIZE -> ", bit size"
                DiameterBasis.CALIPER -> ", caliper"
                DiameterBasis.STATED_ID -> ""
            }
            Text(
                "${f.depth(s.topMd)} – ${f.depth(s.bottomMd)} · ${s.description} · $dia$basis · ${f.capacity(s.capacity)}" +
                    (if (s.excessFraction > 0.0) " · +${UnitFormatter.number(s.excessFraction * 100.0, 0)} %" else "") +
                    " → ${f.volume(s.volume)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    IssueList(v.issues)
}

@Composable
private fun CementCard(job: CementJobVolumes, f: UnitFormatter, ref: String) {
    SectionCard("Cement – ${job.string.name}") {
        Text("Annulus", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        VolumeBreakdownDetails(job.annulus, f, showSegments = true)
        job.lead?.let { VolumeSummary("Lead slurry", it, f) }
        job.tail?.let { VolumeSummary("Tail slurry", it, f) }
        LabeledValue("Shoe track", job.shoeTrack?.let { "${f.volume(it.volume)} (${f.depth(it.length)})" } ?: "Not included (float collar not recorded)")
        LabeledValue(
            "Rathole",
            job.rathole?.let { "${f.volume(it.volume)} (${f.depth(it.length)})" + if (job.ratholeIncluded) "" else " – not in total" } ?: "None",
        )
        LabeledValue("Slurry volume", f.volume(job.slurryVolume))
        LabeledValue("Displacement (to float collar, MD $ref)", f.volume(job.displacement.total))
        IssueList(job.issues)
    }
}
