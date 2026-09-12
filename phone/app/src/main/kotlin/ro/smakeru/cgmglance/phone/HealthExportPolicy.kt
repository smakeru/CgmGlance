package ro.smakeru.cgmglance.phone

import ro.smakeru.cgmglance.domain.glucose.GlucoseReading

/**
 * Decides which readings are worth writing out to Health Connect.
 *
 * Readings arrive from LinX roughly once a minute, and a few hundred of them a day is not what
 * a glucose log is for — it would bury any hand-entered measurement and make
 * the day's chart unreadable. One value every fifteen minutes is the default, and about the
 * coarsest sampling that still shows the shape of a meal; the user can change it in settings,
 * within the bounds [HealthExportSettings] enforces.
 *
 * Plain Kotlin on purpose. This is the whole rule, it decides what does and does not reach an
 * external health record, and it is worth testing in milliseconds rather than on a device.
 *
 * Note this is a *minimum spacing*, not a clock-aligned schedule: writes are not forced onto
 * :00/:15/:30/:45. Aligning them would mean either writing a value the sensor did not produce
 * at that moment, or holding a reading back until the slot came round — both worse than simply
 * exporting the real measurement that happens to arrive once the interval has elapsed.
 */
class HealthExportPolicy(
    // A supplier rather than a value, matching how the repository reads the target range: the
    // user can change the interval while the app is running, and the next reading must be
    // judged against the new one rather than at the next restart.
    private val intervalMillis: () -> Long = { HealthExportSettings.default.intervalMillis },
) {
    /**
     * Whether [reading] should be written, given when the last export happened.
     *
     * [lastWrittenMillis] is the *reading* timestamp of the last export, not the moment the
     * write was performed. Comparing source timestamps to each other keeps the spacing honest
     * when readings arrive late in a burst after the CGM app reconnects.
     *
     * A `null` means nothing has been exported yet, which includes a fresh install. That case
     * writes immediately so the user sees the integration working rather than waiting a quarter
     * of an hour to find out whether they set it up correctly.
     */
    fun shouldWrite(
        reading: GlucoseReading,
        lastWrittenMillis: Long?,
    ): Boolean {
        if (lastWrittenMillis == null) return true

        // Guards a rebind replaying an older notification. Without it an out of order reading
        // would be written with a timestamp before one already in Health Connect, and the next
        // in-order reading would then look like it satisfied the interval when it did not.
        if (reading.timestampMillis <= lastWrittenMillis) return false

        return reading.timestampMillis - lastWrittenMillis >= intervalMillis()
    }
}
