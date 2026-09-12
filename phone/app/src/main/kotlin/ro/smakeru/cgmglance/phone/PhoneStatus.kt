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

    /** Everything works here, but the watch is out of range or switched off. */
    WATCH_UNREACHABLE,
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
    val watchConnected: Boolean,
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

                !watchConnected -> {
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
