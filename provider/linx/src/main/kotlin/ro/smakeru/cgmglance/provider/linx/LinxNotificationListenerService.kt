package ro.smakeru.cgmglance.provider.linx

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Reads the LinX ongoing notification as it is posted.
 *
 * This is the only acquisition path the vendor leaves open. Verified against LinX 2.5.0 with
 * `dumpsys`: the app declares no broadcast, no content provider, no bindable service and no
 * Health Connect permission. Everything cleaner was checked and found absent, not assumed.
 *
 * Privacy: the system delivers **every** notification on the device to this service. Anything
 * not from a supported vendor package is dropped in the first line of [onNotificationPosted],
 * before it is read, stored or logged.
 */
class LinxNotificationListenerService : NotificationListenerService() {
    private val parser = LinxNotificationParser()
    private val extractor by lazy { RemoteViewsTextExtractor(applicationContext) }

    /**
     * Reads whatever is already on screen the moment the service binds.
     *
     * Without this the app stays blank until the vendor next updates its notification, which is
     * the first thing a user sees after granting permission — and the same gap reappears every
     * time the system rebinds the listener after reclaiming memory.
     */
    override fun onListenerConnected() {
        Log.i(TAG, "listener connected, reading notifications already on screen")
        runCatching { activeNotifications }
            .getOrNull()
            ?.forEach { onNotificationPosted(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (sbn.packageName !in LinxNotificationParser.SUPPORTED_PACKAGES) return

        // Only the ongoing foreground-service notification carries the value. The vendor's
        // alert notifications ("Alarmă extrem de înaltă a glicemiei") state a category and a
        // time but never a number, so they are of no use here.
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return

        val layout = notification.bigContentView ?: notification.contentView ?: return
        val views = extractor.extract(layout, sbn.packageName)

        // postTime is the system's own record of when the vendor posted this update, which is
        // as close to the measurement instant as anything available. The layout's own time
        // view is relative and localised, so it can never serve this purpose.
        when (val result = parser.parse(views, sbn.postTime)) {
            is LinxParseResult.Success -> {
                Log.i(
                    TAG,
                    "reading=${result.reading.mgdl}mg/dL unit=${result.unit} " +
                        "fallback=${result.usedFallback} at=${result.reading.timestampMillis}",
                )
                LinxProviderBus.sink?.onReading(result.reading, result.unit, result.usedFallback)
            }

            is LinxParseResult.Failure -> {
                Log.w(TAG, "parse failed: ${result.reason}, views=${views.size}")
                LinxProviderBus.sink?.onParseFailure(result.reason)
            }
        }
    }

    override fun onListenerDisconnected() {
        // Android unbinds listeners under memory pressure and does not always rebind on its
        // own. Left unhandled this is the single most common cause of "it worked for three
        // days and then went quiet", and it fails silently — the app looks alive, the data
        // simply stops.
        Log.w(TAG, "listener disconnected, requesting rebind")
        requestRebind(NOTIFICATION_LISTENER_COMPONENT)
    }

    private companion object {
        const val TAG = "CgmGlance/Linx"

        val NOTIFICATION_LISTENER_COMPONENT =
            android.content.ComponentName(
                "ro.smakeru.cgmglance",
                LinxNotificationListenerService::class.java.name,
            )
    }
}
