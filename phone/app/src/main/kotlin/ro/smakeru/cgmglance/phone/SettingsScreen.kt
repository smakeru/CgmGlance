package ro.smakeru.cgmglance.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    initialHealthExport: HealthExportSettings,
    initialSource: GlucoseSource,
    onSave: (RangeThresholds, GlucoseUnit, HealthExportSettings, GlucoseSource) -> Unit,
    onBack: () -> Unit,
) {
    var urgentLow by remember { mutableStateOf(initialThresholds.urgentLowMgdl.toString()) }
    var low by remember { mutableStateOf(initialThresholds.lowMgdl.toString()) }
    var high by remember { mutableStateOf(initialThresholds.highMgdl.toString()) }
    var unit by remember { mutableStateOf(initialUnit) }
    var source by remember { mutableStateOf(initialSource) }
    var healthEnabled by remember { mutableStateOf(initialHealthExport.enabled) }
    var healthInterval by remember { mutableStateOf(initialHealthExport.intervalMinutes.toString()) }

    // The domain refuses an inconsistent set outright rather than reordering it, so the button
    // is simply disabled until the three numbers make sense together.
    val parsed =
        RangeThresholds.create(
            urgentLowMgdl = urgentLow.toIntOrNull() ?: -1,
            lowMgdl = low.toIntOrNull() ?: -1,
            highMgdl = high.toIntOrNull() ?: -1,
        )

    // Validated the same way, and for the same reason: Save stays disabled rather than quietly
    // storing an interval this build will not honour. Only checked while the export is on —
    // a stale number in a disabled field must not block saving the target range.
    val parsedHealth =
        HealthExportSettings.create(
            enabled = healthEnabled,
            intervalMinutes = healthInterval.toIntOrNull() ?: -1,
        )
    val healthValid = !healthEnabled || parsedHealth != null

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.settings_subtitle), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))

        Text(stringResource(R.string.settings_source_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.settings_source_note), style = MaterialTheme.typography.bodySmall)
        GlucoseSource.entries.forEach { candidate ->
            FilterChip(
                selected = source == candidate,
                onClick = { source = candidate },
                label = { Text(candidate.label) },
            )
            Text(
                stringResource(R.string.settings_source_data_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (source == GlucoseSource.DEXCOM_ONE_PLUS) {
            Text(stringResource(R.string.settings_dexcom_note), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))

        NumberField(stringResource(R.string.field_urgent_low), urgentLow) { urgentLow = it }
        NumberField(stringResource(R.string.field_low), low) { low = it }
        NumberField(stringResource(R.string.field_high), high) { high = it }

        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.settings_units_note), style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.settings_display_unit), style = MaterialTheme.typography.titleMedium)
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
        Text(stringResource(R.string.settings_health_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.settings_health_subtitle), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = healthEnabled, onCheckedChange = { healthEnabled = it })
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.settings_health_enabled))
        }

        // Hidden rather than disabled when the export is off: a greyed field inviting a number
        // that will not be used is just a question the user has to work out the answer to.
        if (healthEnabled) {
            Spacer(Modifier.height(8.dp))
            NumberField(stringResource(R.string.settings_health_interval), healthInterval) {
                healthInterval = it
            }
            Text(
                stringResource(
                    R.string.settings_health_interval_note,
                    HealthExportSettings.MIN_INTERVAL_MINUTES,
                    HealthExportSettings.MAX_INTERVAL_MINUTES,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(28.dp))
        if (!healthValid) {
            Text(
                stringResource(
                    R.string.settings_health_invalid,
                    HealthExportSettings.MIN_INTERVAL_MINUTES,
                    HealthExportSettings.MAX_INTERVAL_MINUTES,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (parsed == null) {
            Text(
                stringResource(R.string.settings_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    parsed?.let { thresholds ->
                        // With the export off the interval field is not shown, so whatever is
                        // in it may be stale. Fall back to the stored value rather than writing
                        // a number the user cannot currently see.
                        val health =
                            parsedHealth
                                ?: HealthExportSettings.create(
                                    enabled = healthEnabled,
                                    intervalMinutes = initialHealthExport.intervalMinutes,
                                )
                                ?: HealthExportSettings.default
                        onSave(thresholds, unit, health, source)
                    }
                },
                enabled = parsed != null && healthValid,
            ) { Text(stringResource(R.string.action_save)) }

            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }

        Spacer(Modifier.height(20.dp))
        TextButton(
            onClick = {
                val defaults = RangeThresholds.default
                urgentLow = defaults.urgentLowMgdl.toString()
                low = defaults.lowMgdl.toString()
                high = defaults.highMgdl.toString()
            },
        ) { Text(stringResource(R.string.action_reset)) }
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
