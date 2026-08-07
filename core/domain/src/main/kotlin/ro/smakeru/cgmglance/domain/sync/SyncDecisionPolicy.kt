package ro.smakeru.cgmglance.domain.sync

import ro.smakeru.cgmglance.domain.glucose.GlucoseRange
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import kotlin.math.abs

/** What to do with a newly acquired reading, as far as the watch is concerned. */
enum class SyncDecision {
    /** Nothing the watch would notice has changed. Keep the radio quiet. */
    SKIP,

    /** Worth sending, but it can ride along with whatever the platform batches next. */
    SEND,

    /**
     * Send now.
     *
     * Without this the Data Layer may hold a payload for up to half an hour. That delay is
     * invisible in testing and ruinous in use, so it is spent only where the difference is
     * worth it: a value the user would want to see immediately.
     */
    SEND_URGENT,
}

/** State of the last payload actually delivered to the watch. */
data class LastSent(
    val mgdl: Int,
    val timestampMillis: Long,
    val range: GlucoseRange,
)

/**
 * Decides whether a reading is worth a radio transmission.
 *
 * The LinX sensor reports about once a minute. Forwarding every reading would mean roughly
 * 1400 transmissions a day to show a number that mostly has not moved. This policy cuts that to
 * a couple of hundred without the user losing anything they could perceive: the value still
 * refreshes at least every few minutes, and anything that actually changes goes immediately.
 *
 * Pure logic, deliberately: the battery behaviour of the whole app is decided here, and it has
 * to be verifiable in a unit test rather than by leaving a watch on a desk overnight.
 */
class SyncDecisionPolicy(
    private val significantDeltaMgdl: Int = SIGNIFICANT_DELTA_MGDL,
    private val maxSilenceMillis: Long = MAX_SILENCE_MILLIS,
    private val gapMillis: Long = GAP_MILLIS,
) {
    fun decide(
        candidate: GlucoseReading,
        candidateRange: GlucoseRange,
        lastSent: LastSent?,
    ): SyncDecision {
        // Nothing on the watch yet. Whatever we have beats an empty complication.
        if (lastSent == null) return SyncDecision.SEND_URGENT

        // Out of order arrival. The watch already holds something newer, so this would move it
        // backwards in time.
        if (candidate.timestampMillis <= lastSent.timestampMillis) return SyncDecision.SKIP

        val silence = candidate.timestampMillis - lastSent.timestampMillis

        // Data resumed after an outage. The watch has been showing a stale or empty value and
        // should stop as soon as possible.
        if (silence >= gapMillis) return SyncDecision.SEND_URGENT

        // Crossing into or out of the target range is the one change the user reacts to.
        if (candidateRange != lastSent.range) return SyncDecision.SEND_URGENT

        if (abs(candidate.mgdl - lastSent.mgdl) >= significantDeltaMgdl) return SyncDecision.SEND_URGENT

        // Nothing notable happened, but the displayed value should not drift far behind.
        if (silence >= maxSilenceMillis) return SyncDecision.SEND

        return SyncDecision.SKIP
    }

    companion object {
        /** Roughly 0.6 mmol/L: below this the watch face would look unchanged. */
        const val SIGNIFICANT_DELTA_MGDL: Int = 10

        /** Longest the watch may go without a refresh while glucose sits still. */
        const val MAX_SILENCE_MILLIS: Long = 5 * 60_000L

        /** Silence longer than this counts as an outage rather than a quiet stretch. */
        const val GAP_MILLIS: Long = 15 * 60_000L
    }
}
