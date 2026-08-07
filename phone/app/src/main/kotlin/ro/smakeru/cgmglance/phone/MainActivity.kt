package ro.smakeru.cgmglance.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit

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
                            onSave = { thresholds, unit ->
                                app.updatePreferences(thresholds, unit)
                                showSettings = false
                            },
                            onBack = { showSettings = false },
                        )
                    } else {
                        StatusScreen(
                            source = app.statusSource,
                            onGrantPermission = { openNotificationAccessSettings() },
                            onOpenSettings = { showSettings = true },
                        )
                    }
                }
            }
        }
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
    onOpenSettings: () -> Unit,
) {
    var watchConnected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(source.snapshot(watchConnected = false)) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Polled rather than observed. Nothing here changes faster than the eye, the screen is only
    // alive while the user is looking at it, and a push pipeline for four fields would be more
    // machinery than the problem deserves.
    LaunchedEffect(Unit) {
        while (true) {
            Wearable
                .getNodeClient(context)
                .connectedNodes
                .addOnSuccessListener { nodes -> watchConnected = nodes.isNotEmpty() }
            status = source.snapshot(watchConnected)
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
            Text(text = "CGM Glance", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            HealthCard(status, onGrantPermission)

            if (status.needsAttention) {
                Spacer(Modifier.height(12.dp))
                AttentionCard()
            }

            Spacer(Modifier.height(24.dp))
            Text(text = "Details", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            DetailRow("Source", "LinX")
            DetailRow("Notification access", if (status.permissionGranted) "Granted" else "Not granted")
            DetailRow("Last reading", describeReading(status))
            DetailRow("Sent to watch", describeLastSent(status))
            DetailRow("Watch", if (status.watchConnected) "Connected" else "Not reachable")

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onOpenSettings) { Text("Target range and unit") }

            Spacer(Modifier.height(16.dp))
            Text(
                text =
                    "Shows a value the official app has already computed. Not a medical device: " +
                        "never use it for treatment decisions, and always confirm with the " +
                        "official app or a meter.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun HealthCard(
    status: PhoneStatus,
    onGrantPermission: () -> Unit,
) {
    val (headline, explanation) = describeHealth(status.health)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorFor(status.health)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(text = headline, style = MaterialTheme.typography.titleLarge, color = Color.Black)
            Spacer(Modifier.height(6.dp))
            Text(text = explanation, style = MaterialTheme.typography.bodyMedium, color = Color.Black)

            if (status.health == PhoneHealth.NO_PERMISSION) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onGrantPermission) { Text("Open notification access") }
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
            Text(text = "Reading via fallback", style = MaterialTheme.typography.titleMedium, color = Color.Black)
            Spacer(Modifier.height(4.dp))
            Text(
                text =
                    "The LinX layout changed, so values are being found by position rather than " +
                        "by name. Still working, but it needs a look before the fallback stops " +
                        "matching too.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black,
            )
        }
    }
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

private fun describeHealth(health: PhoneHealth): Pair<String, String> =
    when (health) {
        PhoneHealth.OK -> {
            "Working" to "Readings are arriving and the watch has the latest one."
        }

        PhoneHealth.NO_PERMISSION -> {
            "Needs notification access" to
                "This app reads the value from the LinX notification, so it needs permission to " +
                "see notifications. It ignores everything that is not from LinX."
        }

        PhoneHealth.WAITING_FOR_FIRST_READING -> {
            "Waiting for the first reading" to
                "Permission is granted. Open LinX so it refreshes its notification; this usually " +
                "takes under a minute."
        }

        PhoneHealth.NOT_RECEIVING -> {
            "Not receiving readings" to
                "Nothing has arrived recently. Check that LinX is running and still connected to " +
                "the sensor."
        }

        PhoneHealth.WATCH_UNREACHABLE -> {
            "Watch not reachable" to
                "Readings are arriving, but the watch is out of range or switched off. It will " +
                "catch up on its own once it reconnects."
        }
    }

private fun colorFor(health: PhoneHealth): Color =
    when (health) {
        PhoneHealth.OK -> COLOR_OK
        PhoneHealth.WAITING_FOR_FIRST_READING, PhoneHealth.WATCH_UNREACHABLE -> COLOR_ATTENTION
        PhoneHealth.NO_PERMISSION, PhoneHealth.NOT_RECEIVING -> COLOR_PROBLEM
    }

private fun describeReading(status: PhoneStatus): String {
    val reading = status.lastReading ?: return "None yet"
    val age =
        when {
            status.ageMinutes == null -> ""
            status.ageMinutes <= 0L -> ", just now"
            status.ageMinutes == 1L -> ", 1 min ago"
            else -> ", ${status.ageMinutes} min ago"
        }
    return "${reading.mgdl} ${GlucoseUnit.MG_DL.label}$age"
}

private fun describeLastSent(status: PhoneStatus): String {
    val sent = status.lastSentMillis ?: return "Not yet"
    val minutes = (System.currentTimeMillis() - sent) / 60_000L
    return when {
        minutes <= 0L -> "Just now"
        minutes == 1L -> "1 min ago"
        else -> "$minutes min ago"
    }
}

private const val REFRESH_INTERVAL_MILLIS = 5_000L

private val COLOR_OK = Color(0xFFA5D6A7)
private val COLOR_ATTENTION = Color(0xFFFFE082)
private val COLOR_PROBLEM = Color(0xFFEF9A9A)
