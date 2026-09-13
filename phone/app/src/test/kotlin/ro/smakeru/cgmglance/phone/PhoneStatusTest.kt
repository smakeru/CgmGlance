package ro.smakeru.cgmglance.phone

import org.junit.jupiter.api.Test
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.ReadingFreshness
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneStatusTest {
    private val reading = requireNotNull(GlucoseReading.create(112, 1_785_738_000_000L))

    private fun status(
        permissionGranted: Boolean = true,
        lastReading: GlucoseReading? = reading,
        freshness: ReadingFreshness = ReadingFreshness.FRESH,
        watchLink: WatchLink = WatchLink.CONNECTED,
        usedFallbackParser: Boolean = false,
    ) = PhoneStatus(
        permissionGranted = permissionGranted,
        lastReading = lastReading,
        freshness = freshness,
        ageMinutes = 1,
        watchLink = watchLink,
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
                watchLink = WatchLink.UNREACHABLE,
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
    fun `a watch that is merely away is reported last`() {
        assertEquals(
            PhoneHealth.WATCH_UNREACHABLE,
            status(watchLink = WatchLink.UNREACHABLE).health,
        )
    }

    @Test
    fun `a missing watch app is a different verdict from an absent watch`() {
        // The two need opposite responses. "Out of range" resolves itself and the user should
        // wait; a watch app that is missing or signed with a different key never will, and
        // telling them to wait would be telling them to wait forever.
        assertEquals(
            PhoneHealth.WATCH_APP_MISSING,
            status(watchLink = WatchLink.NO_COMPATIBLE_APP).health,
        )
    }

    @Test
    fun `a missing watch app outranks a watch merely being away`() {
        // Only one verdict can be shown, and it must be the actionable one.
        val missing = status(watchLink = WatchLink.NO_COMPATIBLE_APP).health
        val away = status(watchLink = WatchLink.UNREACHABLE).health
        assertEquals(PhoneHealth.WATCH_APP_MISSING, missing)
        assertEquals(PhoneHealth.WATCH_UNREACHABLE, away)
        assertTrue(PhoneHealth.WATCH_APP_MISSING.ordinal < PhoneHealth.WATCH_UNREACHABLE.ordinal)
    }

    @Test
    fun `no glucose outranks any watch problem`() {
        // A watch complaint must never hide the fact that readings have stopped: the watch is
        // the display, and an empty display is the lesser failure.
        val silent =
            status(
                freshness = ReadingFreshness.STALE,
                watchLink = WatchLink.NO_COMPATIBLE_APP,
            )

        assertEquals(PhoneHealth.NOT_RECEIVING, silent.health)
    }

    @Test
    fun `a connected watch with everything else fine is ok`() {
        assertEquals(PhoneHealth.OK, status(watchLink = WatchLink.CONNECTED).health)
    }

    @Test
    fun `the fallback parser raises attention even when everything else is fine`() {
        val onFallback = status(usedFallbackParser = true)

        assertEquals(PhoneHealth.OK, onFallback.health)
        assertEquals(true, onFallback.needsAttention)
        assertEquals(false, status().needsAttention)
    }
}
