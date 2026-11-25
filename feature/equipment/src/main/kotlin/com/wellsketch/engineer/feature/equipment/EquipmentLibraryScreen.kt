package com.wellsketch.engineer.feature.equipment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsketch.engineer.core.model.equipment.EquipmentItem
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.ui.components.LabeledValue
import com.wellsketch.engineer.core.ui.components.ModuleTopBar
import com.wellsketch.engineer.core.ui.components.NoticeCard
import com.wellsketch.engineer.core.ui.components.ProvenanceBadge
import com.wellsketch.engineer.core.ui.format.UnitFormatter

@Composable
fun EquipmentLibraryScreen(viewModel: EquipmentLibraryViewModel, unitSystem: UnitSystem, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val f = remember(unitSystem) { UnitFormatter(unitSystem) }
    Scaffold(topBar = { ModuleTopBar("Equipment Library", "Reference data with provenance", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                NoticeCard(
                    "Only synthetic placeholder entries are included. Real entries must be imported from validated " +
                        "API/manufacturer datasets with catalog provenance and units.",
                    warning = true,
                )
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("Search equipment") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.groups.forEach { group ->
                item(key = "header-${group.category.name}") {
                    Text(group.category.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                items(group.items, key = { it.id }) { item ->
                    EquipmentRow(item, f, onClick = { viewModel.select(item) })
                }
            }
        }
    }
    state.selected?.let { item -> EquipmentDetailDialog(item, viewModel, f) }
}

@Composable
private fun EquipmentRow(item: EquipmentItem, f: UnitFormatter, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                ProvenanceBadge(item.provenance)
            }
            val d = item.dimensions
            listOfNotNull(
                d.outerDiameter?.let { "OD ${f.diameter(it)}" },
                d.innerDiameter?.let { "ID ${f.diameter(it)}" },
                d.bore?.let { "Bore ${f.diameter(it)}" },
                d.ratedWorkingPressure?.let { "RWP ${f.pressure(it)}" },
            ).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EquipmentDetailDialog(item: EquipmentItem, viewModel: EquipmentLibraryViewModel, f: UnitFormatter) {
    val readiness = remember(item) { viewModel.library.readiness(item) }
    val d = item.dimensions
    AlertDialog(
        onDismissRequest = { viewModel.select(null) },
        confirmButton = { TextButton(onClick = { viewModel.select(null) }) { Text("Close") } },
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProvenanceBadge(item.provenance)
                LabeledValue("Category", item.category.label)
                LabeledValue("Manufacturer", item.manufacturer ?: "Not provided")
                LabeledValue("Part number", item.partNumber ?: "Not provided")
                d.outerDiameter?.let { LabeledValue("OD", f.diameter(it)) }
                d.innerDiameter?.let { LabeledValue("ID", f.diameter(it)) }
                d.driftDiameter?.let { LabeledValue("Drift", f.diameter(it)) }
                d.wallThickness?.let { LabeledValue("Wall", f.diameter(it)) }
                d.bore?.let { LabeledValue("Bore", f.diameter(it)) }
                d.ratedWorkingPressure?.let { LabeledValue("Rated working pressure", f.pressure(it)) }
                LabeledValue("Source", item.provenance.source)
                LabeledValue("Engineering grade", if (readiness.isEngineeringGrade) "Yes" else "No")
                readiness.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}
