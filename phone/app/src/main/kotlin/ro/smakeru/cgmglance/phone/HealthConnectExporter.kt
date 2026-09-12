package ro.smakeru.cgmglance.phone

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Writes glucose into Health Connect, which is where Samsung Health reads it from.
 *
 * Health Connect is the destination, not a workaround on the way to one. Samsung's own Data SDK
 * can insert into Samsung Health directly, but only for
 * apps Samsung has approved as partners: developer mode waives the signature check, not the
 * partnership, and the write path is gated behind an access code issued on approval. Health
 * Connect needs no such agreement, and Samsung Health syncs from it in both directions.
 *
 * That has a consequence worth stating plainly, because it is the thing that will
 * confuse someone a year from now: a successful write here means the record reached *Health
 * Connect*, and nothing more. Whether it then appears in Samsung Health depends on the user having enabled the
 * Health Connect link in Samsung Health and granted it permission to *read* glucose. This class
 * cannot observe that, and must not claim it — hence [HealthExportState.WORKING] is worded as
 * "written", never "in Samsung Health".
 */
class HealthConnectExporter(
    context: Context,
    private val clock: Clock,
    private val preferences: PhonePreferencesStore,
    // Read per reading rather than captured once, so switching the export off takes effect on
    // the next reading instead of at the next restart.
    private val settings: () -> HealthExportSettings = { HealthExportSettings.default },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : GlucoseExporter {
    private val policy = HealthExportPolicy { settings().intervalMillis }

    private val appContext = context.applicationContext

    /**
     * Serialises the writes.
     *
     * Readings arrive on the notification callback thread, and a burst of reposts can put two
     * of them here within milliseconds. Without this, both could pass the interval check before
     * either had recorded its write, and two records a few seconds apart would land in the log.
     */
    private val writeMutex = Mutex()

    // Written from the IO scope, read from the UI thread on every status tick.
    @Volatile
    private var permissionGranted: Boolean = false

    @Volatile
    private var lastWriteMillis: Long? = null

    @Volatile
    private var lastErrorMessage: String? = null

    /**
     * Offers a reading, and returns immediately.
     *
     * The interval check runs here, on the caller's thread, so the common case — fourteen out of
     * every fifteen readings — costs one comparison against a stored long and never starts a
     * coroutine. Only a reading that is actually due reaches the IO scope.
     */
    override fun offer(reading: GlucoseReading) {
        if (!settings().enabled) return
        if (!policy.shouldWrite(reading, preferences.loadLastHealthWriteMillis())) return

        scope.launch {
            writeMutex.withLock {
                // Re-checked inside the lock: another reading may have been written while this
                // one waited, which would make this one no longer due.
                if (!policy.shouldWrite(reading, preferences.loadLastHealthWriteMillis())) {
                    return@withLock
                }
                write(reading)
            }
        }
    }

    override fun status(): HealthExportStatus {
        val current = settings()
        if (!current.enabled) return HealthExportStatus(HealthExportState.DISABLED)

        val state =
            when (sdkStatus()) {
                HealthConnectClient.SDK_UNAVAILABLE -> {
                    HealthExportState.UNAVAILABLE
                }

                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    HealthExportState.PROVIDER_UPDATE_REQUIRED
                }

                else -> {
                    when {
                        !permissionGranted -> HealthExportState.NEEDS_SETUP
                        lastErrorMessage != null -> HealthExportState.FAILING
                        lastWriteMillis != null -> HealthExportState.WORKING
                        else -> HealthExportState.WAITING
                    }
                }
            }

        return HealthExportStatus(
            state = state,
            lastWriteMillis = lastWriteMillis,
            lastErrorMessage = lastErrorMessage,
            intervalMinutes = current.intervalMinutes,
        )
    }

    /**
     * Re-reads the granted permissions in the background.
     *
     * Permission can be revoked from inside Health Connect at any time, with no callback to us,
     * so the screen asks for a refresh on its own polling tick rather than trusting a value
     * cached at startup.
     */
    fun refresh() {
        scope.launch {
            permissionGranted = hasWritePermission()
        }
    }

    private suspend fun write(reading: GlucoseReading) {
        if (sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return

        if (!hasWritePermission()) {
            permissionGranted = false
            // Not an error: an ungranted permission is a setup step, and reporting it as a
            // failure would send the user looking for a fault that is not there.
            return
        }
        permissionGranted = true

        val instant = Instant.ofEpochMilli(reading.timestampMillis)
        try {
            client().insertRecords(
                listOf(
                    BloodGlucoseRecord(
                        time = instant,
                        // Recorded so the value keeps its wall-clock meaning after the user
                        // travels; a reading taken before breakfast should stay before
                        // breakfast in the log.
                        zoneOffset = ZoneId.systemDefault().rules.getOffset(instant),
                        level = BloodGlucose.milligramsPerDeciliter(reading.mgdl.toDouble()),
                        // A CGM samples interstitial fluid, not blood. Saying so is what lets
                        // Samsung Health show it as sensor data rather than a finger prick.
                        specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
                        metadata = Metadata.autoRecorded(device = Device(type = Device.TYPE_UNKNOWN)),
                    ),
                ),
            )

            // Stored against the *reading* timestamp, which is what the interval is measured
            // from. Persisted so a restart does not reset the cadence and write immediately.
            preferences.saveLastHealthWriteMillis(reading.timestampMillis)
            lastWriteMillis = clock.nowMillis()
            lastErrorMessage = null
            Log.i(TAG, "wrote ${reading.mgdl} mg/dL to Health Connect")
        } catch (e: Exception) {
            // Deliberately broad. The provider can fail for reasons this app cannot enumerate —
            // it was updating, it was disabled mid-write, the device ran out of storage — and
            // none of them are worth crashing the app that is also feeding the watch.
            lastErrorMessage = e.message ?: e::class.simpleName
            Log.w(TAG, "Health Connect write failed", e)
        }
    }

    private suspend fun hasWritePermission(): Boolean =
        try {
            client().permissionController.getGrantedPermissions().contains(WRITE_GLUCOSE)
        } catch (e: Exception) {
            Log.w(TAG, "could not read granted permissions", e)
            false
        }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(appContext)

    private fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(appContext)

    companion object {
        /** The one permission this app asks for. It never reads anything back. */
        val WRITE_GLUCOSE: String = HealthPermission.getWritePermission(BloodGlucoseRecord::class)

        private const val TAG = "CgmGlance/Health"
    }
}
