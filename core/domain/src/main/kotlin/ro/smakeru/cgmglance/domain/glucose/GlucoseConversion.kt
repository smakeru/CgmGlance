package ro.smakeru.cgmglance.domain.glucose

import kotlin.math.roundToInt

/**
 * Conversion between the two glucose scales.
 *
 * The factor is fixed by the molar mass of glucose, so it is a physical constant rather than a
 * tunable. It is spelled out here once; no other part of the codebase may hardcode it.
 */
object GlucoseConversion {
    /** 1 mmol/L of glucose equals this many mg/dL. */
    const val MG_DL_PER_MMOL_L: Double = 18.0182

    /**
     * Converts a canonical mg/dL reading to mmol/L, unrounded.
     *
     * Rounding is left to the presentation layer, which knows how many decimals the target
     * surface can show — a watch complication has room for fewer characters than a phone screen.
     */
    fun mgdlToMmol(mgdl: Int): Double = mgdl / MG_DL_PER_MMOL_L

    /**
     * Converts an mmol/L reading to the canonical whole mg/dL representation.
     *
     * Rounds to nearest rather than truncating, so a round trip through both units does not
     * drift downwards over repeated conversions.
     */
    fun mmolToMgdl(mmol: Double): Int = (mmol * MG_DL_PER_MMOL_L).roundToInt()
}
