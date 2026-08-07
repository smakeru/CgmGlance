# Decision log

What was chosen, what was rejected, and why. A decision log rather than formal ADRs: the project
has one maintainer, and twenty files with ceremony headers would be filed and never read.

Entries are appended, not edited. When one is reversed, add a new entry that supersedes it.

## Structure

**Native Kotlin only.** Rejected Flutter and React Native outright: complications and tiles are
`Service` subclasses bound by the system, so a cross-platform layer would need a native shim
that *is* the whole app. Rejected KMP as unnecessary — iOS physically cannot host a Wear OS
complication.

**No dependency injection framework.** See [Architecture](architecture.md#dependency-injection).
Migration trigger stated there rather than left to taste.

**`:core:domain` is a plain JVM module.** Enforced by its build file. This is the constraint that
keeps the safety-critical logic testable in milliseconds and identical on both devices.

**Modules kept to five.** The approved design had fourteen. With one maintainer and six screens,
feature modules buy build time and ceremony and nothing else; the seams that matter — pure domain,
frozen wire contract, provider isolation — are all present.

## Safety

**Freshness is fixed, target range is configurable.** A target range is a personal clinical
decision. Freshness is a protection mechanism, and one the user can widen to "stop bothering me"
has been disabled at the moment it mattered.

**Freshness thresholds 2 / 10 / 20 minutes**, with `AGING` carrying no visual degradation. The
fresh window is narrower than the vendor's repost interval, so `AGING` is the ordinary state; a
warning that is on most of the time is not a warning.

**No glucose alarms, ever.** See [Non-goals](../README.md#non-goals). Reopened once and declined
again on 2026-08-06, with the additional finding that the vendor's own alarms already bridge to
the watch — our version would be strictly less reliable redundancy.

**Values are refused rather than defaulted.** An unrecognised unit, an implausible number, an
inconsistent threshold set: all rejected. The alternative silently reinterprets a scale, and
6.2 mmol/L rendered as 6 mg/dL is a normal value shown as a lethal one.

**Trend by 15-minute regression, not by two-point difference.** Sensor noise alone produces an
apparent 3 mg/dL per minute across a two-minute gap. `TrendCalculatorTest` holds the fixture that
demonstrates it.

## Wire format

**Flat `DataMap` primitives, never a blob.** Phone and watch are separate Play artifacts updated
days apart. A flat map degrades one field at a time; a blob written by a newer writer is
unreadable in its entirety.

**Enums travel by name, not ordinal.** Reordering an enum must not silently change meaning across
a version boundary.

**Sequence counter seeded from the clock.** The Data Layer deduplicates by content, so a counter
restarting at zero after a process death would replay numbers the watch has already seen — and
those readings would fail to arrive with no error anywhere.

## Platform

**`SharedPreferences` on the watch, not DataStore.** Contrary to the approved design. Eight
primitives on a latency-sensitive path read by three system-driven lifecycles; DataStore's
strengths do not apply and its coroutine surface would have to be threaded into
`TileService.onTileRequest`, which returns a future.

**`AlarmManager` for the silence watchdog, not WorkManager.** One self-rescheduling alarm, not a
job queue, and no new dependency in a module that has almost none.

**Complications are push-only.** The platform's minimum poll period is five minutes, costs
battery on every tick regardless of change, and would still lag a reading by up to that long.

**The arrow travels in the complication's text, not its title.** Found on a real Galaxy Watch: an
arc slot renders text and drops the title silently, so the arrow vanished on exactly the layouts
this app exists for.

**`<queries>` with named packages, never `QUERY_ALL_PACKAGES`.** Adding a provider means adding
one line, which keeps the set of apps we can even see auditable at a glance.

**Wear minSdk 30, phone 26.** Wear OS 2 would mean a second tiles rendering path and a device
matrix that cannot realistically be tested, for hardware that is end-of-life.

## Toolchain

**AGP 9 compiles Kotlin itself.** `org.jetbrains.kotlin.plugin.compose` is still required and
separate. `org.jetbrains.kotlin.android` is a hard error.

**compileSdk 36, not 37.** Only `androidx.core` 1.19 demands 37, and it is unused. Every Wear
library the project needs was verified to build against 36.

**No analytics, no crash reporting.** Health data plus a third-party SDK is a trust problem and a
Play Data Safety complication. Declaring "no data collected" is a feature.

**Versions resolved from the live repositories, not from memory.** The ecosystem moved further
than expected — Kotlin 2.4, AGP 9.3, JUnit 6 — and guessing would have cost hours.
