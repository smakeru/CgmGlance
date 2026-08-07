package ro.smakeru.cgmglance.domain.glucose

/**
 * Unit a glucose value is presented in.
 *
 * The domain stores glucose exclusively as whole mg/dL. mmol/L is a presentation concern:
 * keeping a single canonical integer avoids the rounding drift that accumulates when values
 * are converted back and forth between the two scales.
 */
enum class GlucoseUnit(
    /** Decimals conventionally shown for this unit. */
    val decimals: Int,
    /** Label shown to the user, and the label CGM apps typically emit. */
    val label: String,
) {
    MG_DL(decimals = 0, label = "mg/dL"),
    MMOL_L(decimals = 1, label = "mmol/L"),
    ;

    companion object {
        /**
         * Resolves a unit from a free-form label, such as the one the LinX notification exposes
         * in its `tv_unit` view. Tolerant of casing, spacing and a trailing period, because the
         * exact rendering differs across vendor apps and locales.
         *
         * Returns `null` for an unrecognised label. Callers must treat that as "unknown" and
         * discard the reading rather than falling back to a default: guessing wrong turns
         * 5.5 mmol/L into 5 mg/dL, which is the difference between a normal value and a
         * life-threatening one.
         */
        fun fromLabel(raw: String?): GlucoseUnit? {
            val normalised =
                raw
                    ?.lowercase()
                    ?.filterNot { it.isWhitespace() || it == '.' }
                    ?: return null

            return when (normalised) {
                "mg/dl", "mgdl" -> MG_DL
                "mmol/l", "mmoll", "mmol" -> MMOL_L
                else -> null
            }
        }
    }
}
