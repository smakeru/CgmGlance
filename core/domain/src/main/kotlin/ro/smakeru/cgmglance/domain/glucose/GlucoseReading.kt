package ro.smakeru.cgmglance.domain.glucose

/**
 * A single glucose measurement, already validated.
 *
 * Deliberately not a `data class`: the constructor is private so that an implausible reading
 * cannot exist, and a data class would reopen that hole through the generated `copy()`.
 *
 * The value is always whole mg/dL. mmol/L is a presentation concern — see [GlucoseUnit].
 *
 * The timestamp is when the *source* produced the reading, in epoch milliseconds. It is not the
 * moment we received or parsed it, and it is never derived from a human readable string: the
 * LinX notification renders its age as localised text ("Chiar acum"), which is unusable as a
 * source of truth. Freshness is computed from this field against the reader's own clock.
 */
class GlucoseReading private constructor(
    val mgdl: Int,
    val timestampMillis: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is GlucoseReading && mgdl == other.mgdl && timestampMillis == other.timestampMillis)

    override fun hashCode(): Int = 31 * mgdl + timestampMillis.hashCode()

    override fun toString(): String = "GlucoseReading(mgdl=$mgdl, timestampMillis=$timestampMillis)"

    companion object {
        /**
         * Lowest value treated as a real measurement.
         *
         * Chosen below what any consumer sensor reports, because the job here is to reject
         * parsing accidents and spoofed values, not to second-guess the sensor. A person can
         * genuinely be at 25 mg/dL; a parser that grabbed the wrong text field cannot.
         */
        const val MIN_PLAUSIBLE_MGDL: Int = 20

        /** Highest value treated as a real measurement, chosen on the same reasoning. */
        const val MAX_PLAUSIBLE_MGDL: Int = 600

        /**
         * Builds a reading, or returns `null` when the inputs cannot describe a real
         * measurement.
         *
         * Returning `null` rather than throwing is intentional: an out of range value is an
         * expected event on the acquisition path — a vendor app changed its layout, another
         * app spoofed a broadcast, a regex matched the wrong number — and expected events
         * should not be modelled as exceptions. The caller decides what to record and show.
         */
        fun create(
            mgdl: Int,
            timestampMillis: Long,
        ): GlucoseReading? {
            if (mgdl < MIN_PLAUSIBLE_MGDL || mgdl > MAX_PLAUSIBLE_MGDL) return null
            if (timestampMillis <= 0L) return null
            return GlucoseReading(mgdl, timestampMillis)
        }
    }
}
