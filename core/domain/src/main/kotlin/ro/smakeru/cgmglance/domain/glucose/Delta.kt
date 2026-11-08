package ro.smakeru.cgmglance.domain.glucose

import ro.smakeru.cgmglance.domain.time.Clock
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The signed numeric difference in glucose between the latest reading and a baseline reading
 * from approximately [intervalMinutes] ago.
 *
 * Sits alongside the [TrendArrow]: the arrow provides directional velocity filtered of sensor noise,
 * while delta provides exact magnitude context (e.g. +4 mg/dL vs +12 mg/dL over 10 minutes).
 */
data class GlucoseDelta(
    val deltaMgdl: Int,
    val intervalMinutes: Int,
) {
    /**
     * Formats the signed delta according to the target unit.
     * Examples in mg/dL: "+4", "-5", "0"
     * Examples in mmol/L: "+0.2", "-0.3", "0.0"
     */
    fun format(unit: GlucoseUnit): String =
        when (unit) {
            GlucoseUnit.MG_DL -> {
                if (deltaMgdl > 0) "+$deltaMgdl" else "$deltaMgdl"
            }

            GlucoseUnit.MMOL_L -> {
                val mmol = GlucoseConversion.mgdlToMmol(deltaMgdl)
                val rounded = (mmol * 10.0).roundToInt() / 10.0
                if (rounded > 0.0) {
                    "+$rounded"
                } else if (rounded == 0.0) {
                    "0.0"
                } else {
                    "$rounded"
                }
            }
        }

    /**
     * Compact display with parenthesis, e.g. "(+4)" or "(-0.2)"
     */
    fun formatWithParens(unit: GlucoseUnit): String = "(${format(unit)})"
}

/**
 * Calculates the glucose delta over a configurable time window.
 */
class DeltaCalculator(
    private val freshnessPolicy: FreshnessPolicy = FreshnessPolicy.default,
) {
    /**
     * Finds the reading closest to [targetIntervalMinutes] ago and computes the signed difference.
     *
     * Returns null when:
     * - Fewer than 2 readings exist
     * - The latest reading is stale or invalid according to [freshnessPolicy]
     * - No suitable baseline exists within the acceptable tolerance window
     */
    fun calculate(
        readings: List<GlucoseReading>,
        clock: Clock,
        targetIntervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    ): GlucoseDelta? {
        if (readings.size < 2) return null
        val newest = readings.maxByOrNull { it.timestampMillis } ?: return null

        if (!freshnessPolicy.evaluate(newest, clock).allowsTrend) return null

        val intervalMs = targetIntervalMinutes * MILLIS_PER_MINUTE
        val targetTimestamp = newest.timestampMillis - intervalMs

        // Candidate baseline readings strictly older than newest
        val candidates = readings.filter { it.timestampMillis < newest.timestampMillis }
        if (candidates.isEmpty()) return null

        // Find candidate closest to target timestamp
        val baseline = candidates.minByOrNull { abs(it.timestampMillis - targetTimestamp) } ?: return null

        val actualGapMs = newest.timestampMillis - baseline.timestampMillis

        // Gap must not exceed the maximum allowed gap (20 min)
        if (actualGapMs > MAX_GAP_MINUTES * MILLIS_PER_MINUTE) return null

        // Must have at least a minimal gap to prevent comparing identical back-to-back reposts
        val minGapMs = (targetIntervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES) * MILLIS_PER_MINUTE) / 2
        if (actualGapMs < minGapMs.coerceAtLeast(MIN_TIME_GAP_MILLIS)) return null

        return GlucoseDelta(
            deltaMgdl = newest.mgdl - baseline.mgdl,
            intervalMinutes = targetIntervalMinutes,
        )
    }

    companion object {
        const val DEFAULT_INTERVAL_MINUTES: Int = 10
        const val MIN_INTERVAL_MINUTES: Int = 3
        const val MAX_INTERVAL_MINUTES: Int = 30
        const val MAX_GAP_MINUTES: Int = 20
        private const val MILLIS_PER_MINUTE: Long = 60_000L
        private const val MIN_TIME_GAP_MILLIS: Long = 60_000L // at least 1 minute apart
    }
}
