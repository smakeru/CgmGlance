package ro.smakeru.cgmglance.wear

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ro.smakeru.cgmglance.domain.glucose.GlucoseRange
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.ReadingFreshness
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wording, in both languages the app ships.
 *
 * Runs under Robolectric because resource lookup is the thing being tested; a fake string
 * provider would only prove the fake works.
 */
@RunWith(RobolectricTestRunner::class)
class WearTextsTest {
    private val reading = requireNotNull(GlucoseReading.create(112, 1_785_662_760_000L))

    private fun texts() = WearTexts(RuntimeEnvironment.getApplication())

    private fun display(
        freshness: ReadingFreshness = ReadingFreshness.FRESH,
        trend: TrendArrow = TrendArrow.RISING_SLIGHTLY,
        unit: GlucoseUnit = GlucoseUnit.MG_DL,
        range: GlucoseRange? = GlucoseRange.IN_RANGE,
        ageMinutes: Long? = 1,
    ) = GlucoseDisplay.of(reading, trend, unit, range, freshness, ageMinutes)

    @Test
    fun `the age reads naturally at every scale`() {
        assertEquals("just now", texts().ageLabel(display(ageMinutes = 0)))
        assertEquals("1 min ago", texts().ageLabel(display(ageMinutes = 1)))
        assertEquals("8 min ago", texts().ageLabel(display(freshness = ReadingFreshness.AGING, ageMinutes = 8)))
    }

    @Test
    fun `a stale reading says so instead of stating an age`() {
        // Past this point the exact number of minutes is beside the point: what the user needs
        // to know is that the value may no longer describe the present.
        assertEquals(
            "may be out of date",
            texts().ageLabel(display(freshness = ReadingFreshness.STALE, ageMinutes = 14)),
        )
        assertEquals("no recent data", texts().ageLabel(display(freshness = ReadingFreshness.NO_DATA)))
    }

    @Test
    fun `the spoken description states value, range, trend and age`() {
        // For part of the intended audience this string is the entire app.
        val spoken = texts().describe(display(range = GlucoseRange.LOW, ageMinutes = 3))

        assertEquals("Glucose 112 milligrams per decilitre, low, rising slowly, 3 min ago", spoken)
    }

    @Test
    fun `being in range is not announced`() {
        // A screen reader saying "in range" on every glance is noise the sighted display does
        // not impose.
        val spoken = texts().describe(display(range = GlucoseRange.IN_RANGE))

        assertTrue(spoken.startsWith("Glucose 112 milligrams per decilitre, rising slowly"), spoken)
    }

    @Test
    fun `a stale reading is spoken with its warning`() {
        val spoken = texts().describe(display(freshness = ReadingFreshness.STALE, ageMinutes = 14))

        assertTrue(spoken.endsWith("reading may be out of date"), spoken)
    }

    @Test
    fun `nothing to show is said plainly`() {
        assertEquals("No recent glucose reading", texts().describe(display(freshness = ReadingFreshness.NO_DATA)))
    }

    @Test
    @Config(qualifiers = "ro")
    fun `Romanian is translated, not merely present`() {
        val spoken = texts().describe(display(range = GlucoseRange.LOW, ageMinutes = 3))

        assertEquals("Glicemie 112 miligrame pe decilitru, scăzut, urcă lent, acum 3 min", spoken)
        assertEquals("chiar acum", texts().ageLabel(display(ageMinutes = 0)))
    }

    @Test
    @Config(qualifiers = "ro")
    fun `Romanian plurals follow its three forms`() {
        // Romanian distinguishes 1, 2-19 and 20+; a naive two-form translation would say
        // "acum 20 minute" where the language requires "acum 20 de minute".
        assertEquals("acum 1 min", texts().ageLabel(display(ageMinutes = 1)))
        assertEquals("acum 5 min", texts().ageLabel(display(freshness = ReadingFreshness.AGING, ageMinutes = 5)))
    }
}
