package ro.smakeru.cgmglance.provider.linx

import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit

/**
 * One text view lifted out of the LinX notification layout.
 *
 * [id] is the *simple* resource entry name — `tv_glucose`, not the fully qualified
 * `com.microtech.aidexx.mgdl:id/tv_glucose` — so the parser does not care which of the vendor's
 * package variants produced it.
 */
data class LinxTextView(
    val id: String?,
    val text: String,
)

/** Why a notification did not yield a reading. Surfaced in diagnostics, never to the watch. */
enum class LinxParseFailure {
    /** No text view carried a number inside the plausible glucose range. */
    NO_VALUE_FOUND,

    /** A number was found but the layout named no unit, so its scale is unknown. */
    UNIT_MISSING,

    /** The unit label was present but not one this build recognises. */
    UNIT_UNRECOGNISED,

    /** A value was read but rejected as implausible. */
    VALUE_IMPLAUSIBLE,
}

/** Outcome of parsing one notification. */
sealed interface LinxParseResult {
    data class Success(
        val reading: GlucoseReading,
        val unit: GlucoseUnit,
        /** True when the value came from the positional fallback rather than a named view. */
        val usedFallback: Boolean,
    ) : LinxParseResult

    data class Failure(
        val reason: LinxParseFailure,
    ) : LinxParseResult
}

/**
 * Extracts a glucose reading from the text of the LinX ongoing notification.
 *
 * Grounded in what the shipping app actually renders. LinX 2.5.0 builds its ongoing
 * notification from a custom `RemoteViews` layout whose text views are named:
 *
 * ```
 * tv_glucose  "240"
 * tv_unit     "mg/dL"
 * tv_time     "Chiar acum"
 * ```
 *
 * Two consequences shape this class.
 *
 * The value is **not** in `android.text`; that extra is null on the ongoing notification, so
 * there is no string to run a regular expression over. The caller inflates the layout and hands
 * over the resulting text views.
 *
 * `tv_time` is deliberately ignored. It is relative and localised — "Chiar acum" on a phone set
 * to English, because the vendor app carries its own language — so it cannot yield an instant.
 * The timestamp comes from the notification's own `postTime`, which the caller supplies.
 *
 * Parsing is two tier. The named lookup is stable across layout rearrangements and across the
 * vendor's rebrandings, since resource entry names survive both. When a vendor update renames
 * the ids, the positional fallback keeps the app working in a degraded but honest mode instead
 * of going blind: [LinxParseResult.Success.usedFallback] says which path was taken, so the
 * diagnostics screen can tell the user that an update needs attention.
 */
class LinxNotificationParser {
    fun parse(
        views: List<LinxTextView>,
        postTimeMillis: Long,
    ): LinxParseResult {
        val unitResult = resolveUnit(views)
        if (unitResult is UnitOutcome.Failed) return LinxParseResult.Failure(unitResult.reason)
        val unit = (unitResult as UnitOutcome.Resolved).unit

        val named = views.firstOrNull { it.id == ID_GLUCOSE }?.let { parseNumber(it.text) }
        if (named != null) {
            return toResult(named, unit, postTimeMillis, usedFallback = false)
        }

        // Fallback: the first view that reads as a number in range, excluding the unit label
        // itself and anything that looks like a clock time.
        val fallback =
            views
                .asSequence()
                .filter { it.id != ID_UNIT && it.id != ID_TIME }
                .filterNot { looksLikeClockTime(it.text) }
                .mapNotNull { parseNumber(it.text) }
                .firstOrNull { isPlausible(it, unit) }
                ?: return LinxParseResult.Failure(LinxParseFailure.NO_VALUE_FOUND)

        return toResult(fallback, unit, postTimeMillis, usedFallback = true)
    }

    private fun toResult(
        value: Double,
        unit: GlucoseUnit,
        postTimeMillis: Long,
        usedFallback: Boolean,
    ): LinxParseResult {
        val mgdl = toMgdl(value, unit)
        val reading =
            GlucoseReading.create(mgdl, postTimeMillis)
                ?: return LinxParseResult.Failure(LinxParseFailure.VALUE_IMPLAUSIBLE)
        return LinxParseResult.Success(reading, unit, usedFallback)
    }

    private sealed interface UnitOutcome {
        data class Resolved(
            val unit: GlucoseUnit,
        ) : UnitOutcome

        data class Failed(
            val reason: LinxParseFailure,
        ) : UnitOutcome
    }

    private fun resolveUnit(views: List<LinxTextView>): UnitOutcome {
        val label =
            views.firstOrNull { it.id == ID_UNIT }?.text
                ?: views.firstNotNullOfOrNull { view ->
                    view.text.takeIf { GlucoseUnit.fromLabel(it) != null }
                }
                ?: return UnitOutcome.Failed(LinxParseFailure.UNIT_MISSING)

        // Never assumed from the package name. The vendor ships one build per unit, so an
        // assumption that held for one user silently corrupts the reading for another.
        val unit =
            GlucoseUnit.fromLabel(label)
                ?: return UnitOutcome.Failed(LinxParseFailure.UNIT_UNRECOGNISED)
        return UnitOutcome.Resolved(unit)
    }

    /** Accepts both decimal separators: the vendor app localises its own numbers. */
    private fun parseNumber(raw: String): Double? {
        val cleaned = raw.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        if (!cleaned.all { it.isDigit() || it == '.' }) return null
        if (cleaned.count { it == '.' } > 1) return null
        return cleaned.toDoubleOrNull()
    }

    /** "12:22" and "08-02 12:12" must never be mistaken for a glucose value. */
    private fun looksLikeClockTime(raw: String): Boolean = raw.contains(':') || raw.contains('-')

    private fun isPlausible(
        value: Double,
        unit: GlucoseUnit,
    ): Boolean {
        val mgdl = toMgdl(value, unit)
        return mgdl >= GlucoseReading.MIN_PLAUSIBLE_MGDL && mgdl <= GlucoseReading.MAX_PLAUSIBLE_MGDL
    }

    private fun toMgdl(
        value: Double,
        unit: GlucoseUnit,
    ): Int =
        when (unit) {
            GlucoseUnit.MG_DL -> {
                value.toInt()
            }

            GlucoseUnit.MMOL_L -> {
                ro.smakeru.cgmglance.domain.glucose.GlucoseConversion
                    .mmolToMgdl(value)
            }
        }

    companion object {
        /** Package of the mg/dL build, as installed in Romania. */
        const val PACKAGE_MGDL: String = "com.microtech.aidexx.mgdl"

        /** Package of the mmol/L build. Same app, different unit compiled in. */
        const val PACKAGE_MMOL: String = "com.microtech.aidexx"

        /** Every vendor package this provider will accept notifications from. */
        val SUPPORTED_PACKAGES: Set<String> = setOf(PACKAGE_MGDL, PACKAGE_MMOL)

        const val ID_GLUCOSE: String = "tv_glucose"
        const val ID_UNIT: String = "tv_unit"
        const val ID_TIME: String = "tv_time"
    }
}
