package ro.smakeru.cgmglance.provider.linx

import org.junit.jupiter.api.Test
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The layouts below reproduce the *structure* captured from LinX 2.5.0 on a real device. The
 * numbers are invented: real readings are health data and never enter the repository.
 */
class LinxNotificationParserTest {
    private val parser = LinxNotificationParser()
    private val postTime = 1_785_662_760_000L

    /** The shipping layout: three named text views, mg/dL build. */
    private fun captured(
        value: String = "112",
        unit: String = "mg/dL",
        time: String = "Chiar acum",
    ) = listOf(
        LinxTextView(LinxNotificationParser.ID_GLUCOSE, value),
        LinxTextView(LinxNotificationParser.ID_UNIT, unit),
        LinxTextView(LinxNotificationParser.ID_TIME, time),
    )

    private fun success(views: List<LinxTextView>) = assertIs<LinxParseResult.Success>(parser.parse(views, postTime))

    private fun failure(views: List<LinxTextView>) = assertIs<LinxParseResult.Failure>(parser.parse(views, postTime))

    @Test
    fun `reads the layout the shipping app renders`() {
        val result = success(captured())

        assertEquals(112, result.reading.mgdl)
        assertEquals(GlucoseUnit.MG_DL, result.unit)
        assertEquals(false, result.usedFallback)
    }

    @Test
    fun `takes the timestamp from the notification, never from the localised age text`() {
        // "Chiar acum" is Romanian even on an English phone: the vendor app carries its own
        // language, so that view can never yield an instant.
        val result = success(captured(time = "acum 7 minute"))

        assertEquals(postTime, result.reading.timestampMillis)
    }

    @Test
    fun `reads the millimolar build without assuming the scale from the package`() {
        val result = success(captured(value = "6,2", unit = "mmol/L"))

        // 6.2 mmol/L is 112 mg/dL. Both decimal separators must work: the vendor localises
        // its own numbers independently of the phone's locale.
        assertEquals(112, result.reading.mgdl)
        assertEquals(GlucoseUnit.MMOL_L, result.unit)
        assertEquals(112, success(captured(value = "6.2", unit = "mmol/L")).reading.mgdl)
    }

    @Test
    fun `refuses to guess when the layout names no unit`() {
        // Guessing turns 6.2 mmol/L into 6 mg/dL: a normal value rendered as a lethal one.
        val views = listOf(LinxTextView(LinxNotificationParser.ID_GLUCOSE, "112"))

        assertEquals(LinxParseFailure.UNIT_MISSING, failure(views).reason)
    }

    @Test
    fun `refuses a unit label it does not recognise`() {
        assertEquals(LinxParseFailure.UNIT_UNRECOGNISED, failure(captured(unit = "mg%")).reason)
    }

    @Test
    fun `rejects an implausible value rather than displaying it`() {
        assertEquals(LinxParseFailure.VALUE_IMPLAUSIBLE, failure(captured(value = "2026")).reason)
        assertEquals(LinxParseFailure.VALUE_IMPLAUSIBLE, failure(captured(value = "0")).reason)
    }

    @Test
    fun `falls back to position when a vendor update renames the ids`() {
        // Degraded but working beats blind. The result flags the fallback so diagnostics can
        // tell the user an update needs attention.
        val renamed =
            listOf(
                LinxTextView("tv_value_v3", "112"),
                LinxTextView("tv_unit_v3", "mg/dL"),
                LinxTextView("tv_when_v3", "Chiar acum"),
            )
        val result = success(renamed)

        assertEquals(112, result.reading.mgdl)
        assertEquals(true, result.usedFallback)
    }

    @Test
    fun `the fallback never mistakes a clock for a glucose value`() {
        val renamed =
            listOf(
                LinxTextView(null, "12:22"),
                LinxTextView(null, "08-02 12:12"),
                LinxTextView(null, "112"),
                LinxTextView(null, "mg/dL"),
            )
        val result = success(renamed)

        assertEquals(112, result.reading.mgdl)
    }

    @Test
    fun `the fallback skips numbers outside the plausible range`() {
        // A layout that also shows sensor day, battery percent or a session counter must not
        // have one of those picked up as glucose.
        val renamed =
            listOf(
                LinxTextView(null, "15"),
                LinxTextView(null, "112"),
                LinxTextView(null, "mg/dL"),
            )

        assertEquals(112, success(renamed).reading.mgdl)
    }

    @Test
    fun `reports no value when nothing in the layout is a number`() {
        val views =
            listOf(
                LinxTextView(LinxNotificationParser.ID_UNIT, "mg/dL"),
                LinxTextView(null, "Se incarca"),
            )

        assertEquals(LinxParseFailure.NO_VALUE_FOUND, failure(views).reason)
    }

    @Test
    fun `both vendor package variants are accepted`() {
        assertEquals(true, LinxNotificationParser.PACKAGE_MGDL in LinxNotificationParser.SUPPORTED_PACKAGES)
        assertEquals(true, LinxNotificationParser.PACKAGE_MMOL in LinxNotificationParser.SUPPORTED_PACKAGES)
    }
}
