package ro.smakeru.cgmglance.wear

import ro.smakeru.cgmglance.domain.glucose.GlucoseConversion
import ro.smakeru.cgmglance.domain.glucose.GlucoseRange
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.ReadingFreshness
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import kotlin.math.roundToInt

/**
 * Everything a watch surface needs, already decided.
 *
 * Plain Kotlin so the complication, the tile and the app cannot disagree about what to show,
 * and so the rules can be tested without rendering anything.
 */
data class GlucoseDisplay(
    val value: String,
    val arrow: String,
    val unitLabel: String,
    val range: GlucoseRange?,
    val freshness: ReadingFreshness,
    val contentDescription: String,
    /** For the ranged-value complication. Null when there is nothing trustworthy to plot. */
    val gaugeValue: Float?,
    /** Age of the reading, ready to show. Empty when there is nothing to say about it. */
    val ageLabel: String,
) {
    /** True when there is a number worth showing at all. */
    val hasValue: Boolean get() = freshness != ReadingFreshness.NO_DATA

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
         * Provisional. Watch fonts differ, and a glyph that renders as a blank box on one OEM
         * is worse than no arrow at all, so these are verified on a real device before being
         * trusted; the fallback is a set of vector drawables shipped with the app.
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

        private val SPOKEN_TREND =
            mapOf(
                TrendArrow.RISING_RAPIDLY to "rising rapidly",
                TrendArrow.RISING to "rising",
                TrendArrow.RISING_SLIGHTLY to "rising slowly",
                TrendArrow.STEADY to "steady",
                TrendArrow.FALLING_SLIGHTLY to "falling slowly",
                TrendArrow.FALLING to "falling",
                TrendArrow.FALLING_RAPIDLY to "falling rapidly",
                TrendArrow.UNKNOWN to "trend unknown",
            )

        fun of(
            reading: GlucoseReading?,
            trend: TrendArrow,
            unit: GlucoseUnit,
            range: GlucoseRange?,
            freshness: ReadingFreshness,
            ageMinutes: Long?,
        ): GlucoseDisplay {
            if (reading == null || freshness == ReadingFreshness.NO_DATA) {
                return GlucoseDisplay(
                    value = NO_VALUE,
                    arrow = "",
                    unitLabel = unit.label,
                    range = null,
                    freshness = ReadingFreshness.NO_DATA,
                    contentDescription = "No recent glucose reading",
                    gaugeValue = null,
                    ageLabel = "no recent data",
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
                unitLabel = unit.label,
                range = range,
                freshness = freshness,
                contentDescription = describe(shown, unit, trend, range, freshness, ageMinutes),
                // The gauge empties before the number disappears: an arc at two thirds full is
                // read as a fact about the present far more readily than a digit is.
                gaugeValue = if (freshness.allowsTrend) reading.mgdl.toFloat().coerceIn(GAUGE_MIN, GAUGE_MAX) else null,
                ageLabel = ageLabel(freshness, ageMinutes),
            )
        }

        private fun ageLabel(
            freshness: ReadingFreshness,
            ageMinutes: Long?,
        ): String =
            when {
                freshness == ReadingFreshness.STALE -> "may be out of date"
                ageMinutes == null -> ""
                ageMinutes <= 0L -> "just now"
                ageMinutes == 1L -> "1 min ago"
                else -> "$ageMinutes min ago"
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

        /**
         * What a screen reader says.
         *
         * Not a nicety. Long-standing diabetes carries a high rate of visual impairment, so for
         * part of the intended audience this string *is* the app.
         */
        private fun describe(
            shown: String,
            unit: GlucoseUnit,
            trend: TrendArrow,
            range: GlucoseRange?,
            freshness: ReadingFreshness,
            ageMinutes: Long?,
        ): String {
            val unitSpoken = if (unit == GlucoseUnit.MG_DL) "milligrams per decilitre" else "millimoles per litre"
            val age =
                when {
                    ageMinutes == null -> ""
                    ageMinutes <= 0L -> ", just now"
                    ageMinutes == 1L -> ", 1 minute ago"
                    else -> ", $ageMinutes minutes ago"
                }
            val warning = if (freshness == ReadingFreshness.STALE) ", reading may be out of date" else ""
            val rangeSpoken =
                when (range) {
                    GlucoseRange.URGENT_LOW -> ", urgent low"
                    GlucoseRange.LOW -> ", low"
                    GlucoseRange.HIGH -> ", high"
                    else -> ""
                }
            return "Glucose $shown $unitSpoken$rangeSpoken, ${SPOKEN_TREND.getValue(trend)}$age$warning"
        }
    }
}
