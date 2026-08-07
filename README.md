# CGM Glance

Shows the glucose value your official CGM app already receives on any Wear OS watch face, as a
standard Complication and Tile.

```
      ┌─────────────┐
      │   112 ↗     │   on any watch face, no custom face required
      │   mg/dL     │
      └─────────────┘
```

This app **extends** the official CGM app; it does not replace it. Sensor activation, pairing,
calibration, Bluetooth, alarms, history, cloud sync and account management all remain the
official app's responsibility.

## Status

Working end to end on real hardware: Galaxy S25 Ultra with LinX 2.5.0, paired to a Galaxy
Watch 8. Not published anywhere, no releases, personal use.

| | |
| --- | --- |
| Providers | LinX / MicroTech AiDEX X ([contract](docs/providers/linx.md)) |
| Watch surfaces | Complication (SHORT_TEXT, LONG_TEXT, RANGED_VALUE), Tile, app |
| Phone | status screen, target range settings, silence watchdog |
| Tests | 101, all passing |

## Not a medical device

This app mirrors a value the official app has already computed. It is not certified, not
validated, and must not be used for treatment decisions. Always confirm with the official app or
a blood glucose meter. It deliberately produces **no alarms and no vibration** for glucose
values — see [Non-goals](#non-goals).

## Non-goals

These are permanent, not "not yet":

- Glucose alarms, vibration or any active alerting. Passive display only.
- Insulin dosing, carb counting, prediction or any treatment guidance.
- Direct Bluetooth communication with the sensor.
- Sensor pairing, calibration, firmware updates, account management.
- Reports, statistics, cloud sync.

The reasoning for the first one is worth stating, because it is the item most often reopened:
adding vibration teaches the user that *no buzz* means *you are fine*, when it could equally mean
any of five silent failures between the sensor and the wrist. An alert that works most of the
time is worse than none, because the failures land at night with the phone in another room.
Alarms belong to the app that owns the sensor connection.

## Documentation

| | |
| --- | --- |
| [Architecture](docs/architecture.md) | the chain, the three ideas that shape it, wire format, battery |
| [LinX provider contract](docs/providers/linx.md) | what the vendor exposes, the exact layout, breakage log |
| [Adding a provider](docs/adding-a-provider.md) | recipe for a new sensor |

## Building

Requires a **JDK 17 or newer** to run Gradle. The build pins a Java 21 toolchain for compilation,
so the JDK that launches Gradle does not affect the produced bytecode.

```bash
./gradlew build          # compile, test and check formatting
./gradlew spotlessApply  # fix formatting violations
./gradlew assembleDebug  # both APKs
```

On Windows, if `java -version` reports anything older than 17, point `JAVA_HOME` at a newer JDK
before invoking the wrapper:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat build
```

## Installing

Both APKs must be built on the same machine, or signed with the same key. The Wear Data Layer
only pairs nodes that share an application id **and** a signing certificate; a mismatch produces
no error at all — the watch simply never receives anything.

```bash
adb install -r phone/app/build/outputs/apk/debug/app-debug.apk
```

Then grant notification access: **Settings → Apps → Special access → Notification access**.

For the watch, enable wireless debugging on it and:

```bash
adb pair <watch-ip>:<pairing-port> <code>
adb connect <watch-ip>:<connect-port>
adb -s <watch-ip>:<connect-port> install -r wear/app/build/outputs/apk/debug/app-debug.apk
```

Wear OS 6 uses random ports, not 5555. `adb mdns services` finds the connect port; the pairing
port is only advertised while the pairing screen is open on the watch.

## Modules

| Module | Type | Purpose |
| --- | --- | --- |
| `:core:domain` | Pure JVM | Units, plausibility, freshness, trend, sync policy. No Android dependency, by design. |
| `:core:sync-contract` | Pure JVM | The wire format between the two APKs, with its version-skew rules. |
| `:provider:linx` | Android library | Reads the vendor notification. Parsing logic is plain Kotlin. |
| `:phone:app` | Android app | Acquisition, history, sync, status and settings screens. |
| `:wear:app` | Android app | Complication, tile, watch app. |

Both apps share the application id `ro.smakeru.cgmglance`, which the Data Layer requires. Their
`versionCode` values must still differ, so the wear module is offset by one million.

## Licence

[GPL-3.0](LICENSE).

Chosen to match the diabetes open-source ecosystem this app sits next to — xDrip+ and
AndroidAPS are both GPL — and because a copyleft licence keeps any fork of code that displays
glucose open to the people relying on it.

If a provider SPI is ever extracted into its own module, relicensing that module under Apache-2.0
would be worth considering, so a sensor vendor could implement it without licensing friction.
Today there is no such module and the whole repository is GPL.

## A note on test data

Development involves reading real glucose values off a real device. Those values are special
category health data and must never enter version control. Test fixtures use **invented values in
the real format**; `.gitignore` blocks device dumps and screenshots.
