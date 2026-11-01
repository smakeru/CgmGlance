package ro.smakeru.cgmglance.provider.dexcom

import org.junit.jupiter.api.Test
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Values and timestamps are invented, never copied from real health data.
 * The 1.9.0 Quick Glance fixture reproduces inspected resource IDs and unit formatting only.
 */
class DexcomNotificationParserTest {
    private val parser = DexcomNotificationParser()
    private val posted = 1_785_662_760_000L

    private fun parse(
        title: String? = null,
        text: String? = null,
    ) = parser.parse(title = title, text = text, postTimeMillis = posted)

    private fun views(vararg texts: String) = texts.map { DexcomTextView(null, it) }

    private fun custom(vararg texts: String) =
        parser.parse(
            bigContentViews = views(*texts),
            postTimeMillis = posted,
        )

    private fun failure(
        result: DexcomParseResult,
        reason: DexcomParseFailure,
    ) {
        assertEquals(reason, assertIs<DexcomParseResult.Failure>(result).reason)
    }

    @Test
    fun `standard title and text each support explicit units`() {
        val title = assertIs<DexcomParseResult.Success>(parse("124 mg/dL →", "Dexcom ONE+"))
        assertEquals(124, title.reading.mgdl)
        assertEquals(GlucoseUnit.MG_DL, title.unit)
        assertEquals(false, title.usedFallback)
        assertEquals(124, assertIs<DexcomParseResult.Success>(parse("Dexcom ONE+", "Glucose: 124 mg/dL")).reading.mgdl)
    }

    @Test
    fun `both decimal separators and nonbreaking spaces work without unit inference`() {
        for (text in listOf("6.2 mmol/L", "6,2 mmol/L", "6,2 mmol/L", "6.2 mmol/L")) {
            val result = assertIs<DexcomParseResult.Success>(parse(text = text))
            assertEquals(112, result.reading.mgdl)
            assertEquals(GlucoseUnit.MMOL_L, result.unit)
        }
        failure(parse("124"), DexcomParseFailure.UNIT_MISSING)
        failure(parse("6.2 mmol"), DexcomParseFailure.UNIT_MISSING)
        failure(parse("124 mg%"), DexcomParseFailure.UNIT_MISSING)
    }

    @Test
    fun `ONE plus 1_9_0 Quick Glance word joiner does not hide explicit mgdl unit`() {
        // Resource IDs/format from the installed layout; number and timestamp are invented.
        val fields =
            listOf(
                DexcomTextView("glucose_value_text", "124"),
                DexcomTextView("glucose_unit_of_measure_text", "mg/⁠dL"),
            )
        val result =
            parser.parse(
                bigContentViews = fields,
                contentViews = fields,
                postTimeMillis = posted,
            )
        val success = assertIs<DexcomParseResult.Success>(result)
        assertEquals(124, success.reading.mgdl)
        assertEquals(GlucoseUnit.MG_DL, success.unit)
        assertEquals(false, success.usedFallback)
        assertEquals(posted, success.reading.timestampMillis)
    }

    @Test
    fun `ONE plus word joiner is accepted in explicit mmol unit with either separator`() {
        for (value in listOf("6.2", "6,2")) {
            val fields =
                listOf(
                    DexcomTextView("glucose_value_text", value),
                    DexcomTextView("glucose_unit_of_measure_text", "mmol/⁠L"),
                )
            val result = parser.parse(contentViews = fields, postTimeMillis = posted)
            val success = assertIs<DexcomParseResult.Success>(result)
            assertEquals(112, success.reading.mgdl)
            assertEquals(GlucoseUnit.MMOL_L, success.unit)
        }
    }

    @Test
    fun `named Quick Glance fields are not confused with clocks or graph ticks`() {
        val result =
            parser.parse(
                contentViews =
                    listOf(
                        DexcomTextView("glucose_value_text", "124"),
                        DexcomTextView("glucose_unit_of_measure_text", "mg/⁠dL"),
                        DexcomTextView("graph_axis", "180"),
                        DexcomTextView("notification_time", "12:22"),
                    ),
                postTimeMillis = posted,
            )
        val success = assertIs<DexcomParseResult.Success>(result)
        assertEquals(124, success.reading.mgdl)
        assertEquals(false, success.usedFallback)
    }

    @Test
    fun `named Quick Glance fields still reject conflicting standard extras`() {
        val result =
            parser.parse(
                title = "125 mg/dL",
                contentViews =
                    listOf(
                        DexcomTextView("glucose_value_text", "124"),
                        DexcomTextView("glucose_unit_of_measure_text", "mg/⁠dL"),
                    ),
                postTimeMillis = posted,
            )
        failure(result, DexcomParseFailure.AMBIGUOUS_READING)
    }

    @Test
    fun `word joiner normalisation does not infer a missing or unknown unit`() {
        failure(custom("124", "⁠"), DexcomParseFailure.UNIT_MISSING)
        failure(custom("124", "mg/⁠unknown"), DexcomParseFailure.UNIT_MISSING)
    }

    @Test
    fun `separate values and units require exactly two complete fields in one representation`() {
        assertIs<DexcomParseResult.Success>(parse("124", "mg/dL"))
        assertIs<DexcomParseResult.Success>(custom("mg/dL", "124"))
        failure(custom("124", "mg/dL", "12:22"), DexcomParseFailure.NO_VALUE_FOUND)
        failure(custom("124", "mg/dL", "180"), DexcomParseFailure.NO_VALUE_FOUND)
        failure(custom("124", "mg/dL", "Dexcom ONE+"), DexcomParseFailure.NO_VALUE_FOUND)
        failure(
            parser.parse(title = "124", bigContentViews = views("mg/dL"), postTimeMillis = posted),
            DexcomParseFailure.NO_VALUE_FOUND,
        )
    }

    @Test
    fun `clock graph and prose numbers are not readings`() {
        for (text in listOf(
            "12:22 mg/dL",
            "08-02 12:12",
            "Target 124 mg/dL",
            "Graph 80 120 180 mg/dL",
            "124 mg/dL 5 minutes ago",
            "-124 mg/dL",
        )) {
            assertIs<DexcomParseResult.Failure>(parse(text))
        }
        failure(custom("12:22", "mg/dL"), DexcomParseFailure.NO_VALUE_FOUND)
        failure(
            parser.parse(bigContentViews = listOf(DexcomTextView("graph_axis", "124 mg/dL")), postTimeMillis = posted),
            DexcomParseFailure.NO_VALUE_FOUND,
        )
    }

    @Test
    fun `implausible and fractional mgdl values fail before rounding`() {
        for (text in listOf(
            "0 mg/dL",
            "601 mg/dL",
            "600.1 mg/dL",
            "124.5 mg/dL",
            "40 mmol/L",
            "0.1 mmol/L",
            "999999999999999999999999999999999999999999999 mg/dL",
        )) {
            failure(parse(text), DexcomParseFailure.VALUE_IMPLAUSIBLE)
        }
    }

    @Test
    fun `no reading states veto stale numeric values in any representation`() {
        for (state in listOf(
            "Signal loss",
            "Sensor warmup",
            "Sensor warm-up",
            "Sensor error",
            "Brief sensor issue",
            "No readings",
            "No glucose data",
            "Sensor failed",
            "LOW",
            "HIGH mg/dL",
            "--- mmol/L",
            "Pierdere semnal",
        )) {
            failure(parse("124 mg/dL", state), DexcomParseFailure.NO_CURRENT_READING)
            failure(custom("124 mg/dL", state), DexcomParseFailure.NO_CURRENT_READING)
            failure(
                parser.parse(title = "124 mg/dL", contentViews = views(state), postTimeMillis = posted),
                DexcomParseFailure.NO_CURRENT_READING,
            )
        }
    }

    @Test
    fun `custom layouts are fallback independent of resource id names`() {
        val result =
            assertIs<DexcomParseResult.Success>(
                parser.parse(
                    bigContentViews = listOf(DexcomTextView("renamed_value", "124 mg/dL")),
                    postTimeMillis = posted,
                ),
            )
        assertEquals(true, result.usedFallback)
        assertEquals(124, result.reading.mgdl)
        assertEquals(
            124,
            assertIs<DexcomParseResult.Success>(
                parser.parse(contentViews = views("124 mg/dL"), postTimeMillis = posted),
            ).reading.mgdl,
        )
    }

    @Test
    fun `duplicate forms agree but conflicting forms never choose the first number`() {
        val duplicate =
            assertIs<DexcomParseResult.Success>(
                parser.parse(title = "124 mg/dL", bigContentViews = views("124 mg/dL"), postTimeMillis = posted),
            )
        assertEquals(false, duplicate.usedFallback)
        failure(parse("124 mg/dL", "125 mg/dL"), DexcomParseFailure.AMBIGUOUS_READING)
        failure(custom("124 mg/dL", "125 mg/dL"), DexcomParseFailure.AMBIGUOUS_READING)
        failure(
            parser.parse(title = "124 mg/dL", contentViews = views("125 mg/dL"), postTimeMillis = posted),
            DexcomParseFailure.AMBIGUOUS_READING,
        )
    }

    @Test
    fun `uses only caller supplied posting time never a displayed time`() {
        val result = assertIs<DexcomParseResult.Success>(parse("124 mg/dL", "12:22"))
        assertEquals(posted, result.reading.timestampMillis)
        failure(parser.parse(title = "124 mg/dL", postTimeMillis = 0L), DexcomParseFailure.INVALID_TIMESTAMP)
        failure(parser.parse(title = "124 mg/dL", postTimeMillis = -1L), DexcomParseFailure.INVALID_TIMESTAMP)
    }

    @Test
    fun `only exact ONE plus package is supported`() {
        assertEquals(setOf("com.dexcom.d1plus"), DexcomNotificationParser.SUPPORTED_PACKAGES)
    }

    @Test
    fun `empty payload fails with an explicit reason`() {
        failure(parse(), DexcomParseFailure.UNIT_MISSING)
    }
}
