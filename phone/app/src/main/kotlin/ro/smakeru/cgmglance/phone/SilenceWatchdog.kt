package ro.smakeru.cgmglance.phone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Notices when readings stop arriving.
 *
 * Deliberately not a periodic check. Nothing calls this app unless LinX posts a notification, so
 * silence is precisely the condition no reading-driven code can observe — the absence of an
 * event cannot itself raise one.
 *
 * Instead each accepted reading pushes a single alarm further into the future. While readings
 * keep coming the alarm never fires. The first time it does, the flow has been broken for longer
 * than a working system would ever be quiet, and that is exactly the moment worth reporting.
 *
 * `AlarmManager` rather than `WorkManager`: this needs one self-rescheduling alarm, not a job
 * queue, and it adds no dependency to a module that has almost none.
 */
class SilenceWatchdog(
    private val context: Context,
) {
    /** Cancels any pending alarm and sets a fresh one. Called on every accepted reading. */
    fun arm() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = System.currentTimeMillis() + SILENCE_LIMIT_MILLIS

        // Allowed to fire in Doze: the whole point is to speak up when the phone has gone
        // quiet, which is when Doze is most likely to be active. Inexact, because a few
        // minutes either way changes nothing and exact alarms are a restricted resource.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
    }

    fun disarm() {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    companion object {
        /**
         * How long silence must last before it is worth mentioning.
         *
         * Matched to the point at which the watch stops showing a value at all, so the phone
         * and the watch agree on when the data has run out. Comfortably longer than the
         * observed twenty to thirty second cadence, so an ordinary hiccup never triggers it.
         */
        const val SILENCE_LIMIT_MILLIS: Long = 20 * 60_000L

        private const val REQUEST_CODE = 100

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, SilenceWatchdogReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

/**
 * Fires when readings have been silent for too long.
 *
 * Re-arms itself when it finds nothing wrong, so a transient problem that resolves before the
 * alarm lands does not leave the watchdog disarmed.
 */
class SilenceWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val application = context.applicationContext as? CgmGlanceApplication ?: return
        val status = application.statusSource.snapshot(watchConnected = true)

        Log.i(TAG, "watchdog fired, health=${status.health}")

        when (status.health) {
            PhoneHealth.NO_PERMISSION, PhoneHealth.NOT_RECEIVING -> {
                application.notifier.showProblem(status.health)
            }

            // Still healthy: a reading must have landed between the alarm being set and now.
            else -> {
                application.notifier.clear()
            }
        }

        // Either way, keep watching.
        SilenceWatchdog(context).arm()
    }

    private companion object {
        const val TAG = "CgmGlance/Watchdog"
    }
}
