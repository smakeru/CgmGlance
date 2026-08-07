package ro.smakeru.cgmglance.phone

import android.content.Context
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.RangeThresholds

/**
 * The user's target range and preferred unit.
 *
 * Unlike the freshness thresholds, these are genuinely theirs to set: a target range is a
 * personal clinical decision. Freshness is a protection mechanism and stays fixed.
 *
 * Anything unreadable falls back to the standard range rather than refusing to load. A malformed
 * preference should cost the user their customisation, not their glucose display.
 */
class PhonePreferencesStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun loadThresholds(): RangeThresholds {
        val defaults = RangeThresholds.default
        return RangeThresholds.create(
            urgentLowMgdl = prefs.getInt(KEY_URGENT_LOW, defaults.urgentLowMgdl),
            lowMgdl = prefs.getInt(KEY_LOW, defaults.lowMgdl),
            highMgdl = prefs.getInt(KEY_HIGH, defaults.highMgdl),
        ) ?: defaults
    }

    fun loadUnit(): GlucoseUnit =
        prefs.getString(KEY_UNIT, null)?.let { name ->
            GlucoseUnit.entries.firstOrNull { it.name == name }
        } ?: GlucoseUnit.MG_DL

    /**
     * Writes both together.
     *
     * `commit` rather than `apply`: this runs from a user action, not a hot path, and the value
     * is immediately handed to the watch. Losing the write while the watch already has the new
     * range would leave the two disagreeing until the next edit.
     */
    fun save(
        thresholds: RangeThresholds,
        unit: GlucoseUnit,
    ) {
        prefs
            .edit()
            .putInt(KEY_URGENT_LOW, thresholds.urgentLowMgdl)
            .putInt(KEY_LOW, thresholds.lowMgdl)
            .putInt(KEY_HIGH, thresholds.highMgdl)
            .putString(KEY_UNIT, unit.name)
            .commit()
    }

    private companion object {
        const val FILE_NAME = "cgm_glance_prefs"
        const val KEY_URGENT_LOW = "urgentLowMgdl"
        const val KEY_LOW = "lowMgdl"
        const val KEY_HIGH = "highMgdl"
        const val KEY_UNIT = "unit"
    }
}
