package ro.smakeru.cgmglance.sync

import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.RangeThresholds
import ro.smakeru.cgmglance.domain.glucose.TrendArrow

/**
 * Minimal reader over a key/value payload.
 *
 * Exists so the encoding rules can be written and tested without Android. The watch and phone
 * implement it over `DataMap`; tests implement it over a plain map.
 *
 * Every accessor takes a default rather than throwing: a key absent because the sender is an
 * older version is an expected condition, not an error.
 */
interface PayloadReader {
    fun getInt(
        key: String,
        default: Int,
    ): Int

    fun getLong(
        key: String,
        default: Long,
    ): Long

    fun getString(key: String): String?
}

/** [PayloadReader] over an ordinary map. Used by tests and by any non-Android caller. */
class MapPayloadReader(
    private val values: Map<String, Any?>,
) : PayloadReader {
    override fun getInt(
        key: String,
        default: Int,
    ): Int = (values[key] as? Number)?.toInt() ?: default

    override fun getLong(
        key: String,
        default: Long,
    ): Long = (values[key] as? Number)?.toLong() ?: default

    override fun getString(key: String): String? = values[key] as? String
}

/**
 * The latest reading, as it travels from phone to watch.
 *
 * The trend is computed on the phone and carried here rather than recomputed on the watch: the
 * phone holds the longer history and does the work once. The watch still judges *freshness*
 * itself, from [timestampMillis] against its own clock, because that is the one judgement it
 * must be able to make when the phone is unreachable.
 */
class LatestReadingPayload(
    val reading: GlucoseReading,
    val trend: TrendArrow,
    val senderNowMillis: Long,
    val sequence: Long,
) {
    fun toMap(): Map<String, Any> =
        mapOf(
            SyncContract.KEY_SCHEMA_VERSION to SyncContract.SCHEMA_VERSION,
            SyncContract.KEY_SEQUENCE to sequence,
            SyncContract.KEY_MGDL to reading.mgdl,
            SyncContract.KEY_TIMESTAMP_MILLIS to reading.timestampMillis,
            SyncContract.KEY_SENDER_NOW_MILLIS to senderNowMillis,
            SyncContract.KEY_TREND to trend.name,
        )

    companion object {
        /**
         * Reads a payload, or returns `null` when it cannot describe a real reading.
         *
         * Unknown values degrade instead of failing: a trend name this build has never heard
         * of becomes [TrendArrow.UNKNOWN], because a newer phone paired with an older watch is
         * routine and must still show a glucose number.
         */
        fun from(reader: PayloadReader): LatestReadingPayload? {
            val mgdl = reader.getInt(SyncContract.KEY_MGDL, default = -1)
            val timestamp = reader.getLong(SyncContract.KEY_TIMESTAMP_MILLIS, default = 0L)
            val reading = GlucoseReading.create(mgdl, timestamp) ?: return null

            val trend =
                reader
                    .getString(SyncContract.KEY_TREND)
                    ?.let { name -> TrendArrow.entries.firstOrNull { it.name == name } }
                    ?: TrendArrow.UNKNOWN

            return LatestReadingPayload(
                reading = reading,
                trend = trend,
                // Falling back to the reading's own timestamp means "assume no skew", which is
                // what an older sender that never wrote this key effectively implies.
                senderNowMillis = reader.getLong(SyncContract.KEY_SENDER_NOW_MILLIS, timestamp),
                sequence = reader.getLong(SyncContract.KEY_SEQUENCE, default = 0L),
            )
        }
    }
}

/**
 * User preferences the watch needs to classify and format on its own.
 *
 * Sent separately from readings and far less often: these change when the user edits a setting,
 * not every couple of minutes.
 */
class PrefsPayload(
    val unit: GlucoseUnit,
    val thresholds: RangeThresholds,
) {
    fun toMap(): Map<String, Any> =
        mapOf(
            SyncContract.KEY_SCHEMA_VERSION to SyncContract.SCHEMA_VERSION,
            SyncContract.KEY_UNIT to unit.name,
            SyncContract.KEY_URGENT_LOW_MGDL to thresholds.urgentLowMgdl,
            SyncContract.KEY_LOW_MGDL to thresholds.lowMgdl,
            SyncContract.KEY_HIGH_MGDL to thresholds.highMgdl,
        )

    companion object {
        /**
         * Reads preferences, substituting defaults for anything missing or unusable.
         *
         * Never returns `null`: preferences are cosmetic compared with the reading itself, and
         * a watch that refused to render because a threshold was malformed would be worse than
         * one showing the standard range.
         */
        fun from(reader: PayloadReader): PrefsPayload {
            val unit =
                reader
                    .getString(SyncContract.KEY_UNIT)
                    ?.let { name -> GlucoseUnit.entries.firstOrNull { it.name == name } }
                    ?: GlucoseUnit.MG_DL

            val defaults = RangeThresholds.default
            val thresholds =
                RangeThresholds.create(
                    urgentLowMgdl = reader.getInt(SyncContract.KEY_URGENT_LOW_MGDL, defaults.urgentLowMgdl),
                    lowMgdl = reader.getInt(SyncContract.KEY_LOW_MGDL, defaults.lowMgdl),
                    highMgdl = reader.getInt(SyncContract.KEY_HIGH_MGDL, defaults.highMgdl),
                ) ?: defaults

            return PrefsPayload(unit, thresholds)
        }
    }
}
