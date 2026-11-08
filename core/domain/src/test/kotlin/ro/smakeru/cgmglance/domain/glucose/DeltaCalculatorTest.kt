package ro.smakeru.cgmglance.domain.glucose

import ro.smakeru.cgmglance.domain.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeltaCalculatorTest {
    private val calculator = DeltaCalculator()
    private val baseNow = 1_785_662_760_000L

    @Test
    fun `calculates 10-minute delta accurately from readings`() {
        val now = baseNow
        val clock = Clock { now }

        val readings =
            listOf(
                GlucoseReading.create(100, now - 10 * 60_000L)!!,
                GlucoseReading.create(105, now - 5 * 60_000L)!!,
                GlucoseReading.create(112, now)!!,
            )

        val delta = calculator.calculate(readings, clock, targetIntervalMinutes = 10)
        assertNotNull(delta)
        assertEquals(12, delta.deltaMgdl)
        assertEquals(10, delta.intervalMinutes)
        assertEquals("+12", delta.format(GlucoseUnit.MG_DL))
        assertEquals("(+12)", delta.formatWithParens(GlucoseUnit.MG_DL))
    }

    @Test
    fun `calculates 5-minute delta accurately from readings`() {
        val now = baseNow
        val clock = Clock { now }

        val readings =
            listOf(
                GlucoseReading.create(110, now - 10 * 60_000L)!!,
                GlucoseReading.create(105, now - 5 * 60_000L)!!,
                GlucoseReading.create(100, now)!!,
            )

        val delta = calculator.calculate(readings, clock, targetIntervalMinutes = 5)
        assertNotNull(delta)
        assertEquals(-5, delta.deltaMgdl)
        assertEquals(5, delta.intervalMinutes)
        assertEquals("-5", delta.format(GlucoseUnit.MG_DL))
        assertEquals("(-5)", delta.formatWithParens(GlucoseUnit.MG_DL))
    }

    @Test
    fun `formats negative and zero deltas in mmol per liter`() {
        val zeroDelta = GlucoseDelta(0, 10)
        assertEquals("0.0", zeroDelta.format(GlucoseUnit.MMOL_L))

        val positiveDelta = GlucoseDelta(18, 10) // ~1.0 mmol/L
        assertEquals("+1.0", positiveDelta.format(GlucoseUnit.MMOL_L))

        val negativeDelta = GlucoseDelta(-9, 10) // ~-0.5 mmol/L
        assertEquals("-0.5", negativeDelta.format(GlucoseUnit.MMOL_L))
        assertEquals("-0,5", negativeDelta.format(GlucoseUnit.MMOL_L, decimalSeparator = ','))
        assertEquals("(-0,5)", negativeDelta.formatWithParens(GlucoseUnit.MMOL_L, decimalSeparator = ','))
    }

    @Test
    fun `returns null when fewer than 2 readings exist`() {
        val now = baseNow
        val clock = Clock { now }
        val single = listOf(GlucoseReading.create(100, now)!!)

        assertNull(calculator.calculate(emptyList(), clock))
        assertNull(calculator.calculate(single, clock))
    }

    @Test
    fun `returns null when latest reading is stale`() {
        val now = baseNow
        val clock = Clock { now }
        // 25 minutes old reading (stale limit is 20 min)
        val staleTime = now - 25 * 60_000L
        val readings =
            listOf(
                GlucoseReading.create(100, staleTime - 10 * 60_000L)!!,
                GlucoseReading.create(112, staleTime)!!,
            )

        assertNull(calculator.calculate(readings, clock))
    }

    @Test
    fun `returns null when baseline gap is outside tolerance window`() {
        val now = baseNow
        val clock = Clock { now }
        // For 10 min, tolerance 40% allows 6..14 min. Baseline at 16 min is outside tolerance.
        val readingsOutside =
            listOf(
                GlucoseReading.create(100, now - 16 * 60_000L)!!,
                GlucoseReading.create(112, now)!!,
            )
        assertNull(calculator.calculate(readingsOutside, clock, targetIntervalMinutes = 10))

        // Baseline at 12 min is inside tolerance
        val readingsInside =
            listOf(
                GlucoseReading.create(100, now - 12 * 60_000L)!!,
                GlucoseReading.create(112, now)!!,
            )
        assertNotNull(calculator.calculate(readingsInside, clock, targetIntervalMinutes = 10))
    }
}
