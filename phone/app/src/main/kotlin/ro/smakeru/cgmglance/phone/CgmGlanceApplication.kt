package ro.smakeru.cgmglance.phone

import android.app.Application
import android.util.Log
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.RangeThresholds
import ro.smakeru.cgmglance.domain.time.Clock
import ro.smakeru.cgmglance.provider.dexcom.DexcomParseFailure
import ro.smakeru.cgmglance.provider.dexcom.DexcomProviderBus
import ro.smakeru.cgmglance.provider.dexcom.DexcomReadingSink
import ro.smakeru.cgmglance.provider.linx.LinxParseFailure
import ro.smakeru.cgmglance.provider.linx.LinxProviderBus
import ro.smakeru.cgmglance.provider.linx.LinxReadingSink
import ro.smakeru.cgmglance.sync.PrefsPayload

/** Wires the small object graph by hand, including the two Android-created provider services. */
class CgmGlanceApplication : Application() {
    private val clock = Clock { System.currentTimeMillis() }
    private val ingestionLock = Any()

    private lateinit var sources: PhoneGlucoseSources
    private lateinit var sender: WatchSyncSender

    /** Read by the status screen and by the watchdog. Set up before anything can ask for it. */
    lateinit var statusSource: PhoneStatusSource
        private set

    lateinit var notifier: AppStateNotifier
        private set

    private lateinit var watchdog: SilenceWatchdog

    lateinit var preferences: PhonePreferencesStore
        private set

    /** Writes glucose out to Health Connect. Read by the status screen to report on itself. */
    lateinit var healthExporter: HealthConnectExporter
        private set

    override fun onCreate() {
        super.onCreate()

        preferences = PhonePreferencesStore(this)
        sources =
            PhoneGlucoseSources(
                repositories =
                    GlucoseSource.entries.associateWith { source ->
                        PhoneGlucoseRepository(
                            clock = clock,
                            thresholds = { preferences.loadThresholds() },
                            persistence = ReadingHistoryStore(this, clock, source = source),
                        )
                    },
                initialSource = preferences.loadSource(),
            )
        sender = WatchSyncSender(this)
        healthExporter =
            HealthConnectExporter(
                context = this,
                clock = clock,
                preferences = preferences,
                settings = { preferences.loadHealthExportSettings() },
            )
        statusSource = PhoneStatusSource(this, sources, clock, healthExporter)
        notifier = AppStateNotifier(this).apply { ensureChannel() }
        watchdog = SilenceWatchdog(this)

        // A reboot into a broken state must still report silence.
        watchdog.arm()
        LinxProviderBus.sink = LinxSink()
        DexcomProviderBus.sink = DexcomSink()
        sendPreferencesToWatch()
    }

    /** Source is phone-only. The watch's range/unit payload and reading contract stay unchanged. */
    fun updatePreferences(
        thresholds: RangeThresholds,
        unit: GlucoseUnit,
        healthExport: HealthExportSettings,
        source: GlucoseSource,
    ) = synchronized(ingestionLock) {
        preferences.save(thresholds, unit)
        preferences.saveHealthExportSettings(healthExport)
        preferences.saveSource(source)
        sendPreferencesToWatch()

        sources.select(source)?.let { outcome ->
            // Changing selection is not a new reading and must not reset freshness or the
            // Health Connect cadence. A pending warning belongs to the previous selection.
            notifier.clear()
            watchdog.arm()
            sendOutcome(outcome)
        }
        GlucoseWidgetProvider.refresh(this)
    }

    /** Read-only view for home-screen widgets; inspecting Dexcom never selects it for the watch. */
    fun widgetSnapshot(source: GlucoseSource? = null): PhoneGlucoseSources.Snapshot = sources.snapshot(source)

    private fun sendPreferencesToWatch() {
        sender.sendPrefs(PrefsPayload(preferences.loadUnit(), preferences.loadThresholds()))
    }

    private fun acceptReading(
        source: GlucoseSource,
        reading: GlucoseReading,
        usedFallback: Boolean,
    ) = synchronized(ingestionLock) {
        val outcome = sources.record(source, reading, usedFallback)
        // Widgets can explicitly follow the other sensor even while LinX owns the watch feed.
        GlucoseWidgetProvider.refresh(this)
        if (outcome == null) return@synchronized
        // Background readings do not reach this path: they must neither export competing
        // values nor hide that the selected feed has stopped.
        notifier.clear()
        watchdog.arm()
        if (usedFallback) Log.w(TAG, "${source.label}: parsed via fallback; verify vendor layout")
        healthExporter.offer(reading)
        sendOutcome(outcome)
    }

    private fun sendOutcome(outcome: PhoneGlucoseRepository.Outcome) {
        val payload = outcome.payload ?: return
        sender.sendLatest(payload, outcome.urgent)
        statusSource.lastSentMillis = clock.nowMillis()
        Log.i(TAG, "sent reading, source=${sources.snapshot().source}, trend=${payload.trend}")
    }

    private inner class LinxSink : LinxReadingSink {
        override fun onReading(
            reading: GlucoseReading,
            unit: GlucoseUnit,
            usedFallback: Boolean,
        ) = acceptReading(GlucoseSource.LINX, reading, usedFallback)

        override fun onParseFailure(reason: LinxParseFailure) = sources.recordFailure(GlucoseSource.LINX, reason.name)
    }

    private inner class DexcomSink : DexcomReadingSink {
        override fun onReading(
            reading: GlucoseReading,
            unit: GlucoseUnit,
            usedFallback: Boolean,
        ) = acceptReading(GlucoseSource.DEXCOM_ONE_PLUS, reading, usedFallback)

        override fun onParseFailure(reason: DexcomParseFailure) =
            sources.recordFailure(GlucoseSource.DEXCOM_ONE_PLUS, reason.name)
    }

    private companion object {
        const val TAG = "CgmGlance/App"
    }
}
