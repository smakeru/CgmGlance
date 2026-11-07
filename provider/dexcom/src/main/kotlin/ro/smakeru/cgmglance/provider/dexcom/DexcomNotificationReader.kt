package ro.smakeru.cgmglance.provider.dexcom

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * ONE+ acquisition only. Real ONE+ notifications have not yet been captured; this is a
 * conservative text adapter, not a claim of validated support for a particular vendor release.
 * No ongoing flag requirement: alerts and ordinary updates receive the same strict parser.
 * No notification text, glucose values or unrelated package names are logged.
 */
class DexcomNotificationReader(
    context: Context,
) {
    private val parser = DexcomNotificationParser()
    private val extractor = RemoteViewsTextExtractor(context.applicationContext)

    fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName != DexcomNotificationParser.SUPPORTED_PACKAGE) return
        val notification = sbn.notification ?: return
        // Samsung adds a summary and Dexcom posts a separate foreground-service notice. Neither
        // is a glucose update; reporting their missing units would overwrite useful diagnostics.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val result =
            try {
                val extras = notification.extras
                val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 &&
                    notification.contentView == null && notification.bigContentView == null &&
                    listOfNotNull(title, text).none { field -> field.any(Char::isDigit) }
                ) {
                    return
                }
                // Inspect BOTH custom forms even when extras parse: an error/stale value in the
                // expanded form must not be bypassed by an apparently valid collapsed/extra value.
                parser.parse(
                    title = title,
                    text = text,
                    bigContentViews =
                        notification.bigContentView
                            ?.let {
                                extractor.extract(it, sbn.packageName)
                            }.orEmpty(),
                    contentViews =
                        notification.contentView
                            ?.let {
                                extractor.extract(it, sbn.packageName)
                            }.orEmpty(),
                    // System posting time, never a displayed clock or Notification.when.
                    // A repost may make an old value appear fresh: this is NOT a guaranteed
                    // sensor timestamp. Real captures/device testing are needed to assess it.
                    postTimeMillis = sbn.postTime,
                )
            } catch (_: RuntimeException) {
                // Malformed extras from another process must not crash the listener.
                DexcomParseResult.Failure(DexcomParseFailure.NO_VALUE_FOUND)
            }
        when (result) {
            is DexcomParseResult.Success -> {
                DexcomProviderBus.sink?.onReading(result.reading, result.unit, result.usedFallback)
            }

            is DexcomParseResult.Failure -> {
                Log.w(TAG, "parse failed: ${result.reason}")
                DexcomProviderBus.sink?.onParseFailure(result.reason)
            }
        }
    }

    private companion object {
        const val TAG = "CgmGlance/Dexcom"
    }
}
