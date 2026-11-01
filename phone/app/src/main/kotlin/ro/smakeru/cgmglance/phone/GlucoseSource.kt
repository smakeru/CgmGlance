package ro.smakeru.cgmglance.phone

/** Phone-only choice. The watch continues to receive the existing source-independent payload. */
enum class GlucoseSource(
    val label: String,
) {
    LINX("LinX"),
    DEXCOM_ONE_PLUS("Dexcom ONE+"),
    ;

    companion object {
        // Upgrades and unreadable preferences must never replace a working LinX feed with Dexcom.
        fun fromStored(value: String?): GlucoseSource = entries.firstOrNull { it.name == value } ?: LINX
    }
}
