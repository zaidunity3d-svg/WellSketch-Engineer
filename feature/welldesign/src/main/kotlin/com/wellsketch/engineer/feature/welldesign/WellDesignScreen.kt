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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.UnitSystem
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
import com.wellsketch.engineer.core.ui.components.surfaceObjectDetails
import com.wellsketch.engineer.core.ui.components.wellObjectDetails
import com.wellsketch.engineer.core.engineering.summary.KeyDepths
import com.wellsketch.engineer.core.ui.format.KeyDepthText
import com.wellsketch.engineer.core.ui.format.UnitFormatter
import com.wellsketch.engineer.core.ui.schematic.SurfaceDetailCanvas
import com.wellsketch.engineer.core.ui.schematic.WellSchematicCanvas
import com.wellsketch.engineer.domain.welldesign.SurfaceMode
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
    var surfaceDetail by rememberSaveable { mutableStateOf(false) }
    val schematic = @Composable { modifier: Modifier ->
        Column(modifier) {
            SurfaceModeBar(snapshot, surfaceDetail, { surfaceDetail = it }, onEvent)
            if (surfaceDetail && snapshot.scene.hasSurface) {
                SurfaceDetailCanvas(
                    scene = snapshot.scene,
                    selectedId = state.selectedSurfaceBlock?.sourceId,
                    lengthInFeet = formatter.depthUnit == "ft",
                    onSurfaceBlockSelected = { onEvent(WellDesignEvent.SelectSurfaceBlock(it)) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                WellSchematicCanvas(
                    scene = snapshot.scene,
                    formatter = formatter,
                    selectedId = snapshot.selected?.id ?: state.selectedSurfaceBlock?.sourceId,
                    onElementSelected = { onEvent(WellDesignEvent.SelectElement(it?.id)) },
                    onSurfaceBlockSelected = { onEvent(WellDesignEvent.SelectSurfaceBlock(it)) },
                    surfaceBandHeight = 112.dp,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
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
                val ctStack = state.project?.ctOperations?.firstOrNull()?.rigUp?.pressureControlStack
                val details = surfaceObjectDetails(block, well, ctStack, snapshot.surfaceStackup, f)
                DetailCard(
                    title = details.title,
                    rows = details.rows,
                    provenance = details.provenance,
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
                    Text(
                        if (snapshot.showsCompletion) "Completion state:" else "Drilling state: casing and open hole only",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (snapshot.showsCompletion) {
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
        }
        if (state.project?.isSyntheticDemo == true) {
            item { NoticeCard("Synthetic demonstration data. Not real well, catalog or test data.", pad, warning = true) }
        }
        item {
            // Read-only summary; MD and TVD share the canonical datum so they are never mixed.
            val keyDepths = remember(well, snapshot.completionState, snapshot.showsCompletion) {
                KeyDepths.build(well, snapshot.completionState.takeIf { snapshot.showsCompletion }, ctx)
            }
            if (keyDepths.isNotEmpty()) {
                SectionCard("Key depths (MD and TVD from ${ref.shortLabel})", pad) {
                    keyDepths.forEach { row -> LabeledValue(KeyDepthText.label(row, f), KeyDepthText.value(row, f)) }
                }
            }
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
        surfaceItems(snapshot, f, onEvent, pad)
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
        // Editable structure: formations, hole sections, casing strings / liners, tubing.
        wellStructureEditorItems(well, snapshot.completionState, ctx, f, onEvent, pad, snapshot.showsCompletion)
        if (snapshot.showsCompletion) item {
            SectionCard("Completion equipment – ${snapshot.completionState.label}", pad) {
                if (completion == null || (completion.components.isEmpty() && completion.perforations.isEmpty())) {
                    Text("No completion equipment recorded for this state.", style = MaterialTheme.typography.bodySmall)
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

/** Surface configuration chips (drilling / completion / CT) and the surface-detail toggle. */
@Composable
private fun SurfaceModeBar(
    snapshot: WellDesignSnapshot,
    surfaceDetail: Boolean,
    onSurfaceDetail: (Boolean) -> Unit,
    onEvent: (WellDesignEvent) -> Unit,
) {
    if (snapshot.availableSurfaceModes.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        snapshot.availableSurfaceModes.forEach { mode ->
            FilterChip(
                selected = mode == snapshot.surfaceMode,
                onClick = { onEvent(WellDesignEvent.SelectSurfaceMode(mode)) },
                label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
            )
        }
        FilterChip(
            selected = surfaceDetail,
            onClick = { onSurfaceDetail(!surfaceDetail) },
            label = { Text("Surface detail", style = MaterialTheme.typography.labelMedium) },
        )
    }
}

/** Surface stack-up elevations (calculated from entered data) and editable cellar/wellhead data. */
private fun LazyListScope.surfaceItems(
    snapshot: WellDesignSnapshot,
    f: UnitFormatter,
    onEvent: (WellDesignEvent) -> Unit,
    pad: Modifier,
) {
    val well = snapshot.well
    val stackup = snapshot.surfaceStackup ?: return
    fun elevation(v: Length?) = when {
        v == null -> "Not calculated"
        v < Length.ZERO -> "${f.depth(-v)} below GL"
        else -> "${f.depth(v)} above GL"
    }
    item {
        SectionCard("Surface – ${snapshot.surfaceMode?.label ?: "not defined"}", pad) {
            well.cellar?.let { cellar ->
                DepthField("Cellar depth below GL", cellar.depthBelowGroundLevel, f, { v ->
                    onEvent(WellDesignEvent.Edit { w -> WellEdits.setCellarDepth(w, v) })
                })
            }
            well.wellhead?.let { wh ->
                wh.baseRelativeToGroundLevel?.let { base ->
                    DepthField("Wellhead base relative to GL (− below)", base, f, { v ->
                        onEvent(WellDesignEvent.Edit { w -> WellEdits.setWellheadBase(w, v) })
                    })
                }
                wh.sections.forEach { section ->
                    section.height?.let { h ->
                        DepthField("${section.name} height", h, f, { v ->
                            onEvent(WellDesignEvent.Edit { w -> WellEdits.setWellheadSectionHeight(w, section.id, v) })
                        })
                    }
                }
            }
            LabeledValue("Wellhead position", stackup.wellheadBasis.label)
            LabeledValue("Wellhead base", elevation(stackup.wellheadBase))
            LabeledValue("Wellhead top flange", elevation(stackup.wellheadTop))
            LabeledValue("RKB to wellhead top", stackup.rkbToWellheadTop?.let(f::depth) ?: "Not calculated")
            when (snapshot.surfaceMode) {
                SurfaceMode.DRILLING -> {
                    val rigUp = well.drillingRigUp
                    val sections = well.wellhead?.sections.orEmpty()
                    if (rigUp != null && sections.size > 1) {
                        // Which section the BOP sits on decides which sections are installed yet.
                        Text("BOP nippled up on", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = rigUp.nippledUpOnSectionId == null,
                                onClick = { onEvent(WellDesignEvent.Edit { w -> WellEdits.setBopNippleUpSection(w, null) }) },
                                label = { Text("Auto") },
                            )
                            sections.forEach { s ->
                                FilterChip(
                                    selected = rigUp.nippledUpOnSectionId == s.id,
                                    onClick = { onEvent(WellDesignEvent.Edit { w -> WellEdits.setBopNippleUpSection(w, s.id) }) },
                                    label = { Text(s.name) },
                                )
                            }
                        }
                    }
                    stackup.installedWellhead?.let { iw ->
                        iw.top?.let { LabeledValue("BOP sits on", "${it.name} – ${iw.basis.label}") }
                        if (iw.notInstalled.isNotEmpty()) LabeledValue("Not installed yet", iw.notInstalled.joinToString { it.name })
                    }
                    LabeledValue("Drilling stack top", elevation(stackup.drillingStackTop))
                    LabeledValue("Drill floor", elevation(stackup.drillFloor))
                    LabeledValue("Clearance under rotary beams", stackup.clearanceUnderRotaryBeams?.let(f::depth) ?: "Not calculated")
                }
                SurfaceMode.COMPLETION -> {
                    LabeledValue("Tree top (with cap)", elevation(stackup.treeTopWithCap))
                }
                SurfaceMode.COILED_TUBING -> {
                    LabeledValue("Tree top (cap removed)", elevation(stackup.treeTopWithoutCap))
                    LabeledValue("Injector base (CT stack top)", elevation(stackup.ctStackTop))
                }
                null -> Unit
            }
            Text(
                "Elevations are calculated only from entered heights; drawing placeholders are never used.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
