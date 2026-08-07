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
) {
    /** Classifies a reading against these boundaries. Boundary values count as in range. */
    fun classify(reading: GlucoseReading): GlucoseRange =
        when {
            reading.mgdl < urgentLowMgdl -> GlucoseRange.URGENT_LOW
            reading.mgdl < lowMgdl -> GlucoseRange.LOW
            reading.mgdl > highMgdl -> GlucoseRange.HIGH
            else -> GlucoseRange.IN_RANGE
        }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is RangeThresholds &&
                    urgentLowMgdl == other.urgentLowMgdl &&
                    lowMgdl == other.lowMgdl &&
                    highMgdl == other.highMgdl
            )

    override fun hashCode(): Int = (31 * urgentLowMgdl + lowMgdl) * 31 + highMgdl

    override fun toString(): String = "RangeThresholds($urgentLowMgdl/$lowMgdl/$highMgdl)"

    companion object {
        /** Internationally used target range for time in range reporting. */
        const val DEFAULT_LOW_MGDL: Int = 70

        /** Upper bound of that same target range. */
        const val DEFAULT_HIGH_MGDL: Int = 180

        /** Severe hypoglycaemia boundary, shown differently from an ordinary low. */
        const val DEFAULT_URGENT_LOW_MGDL: Int = 55

        /** The defaults, used until the user changes them. */
        val default: RangeThresholds =
            requireNotNull(
                create(DEFAULT_URGENT_LOW_MGDL, DEFAULT_LOW_MGDL, DEFAULT_HIGH_MGDL),
            ) { "Built-in defaults must be valid" }

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
        ): RangeThresholds? {
            val withinSensorRange =
                listOf(urgentLowMgdl, lowMgdl, highMgdl).all {
                    it >= GlucoseReading.MIN_PLAUSIBLE_MGDL && it <= GlucoseReading.MAX_PLAUSIBLE_MGDL
                }
            if (!withinSensorRange) return null
            if (urgentLowMgdl >= lowMgdl) return null
            if (lowMgdl >= highMgdl) return null
            return RangeThresholds(urgentLowMgdl, lowMgdl, highMgdl)
        }
    }
}
