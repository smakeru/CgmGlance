package ro.smakeru.cgmglance.domain.glucose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RangeThresholdsTest {
    private val someTimestamp = 1_785_662_760_281L

    private fun reading(mgdl: Int) = requireNotNull(GlucoseReading.create(mgdl, someTimestamp))

    @Test
    fun `defaults are the internationally used target range`() {
        assertEquals(55, RangeThresholds.default.urgentLowMgdl)
        assertEquals(70, RangeThresholds.default.lowMgdl)
        assertEquals(180, RangeThresholds.default.highMgdl)
    }

    @Test
    fun `classifies readings across the whole scale`() {
        val thresholds = RangeThresholds.default

        assertEquals(GlucoseRange.URGENT_LOW, thresholds.classify(reading(40)))
        assertEquals(GlucoseRange.LOW, thresholds.classify(reading(62)))
        assertEquals(GlucoseRange.IN_RANGE, thresholds.classify(reading(112)))
        assertEquals(GlucoseRange.HIGH, thresholds.classify(reading(250)))
    }

    @Test
    fun `boundary values fall on the safer side`() {
        val thresholds = RangeThresholds.default

        // Exactly at a boundary counts as the calmer state: 70 is in range, not low.
        assertEquals(GlucoseRange.LOW, thresholds.classify(reading(69)))
        assertEquals(GlucoseRange.IN_RANGE, thresholds.classify(reading(70)))
        assertEquals(GlucoseRange.IN_RANGE, thresholds.classify(reading(180)))
        assertEquals(GlucoseRange.HIGH, thresholds.classify(reading(181)))

        // Same rule one level down: 55 is a low, 54 is an urgent low.
        assertEquals(GlucoseRange.URGENT_LOW, thresholds.classify(reading(54)))
        assertEquals(GlucoseRange.LOW, thresholds.classify(reading(55)))
    }

    @Test
    fun `accepts a custom range the user configured`() {
        val tighter = RangeThresholds.create(urgentLowMgdl = 50, lowMgdl = 80, highMgdl = 140)

        assertNotNull(tighter)
        assertEquals(GlucoseRange.HIGH, tighter.classify(reading(150)))
        assertEquals(GlucoseRange.IN_RANGE, tighter.classify(reading(150 - 20)))
    }

    @Test
    fun `rejects thresholds that are out of order rather than reordering them`() {
        // Silently swapping a user's inverted input would put them in a range they never chose.
        assertNull(RangeThresholds.create(urgentLowMgdl = 90, lowMgdl = 70, highMgdl = 180))
        assertNull(RangeThresholds.create(urgentLowMgdl = 55, lowMgdl = 200, highMgdl = 180))
    }

    @Test
    fun `rejects thresholds that collapse into each other`() {
        assertNull(RangeThresholds.create(urgentLowMgdl = 70, lowMgdl = 70, highMgdl = 180))
        assertNull(RangeThresholds.create(urgentLowMgdl = 55, lowMgdl = 180, highMgdl = 180))
    }

    @Test
    fun `rejects thresholds outside what a sensor can report`() {
        assertNull(RangeThresholds.create(urgentLowMgdl = 5, lowMgdl = 70, highMgdl = 180))
        assertNull(RangeThresholds.create(urgentLowMgdl = 55, lowMgdl = 70, highMgdl = 900))
    }
}
