package ro.smakeru.cgmglance.domain.glucose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Values used here are invented. Real readings captured from a device are health data and never
 * enter the repository, so fixtures reproduce the *format* of real data, not its content.
 */
class GlucoseReadingTest {
    private val someTimestamp = 1_785_662_760_281L

    @Test
    fun `accepts an ordinary reading`() {
        val reading = GlucoseReading.create(mgdl = 112, timestampMillis = someTimestamp)

        assertNotNull(reading)
        assertEquals(112, reading.mgdl)
        assertEquals(someTimestamp, reading.timestampMillis)
    }

    @Test
    fun `accepts the extremes of the plausible range`() {
        assertNotNull(GlucoseReading.create(GlucoseReading.MIN_PLAUSIBLE_MGDL, someTimestamp))
        assertNotNull(GlucoseReading.create(GlucoseReading.MAX_PLAUSIBLE_MGDL, someTimestamp))
    }

    @Test
    fun `rejects values outside the plausible range`() {
        // A person really can be at 25 mg per dL. A parser that grabbed the wrong text field
        // produces 0, 2026 or 1440 — those are what this guard exists to stop.
        assertNull(GlucoseReading.create(GlucoseReading.MIN_PLAUSIBLE_MGDL - 1, someTimestamp))
        assertNull(GlucoseReading.create(GlucoseReading.MAX_PLAUSIBLE_MGDL + 1, someTimestamp))
        assertNull(GlucoseReading.create(0, someTimestamp))
        assertNull(GlucoseReading.create(-112, someTimestamp))
        assertNull(GlucoseReading.create(2026, someTimestamp))
    }

    @Test
    fun `rejects a missing or nonsensical timestamp`() {
        // Zero is what an uninitialised long looks like, and it would make every reading
        // appear to be from 1970 — permanently stale rather than wrongly fresh, but still wrong.
        assertNull(GlucoseReading.create(112, timestampMillis = 0L))
        assertNull(GlucoseReading.create(112, timestampMillis = -1L))
    }

    @Test
    fun `readings with the same value and time are equal`() {
        val first = GlucoseReading.create(112, someTimestamp)
        val second = GlucoseReading.create(112, someTimestamp)
        val later = GlucoseReading.create(112, someTimestamp + 1)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        // Two identical values at different times are distinct readings. The sync layer relies
        // on this: an unchanged value still has to propagate, because its freshness changed.
        assertNotNull(later)
        assertEquals(false, first == later)
    }
}
