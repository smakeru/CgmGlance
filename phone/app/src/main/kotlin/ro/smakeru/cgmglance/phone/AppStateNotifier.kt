package ro.smakeru.cgmglance.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Tells the user when this app has stopped working, and nothing else.
 *
 * Never about glucose. No alarm for a low, no vibration for a high, not even a silent one: that
 * line is what keeps this a display mirror rather than an alerting system, and an alerting
 * system carries reliability guarantees that cannot be met across a Bluetooth link which fails
 * quietly. The official app owns alarms.
 *
 * One notification, updated in place and cleared the moment readings resume. A notification that
 * appears often stops being read.
 */
class AppStateNotifier(
    private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_status_name),
                // Low: it appears in the shade without interrupting anything. This is
                // information about the app, not about the person's glucose.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_status_description)
                enableVibration(false)
                setSound(null, null)
            }
        manager.createNotificationChannel(channel)
    }

    /** Shows or replaces the problem notification. */
    fun showProblem(health: PhoneHealth) {
        val (titleRes, textRes) =
            when (health) {
                PhoneHealth.NO_PERMISSION -> {
                    R.string.notif_no_permission_title to R.string.notif_no_permission_text
                }

                PhoneHealth.NOT_RECEIVING -> {
                    R.string.notif_not_receiving_title to R.string.notif_not_receiving_text
                }

                // Nothing else is worth interrupting for: a watch out of range reconnects on
                // its own, and a first reading that has not landed yet is normal after setup.
                else -> {
                    return
                }
            }

        val title = context.getString(titleRes)
        val text = context.getString(textRes)

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    /** Called as soon as a reading arrives: the problem is over, so the notice goes. */
    fun clear() = manager.cancel(NOTIFICATION_ID)

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private companion object {
        const val CHANNEL_ID = "app_status"
        const val NOTIFICATION_ID = 1
    }
}
