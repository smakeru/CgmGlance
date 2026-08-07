package ro.smakeru.cgmglance.domain.glucose

import ro.smakeru.cgmglance.domain.time.Clock

/**
 * How much a reading can still be believed, judged purely by its age.
 *
 * This is the safety core of the app. A glucose number carries an implicit claim of "now"; the
 * only defence against that claim being false is to state the age alongside it and to stop
 * showing the number entirely once it is meaningless.
 *
 * The reader computes this against **its own clock**, never against a flag sent by the phone.
 * If the phone is gone, so is any assurance it could have provided — which is exactly the
 * situation the watch most needs to detect.
 */
enum class ReadingFreshness {
    /** Recent enough to display plainly. */
    FRESH,

    /**
     * Older than the sensor's own cadence, but still describing the present.
     *
     * Presented as the value plus its age, with **no dimming and no warning treatment**. The
     * FRESH window is deliberately narrow, so this state is common; a warning that is on most
     * of the time is not a warning, and the visual budget for alarm is spent at [STALE].
     */
    AGING,

    /** Old enough that the number may no longer describe the present. Shown degraded. */
    STALE,

    /** Nothing worth showing: either far too old, or never received. */
    NO_DATA,

    ;

    /**
     * Whether a trend may be derived from a series ending in a reading of this age.
     *
     * A regression fitted across a gap produces a confident looking slope from no evidence, so
     * the trend disappears before the value does.
     */
    val allowsTrend: Boolean
        get() = this == FRESH || this == AGING
}

/**
 * Age boundaries between the [ReadingFreshness] states.
 *
 * Not user configurable, on purpose. A target range is a personal clinical choice; freshness is
 * a protection mechanism, and one that the user can widen to "stop bothering me" has been
 * disabled at exactly the moment it mattered.
 */
class FreshnessPolicy private constructor(
    val freshLimitMillis: Long,
    val agingLimitMillis: Long,
    val staleLimitMillis: Long,
) {
    /**
     * Age of [reading] against [clock], or `null` when there is no reading.
     *
     * A negative result means the reading is stamped in the future, which happens when the two
     * devices' clocks disagree.
     */
    fun ageMillis(
        reading: GlucoseReading?,
        clock: Clock,
    ): Long? = reading?.let { clock.nowMillis() - it.timestampMillis }

    /** Classifies [reading] by age. A missing reading is [ReadingFreshness.NO_DATA]. */
    fun evaluate(
        reading: GlucoseReading?,
        clock: Clock,
    ): ReadingFreshness {
        val age = ageMillis(reading, clock) ?: return ReadingFreshness.NO_DATA

        if (age < 0) {
            // Stamped in the future. A little skew between two devices is normal and harmless;
            // beyond that the age is not a number we can defend, and claiming freshness we
            // cannot substantiate is the one failure this whole type exists to prevent.
            return if (-age <= CLOCK_SKEW_TOLERANCE_MILLIS) {
                ReadingFreshness.FRESH
            } else {
                ReadingFreshness.NO_DATA
            }
        }

        return when {
            age < freshLimitMillis -> ReadingFreshness.FRESH
            age < agingLimitMillis -> ReadingFreshness.AGING
            age < staleLimitMillis -> ReadingFreshness.STALE
            else -> ReadingFreshness.NO_DATA
        }
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L

        /** Beyond the sensor's own update cadence, so the age becomes worth stating. */
        const val FRESH_LIMIT_MINUTES: Int = 2

        /** Beyond this the number may no longer describe the present. */
        const val AGING_LIMIT_MINUTES: Int = 10

        /** Beyond this there is nothing worth showing. */
        const val STALE_LIMIT_MINUTES: Int = 20

        /**
         * How far the source's clock may run ahead of the reader's before its timestamps stop
         * being usable. Matched to the FRESH window: a disagreement larger than the window
         * itself makes every freshness claim guesswork.
         */
        const val CLOCK_SKEW_TOLERANCE_MILLIS: Long = FRESH_LIMIT_MINUTES * MILLIS_PER_MINUTE

        val default: FreshnessPolicy =
            FreshnessPolicy(
                freshLimitMillis = FRESH_LIMIT_MINUTES * MILLIS_PER_MINUTE,
                agingLimitMillis = AGING_LIMIT_MINUTES * MILLIS_PER_MINUTE,
                staleLimitMillis = STALE_LIMIT_MINUTES * MILLIS_PER_MINUTE,
            )
    }
}
