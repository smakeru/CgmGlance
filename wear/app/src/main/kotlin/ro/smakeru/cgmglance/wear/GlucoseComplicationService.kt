package ro.smakeru.cgmglance.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import ro.smakeru.cgmglance.domain.glucose.FreshnessPolicy
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.ReadingFreshness
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import ro.smakeru.cgmglance.domain.time.Clock

/**
 * Publishes glucose to any watch face, through the standard complication slots.
 *
 * This is the product. A user opens the watch face editor, picks "My CGM", and the number
 * appears — no custom watch face, no vendor lock-in, on any Wear OS device.
 *
 * Freshness is judged here, from the stored timestamp against **this watch's** clock. The phone
 * is not asked and is not trusted to say; when the phone is unreachable is exactly when the
 * answer matters, and it is exactly when the phone cannot provide it.
 */
class GlucoseComplicationService : SuspendingComplicationDataSourceService() {
    private val clock = Clock { System.currentTimeMillis() }
    private val freshnessPolicy = FreshnessPolicy.default
    private val store by lazy { WatchGlucoseStore(applicationContext) }
    private val texts by lazy { WearTexts(applicationContext) }

    /**
     * Opens the watch app when the complication is tapped.
     *
     * `FLAG_IMMUTABLE` because nothing outside this app has any business altering where the tap
     * leads, and on Android 12 and up a mutable intent without a declared reason is rejected.
     */
    private val tapAction: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, WatchMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Shown in the watch face editor before the complication is chosen.
     *
     * The editor calls this with no data, possibly before anything has ever synced. Returning
     * null here leaves a blank entry in the picker, and a blank entry does not get picked —
     * which would sink the one requirement this whole app exists to satisfy.
     */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        render(
            display =
                GlucoseDisplay(
                    value = "112",
                    arrow = "↗",
                    unit = GlucoseUnit.MG_DL,
                    range = null,
                    freshness = ReadingFreshness.FRESH,
                    trend = TrendArrow.RISING_SLIGHTLY,
                    ageMinutes = 0,
                    gaugeValue = 112f,
                    // Deliberately the value the watch face editor shows before anything has
                    // synced. A blank entry in the picker does not get picked.
                ),
            type = type,
        )

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val payload = store.loadLatest()
        val prefs = store.loadPrefs()
        val reading = payload?.reading

        val freshness = freshnessPolicy.evaluate(reading, clock)
        val ageMinutes = freshnessPolicy.ageMillis(reading, clock)?.let { it / 60_000L }

        val display =
            GlucoseDisplay.of(
                reading = reading,
                trend = payload?.trend ?: TrendArrow.UNKNOWN,
                unit = prefs.unit,
                range = reading?.let { prefs.thresholds.classify(it) },
                freshness = freshness,
                ageMinutes = ageMinutes,
            )

        return render(display, request.complicationType)
    }

    private fun render(
        display: GlucoseDisplay,
        type: ComplicationType,
    ): ComplicationData? {
        val description = PlainComplicationText.Builder(texts.describe(display)).build()

        return when (type) {
            // The common case. The arrow rides in the text rather than the title, because a
            // narrow slot drops the title without warning and the arrow is half the point.
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData
                    .Builder(
                        text = PlainComplicationText.Builder(display.compact).build(),
                        contentDescription = description,
                    ).setTapAction(tapAction)
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData
                    .Builder(
                        text =
                            PlainComplicationText
                                .Builder(
                                    listOf(display.value, display.arrow)
                                        .filter { it.isNotEmpty() }
                                        .joinToString(" "),
                                ).build(),
                        contentDescription = description,
                    ).setTitle(PlainComplicationText.Builder(display.unitLabel).build())
                    .setTapAction(tapAction)
                    .build()
            }

            // Arc and gauge faces. The arc empties while the number is still shown, so a
            // reading that has stopped describing the present stops looking like a measurement.
            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData
                    .Builder(
                        value = display.gaugeValue ?: GlucoseDisplay.GAUGE_MIN,
                        min = GlucoseDisplay.GAUGE_MIN,
                        max = GlucoseDisplay.GAUGE_MAX,
                        contentDescription = description,
                    ).setText(PlainComplicationText.Builder(display.compact).build())
                    .setTapAction(tapAction)
                    .build()
            }

            // Declared unsupported rather than approximated: a face asking for an image slot
            // gets nothing from us until real vector arrows ship, which is honest and lets the
            // face fall back to another provider.
            else -> {
                null
            }
        }
    }
}
