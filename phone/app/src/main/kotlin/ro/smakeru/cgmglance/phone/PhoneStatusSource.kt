package ro.smakeru.cgmglance.phone

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import ro.smakeru.cgmglance.domain.glucose.FreshnessPolicy
import ro.smakeru.cgmglance.domain.time.Clock
import ro.smakeru.cgmglance.provider.dexcom.DexcomNotificationListenerService
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
            permissionGranted = isNotificationAccessGranted(selected.source),
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

    /**
     * Whether the user has granted notification access.
     *
     * Read straight from the secure setting rather than through a support library: it is three
     * lines, it cannot go stale behind a cache, and it is exactly what the system itself checks.
     */
    fun isNotificationAccessGranted(source: GlucoseSource = sources.snapshot().source): Boolean {
        val enabled =
            Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS)
                ?: return false
        // Android grants access per service, not per APK. LinX access alone cannot read Dexcom.
        val service =
            when (source) {
                GlucoseSource.LINX -> LinxNotificationListenerService::class.java.name
                GlucoseSource.DEXCOM_ONE_PLUS -> DexcomNotificationListenerService::class.java.name
            }
        return enabled.split(':').any { value ->
            val component = ComponentName.unflattenFromString(value)
            component?.packageName == context.packageName && component.className == service
        }
    }

    private companion object {
        const val ENABLED_LISTENERS = "enabled_notification_listeners"
    }
}
