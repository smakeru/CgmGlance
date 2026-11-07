package ro.smakeru.cgmglance.phone

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.graphics.Color
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.time.Clock
import ro.smakeru.cgmglance.provider.linx.LinxNotificationListenerService
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationAccessTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val component = ComponentName(context, LinxNotificationListenerService::class.java)
    private val now = 1_800_000_000_000L
    private val clock = Clock { now }
    private val sources =
        PhoneGlucoseSources(
            GlucoseSource.entries.associateWith { PhoneGlucoseRepository(clock = clock) },
        )
    private val exporter =
        object : GlucoseExporter {
            override fun offer(reading: GlucoseReading) = Unit

            override fun status() = HealthExportStatus(HealthExportState.DISABLED)
        }
    private val status = PhoneStatusSource(context, sources, clock, exporter)

    @Test
    fun `manifest exposes exactly one listener using the original LinX component`() {
        val listeners =
            context.packageManager.queryIntentServices(
                Intent("android.service.notification.NotificationListenerService").setPackage(context.packageName),
                0,
            )
        assertEquals(1, listeners.size)
        val info = listeners.single().serviceInfo
        assertEquals("ro.smakeru.cgmglance.provider.linx.LinxNotificationListenerService", info.name)
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", info.permission)
        assertTrue(info.exported)
        assertEquals("CGM Glance", info.loadLabel(context.packageManager).toString())
    }

    @Test
    fun `without shared access either selected source reports missing permission`() {
        for (source in GlucoseSource.entries) {
            sources.select(source)
            assertFalse(status.isNotificationAccessGranted())
            assertEquals(PhoneHealth.NO_PERMISSION, status.snapshot(WatchLink.UNREACHABLE).health)
        }
    }

    @Test
    fun `one grant covers both sources and waiting is yellow not a permission error`() {
        shadowOf(manager).setNotificationListenerAccessGranted(component, true)
        for (source in GlucoseSource.entries) {
            sources.select(source)
            val snapshot = status.snapshot(WatchLink.UNREACHABLE)
            assertTrue(snapshot.permissionGranted)
            assertEquals(source, snapshot.source)
            assertEquals(PhoneHealth.WAITING_FOR_FIRST_READING, snapshot.health)
            assertEquals(Color(0xFFFFE082), colorFor(snapshot.health))
        }
    }

    @Test
    fun `fresh selected readings with an absent watch stay yellow with only one grant`() {
        shadowOf(manager).setNotificationListenerAccessGranted(component, true)
        for (source in GlucoseSource.entries) {
            sources.record(source, requireNotNull(GlucoseReading.create(123, now)), false)
            sources.select(source)
            val snapshot = status.snapshot(WatchLink.UNREACHABLE)
            assertEquals(PhoneHealth.WATCH_UNREACHABLE, snapshot.health)
            assertEquals(Color(0xFFFFE082), colorFor(snapshot.health))
        }
    }

    @Test
    fun `background readings do not conceal that the selected source has no data`() {
        shadowOf(manager).setNotificationListenerAccessGranted(component, true)
        sources.record(GlucoseSource.DEXCOM_ONE_PLUS, requireNotNull(GlucoseReading.create(123, now)), false)
        assertEquals(PhoneHealth.WAITING_FOR_FIRST_READING, status.snapshot(WatchLink.CONNECTED).health)
        sources.select(GlucoseSource.DEXCOM_ONE_PLUS)
        assertEquals(PhoneHealth.OK, status.snapshot(WatchLink.CONNECTED).health)
    }

    @Test
    fun `revoking shared access is reflected immediately for either source`() {
        shadowOf(manager).setNotificationListenerAccessGranted(component, true)
        assertTrue(status.isNotificationAccessGranted())
        shadowOf(manager).setNotificationListenerAccessGranted(component, false)
        for (source in GlucoseSource.entries) {
            sources.select(source)
            assertEquals(PhoneHealth.NO_PERMISSION, status.snapshot(WatchLink.CONNECTED).health)
        }
    }

    @Test
    @Config(sdk = [26])
    fun `Android 8 uses the shared component without requiring the newer permission API`() {
        android.provider.Settings.Secure
            .putString(context.contentResolver, "enabled_notification_listeners", null)
        assertFalse(status.isNotificationAccessGranted())
        android.provider.Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            "other.app/.Reader:${component.flattenToShortString()}",
        )
        for (source in GlucoseSource.entries) {
            sources.select(source)
            assertTrue(status.isNotificationAccessGranted())
            assertEquals(PhoneHealth.WAITING_FOR_FIRST_READING, status.snapshot(WatchLink.UNREACHABLE).health)
        }
        android.provider.Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            "other.app/.Reader",
        )
        assertFalse(status.isNotificationAccessGranted())
    }

    @Test
    fun `a historical Dexcom-only grant cannot silently authorize the replacement listener`() {
        shadowOf(manager).setNotificationListenerAccessGranted(
            ComponentName(
                context.packageName,
                "ro.smakeru.cgmglance.provider.dexcom.DexcomNotificationListenerService",
            ),
            true,
        )
        assertFalse(status.isNotificationAccessGranted())
    }
}
