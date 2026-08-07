package ro.smakeru.cgmglance.phone

import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.RangeThresholds
import ro.smakeru.cgmglance.domain.glucose.TrendCalculator
import ro.smakeru.cgmglance.domain.sync.LastSent
import ro.smakeru.cgmglance.domain.sync.SyncDecision
import ro.smakeru.cgmglance.domain.sync.SyncDecisionPolicy
import ro.smakeru.cgmglance.domain.time.Clock
import ro.smakeru.cgmglance.sync.LatestReadingPayload

/**
 * Where the recent readings survive process death.
 *
 * An interface so the repository stays a plain JVM object: its rules are worth testing without
 * an emulator, and storage is the one part that cannot be.
 */
interface HistoryPersistence {
    fun load(): List<GlucoseReading>

    fun save(readings: List<GlucoseReading>)

    /** Used in tests and wherever persistence is genuinely not wanted. */
    companion object None : HistoryPersistence {
        override fun load(): List<GlucoseReading> = emptyList()

        override fun save(readings: List<GlucoseReading>) = Unit
    }
}

/**
 * Everything the phone knows about glucose, and the single place that decides what the watch
 * gets told.
 *
 * Holds two hours of readings. That is far more than the display needs — the trend fit reaches
 * back fifteen minutes — and exists so the diagnostics screen can answer "when did readings
 * arrive, and how far apart", which is how a broken parser will be diagnosed after a vendor
 * update.
 */
class PhoneGlucoseRepository(
    private val clock: Clock,
    private val trendCalculator: TrendCalculator = TrendCalculator(),
    private val syncPolicy: SyncDecisionPolicy = SyncDecisionPolicy(),
    // A supplier rather than a value: the user can change their target range while the app is
    // running, and the next reading must be classified against the new one without the
    // repository being rebuilt around it.
    private val thresholds: () -> RangeThresholds = { RangeThresholds.default },
    private val historyWindowMillis: Long = HISTORY_WINDOW_MILLIS,
    private val persistence: HistoryPersistence = HistoryPersistence.None,
) {
    /** What [record] concluded: a payload to transmit, or nothing worth transmitting. */
    data class Outcome(
        val payload: LatestReadingPayload?,
        val urgent: Boolean,
    )

    private val lock = Any()
    private val history = ArrayDeque<GlucoseReading>()
    private var lastSent: LastSent? = null

    /** First post of the current run of identical values. See [collapseRepost]. */
    private var runAnchorMillis: Long = 0L

    /**
     * Seeded from the clock rather than starting at zero.
     *
     * The Data Layer ignores a payload identical to the one already stored. After a process
     * restart a counter beginning at zero would replay numbers the watch has already seen, so
     * the first readings after a crash would silently fail to arrive.
     */
    private var sequence: Long = clock.nowMillis()

    init {
        // Restoring before the first reading arrives is what keeps the trend alive across a
        // restart. The store drops anything outside the window, so a phone that was off for
        // hours starts empty rather than with readings that would mislead the fit.
        history.addAll(persistence.load())
        runAnchorMillis = history.lastOrNull()?.timestampMillis ?: 0L
    }

    val readings: List<GlucoseReading>
        get() = synchronized(lock) { history.toList() }

    val latest: GlucoseReading?
        get() = synchronized(lock) { history.lastOrNull() }

    /** Accepts a reading and reports whether the watch should hear about it. */
    fun record(reading: GlucoseReading): Outcome =
        synchronized(lock) {
            val collapsed = collapseRepost(reading)
            insertInOrder(reading)
            trimToWindow(reading.timestampMillis)

            // Only when the shape of the history changed. A collapsed repost merely refreshes
            // the last entry's timestamp, and writing on every one of those would mean a few
            // thousand disk writes a day to preserve something the next reading replaces.
            if (!collapsed) persistence.save(history.toList())

            val decision = syncPolicy.decide(reading, thresholds().classify(reading), lastSent)
            if (decision == SyncDecision.SKIP) return Outcome(payload = null, urgent = false)

            lastSent = LastSent(reading.mgdl, reading.timestampMillis, thresholds().classify(reading))
            sequence++

            Outcome(
                payload =
                    LatestReadingPayload(
                        reading = reading,
                        trend = trendCalculator.calculate(history.toList(), clock),
                        senderNowMillis = clock.nowMillis(),
                        sequence = sequence,
                    ),
                urgent = decision == SyncDecision.SEND_URGENT,
            )
        }

    /**
     * Drops the previous entry when this one is the same measurement posted again.
     *
     * Observed on a real device: LinX republishes its ongoing notification four or five times
     * within a few milliseconds, and again every twenty to thirty seconds, carrying the same
     * value each time. Each repost has its own `postTime`, so without this every one of them
     * would land as a distinct reading — thousands of entries in the two hour buffer instead of
     * dozens, and a trend fit crowded with points that share an x coordinate.
     *
     * The window is measured from [runAnchorMillis], the first post of the current run, and not
     * from the entry last stored. A window measured from the last entry slides forward on every
     * repost, so with reposts arriving closer together than the window itself it never expires:
     * an unchanging glucose would collapse into a single entry for as long as it stayed
     * unchanged, and the trend fit would never gather the three points it needs. Steady is
     * precisely when a `→` is worth showing, so that failure mode is unacceptable.
     *
     * Within a run the later timestamp replaces the earlier one, because it is the most recent
     * moment the source confirmed this value — which is what freshness should be measured from.
     */
    private fun collapseRepost(incoming: GlucoseReading): Boolean {
        val previous = history.lastOrNull()
        val sameValue = previous != null && previous.mgdl == incoming.mgdl
        val withinRun = incoming.timestampMillis - runAnchorMillis in 0 until REPOST_WINDOW_MILLIS

        if (sameValue && withinRun) {
            history.removeLast()
            return true
        }
        runAnchorMillis = incoming.timestampMillis
        return false
    }

    /**
     * Keeps the buffer sorted by time.
     *
     * Notifications normally arrive in order, but a rebind after the system unbound the
     * listener can replay one out of sequence, and the trend fit assumes nothing about order
     * only because this does.
     */
    private fun insertInOrder(reading: GlucoseReading) {
        if (history.isEmpty() || history.last().timestampMillis <= reading.timestampMillis) {
            history.addLast(reading)
            return
        }
        val ordered = (history + reading).sortedBy { it.timestampMillis }
        history.clear()
        history.addAll(ordered)
    }

    private fun trimToWindow(nowMillis: Long) {
        val cutoff = nowMillis - historyWindowMillis
        while (history.isNotEmpty() && history.first().timestampMillis < cutoff) {
            history.removeFirst()
        }
    }

    companion object {
        /** Two hours, kept for diagnostics rather than for display. */
        const val HISTORY_WINDOW_MILLIS: Long = 2 * 60 * 60 * 1000L

        /**
         * Two identical values closer together than this are one measurement, not two.
         *
         * Sized from the observed repost rhythm — bursts of milliseconds, then roughly every
         * twenty to thirty seconds — while staying well under the sensor's own one minute
         * cadence, so a genuinely unchanged consecutive measurement is still recorded.
         */
        const val REPOST_WINDOW_MILLIS: Long = 45_000L
    }
}
