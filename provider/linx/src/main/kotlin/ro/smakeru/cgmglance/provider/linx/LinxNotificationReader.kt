package ro.smakeru.cgmglance.provider.linx

import android.app.Notification
import android.content.Context
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
 * not from a supported vendor package is dropped by [onNotificationPosted],
 * before it is read, stored or logged.
 */
class LinxNotificationReader(
    context: Context,
) {
    private val parser = LinxNotificationParser()
    private val extractor = RemoteViewsTextExtractor(context.applicationContext)

    fun onNotificationPosted(sbn: StatusBarNotification?) {
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

    private companion object {
        const val TAG = "CgmGlance/Linx"
    }
}
