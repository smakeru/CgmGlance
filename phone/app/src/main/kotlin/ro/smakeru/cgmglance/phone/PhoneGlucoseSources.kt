package ro.smakeru.cgmglance.phone

import ro.smakeru.cgmglance.domain.glucose.GlucoseDelta
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.TrendArrow

/**
 * Independent histories, one explicitly selected output. LinX wins by default and remains selected
 * across silence, Dexcom posts, rebinds and restarts. Switching to Dexcom is a phone setting, never
 * a side effect of notification order. This matters while both sensors are running together.
 *
 * Selection, recording and snapshots share a lock. The application's effects (watch/export/watchdog)
 * also run under its own ingestion lock so an old callback cannot publish after a source switch.
 */
class PhoneGlucoseSources(
    private val repositories: Map<GlucoseSource, PhoneGlucoseRepository>,
    initialSource: GlucoseSource = GlucoseSource.LINX,
) {
    private var selected = initialSource
    private val fallback = mutableMapOf<GlucoseSource, Boolean>()
    private val failures = mutableMapOf<GlucoseSource, String>()

    init {
        require(GlucoseSource.entries.all { it in repositories })
    }

    data class Snapshot(
        val source: GlucoseSource,
        val latest: GlucoseReading?,
        val usedFallbackParser: Boolean,
        val trend: TrendArrow,
        val parseFailure: String?,
        val delta: GlucoseDelta? = null,
    )

    /** A fixed-source widget may inspect the background feed without changing the watch source. */
    @Synchronized
    fun snapshot(source: GlucoseSource? = null): Snapshot {
        val actual = source ?: selected
        val repository = repositories.getValue(actual)
        return Snapshot(
            actual,
            repository.latest,
            fallback[actual] ?: false,
            repository.trend,
            failures[actual],
            repository.delta,
        )
    }

    @Synchronized
    fun recordFailure(
        source: GlucoseSource,
        reason: String,
    ) {
        failures[source] = reason
    }

    /** Returns an output only for the selected source; background readings never reset its silence. */
    @Synchronized
    fun record(
        source: GlucoseSource,
        reading: GlucoseReading,
        usedFallback: Boolean,
    ): PhoneGlucoseRepository.Outcome? {
        val repository = repositories.getValue(source)
        // Active-notification replays must not roll the watch back or count twice in a trend.
        val latest = repository.latest
        if (latest != null && reading.timestampMillis <= latest.timestampMillis) return null
        fallback[source] = usedFallback
        failures.remove(source)
        val outcome = repository.record(reading)
        return outcome.takeIf { source == selected }
    }

    /** Resends cached data with its original timestamp, without mixing or duplicating history. */
    @Synchronized
    fun select(source: GlucoseSource): PhoneGlucoseRepository.Outcome? {
        if (source == selected) return null
        selected = source
        return repositories.getValue(source).latestForSync()
    }
}
