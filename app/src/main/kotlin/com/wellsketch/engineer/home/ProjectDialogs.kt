package com.wellsketch.engineer.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.WellLocationType
import com.wellsketch.engineer.core.ui.components.ChoiceField
import com.wellsketch.engineer.core.ui.format.UnitFormatter

/**
 * New-well form (HTML prototype "Well info" + datums). Only what is needed to start: the
 * user then adds formations, hole sections, casing and tubing in Well Design.
 */
@Composable
internal fun NewWellDialog(
    formatter: UnitFormatter,
    onDismiss: () -> Unit,
    onCreate: (name: String, rkbAboveGround: Length, groundElevation: Length, location: WellLocationType) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf(WellLocationType.ONSHORE) }
    var rkbText by remember { mutableStateOf("") }
    var glText by remember { mutableStateOf("") }

    val rkb = formatter.parseDepth(rkbText)
    val gl = formatter.parseDepth(glText)
    val nameError = name.isBlank()
    val rkbError = rkb == null || rkb < Length.ZERO
    val glError = gl == null
    val unit = formatter.depthUnit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New well") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Well name") }, singleLine = true, isError = nameError,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceField("Location", WellLocationType.entries, location, { it.label }, { location = it })
                OutlinedTextField(
                    value = rkbText, onValueChange = { rkbText = it },
                    label = { Text("RKB above ground level ($unit)") }, singleLine = true, isError = rkbText.isNotEmpty() && rkbError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = glText, onValueChange = { glText = it },
                    label = { Text("Ground level elevation above MSL ($unit)") }, singleLine = true, isError = glText.isNotEmpty() && glError,
                    supportingText = { Text("Negative if below sea level. Offshore: enter the mudline / seabed elevation.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "The well starts empty: add formations, hole sections, casing / liners and tubing in Well Design. " +
                        "Nothing is filled in with demo data.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !nameError && !rkbError && !glError,
                onClick = { if (rkb != null && gl != null) onCreate(name.trim(), rkb, gl, location) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun DeleteProjectDialog(projectName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete project?") },
        text = { Text("\"$projectName\" will be deleted from this device. This cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
