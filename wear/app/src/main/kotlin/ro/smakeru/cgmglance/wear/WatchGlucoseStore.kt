package ro.smakeru.cgmglance.wear

import android.content.Context
import android.content.SharedPreferences
import ro.smakeru.cgmglance.sync.LatestReadingPayload
import ro.smakeru.cgmglance.sync.PayloadReader
import ro.smakeru.cgmglance.sync.PrefsPayload

/**
 * The watch's copy of what the phone last sent.
 *
 * Read by three independent, system-driven lifecycles — the complication provider, the tile
 * provider and the app — so it is a file rather than an object held in memory by any one of
 * them.
 *
 * Deliberately `SharedPreferences` rather than DataStore, which the architecture originally
 * called for. The payload is eight primitives read on a latency-sensitive path: a complication
 * request has milliseconds, and `TileService.onTileRequest` returns a future. DataStore would
 * push coroutine scaffolding into both for no gain, and its real strengths — structured data,
 * transactional updates — are not in play here. Revisit if the payload ever grows a list.
 */
class WatchGlucoseStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Stores whatever arrived, verbatim. Interpretation happens on read.
     *
     * Takes a plain map rather than a `DataMap`: the Play Services type belongs at the edge that
     * receives it, and keeping it out of here is what lets the store be tested without it.
     */
    fun saveLatest(values: Map<String, Any?>) = save(PREFIX_LATEST, values)

    fun savePrefs(values: Map<String, Any?>) = save(PREFIX_PREFS, values)

    /**
     * The last reading, or `null` when nothing usable has arrived.
     *
     * Runs the same version-skew rules as every other reader: unknown trend names degrade,
     * missing keys fall back, implausible values are refused.
     */
    fun loadLatest(): LatestReadingPayload? = LatestReadingPayload.from(PrefixedReader(PREFIX_LATEST))

    /** User preferences, falling back to the standard range when nothing has arrived. */
    fun loadPrefs(): PrefsPayload = PrefsPayload.from(PrefixedReader(PREFIX_PREFS))

    private fun save(
        prefix: String,
        values: Map<String, Any?>,
    ) {
        prefs.edit().apply {
            values.forEach { (key, raw) ->
                when (val value = raw) {
                    is Int -> putInt(prefix + key, value)

                    is Long -> putLong(prefix + key, value)

                    is String -> putString(prefix + key, value)

                    is Boolean -> putBoolean(prefix + key, value)

                    // A key of a type this build does not know is simply not stored. That is
                    // the forward-compatibility rule of the contract, applied at the edge.
                    else -> Unit
                }
            }
            apply()
        }
    }

    /**
     * Reads back through the shared contract so both sides interpret the wire format with one
     * implementation rather than two that can drift apart.
     */
    private inner class PrefixedReader(
        private val prefix: String,
    ) : PayloadReader {
        override fun getInt(
            key: String,
            default: Int,
        ): Int =
            runCatching { prefs.getInt(prefix + key, default) }
                // A value written as a long by a newer sender must not read as absent.
                .getOrElse { prefs.getLong(prefix + key, default.toLong()).toInt() }

        override fun getLong(
            key: String,
            default: Long,
        ): Long =
            runCatching { prefs.getLong(prefix + key, default) }
                .getOrElse { prefs.getInt(prefix + key, default.toInt()).toLong() }

        override fun getString(key: String): String? = prefs.getString(prefix + key, null)
    }

    private companion object {
        const val FILE_NAME = "cgm_glance_watch"

        // One file, two payloads, distinct prefixes: simpler than two files and keeps a single
        // point of truth for what the watch believes.
        const val PREFIX_LATEST = "latest."
        const val PREFIX_PREFS = "prefs."
    }
}
