package ro.smakeru.cgmglance.sync

import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.RangeThresholds
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PayloadsTest {
    private val timestamp = 1_785_662_760_000L
    private val reading = requireNotNull(GlucoseReading.create(112, timestamp))

    private fun payload() =
        LatestReadingPayload(
            reading = reading,
            trend = TrendArrow.RISING_SLIGHTLY,
            senderNowMillis = timestamp + 1_500,
            sequence = 42L,
        )

    private fun read(map: Map<String, Any?>) = LatestReadingPayload.from(MapPayloadReader(map))

    @Test
    fun `a payload survives a round trip unchanged`() {
        val decoded = read(payload().toMap())

        assertNotNull(decoded)
        assertEquals(112, decoded.reading.mgdl)
        assertEquals(timestamp, decoded.reading.timestampMillis)
        assertEquals(TrendArrow.RISING_SLIGHTLY, decoded.trend)
        assertEquals(timestamp + 1_500, decoded.senderNowMillis)
        assertEquals(42L, decoded.sequence)
    }

    @Test
    fun `the trend travels by name so ordinals can be reordered safely`() {
        assertEquals(TrendArrow.RISING_SLIGHTLY.name, payload().toMap()[SyncContract.KEY_TREND])
    }

    @Test
    fun `an unrecognised trend degrades to unknown rather than losing the reading`() {
        // A newer phone paired with an older watch is routine. The watch must still show a
        // glucose number; only the arrow is allowed to disappear.
        val decoded = read(payload().toMap() + (SyncContract.KEY_TREND to "SPIRALLING_UPWARDS"))

        assertNotNull(decoded)
        assertEquals(112, decoded.reading.mgdl)
        assertEquals(TrendArrow.UNKNOWN, decoded.trend)
    }

    @Test
    fun `keys this build has never heard of are ignored`() {
        val decoded = read(payload().toMap() + ("somethingAddedLater" to "value"))

        assertNotNull(decoded)
        assertEquals(112, decoded.reading.mgdl)
    }

    @Test
    fun `a missing sender clock is treated as no skew`() {
        val decoded = read(payload().toMap() - SyncContract.KEY_SENDER_NOW_MILLIS)

        assertNotNull(decoded)
        // What an older sender that never wrote the key effectively means.
        assertEquals(timestamp, decoded.senderNowMillis)
    }

    @Test
    fun `a payload without a usable reading is rejected`() {
        assertNull(read(emptyMap()))
        assertNull(read(payload().toMap() - SyncContract.KEY_MGDL))
        assertNull(read(payload().toMap() - SyncContract.KEY_TIMESTAMP_MILLIS))
        // The same plausibility guard as the acquisition path: nothing implausible may reach
        // the watch, whatever produced it.
        assertNull(read(payload().toMap() + (SyncContract.KEY_MGDL to 2026)))
    }

    @Test
    fun `numbers arriving as a wider type are still read`() {
        // DataMap distinguishes int from long; a value written as one and read as the other
        // must not silently become a default.
        val decoded =
            read(
                payload().toMap() +
                    mapOf(
                        SyncContract.KEY_MGDL to 112L,
                        SyncContract.KEY_SEQUENCE to 7,
                    ),
            )

        assertNotNull(decoded)
        assertEquals(112, decoded.reading.mgdl)
        assertEquals(7L, decoded.sequence)
    }

    @Test
    fun `preferences round trip`() {
        val custom = requireNotNull(RangeThresholds.create(50, 80, 140))
        val decoded = PrefsPayload.from(MapPayloadReader(PrefsPayload(GlucoseUnit.MMOL_L, custom).toMap()))

        assertEquals(GlucoseUnit.MMOL_L, decoded.unit)
        assertEquals(custom, decoded.thresholds)
    }

    @Test
    fun `preferences fall back to defaults instead of refusing to render`() {
        val decoded = PrefsPayload.from(MapPayloadReader(emptyMap()))

        assertEquals(GlucoseUnit.MG_DL, decoded.unit)
        assertEquals(RangeThresholds.default, decoded.thresholds)
    }

    @Test
    fun `inconsistent preferences fall back to defaults`() {
        // Low above high cannot classify anything. Showing the standard range beats showing
        // nothing at all, because preferences are cosmetic next to the reading itself.
        val decoded =
            PrefsPayload.from(
                MapPayloadReader(
                    mapOf(
                        SyncContract.KEY_URGENT_LOW_MGDL to 55,
                        SyncContract.KEY_LOW_MGDL to 200,
                        SyncContract.KEY_HIGH_MGDL to 180,
                    ),
                ),
            )

        assertEquals(RangeThresholds.default, decoded.thresholds)
    }
}
