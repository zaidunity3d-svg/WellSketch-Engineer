package com.wellsketch.engineer.feature.wellcalc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wellsketch.engineer.core.ui.components.LabeledValue
import com.wellsketch.engineer.core.ui.components.ModuleTopBar
import com.wellsketch.engineer.core.ui.components.NoticeCard
import com.wellsketch.engineer.core.ui.components.SectionCard
import com.wellsketch.engineer.domain.wellcalc.WellCalculationCatalog

/**
 * Well Calculations module (placeholder). Lists the planned calculations and their
 * specification status. No formula is executed until its specification is approved.
 */
@Composable
fun WellCalculationsScreen(projectName: String?, onBack: () -> Unit) {
    val groups = WellCalculationCatalog.specifications.groupBy { it.category }
    Scaffold(topBar = { ModuleTopBar("Well Calculations", projectName, onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                NoticeCard(
                    "Calculations are not implemented yet. Each one needs an approved specification " +
                        "(formula, references, units, validity range and test cases) before it is enabled.",
                    warning = true,
                )
            }
            groups.forEach { (category, specs) ->
                item { Text(category.label, style = MaterialTheme.typography.titleMedium) }
                items(specs, key = { it.id }) { spec ->
                    SectionCard(spec.title) {
                        Text(spec.purpose, style = MaterialTheme.typography.bodySmall)
                        LabeledValue("Status", spec.status.label)
                        LabeledValue("Planned inputs", spec.plannedInputs.joinToString())
                        LabeledValue("Planned outputs", spec.plannedOutputs.joinToString())
                    }
                }
            }
        }
    }
}
