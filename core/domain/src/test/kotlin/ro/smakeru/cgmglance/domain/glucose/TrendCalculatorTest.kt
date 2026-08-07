package ro.smakeru.cgmglance.domain.glucose

import ro.smakeru.cgmglance.domain.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class TrendCalculatorTest {
    private val calculator = TrendCalculator()
    private val start = 1_785_600_000_000L
    private val minute = 60_000L

    /** Builds a series starting at [start], one reading every [everyMinutes]. */
    private fun series(
        vararg mgdl: Int,
        everyMinutes: Int = 2,
    ): List<GlucoseReading> =
        mgdl.mapIndexed { index, value ->
            requireNotNull(GlucoseReading.create(value, start + index * everyMinutes * minute))
        }

    /** A clock positioned [secondsAfter] the last reading of a series built by [series]. */
    private fun clockAfter(
        readings: List<GlucoseReading>,
        secondsAfter: Long = 30,
    ) = Clock { readings.maxOf { it.timestampMillis } + secondsAfter * 1000 }

    private fun trendOf(
        readings: List<GlucoseReading>,
        secondsAfter: Long = 30,
    ) = calculator.calculate(readings, clockAfter(readings, secondsAfter))

    @Test
    fun `a steady climb of two and a half per minute is a moderate rise`() {
        val readings = series(100, 105, 110, 115, 120, 125)
        assertEquals(TrendArrow.RISING, trendOf(readings))
    }

    @Test
    fun `a steep fall is reported as rapid`() {
        val readings = series(200, 190, 180, 170, 160)
        assertEquals(TrendArrow.FALLING_RAPIDLY, trendOf(readings))
    }

    @Test
    fun `noise around a flat line does not fabricate a trend`() {
        // This is the test that justifies fitting a line instead of subtracting two readings.
        // The series wobbles by up to 8 mg/dL while going nowhere; its last two points imply
        // +4 mg/dL per minute, which a two point difference would render as a rapid rise.
        val readings = series(112, 104, 118, 108, 116, 110, 118)

        val lastTwoRate =
            (readings[6].mgdl - readings[5].mgdl) / 2.0
        assertEquals(4.0, lastTwoRate, "fixture must actually mislead a two point difference")

        assertEquals(TrendArrow.STEADY, trendOf(readings))
    }

    @Test
    fun `threshold boundaries fall on the calmer arrow`() {
        // Exactly 1.0 per minute is still steady; exactly 2.0 is still only slight.
        assertEquals(TrendArrow.STEADY, trendOf(series(100, 102, 104, 106)))
        assertEquals(TrendArrow.RISING_SLIGHTLY, trendOf(series(100, 104, 108, 112)))
        assertEquals(TrendArrow.RISING, trendOf(series(100, 106, 112, 118)))
        assertEquals(TrendArrow.RISING_RAPIDLY, trendOf(series(100, 110, 120, 130)))
    }

    @Test
    fun `the falling side mirrors the rising side`() {
        assertEquals(TrendArrow.FALLING_SLIGHTLY, trendOf(series(112, 108, 104, 100)))
        assertEquals(TrendArrow.FALLING, trendOf(series(118, 112, 106, 100)))
    }

    @Test
    fun `too few readings produce no arrow`() {
        assertEquals(TrendArrow.UNKNOWN, trendOf(emptyList()))
        assertEquals(TrendArrow.UNKNOWN, trendOf(series(100)))
        assertEquals(TrendArrow.UNKNOWN, trendOf(series(100, 110)))
    }

    @Test
    fun `a span shorter than six minutes produces no arrow`() {
        // Three readings one minute apart span only two minutes: enough points, not enough time.
        assertEquals(TrendArrow.UNKNOWN, trendOf(series(100, 110, 120, everyMinutes = 1)))
    }

    @Test
    fun `a stale newest reading produces no arrow`() {
        val readings = series(100, 105, 110, 115)
        // Fifteen minutes after the last reading the value itself is already shown degraded;
        // a slope drawn from data that stopped arriving would look far more certain than it is.
        assertEquals(TrendArrow.UNKNOWN, trendOf(readings, secondsAfter = 15 * 60))
    }

    @Test
    fun `readings older than the window are ignored`() {
        val recent = series(112, 110, 114, 112)
        val ancient =
            requireNotNull(GlucoseReading.create(40, start - 40 * minute))

        // A steep climb out of a much older reading must not bend the current fit.
        assertEquals(TrendArrow.STEADY, trendOf(recent))
        assertEquals(TrendArrow.STEADY, calculator.calculate(recent + ancient, clockAfter(recent)))
    }

    @Test
    fun `input order does not matter`() {
        val readings = series(100, 105, 110, 115, 120, 125)
        assertEquals(TrendArrow.RISING, calculator.calculate(readings.reversed(), clockAfter(readings)))
        assertEquals(TrendArrow.RISING, calculator.calculate(readings.shuffled(), clockAfter(readings)))
    }

    @Test
    fun `readings that all share a timestamp cannot form a line`() {
        val sameInstant =
            List(4) { requireNotNull(GlucoseReading.create(100 + it, start)) }
        assertEquals(TrendArrow.UNKNOWN, calculator.calculate(sameInstant, Clock { start + 1000 }))
    }
}
