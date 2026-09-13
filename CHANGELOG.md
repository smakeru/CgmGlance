# Changelog

Notable changes per released version. Both APKs share a `versionName`; their `versionCode`
values differ by the one million offset described in the [README](README.md#modules).

Entries record what changed and, where it is not obvious, why — the reasoning belongs in
[docs/decisions.md](docs/decisions.md), and this file links to it rather than repeating it.

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
