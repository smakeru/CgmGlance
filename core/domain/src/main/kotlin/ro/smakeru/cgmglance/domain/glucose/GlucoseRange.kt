package ro.smakeru.cgmglance.domain.glucose

/**
 * Where a reading sits relative to the user's target range.
 *
 * Drives colour and wording only. This app never alerts on these states — see the non-goals in
 * the README. A distinct [URGENT_LOW] exists so that a glance at 50 does not look like a glance
 * at 68, not so that anything can be triggered by it.
 */
enum class GlucoseRange {
    URGENT_LOW,
    LOW,
    IN_RANGE,
    HIGH,

    /**
     * Above the user's very-high boundary, 260 mg/dL by default.
     *
     * Shown with the same weight as [URGENT_LOW]: a glance at 320 must not read like a glance
     * at 190. Colour only, like every other state here.
     */
    VERY_HIGH,
}

/**
 * User configurable boundaries of the target range, in mg/dL.
 *
 * Unlike the freshness thresholds, these are legitimately the user's to change: a target range
 * is a personal clinical decision. Freshness is a protection mechanism and stays fixed.
 *
 * Instances are only obtainable through [create], so an inconsistent set of thresholds — a low
 * above a high, an urgent low above a low — cannot reach the classification code.
 */
class RangeThresholds private constructor(
    val urgentLowMgdl: Int,
    val lowMgdl: Int,
    val highMgdl: Int,
    val veryHighMgdl: Int,
) {
    /** Classifies a reading against these boundaries. Boundary values count as the calmer state. */
    fun classify(reading: GlucoseReading): GlucoseRange =
        when {
            reading.mgdl < urgentLowMgdl -> GlucoseRange.URGENT_LOW
            reading.mgdl < lowMgdl -> GlucoseRange.LOW
            reading.mgdl > veryHighMgdl -> GlucoseRange.VERY_HIGH
            reading.mgdl > highMgdl -> GlucoseRange.HIGH
            else -> GlucoseRange.IN_RANGE
        }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is RangeThresholds &&
                    urgentLowMgdl == other.urgentLowMgdl &&
                    lowMgdl == other.lowMgdl &&
                    highMgdl == other.highMgdl &&
                    veryHighMgdl == other.veryHighMgdl
            )

    override fun hashCode(): Int = ((31 * urgentLowMgdl + lowMgdl) * 31 + highMgdl) * 31 + veryHighMgdl

    override fun toString(): String = "RangeThresholds($urgentLowMgdl/$lowMgdl/$highMgdl/$veryHighMgdl)"

    companion object {
        /** Internationally used target range for time in range reporting. */
        const val DEFAULT_LOW_MGDL: Int = 70

        /** Upper bound of that same target range. */
        const val DEFAULT_HIGH_MGDL: Int = 180

        /** Severe hypoglycaemia boundary, shown differently from an ordinary low. */
        const val DEFAULT_URGENT_LOW_MGDL: Int = 55

        /** Severe hyperglycaemia boundary, shown with the same weight as an urgent low. */
        const val DEFAULT_VERY_HIGH_MGDL: Int = 260

        /** The defaults, used until the user changes them. */
        val default: RangeThresholds =
            requireNotNull(
                create(DEFAULT_URGENT_LOW_MGDL, DEFAULT_LOW_MGDL, DEFAULT_HIGH_MGDL, DEFAULT_VERY_HIGH_MGDL),
            ) { "Built-in defaults must be valid" }

        /**
         * The very-high boundary to assume when none was stored or sent.
         *
         * Settings saved by 1.1, and prefs from a 1.1 phone, carry no very-high value. Taking 260
         * unconditionally would invalidate a custom high of, say, 280, and the whole range would
         * then fall back to the defaults. Staying above the user's own high keeps their range.
         */
        fun defaultVeryHighFor(highMgdl: Int): Int = maxOf(DEFAULT_VERY_HIGH_MGDL, highMgdl + 1)

        /**
         * Builds a threshold set, or returns `null` when the values are inconsistent or fall
         * outside the plausible measurement range.
         *
         * Strict ordering is required rather than silently reordering: a user who typed the
         * numbers the wrong way round should be told, not quietly corrected into a range they
         * did not ask for.
         */
        fun create(
            urgentLowMgdl: Int,
            lowMgdl: Int,
            highMgdl: Int,
            veryHighMgdl: Int = defaultVeryHighFor(highMgdl),
        ): RangeThresholds? {
            val withinSensorRange =
                listOf(urgentLowMgdl, lowMgdl, highMgdl, veryHighMgdl).all {
                    it >= GlucoseReading.MIN_PLAUSIBLE_MGDL && it <= GlucoseReading.MAX_PLAUSIBLE_MGDL
                }
            if (!withinSensorRange) return null
            if (urgentLowMgdl >= lowMgdl) return null
            if (lowMgdl >= highMgdl) return null
            if (highMgdl >= veryHighMgdl) return null
            return RangeThresholds(urgentLowMgdl, lowMgdl, highMgdl, veryHighMgdl)
        }
    }
}
