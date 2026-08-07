package ro.smakeru.cgmglance.wear

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import ro.smakeru.cgmglance.sync.SyncContract

/**
 * Pulls whatever the phone has already published, the first time anything on the watch runs.
 *
 * `onDataChanged` fires on *changes*. A data item written before this app existed — which is
 * every item, on a fresh install — never produces one, so a watch that only listened would show
 * nothing until the phone happened to send its next reading. The Data Layer keeps those items,
 * so they are there to be read; nothing was listening yet.
 *
 * The same gap appears whenever the system evicts the app from memory, which makes this a
 * routine path rather than a first-run special case.
 */
class CgmGlanceWearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        readWhatIsAlreadyThere()
    }

    private fun readWhatIsAlreadyThere() {
        val store = WatchGlucoseStore(this)

        Wearable
            .getDataClient(this)
            .dataItems
            .addOnSuccessListener { buffer ->
                var found = false
                try {
                    for (item in buffer) {
                        val values = DataMapItem.fromDataItem(item).dataMap.toPlainMap()
                        when (item.uri.path) {
                            SyncContract.PATH_LATEST -> {
                                store.saveLatest(values)
                                found = true
                            }

                            SyncContract.PATH_PREFS -> {
                                store.savePrefs(values)
                                found = true
                            }
                        }
                    }
                } finally {
                    // The buffer holds native resources and is not garbage collected.
                    buffer.release()
                }

                Log.i(TAG, if (found) "restored state published before this app started" else "nothing published yet")
                if (found) refreshSurfaces()
            }.addOnFailureListener { error ->
                // Not fatal: the next reading from the phone will arrive through the listener.
                Log.w(TAG, "could not read existing data items", error)
            }
    }

    private fun refreshSurfaces() {
        ComplicationDataSourceUpdateRequester
            .create(
                context = this,
                complicationDataSourceComponent = ComponentName(this, GlucoseComplicationService::class.java),
            ).requestUpdateAll()
        TileService.getUpdater(this).requestUpdate(GlucoseTileService::class.java)
    }

    private companion object {
        const val TAG = "CgmGlance/WearBoot"
    }
}
