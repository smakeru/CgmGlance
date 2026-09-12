package ro.smakeru.cgmglance.phone

/**
 * Whether to write glucose out to Health Connect, and how often.
 *
 * Validated on construction the same way [ro.smakeru.cgmglance.domain.glucose.RangeThresholds]
 * is, and for the same reason: an interval that cannot be honoured should be impossible to hold,
 * not something every caller has to remember to check.
 *
 * Lives in the phone module rather than the shared domain. The watch neither writes health data
 * nor needs to know this exists, and putting it in `core:domain` would place it in the watch APK
 * for no benefit.
 */
class HealthExportSettings private constructor(
    /** Off means nothing is written at all, whatever permissions are in place. */
    val enabled: Boolean,
    val intervalMinutes: Int,
) {
    /** What [HealthExportPolicy] measures spacing with. */
    val intervalMillis: Long get() = intervalMinutes * 60_000L

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is HealthExportSettings &&
                    enabled == other.enabled &&
                    intervalMinutes == other.intervalMinutes
            )

    override fun hashCode(): Int = 31 * intervalMinutes + if (enabled) 1 else 0

    override fun toString(): String = "HealthExportSettings(enabled=$enabled, intervalMinutes=$intervalMinutes)"

    companion object {
        /** What was asked for, and what a new install gets. */
        const val DEFAULT_INTERVAL_MINUTES: Int = 15

        /**
         * Floor on the interval.
         *
         * The sensor produces a value about once a minute. Allowing that as an export interval
         * would put roughly 1,400 entries a day into a health record meant to hold measurements,
         * burying anything entered by hand and making the day's chart unreadable. Five minutes
         * is still far finer than anyone needs and keeps a mistyped `1` from doing that.
         */
        const val MIN_INTERVAL_MINUTES: Int = 5

        /** Ceiling, past which the export stops being a record of the day at all. */
        const val MAX_INTERVAL_MINUTES: Int = 180

        /** Enabled, every fifteen minutes. Writing still cannot happen without permission. */
        val default: HealthExportSettings =
            HealthExportSettings(enabled = true, intervalMinutes = DEFAULT_INTERVAL_MINUTES)

        /**
         * Builds settings, or returns `null` when the interval is outside what can be honoured.
         *
         * `null` rather than an exception, and rather than clamping: the settings screen uses it
         * to keep Save disabled while the field is out of range, which tells the user something.
         * Silently clamping a typed 300 down to 180 would not.
         */
        fun create(
            enabled: Boolean,
            intervalMinutes: Int,
        ): HealthExportSettings? {
            if (intervalMinutes < MIN_INTERVAL_MINUTES) return null
            if (intervalMinutes > MAX_INTERVAL_MINUTES) return null
            return HealthExportSettings(enabled, intervalMinutes)
        }
    }
}
