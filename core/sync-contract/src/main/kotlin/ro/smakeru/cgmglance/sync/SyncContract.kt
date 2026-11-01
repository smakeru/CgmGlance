package ro.smakeru.cgmglance.sync

/**
 * The wire format between the phone and the watch.
 *
 * Treat everything in this file as a published contract. The two APKs are separate Play
 * artifacts and users update them days or weeks apart, so at any moment an old watch may be
 * reading a new phone's payload and the reverse. That is the normal case, not an edge case.
 *
 * Rules that follow from it:
 *  - fields are only ever **added**, never renamed, retyped or removed;
 *  - a reader ignores keys it does not recognise;
 *  - a reader supplies its own default for keys that are missing;
 *  - [SCHEMA_VERSION] increases on every addition, and readers never require equality.
 *
 * Flat primitives rather than a serialised blob, for the same reason: a blob written by a newer
 * writer is unreadable in its entirety by an older reader, while a flat map degrades one field
 * at a time.
 */
object SyncContract {
    /**
     * Incremented whenever a key is added. Readers use it for diagnostics only; they must
     * never refuse a payload merely because the number is higher than they know.
     */
    const val SCHEMA_VERSION: Int = 2

    /**
     * Advertised by the watch, looked for by the phone.
     *
     * The Data Layer only pairs nodes sharing an application id *and* a signing certificate,
     * and a mismatch fails silently — nothing is thrown and nothing arrives. Asking the node
     * client for connected nodes cannot detect that: it answers about the Wear pairing, so a
     * watch with no app installed, or one signed with a different key, still counts as
     * connected. A capability is only visible across a link that actually works, which makes
     * "is the watch reachable" answerable instead of merely plausible.
     *
     * Declared on the watch in `wear/app/src/main/res/values/wear.xml`, which must hold this
     * exact string. Changing it here alone silently breaks the check.
     */
    const val CAPABILITY_WATCH: String = "cgm_glance_watch"

    /** Latest reading. Overwritten in place; only ever one of these. */
    const val PATH_LATEST: String = "/cgm/latest"

    /** User preferences the watch needs in order to classify and format locally. */
    const val PATH_PREFS: String = "/cgm/prefs"

    const val KEY_SCHEMA_VERSION: String = "schemaVersion"

    /**
     * Monotonic counter, incremented on every send.
     *
     * The Data Layer deduplicates by content: an identical payload produces no change event on
     * the receiver. Two consecutive equal readings must still propagate, because their age has
     * changed even though their value has not, so something in the payload has to differ.
     */
    const val KEY_SEQUENCE: String = "sequence"

    const val KEY_MGDL: String = "mgdl"

    /** When the source produced the reading, epoch millis. */
    const val KEY_TIMESTAMP_MILLIS: String = "timestampMillis"

    /**
     * The sender's own clock at the moment of sending, epoch millis.
     *
     * Lets the receiver measure how far the two clocks disagree instead of assuming they
     * agree. Without it a watch running a minute behind reports every reading as arriving
     * from the future.
     */
    const val KEY_SENDER_NOW_MILLIS: String = "senderNowMillis"

    /** [ro.smakeru.cgmglance.domain.glucose.TrendArrow] by name, never by ordinal. */
    const val KEY_TREND: String = "trend"

    /** [ro.smakeru.cgmglance.domain.glucose.GlucoseUnit] by name, never by ordinal. */
    const val KEY_UNIT: String = "unit"

    const val KEY_URGENT_LOW_MGDL: String = "urgentLowMgdl"
    const val KEY_LOW_MGDL: String = "lowMgdl"
    const val KEY_HIGH_MGDL: String = "highMgdl"

    /**
     * Added in schema 2. A 1.1 watch ignores it; a newer watch paired with a 1.1 phone derives
     * it from the high threshold rather than discarding the user's whole range.
     */
    const val KEY_VERY_HIGH_MGDL: String = "veryHighMgdl"
}
