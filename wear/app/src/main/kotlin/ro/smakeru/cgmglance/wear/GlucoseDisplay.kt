package ro.smakeru.cgmglance.wear

import ro.smakeru.cgmglance.domain.glucose.GlucoseConversion
import ro.smakeru.cgmglance.domain.glucose.GlucoseDelta
import ro.smakeru.cgmglance.domain.glucose.GlucoseRange
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.ReadingFreshness
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import kotlin.math.roundToInt

/**
 * What every watch surface shows, already decided — and nothing that depends on language.
 *
 * The complication, the tile and the app must not be able to disagree about what to render, so
 * the decisions live here once. Wording lives in [WearTexts]: mixing the two would mean either
 * dragging a `Context` into this class and losing its tests, or leaving the app English-only.
 */
data class GlucoseDisplay(
    val value: String,
    val arrow: String,
    val unit: GlucoseUnit,
    val range: GlucoseRange?,
    val freshness: ReadingFreshness,
    val trend: TrendArrow,
    val ageMinutes: Long?,
    /** For the ranged-value complication. Null when there is nothing trustworthy to plot. */
    val gaugeValue: Float?,
    /** Signed difference against baseline reading from configured window (e.g. 10 min), or null. */
    val deltaMgdl: Int? = null,
) {
    /** True when there is a number worth showing at all. */
    val hasValue: Boolean get() = freshness != ReadingFreshness.NO_DATA

    val unitLabel: String get() = unit.label

    /** Formatted delta with sign according to unit (e.g. "+4", "-5", "+0.2"), or null. */
    val deltaFormatted: String?
        get() = deltaMgdl?.let { GlucoseDelta.formatValue(it, unit) }

    /** Formatted delta with parenthesis (e.g. "(+4)", "(-0.2)"), or null. */
    val deltaWithParens: String?
        get() = deltaMgdl?.let { GlucoseDelta.formatWithParens(it, unit) }

    /**
     * Value and arrow as one string, for slots that render a single piece of text.
     *
     * Watch faces treat a complication's title as optional and drop it whenever the slot is
     * narrow — an arc slot renders text only. Carrying the arrow in the title therefore made it
     * vanish on exactly the layouts this app was built for, so it travels in the text instead.
     *
     * No separating space: the slots that show this are the tight ones, and four characters fit
     * where five may not.
     */
    val compact: String get() = value + arrow

    companion object {
        /** Bounds of the ranged-value gauge. Wide enough that real readings never clip. */
        const val GAUGE_MIN: Float = 40f
        const val GAUGE_MAX: Float = 400f

        const val NO_VALUE: String = "--"

        /**
         * Arrow glyphs.
         *
         * Verified to render on One UI Watch. Not translated: they are symbols, and a locale
         * that reads right to left would need different glyphs rather than different words.
         */
        private val ARROWS =
            mapOf(
                TrendArrow.RISING_RAPIDLY to "⇈",
                TrendArrow.RISING to "↑",
                TrendArrow.RISING_SLIGHTLY to "↗",
                TrendArrow.STEADY to "→",
                TrendArrow.FALLING_SLIGHTLY to "↘",
                TrendArrow.FALLING to "↓",
                TrendArrow.FALLING_RAPIDLY to "⇊",
                TrendArrow.UNKNOWN to "",
            )

        fun of(
            reading: GlucoseReading?,
            trend: TrendArrow,
            unit: GlucoseUnit,
            range: GlucoseRange?,
            freshness: ReadingFreshness,
            ageMinutes: Long?,
            deltaMgdl: Int? = null,
        ): GlucoseDisplay {
            if (reading == null || freshness == ReadingFreshness.NO_DATA) {
                return GlucoseDisplay(
                    value = NO_VALUE,
                    arrow = "",
                    unit = unit,
                    range = null,
                    freshness = ReadingFreshness.NO_DATA,
                    trend = TrendArrow.UNKNOWN,
                    ageMinutes = null,
                    gaugeValue = null,
                    deltaMgdl = null,
                )
            }

            val shown = format(reading.mgdl, unit)

            return GlucoseDisplay(
                // A stale number keeps a question mark attached to it. It is still shown,
                // because hiding it would hide the only clue about what happened, but it must
                // never read as a statement about now.
                value = if (freshness == ReadingFreshness.STALE) "$shown?" else shown,
                // The arrow is dropped once the value stops describing the present; a direction
                // computed from data that stopped arriving is the most confident kind of lie.
                arrow = if (freshness.allowsTrend) ARROWS.getValue(trend) else "",
                unit = unit,
                range = range,
                freshness = freshness,
                trend = trend,
                ageMinutes = ageMinutes,
                // The gauge empties before the number disappears: an arc at two thirds full is
                // read as a fact about the present far more readily than a digit is.
                gaugeValue = if (freshness.allowsTrend) reading.mgdl.toFloat().coerceIn(GAUGE_MIN, GAUGE_MAX) else null,
                // Delta is also dropped if the reading is stale
                deltaMgdl = if (freshness.allowsTrend) deltaMgdl else null,
            )
        }

        private fun format(
            mgdl: Int,
            unit: GlucoseUnit,
        ): String =
            when (unit) {
                GlucoseUnit.MG_DL -> {
                    mgdl.toString()
                }

                GlucoseUnit.MMOL_L -> {
                    val mmol = GlucoseConversion.mgdlToMmol(mgdl)
                    val rounded = (mmol * 10).roundToInt() / 10.0
                    rounded.toString()
                }
            }
    }
}
