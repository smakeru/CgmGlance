package ro.smakeru.cgmglance.phone

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import ro.smakeru.cgmglance.domain.glucose.FreshnessPolicy
import ro.smakeru.cgmglance.domain.glucose.GlucoseConversion
import ro.smakeru.cgmglance.domain.glucose.GlucoseRangePalette
import ro.smakeru.cgmglance.domain.glucose.GlucoseUnit
import ro.smakeru.cgmglance.domain.glucose.ReadingFreshness
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import ro.smakeru.cgmglance.domain.time.Clock
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Phone launcher widgets only. Each ID owns its source choice; null follows the watch source. */
class GlucoseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        refresh(context)
    }

    override fun onEnabled(context: Context) {
        refresh(context)
    }

    override fun onDisabled(context: Context) {
        cancelRefresh(context)
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        val editor = preferences(context).edit()
        appWidgetIds.forEach { editor.remove(it.toString()) }
        editor.apply()
        refresh(context)
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            -> refresh(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "ro.smakeru.cgmglance.phone.WIDGET_FRESHNESS"
        private val policy = FreshnessPolicy.default

        private fun preferences(context: Context) =
            context.getSharedPreferences("glucose_widgets", Context.MODE_PRIVATE)

        internal fun loadSource(
            context: Context,
            id: Int,
        ): GlucoseSource? =
            preferences(context).getString(id.toString(), null)?.let { stored ->
                GlucoseSource.entries.firstOrNull { it.name == stored }
            }

        internal fun saveSource(
            context: Context,
            id: Int,
            source: GlucoseSource?,
        ): Boolean {
            val editor = preferences(context).edit()
            if (source == null) editor.remove(id.toString()) else editor.putString(id.toString(), source.name)
            return editor.commit()
        }

        /** Called by the app for readings from either source and whenever preferences change. */
        @JvmStatic
        @Synchronized
        fun refresh(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, GlucoseWidgetProvider::class.java))
            if (ids.isEmpty()) {
                cancelRefresh(appContext)
                return
            }
            val app = appContext as CgmGlanceApplication
            val now = System.currentTimeMillis()
            val elapsed = SystemClock.elapsedRealtime()
            val clock = Clock { now }
            val unit = app.preferences.loadUnit()
            val thresholds = app.preferences.loadThresholds()
            var nextBoundary: Long? = null

            ids.forEach { id ->
                val snapshot = app.widgetSnapshot(loadSource(appContext, id))
                val reading = snapshot.latest
                val freshness = policy.evaluate(reading, clock)
                val views = RemoteViews(appContext.packageName, R.layout.glucose_widget)
                views.setTextViewText(R.id.widget_source, snapshot.source.label)
                views.setTextViewText(R.id.widget_unit, unit.label)
                val value =
                    if (reading != null && freshness != ReadingFreshness.NO_DATA) {
                        when (unit) {
                            GlucoseUnit.MG_DL -> {
                                reading.mgdl.toString()
                            }

                            GlucoseUnit.MMOL_L -> {
                                String.format(
                                    Locale.getDefault(),
                                    "%.1f",
                                    GlucoseConversion.mgdlToMmol(reading.mgdl),
                                )
                            }
                        }
                    } else {
                        "—"
                    }
                views.setTextViewText(R.id.widget_value, value)
                views.setTextViewText(
                    R.id.widget_trend,
                    if (freshness.allowsTrend) trendSymbol(snapshot.trend) else "",
                )
                // Same locale as the value above, so "7,2" is never followed by "+0.3".
                val delta = snapshot.delta?.takeIf { freshness.allowsTrend }
                val decimalSeparator = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
                views.setTextViewText(R.id.widget_delta, delta?.format(unit, decimalSeparator).orEmpty())
                views.setViewVisibility(R.id.widget_delta, if (delta != null) View.VISIBLE else View.GONE)
                // Same palette and thresholds as the watch, so the wrist and the home screen
                // never disagree. An old reading is muted rather than coloured.
                val color = GlucoseRangePalette.colorFor(reading?.let(thresholds::classify), freshness)
                views.setTextColor(R.id.widget_value, color)
                views.setTextColor(R.id.widget_trend, color)
                views.setTextColor(R.id.widget_delta, color)
                views.setInt(R.id.widget_accent, "setColorFilter", color)
                views.setInt(R.id.widget_root, "setBackgroundResource", backgroundFor(color))
                views.setTextViewText(
                    R.id.widget_status,
                    appContext.getString(
                        when (freshness) {
                            ReadingFreshness.FRESH, ReadingFreshness.AGING -> R.string.widget_reading
                            ReadingFreshness.STALE -> R.string.widget_stale
                            ReadingFreshness.NO_DATA -> R.string.widget_no_current_data
                        },
                    ),
                )
                // Chronometer runs in the launcher, even when our inexact alarm is delayed.
                // It states elapsed age rather than leaving an old value looking current.
                // A small accepted future clock skew is clamped to zero for presentation only.
                val age = policy.ageMillis(reading, clock)
                val usableAge = age != null && age >= -FreshnessPolicy.CLOCK_SKEW_TOLERANCE_MILLIS
                views.setViewVisibility(R.id.widget_age, if (usableAge) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widget_status, if (usableAge) View.GONE else View.VISIBLE)
                views.setViewVisibility(
                    R.id.widget_unit,
                    if (freshness ==
                        ReadingFreshness.NO_DATA
                    ) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    },
                )
                if (usableAge) {
                    views.setChronometer(
                        R.id.widget_age,
                        elapsed - age.coerceAtLeast(0L),
                        appContext.getString(
                            when (freshness) {
                                ReadingFreshness.STALE -> R.string.widget_stale_age_format
                                ReadingFreshness.NO_DATA -> R.string.widget_expired_age_format
                                else -> R.string.widget_age_format
                            },
                        ),
                        true,
                    )
                } else {
                    views.setChronometer(R.id.widget_age, elapsed, null, false)
                }
                views.setOnClickPendingIntent(R.id.widget_root, openSource(appContext, id, snapshot.source))
                manager.updateAppWidget(id, views)

                if (reading != null) {
                    // Reuse the domain boundaries, including recovery from an excessive future stamp.
                    val boundaries =
                        longArrayOf(
                            reading.timestampMillis - FreshnessPolicy.CLOCK_SKEW_TOLERANCE_MILLIS,
                            reading.timestampMillis + policy.freshLimitMillis,
                            reading.timestampMillis + policy.agingLimitMillis,
                            reading.timestampMillis + policy.staleLimitMillis,
                        )
                    boundaries.filter { it > now }.minOrNull()?.let { boundary ->
                        nextBoundary = nextBoundary?.let { minOf(it, boundary) } ?: boundary
                    }
                }
            }
            val alarm = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = refreshIntent(appContext)
            alarm.cancel(pending)
            nextBoundary?.let {
                // Inexact, no exact-alarm permission and no periodic polling. Doze/OEM policies
                // may delay hiding the number/trend; the launcher chronometer keeps aging it.
                alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, elapsed + (it - now), pending)
            }
        }

        private fun openSource(
            context: Context,
            id: Int,
            source: GlucoseSource,
        ): PendingIntent {
            val packages =
                when (source) {
                    GlucoseSource.LINX -> listOf("com.microtech.aidexx.mgdl", "com.microtech.aidexx")
                    GlucoseSource.DEXCOM_ONE_PLUS -> listOf("com.dexcom.d1plus")
                }
            val launch =
                packages.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
                    ?: Intent(context, MainActivity::class.java)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Widget ID separates instances; the resolved component separates official sources.
            // UPDATE_CURRENT also replaces the destination when follow-selected changes source.
            return PendingIntent.getActivity(
                context,
                id,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun refreshIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, GlucoseWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun cancelRefresh(context: Context) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(refreshIntent(context))
        }

        /** One prepared background per palette colour; RemoteViews cannot recolour a gradient. */
        private fun backgroundFor(color: Int): Int =
            when (color) {
                GlucoseRangePalette.URGENT -> R.drawable.widget_bg_urgent
                GlucoseRangePalette.LOW -> R.drawable.widget_bg_low
                GlucoseRangePalette.IN_RANGE -> R.drawable.widget_bg_in_range
                GlucoseRangePalette.HIGH -> R.drawable.widget_bg_high
                else -> R.drawable.widget_background
            }

        private fun trendSymbol(trend: TrendArrow): String =
            when (trend) {
                TrendArrow.RISING_RAPIDLY -> "↑↑"
                TrendArrow.RISING -> "↑"
                TrendArrow.RISING_SLIGHTLY -> "↗"
                TrendArrow.STEADY -> "→"
                TrendArrow.FALLING_SLIGHTLY -> "↘"
                TrendArrow.FALLING -> "↓"
                TrendArrow.FALLING_RAPIDLY -> "↓↓"
                TrendArrow.UNKNOWN -> ""
            }
    }
}
