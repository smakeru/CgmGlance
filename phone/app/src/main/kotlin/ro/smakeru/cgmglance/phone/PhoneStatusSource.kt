package ro.smakeru.cgmglance.phone

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import ro.smakeru.cgmglance.domain.glucose.FreshnessPolicy
import ro.smakeru.cgmglance.domain.time.Clock
import ro.smakeru.cgmglance.provider.linx.LinxNotificationListenerService

/**
 * Assembles what the status screen shows from the parts scattered across the app.
 *
 * Also the place that records the two facts nothing else keeps: when the watch was last told
 * something, and whether the parser had to fall back. Both are written from the notification
 * callback and read from the UI thread, hence the volatiles.
 */
class PhoneStatusSource(
    private val context: Context,
    private val sources: PhoneGlucoseSources,
    private val clock: Clock,
    private val healthExporter: GlucoseExporter,
    private val freshnessPolicy: FreshnessPolicy = FreshnessPolicy.default,
) {
    @Volatile
    var lastSentMillis: Long? = null

    fun snapshot(watchLink: WatchLink): PhoneStatus {
        val selected = sources.snapshot()
        val latest = selected.latest
        return PhoneStatus(
            permissionGranted = isNotificationAccessGranted(),
            lastReading = latest,
            freshness = freshnessPolicy.evaluate(latest, clock),
            ageMinutes = freshnessPolicy.ageMillis(latest, clock)?.let { it / 60_000L },
            watchLink = watchLink,
            lastSentMillis = lastSentMillis,
            usedFallbackParser = selected.usedFallbackParser,
            healthExport = healthExporter.status(),
            source = selected.source,
            parseFailure = selected.parseFailure,
        )
    }

    /** Both readers share one listener; readings and diagnostics still follow the selected source. */
    fun isNotificationAccessGranted(): Boolean {
        val listener = ComponentName(context, LinxNotificationListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return context
                .getSystemService(
                    NotificationManager::class.java,
                ).isNotificationListenerAccessGranted(listener)
        }
        // The public NotificationManager query was added in API 27; Android 8.0 remains supported.
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabled?.split(':')?.any { ComponentName.unflattenFromString(it) == listener } == true
    }
}
