package ro.smakeru.cgmglance.phone

import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.ReadingFreshness

/**
 * What is wrong, or that nothing is.
 *
 * Ordered by what the user can act on. A revoked permission is both the most likely cause of
 * silence and the only one they can fix in ten seconds, so it outranks everything; a
 * disconnected watch comes last because it usually resolves itself.
 */
enum class PhoneHealth {
    /** Reading glucose and the watch has it. */
    OK,

    /** Notification access was never granted, or the system took it back. */
    NO_PERMISSION,

    /** Permission is in place but nothing has arrived yet. */
    WAITING_FOR_FIRST_READING,

    /** Readings stopped arriving. Usually the CGM app was killed or the sensor dropped out. */
    NOT_RECEIVING,

    /**
     * No watch anywhere is running a compatible copy of this app.
     *
     * Ranked above [WATCH_UNREACHABLE] because the two need opposite responses: this one will
     * never resolve on its own, and telling the user to wait for it would be telling them to
     * wait forever.
     */
    WATCH_APP_MISSING,

    /** Everything works here, but the watch is out of range or switched off. */
    WATCH_UNREACHABLE,
}

/**
 * What is known about the watch end of the link.
 *
 * Derived from a capability lookup rather than a node count. The distinction that matters is
 * between a watch that has the app and is merely away, and no compatible watch at all — the
 * first resolves itself, the second never does. A node count cannot tell them apart, and
 * reporting "unreachable" for a watch that was never going to work sends the user to wait
 * instead of to reinstall.
 */
enum class WatchLink {
    /** A watch running this app is reachable now. */
    CONNECTED,

    /**
     * Cannot reach one right now.
     *
     * Also what a failed lookup reports. That is deliberate: failing to ask is not evidence the
     * app is missing, and "cannot reach it" is the weaker, safer claim of the two.
     */
    UNREACHABLE,

    /**
     * The lookup succeeded and found no watch advertising the capability — anywhere, reachable
     * or not.
     *
     * In practice: the watch app is not installed, or it is signed with a different certificate,
     * which the Data Layer refuses silently.
     */
    NO_COMPATIBLE_APP,
}

/**
 * Everything the status screen shows, already decided.
 *
 * Plain Kotlin: this is the screen's entire logic, and it is worth testing without an emulator.
 */
data class PhoneStatus(
    val permissionGranted: Boolean,
    val lastReading: GlucoseReading?,
    val freshness: ReadingFreshness,
    val ageMinutes: Long?,
    val watchLink: WatchLink,
    val lastSentMillis: Long?,
    val usedFallbackParser: Boolean,
    /**
     * How the Health Connect export is doing.
     *
     * Deliberately absent from [health] below. That verdict answers "is glucose reaching the
     * watch", which is this app's reason to exist; an export that has stopped is worth showing,
     * but turning the main card red over it would report a working app as broken. Defaulted so
     * that tests about the core verdict need not describe an integration they are not testing.
     */
    val healthExport: HealthExportStatus = HealthExportStatus(HealthExportState.UNAVAILABLE),
    val source: GlucoseSource = GlucoseSource.LINX,
    val parseFailure: String? = null,
) {
    val health: PhoneHealth
        get() =
            when {
                !permissionGranted -> {
                    PhoneHealth.NO_PERMISSION
                }

                lastReading == null -> {
                    PhoneHealth.WAITING_FOR_FIRST_READING
                }

                // Both stale and absent mean the same thing to the user: the flow stopped.
                // Distinguishing them here would be precision they cannot act on.
                freshness == ReadingFreshness.STALE || freshness == ReadingFreshness.NO_DATA -> {
                    PhoneHealth.NOT_RECEIVING
                }

                // Ranked above plain unreachability: a missing or mismatched watch app is the
                // one of the two the user has to act on, and it will not fix itself.
                watchLink == WatchLink.NO_COMPATIBLE_APP -> {
                    PhoneHealth.WATCH_APP_MISSING
                }

                watchLink == WatchLink.UNREACHABLE -> {
                    PhoneHealth.WATCH_UNREACHABLE
                }

                else -> {
                    PhoneHealth.OK
                }
            }

    /**
     * Whether the parser is running on its fallback path.
     *
     * Surfaced even when everything else is fine: it is the early warning that a vendor update
     * has changed the notification layout, and the moment to look at it is before the fallback
     * stops matching too.
     */
    val needsAttention: Boolean get() = usedFallbackParser
}
