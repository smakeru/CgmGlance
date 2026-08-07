package ro.smakeru.cgmglance.wear

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import ro.smakeru.cgmglance.sync.SyncContract
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The complication is the product: this is the surface a user actually reads glucose from.
 *
 * Driven through the real service rather than around it, so a mistake in what the watch face is
 * handed shows up here rather than on a wrist.
 */
@RunWith(RobolectricTestRunner::class)
class GlucoseComplicationServiceTest {
    private val service =
        Robolectric
            .buildService(GlucoseComplicationService::class.java)
            .create()
            .get()

    private val store = WatchGlucoseStore(RuntimeEnvironment.getApplication())

    private fun seed(
        mgdl: Int,
        ageMinutes: Long,
        trend: TrendArrow = TrendArrow.RISING_SLIGHTLY,
    ) {
        val timestamp = System.currentTimeMillis() - ageMinutes * 60_000L
        store.saveLatest(
            mapOf(
                SyncContract.KEY_SCHEMA_VERSION to SyncContract.SCHEMA_VERSION,
                SyncContract.KEY_SEQUENCE to 1L,
                SyncContract.KEY_MGDL to mgdl,
                SyncContract.KEY_TIMESTAMP_MILLIS to timestamp,
                SyncContract.KEY_SENDER_NOW_MILLIS to timestamp,
                SyncContract.KEY_TREND to trend.name,
            ),
        )
    }

    private fun request(type: ComplicationType) =
        runBlocking { service.onComplicationRequest(ComplicationRequest(1, type, false)) }

    private fun shortTextOf(type: ComplicationType = ComplicationType.SHORT_TEXT): String {
        val data = assertNotNull(request(type)) as ShortTextComplicationData
        return data.text.getTextAt(service.resources, Instant.now()).toString()
    }

    @Test
    fun `the editor sees a sample before anything has synced`() {
        // The watch face editor calls this with no data and possibly before the first sync. A
        // blank entry in the picker does not get picked, which would sink the whole app.
        val preview = assertNotNull(service.getPreviewData(ComplicationType.SHORT_TEXT))
        val text = (preview as ShortTextComplicationData).text.getTextAt(service.resources, Instant.now())

        assertEquals("112↗", text.toString())
    }

    @Test
    fun `a fresh reading is shown with its arrow`() {
        seed(mgdl = 181, ageMinutes = 0)

        assertEquals("181↗", shortTextOf())
    }

    @Test
    fun `the arrow rides in the text, because arc slots drop the title`() {
        // Found on a real Galaxy Watch: a ranged-value slot renders text only.
        seed(mgdl = 181, ageMinutes = 0)

        val data = assertNotNull(request(ComplicationType.RANGED_VALUE)) as RangedValueComplicationData
        val text = data.text?.getTextAt(service.resources, Instant.now())

        assertEquals("181↗", text.toString())
        assertEquals(181f, data.value)
    }

    @Test
    fun `an aging reading is not degraded`() {
        // The fresh window is two minutes, so this is the ordinary state; marking it as a
        // problem would put the display in a permanent warning posture.
        seed(mgdl = 120, ageMinutes = 5)

        assertEquals("120↗", shortTextOf())
    }

    @Test
    fun `a stale reading keeps the number but stops asserting it`() {
        seed(mgdl = 120, ageMinutes = 15)

        // Question mark, no arrow: still shown, no longer a statement about now.
        assertEquals("120?", shortTextOf())

        // And the gauge empties before the digits do.
        val ranged = assertNotNull(request(ComplicationType.RANGED_VALUE)) as RangedValueComplicationData
        assertEquals(GlucoseDisplay.GAUGE_MIN, ranged.value)
    }

    @Test
    fun `a reading past the last threshold disappears entirely`() {
        seed(mgdl = 120, ageMinutes = 45)

        assertEquals("--", shortTextOf())
    }

    @Test
    fun `every reply carries a description for a screen reader`() {
        seed(mgdl = 181, ageMinutes = 1)

        val data = assertNotNull(request(ComplicationType.SHORT_TEXT)) as ShortTextComplicationData
        val spoken = data.contentDescription?.getTextAt(service.resources, Instant.now()).toString()

        assertEquals(true, spoken.contains("181"), "said: $spoken")
        assertEquals(true, spoken.contains("milligrams"), "said: $spoken")
    }

    @Test
    fun `image slots are declined rather than approximated`() {
        // A face asking for an image gets nothing from us until real vector arrows ship, so it
        // can fall back to another provider instead of showing something misleading.
        seed(mgdl = 181, ageMinutes = 0)

        assertNull(request(ComplicationType.MONOCHROMATIC_IMAGE))
        assertNull(request(ComplicationType.SMALL_IMAGE))
    }
}
