package ro.smakeru.cgmglance.domain.glucose

import ro.smakeru.cgmglance.domain.time.Clock
import kotlin.math.abs

/**
 * Direction and pace of change, as a arrow.
 *
 * The official LinX app does not publish a trend arrow anywhere we can read — its notification
 * carries only value, unit and a localised age — so this is derived from our own history. That
 * makes it the one number in the app the user cannot check against the vendor's screen, which
 * is why the derivation is conservative and refuses to answer more readily than it guesses.
 *
 * No display symbol lives here. Unicode arrows render inconsistently across watch fonts, so the
 * presentation layer maps these to vector drawables.
 */
enum class TrendArrow {
    RISING_RAPIDLY,
    RISING,
    RISING_SLIGHTLY,
    STEADY,
    FALLING_SLIGHTLY,
    FALLING,
    FALLING_RAPIDLY,

    /** Not enough evidence to claim a direction. */
    UNKNOWN,
}

/**
 * Derives a [TrendArrow] by fitting a least squares line through recent readings.
 *
 * A two point difference was rejected deliberately. Sensor noise of ±5 to 10 mg/dL across a two
 * minute gap produces an apparent 3 mg/dL per minute on its own, so the arrow would swing
 * between extremes while glucose sat still. An arrow that lies often is worse than no arrow,
 * because the user cannot tell which readings to believe.
 */
class TrendCalculator(
    private val freshnessPolicy: FreshnessPolicy = FreshnessPolicy.default,
) {
    /**
     * Fits the trend over the readings ending at the most recent one.
     *
     * Returns [TrendArrow.UNKNOWN] rather than a weak guess when the evidence is thin: fewer
     * than [MIN_READINGS] points, a span shorter than [MIN_SPAN_MINUTES], or a newest reading
     * too old for [ReadingFreshness.allowsTrend].
     *
     * [readings] may arrive in any order and may contain entries older than the window; both
     * are handled here so callers are not required to maintain a sorted, trimmed buffer.
     */
    fun calculate(
        readings: List<GlucoseReading>,
        clock: Clock,
    ): TrendArrow {
        val newest = readings.maxByOrNull { it.timestampMillis } ?: return TrendArrow.UNKNOWN

        if (!freshnessPolicy.evaluate(newest, clock).allowsTrend) return TrendArrow.UNKNOWN

        // Anchored on the newest reading rather than on "now": the window should always cover
        // the last quarter hour of data, even when that data stopped arriving a few minutes ago.
        val windowStart = newest.timestampMillis - WINDOW_MINUTES * MILLIS_PER_MINUTE
        val window = readings.filter { it.timestampMillis >= windowStart }

        if (window.size < MIN_READINGS) return TrendArrow.UNKNOWN

        val oldest = window.minOf { it.timestampMillis }
        val spanMillis = newest.timestampMillis - oldest
        if (spanMillis < MIN_SPAN_MINUTES * MILLIS_PER_MINUTE) return TrendArrow.UNKNOWN

        val slope = slopePerMinute(window, newest.timestampMillis) ?: return TrendArrow.UNKNOWN
        return arrowFor(slope)
    }

    /**
     * Least squares slope in mg/dL per minute, or `null` when every reading shares a timestamp
     * and no line can be fitted.
     */
    private fun slopePerMinute(
        window: List<GlucoseReading>,
        originMillis: Long,
    ): Double? {
        val xs = window.map { (it.timestampMillis - originMillis) / MILLIS_PER_MINUTE.toDouble() }
        val ys = window.map { it.mgdl.toDouble() }

        val meanX = xs.average()
        val meanY = ys.average()

        var covariance = 0.0
        var variance = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - meanX
            covariance += dx * (ys[i] - meanY)
            variance += dx * dx
        }

        if (variance == 0.0) return null
        return covariance / variance
    }

    private fun arrowFor(slope: Double): TrendArrow =
        when {
            abs(slope) <= SLIGHT_THRESHOLD -> TrendArrow.STEADY
            slope > RAPID_THRESHOLD -> TrendArrow.RISING_RAPIDLY
            slope > MODERATE_THRESHOLD -> TrendArrow.RISING
            slope > SLIGHT_THRESHOLD -> TrendArrow.RISING_SLIGHTLY
            slope < -RAPID_THRESHOLD -> TrendArrow.FALLING_RAPIDLY
            slope < -MODERATE_THRESHOLD -> TrendArrow.FALLING
            else -> TrendArrow.FALLING_SLIGHTLY
        }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L

        /** How far back the fit reaches. Matches what commercial CGM systems use. */
        const val WINDOW_MINUTES: Int = 15

        /** Below this many points a line says more about noise than about glucose. */
        const val MIN_READINGS: Int = 3

        /** A fit across a shorter span cannot separate a real slope from sensor noise. */
        const val MIN_SPAN_MINUTES: Int = 6

        /** Boundary between steady and a slight change, in mg/dL per minute. */
        const val SLIGHT_THRESHOLD: Double = 1.0

        /** Boundary between a slight and a moderate change. */
        const val MODERATE_THRESHOLD: Double = 2.0

        /** Boundary between a moderate and a rapid change. */
        const val RAPID_THRESHOLD: Double = 3.0
    }
}
