package ro.smakeru.cgmglance.phone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.sync.SyncContract

/**
 * The only screen. Answers one question: is this working, and if not, what do I do?
 *
 * Its existence is not cosmetic. Without an entry point the app cannot be opened at all, which
 * means a revoked permission cannot be restored from inside it, and — as found on a real device
 * — an app that has been force stopped can never be started again, because Android will not
 * revive a stopped app from a background trigger.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val app = application as CgmGlanceApplication

        setContent {
            MaterialTheme {
                // Two screens and no back stack worth preserving, so a boolean beats pulling in
                // a navigation library and its graph.
                var showSettings by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showSettings) {
                        SettingsScreen(
                            initialThresholds = app.preferences.loadThresholds(),
                            initialUnit = app.preferences.loadUnit(),
                            initialHealthExport = app.preferences.loadHealthExportSettings(),
                            initialSource = app.preferences.loadSource(),
                            onSave = { thresholds, unit, healthExport, glucoseSource ->
                                app.updatePreferences(thresholds, unit, healthExport, glucoseSource)
                                showSettings = false
                            },
                            onBack = { showSettings = false },
                        )
                    } else {
                        StatusScreen(
                            source = app.statusSource,
                            onGrantPermission = { openNotificationAccessSettings() },
                            onOpenPlayStore = { openPlayStoreListing() },
                            onOpenSettings = { showSettings = true },
                            onSelectSource = { selected ->
                                app.updatePreferences(
                                    thresholds = app.preferences.loadThresholds(),
                                    unit = app.preferences.loadUnit(),
                                    healthExport = app.preferences.loadHealthExportSettings(),
                                    source = selected,
                                )
                            },
                            onRefreshHealth = { app.healthExporter.refresh() },
                        )
                    }
                }
            }
        }
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openPlayStoreListing() {
        val marketIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                setPackage("com.android.vending")
            }
        try {
            startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
                ),
            )
        }
    }

    /**
     * Asked for once, and nothing depends on the answer.
     *
     * A refusal costs only the warning that readings have stopped; glucose still reaches the
     * watch either way, so there is nothing here worth nagging about — hence the plain platform
     * call rather than the result-contract machinery, whose result we would discard anyway.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
    }
}

@Composable
private fun StatusScreen(
    source: PhoneStatusSource,
    onGrantPermission: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectSource: (GlucoseSource) -> Unit,
    onRefreshHealth: () -> Unit,
) {
    // Starts at UNREACHABLE rather than NO_COMPATIBLE_APP: before the first lookup returns,
    // nothing is known, and the weaker claim is the one that is not yet wrong.
    var watchLink by remember { mutableStateOf(WatchLink.UNREACHABLE) }
    var status by remember { mutableStateOf(source.snapshot(WatchLink.UNREACHABLE)) }
    val context = LocalContext.current

    // Polled rather than observed. Nothing here changes faster than the eye, the screen is only
    // alive while the user is looking at it, and a push pipeline for four fields would be more
    // machinery than the problem deserves.
    LaunchedEffect(Unit) {
        while (true) {
            // Asked twice on purpose. FILTER_REACHABLE answers "can I talk to it now";
            // FILTER_ALL answers "does a compatible watch app exist at all, anywhere". Only
            // together do they separate a watch that is merely away from one that was never
            // going to work — a node count reports both as the same thing, and sends someone
            // with a missing or mismatched watch app off to wait for a reconnection that will
            // never come.
            val capabilities = Wearable.getCapabilityClient(context)
            capabilities
                .getCapability(SyncContract.CAPABILITY_WATCH, CapabilityClient.FILTER_ALL)
                .addOnSuccessListener { all ->
                    capabilities
                        .getCapability(SyncContract.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                        .addOnSuccessListener { reachable ->
                            watchLink =
                                when {
                                    reachable.nodes.isNotEmpty() -> WatchLink.CONNECTED
                                    all.nodes.isNotEmpty() -> WatchLink.UNREACHABLE
                                    else -> WatchLink.NO_COMPATIBLE_APP
                                }
                        }
                        // A lookup that failed is not evidence about the watch app, so it
                        // reports the weaker of the two claims rather than accusing the user
                        // of a bad install.
                        .addOnFailureListener { watchLink = WatchLink.UNREACHABLE }
                }.addOnFailureListener { watchLink = WatchLink.UNREACHABLE }
            // Health Connect permission can be revoked from outside this app with no callback,
            // so it is re-read on the same tick rather than trusted from startup.
            onRefreshHealth()
            status = source.snapshot(watchLink)
            delay(REFRESH_INTERVAL_MILLIS)
        }
    }

    Scaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                // Read from BuildConfig rather than typed here, so it cannot drift from the
                // `versionName` the APK was actually built with.
                Text(
                    text = stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))

            HealthCard(status, onGrantPermission, onOpenPlayStore)

            if (status.needsAttention) {
                Spacer(Modifier.height(12.dp))
                AttentionCard()
            }

            Spacer(Modifier.height(24.dp))
            Text(text = stringResource(R.string.details_heading), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            DetailRow(stringResource(R.string.detail_source), status.source.label)
            DetailRow(
                stringResource(R.string.detail_permission),
                stringResource(if (status.permissionGranted) R.string.value_granted else R.string.value_not_granted),
            )
            status.parseFailure?.let { reason ->
                DetailRow(stringResource(R.string.detail_parse_failure), reason)
            }
            DetailRow(stringResource(R.string.detail_last_reading), describeReading(status))
            DetailRow(stringResource(R.string.detail_sent), describeLastSent(status))
            DetailRow(
                stringResource(R.string.detail_watch),
                stringResource(
                    when (status.watchLink) {
                        WatchLink.CONNECTED -> R.string.value_connected
                        WatchLink.UNREACHABLE -> R.string.value_not_reachable
                        WatchLink.NO_COMPATIBLE_APP -> R.string.value_watch_no_app
                    },
                ),
            )

            Spacer(Modifier.height(24.dp))
            HealthConnectCard(status.healthExport)

            Spacer(Modifier.height(16.dp))
            QuickSettingsCard(
                selectedSource = status.source,
                onSelectSource = { selected ->
                    onSelectSource(selected)
                    status = source.snapshot(watchLink)
                },
                onOpenSettings = onOpenSettings,
            )

            Spacer(Modifier.height(16.dp))
            SetupGuideCard(onOpenPlayStore = onOpenPlayStore)

            Spacer(Modifier.height(16.dp))
            Text(text = stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuickSettingsCard(
    selectedSource: GlucoseSource,
    onSelectSource: (GlucoseSource) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = COLOR_QUICK_SETTINGS_BG),
        border = BorderStroke(1.5.dp, COLOR_BRAND_TEAL),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.quick_settings_sensor_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = COLOR_QUICK_SETTINGS_TEXT,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlucoseSource.entries.forEach { candidate ->
                    val selected = candidate == selectedSource
                    Surface(
                        onClick = { onSelectSource(candidate) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) COLOR_BRAND_TEAL else Color.White,
                        border = if (selected) null else BorderStroke(1.dp, COLOR_BRAND_TEAL),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = candidate.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else COLOR_BRAND_TEAL,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (selectedSource == GlucoseSource.DEXCOM_ONE_PLUS) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_dexcom_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = COLOR_QUICK_SETTINGS_TEXT,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = COLOR_BRAND_TEAL),
            ) {
                Text(
                    text = stringResource(R.string.action_open_full_settings),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.quick_settings_details),
                style = MaterialTheme.typography.bodySmall,
                color = COLOR_QUICK_SETTINGS_TEXT,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HealthCard(
    status: PhoneStatus,
    onGrantPermission: () -> Unit,
    onOpenPlayStore: () -> Unit,
) {
    val (titleRes, bodyRes) = textsFor(status.health)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorFor(status.health)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge, color = Color.Black)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium, color = Color.Black)

            if (status.health == PhoneHealth.NO_PERMISSION) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onGrantPermission) {
                    Text(stringResource(R.string.action_open_notification_access))
                }
            } else if (status.health == PhoneHealth.WATCH_APP_MISSING) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenPlayStore) {
                    Text(stringResource(R.string.action_open_play_store))
                }
            }
        }
    }
}

@Composable
private fun AttentionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = COLOR_ATTENTION),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.fallback_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.fallback_body),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black,
            )
        }
    }
}

/**
 * How the Health Connect export is doing, and the one button that fixes the common case.
 *
 * Kept visually separate from the main health card above. That card answers "is glucose reaching
 * the watch"; this one is about a secondary export, and letting it colour the main verdict would
 * report a perfectly working app as broken because a log entry did not land.
 */
@Composable
private fun HealthConnectCard(export: HealthExportStatus) {
    // Health Connect owns its own permission UI; this contract launches it and hands back the
    // result. The result is discarded because the next status tick re-reads the real grant,
    // which is also what catches a permission revoked from outside the app.
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = PermissionController.createRequestPermissionResultContract(),
            onResult = { },
        )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorForExport(export.state)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.health_export_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                exportBodyText(export),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black,
            )

            if (export.state == HealthExportState.WORKING && export.lastWriteMillis != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.health_export_last_write, agoText(export.lastWriteMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                )
            }

            // Shown verbatim. These failures are device and provider specific, and the user is
            // the one who has to repeat them when a value does not appear in Samsung Health.
            if (export.state == HealthExportState.FAILING && export.lastErrorMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    export.lastErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                )
            }

            if (export.state == HealthExportState.NEEDS_SETUP) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(setOf(HealthConnectExporter.WRITE_GLUCOSE)) }) {
                    Text(stringResource(R.string.action_grant_health_permission))
                }
            }
        }
    }
}

@Composable
private fun agoText(millis: Long): String {
    val minutes = ((System.currentTimeMillis() - millis) / 60_000L).toInt()
    return if (minutes <= 0) {
        stringResource(R.string.age_just_now)
    } else {
        pluralStringResource(R.plurals.age_minutes_ago, minutes, minutes)
    }
}

/**
 * The one line explaining the current state.
 *
 * The two states that describe the cadence quote the interval the user actually chose, rather
 * than a fixed fifteen: a screen that insists on 15 while settings say 30 is a bug report
 * waiting to happen.
 */
@Composable
private fun exportBodyText(export: HealthExportStatus): String =
    when (export.state) {
        HealthExportState.DISABLED -> stringResource(R.string.health_export_disabled)
        HealthExportState.UNAVAILABLE -> stringResource(R.string.health_export_unavailable)
        HealthExportState.PROVIDER_UPDATE_REQUIRED -> stringResource(R.string.health_export_update_required)
        HealthExportState.NEEDS_SETUP -> stringResource(R.string.health_export_needs_setup)
        HealthExportState.FAILING -> stringResource(R.string.health_export_failing)
        HealthExportState.WAITING -> stringResource(R.string.health_export_waiting, export.intervalMinutes)
        HealthExportState.WORKING -> stringResource(R.string.health_export_working, export.intervalMinutes)
    }

private fun colorForExport(state: HealthExportState): Color =
    when (state) {
        HealthExportState.WORKING -> COLOR_OK

        HealthExportState.FAILING -> COLOR_PROBLEM

        // Grey, not amber: switched off on purpose is not a warning.
        HealthExportState.DISABLED -> COLOR_NEUTRAL

        HealthExportState.UNAVAILABLE,
        HealthExportState.PROVIDER_UPDATE_REQUIRED,
        HealthExportState.NEEDS_SETUP,
        HealthExportState.WAITING,
        -> COLOR_ATTENTION
    }

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun textsFor(health: PhoneHealth): Pair<Int, Int> =
    when (health) {
        PhoneHealth.OK -> R.string.health_ok_title to R.string.health_ok_body
        PhoneHealth.NO_PERMISSION -> R.string.health_no_permission_title to R.string.health_no_permission_body
        PhoneHealth.WAITING_FOR_FIRST_READING -> R.string.health_waiting_title to R.string.health_waiting_body
        PhoneHealth.NOT_RECEIVING -> R.string.health_not_receiving_title to R.string.health_not_receiving_body
        PhoneHealth.WATCH_UNREACHABLE -> R.string.health_watch_title to R.string.health_watch_body
        PhoneHealth.WATCH_APP_MISSING -> R.string.health_watch_missing_title to R.string.health_watch_missing_body
    }

internal fun colorFor(health: PhoneHealth): Color =
    when (health) {
        PhoneHealth.OK -> COLOR_OK

        PhoneHealth.WAITING_FOR_FIRST_READING, PhoneHealth.WATCH_UNREACHABLE -> COLOR_ATTENTION

        // Red, unlike plain unreachability: this one does not resolve by waiting.
        PhoneHealth.WATCH_APP_MISSING -> COLOR_PROBLEM

        PhoneHealth.NO_PERMISSION, PhoneHealth.NOT_RECEIVING -> COLOR_PROBLEM
    }

@Composable
private fun describeReading(status: PhoneStatus): String {
    val reading = status.lastReading ?: return stringResource(R.string.value_none_yet)
    return stringResource(
        R.string.reading_with_age,
        reading.mgdl,
        GlucoseUnit.MG_DL.label,
        ageText(status.ageMinutes),
    )
}

@Composable
private fun describeLastSent(status: PhoneStatus): String {
    val sent = status.lastSentMillis ?: return stringResource(R.string.value_not_yet)
    val minutes = ((System.currentTimeMillis() - sent) / 60_000L).toInt()
    return if (minutes <= 0) {
        stringResource(R.string.sent_just_now)
    } else {
        pluralStringResource(R.plurals.age_minutes_ago, minutes, minutes)
    }
}

@Composable
private fun ageText(ageMinutes: Long?): String {
    val minutes = ageMinutes?.toInt() ?: return stringResource(R.string.age_just_now)
    return if (minutes <= 0) {
        stringResource(R.string.age_just_now)
    } else {
        pluralStringResource(R.plurals.age_minutes_ago, minutes, minutes)
    }
}

private const val REFRESH_INTERVAL_MILLIS = 5_000L

private val COLOR_OK = Color(0xFFA5D6A7)
private val COLOR_ATTENTION = Color(0xFFFFE082)
private val COLOR_PROBLEM = Color(0xFFEF9A9A)
private val COLOR_NEUTRAL = Color(0xFFE0E0E0)
private val COLOR_BRAND_TEAL = Color(0xFF0F5F52)
private val COLOR_QUICK_SETTINGS_BG = Color(0xFFE8F5F2)
private val COLOR_QUICK_SETTINGS_TEXT = Color(0xFF142623)
