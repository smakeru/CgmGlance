package ro.smakeru.cgmglance.phone

import android.content.Context
import android.content.SharedPreferences
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.time.Clock

/**
 * Keeps the recent readings across process death.
 *
 * Without this the trend disappears every time Android reclaims the app — on a reboot, after a
 * system update, or simply when memory runs short, which on Samsung happens routinely. The
 * regression needs three readings spanning six minutes, so each restart blanks the arrow for
 * about seven minutes with no explanation the user could act on. Observed on a real device.
 *
 * Deliberately the same modest storage as the watch side: a handful of numbers written a few
 * times an hour. A database would bring migrations and a coroutine surface for no benefit.
 */
class ReadingHistoryStore(
    context: Context,
    private val clock: Clock,
    private val windowMillis: Long = PhoneGlucoseRepository.HISTORY_WINDOW_MILLIS,
) : HistoryPersistence {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Readings still inside the window, oldest first.
     *
     * Filtered on read rather than trusting what was written. A phone that was off for three
     * hours holds readings that are useless to the trend fit and actively misleading if handed
     * to it: a regression across that gap would produce a confident slope from nothing.
     */
    override fun load(): List<GlucoseReading> {
        val raw = prefs.getString(KEY_READINGS, null) ?: return emptyList()
        val cutoff = clock.nowMillis() - windowMillis

        return raw
            .split(RECORD_SEPARATOR)
            .mapNotNull { record ->
                val parts = record.split(FIELD_SEPARATOR)
                if (parts.size != 2) return@mapNotNull null
                val mgdl = parts[0].toIntOrNull() ?: return@mapNotNull null
                val timestamp = parts[1].toLongOrNull() ?: return@mapNotNull null
                GlucoseReading.create(mgdl, timestamp)
            }.filter { it.timestampMillis >= cutoff }
            .sortedBy { it.timestampMillis }
    }

    /**
     * Replaces the stored history.
     *
     * `apply` rather than `commit`: this runs on the notification callback, and blocking that
     * thread on disk would delay the reading reaching the watch. Losing the last write to a
     * crash costs at most one entry, which the next reading replaces within a minute.
     */
    override fun save(readings: List<GlucoseReading>) {
        val encoded =
            readings.joinToString(RECORD_SEPARATOR) { "${it.mgdl}$FIELD_SEPARATOR${it.timestampMillis}" }
        prefs.edit().putString(KEY_READINGS, encoded).apply()
    }

    private companion object {
        const val FILE_NAME = "cgm_glance_history"
        const val KEY_READINGS = "readings"

        // A flat string rather than JSON: two numbers per record, no schema to evolve, and no
        // serialisation dependency in a module that needs none.
        const val RECORD_SEPARATOR = ";"
        const val FIELD_SEPARATOR = ":"
    }
}
