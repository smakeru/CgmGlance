package ro.smakeru.cgmglance.provider.linx

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import ro.smakeru.cgmglance.provider.dexcom.DexcomNotificationParser
import ro.smakeru.cgmglance.provider.dexcom.DexcomNotificationReader

/**
 * One system permission for both sensors. Android's notification-access list uses the application
 * label, so two services in one APK can appear as indistinguishable "CGM Glance" switches.
 *
 * Keep the original LinX component name to preserve existing LinX grants on upgrade. This class
 * now belongs to the phone module, which can route to both providers without coupling them.
 * Only supported vendor packages reach a reader; unrelated notifications are not inspected.
 */
class LinxNotificationListenerService : NotificationListenerService() {
    private val linx by lazy { LinxNotificationReader(applicationContext) }
    private val dexcom by lazy { DexcomNotificationReader(applicationContext) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        when (sbn?.packageName) {
            in LinxNotificationParser.SUPPORTED_PACKAGES -> linx.onNotificationPosted(sbn)
            DexcomNotificationParser.SUPPORTED_PACKAGE -> dexcom.onNotificationPosted(sbn)
        }
    }

    override fun onListenerConnected() {
        // Replay already visible readings after permission grants, upgrades and system rebinds.
        runCatching { activeNotifications }.getOrNull()?.forEach { onNotificationPosted(it) }
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "listener disconnected, requesting rebind")
        requestRebind(ComponentName(this, LinxNotificationListenerService::class.java))
    }

    private companion object {
        const val TAG = "CgmGlance/Notifications"
    }
}
