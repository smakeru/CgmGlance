package ro.smakeru.cgmglance.wear

import android.content.ComponentName
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import ro.smakeru.cgmglance.sync.SyncContract

/**
 * Receives payloads from the phone and pushes the surfaces that display them.
 *
 * The complication is updated by request rather than by polling. A data source may declare an
 * update period, but the platform floor is five minutes, it costs battery on every tick whether
 * or not anything changed, and it would still lag a new reading by up to that long. Pushing on
 * arrival is both fresher and cheaper.
 */
class WearDataListenerService : WearableListenerService() {
    private val store by lazy { WatchGlucoseStore(applicationContext) }

    override fun onDataChanged(events: DataEventBuffer) {
        var latestChanged = false

        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue

            val values = DataMapItem.fromDataItem(event.dataItem).dataMap.toPlainMap()
            when (event.dataItem.uri.path) {
                SyncContract.PATH_LATEST -> {
                    store.saveLatest(values)
                    latestChanged = true
                }

                SyncContract.PATH_PREFS -> {
                    store.savePrefs(values)
                    // Thresholds decide colour and wording, so the surfaces have to be redrawn
                    // even though no new reading arrived.
                    latestChanged = true
                }

                else -> {
                    Log.d(TAG, "ignoring unknown path ${event.dataItem.uri.path}")
                }
            }
        }

        if (latestChanged) {
            requestComplicationUpdate()
            // Tiles only render while visible, so this costs nothing when the user is not
            // looking; when they are, it is the difference between current and five minutes old.
            TileService.getUpdater(applicationContext).requestUpdate(GlucoseTileService::class.java)
        }
    }

    private fun requestComplicationUpdate() {
        ComplicationDataSourceUpdateRequester
            .create(
                context = applicationContext,
                complicationDataSourceComponent =
                    ComponentName(applicationContext, GlucoseComplicationService::class.java),
            ).requestUpdateAll()
    }

    private companion object {
        const val TAG = "CgmGlance/WearRx"
    }
}

/**
 * Converts a Play Services `DataMap` into an ordinary map.
 *
 * The boundary where the transport type stops. Everything past this point works with plain
 * values, which is what makes the store and the payload rules testable without Play Services.
 */
internal fun DataMap.toPlainMap(): Map<String, Any?> = keySet().associateWith { key -> get<Any?>(key) }
