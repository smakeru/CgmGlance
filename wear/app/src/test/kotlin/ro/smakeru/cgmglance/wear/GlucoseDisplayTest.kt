package ro.smakeru.cgmglance.wear

import org.junit.jupiter.api.Test
import ro.smakeru.cgmglance.domain.glucose.GlucoseRange
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.ReadingFreshness
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlucoseDisplayTest {
    private val reading = requireNotNull(GlucoseReading.create(112, 1_785_662_760_000L))

    private fun display(
        freshness: ReadingFreshness,
        trend: TrendArrow = TrendArrow.RISING_SLIGHTLY,
        unit: GlucoseUnit = GlucoseUnit.MG_DL,
        range: GlucoseRange? = GlucoseRange.IN_RANGE,
        ageMinutes: Long? = 1,
    ) = GlucoseDisplay.of(reading, trend, unit, range, freshness, ageMinutes)

    @Test
    fun `a fresh reading shows the value and the arrow plainly`() {
        val shown = display(ReadingFreshness.FRESH)

        assertEquals("112", shown.value)
        assertEquals("↗", shown.arrow)
        assertEquals(112f, shown.gaugeValue)
    }

    @Test
    fun `an aging reading is not degraded`() {
        // The fresh window is two minutes, so this state is the common one. Dimming here would
        // put the app in a permanent warning posture, and a warning that is always on is not a
        // warning at all — the visual budget for alarm is spent at STALE.
        val shown = display(ReadingFreshness.AGING)

        assertEquals("112", shown.value)
        assertEquals("↗", shown.arrow)
        assertEquals(112f, shown.gaugeValue)
    }

    @Test
    fun `a stale reading keeps the number but stops asserting it`() {
        val shown = display(ReadingFreshness.STALE)

        assertEquals("112?", shown.value)
        // No arrow: a direction drawn from data that stopped arriving is the most confident
        // kind of lie.
        assertEquals("", shown.arrow)
        // The gauge empties before the digits do, because an arc reads as a fact about now.
        assertNull(shown.gaugeValue)
        assertTrue(shown.contentDescription.contains("may be out of date"))
    }

    @Test
    fun `with no recent data there is nothing to show`() {
        val shown = display(ReadingFreshness.NO_DATA)

        assertEquals("--", shown.value)
        assertEquals("", shown.arrow)
        assertNull(shown.gaugeValue)
        assertEquals(false, shown.hasValue)
    }

    @Test
    fun `a missing reading is handled like no data`() {
        val shown =
            GlucoseDisplay.of(null, TrendArrow.UNKNOWN, GlucoseUnit.MG_DL, null, ReadingFreshness.FRESH, null)

        assertEquals("--", shown.value)
        assertEquals(false, shown.hasValue)
    }

    @Test
    fun `millimolar display carries one decimal`() {
        val shown = display(ReadingFreshness.FRESH, unit = GlucoseUnit.MMOL_L)

        assertEquals("6.2", shown.value)
        assertEquals("mmol/L", shown.unitLabel)
    }

    @Test
    fun `the spoken description states value, range, trend and age`() {
        val shown = display(ReadingFreshness.FRESH, range = GlucoseRange.LOW, ageMinutes = 3)

        // For part of the intended audience this string is the entire app.
        assertEquals(
            "Glucose 112 milligrams per decilitre, low, rising slowly, 3 minutes ago",
            shown.contentDescription,
        )
    }

    @Test
    fun `an unknown trend simply has no arrow`() {
        assertEquals("", display(ReadingFreshness.FRESH, trend = TrendArrow.UNKNOWN).arrow)
    }

    @Test
    fun `the compact form carries the arrow with the value`() {
        // Observed on a real Galaxy Watch: an arc slot renders a complication's text and drops
        // its title entirely, so an arrow placed in the title never appears.
        assertEquals("112↗", display(ReadingFreshness.FRESH).compact)
        assertEquals("112↗", display(ReadingFreshness.AGING).compact)
    }

    @Test
    fun `the compact form drops the arrow once the value stops describing the present`() {
        assertEquals("112?", display(ReadingFreshness.STALE).compact)
        assertEquals("--", display(ReadingFreshness.NO_DATA).compact)
    }

    @Test
    fun `the age label reads naturally at every scale`() {
        assertEquals("just now", display(ReadingFreshness.FRESH, ageMinutes = 0).ageLabel)
        assertEquals("1 min ago", display(ReadingFreshness.FRESH, ageMinutes = 1).ageLabel)
        assertEquals("8 min ago", display(ReadingFreshness.AGING, ageMinutes = 8).ageLabel)
    }

    @Test
    fun `a stale reading says so instead of stating an age`() {
        // Past this point the exact number of minutes is beside the point: what the user needs
        // to know is that the value may no longer describe the present.
        assertEquals("may be out of date", display(ReadingFreshness.STALE, ageMinutes = 14).ageLabel)
        assertEquals("no recent data", display(ReadingFreshness.NO_DATA).ageLabel)
    }

    @Test
    fun `extreme readings stay inside the gauge bounds`() {
        val high = requireNotNull(GlucoseReading.create(590, 1_785_662_760_000L))
        val shown =
            GlucoseDisplay.of(
                reading = high,
                trend = TrendArrow.STEADY,
                unit = GlucoseUnit.MG_DL,
                range = GlucoseRange.HIGH,
                freshness = ReadingFreshness.FRESH,
                ageMinutes = 0,
            )

        assertEquals(GlucoseDisplay.GAUGE_MAX, shown.gaugeValue)
        // Clipping the gauge must never clip the number itself.
        assertEquals("590", shown.value)
    }
}
