package ro.smakeru.cgmglance.provider.linx

import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit

/** Receives what the LinX notification yielded. Implemented by the app. */
interface LinxReadingSink {
    fun onReading(
        reading: GlucoseReading,
        unit: GlucoseUnit,
        usedFallback: Boolean,
    )

    /** Reported for the diagnostics screen. Never surfaced to the watch. */
    fun onParseFailure(reason: LinxParseFailure) = Unit
}

/**
 * Connects the system-instantiated notification listener to the application's object graph.
 *
 * A deliberate compromise, and the only mutable global in the project. `NotificationListenerService`
 * is constructed by the platform, so it cannot take anything through its constructor; the
 * alternatives are a service locator with wider reach or a dependency injection framework whose
 * whole value here would be this single edge. One documented seam is smaller than either.
 */
object LinxProviderBus {
    @Volatile
    var sink: LinxReadingSink? = null
}
