package ro.smakeru.cgmglance.domain.glucose

/**
 * One colour per range, shared by the watch tile, the watch app and the phone widget.
 *
 * Kept here, as plain ARGB integers, so the three surfaces cannot drift apart: a widget that
 * shows 65 orange while the wrist shows it white would make one of them look wrong.
 *
 * Colour carries the range, never an alarm. A reading that no longer describes the present is
 * muted before it is coloured: a green number forty minutes old would read as "you are fine right
 * now", which is the one message this app must never send by accident.
 */
object GlucoseRangePalette {
    const val URGENT: Int = 0xFFFF5252.toInt()
    const val LOW: Int = 0xFFFFB74D.toInt()
    const val IN_RANGE: Int = 0xFF66BB6A.toInt()
    const val HIGH: Int = 0xFFFFD54F.toInt()
    const val MUTED: Int = 0xFF9E9E9E.toInt()

    fun colorFor(
        range: GlucoseRange?,
        freshness: ReadingFreshness,
    ): Int =
        when {
            freshness == ReadingFreshness.STALE || freshness == ReadingFreshness.NO_DATA -> MUTED
            range == null -> MUTED
            range == GlucoseRange.URGENT_LOW || range == GlucoseRange.VERY_HIGH -> URGENT
            range == GlucoseRange.LOW -> LOW
            range == GlucoseRange.HIGH -> HIGH
            else -> IN_RANGE
        }
}
