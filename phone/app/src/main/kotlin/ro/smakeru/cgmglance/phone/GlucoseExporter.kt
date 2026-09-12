package ro.smakeru.cgmglance.phone

import ro.smakeru.cgmglance.domain.glucose.GlucoseReading

/**
 * How the integration is doing, in the terms the status screen needs.
 *
 * Ordered by what the user can act on, the same way [PhoneHealth] is. Everything above
 * [WORKING] is something they can fix; [WORKING] and [WAITING] are not problems.
 */
enum class HealthExportState {
    /**
     * Switched off by the user in settings.
     *
     * Outranks every other state, including a missing permission: someone who has turned the
     * export off should not be shown a fault about a thing they chose not to use.
     */
    DISABLED,

    /**
     * No Health Connect on this device.
     *
     * Either the phone is below Android 9, where the API does not exist, or the provider is
     * simply not installed. Distinguished from [NEEDS_SETUP] because there is no permission to
     * grant — the user has to install or update something first.
     */
    UNAVAILABLE,

    /** Health Connect is there but too old to serve this app. */
    PROVIDER_UPDATE_REQUIRED,

    /** Available, but the user has not granted permission to write glucose. */
    NEEDS_SETUP,

    /** The last write failed. Kept distinct from [NEEDS_SETUP] so the cause is not guessed at. */
    FAILING,

    /** Set up correctly, no reading has come due for export yet. */
    WAITING,

    /** Writing. The only state that means the integration is doing its job. */
    WORKING,
}

/**
 * A snapshot of the export path.
 *
 * [lastWriteMillis] is when a write last succeeded, as wall clock time, so the screen can say
 * how long ago it was. [lastErrorMessage] is carried rather than logged only, because the
 * failures here are provider- and device-specific and the user is the one who has to read them
 * out when something does not appear in their health log.
 */
data class HealthExportStatus(
    val state: HealthExportState,
    val lastWriteMillis: Long? = null,
    val lastErrorMessage: String? = null,
    /** Carried so the screen can state the cadence the user actually chose, not a fixed 15. */
    val intervalMinutes: Int = HealthExportSettings.DEFAULT_INTERVAL_MINUTES,
)

/**
 * Writes glucose out to an external health record.
 *
 * An interface for one real reason, not for symmetry: Samsung's own Data SDK can write straight
 * into Samsung Health, but only for apps Samsung has approved as partners, so it is unusable
 * until that approval exists. Health Connect is the route that works without it, and Samsung
 * Health picks the reading up from there. If the partnership ever lands, a second implementation
 * drops in here and nothing else in the app changes.
 */
interface GlucoseExporter {
    /**
     * Offers a reading for export.
     *
     * Called on every reading; the implementation decides whether this one is due. Must not
     * block — this runs on the notification callback path, which also feeds the watch.
     */
    fun offer(reading: GlucoseReading)

    /** What to show on the status screen. Cheap enough to call on the screen's refresh tick. */
    fun status(): HealthExportStatus
}
