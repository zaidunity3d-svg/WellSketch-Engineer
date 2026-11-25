@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wellsketch.engineer.feature.welldesign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.model.well.TubingConfiguration
import com.wellsketch.engineer.core.model.well.findObject
import com.wellsketch.engineer.core.ui.components.BREAKDOWN_PENDING_NOTE
import com.wellsketch.engineer.core.ui.components.DepthField
import com.wellsketch.engineer.core.ui.components.DetailCard
import com.wellsketch.engineer.core.ui.components.IssueList
import com.wellsketch.engineer.core.ui.components.LabeledValue
import com.wellsketch.engineer.core.ui.components.ModuleTopBar
import com.wellsketch.engineer.core.ui.components.NoticeCard
import com.wellsketch.engineer.core.ui.components.ProvenanceBadge
import com.wellsketch.engineer.core.ui.components.SectionCard
import com.wellsketch.engineer.core.ui.components.wellObjectDetails
import com.wellsketch.engineer.core.ui.format.UnitFormatter
import com.wellsketch.engineer.core.ui.schematic.WellSchematicCanvas
import com.wellsketch.engineer.domain.welldesign.WellDesignSnapshot
import com.wellsketch.engineer.domain.welldesign.WellEdits
import kotlinx.coroutines.launch

@Composable
fun WellDesignScreen(viewModel: WellDesignViewModel, unitSystem: UnitSystem, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = remember(unitSystem) { UnitFormatter(unitSystem) }
    Scaffold(
        topBar = {
            ModuleTopBar(
                title = state.project?.well?.name ?: "Well Design",
                subtitle = state.project?.name ?: "Well Design",
                onBack = onBack,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val snapshot = state.snapshot
            when {
                state.error != null -> NoticeCard(state.error!!, Modifier.padding(16.dp), warning = true)
                state.loading || snapshot == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> WellDesignContent(state, snapshot, formatter, viewModel::onEvent)
            }
        }
    }
}

@Composable
private fun WellDesignContent(
    state: WellDesignUiState,
    snapshot: WellDesignSnapshot,
    formatter: UnitFormatter,
    onEvent: (WellDesignEvent) -> Unit,
) {
    val ctx = remember(snapshot.well) { WellGeometryContext(snapshot.well) }
    val schematic = @Composable { modifier: Modifier ->
        WellSchematicCanvas(
            scene = snapshot.scene,
            formatter = formatter,
            selectedId = snapshot.selected?.id ?: state.selectedSurfaceBlock?.sourceId,
            onElementSelected = { onEvent(WellDesignEvent.SelectElement(it?.id)) },
            onSurfaceBlockSelected = { onEvent(WellDesignEvent.SelectSurfaceBlock(it)) },
            surfaceBandHeight = 96.dp,
            modifier = modifier,
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 720.dp) {
            // Tablet / landscape: sketch on the left, editable data panel on the right.
            Row(Modifier.fillMaxSize()) {
                schematic(Modifier.weight(1f).fillMaxHeight())
                LazyColumn(
                    modifier = Modifier.width(380.dp).fillMaxHeight().imePadding(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { wellDataItems(state, snapshot, ctx, formatter, onEvent) }
            }
        } else {
            // Phone: one scrolling page. The sketch scrolls up out of the way so the
            // parameters (and the focused field) are visible above the keyboard.
            val sketchHeight = maxHeight * 0.72f
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val sketchScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
            Box(Modifier.fillMaxSize().imePadding()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "sketch") { schematic(Modifier.fillMaxWidth().height(sketchHeight)) }
                    item(key = "scroll-hint") {
                        Text(
                            "Scroll up for well data. Edits update the sketch immediately.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    wellDataItems(state, snapshot, ctx, formatter, onEvent, horizontalPadding = 12.dp)
                }
                if (sketchScrolledAway) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.scrollToItem(0) } }, // instant, no animation
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                            Text("Sketch", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/** Editable well data, shared by the phone (single list) and tablet (side panel) layouts. */
private fun LazyListScope.wellDataItems(
    state: WellDesignUiState,
    snapshot: WellDesignSnapshot,
    ctx: WellGeometryContext,
    f: UnitFormatter,
    onEvent: (WellDesignEvent) -> Unit,
    horizontalPadding: Dp = 0.dp,
) {
    val pad = Modifier.padding(horizontal = horizontalPadding)
    val well = snapshot.well
    val ref = ctx.canonicalReference
    val completion = well.completion(snapshot.completionState)
    run {
        state.message?.let { msg ->
            item {
                Column(pad) {
                    NoticeCard("Edit not applied: $msg", warning = true)
                    TextButton(onClick = { onEvent(WellDesignEvent.DismissMessage) }) { Text("Dismiss") }
                }
            }
        }
        snapshot.selected?.let { element ->
            item {
                val obj = well.findObject(element.sourceId, completion)
                val details = obj?.let { wellObjectDetails(it, ctx, f) }
                DetailCard(
                    title = details?.title ?: element.label,
                    rows = details?.rows ?: listOf("Element" to element.kind.name),
                    provenance = details?.provenance,
                    onClose = { onEvent(WellDesignEvent.ClearSelection) },
                    footnote = BREAKDOWN_PENDING_NOTE,
                    modifier = pad,
                )
            }
        }
        state.selectedSurfaceBlock?.let { block ->
            item {
                DetailCard(
                    title = block.label,
                    rows = listOf(
                        "Height" to if (block.sizeIsSchematic) "Not provided (drawing placeholder)" else f.depth(Length.meters(block.height)),
                        "Base above GL" to f.depth(Length.meters(block.bottomAboveGl)),
                    ),
                    provenance = null,
                    onClose = { onEvent(WellDesignEvent.ClearSelection) },
                    footnote = BREAKDOWN_PENDING_NOTE,
                    modifier = pad,
                )
            }
        }
        item {
            Column(pad) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.project?.provenance?.let { ProvenanceBadge(it) }
                    Text("Completion state:", style = MaterialTheme.typography.labelMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    snapshot.availableStates.forEach { s ->
                        FilterChip(
                            selected = s == snapshot.completionState,
                            onClick = { onEvent(WellDesignEvent.SelectCompletionState(s)) },
                            label = { Text(s.label) },
                        )
                    }
                }
            }
        }
        if (state.project?.isSyntheticDemo == true) {
            item { NoticeCard("Synthetic demonstration data. Not real well, catalog or test data.", pad, warning = true) }
        }
        item {
            SectionCard("Datums (elevation above MSL)", pad) {
                DepthField("RKB elevation", well.datums.rkbElevation, f, { v ->
                    onEvent(WellDesignEvent.Edit { w -> WellEdits.setDatumElevations(w, v, w.datums.groundLevelElevation) })
                })
                DepthField("Ground level elevation", well.datums.groundLevelElevation, f, { v ->
                    onEvent(WellDesignEvent.Edit { w -> WellEdits.setDatumElevations(w, w.datums.rkbElevation, v) })
                })
                LabeledValue("RKB above GL", f.depth(well.datums.rkbAboveGroundLevel))
                LabeledValue("MD reference", ref.description)
            }
        }
        item {
            val td = snapshot.scene.totalDepthMd
            val survey = well.activeSurvey
            SectionCard("Trajectory", pad) {
                LabeledValue("Declared profile", well.declaredTrajectoryType.label)
                LabeledValue("Geometry source", ctx.trajectorySource.label)
                LabeledValue("Survey", survey?.let { "${it.name} (${it.kind.label})" } ?: "None")
                LabeledValue("Max inclination", "${UnitFormatter.number(ctx.trajectory.stations.maxOf { it.inclination.degrees }, 1)}°")
                LabeledValue("Total depth", "${f.depth(Length.meters(td))} MD ${ref.shortLabel}")
                LabeledValue("TVD at TD", "${f.depth(ctx.tvdBelowGroundLevel(Length.meters(td)))} below GL")
            }
        }
        item {
            SectionCard("Formation tops (TVD below GL)", pad) {
                well.formations.forEach { formation ->
                    DepthField(
                        label = "${formation.name} – ${formation.lithology.label}",
                        value = ctx.tvdBelowGroundLevel(formation.top),
                        formatter = f,
                        onValueChange = { v ->
                            onEvent(WellDesignEvent.Edit { w -> WellEdits.setFormationTop(w, formation.id, TrueVerticalDepth.gl(v)) })
                        },
                    )
                }
            }
        }
        item {
            SectionCard("Hole sections", pad) {
                well.holeSections.forEach { h ->
                    LabeledValue(h.name, "${f.depth(ctx.canonical(h.top))} – ${f.depth(ctx.canonical(h.bottom))}")
                }
            }
        }
        item {
            SectionCard("Casing strings & liners (MD ${ref.shortLabel})", pad) {
                well.casingStrings.forEach { s ->
                    Text(s.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    LabeledValue("OD / ID", "${f.diameter(s.spec.outerDiameter)} / ${f.diameter(s.spec.innerDiameter)}")
                    LabeledValue("Top", f.depth(ctx.canonical(s.top)))
                    DepthField("Shoe MD", ctx.canonical(s.shoe.depth), f, { v ->
                        onEvent(WellDesignEvent.Edit { w -> WellEdits.setCasingShoe(w, s.id, MeasuredDepth(v, ref)) })
                    })
                    s.cement?.let { c ->
                        DepthField("Top of cement MD", ctx.canonical(c.top), f, { v ->
                            onEvent(WellDesignEvent.Edit { w -> WellEdits.setTopOfCement(w, s.id, MeasuredDepth(v, ref)) })
                        })
                    }
                    HorizontalDivider()
                }
            }
        }
        item {
            SectionCard("Completion – ${snapshot.completionState.label}", pad) {
                when (val tubing = completion?.tubing) {
                    null -> Text("No completion recorded for this state.", style = MaterialTheme.typography.bodySmall)
                    is TubingConfiguration.ExplicitlyNone -> LabeledValue("Production tubing", "None – ${tubing.reason}")
                    is TubingConfiguration.Installed -> {
                        LabeledValue("Production tubing", tubing.tubing.name)
                        LabeledValue("OD / ID", "${f.diameter(tubing.tubing.spec.outerDiameter)} / ${f.diameter(tubing.tubing.spec.innerDiameter)}")
                        DepthField("End of tubing MD", ctx.canonical(tubing.tubing.endOfTubing), f, { v ->
                            onEvent(WellDesignEvent.Edit { w -> WellEdits.setEndOfTubing(w, snapshot.completionState, MeasuredDepth(v, ref)) })
                        })
                    }
                }
                completion?.components?.forEach { c ->
                    LabeledValue(c.name, f.depth(ctx.canonical(c.top)))
                }
                completion?.perforations?.forEach { p ->
                    LabeledValue("Perforations (${p.status.label})", "${f.depth(ctx.canonical(p.top))} – ${f.depth(ctx.canonical(p.bottom))}")
                }
            }
        }
        item {
            SectionCard("Geometry checks", pad) { IssueList(snapshot.scene.issues) }
        }
        item {
            NoticeCard(
                modifier = pad,
                text = "Live sketch: updates immediately from the entered data. The detailed final well illustration " +
                    "is a separate view (planned) generated only from complete data.",
            )
        }
    }
}
