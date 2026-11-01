package ro.smakeru.cgmglance.provider.dexcom

import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit

/** App-owned seam; the provider neither chooses the active source nor writes to sync. */
interface DexcomReadingSink {
    fun onReading(
        reading: GlucoseReading,
        unit: GlucoseUnit,
        usedFallback: Boolean,
    )

    fun onParseFailure(reason: DexcomParseFailure) = Unit
}

/** Connects the platform-created service to phone wiring, independently of Linx. */
object DexcomProviderBus {
    @Volatile
    var sink: DexcomReadingSink? = null
}
