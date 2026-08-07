package ro.smakeru.cgmglance.phone

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import ro.smakeru.cgmglance.sync.LatestReadingPayload
import ro.smakeru.cgmglance.sync.PrefsPayload
import ro.smakeru.cgmglance.sync.SyncContract

/**
 * Pushes payloads to the watch over the Wear Data Layer.
 *
 * Data items rather than messages, on purpose: the platform stores them and replays them when
 * the watch reconnects. A message sent while the watch is off the wrist is simply lost, which
 * would leave the complication frozen until the next reading happened to arrive at a moment the
 * link was up.
 */
class WatchSyncSender(
    context: Context,
) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    fun sendLatest(
        payload: LatestReadingPayload,
        urgent: Boolean,
    ) = put(SyncContract.PATH_LATEST, payload.toMap(), urgent)

    /** Preferences change when the user edits a setting, so they are never urgent. */
    fun sendPrefs(payload: PrefsPayload) = put(SyncContract.PATH_PREFS, payload.toMap(), urgent = false)

    private fun put(
        path: String,
        values: Map<String, Any>,
        urgent: Boolean,
    ) {
        val request = PutDataMapRequest.create(path)
        val map = request.dataMap
        values.forEach { (key, value) ->
            when (value) {
                is Int -> map.putInt(key, value)

                is Long -> map.putLong(key, value)

                is String -> map.putString(key, value)

                is Boolean -> map.putBoolean(key, value)

                // The contract only ever carries these four. Anything else is a mistake in the
                // payload, and failing loudly here beats a field silently vanishing in transit.
                else -> error("Unsupported payload type for '$key': ${value::class.java.name}")
            }
        }

        val asPutDataRequest =
            request.asPutDataRequest().apply {
                // Without this the platform may batch the item for up to half an hour.
                if (urgent) setUrgent()
            }

        dataClient
            .putDataItem(asPutDataRequest)
            .addOnFailureListener { error -> Log.w(TAG, "failed to put $path", error) }
    }

    private companion object {
        const val TAG = "CgmGlance/Sync"
    }
}
