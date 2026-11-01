# Phone home-screen widgets — 1.2

These widgets run on the **Android phone launcher**, not on Wear OS. They share their range
colours with the watch tile and app, through `GlucoseRangePalette`.

## Multiple independent instances

Add CGM Glance's **Glucose / Glicemie** widget from the launcher's widget picker. Each widget ID
has its own configuration, saved on the phone:

| Choice | Reading shown | Tap destination |
| --- | --- | --- |
| Follow selected source (default) | The source selected in the phone app for watch/Health Connect | That source's official phone app |
| LinX | The LinX history, regardless of the selected watch source | `com.microtech.aidexx.mgdl`, otherwise `com.microtech.aidexx` |
| Dexcom ONE+ | The separate Dexcom history | `com.dexcom.d1plus` |

If the official app has no launchable activity installed, tapping falls back to CGM Glance.
Every widget displays the actual source name. Configuring a fixed-source widget is read-only
with respect to the main source preference: it must not change the watch or export feed.
The launcher can offer reconfiguration; removing a widget deletes only that widget's choice.

## Display

The widget contains the source label, glucose value, preferred display unit, calculated trend
and reading age. The age uses a launcher-hosted `Chronometer`, so it continues increasing if the
app process is not running. Units follow the phone settings for all widgets.

The same `FreshnessPolicy` used elsewhere applies:

- under 2 minutes: fresh;
- 2–10 minutes: aging, still usable, no warning treatment;
- 10–20 minutes: stale, no trend and explicit old-age wording;
- 20 minutes or older: no numeric reading, explicit expired age;
- no data or excessive future timestamp: no numeric reading/trend.

Colour comes from the user's thresholds in settings, identical to the watch:

| Range | Default | Colour |
| --- | --- | --- |
| Urgent low | below 55 | red |
| Low | 55–69 | orange |
| In range | 70–180 | green |
| High | 181–260 | yellow |
| Very high | above 260 | red |
| Stale or no data | — | grey |

The tile is a blue gradient. The number, its arrow, a thin accent bar on the left and a soft
halo from the top-right corner carry the range colour; the tile never fills with it, so it reads
on any wallpaper and never looks like an alert. A stale reading drops the halo and turns grey.
Each range has its own prepared background, because RemoteViews cannot recolour a gradient.
Colour is display only, never an alarm.

Trend is CgmGlance's regression over **that source only**, not an extracted official arrow.
The widget requests a compact 2×1 initial size and supports resizing; actual launcher dimensions
and accessibility font scaling still need verification on the user's phone.

## Updates and battery

An accepted notification from either source refreshes widgets, even when the source is not
selected for the watch. Preference edits and launcher update/time-change callbacks refresh them
as well. A normal `RECEIVE_BOOT_COMPLETED` permission restores the age timer and expiry schedule
after reboot; package updates also refresh widgets. No network polling, foreground service or
exact-alarm permission is introduced.

A single shared inexact `AlarmManager` refresh is scheduled at the next freshness boundary among
all displayed readings, and is cancelled when there are no widgets or no future boundaries.
Android Doze/OEM restrictions may delay the text/number/trend refresh. **The increasing age must
remain visible; do not assume a value is current because its last rendering is unchanged.**
No glucose alarms, vibration, dosing or treatment advice are provided.

## Device validation pending

The widget code and automated checks do not establish launcher behaviour. Validate multiple
instances, resize/font scaling, source-app taps, language/unit changes, process death/reboot,
Doze, aging/expiry, and the overlap period with both sensors. The Dexcom source itself remains
[experimental](providers/dexcom-one-plus.md): basic 1.9.0 mg/dL acquisition is checked, while
failure-state, timestamp and sustained-operation validation remain pending.
