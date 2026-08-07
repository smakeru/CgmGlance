# Provider: LinX (MicroTech AiDEX X)

Everything here was verified against a real device, not inferred. Where something is unverified
it says so.

## The vendor app

LinX is MicroTech Medical's AiDEX X, rebranded for Europe. The same app ships as AiDEX X in
Asia, Wellion AiDEX in Austria and Germany, and GlucoRx in the UK. Assume more names exist.

| | |
| --- | --- |
| Package (mg/dL build) | `com.microtech.aidexx.mgdl` |
| Package (mmol/L build) | `com.microtech.aidexx` |
| Verified against | versionName 2.5.0, versionCode 2501 |
| Sensor cadence | 1 minute claimed; see [Cadence](#cadence) for what actually happens |
| Source | closed, no SDK, no public repository |

**The unit is compiled into the build, not a setting.** Two users of the same sensor can be
running different packages. Nothing may assume a scale from the package name — the parser reads
it from the layout every time.

## What the app does not expose

Checked with `dumpsys package` on a real install. All absent:

- **No broadcast.** The receiver table holds only WorkManager's constraint proxies, a boot
  receiver, ProfileInstaller and Firebase. The older app (`com.microtechmd.cgms.aidex`) did emit
  `…action.BgEstimate` with `AIDEX_BG_VALUE` / `AIDEX_BG_TYPE` / `AIDEX_BG_SLOPE_NAME`; 2.5.0
  does not.
- **No data content provider.** Only a `FileProvider`, androidx startup, and Huawei init
  providers.
- **No bindable service.** Only Firebase messaging and Play Services module metadata.
- **No Health Connect.** Not one `android.permission.health.*` in the requested set.
- **No usable custom permission.** The two it declares are `signature` level, for Getui and
  androidx.

That leaves the notification. Every cleaner route was checked and found closed, not assumed
closed.

## The notification

Two kinds are posted. Only one carries a value.

**Alerts** (group `alert`) look like:

```
android.title = "LinX"
android.text  = "08-02 12:12 Alarmă extrem de înaltă a glicemiei"
```

They state a category and a time and **never a number**. Useless to us. Note the Romanian on a
phone set to English: the vendor app carries its own localisation, independent of the system.

**The ongoing foreground-service notification** (`ONGOING_EVENT | NO_CLEAR | FOREGROUND_SERVICE`)
is the data source:

```
android.title = null
android.text  = null
contentView   = com.microtech.aidexx.mgdl/0x7f0c0101
android.contains.customView = true
```

There is no string to run a regular expression over. The content lives in a custom `RemoteViews`
tree, which `RemoteViews.apply` inflates in our process using the vendor's own resources — a
documented public API, the same one the status bar uses. This is why the app needs no reverse
engineering.

### The layout

Captured with `uiautomator dump` on an expanded shade. The values shown are illustrative — real
readings are health data and stay out of the repository — but the ids, classes and text shapes
are exactly what the device produced:

| View id | Class | Example |
| --- | --- | --- |
| `com.microtech.aidexx.mgdl:id/notice` | RelativeLayout | container |
| `…:id/tv_glucose` | TextView | `112` |
| `…:id/tv_unit` | TextView | `mg/dL` |
| `…:id/tv_time` | TextView | `Chiar acum` |

`bigContentView` is identical to `contentView`. **There is no trend arrow anywhere** — verified
with the notification expanded. The trend this app shows is computed from our own history; see
[Trend](../architecture.md#trend).

`tv_time` is relative and localised, so it can never yield an instant. The reading's timestamp
comes from `StatusBarNotification.postTime`.

### Parsing

Two tiers, in `LinxNotificationParser`:

1. **By resource entry name** — `tv_glucose`, `tv_unit`. Names survive layout rearrangement and
   rebranding, which numeric ids and tree positions do not.
2. **By position** — the first text that reads as a plausible number, excluding the unit label
   and anything containing `:` or `-` (which would be a clock). Degraded but honest; the result
   carries `usedFallback = true` so the status screen can say a vendor update needs attention.

The unit is never guessed. An unrecognised label is refused outright, because guessing turns
6.2 mmol/L into 6 mg/dL — a normal value rendered as a lethal one.

## Cadence

The vendor claims one reading a minute. What the device actually does:

- the ongoing notification is **reposted every 20 to 30 seconds**, and
- in **bursts of four or five within a few milliseconds**, each with its own `postTime`.

`dumpsys notification` aggregate statistics suggested about one update every 2.3 minutes. That
was wrong — the statistics undercount. Measure, do not infer.

Consequences, both handled in `PhoneGlucoseRepository`:

- reposts are collapsed into one entry per 45-second window, anchored on the **first** post of a
  run rather than the last (a window measured from the last entry never expires when reposts
  arrive faster than the window, and an unchanging glucose then collapses to a single point and
  never produces a trend);
- the sync policy transmits roughly one payload per few minutes instead of one per repost.

## Permissions and setup

The user grants notification access once. During development:

```
adb shell cmd notification allow_listener \
  ro.smakeru.cgmglance/ro.smakeru.cgmglance.provider.linx.LinxNotificationListenerService 0
```

The trailing `0` (user id) is required on Samsung; without it the command reports success and
changes nothing.

## Breakage log

Append here whenever the vendor changes something. This section is the point of the document.

| Date | App version | What changed | What we did |
| --- | --- | --- | --- |
| 2026-08-02 | 2.5.0 | Baseline. Named views present, parser on the primary path. | — |

## What to check first when it breaks

1. Status screen — does it say *Reading via fallback*? Then the ids were renamed and only the
   heuristic still matches.
2. `adb logcat -s CgmGlance/Linx` — a `parse failed` line names the reason.
3. `views=0` means `RemoteViews.apply` inflated nothing: the vendor's layout references a
   resource we cannot resolve. That is the serious case.
4. Re-run the capture: expand the shade, `uiautomator dump`, and compare the ids against the
   table above.
