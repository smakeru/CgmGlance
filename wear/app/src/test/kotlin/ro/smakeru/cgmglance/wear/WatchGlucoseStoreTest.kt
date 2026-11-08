package ro.smakeru.cgmglance.wear

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.RangeThresholds
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import ro.smakeru.cgmglance.sync.SyncContract
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The watch's side of the wire, end to end: what the phone sends, stored and read back.
 *
 * Runs under Robolectric because the store is `SharedPreferences`, and a fake would test the
 * fake. The values are invented; only their shape matches what a device produces.
 */
@RunWith(RobolectricTestRunner::class)
class WatchGlucoseStoreTest {
    private val store = WatchGlucoseStore(RuntimeEnvironment.getApplication())

    private val timestamp = 1_785_662_760_000L

    private fun latestPayload(
        mgdl: Any = 112,
        trend: String = TrendArrow.RISING_SLIGHTLY.name,
    ) = mapOf(
        SyncContract.KEY_SCHEMA_VERSION to SyncContract.SCHEMA_VERSION,
        SyncContract.KEY_SEQUENCE to 42L,
        SyncContract.KEY_MGDL to mgdl,
        SyncContract.KEY_TIMESTAMP_MILLIS to timestamp,
        SyncContract.KEY_SENDER_NOW_MILLIS to timestamp + 200,
        SyncContract.KEY_TREND to trend,
    )

    @Test
    fun `a payload survives storage and reads back intact`() {
        store.saveLatest(latestPayload())

        val loaded = store.loadLatest()

        assertNotNull(loaded)
        assertEquals(112, loaded.reading.mgdl)
        assertEquals(timestamp, loaded.reading.timestampMillis)
        assertEquals(TrendArrow.RISING_SLIGHTLY, loaded.trend)
        assertEquals(timestamp + 200, loaded.senderNowMillis)
        assertEquals(42L, loaded.sequence)
    }

    @Test
    fun `nothing stored yet reads as no reading`() {
        assertNull(store.loadLatest())
    }

    @Test
    fun `preferences fall back to the standard range when nothing arrived`() {
        val prefs = store.loadPrefs()

        assertEquals(GlucoseUnit.MG_DL, prefs.unit)
        assertEquals(RangeThresholds.default, prefs.thresholds)
    }

    @Test
    fun `preferences survive storage`() {
        val custom = requireNotNull(RangeThresholds.create(50, 80, 140))
        store.savePrefs(
            mapOf(
                SyncContract.KEY_UNIT to GlucoseUnit.MMOL_L.name,
                SyncContract.KEY_URGENT_LOW_MGDL to custom.urgentLowMgdl,
                SyncContract.KEY_LOW_MGDL to custom.lowMgdl,
                SyncContract.KEY_HIGH_MGDL to custom.highMgdl,
            ),
        )

        val loaded = store.loadPrefs()

        assertEquals(GlucoseUnit.MMOL_L, loaded.unit)
        assertEquals(custom, loaded.thresholds)
    }

    @Test
    fun `a value written as a long still reads as a value`() {
        // The transport distinguishes int from long. A field written one way and read the other
        // must not silently become a default, which on a glucose value would be catastrophic.
        store.saveLatest(latestPayload(mgdl = 112L))

        assertEquals(112, store.loadLatest()?.reading?.mgdl)
    }

    @Test
    fun `a trend this build has never heard of does not take the reading with it`() {
        // A newer phone paired with an older watch is routine. Only the arrow may disappear.
        store.saveLatest(latestPayload(trend = "SPIRALLING_UPWARDS"))

        val loaded = store.loadLatest()

        assertNotNull(loaded)
        assertEquals(112, loaded.reading.mgdl)
        assertEquals(TrendArrow.UNKNOWN, loaded.trend)
    }

    @Test
    fun `an implausible value is refused even though it was stored`() {
        // The plausibility guard applies wherever a reading is reconstructed, not only at the
        // point it was parsed.
        store.saveLatest(latestPayload(mgdl = 2026))

        assertNull(store.loadLatest())
    }

    @Test
    fun `a later payload replaces the earlier one`() {
        store.saveLatest(latestPayload(mgdl = 112))
        store.saveLatest(
            latestPayload(mgdl = 150) + (SyncContract.KEY_TIMESTAMP_MILLIS to timestamp + 60_000),
        )

        assertEquals(150, store.loadLatest()?.reading?.mgdl)
    }

    @Test
    fun `a later payload without delta clears the previous delta`() {
        store.saveLatest(latestPayload() + (SyncContract.KEY_DELTA_MGDL to 25))
        assertEquals(25, store.loadLatest()?.deltaMgdl)

        store.saveLatest(latestPayload(mgdl = 120))
        val loaded = store.loadLatest()
        assertNotNull(loaded)
        assertEquals(120, loaded.reading.mgdl)
        assertNull(loaded.deltaMgdl)
    }
}
