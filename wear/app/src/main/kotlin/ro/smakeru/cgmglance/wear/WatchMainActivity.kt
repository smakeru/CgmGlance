package ro.smakeru.cgmglance.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import ro.smakeru.cgmglance.domain.glucose.FreshnessPolicy
import ro.smakeru.cgmglance.domain.glucose.GlucoseRangePalette
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import ro.smakeru.cgmglance.domain.time.Clock

/**
 * The watch app: the value in full, and where a tap on the complication lands.
 *
 * Reads the same local store as the complication and the tile, and judges freshness against the
 * watch's own clock exactly as they do. Three surfaces cannot be allowed to disagree about how
 * old a number is.
 */
class WatchMainActivity : ComponentActivity() {
    private val clock = Clock { System.currentTimeMillis() }
    private val freshnessPolicy = FreshnessPolicy.default

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = WatchGlucoseStore(applicationContext)
        val texts = WearTexts(applicationContext)

        setContent {
            MaterialTheme {
                GlucoseScreen(
                    texts = texts,
                    read = {
                        val payload = store.loadLatest()
                        val prefs = store.loadPrefs()
                        val reading = payload?.reading
                        GlucoseDisplay.of(
                            reading = reading,
                            trend = payload?.trend ?: TrendArrow.UNKNOWN,
                            unit = prefs.unit,
                            range = reading?.let { prefs.thresholds.classify(it) },
                            freshness = freshnessPolicy.evaluate(reading, clock),
                            ageMinutes = freshnessPolicy.ageMillis(reading, clock)?.let { it / 60_000L },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun GlucoseScreen(
    texts: WearTexts,
    read: () -> GlucoseDisplay,
) {
    var display by remember { mutableStateOf(read()) }

    // Re-read while the screen is on. The store is a local file and the screen is only alive
    // while the user is looking at it, so this costs nothing worth measuring — and it means the
    // displayed age advances instead of freezing at whatever it was when the app opened.
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_INTERVAL_MILLIS)
            display = read()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = texts.describe(display) },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = display.compact,
            fontSize = VALUE_SIZE_SP.sp,
            fontWeight = FontWeight.Medium,
            color = colorFor(display),
        )

        Text(
            text = display.unitLabel,
            fontSize = SECONDARY_SIZE_SP.sp,
            color = COLOR_MUTED,
        )

        Text(
            text = texts.ageLabel(display),
            fontSize = SECONDARY_SIZE_SP.sp,
            color = COLOR_MUTED,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Colour carries the range, never an alarm.
 *
 * Muted before coloured once the reading stops describing the present: an amber number that is
 * forty minutes old would read as "you are high right now", which is the one thing this app must
 * never say by accident.
 */
private fun colorFor(display: GlucoseDisplay): Color =
    Color(GlucoseRangePalette.colorFor(display.range, display.freshness))

private const val REFRESH_INTERVAL_MILLIS = 10_000L
private const val VALUE_SIZE_SP = 46
private const val SECONDARY_SIZE_SP = 14

private val COLOR_MUTED = Color(GlucoseRangePalette.MUTED)
