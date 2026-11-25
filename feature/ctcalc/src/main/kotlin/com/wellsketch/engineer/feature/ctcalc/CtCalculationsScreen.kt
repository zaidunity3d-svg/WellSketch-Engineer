@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wellsketch.engineer.feature.ctcalc

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.ct.CtOperation
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.model.well.findObject
import com.wellsketch.engineer.core.schematic.CtSurfaceViewMode
import com.wellsketch.engineer.core.ui.components.BREAKDOWN_PENDING_NOTE
import com.wellsketch.engineer.core.ui.components.DepthField
import com.wellsketch.engineer.core.ui.components.DetailCard
import com.wellsketch.engineer.core.ui.components.IssueList
import com.wellsketch.engineer.core.ui.components.LabeledValue
import com.wellsketch.engineer.core.ui.components.ModuleTopBar
import com.wellsketch.engineer.core.ui.components.NoticeCard
import com.wellsketch.engineer.core.ui.components.NumberField
import com.wellsketch.engineer.core.ui.components.SectionCard
import com.wellsketch.engineer.core.ui.components.surfaceObjectDetails
import com.wellsketch.engineer.core.ui.components.wellObjectDetails
import com.wellsketch.engineer.core.ui.format.UnitFormatter
import com.wellsketch.engineer.core.ui.schematic.SurfaceDetailCanvas
import com.wellsketch.engineer.core.ui.schematic.WellSchematicCanvas
import com.wellsketch.engineer.domain.ct.CtAnalysis
import com.wellsketch.engineer.domain.ct.CtCalculationCatalog
import kotlinx.coroutines.launch

@Composable
fun CtCalculationsScreen(viewModel: CtCalculationsViewModel, unitSystem: UnitSystem, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = remember(unitSystem) { UnitFormatter(unitSystem) }
    Scaffold(
        topBar = {
            ModuleTopBar(
                title = state.operation?.name ?: "CT Calculations",
                subtitle = state.project?.let { "${it.well.name} – ${it.name}" } ?: "CT Calculations",
                onBack = onBack,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val analysis = state.analysis
            val op = state.operation
            when {
                state.error != null -> NoticeCard(state.error!!, Modifier.padding(16.dp), warning = true)
                state.loading || analysis == null || op == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> CtContent(state, op, analysis, formatter, viewModel::onEvent)
            }
        }
    }
}

@Composable
private fun CtContent(state: CtUiState, op: CtOperation, analysis: CtAnalysis, f: UnitFormatter, onEvent: (CtEvent) -> Unit) {
    val project = state.project ?: return
    val ctx = remember(project.well) { WellGeometryContext(project.well) }
    var surfaceDetail by rememberSaveable { mutableStateOf(false) }
    val schematic = @Composable { modifier: Modifier ->
        Column(modifier) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CtSurfaceViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == state.viewMode,
                        onClick = { onEvent(CtEvent.SetViewMode(mode)) },
                        label = { Text(if (mode == CtSurfaceViewMode.FULL) "Full spread" else "Compact", style = MaterialTheme.typography.labelMedium) },
                    )
                }
                FilterChip(
                    selected = surfaceDetail,
                    onClick = { surfaceDetail = !surfaceDetail },
                    label = { Text("Surface detail", style = MaterialTheme.typography.labelMedium) },
                )
            }
            if (surfaceDetail && analysis.scene.hasSurface) {
                SurfaceDetailCanvas(
                    scene = analysis.scene,
                    selectedId = state.selectedBlock?.sourceId,
                    lengthInFeet = f.depthUnit == "ft",
                    onSurfaceBlockSelected = { onEvent(CtEvent.SelectBlock(it)) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                WellSchematicCanvas(
                    scene = analysis.scene,
                    formatter = f,
                    selectedId = state.selectedElement?.id ?: state.selectedBlock?.sourceId,
                    onElementSelected = { onEvent(CtEvent.SelectElement(it)) },
                    onSurfaceBlockSelected = { onEvent(CtEvent.SelectBlock(it)) },
                    surfaceBandHeight = if (state.viewMode == CtSurfaceViewMode.FULL) 170.dp else 140.dp,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxSize()) {
                schematic(Modifier.weight(1f).fillMaxHeight())
                LazyColumn(
                    modifier = Modifier.width(380.dp).fillMaxHeight().imePadding(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { ctItems(state, project, op, analysis, ctx, f, onEvent) }
            }
        } else {
            // Phone: one scrolling page; the sketch scrolls up so fields stay above the keyboard.
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
                            "Scroll up for CT depth and data. The CT and BHA move instantly.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    ctItems(state, project, op, analysis, ctx, f, onEvent, horizontalPadding = 12.dp)
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

private fun LazyListScope.ctItems(
    state: CtUiState,
    project: WellProject,
    op: CtOperation,
    analysis: CtAnalysis,
    ctx: WellGeometryContext,
    f: UnitFormatter,
    onEvent: (CtEvent) -> Unit,
    horizontalPadding: Dp = 0.dp,
) {
    val pad = Modifier.padding(horizontal = horizontalPadding)
    val well = project.well
    val canonical = ctx.canonicalReference.shortLabel
    val placement = analysis.placement
    run {
        state.selectedElement?.let { element ->
            item {
                val ctString = op.ctString.takeIf { it.id == element.sourceId }
                val bha = op.bha.components.firstOrNull { it.id == element.sourceId }
                val details = when {
                    ctString != null -> Triple(
                        ctString.name,
                        ctString.sections.mapIndexed { i, s ->
                            "Section ${i + 1}" to "${f.depth(s.length)}, OD ${f.diameter(s.outerDiameter)}, wall ${f.diameter(s.wallThickness)}"
                        },
                        ctString.provenance,
                    )
                    bha != null -> Triple(
                        bha.name,
                        listOf("Type" to bha.type.label, "Length" to f.depth(bha.length), "Max OD" to f.diameter(bha.maxOuterDiameter)),
                        bha.provenance,
                    )
                    else -> well.findObject(element.sourceId, well.completion(analysis.completionState))
                        ?.let { wellObjectDetails(it, ctx, f) }
                        ?.let { Triple(it.title, it.rows, it.provenance) }
                }
                DetailCard(
                    title = details?.first ?: element.label,
                    rows = details?.second.orEmpty(),
                    provenance = details?.third,
                    onClose = { onEvent(CtEvent.ClearSelection) },
                    footnote = BREAKDOWN_PENDING_NOTE,
                    modifier = pad,
                )
            }
        }
        state.selectedBlock?.let { block ->
            item {
                val details = surfaceObjectDetails(block, well, op.rigUp.pressureControlStack, analysis.surfaceStackup, f)
                DetailCard(
                    title = details.title,
                    rows = details.rows,
                    provenance = details.provenance,
                    onClose = { onEvent(CtEvent.ClearSelection) },
                    footnote = BREAKDOWN_PENDING_NOTE,
                    modifier = pad,
                )
            }
        }
        if (project.isSyntheticDemo) {
            item { NoticeCard("Synthetic demonstration data. Not real well, CT string or equipment data.", pad, warning = true) }
        }
        item {
            SectionCard("CT depth", pad) {
                Text("Depth reference for this operation", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DepthReferenceChoice.entries.forEach { choice ->
                        val selected = when (op.depthReference) {
                            DepthReference.Rkb -> choice == DepthReferenceChoice.RKB
                            DepthReference.GroundLevel -> choice == DepthReferenceChoice.GL
                            is DepthReference.Operation -> choice == DepthReferenceChoice.OPERATION
                            DepthReference.MeanSeaLevel -> false
                        }
                        FilterChip(selected = selected, onClick = { onEvent(CtEvent.SetReference(choice)) }, label = { Text(choice.label) })
                    }
                }
                (op.depthReference as? DepthReference.Operation)?.let { datum ->
                    DepthField("'${datum.name}' height above GL", datum.heightAboveGroundLevel, f, { v ->
                        onEvent(CtEvent.SetOperationDatumHeight(v))
                    })
                }
                DepthField("BHA nose depth (MD ${op.depthReference.shortLabel})", op.currentDepth, f, { v ->
                    onEvent(CtEvent.SetDepth(v))
                })
                val maxDepth = analysis.flowPath.totalDepth
                    ?.let { ctx.converter.convert(MeasuredDepth(it, ctx.canonicalReference), op.depthReference).value }
                    ?: Length.meters(1.0)
                val maxDisplay = f.depthValue(maxDepth).toFloat().coerceAtLeast(1f)
                Slider(
                    value = f.depthValue(op.currentDepth).toFloat().coerceIn(0f, maxDisplay),
                    onValueChange = { onEvent(CtEvent.SetDepth(f.depthFromValue(it.toDouble()))) },
                    valueRange = 0f..maxDisplay,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-100.0, -10.0, 10.0, 100.0).forEach { step ->
                        OutlinedButton(onClick = {
                            val next = f.depthFromValue(f.depthValue(op.currentDepth) + step)
                            onEvent(CtEvent.SetDepth(if (next < Length.ZERO) Length.ZERO else next))
                        }) {
                            Text((if (step > 0) "+" else "−") + UnitFormatter.number(kotlin.math.abs(step), 0))
                        }
                    }
                }
                Text("Positions update instantly; no animation is used.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("Surface view", pad) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CtSurfaceViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == state.viewMode,
                            onClick = { onEvent(CtEvent.SetViewMode(mode)) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Text(
                    "CT mode shows the Christmas Tree and CT pressure-control stack; no drilling rig. " +
                        "Dashed outlines mark drawing-placeholder sizes.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            SectionCard("Placement", pad) {
                LabeledValue("Nose MD (${op.depthReference.shortLabel})", f.depth(op.currentDepth))
                LabeledValue("Nose MD ($canonical)", f.depth(placement.noseMd))
                LabeledValue("Nose MD below GL", f.depth(placement.noseMdBelowGl))
                LabeledValue("Nose TVD below GL", f.depth(placement.noseTvdBelowGl))
                LabeledValue("Inclination at nose", "${UnitFormatter.number(placement.noseInclinationDeg, 1)}°")
                LabeledValue("BHA top MD ($canonical)", f.depth(placement.bhaTopMd))
                LabeledValue("CT below datum", f.depth(placement.ctLengthBelowDatum))
                LabeledValue("CT string length", f.depth(op.ctString.totalLength))
                LabeledValue("Completion used", analysis.completionState.label)
            }
        }
        item {
            SectionCard("Flow path (MD $canonical)", pad) {
                analysis.flowPath.segments.forEach { s ->
                    LabeledValue(
                        "${s.conduitType.label}: ${f.depth(s.topMd)} – ${f.depth(s.bottomMd)}",
                        "ID ${f.diameter(s.innerDiameter)}",
                    )
                }
            }
        }
        item {
            SectionCard("Restrictions above the nose", pad) {
                val smallest = analysis.passability.smallestRestriction
                if (smallest?.innerDiameter != null) {
                    LabeledValue("Smallest restriction", smallest.label)
                    LabeledValue("Diameter (${smallest.basis.label})", f.diameter(smallest.innerDiameter!!))
                    LabeledValue("Depth", "${f.depth(smallest.topMd)} MD $canonical")
                } else {
                    Text("No restriction with known diameter above the nose.", style = MaterialTheme.typography.bodySmall)
                }
                op.bha.maxOuterDiameter?.let { LabeledValue("BHA max OD", f.diameter(it)) }
                LabeledValue("CT OD", f.diameter(op.ctString.maxOuterDiameter))
                analysis.passability.bhaDiametralClearance?.let {
                    LabeledValue("Restriction ID − BHA OD", f.diameter(it))
                }
                Text(
                    "Geometric comparison only; no clearance criterion is applied until specified.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            SectionCard("Pressure-control stack (top to bottom)", pad) {
                op.rigUp.pressureControlStack.components.forEach { c ->
                    Text(c.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    LabeledValue("Bore / rating", "${f.diameter(c.bore)} / ${f.pressure(c.ratedWorkingPressure)}")
                }
                well.christmasTree?.let { tree ->
                    Text("On ${tree.name}", style = MaterialTheme.typography.labelMedium)
                }
                op.maxExpectedWellheadPressure?.let { LabeledValue("Max expected WHP (entered)", f.pressure(it)) }
            }
        }
        item {
            val s = analysis.surfaceStackup
            fun elevation(v: Length?) = when {
                v == null -> "Not calculated"
                v < Length.ZERO -> "${f.depth(-v)} below GL"
                else -> "${f.depth(v)} above GL"
            }
            SectionCard("Surface stack-up (from entered heights)", pad) {
                LabeledValue("Cellar floor", elevation(s.cellarFloor))
                LabeledValue("Wellhead top flange", elevation(s.wellheadTop))
                LabeledValue("Tree top (cap removed)", elevation(s.treeTopWithoutCap))
                LabeledValue("Injector base (CT stack top)", elevation(s.ctStackTop))
                (op.depthReference as? DepthReference.Operation)?.let { datum ->
                    LabeledValue("'${datum.name}' height", elevation(datum.heightAboveGroundLevel))
                }
            }
        }
        item {
            SectionCard("CT BHA (top to nose)", pad) {
                op.bha.components.forEach { c ->
                    LabeledValue(c.name, "${f.depth(c.length)}, OD ${f.diameter(c.maxOuterDiameter)}")
                }
                LabeledValue("Total BHA length", f.depth(op.bha.totalLength))
            }
        }
        item { SectionCard("Checks", pad) { IssueList(analysis.issues) } }
        item {
            val v = analysis.volumes
            SectionCard("CT volumes and pumping (v${com.wellsketch.engineer.domain.ct.CtVolumeCalculator.VERSION}, pending validation)", pad) {
                NumberField(
                    "Pump rate", op.pumpRate?.let(f::flowRateValue) ?: 0.0, f.flowRateUnit, decimals = 2,
                    onValueChange = { onEvent(CtEvent.SetPumpRate(f.flowRateFromValue(it))) }, isValid = { it >= 0.0 },
                )
                v.sections.forEachIndexed { i, s ->
                    LabeledValue(
                        "CT section ${i + 1} ID (OD − 2 × wall)",
                        "${f.diameter(s.section.nominalInnerDiameter)} · ${f.capacity(s.internalCapacity)}",
                    )
                }
                LabeledValue("CT internal volume (whole string)", f.volume(v.ctInternalVolumeTotal))
                LabeledValue("CT in well (conduit top to BHA)", f.depth(v.ctLengthInWell))
                LabeledValue("CT internal volume in well", f.volume(v.ctInternalVolumeInWell))
                LabeledValue("CT steel displacement in well", f.volume(v.ctSteelDisplacementInWell))
                LabeledValue("BHA displacement (max OD, upper bound)", f.volume(v.bhaDisplacementUpperBound))
                LabeledValue("Annulus volume to nose", f.volume(v.annulus.total))
                v.timeToNozzleMinutes?.let { LabeledValue("Time to pump CT volume (fluid at nozzle)", f.minutes(it)) }
                v.bottomsUpMinutes?.let { LabeledValue("Bottoms-up time (annulus)", f.minutes(it)) }
                v.slowestAnnularVelocity?.let { LabeledValue("Slowest annular velocity", "${f.velocity(it.metersPerMinute)} – ${it.segment.description}") }
                v.fastestAnnularVelocity?.let { LabeledValue("Fastest annular velocity", "${f.velocity(it.metersPerMinute)} – ${it.segment.description}") }
                v.annulus.segments.forEach { s ->
                    Text(
                        "${f.depth(s.topMd)} – ${f.depth(s.bottomMd)} · ${s.description} · ${f.capacity(s.capacity)} → ${f.volume(s.volume)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "Surface lines, tree/stack bore and the CT on surface between reel and datum are not included. " +
                        "Velocities assume uniform flow in the annulus.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            SectionCard("Not yet implemented (needs a reviewed model)", pad) {
                CtCalculationCatalog.planned.filter { it.id == "ct-forces" }.forEach { c -> LabeledValue(c.title, "Not implemented") }
            }
        }
    }
}
