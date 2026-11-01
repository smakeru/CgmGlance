package ro.smakeru.cgmglance.wear

import android.content.Context
import ro.smakeru.cgmglance.domain.glucose.GlucoseRange
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.ReadingFreshness
import ro.smakeru.cgmglance.domain.glucose.TrendArrow

/**
 * Puts words to what [GlucoseDisplay] decided.
 *
 * Kept apart from the display itself so the decisions stay testable without a `Context` and the
 * wording stays translatable. Every surface uses this, so the complication, the tile and the app
 * cannot describe the same reading differently.
 */
class WearTexts(
    private val context: Context,
) {
    /** Age of the reading, ready to show. */
    fun ageLabel(display: GlucoseDisplay): String =
        when {
            display.freshness == ReadingFreshness.NO_DATA -> context.getString(R.string.age_no_recent_data)

            // Past this point the exact number of minutes is beside the point: what matters is
            // that the value may no longer describe the present.
            display.freshness == ReadingFreshness.STALE -> context.getString(R.string.age_may_be_out_of_date)

            else -> relativeAge(display.ageMinutes)
        }

    /**
     * What a screen reader says.
     *
     * Assembled from parts rather than one format string: the pieces are conditional, and a
     * single template with five optional slots would be untranslatable in practice.
     */
    fun describe(display: GlucoseDisplay): String {
        if (!display.hasValue) return context.getString(R.string.cd_no_reading)

        val parts = mutableListOf<String>()
        parts += context.getString(R.string.cd_glucose, display.value, spokenUnit(display.unit))
        spokenRange(display.range)?.let { parts += it }
        parts += context.getString(trendRes(display.trend))
        if (display.ageMinutes != null) parts += relativeAge(display.ageMinutes)
        if (display.freshness == ReadingFreshness.STALE) parts += context.getString(R.string.cd_out_of_date)

        return parts.joinToString(", ")
    }

    private fun relativeAge(ageMinutes: Long?): String {
        val minutes = ageMinutes?.toInt() ?: return ""
        if (minutes <= 0) return context.getString(R.string.age_just_now)
        return context.resources.getQuantityString(R.plurals.age_minutes_ago, minutes, minutes)
    }

    private fun spokenUnit(unit: GlucoseUnit): String =
        context.getString(
            when (unit) {
                GlucoseUnit.MG_DL -> R.string.unit_spoken_mgdl
                GlucoseUnit.MMOL_L -> R.string.unit_spoken_mmol
            },
        )

    private fun spokenRange(range: GlucoseRange?): String? =
        when (range) {
            GlucoseRange.URGENT_LOW -> context.getString(R.string.range_urgent_low)

            GlucoseRange.LOW -> context.getString(R.string.range_low)

            GlucoseRange.HIGH -> context.getString(R.string.range_high)

            GlucoseRange.VERY_HIGH -> context.getString(R.string.range_very_high)

            // In range needs no saying; a screen reader announcing "in range" on every glance
            // is noise the sighted display does not impose.
            else -> null
        }

    private fun trendRes(trend: TrendArrow): Int =
        when (trend) {
            TrendArrow.RISING_RAPIDLY -> R.string.trend_rising_rapidly
            TrendArrow.RISING -> R.string.trend_rising
            TrendArrow.RISING_SLIGHTLY -> R.string.trend_rising_slightly
            TrendArrow.STEADY -> R.string.trend_steady
            TrendArrow.FALLING_SLIGHTLY -> R.string.trend_falling_slightly
            TrendArrow.FALLING -> R.string.trend_falling
            TrendArrow.FALLING_RAPIDLY -> R.string.trend_falling_rapidly
            TrendArrow.UNKNOWN -> R.string.trend_unknown
        }
}
