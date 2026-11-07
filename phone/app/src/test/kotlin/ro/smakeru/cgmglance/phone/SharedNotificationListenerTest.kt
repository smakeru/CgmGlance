package ro.smakeru.cgmglance.phone

import android.app.Application
import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.provider.dexcom.DexcomParseFailure
import ro.smakeru.cgmglance.provider.dexcom.DexcomProviderBus
import ro.smakeru.cgmglance.provider.dexcom.DexcomReadingSink
import ro.smakeru.cgmglance.provider.linx.LinxNotificationListenerService
import ro.smakeru.cgmglance.provider.linx.LinxNotificationParser
import ro.smakeru.cgmglance.provider.linx.LinxParseFailure
import ro.smakeru.cgmglance.provider.linx.LinxProviderBus
import ro.smakeru.cgmglance.provider.linx.LinxReadingSink
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Synthetic notifications only; no personal glucose values or captured notification data. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SharedNotificationListenerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val service = Robolectric.buildService(LinxNotificationListenerService::class.java).create().get()
    private val readings = mutableListOf<Pair<GlucoseSource, GlucoseReading>>()
    private val failures = mutableListOf<GlucoseSource>()
    private val posted = 1_800_000_000_000L

    @Before
    fun attachReaders() {
        LinxProviderBus.sink =
            object : LinxReadingSink {
                override fun onReading(
                    reading: GlucoseReading,
                    unit: GlucoseUnit,
                    usedFallback: Boolean,
                ) {
                    readings += GlucoseSource.LINX to reading
                }

                override fun onParseFailure(reason: LinxParseFailure) {
                    failures += GlucoseSource.LINX
                }
            }
        DexcomProviderBus.sink =
            object : DexcomReadingSink {
                override fun onReading(
                    reading: GlucoseReading,
                    unit: GlucoseUnit,
                    usedFallback: Boolean,
                ) {
                    readings += GlucoseSource.DEXCOM_ONE_PLUS to reading
                }

                override fun onParseFailure(reason: DexcomParseFailure) {
                    failures += GlucoseSource.DEXCOM_ONE_PLUS
                }
            }
    }

    @After
    fun detachReaders() {
        LinxProviderBus.sink = null
        DexcomProviderBus.sink = null
    }

    private fun postedNotification(
        pkg: String,
        notification: Notification,
    ) = StatusBarNotification(pkg, pkg, 1, null, 1000, 0, 0, notification, Process.myUserHandle(), posted)

    private fun dexcomNotification() =
        Notification
            .Builder(context, "synthetic")
            .setContentTitle("Dexcom ONE+")
            .setContentText("Glucose: 124 mg/dL")
            .build()

    @Test
    fun `shared listener routes ONE plus extras only to the Dexcom sink`() {
        service.onNotificationPosted(postedNotification("com.dexcom.d1plus", dexcomNotification()))
        assertEquals(listOf(GlucoseSource.DEXCOM_ONE_PLUS to 124), readings.map { it.first to it.second.mgdl })
        assertEquals(posted, readings.single().second.timestampMillis)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `both LinX packages still reach the LinX reader with their posting timestamp`() {
        val layout =
            // Reuse a RemoteViews-compatible layout; framework simple_list_item_2 contains a
            // TwoLineListItem, which Android correctly refuses to inflate inside a notification.
            RemoteViews(context.packageName, R.layout.glucose_widget).apply {
                setTextViewText(R.id.widget_value, "123")
                setTextViewText(R.id.widget_unit, "mg/dL")
            }
        val notification =
            Notification
                .Builder(context, "synthetic")
                .setOngoing(true)
                .setCustomContentView(layout)
                .build()
        for (pkg in LinxNotificationParser.SUPPORTED_PACKAGES) {
            service.onNotificationPosted(postedNotification(pkg, notification))
        }
        assertEquals(LinxNotificationParser.SUPPORTED_PACKAGES.size, readings.size)
        assertTrue(
            readings.all {
                it.first == GlucoseSource.LINX && it.second.mgdl == 123 &&
                    it.second.timestampMillis == posted
            },
        )
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `unrelated packages and null callbacks never reach either provider`() {
        service.onNotificationPosted(null)
        service.onNotificationPosted(postedNotification("com.example.unrelated", dexcomNotification()))
        assertTrue(readings.isEmpty())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `Dexcom group summaries remain ignored by the common listener`() {
        val summary = dexcomNotification().apply { flags = flags or Notification.FLAG_GROUP_SUMMARY }
        service.onNotificationPosted(postedNotification("com.dexcom.d1plus", summary))
        assertTrue(readings.isEmpty())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `a malformed Dexcom reading does not contaminate the LinX diagnostics`() {
        val notification = Notification.Builder(context, "synthetic").setContentText("124").build()
        service.onNotificationPosted(postedNotification("com.dexcom.d1plus", notification))
        assertTrue(readings.isEmpty())
        assertEquals(listOf(GlucoseSource.DEXCOM_ONE_PLUS), failures)
    }
}
