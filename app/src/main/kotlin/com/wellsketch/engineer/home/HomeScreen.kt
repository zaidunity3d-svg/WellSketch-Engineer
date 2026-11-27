@file:OptIn(ExperimentalLayoutApi::class)

package com.wellsketch.engineer.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsketch.engineer.core.model.project.WellProjectSummary
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.ui.components.NoticeCard
import com.wellsketch.engineer.core.ui.format.UnitFormatter

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    unitSystem: UnitSystem,
    onUnitSystemChange: (UnitSystem) -> Unit,
    onOpenWellDesign: (String) -> Unit,
    onOpenWellCalculations: (String) -> Unit,
    onOpenCtCalculations: (String) -> Unit,
    onOpenEquipmentLibrary: () -> Unit,
    onOpenWorkover: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val active = state.activeProject
    val formatter = remember(unitSystem) { UnitFormatter(unitSystem) }
    var showNewWell by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<WellProjectSummary?>(null) }
    if (showNewWell) {
        NewWellDialog(
            formatter = formatter,
            onDismiss = { showNewWell = false },
            onCreate = { name, rkb, gl, location ->
                showNewWell = false
                viewModel.createWell(name, rkb, gl, location, onCreated = onOpenWellDesign)
            },
        )
    }
    confirmDelete?.let { project ->
        DeleteProjectDialog(
            projectName = project.name,
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                viewModel.delete(project.id)
            },
        )
    }
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Header()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ProjectSelector(
                    state,
                    onSelect = viewModel::selectProject,
                    onNewWell = { showNewWell = true },
                    onDuplicate = { active?.let { viewModel.duplicate(it.id) } },
                    onDelete = { confirmDelete = active },
                )
                Text("Engineering modules", style = MaterialTheme.typography.titleMedium)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth >= 840.dp) 3 else if (maxWidth >= 560.dp) 2 else 1
                    val cardWidth = (maxWidth - 12.dp * (columns - 1)) / columns
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModuleCard(
                            "Well Design", "Editable well schema, half-cut casing sketch, stratigraphy and trajectories.",
                            Icons.Filled.Build, enabled = active != null, modifier = Modifier.width(cardWidth),
                        ) { active?.let { onOpenWellDesign(it.id) } }
                        ModuleCard(
                            "Well Calculations", "Capacities, displacements, hydrostatics and cementing (specifications pending review).",
                            Icons.AutoMirrored.Filled.List, enabled = active != null, modifier = Modifier.width(cardWidth),
                        ) { active?.let { onOpenWellCalculations(it.id) } }
                        ModuleCard(
                            "CT Calculations", "Completed-well CT setup, Christmas Tree and CT stack, CT/BHA placement and restrictions.",
                            Icons.Filled.Settings, enabled = active != null, modifier = Modifier.width(cardWidth),
                        ) { active?.let { onOpenCtCalculations(it.id) } }
                        ModuleCard(
                            "Workover", "Workover rig and BOP on a completed well; drill pipe or tubing work string, tools, " +
                                "perforation, cement plug, packer, ESP and acid jobs.",
                            Icons.Filled.Refresh, enabled = active != null, modifier = Modifier.width(cardWidth),
                        ) { active?.let { onOpenWorkover(it.id) } }
                    }
                }
                ModuleCard(
                    "Equipment Library", "Tubulars, completion tools, tree and CT pressure-control components with data provenance.",
                    Icons.Filled.Star, enabled = true, modifier = Modifier.fillMaxWidth(), onClick = onOpenEquipmentLibrary,
                )
                Text("Display units", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnitSystem.entries.forEach { system ->
                        FilterChip(
                            selected = system == unitSystem,
                            onClick = { onUnitSystemChange(system) },
                            label = { Text(system.label) },
                        )
                    }
                }
                NoticeCard(
                    "Offline app. Data is stored only on this device. Demo projects are synthetic and labelled; " +
                        "engineering calculations are enabled only after their specifications are reviewed.",
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            Text(
                "WellSketch Engineer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Drilling · Well design · Completion · Coiled tubing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun ProjectSelector(
    state: HomeUiState,
    onSelect: (String) -> Unit,
    onNewWell: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Active well project", style = MaterialTheme.typography.labelMedium)
            val active = state.activeProject
            when {
                state.loading -> Text("Loading projects…")
                active == null -> Text("No projects yet. Create a new well to start.")
                else -> {
                    Text(active.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Well ${active.wellName}" + if (active.isSyntheticDemo) " · synthetic demo" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onNewWell) { Text("+ New well") }
                OutlinedButton(onClick = onDuplicate, enabled = active != null) { Text("Duplicate") }
                TextButton(onClick = onDelete, enabled = active != null) { Text("Delete") }
            }
            Box {
                OutlinedButton(onClick = { expanded = true }, enabled = state.projects.isNotEmpty()) {
                    Text("Change project")
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.projects.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                onSelect(p.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
