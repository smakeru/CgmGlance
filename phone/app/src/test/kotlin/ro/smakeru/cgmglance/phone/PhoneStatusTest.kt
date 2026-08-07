package ro.smakeru.cgmglance.phone

import org.junit.jupiter.api.Test
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.ReadingFreshness
import kotlin.test.assertEquals

class PhoneStatusTest {
    private val reading = requireNotNull(GlucoseReading.create(112, 1_785_738_000_000L))

    private fun status(
        permissionGranted: Boolean = true,
        lastReading: GlucoseReading? = reading,
        freshness: ReadingFreshness = ReadingFreshness.FRESH,
        watchConnected: Boolean = true,
        usedFallbackParser: Boolean = false,
    ) = PhoneStatus(
        permissionGranted = permissionGranted,
        lastReading = lastReading,
        freshness = freshness,
        ageMinutes = 1,
        watchConnected = watchConnected,
        lastSentMillis = 1_785_738_000_000L,
        usedFallbackParser = usedFallbackParser,
    )

    @Test
    fun `everything working reports ok`() {
        assertEquals(PhoneHealth.OK, status().health)
    }

    @Test
    fun `a missing permission outranks every other problem`() {
        // It is both the likeliest cause of silence and the only one the user fixes in seconds,
        // so it must not be hidden behind a complaint about the watch.
        val broken =
            status(
                permissionGranted = false,
                lastReading = null,
                freshness = ReadingFreshness.NO_DATA,
                watchConnected = false,
            )

        assertEquals(PhoneHealth.NO_PERMISSION, broken.health)
    }

    @Test
    fun `permission granted but nothing received yet is its own state`() {
        // Distinct from "stopped receiving": right after setup this is normal and the user
        // should be told to wait, not sent hunting for a fault.
        assertEquals(
            PhoneHealth.WAITING_FOR_FIRST_READING,
            status(lastReading = null, freshness = ReadingFreshness.NO_DATA).health,
        )
    }

    @Test
    fun `stale and absent readings both mean the flow stopped`() {
        // The difference is precision the user cannot act on.
        assertEquals(PhoneHealth.NOT_RECEIVING, status(freshness = ReadingFreshness.STALE).health)
        assertEquals(PhoneHealth.NOT_RECEIVING, status(freshness = ReadingFreshness.NO_DATA).health)
    }

    @Test
    fun `an aging reading is still healthy`() {
        // The fresh window is two minutes, so aging is the ordinary state and reporting it as a
        // fault would make the screen cry wolf.
        assertEquals(PhoneHealth.OK, status(freshness = ReadingFreshness.AGING).health)
    }

    @Test
    fun `a disconnected watch is reported last`() {
        assertEquals(PhoneHealth.WATCH_UNREACHABLE, status(watchConnected = false).health)
    }

    @Test
    fun `the fallback parser raises attention even when everything else is fine`() {
        val onFallback = status(usedFallbackParser = true)

        assertEquals(PhoneHealth.OK, onFallback.health)
        assertEquals(true, onFallback.needsAttention)
        assertEquals(false, status().needsAttention)
    }
}
