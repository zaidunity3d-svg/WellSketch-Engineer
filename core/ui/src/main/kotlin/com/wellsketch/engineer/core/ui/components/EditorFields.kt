package com.wellsketch.engineer.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.catalog.TubularKind
import com.wellsketch.engineer.core.engineering.volumes.TubularGeometry
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.TubularSpec
import com.wellsketch.engineer.core.ui.format.UnitFormatter

/**
 * Drop-down selection field. A read-only text field shows the current choice; tapping it
 * opens the list. (Built from TextField + DropdownMenu so it does not depend on the
 * experimental exposed-dropdown API.)
 */
@Composable
fun <T> ChoiceField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "Select…",
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: placeholder,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Read-only text fields do not open menus themselves: a transparent layer takes the tap.
        Box(Modifier.matchParentSize().clickable(enabled = enabled && options.isNotEmpty()) { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * Text input committed on every change (blank text is flagged and not applied); re-synced
 * from the model when the field is not focused, like [DepthField].
 */
@Composable
fun TextEntryField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            if (newText.isNotBlank()) onValueChange(newText)
        },
        label = { Text(label) },
        isError = text.isBlank(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
    )
}

/**
 * Pipe selection from the tubular catalogue, as in the HTML prototype: outside diameter, then
 * nominal weight, then grade and connection. Wall, ID, drift and capacity come from the
 * selected record and are shown with its source; a pipe that is not in the catalogue keeps
 * its own dimensions until an OD is chosen.
 */
@Composable
fun TubularPicker(
    kind: TubularKind,
    spec: TubularSpec,
    formatter: UnitFormatter,
    onSpecChange: (TubularSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = TubularCatalog.match(spec, kind)
    val connections = TubularCatalog.connections(kind)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceField(
            label = "Outside diameter (OD)",
            options = TubularCatalog.outerDiameters(kind),
            selected = entry?.outerDiameterIn,
            optionLabel = { formatter.nominalSize(Length.inches(it)) },
            placeholder = "${formatter.diameter(spec.outerDiameter)} (not in catalogue)",
            onSelect = { od ->
                val weights = TubularCatalog.weights(kind, od)
                val pick = weights.firstOrNull { it.nominalWeightLbFt == entry?.nominalWeightLbFt } ?: weights.first()
                onSpecChange(pick.toSpec(spec.grade, spec.connection?.takeUnless { it == entry?.endFinish || it == entry?.toolJointConnection }))
            },
        )
        if (entry == null) {
            Text(
                "This pipe is not a catalogue record (OD ${formatter.diameter(spec.outerDiameter)}, ID ${formatter.diameter(spec.innerDiameter)}). " +
                    "Choose an OD to take wall, ID and drift from the catalogue.",
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }
        ChoiceField(
            label = "Nominal weight",
            options = TubularCatalog.weights(kind, entry.outerDiameterIn),
            selected = entry,
            optionLabel = { it.weightLabel },
            onSelect = { e -> onSpecChange(e.toSpec(spec.grade, spec.connection?.takeUnless { it == entry.endFinish || it == entry.toolJointConnection })) },
        )
        ChoiceField(
            label = "Steel grade",
            options = TubularCatalog.grades(kind),
            selected = spec.grade,
            optionLabel = { it },
            placeholder = "Not selected",
            onSelect = { g -> onSpecChange(spec.copy(grade = g)) },
        )
        ChoiceField(
            label = "Connection",
            options = connections,
            selected = spec.connection,
            optionLabel = { it },
            placeholder = "Not selected",
            onSelect = { c -> onSpecChange(spec.copy(connection = c)) },
        )
        LabeledValue("Wall", formatter.diameter(entry.wallThickness))
        LabeledValue("ID", formatter.diameter(entry.innerDiameter))
        LabeledValue("Drift", entry.drift?.let(formatter::diameter) ?: "Not listed")
        LabeledValue("Capacity", formatter.capacity(TubularGeometry.insideCapacity(entry.innerDiameter)))
        if (entry.toolJointOdIn != null) {
            LabeledValue(
                "Tool joint (reference)",
                "${entry.toolJointConnection} – OD ${formatter.diameter(Length.inches(entry.toolJointOdIn))}, ID ${formatter.diameter(Length.inches(entry.toolJointIdIn ?: 0.0))}",
            )
        }
        Text(
            "Source: ${entry.sourceUrl} — public API ${if (kind == TubularKind.DRILL_PIPE) "5DP" else "5CT"} reference; data sheet / OEM verification required. " +
                "Grade and connection ratings are not part of this record.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
