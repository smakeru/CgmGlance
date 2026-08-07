package ro.smakeru.cgmglance.phone

import android.app.Application
import android.util.Log
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.RangeThresholds
import ro.smakeru.cgmglance.domain.time.Clock
import ro.smakeru.cgmglance.provider.linx.LinxProviderBus
import ro.smakeru.cgmglance.provider.linx.LinxReadingSink
import ro.smakeru.cgmglance.sync.PrefsPayload

/**
 * Wires the object graph by hand.
 *
 * No dependency injection framework: the graph is a handful of objects with no scopes and no
 * variants, and the two components the platform constructs for us — the notification listener
 * and, on the watch, the complication and tile services — would each need an escape hatch
 * anyway. Introduce Hilt when the graph outgrows this file, not before.
 */
class CgmGlanceApplication : Application() {
    private val clock = Clock { System.currentTimeMillis() }

    private lateinit var repository: PhoneGlucoseRepository
    private lateinit var sender: WatchSyncSender

    /** Read by the status screen and by the watchdog. Set up before anything can ask for it. */
    lateinit var statusSource: PhoneStatusSource
        private set

    lateinit var notifier: AppStateNotifier
        private set

    private lateinit var watchdog: SilenceWatchdog

    lateinit var preferences: PhonePreferencesStore
        private set

    override fun onCreate() {
        super.onCreate()

        preferences = PhonePreferencesStore(this)
        repository =
            PhoneGlucoseRepository(
                clock = clock,
                // Read per reading rather than captured once, so an edit takes effect on the
                // next value instead of at the next restart.
                thresholds = { preferences.loadThresholds() },
                persistence = ReadingHistoryStore(this, clock),
            )
        sender = WatchSyncSender(this)
        statusSource = PhoneStatusSource(this, repository, clock)
        notifier = AppStateNotifier(this).apply { ensureChannel() }
        watchdog = SilenceWatchdog(this)

        // Armed at startup as well as on every reading, so a phone that reboots into a broken
        // state still reports it rather than waiting for a reading that will never come.
        watchdog.arm()

        LinxProviderBus.sink = LinxSink()

        // Sent once at start so a freshly installed watch can classify and format before the
        // first reading arrives. Cheap, and it removes a class of "why is it grey" reports.
        sendPreferencesToWatch()
    }

    /**
     * Persists the user's range and unit, then tells the watch.
     *
     * Both sides must agree: the watch classifies and formats locally, so a range saved here
     * and not sent would leave the complication colouring against the old one.
     */
    fun updatePreferences(
        thresholds: RangeThresholds,
        unit: GlucoseUnit,
    ) {
        preferences.save(thresholds, unit)
        sendPreferencesToWatch()
    }

    private fun sendPreferencesToWatch() {
        sender.sendPrefs(PrefsPayload(preferences.loadUnit(), preferences.loadThresholds()))
    }

    private inner class LinxSink : LinxReadingSink {
        override fun onReading(
            reading: GlucoseReading,
            unit: GlucoseUnit,
            usedFallback: Boolean,
        ) {
            // A reading arrived, so whatever the notification was warning about is over, and
            // the silence clock starts again.
            notifier.clear()
            watchdog.arm()

            statusSource.usedFallbackParser = usedFallback
            if (usedFallback) {
                // The named views are gone, so a vendor update has changed the layout. The app
                // still works, but this is the signal that the parser needs attention before
                // the fallback stops matching too.
                Log.w(TAG, "parsed via positional fallback: vendor layout has changed")
            }

            val outcome = repository.record(reading)
            val payload = outcome.payload ?: return
            sender.sendLatest(payload, outcome.urgent)
            statusSource.lastSentMillis = clock.nowMillis()
            Log.i(TAG, "sent ${payload.reading.mgdl} trend=${payload.trend} urgent=${outcome.urgent}")
        }
    }

    private companion object {
        const val TAG = "CgmGlance/App"
    }
}
