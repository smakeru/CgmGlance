package ro.smakeru.cgmglance.provider.dexcom

import ro.smakeru.cgmglance.domain.glucose.GlucoseConversion
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit

/** Plain text only; no Android types or vendor resource-id assumptions. */
data class DexcomTextView(
    val id: String?,
    val text: String,
)

enum class DexcomParseFailure {
    NO_VALUE_FOUND,
    UNIT_MISSING,
    VALUE_IMPLAUSIBLE,
    AMBIGUOUS_READING,
    NO_CURRENT_READING,
    INVALID_TIMESTAMP,
}

sealed interface DexcomParseResult {
    data class Success(
        val reading: GlucoseReading,
        val unit: GlucoseUnit,
        /** True only for generic custom-view matching, not the verified Quick Glance named fields. */
        val usedFallback: Boolean,
    ) : DexcomParseResult

    data class Failure(
        val reason: DexcomParseFailure,
    ) : DexcomParseResult
}

/**
 * ONE+ text grammar. Version 1.9.0 Quick Glance IDs/unit formatting have been inspected on-device;
 * tests preserve that shape with invented values. Only an entire field containing a number with
 * explicit mg/dL or mmol/L (optionally a glucose label/trend arrow) is accepted. We never search
 * arbitrary prose for numbers. Separate value/unit fields are accepted only when they are the
 * sole two nonblank fields in the same representation. No cross-layout/extra stitching.
 * Multiple distinct candidates, including conflicting collapsed/expanded forms, fail closed.
 * Known no-reading states veto ALL representations, including otherwise valid stale values.
 * Unknown/localised vendor wording may require additional rejection rules after real captures.
 * The timestamp is the supplied system postTime, never displayed text or Notification.when.
 * It is NOT guaranteed to be sensor time: reposting can make an old value appear fresh.
 */
class DexcomNotificationParser {
    fun parse(
        title: String? = null,
        text: String? = null,
        bigContentViews: List<DexcomTextView> = emptyList(),
        contentViews: List<DexcomTextView> = emptyList(),
        postTimeMillis: Long,
    ): DexcomParseResult {
        val standard = listOfNotNull(title, text).map(::normalise).filter(String::isNotBlank)
        val expanded = usableTexts(bigContentViews)
        val collapsed = usableTexts(contentViews)
        // Inspect excluded time/graph views for state messages too: exclusion must not hide errors.
        val all = standard + (bigContentViews + contentViews).map { normalise(it.text) }
        if (all.any { unavailable.containsMatchIn(it) || noValue.matches(it) }) {
            return failure(DexcomParseFailure.NO_CURRENT_READING)
        }
        val timestamp = postTimeMillis
        if (timestamp <= 0L) return failure(DexcomParseFailure.INVALID_TIMESTAMP)

        val primary =
            candidates(standard) +
                quickGlanceCandidates(bigContentViews) + quickGlanceCandidates(contentViews)
        val fallback = candidates(expanded) + candidates(collapsed)
        val distinct = (primary + fallback).distinct()
        if (distinct.size > 1) return failure(DexcomParseFailure.AMBIGUOUS_READING)
        val candidate =
            distinct.singleOrNull()
                ?: return failure(
                    if (all.any { unit.containsMatchIn(it) }) {
                        DexcomParseFailure.NO_VALUE_FOUND
                    } else {
                        DexcomParseFailure.UNIT_MISSING
                    },
                )
        val value = candidate.value
        // Check before rounding/conversion: rounding must not rescue an out-of-range input.
        val mgdl =
            when (candidate.unit) {
                GlucoseUnit.MG_DL -> value
                GlucoseUnit.MMOL_L -> value * GlucoseConversion.MG_DL_PER_MMOL_L
            }
        if (!mgdl.isFinite() || mgdl < GlucoseReading.MIN_PLAUSIBLE_MGDL ||
            mgdl > GlucoseReading.MAX_PLAUSIBLE_MGDL ||
            (candidate.unit == GlucoseUnit.MG_DL && value % 1.0 != 0.0)
        ) {
            return failure(DexcomParseFailure.VALUE_IMPLAUSIBLE)
        }
        val canonical =
            when (candidate.unit) {
                GlucoseUnit.MG_DL -> value.toInt()
                GlucoseUnit.MMOL_L -> GlucoseConversion.mmolToMgdl(value)
            }
        val reading =
            GlucoseReading.create(canonical, timestamp)
                ?: return failure(DexcomParseFailure.VALUE_IMPLAUSIBLE)
        return DexcomParseResult.Success(reading, candidate.unit, candidate !in primary)
    }

    private data class Candidate(
        val value: Double,
        val unit: GlucoseUnit,
    )

    /** Named fields verified in notification_quick_glance from ONE+ 1.9.0 (17006). */
    private fun quickGlanceCandidates(views: List<DexcomTextView>): List<Candidate> {
        val value =
            views.singleOrNull { it.id == "glucose_value_text" }?.text?.let(::normalise)
                ?: return emptyList()
        val label =
            views.singleOrNull { it.id == "glucose_unit_of_measure_text" }?.text?.let(::normalise)
                ?: return emptyList()
        if (!numeric.matches(value) || !unit.matches(label)) return emptyList()
        return listOfNotNull(candidate(value, label))
    }

    private fun candidates(fields: List<String>): List<Candidate> {
        val inline =
            fields.mapNotNull { field ->
                reading.matchEntire(field)?.let { match -> candidate(match.groupValues[1], match.groupValues[2]) }
            }
        if (inline.isNotEmpty()) return inline
        // Deliberately refuse a number/unit pair beside a clock, graph ticks, age, app label,
        // or any other text. Without captured ids, positional association is not trustworthy.
        if (fields.size != 2) return emptyList()
        val number = fields.singleOrNull { numeric.matches(it) } ?: return emptyList()
        val label = fields.singleOrNull { unit.matches(it) } ?: return emptyList()
        return listOfNotNull(candidate(number, label))
    }

    private fun candidate(
        number: String,
        label: String,
    ): Candidate? {
        val value = number.replace(',', '.').toDoubleOrNull() ?: return null
        val resolved =
            when (label.lowercase().filterNot(Char::isWhitespace)) {
                "mg/dl" -> GlucoseUnit.MG_DL
                "mmol/l" -> GlucoseUnit.MMOL_L
                else -> return null
            }
        return Candidate(value, resolved)
    }

    private fun usableTexts(views: List<DexcomTextView>): List<String> =
        views
            .filterNot { excludedId.containsMatchIn(it.id.orEmpty()) }
            .map { normalise(it.text) }
            .filter(String::isNotBlank)

    private fun failure(reason: DexcomParseFailure) = DexcomParseResult.Failure(reason)

    companion object {
        /** Exact ONE+ package, confirmed externally against xDrip; no G6/G7/ONE aliases. */
        const val SUPPORTED_PACKAGE = "com.dexcom.d1plus"
        val SUPPORTED_PACKAGES: Set<String> = setOf(SUPPORTED_PACKAGE)

        // ONE+ 1.9.0 inserts WORD JOINER (U+2060) after the slash in both unit labels.
        // It prevents a visual line break; it is not part of the measurement unit.
        private fun normalise(raw: String): String =
            raw
                .replace("⁠", "")
                .replace(' ', ' ')
                .replace(' ', ' ')
                .trim()

        private const val NUMBER = "[0-9]+(?:[.,][0-9]+)?"
        private const val UNIT = "(?:mg\\s*/\\s*dL|mmol\\s*/\\s*L)"
        private val numeric = Regex(NUMBER)
        private val unit = Regex(UNIT, RegexOption.IGNORE_CASE)
        private val reading =
            Regex(
                "(?:\\b(?:current glucose|blood glucose|glucose)\\s*:?\\s*)?" +
                    "($NUMBER)\\s*($UNIT)(?:\\s*[↑↓→↗↘↟↡]{1,2})?",
                RegexOption.IGNORE_CASE,
            )
        private val excludedId =
            Regex(
                "time|clock|date|graph|chart|axis|tick|history|previous|target|threshold|limit",
                RegexOption.IGNORE_CASE,
            )
        private val unavailable =
            Regex(
                "\\b(?:signal\\s+loss|lost\\s+signal|no\\s+(?:readings?|glucose(?:\\s+(?:data|readings?))?)|" +
                    "sensor\\s+(?:warm[ -]?up|error|failed|failure|expired|ended|starting|unavailable)|" +
                    "warm[ -]?up|brief\\s+sensor\\s+issue|replace\\s+sensor|" +
                    "perte\\s+de\\s+signal|signalverlust|perdita\\s+di\\s+segnale|" +
                    "p[eé]rdida\\s+de\\s+se[nñ]al|pierdere\\s+semnal|eroare\\s+senzor|" +
                    "[îi]nc[ăa]lzire\\s+senzor)\\b",
                RegexOption.IGNORE_CASE,
            )
        private val noValue = Regex("(?:LOW|HIGH|LO|HI|[-–—?]{1,3})(?:\\s*$UNIT)?", RegexOption.IGNORE_CASE)
    }
}
