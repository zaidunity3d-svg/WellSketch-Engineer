package com.wellsketch.engineer.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.provenance.DataStatus
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.ui.format.UnitFormatter

/** Small colored badge showing where data came from. Synthetic data is always flagged. */
@Composable
fun ProvenanceBadge(provenance: DataProvenance, modifier: Modifier = Modifier) {
    val (background, foreground) = when (provenance.status) {
        DataStatus.SYNTHETIC_DEMO -> Color(0xFFFFE2B8) to Color(0xFF5A3800)
        DataStatus.USER_ENTERED -> Color(0xFFDCE5EE) to Color(0xFF14232F)
        DataStatus.CATALOG_VERIFIED, DataStatus.FIELD_MEASURED -> Color(0xFFD5F0DC) to Color(0xFF0E3B1C)
        DataStatus.UNVERIFIED -> Color(0xFFF6D5D5) to Color(0xFF4A0F0F)
    }
    Surface(color = background, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text = provenance.status.label.uppercase(),
            color = foreground,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** A titled card used for panels of editable well data. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** A label/value row. */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

/** Highlighted notice for placeholders and data-quality statements. */
@Composable
fun NoticeCard(text: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    Surface(
        color = if (warning) Color(0xFFFFF1D6) else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (warning) Icons.Filled.Warning else Icons.Filled.Info,
                contentDescription = null,
                tint = if (warning) Color(0xFF8A5A00) else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** List of validation issues, errors first. */
@Composable
fun IssueList(issues: List<ValidationIssue>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (issues.isEmpty()) {
            Text("No issues found.", style = MaterialTheme.typography.bodySmall)
        }
        issues.sortedBy { it.severity.ordinal }.forEach { issue ->
            val color = when (issue.severity) {
                ValidationIssue.Severity.ERROR -> Color(0xFFB3261E)
                ValidationIssue.Severity.WARNING -> Color(0xFF8A5A00)
                ValidationIssue.Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                "${issue.severity.label}: ${issue.message}",
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

/**
 * Numeric depth input in the selected unit system. The model value updates on every valid
 * keystroke (so the schematic redraws immediately); invalid text is flagged, not applied.
 */
@Composable
fun DepthField(
    label: String,
    value: Length,
    formatter: UnitFormatter,
    onValueChange: (Length) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(formatter.system) { mutableStateOf(formatter.depthInput(value)) }
    var invalid by remember { mutableStateOf(false) }
    LaunchedEffect(value, formatter.system) {
        val parsed = formatter.parseDepth(text)
        if (parsed == null || !parsed.isApproximately(value, Length.meters(0.005))) {
            text = formatter.depthInput(value)
            invalid = false
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            val parsed = formatter.parseDepth(newText)
            invalid = parsed == null
            if (parsed != null) onValueChange(parsed)
        },
        label = { Text(label) },
        suffix = { Text(formatter.depthUnit) },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}
