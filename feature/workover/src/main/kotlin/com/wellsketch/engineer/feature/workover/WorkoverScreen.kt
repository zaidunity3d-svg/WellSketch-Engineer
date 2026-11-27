@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wellsketch.engineer.feature.workover

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsketch.engineer.core.engineering.catalog.TubularKind
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.surface.DrillingBopComponentType
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.UnitDefinitions
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.model.workover.WorkStringPipeKind
import com.wellsketch.engineer.core.model.workover.WorkoverOperation
import com.wellsketch.engineer.core.model.workover.WorkoverOperationType
import com.wellsketch.engineer.core.model.workover.WorkoverToolType
import com.wellsketch.engineer.core.ui.components.ChoiceField
import com.wellsketch.engineer.core.ui.components.DepthField
import com.wellsketch.engineer.core.ui.components.IssueList
import com.wellsketch.engineer.core.ui.components.LabeledValue
import com.wellsketch.engineer.core.ui.components.ModuleTopBar
import com.wellsketch.engineer.core.ui.components.NoticeCard
import com.wellsketch.engineer.core.ui.components.NumberField
import com.wellsketch.engineer.core.ui.components.SectionCard
import com.wellsketch.engineer.core.ui.components.TextEntryField
import com.wellsketch.engineer.core.ui.components.TubularPicker
import com.wellsketch.engineer.core.ui.format.UnitFormatter
import com.wellsketch.engineer.core.ui.schematic.SurfaceDetailCanvas
import com.wellsketch.engineer.core.ui.schematic.WellSchematicCanvas
import com.wellsketch.engineer.domain.workover.WorkoverAnalysis
import com.wellsketch.engineer.domain.workover.WorkoverCalculator
import com.wellsketch.engineer.domain.workover.WorkoverEdits
import kotlinx.coroutines.launch

@Composable
fun WorkoverScreen(viewModel: WorkoverViewModel, unitSystem: UnitSystem, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = remember(unitSystem) { UnitFormatter(unitSystem) }
    Scaffold(
        topBar = {
            ModuleTopBar(
                title = state.job?.name ?: "Workover",
                subtitle = state.project?.let { "${it.well.name} – ${it.name}" } ?: "Workover",
                onBack = onBack,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val project = state.project
            when {
                state.error != null -> NoticeCard(state.error!!, Modifier.padding(16.dp), warning = true)
                state.loading || project == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> WorkoverContent(state, project, formatter, viewModel::onEvent, viewModel::newId)
            }
        }
    }
}

@Composable
private fun WorkoverContent(
    state: WorkoverUiState,
    project: WellProject,
    f: UnitFormatter,
    onEvent: (WorkoverEvent) -> Unit,
    newId: (String) -> String,
) {
    val result = state.result
    var surfaceDetail by rememberSaveable { mutableStateOf(false) }
    val schematic = @Composable { modifier: Modifier ->
        Column(modifier) {
            if (result == null) {
                NoticeCard("Create a workover job to see the workover rig, BOP and work string on the well.", Modifier.padding(12.dp))
                return@Column
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = surfaceDetail,
                    onClick = { surfaceDetail = !surfaceDetail },
                    label = { Text("Surface detail", style = MaterialTheme.typography.labelMedium) },
                )
            }
            if (surfaceDetail && result.scene.hasSurface) {
                SurfaceDetailCanvas(
                    scene = result.scene,
                    selectedId = state.selectedBlock?.sourceId,
                    lengthInFeet = f.depthUnit == "ft",
                    onSurfaceBlockSelected = { onEvent(WorkoverEvent.SelectBlock(it)) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                WellSchematicCanvas(
                    scene = result.scene,
                    formatter = f,
                    selectedId = state.selectedElement?.id ?: state.selectedBlock?.sourceId,
                    onElementSelected = { onEvent(WorkoverEvent.SelectElement(it)) },
                    onSurfaceBlockSelected = { onEvent(WorkoverEvent.SelectBlock(it)) },
                    surfaceBandHeight = 150.dp,
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
                    modifier = Modifier.width(400.dp).fillMaxHeight().imePadding(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { workoverItems(state, project, f, onEvent, newId) }
            }
        } else {
            val sketchHeight = maxHeight * 0.7f
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
                    workoverItems(state, project, f, onEvent, newId, horizontalPadding = 12.dp)
                }
                if (sketchScrolledAway) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.scrollToItem(0) } },
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

/** Number input where 0 means "not entered". */
@Composable
private fun OptionalNumberField(label: String, value: Double?, unit: String, decimals: Int, onChange: (Double?) -> Unit) {
    NumberField(
        "$label (0 = not entered)", value ?: 0.0, unit, decimals,
        onValueChange = { v -> onChange(v.takeIf { it > 0.0 }) },
        isValid = { it >= 0.0 },
    )
}

private fun LazyListScope.workoverItems(
    state: WorkoverUiState,
    project: WellProject,
    f: UnitFormatter,
    onEvent: (WorkoverEvent) -> Unit,
    newId: (String) -> String,
    horizontalPadding: Dp = 0.dp,
) {
    val pad = Modifier.padding(horizontal = horizontalPadding)
    val job = state.job
    val result = state.result
    fun edit(t: (WorkoverOperation) -> WorkoverOperation) = onEvent(WorkoverEvent.Edit { _, op -> t(op) })

    // --- Job ---------------------------------------------------------------------------------
    item(key = "job") {
        SectionCard("Workover job", pad) {
            if (job == null) {
                Text(
                    "No workover job for this well yet. A job puts the workover BOP and rig floor on the wellhead (tree removed) " +
                        "and runs your work string – drill pipe or tubing – into the production casing / liner.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                ChoiceField("Job", project.workoverOperations, job, { it.name }, { onEvent(WorkoverEvent.SelectJob(it.id)) })
                ChoiceField("Operation", WorkoverOperationType.entries, job.type, { it.label }, { t -> edit { it.copy(type = t) } })
                TextEntryField("Job name", job.name, { n -> edit { it.copy(name = n) } })
            }
            ChoiceField(
                "+ New job", WorkoverOperationType.entries, null, { it.label },
                { onEvent(WorkoverEvent.NewJob(it)) }, placeholder = "Choose an operation to add a job",
            )
            if (job != null) TextButton(onClick = { onEvent(WorkoverEvent.DeleteJob) }) { Text("Delete this job") }
        }
    }
    if (job == null || result == null) return
    val a = result.analysis
    val ctx = WellGeometryContext(result.workoverWell)
    val wellRef = ctx.canonicalReference.shortLabel
    val datum = job.depthReference as? DepthReference.Operation
    val datumLabel = datum?.name ?: job.depthReference.shortLabel

    state.selectedElement?.let { e ->
        item(key = "selected") {
            SectionCard(e.label, pad) {
                LabeledValue("Top", "${f.depth(Length(e.mdTop))} MD $wellRef")
                LabeledValue("Bottom", "${f.depth(Length(e.mdBottom))} MD $wellRef")
                LabeledValue("Outside diameter", f.diameter(Length(2 * e.outerRadius)))
                if (e.innerRadius > 0.0) LabeledValue("Inside diameter", f.diameter(Length(2 * e.innerRadius)))
                TextButton(onClick = { onEvent(WorkoverEvent.ClearSelection) }) { Text("Close") }
            }
        }
    }

    // --- Rig and BOP -----------------------------------------------------------------------------
    item(key = "rig") {
        SectionCard("Workover rig & BOP (tree removed)", pad) {
            if (datum != null) {
                NumberField(
                    "Workover RKB above GL", f.depthValue(datum.heightAboveGroundLevel), f.depthUnit, 2,
                    onValueChange = { v -> edit { WorkoverEdits.setRkbHeight(it, f.depthFromValue(v)) } },
                )
                Text(
                    "Workover depths are measured from this RKB, not the drilling RKB (${f.depth(result.workoverWell.datums.rkbAboveGroundLevel)} above GL). " +
                        "Changing it keeps the string where it is.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            NumberField(
                "Rig floor below RKB", f.depthValue(job.rigUp.rig.floorBelowRkb ?: Length.ZERO), f.depthUnit, 2,
                onValueChange = { v -> edit { WorkoverEdits.setRigFloorBelowRkb(it, f.depthFromValue(v)) } },
                isValid = { it >= 0.0 },
            )
            HorizontalDivider()
            Text("BOP stack, top to bottom", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (job.rigUp.bopStack.components.isEmpty()) {
                Text("No BOP component entered. Add them from the data sheet (bore and height).", style = MaterialTheme.typography.bodySmall)
            }
            job.rigUp.bopStack.components.forEach { c ->
                ChoiceField("Type", DrillingBopComponentType.entries, c.type, { it.label }, { t ->
                    edit { op -> WorkoverEdits.updateBopComponent(op, c.id) { it.copy(type = t, name = if (it.name == it.type.label) t.label else it.name) } }
                })
                NumberField(
                    "Bore", f.diameterValue(c.bore), f.diameterUnit, 3,
                    onValueChange = { v -> edit { op -> WorkoverEdits.updateBopComponent(op, c.id) { it.copy(bore = f.diameterFromValue(v)) } } },
                    isValid = { it > 0.0 },
                )
                NumberField(
                    "Height", f.depthValue(c.height ?: Length.ZERO), f.depthUnit, 2,
                    onValueChange = { v -> edit { op -> WorkoverEdits.updateBopComponent(op, c.id) { it.copy(height = f.depthFromValue(v)) } } },
                    isValid = { it > 0.0 },
                )
                TextButton(onClick = { edit { WorkoverEdits.removeBopComponent(it, c.id) } }) { Text("Remove ${c.name}") }
                HorizontalDivider()
            }
            ChoiceField(
                "+ Add BOP component (bottom)", DrillingBopComponentType.entries, null, { it.label },
                { t -> onEvent(WorkoverEvent.Edit { well, op -> WorkoverEdits.addBopComponent(op, well, t, newId("bop")) }) },
                placeholder = "Annular, ram preventer, spool…",
            )
        }
    }

    // --- Work string -------------------------------------------------------------------------------
    item(key = "string") {
        SectionCard("Work string (top to bottom)", pad) {
            job.workString.sections.forEachIndexed { i, s ->
                val placed = a.items.firstOrNull { it.id == s.id }
                Text("Section ${i + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                ChoiceField("Pipe", WorkStringPipeKind.entries, s.kind, { it.label }, { k -> edit { WorkoverEdits.setSectionKind(it, s.id, k) } })
                TubularPicker(
                    if (s.kind == WorkStringPipeKind.DRILL_PIPE) TubularKind.DRILL_PIPE else TubularKind.TUBING,
                    s.spec, f, { spec -> edit { WorkoverEdits.setSectionSpec(it, s.id, spec) } },
                )
                if (i == 0) {
                    LabeledValue("Length (to the rig floor)", placed?.let { f.depth(it.length) } ?: "–")
                } else {
                    DepthField("Length", s.length, f, { v -> edit { WorkoverEdits.setSectionLength(it, s.id, v) } })
                }
                NumberField(
                    "Tool joint / coupling OD (0 = pipe OD)", s.connectionOuterDiameter?.let(f::diameterValue) ?: 0.0, f.diameterUnit, 3,
                    onValueChange = { v -> edit { WorkoverEdits.setSectionConnectionOd(it, s.id, f.diameterFromValue(v)) } },
                    isValid = { it >= 0.0 },
                )
                placed?.let { LabeledValue("In the well", "${f.depth(it.top)} – ${f.depth(it.bottom)} MD $wellRef") }
                if (job.workString.sections.size > 1) {
                    TextButton(onClick = { edit { WorkoverEdits.removeSection(it, s.id) } }) { Text("Remove section ${i + 1}") }
                }
                HorizontalDivider()
            }
            ChoiceField(
                "+ Add section below", WorkStringPipeKind.entries, null, { it.label },
                { k -> edit { WorkoverEdits.addSection(it, k, newId("sec")) } }, placeholder = "Drill pipe or tubing (tapered string)",
            )
            Text("The top section reaches the rig floor; its length follows from the bottom depth.", style = MaterialTheme.typography.bodySmall)
        }
    }

    // --- Tools -------------------------------------------------------------------------------------
    item(key = "tools") {
        SectionCard("Tools below the string (top to bottom)", pad) {
            if (job.tools.isEmpty()) Text("Open-ended string: no tool.", style = MaterialTheme.typography.bodySmall)
            job.tools.forEachIndexed { i, t ->
                ChoiceField("Tool", WorkoverToolType.entries, t.type, { it.label }, { ty ->
                    edit { op -> WorkoverEdits.updateTool(op, t.id) { it.copy(type = ty, name = if (it.name == it.type.label) ty.label else it.name) } }
                })
                TextEntryField("Name", t.name, { n -> edit { op -> WorkoverEdits.updateTool(op, t.id) { it.copy(name = n) } } })
                DepthField("Length", t.length, f, { v -> edit { op -> WorkoverEdits.updateTool(op, t.id) { it.copy(length = v) } } })
                NumberField(
                    "Max OD", f.diameterValue(t.maxOuterDiameter), f.diameterUnit, 3,
                    onValueChange = { v -> edit { op -> WorkoverEdits.updateTool(op, t.id) { it.copy(maxOuterDiameter = f.diameterFromValue(v)) } } },
                    isValid = { it > 0.0 },
                )
                NumberField(
                    "Through bore ID (0 = closed)", t.innerDiameter?.let(f::diameterValue) ?: 0.0, f.diameterUnit, 3,
                    onValueChange = { v ->
                        edit { op -> WorkoverEdits.updateTool(op, t.id) { it.copy(innerDiameter = f.diameterFromValue(v).takeIf { d -> d.meters > 0.0 }) } }
                    },
                    isValid = { it >= 0.0 },
                )
                a.items.firstOrNull { it.id == t.id }?.let { LabeledValue("In the well", "${f.depth(it.top)} – ${f.depth(it.bottom)} MD $wellRef") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (i > 0) TextButton(onClick = { edit { WorkoverEdits.moveTool(it, t.id, up = true) } }) { Text("Move up") }
                    if (i < job.tools.lastIndex) TextButton(onClick = { edit { WorkoverEdits.moveTool(it, t.id, up = false) } }) { Text("Move down") }
                    TextButton(onClick = { edit { WorkoverEdits.removeTool(it, t.id) } }) { Text("Remove") }
                }
                HorizontalDivider()
            }
            ChoiceField(
                "+ Add tool (bottom)", WorkoverToolType.entries, null, { it.label },
                { ty -> edit { WorkoverEdits.addTool(it, ty, newId("tool")) } }, placeholder = "Packer, gun, ESP, stinger…",
            )
            Text("Enter tool lengths and diameters from the tool data sheet.", style = MaterialTheme.typography.bodySmall)
        }
    }

    // --- Depths ------------------------------------------------------------------------------------
    item(key = "depths") {
        SectionCard("Depths (from $datumLabel)", pad) {
            DepthField("Bottom of string / tools", job.bottomDepth, f, { v -> edit { it.copy(bottomDepth = v) } })
            LabeledValue("= well depth", "${f.depth(a.bottomMd)} MD $wellRef")
            val top = job.targetTop
            val bottom = job.targetBottom
            if (top == null || bottom == null) {
                TextButton(onClick = { edit { it.copy(targetTop = it.bottomDepth, targetBottom = it.bottomDepth + Length.meters(10.0)) } }) {
                    Text("+ Set target interval (perforations / treatment / plug)")
                }
            } else {
                DepthField("Target top", top, f, { v -> edit { it.copy(targetTop = v) } })
                DepthField("Target bottom", bottom, f, { v -> edit { it.copy(targetBottom = v) } })
                TextButton(onClick = { edit { it.copy(targetTop = null, targetBottom = null) } }) { Text("Clear target") }
            }
        }
    }

    // --- Fluids and pressures ------------------------------------------------------------------------
    item(key = "fluids") {
        SectionCard("Fluids, pressures and rate", pad) {
            OptionalNumberField("Well fluid density", job.wellFluidDensity?.let(f::densityValue), f.densityUnit, 3) { v ->
                edit { it.copy(wellFluidDensity = v?.let(f::densityFromValue)) }
            }
            OptionalNumberField("Pumped fluid density", job.pumpedFluidDensity?.let(f::densityValue), f.densityUnit, 3) { v ->
                edit { it.copy(pumpedFluidDensity = v?.let(f::densityFromValue)) }
            }
            OptionalNumberField("Pump rate", job.pumpRate?.let(f::flowRateValue), f.flowRateUnit, 2) { v ->
                edit { it.copy(pumpRate = v?.let(f::flowRateFromValue)) }
            }
            OptionalNumberField("Reservoir pressure at target", job.reservoirPressure?.let(f::pressureValue), f.pressureUnit, 0) { v ->
                edit { it.copy(reservoirPressure = v?.let(f::pressureFromValue)) }
            }
            OptionalNumberField("Kill overbalance", job.killOverbalance?.let(f::pressureValue), f.pressureUnit, 0) { v ->
                edit { it.copy(killOverbalance = v?.let(f::pressureFromValue)) }
            }
            OptionalNumberField("Fracture gradient", job.fractureGradient?.let(f::gradientValue), f.gradientUnit, 4) { v ->
                edit { it.copy(fractureGradient = v?.let(f::gradientFromValue)) }
            }
        }
    }

    // --- Operation inputs ------------------------------------------------------------------------------
    when (job.type) {
        WorkoverOperationType.PERFORATION -> item(key = "perf-in") {
            SectionCard("Perforation inputs", pad) {
                val perFoot = f.depthUnit == "ft"
                val factor = if (perFoot) UnitDefinitions.METERS_PER_FOOT else 1.0
                OptionalNumberField("Shot density", job.shotDensityPerMeter?.let { it * factor }, if (perFoot) "shots/ft" else "shots/m", 1) { v ->
                    edit { it.copy(shotDensityPerMeter = v?.let { d -> d / factor }) }
                }
                OptionalNumberField("Loaded gun length per run", job.gunLengthPerRun?.let(f::depthValue), f.depthUnit, 1) { v ->
                    edit { it.copy(gunLengthPerRun = v?.let(f::depthFromValue)) }
                }
            }
        }
        WorkoverOperationType.CEMENT_PLUG -> item(key = "plug-in") {
            SectionCard("Cement plug inputs (pipe end at plug bottom)", pad) {
                OptionalNumberField("Plug length", job.plugLength?.let(f::depthValue), f.depthUnit, 1) { v ->
                    edit { it.copy(plugLength = v?.let(f::depthFromValue)) }
                }
                NumberField(
                    "Slurry excess", job.slurryExcessFraction * 100.0, "%", 0,
                    onValueChange = { v -> edit { it.copy(slurryExcessFraction = v / 100.0) } }, isValid = { it in 0.0..300.0 },
                )
                OptionalNumberField("Spacer ahead", job.spacerAheadVolume?.let(f::volumeValue), f.volumeUnit, 2) { v ->
                    edit { it.copy(spacerAheadVolume = v?.let(f::volumeFromValue)) }
                }
                OptionalNumberField("Under-displacement", job.underDisplacementVolume?.let(f::volumeValue), f.volumeUnit, 2) { v ->
                    edit { it.copy(underDisplacementVolume = v?.let(f::volumeFromValue)) }
                }
            }
        }
        WorkoverOperationType.ACIDIZING -> item(key = "acid-in") {
            SectionCard("Treatment inputs", pad) {
                OptionalNumberField("Treatment volume per length", job.treatmentVolumePerLength?.let(f::capacityValue), f.capacityUnit, 4) { v ->
                    edit { it.copy(treatmentVolumePerLength = v?.let(f::capacityFromValue)) }
                }
                OptionalNumberField("Overflush", job.overflushVolume?.let(f::volumeValue), f.volumeUnit, 2) { v ->
                    edit { it.copy(overflushVolume = v?.let(f::volumeFromValue)) }
                }
            }
        }
        else -> Unit
    }

    // --- Results -------------------------------------------------------------------------------
    item(key = "results") { ResultsCard(a, f, pad) }
    operationResults(a, f, wellRef, pad)
    item(key = "clearance") {
        SectionCard("Clearance on the way down", pad) {
            a.clearances.forEach { c ->
                LabeledValue(c.itemName, "${f.diameter(c.outerDiameter)} in ${c.restrictionName} ${c.basis} ${f.diameter(c.restriction)}")
                LabeledValue("  clearance", f.diameter(c.clearance))
            }
            Text("Tool joint / coupling OD is used where entered; BOP bores, wellhead bores, casing drift (else ID) and open hole are checked.", style = MaterialTheme.typography.bodySmall)
        }
    }
    item(key = "issues") {
        SectionCard("Checks", pad) { IssueList(result.issues) }
    }
    item(key = "method") {
        NoticeCard(
            "Workover calculations ${WorkoverCalculator.VERSION}: nominal body dimensions, production tubing pulled, " +
                "hydrostatics with TVD below the workover RKB. Implemented, pending engineering validation.",
            pad,
        )
    }
}

@Composable
private fun ResultsCard(a: WorkoverAnalysis, f: UnitFormatter, modifier: Modifier) {
    SectionCard("Volumes, times and pressures", modifier) {
        LabeledValue("Inside work string", f.volume(a.stringVolume.total))
        LabeledValue("Annulus (casing top to bottom)", f.volume(a.annulus.total))
        a.belowString?.let { LabeledValue("Below the string to target bottom", f.volume(it.total)) }
        a.boreToTarget?.let { LabeledValue("Well bore to target top, no string", f.volume(it.total)) }
        LabeledValue("Fill when pulling dry (steel)", f.volume(a.openEndDisplacement))
        LabeledValue("Fill when pulling wet / closed end", f.volume(a.closedEndDisplacement))
        a.surfaceToBottomMinutes?.let { LabeledValue("Surface to bottom", f.minutes(it)) }
        a.bottomsUpMinutes?.let { LabeledValue("Bottoms up", f.minutes(it)) }
        a.circulationMinutes?.let { LabeledValue("Full circulation", f.minutes(it)) }
        HorizontalDivider()
        a.targetTvdBelowDatum?.let { LabeledValue("Target TVD below workover RKB", f.depth(it)) }
        a.hydrostaticAtTarget?.let { LabeledValue("Well fluid hydrostatic at target", f.pressure(it)) }
        a.balance?.let { b ->
            LabeledValue(if (b.pascals >= 0) "Overbalance" else "Underbalance", f.pressure(com.wellsketch.engineer.core.model.units.Pressure(kotlin.math.abs(b.pascals))))
        }
        a.killDensity?.let { LabeledValue("Kill fluid density", f.density(it)) }
    }
}

private fun LazyListScope.operationResults(a: WorkoverAnalysis, f: UnitFormatter, wellRef: String, pad: Modifier) {
    a.cementPlug?.let { p ->
        item(key = "plug") {
            SectionCard("Balanced cement plug", pad) {
                LabeledValue("Plug", "${f.depth(p.plugTop)} – ${f.depth(p.plugBottom)} MD $wellRef")
                LabeledValue("Slurry volume", f.volume(p.slurryVolume))
                LabeledValue("Top of slurry with pipe in", "${f.depth(p.topWithPipeIn)} MD $wellRef")
                LabeledValue("Spacer ahead / behind", "${f.volume(p.spacerAhead)} / ${f.volume(p.spacerBehind)}")
                LabeledValue("Displacement", f.volume(p.displacement))
            }
        }
    }
    a.acid?.let { r ->
        item(key = "acid") {
            SectionCard("Treatment", pad) {
                LabeledValue("Treatment volume", f.volume(r.treatmentVolume))
                LabeledValue("Displacement to target top", f.volume(r.displacementToTop))
                LabeledValue("Overflush", f.volume(r.overflush))
                r.maxSurfacePressure?.let { LabeledValue("Max surface pressure (no friction)", f.pressure(it)) }
            }
        }
    }
    a.perforation?.let { r ->
        item(key = "perf") {
            SectionCard("Perforation", pad) {
                LabeledValue("Interval length", f.depth(r.intervalLength))
                r.shots?.let { LabeledValue("Shots", it.toString()) }
                r.gunRuns?.let { LabeledValue("Gun runs", it.toString()) }
                val gunTop = r.gunTop
                val gunBottom = r.gunBottom
                if (gunTop != null && gunBottom != null) LabeledValue("Guns at", "${f.depth(gunTop)} – ${f.depth(gunBottom)} MD $wellRef")
            }
        }
    }
    a.packer?.let { r ->
        item(key = "packer") {
            SectionCard("Packer", pad) {
                LabeledValue("Packer", "${f.depth(r.packerTop)} – ${f.depth(r.packerBottom)} MD $wellRef")
                LabeledValue("Set in", r.hostName?.let { name -> r.hostInnerDiameter?.let { "$name (ID ${f.diameter(it)})" } ?: name } ?: "Open hole")
                LabeledValue("Packer fluid (annulus above)", f.volume(r.packerFluidVolume))
                r.volumeBelowPacker?.let { LabeledValue("Below packer to target top", f.volume(it)) }
            }
        }
    }
    a.esp?.let { r ->
        item(key = "esp") {
            SectionCard("ESP", pad) {
                r.intakeDepth?.let { LabeledValue("Intake", "${f.depth(it)} MD $wellRef") }
                r.intakeTvdBelowDatum?.let { LabeledValue("Intake TVD below workover RKB", f.depth(it)) }
                r.intakeInclination?.let { LabeledValue("Inclination at intake", "${UnitFormatter.number(it.degrees, 1)}°") }
                r.motorBottom?.let { LabeledValue("Motor bottom", "${f.depth(it)} MD $wellRef") }
                LabeledValue("Largest ESP OD", f.diameter(r.maxOuterDiameter))
            }
        }
    }
}

