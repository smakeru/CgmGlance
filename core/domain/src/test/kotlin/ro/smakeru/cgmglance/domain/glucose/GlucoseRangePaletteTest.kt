package ro.smakeru.cgmglance.domain.glucose

import kotlin.test.Test
import kotlin.test.assertEquals

class GlucoseRangePaletteTest {
    private fun fresh(range: GlucoseRange?) = GlucoseRangePalette.colorFor(range, ReadingFreshness.FRESH)

    @Test
    fun `each range has its colour`() {
        assertEquals(GlucoseRangePalette.URGENT, fresh(GlucoseRange.URGENT_LOW))
        assertEquals(GlucoseRangePalette.LOW, fresh(GlucoseRange.LOW))
        assertEquals(GlucoseRangePalette.IN_RANGE, fresh(GlucoseRange.IN_RANGE))
        assertEquals(GlucoseRangePalette.HIGH, fresh(GlucoseRange.HIGH))
    }

    @Test
    fun `very high carries the same weight as urgent low`() {
        assertEquals(GlucoseRangePalette.URGENT, fresh(GlucoseRange.VERY_HIGH))
    }

    @Test
    fun `an aging reading keeps its colour`() {
        // Aging is the ordinary state; muting it would put the display in permanent warning.
        assertEquals(
            GlucoseRangePalette.IN_RANGE,
            GlucoseRangePalette.colorFor(GlucoseRange.IN_RANGE, ReadingFreshness.AGING),
        )
    }

    @Test
    fun `a reading that no longer describes the present is muted, whatever its range`() {
        for (range in GlucoseRange.entries) {
            assertEquals(GlucoseRangePalette.MUTED, GlucoseRangePalette.colorFor(range, ReadingFreshness.STALE))
            assertEquals(GlucoseRangePalette.MUTED, GlucoseRangePalette.colorFor(range, ReadingFreshness.NO_DATA))
        }
        assertEquals(GlucoseRangePalette.MUTED, fresh(null))
    }
}
