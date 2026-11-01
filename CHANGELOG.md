# Changelog

Notable changes per version. Phone and watch may be updated independently: the wire format only
ever gains keys. Wear version codes use the one million offset described in the
[README](README.md#modules).

Entries record what changed and, where it is not obvious, why — the reasoning belongs in
[docs/decisions.md](docs/decisions.md), and this file links to it rather than repeating it.

## 1.2 — unreleased

Phone `versionCode` 3, wear `versionCode` 1000003. Sync schema 2 only adds a key, so either side
may still be updated first: a 1.1 watch ignores the new threshold, and a 1.2 watch paired with a
1.1 phone derives it. Both APKs must still share a signing certificate.

### Added

- **Dexcom ONE+ notification provider**, exact package `com.dexcom.d1plus`, with explicit units,
  standard-extras and custom-view parsing, conservative ambiguity/error rejection and synthetic
  tests. Basic live mg/dL acquisition checked with ONE+ 1.9.0; failure states and sustained
  operation remain unvalidated. See the [contract and limitations](docs/providers/dexcom-one-plus.md).
- **Phone-only source selection** for the watch and Health Connect. LinX is the default and
  retains priority during overlap; sources never alternate based on notification order or
  switch silently on a LinX outage. Dexcom is selected explicitly when ready.
- **Independent histories and trends** for both sensors, preserving the existing LinX history
  on upgrade. A source switch resends the selected cached reading without changing its age.
- **Configurable home-screen widgets**, with multiple independent instances following LinX,
  Dexcom ONE+ or the selected phone source. Each shows its source, glucose, trend and age;
  tapping opens the matching official app. A Dexcom widget does not replace LinX on the watch.
- **Source-aware phone diagnostics**, including the last notification-parser rejection and
  notification permission for the selected provider's service.

- **Very high threshold**, 260 mg/dL by default and editable in settings. Above it the value is
  shown in the same red as an urgent low on the widget, tile and watch app. A custom high saved
  by 1.1 above 260 is kept, with very high placed just above it.
- **Range colours on the phone widget**, from one palette shared with the watch so the two never
  disagree. The widget is redesigned: a blue gradient tile, with the range colour on the number, an accent
  bar and a soft halo from the top-right corner.

### Changed

- **In range is now green**, not white, on the tile, the watch app and the widget. Red for urgent
  low and very high, orange for low, yellow for high, grey once a reading is stale.
- The LinX notification-access entry is renamed from "CGM Glance — read LinX glucose" to
  **CGM Glance — LinX**, matching **CGM Glance — Dexcom ONE+**, so the two entries Android lists
  read as parts of one app. An already granted permission is unaffected by the rename.
- Settings and status wording cover both providers. Background readings do not export competing
  values or reset the selected source's silence watchdog.
- Each source choice explains in small secondary text that only glucose is acquired and trend
  is calculated by CGM Glance. The official Dexcom arrow remains intentionally unused: its
  accessibility description is static, and exact matching of internal images is not a stable
  data contract.

### Fixed

- ONE+ 1.9.0 inserts WORD JOINER (U+2060) in `mg/dL` and `mmol/L`. Normalise that invisible
  character before parsing; previously Quick Glance was rejected with `UNIT_MISSING`.
- Recognise the inspected Quick Glance value/unit IDs as the primary path; ignore non-reading
  group summaries and foreground-service notices instead of reporting missing units for them.

## 1.1 — 2026-09-12

Phone `versionCode` 2, wear `versionCode` 1000002.

### Added

- **Glucose export to Health Connect**, off by default and opted into from settings. Writes one
  reading on a chosen interval, fifteen minutes by default, floored at five and capped at three
  hours. Samsung Health reads the values from Health Connect, so they appear there without this
  app ever talking to Samsung directly.

  Health Connect is the destination rather than a detour: Samsung's own Data SDK can insert into
  Samsung Health, but only for apps Samsung has approved as partners. Developer mode waives the
  signature check, not the partnership, and the write path is gated behind an access code issued
  on approval — with no exemption for personal use.

  The export is deliberately isolated behind `GlucoseExporter`, so a Samsung Data SDK
  implementation can be dropped in later without touching anything else. Nothing is ever read
  back out of Health Connect.

- **Status card for the export**, kept out of the main health verdict. An export that stopped
  must not report an app that is still feeding the watch as broken. It says "written to Health
  Connect" and never "in Samsung Health": whether Samsung Health picked a record up is not
  observable from here.

- **Build version shown beside the app name**, read from `BuildConfig` so it cannot drift from
  `versionName`.

- **"No watch app found" as a distinct verdict** from "watch not reachable". The two need
  opposite responses — one resolves itself when the watch comes back in range, the other never
  does — and reporting both as unreachable sent anyone with a missing or differently-signed
  watch app off to wait for a reconnection that was never coming. Matters most for a fresh
  install from source, where the two APKs are the easiest thing to get half-right.

### Fixed

- **The watch was reported as reachable when it could not receive anything.** The status screen
  asked the node client for connected nodes, which reports the Wear pairing — so a watch with no
  app installed, or one signed with a different certificate, counted as connected. The Data Layer
  refuses a certificate mismatch silently, so this showed a working sync while nothing arrived.
  The phone now looks for a capability the watch advertises, which is only visible across a link
  that actually works.

- **The capability was stripped from release builds.** It is never referenced from code — Play
  services reads it out of the package at runtime — so the resource shrinker removed it. Nothing
  failed at build time and nothing threw at runtime; the watch simply stopped advertising it.
  Held by `wear/app/src/main/res/raw/keep.xml`.

### Changed

- The unreachable-watch message no longer offers "out of range or switched off" as the only
  explanation, which is the one cause it cannot have when the app is missing or mismatched.

- Wear `versionName` aligned to the phone's. The two had drifted to `0.1.0` and `1.1`.

## 0.1.0

Initial version: CGM glucose from the official vendor app, mirrored to a Wear OS complication
and tile. See the [README](README.md) for what it does and, more importantly, what it refuses
to do.
