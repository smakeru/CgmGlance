# Architecture

## What this app is

A display mirror. The official CGM app owns the sensor, the Bluetooth link, calibration, alarms,
history and the cloud. This app takes the number that app has already computed and puts it on a
Wear OS watch face, through standard complication and tile APIs, so it works on any watch face
without a custom one.

Everything below follows from that. When a decision looks over-cautious, it is usually because
the alternative would have quietly turned a mirror into something that makes claims.

## The chain

```
LinX notification
  → LinxNotificationListenerService     filters by package, ongoing notification only
  → RemoteViewsTextExtractor            inflates the vendor layout, walks the tree
  → LinxNotificationParser              tv_glucose / tv_unit, positional fallback
  → PhoneGlucoseRepository              2h history, repost collapsing, trend fit
  │
  ├─→ SyncDecisionPolicy                decides whether the watch needs to hear
  │   → WatchSyncSender                 DataClient, setUrgent on state change
  │   ═══ Bluetooth ═══
  │   → WearDataListenerService          stores, then pushes the surfaces
  │   → WatchGlucoseStore                SharedPreferences, read by three services
  │   → complication · tile · watch app
  │
  └─→ HealthExportPolicy                one reading per interval, default 15 min
      → HealthConnectExporter           BloodGlucoseRecord, off unless opted in
      → Health Connect → Samsung Health  (the user's own sync, not ours)
```

The two branches are independent on purpose. The sync policy suppresses readings the watch would
gain nothing from; a health log wants a steady sample whether or not the number moved, so the
export is offered every reading and decides for itself.

The **LinX chain above** was verified on a real Galaxy S25 Ultra and Galaxy Watch 8, the export
included — readings reach Health Connect and appear in Samsung Health. This does **not** validate
the new Dexcom ONE+ notification grammar or phone widgets.

### Phone 1.2: two inputs, one selected output

`DexcomNotificationListenerService` is a separate, per-service permission: it filters exact ONE+
package `com.dexcom.d1plus` and passes standard-extras/custom-view text to a pure strict parser.
The [provider contract](providers/dexcom-one-plus.md) records live mg/dL acquisition checked on
ONE+ 1.9.0, including its U+2060 unit-format fix. Failure-state/sustained-operation validation
and inspection for cleaner exported vendor contracts are still pending.

`PhoneGlucoseSources` owns a repository per source. LinX remains selected by default, irrespective
of callback order, temporary silence or Dexcom availability. Settings persist an explicit change
to Dexcom. Only the selected source's accepted readings reach sync, health export and the silence
watchdog; inactive-source readings only update their own history and widgets. Selection and
outbound effects are serialised, so an in-flight old-source callback cannot win after a switch.

Phone home-screen widgets independently choose a fixed source or follow the selected feed. Their
read-only snapshots cannot change the watch source. Widget taps open the actual source's vendor
app, and each instance carries its own configuration. Freshness follows the domain policy; alarm
refreshes are inexact and subject to Android background scheduling. No glucose alarm is added.

The phone sends exactly the same `LatestReadingPayload`; no source field is needed. A cached
source switch preserves the reading's timestamp; if the chosen source has no data yet, the old
watch value simply ages under its existing policy until a reading arrives.

### Range colours, shared

`GlucoseRangePalette` in `:core:domain` holds one ARGB colour per range, and the widget, tile
and watch app all read it, so the home screen and the wrist cannot disagree. Thresholds now have a
fourth boundary, very high (260 by default), coloured like urgent low. It travels as one added
key, schema 2: a 1.1 watch ignores it, and a 1.2 watch paired with a 1.1 phone derives it from the
high threshold rather than discarding a custom range.

## Modules

| Module | Type | Why it exists |
| --- | --- | --- |
| `:core:domain` | plain JVM | The safety-critical logic: units, plausibility, freshness, trend, sync policy. No Android dependency, on purpose — it must be testable in milliseconds and behave identically on both devices. |
| `:core:sync-contract` | plain JVM | The wire format between two separately-updated APKs. Plain JVM so version-skew rules can be tested without a device. |
| `:provider:linx` | Android library | Reads the vendor's notification. Its parsing logic is plain Kotlin and testable against captured layouts; only the `RemoteViews` extraction touches Android. |
| `:provider:dexcom` | Android library | ONE+ notification reader: 1.9.0 live mg/dL acquisition checked; invented-value format tests, further device validation pending. |
| `:phone:app` | Android app | Acquisition, source selection, separate histories, sync, status screen, phone widgets, and the Health Connect export. The export lives here rather than in a module of its own: it is one policy and one writer, and the watch must not carry either. |
| `:wear:app` | Android app | Complication, tile, watch app. |

`:core:domain` being a `kotlin-jvm` module rather than an Android library is enforced by its
build file and is the single constraint that keeps the important logic fast to test. Adding an
Android dependency there is an architecture change, not a convenience.

## The three ideas that shape everything

### Freshness is a domain concept, judged by the reader

A glucose number carries an implicit claim of *now*. The only defence against that claim being
false is to state the age beside it and to stop showing the number once it is meaningless.

`ReadingFreshness` has four states with thresholds at 2, 10 and 20 minutes. The **watch computes
this against its own clock**, never against a flag from the phone: when the phone is unreachable
is exactly when the answer matters, and exactly when the phone cannot supply it.

The fresh window is deliberately narrow, so `AGING` is the ordinary state. It therefore carries
**no visual degradation at all** — only the age appears. A warning that is on most of the time is
not a warning; the visual budget for alarm is spent at `STALE`.

Clock skew between the two devices is tolerated up to the fresh window. Beyond that the age
cannot be defended and the reading is treated as absent, rather than claiming a freshness we
cannot substantiate.

### Trend

LinX publishes no arrow in the observed notification. For both providers we derive our own arrow
from each source's separate history: a least-squares fit over the last 15 minutes, with the
standard ±1 / ±2 / ±3 mg·dL⁻¹·min⁻¹ boundaries. The Dexcom arrow is not copied from the vendor and
must not be presented as its official trend.

A two-point difference was rejected. Sensor noise of ±5–10 mg/dL across a two-minute gap produces
an apparent 3 mg/dL per minute on its own, so the arrow would swing between extremes while
glucose sat still. `TrendCalculatorTest` contains the fixture that demonstrates it.

The fit refuses to answer rather than guess: fewer than three points, a span under six minutes,
or a newest reading too old all yield `UNKNOWN`. A regression drawn across a gap looks more
certain than anything it describes.

### Silence must mean something

Nothing calls this app unless the vendor posts a notification, so the absence of readings is
precisely the condition no reading-driven code can observe. `SilenceWatchdog` therefore pushes a
single alarm forward on every reading; if it ever fires, the flow has been broken for longer than
a working system is ever quiet.

The same reasoning is why glucose alarms are permanently out of scope. Adding vibration would
teach the user that *no buzz* means *fine*, when it could equally mean any of five silent
failures in the chain. See [Non-goals](../README.md#non-goals).

## Wire format

Flat primitives in a `DataMap`, never a serialised blob. Phone and watch are separate Play
artifacts updated days apart, so at any moment an old reader may face a new writer. A flat map
degrades one field at a time; a blob is unreadable in its entirety.

Rules, enforced by `PayloadsTest`:

- fields are only added, never renamed, retyped or removed;
- readers ignore unknown keys and supply defaults for missing ones;
- enums travel **by name**, so ordinals can be reordered safely;
- an unrecognised trend degrades to `UNKNOWN` rather than losing the reading with it;
- `SCHEMA_VERSION` is for diagnostics; a reader must never refuse a payload for having a higher
  one.

A monotonic `sequence` is included because the Data Layer deduplicates by content: two identical
readings must still propagate, since their age changed even though their value did not. The
counter is seeded from the clock, so a process restart cannot replay numbers the watch has
already seen.

## Storage

| Where | What | Why |
| --- | --- | --- |
| Phone | `ReadingHistoryStore` — 2h of readings **per source** | Without it the trend blanks for seven minutes after every process death, which on Samsung is a weekly event. Verified by rebooting the phone. |
| Phone | `PhonePreferencesStore` — range, unit and selected source | Range is the user's clinical choice, unlike freshness; source defaults to LinX and changes only explicitly. |
| Phone | `PhonePreferencesStore` — health export settings and last write | The interval is measured from the last exported *reading*, so it has to outlive the process; without persisting it, every restart would write immediately and the cadence would be whatever the restart rate happened to be. |
| Watch | `WatchGlucoseStore` — latest payload and prefs | Read by three independent, system-driven lifecycles, so it is a file rather than an object any one of them owns. |

`SharedPreferences` rather than DataStore on the watch, contrary to the original design: the
payload is eight primitives read on a latency-sensitive path, a complication request has
milliseconds, and `TileService.onTileRequest` returns a future. DataStore would push coroutine
scaffolding into both for no gain.

## Battery

The sensor reposts every 20–30 seconds. Forwarding each would mean thousands of transmissions a
day to show a number that mostly has not moved.

`SyncDecisionPolicy` transmits only on a change of ≥10 mg/dL, a change of range state, a gap
longer than 15 minutes, or five minutes of silence — and marks urgent only the first three.
`setUrgent` matters: without it the platform may hold a payload for up to half an hour.

Complications are push-only (`UPDATE_PERIOD_SECONDS = 0`). The platform's minimum polling period
is five minutes, costs battery on every tick regardless of whether anything changed, and would
still lag a new reading by up to that long.

Measured on a real device: twelve readings received, one transmitted, across five minutes.

## Dependency injection

None. The graph is a handful of objects with no scopes and no variants, and the components the
platform constructs for us — the notification listener, the complication and tile services —
would each need an escape hatch from any framework anyway.

`LinxProviderBus` and `DexcomProviderBus` connect the platform-created notification listeners to
phone wiring. `NotificationListenerService` cannot take constructor dependencies. These small,
provider-scoped seams keep acquisition independent of source selection and watch transport.

Adopt Hilt when the graph outgrows `CgmGlanceApplication`, not before.
