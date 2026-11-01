package ro.smakeru.cgmglance.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import ro.smakeru.cgmglance.domain.glucose.FreshnessPolicy
import ro.smakeru.cgmglance.domain.glucose.GlucoseRangePalette
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import ro.smakeru.cgmglance.domain.time.Clock

/**
 * The glucose tile: one swipe from the watch face, no watch face slot required.
 *
 * Tiles render only while the user is looking at them, so the work here is deliberately trivial
 * — one synchronous read of a local file. No network, no Data Layer round trip, nothing that
 * could keep the screen waiting.
 */
class GlucoseTileService : TileService() {
    private val clock = Clock { System.currentTimeMillis() }
    private val freshnessPolicy = FreshnessPolicy.default
    private val store by lazy { WatchGlucoseStore(applicationContext) }
    private val texts by lazy { WearTexts(applicationContext) }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val payload = store.loadLatest()
        val prefs = store.loadPrefs()
        val reading = payload?.reading

        val freshness = freshnessPolicy.evaluate(reading, clock)
        val display =
            GlucoseDisplay.of(
                reading = reading,
                trend = payload?.trend ?: TrendArrow.UNKNOWN,
                unit = prefs.unit,
                range = reading?.let { prefs.thresholds.classify(it) },
                freshness = freshness,
                ageMinutes = freshnessPolicy.ageMillis(reading, clock)?.let { it / 60_000L },
            )

        val tile =
            TileBuilders.Tile
                .Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                // A backstop only. The real refresh is pushed by WearDataListenerService the
                // moment a reading arrives; this just bounds how stale a tile can look if a
                // push is ever missed.
                .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MILLIS)
                .setTileTimeline(
                    TimelineBuilders.Timeline
                        .Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry
                                .Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout
                                        .Builder()
                                        .setRoot(layout(display, requestParams.deviceConfiguration))
                                        .build(),
                                ).build(),
                        ).build(),
                ).build()

        return resolved(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        resolved(
            ResourceBuilders.Resources
                .Builder()
                .setVersion(RESOURCES_VERSION)
                .build(),
        )

    /**
     * Everything here is already in memory, so the future is complete before it is returned.
     *
     * `CallbackToFutureAdapter` rather than `ResolvableFuture`: the latter is restricted to
     * androidx's own libraries and lint rejects it outright. Guava would also do, at the cost
     * of megabytes in a watch APK for a single call.
     */
    private fun <T> resolved(value: T): ListenableFuture<T> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(value)
            "immediate"
        }

    private fun layout(
        display: GlucoseDisplay,
        device: DeviceParameters,
    ): LayoutElementBuilders.LayoutElement {
        // Scaled from the screen's own width rather than hardcoded, because watch faces run
        // from about 1.2 to 1.5 inches and a fixed size clips on the small ones.
        val valueSize = (device.screenWidthDp * VALUE_SIZE_RATIO).toFloat()

        return LayoutElementBuilders.Column
            .Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers
                    .Builder()
                    .setSemantics(
                        ModifiersBuilders.Semantics
                            .Builder()
                            .setContentDescription(texts.describe(display))
                            .build(),
                    ).setClickable(
                        // Tapping the tile opens the watch app, matching the complication.
                        ModifiersBuilders.Clickable
                            .Builder()
                            .setId("open")
                            .setOnClick(
                                ActionBuilders.LaunchAction
                                    .Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity
                                            .Builder()
                                            .setPackageName(packageName)
                                            .setClassName(WatchMainActivity::class.java.name)
                                            .build(),
                                    ).build(),
                            ).build(),
                    ).build(),
            ).addContent(
                text(
                    listOf(display.value, display.arrow).filter { it.isNotEmpty() }.joinToString(" "),
                    valueSize,
                    colorFor(display),
                ),
            ).addContent(text(display.unitLabel, SECONDARY_SIZE_SP, GlucoseRangePalette.MUTED))
            .build()
    }

    private fun text(
        value: String,
        sizeSp: Float,
        color: Int,
    ): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text
            .Builder()
            .setText(value)
            .setFontStyle(
                LayoutElementBuilders.FontStyle
                    .Builder()
                    .setSize(DimensionBuilders.sp(sizeSp))
                    .setColor(argb(color))
                    .build(),
            ).build()

    /**
     * Colour carries the range, never an alarm.
     *
     * A stale reading is muted before it is coloured: an amber number that is forty minutes old
     * would read as "you are high right now", which is the one message this app must never send
     * by accident.
     */
    private fun colorFor(display: GlucoseDisplay): Int = GlucoseRangePalette.colorFor(display.range, display.freshness)

    private companion object {
        const val RESOURCES_VERSION = "1"

        /** Backstop refresh. Pushes on arrival are what actually keeps the tile current. */
        const val FRESHNESS_INTERVAL_MILLIS = 5 * 60_000L

        const val VALUE_SIZE_RATIO = 0.28
        const val SECONDARY_SIZE_SP = 14f
    }
}
