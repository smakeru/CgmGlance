# Provider: Dexcom ONE+

## Status and source

**Implemented in 1.2 and in daily use**, alongside LinX. The provider is phone-only: the watch
receives the same reading payload whichever source is selected.

The only supported vendor package is **`com.dexcom.d1plus`**, Dexcom ONE+. This is not the original
Dexcom ONE (`com.dexcom.dexcomone`), G6, G7, Follow, or the third-party Glucose Watch app.

**Verified 2026-11-01 on ONE+ 1.9.0 (versionCode 17006), in mg/dL**, on a Galaxy S25 Ultra paired
to a Galaxy Watch 8. The real Quick Glance layout was inspected; after the unit fix below, readings
were accepted, shown on the phone and on the home-screen widget, and delivered to the watch. Both
sensors ran in parallel for several hours with LinX kept on the watch, then the watch was switched
to Dexcom. Not yet verified: mmol/L, and the failure cases listed below.

Unit-test values/timestamps are invented. Fixtures preserve only the inspected resource IDs and
formatting, never real glucose values or notification captures.

External evidence for feasibility:

- [xDrip UiBasedCollector](https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/services/UiBasedCollector.java)
  accepts `com.dexcom.d1plus` and reads standard notification titles or custom-view text. This
  implementation does not copy its unit inference: an explicit unit is mandatory here.
- [AndroidAPS G7 / ONE+ documentation](https://androidaps.readthedocs.io/en/latest/CompatibleCgms/DexcomG7.html)
  describes ONE+ acquisition through notification access while the official app owns the sensor.
- [Dexcom ONE+ Wear OS FAQ](https://www.dexcom.com/es-ES/faqs/puedo-ver-los-datos-de-mi-dexcom-one-plus-en-un-reloj-wear-os)
  describes a vendor watch face; its availability on the user's watch/region has not been established.

## What has and has not been checked

A local notification route is supported by those external sources. The installed ONE+ APK has
**not** yet been inspected for broadcasts, exported data providers, bindable services or Health
Connect. Do not infer the absence of those routes from the LinX investigation. Before claiming
validation, inspect the installed package as described in [Adding a provider](../adding-a-provider.md)
and prefer a stable native contract if one is available.

## Setup

1. Install official Dexcom ONE+ and activate its sensor normally.
2. Enable **Quick Glance** in Dexcom; keep notifications and background operation enabled.
3. In Android's notification-access settings, enable the single **CGM Glance** entry. One
   listener reads both providers; choosing the sensor happens inside CGM Glance. When upgrading
   from a version where only the old Dexcom listener had access, grant access again once.
4. For the overlap period, leave **LinX** selected under CGM Glance **Settings → Source for watch
   and Health Connect**. Dexcom readings are collected separately without replacing LinX.
5. When ready, explicitly select **Dexcom ONE+** and save. Silence in LinX never switches sources
   on its own. The source setting survives process death/reboots.
6. A home-screen widget can independently follow LinX, Dexcom, or the selected source. Choosing a
   widget source never changes the watch or Health Connect feed.

The phone status screen identifies the selected source and reports the last parser rejection.
Widget labels identify their actual source; tapping opens that source's official phone app.
The watch continues to show an unlabelled reading, using its existing APK.

## Notification contract currently accepted

- Filter exact package before inspecting notification contents. No `ongoing` flag requirement.
- Read `Notification.EXTRA_TITLE`, `EXTRA_TEXT`, and text from both expanded and collapsed
  `RemoteViews`. No OCR, graph interpretation, or arbitrary-number extraction.
- Accept an entire field such as `124 mg/dL`, `6.2 mmol/L`, or `6,2 mmol/L`; optional English
  glucose label and Unicode trend glyphs are tolerated, not used as a vendor trend.
- Primary custom-view path: `notification_quick_glance`, with `glucose_value_text` and
  `glucose_unit_of_measure_text`, verified in 1.9.0. Resource strings `mgdl_unit_of_measurement`
  and `mmol_unit_of_measurement` insert **WORD JOINER (U+2060)** after `/`. Strip that invisible
  line-breaking control before recognising the explicit unit; never infer the unit.
- Generic separate numeric/unit matching remains a fallback, accepted only when exactly two
  usable texts remain in one representation. Never join fields from different representations.
- Ignore group summaries and non-numeric, non-custom foreground-service notices such as the
  observed "Dexcom app is running". Their lack of units must not overwrite glucose diagnostics.
- Reject missing units, implausible values, fractional mg/dL, conflicting representations, and
  known no-current-reading states. `LOW` / `HIGH` are not converted to invented numeric bounds.
- Time/graph/history resource IDs are excluded from candidate values. An unknown/localised
  layout can still require an additional rejection rule: real captures are mandatory.
- Use `StatusBarNotification.postTime`, not displayed text, as required by the provider contract.
  **This is not guaranteed sensor time.** A repost can stamp an old reading as new. The behaviour
  on signal loss/warmup/error must be observed on the actual device before trusting this route.

Values are constructed through `GlucoseReading.create`, normalised to mg/dL. Trend comes from
CgmGlance's existing regression, using only that sensor's history; it is not the official Dexcom
trend arrow. Same-value reposts use the existing 45-second compaction policy, pending validation
against ONE+'s real cadence.

## Trend decision for phone 1.2

The official arrow was investigated on 2026-11-01. In the inspected Quick Glance layout, the
`arrow_icon` accessibility description is the static `trend_falling` string. Runtime inspection
and a screenshot showed a visible flat arrow while that description still said down. It must
not be used as the official direction.

A separate local prototype matched the displayed drawable exactly against the vendor's own
vector resources, correctly identifying the flat arrow. That is possible, but it depends on
internal images rather than a stable exposed data contract and has not been validated across
all live directions/vendor releases. **It is not used in the product.**

For 1.2, keep CgmGlance's own per-source trend calculation for both LinX and Dexcom. The phone
source selector states, in small secondary text, that only glucose is acquired and trend is
calculated by CgmGlance. Do not describe this arrow as Dexcom's official arrow or claim medical
equivalence. At least three samples spanning six minutes are required; an initial missing arrow
is intentional. No source-arrow payload, persistence or watch change was added.

## Source separation and switching

Each source has its own two-hour repository, trend and history file. LinX retains its old history
file unchanged on upgrade. Only the selected source may drive watch sync, Health Connect export
and the silence watchdog. Background Dexcom readings must not hide a stopped LinX feed.

An explicit source switch publishes the newly selected source's cached reading immediately,
even if its number equals the old source, retaining its original timestamp. It does not append a
sample or export a cached sample as a new measurement. If the new source has no reading yet,
no fabricated clearing value is sent: the watch keeps its previous reading with its original
age until a new one arrives or its existing freshness policy hides it.

## Validation still required

- Investigate cleaner exported contracts and regional/version differences. The inspected
  package/version is confirmed above; no absence of alternative contracts is claimed.
- The inspected 1.9.0 Quick Glance has null standard title/text and the named custom-view fields
  above. Fixtures cover that shape; validate any other shipped layouts separately.
- Check mg/dL and mmol/L, display locale, screen lock, rebind/restart and background restrictions.
- Check sensor warmup, signal loss, brief sensor issue, expired sensor and LOW/HIGH.
- Observe whether reposts can keep an old number apparently fresh; compare displayed sensor age.

Done on 2026-11-01: both sensors together with LinX retaining the watch, a Dexcom widget beside a
LinX widget, explicit switch to Dexcom, separate trends, and both notification permissions.

## Breakage log

| Date | Vendor version | Observation | Follow-up |
| --- | --- | --- | --- |
| 2026-11-01 | Not captured | Initial provisional adapter; synthetic parser tests only | Obtain real notification format |
| 2026-11-01 | 1.9.0 / 17006 | Quick Glance unit contains U+2060: initial parser rejected it with UNIT_MISSING. Strip WORD JOINER, recognise named fields and ignore non-reading summaries/service notices. Live mg/dL acquisition and watch-send path checked after update. | Validate failure states, reading timestamps, sustained operation, mmol/L and watch rendering |
