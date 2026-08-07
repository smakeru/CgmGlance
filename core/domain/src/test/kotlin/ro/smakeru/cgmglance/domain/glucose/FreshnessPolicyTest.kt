package ro.smakeru.cgmglance.domain.glucose

import ro.smakeru.cgmglance.domain.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FreshnessPolicyTest {
    private val policy = FreshnessPolicy.default
    private val readingTime = 1_785_662_760_000L

    private val reading = requireNotNull(GlucoseReading.create(112, readingTime))

    /** Reader's clock, positioned a chosen distance after the reading. */
    private fun clockAt(secondsAfterReading: Long) = Clock { readingTime + secondsAfterReading * 1000 }

    @Test
    fun `a reading younger than two minutes is fresh`() {
        assertEquals(ReadingFreshness.FRESH, policy.evaluate(reading, clockAt(0)))
        assertEquals(ReadingFreshness.FRESH, policy.evaluate(reading, clockAt(119)))
    }

    @Test
    fun `the boundaries fall on the older state`() {
        // Exactly at a limit means the calmer state has been left behind, not that it lingers.
        assertEquals(ReadingFreshness.AGING, policy.evaluate(reading, clockAt(120)))
        assertEquals(ReadingFreshness.AGING, policy.evaluate(reading, clockAt(599)))
        assertEquals(ReadingFreshness.STALE, policy.evaluate(reading, clockAt(600)))
        assertEquals(ReadingFreshness.STALE, policy.evaluate(reading, clockAt(1199)))
        assertEquals(ReadingFreshness.NO_DATA, policy.evaluate(reading, clockAt(1200)))
    }

    @Test
    fun `a very old reading is no data rather than merely stale`() {
        assertEquals(ReadingFreshness.NO_DATA, policy.evaluate(reading, clockAt(60 * 60 * 8)))
    }

    @Test
    fun `no reading at all is no data`() {
        assertEquals(ReadingFreshness.NO_DATA, policy.evaluate(null, clockAt(0)))
        assertNull(policy.ageMillis(null, clockAt(0)))
    }

    @Test
    fun `small clock skew between phone and watch is tolerated`() {
        // The phone stamps the reading; the watch judges it. Their clocks are never identical,
        // and a reading that appears one minute in the future is jitter, not a fault.
        assertEquals(ReadingFreshness.FRESH, policy.evaluate(reading, clockAt(-60)))
        assertEquals(ReadingFreshness.FRESH, policy.evaluate(reading, clockAt(-120)))
    }

    @Test
    fun `large clock skew produces no data instead of a fabricated age`() {
        // Ten minutes into the future is not jitter. Any freshness claim here would be
        // invented, and inventing one is the exact failure this type exists to prevent.
        assertEquals(ReadingFreshness.NO_DATA, policy.evaluate(reading, clockAt(-600)))
    }

    @Test
    fun `age is reported in milliseconds and can be negative under skew`() {
        assertEquals(90_000L, policy.ageMillis(reading, clockAt(90)))
        assertEquals(-30_000L, policy.ageMillis(reading, clockAt(-30)))
    }

    @Test
    fun `trend is allowed only while the value still describes the present`() {
        assertEquals(true, ReadingFreshness.FRESH.allowsTrend)
        assertEquals(true, ReadingFreshness.AGING.allowsTrend)
        // Matches the rule that no trend is derived once the last reading passes ten minutes:
        // a regression fitted across a gap looks confident and means nothing.
        assertEquals(false, ReadingFreshness.STALE.allowsTrend)
        assertEquals(false, ReadingFreshness.NO_DATA.allowsTrend)
    }

    @Test
    fun `the configured limits are the agreed two ten and twenty minutes`() {
        assertEquals(2, FreshnessPolicy.FRESH_LIMIT_MINUTES)
        assertEquals(10, FreshnessPolicy.AGING_LIMIT_MINUTES)
        assertEquals(20, FreshnessPolicy.STALE_LIMIT_MINUTES)
    }
}
