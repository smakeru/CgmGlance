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
| Verified against | versionName 2.6.1, versionCode 2612 |
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

## The vendor's watch app

Since 2.6.0 the vendor also ships a Wear OS app, under the same package name. It is documented
here because it looks like a second source of readings and is not one.

What it does, verified on a Galaxy Watch 8 with the phone's Bluetooth switched off:

- it talks to the sensor **directly**, independently of the phone, through a foreground service
  (`com.microtechmd.wear.service.MyForegroundService`) on a notification channel the vendor
  named *CGM Direct Connection*;
- readings keep arriving and its own screen keeps updating — observed going 181 → 174, each
  labelled *just now* — while the phone received nothing at all.

So the watch holds a live value. It has no way to hand it over:

| Route | Finding |
| --- | --- |
| Complication | None declared. `cmd package query-services -a android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST` returns only this app's own provider. |
| Tile | None declared. Same check with `androidx.wear.tiles.action.BIND_TILE_PROVIDER`. |
| Notification | Ongoing, but `contentView` and `bigContentView` are both null and the text is the fixed string `Connecting to CGM sensor directly` — unchanged across the whole session while the app itself displayed fresh values. Nothing to parse. |
| Logs | A state line carries `glucoseTime`, which does advance with each reading, but `glucose` stays `0.0`, and the displayed value appears nowhere in the app's output. A reader could learn *when* a reading arrived, never *what* it was — and reading another process's log needs `READ_LOGS`, which an ordinary app cannot hold. |
| Components | `HelloActivity`, `BootCompleteReceiver`, `MyForegroundService`, `WearCommListenerService`, plus androidx startup and WorkManager. No content provider, no broadcast, and the Data Layer is scoped to the package. |

This is the sandbox working as designed, not an oversight to route around. The phone path exists
only because the vendor happens to render the number into a notification, which the system then
shows to listeners; on the watch it renders the number nowhere outside its own process.

When the phone is unreachable, this app's surfaces therefore degrade as designed, and the only
alternative would be speaking to the sensor over BLE — a [non-goal](../../README.md#non-goals).

## Breakage log

Append here whenever the vendor changes something. This section is the point of the document.

| Date | App version | What changed | What we did |
| --- | --- | --- | --- |
| 2026-08-02 | 2.5.0 | Baseline. Named views present, parser on the primary path. | — |
| 2026-09-18 | 2.6.0 | Vendor shipped a Wear OS app and a phone widget. Named views unchanged, parser still on the primary path over 58 consecutive readings. Nothing cleaner exposed: still no broadcast, no data provider, no bindable service, no Health Connect. | — |
| 2026-10-08 | 2.6.1 | Phone-only release; the wear APK stayed at 2.6.0. Declared components and requested permissions identical to 2.6.0. Parser on the primary path over 56 consecutive readings. | — |

## What to check first when it breaks

1. Status screen — does it say *Reading via fallback*? Then the ids were renamed and only the
   heuristic still matches.
2. `adb logcat -s CgmGlance/Linx` — a `parse failed` line names the reason.
3. `views=0` means `RemoteViews.apply` inflated nothing: the vendor's layout references a
   resource we cannot resolve. That is the serious case.
4. Re-run the capture: expand the shade, `uiautomator dump`, and compare the ids against the
   table above.
