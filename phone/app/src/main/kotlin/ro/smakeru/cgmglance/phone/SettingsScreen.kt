package ro.smakeru.cgmglance.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.RangeThresholds

/**
 * Lets the user set their target range and unit.
 *
 * Values are entered and stored in mg/dL regardless of the display unit. Editing in mmol/L would
 * mean rounding a decimal into an integer scale on every keystroke, and a range that shifted by
 * a point each time it was opened would be worse than one that reads slightly foreign.
 */
@Composable
fun SettingsScreen(
    initialThresholds: RangeThresholds,
    initialUnit: GlucoseUnit,
    onSave: (RangeThresholds, GlucoseUnit) -> Unit,
    onBack: () -> Unit,
) {
    var urgentLow by remember { mutableStateOf(initialThresholds.urgentLowMgdl.toString()) }
    var low by remember { mutableStateOf(initialThresholds.lowMgdl.toString()) }
    var high by remember { mutableStateOf(initialThresholds.highMgdl.toString()) }
    var unit by remember { mutableStateOf(initialUnit) }

    // The domain refuses an inconsistent set outright rather than reordering it, so the button
    // is simply disabled until the three numbers make sense together.
    val parsed =
        RangeThresholds.create(
            urgentLowMgdl = urgentLow.toIntOrNull() ?: -1,
            lowMgdl = low.toIntOrNull() ?: -1,
            highMgdl = high.toIntOrNull() ?: -1,
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text("Target range", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Used for colour and wording only. This app never alerts on these values — " +
                "alarms stay with the official app.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(20.dp))

        NumberField("Urgent low", urgentLow) { urgentLow = it }
        NumberField("Low", low) { low = it }
        NumberField("High", high) { high = it }

        Spacer(Modifier.height(4.dp))
        Text("All values in mg/dL.", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(24.dp))
        Text("Display unit", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlucoseUnit.entries.forEach { candidate ->
                FilterChip(
                    selected = unit == candidate,
                    onClick = { unit = candidate },
                    label = { Text(candidate.label) },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        if (parsed == null) {
            Text(
                "Urgent low must be below low, and low below high. All three must be values a " +
                    "sensor can report.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { parsed?.let { onSave(it, unit) } },
                enabled = parsed != null,
            ) { Text("Save") }

            TextButton(onClick = onBack) { Text("Back") }
        }

        Spacer(Modifier.height(20.dp))
        TextButton(
            onClick = {
                val defaults = RangeThresholds.default
                urgentLow = defaults.urgentLowMgdl.toString()
                low = defaults.lowMgdl.toString()
                high = defaults.highMgdl.toString()
            },
        ) { Text("Reset to 55 / 70 / 180") }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        // Digits only: a stray character would silently turn the field into "invalid" with no
        // hint as to which one, on a keyboard that should not have offered it in the first place.
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.take(3)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(200.dp).padding(vertical = 4.dp),
    )
}
