package ro.smakeru.cgmglance.phone

import android.content.Context
import android.provider.Settings
import ro.smakeru.cgmglance.domain.glucose.FreshnessPolicy
import ro.smakeru.cgmglance.domain.time.Clock

/**
 * Assembles what the status screen shows from the parts scattered across the app.
 *
 * Also the place that records the two facts nothing else keeps: when the watch was last told
 * something, and whether the parser had to fall back. Both are written from the notification
 * callback and read from the UI thread, hence the volatiles.
 */
class PhoneStatusSource(
    private val context: Context,
    private val repository: PhoneGlucoseRepository,
    private val clock: Clock,
    private val healthExporter: GlucoseExporter,
    private val freshnessPolicy: FreshnessPolicy = FreshnessPolicy.default,
) {
    @Volatile
    var lastSentMillis: Long? = null

    @Volatile
    var usedFallbackParser: Boolean = false

    fun snapshot(watchConnected: Boolean): PhoneStatus {
        val latest = repository.latest
        return PhoneStatus(
            permissionGranted = isNotificationAccessGranted(),
            lastReading = latest,
            freshness = freshnessPolicy.evaluate(latest, clock),
            ageMinutes = freshnessPolicy.ageMillis(latest, clock)?.let { it / 60_000L },
            watchConnected = watchConnected,
            lastSentMillis = lastSentMillis,
            usedFallbackParser = usedFallbackParser,
            healthExport = healthExporter.status(),
        )
    }

    /**
     * Whether the user has granted notification access.
     *
     * Read straight from the secure setting rather than through a support library: it is three
     * lines, it cannot go stale behind a cache, and it is exactly what the system itself checks.
     */
    fun isNotificationAccessGranted(): Boolean {
        val enabled =
            Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS)
                ?: return false
        return enabled.split(':').any { it.startsWith("${context.packageName}/") }
    }

    private companion object {
        const val ENABLED_LISTENERS = "enabled_notification_listeners"
    }
}
