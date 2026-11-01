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
| Providers | LinX / MicroTech AiDEX X ([validated contract](docs/providers/linx.md)); Dexcom ONE+ ([1.9.0 live acquisition checked; further validation pending](docs/providers/dexcom-one-plus.md)) |
| Watch surfaces | Complication (SHORT_TEXT, LONG_TEXT, RANGED_VALUE), Tile, app |
| Phone | 1.2: source-labelled status, explicit source selection (LinX default), independent histories, target range settings with a very-high threshold, silence watchdog, range-coloured home-screen widgets |
| Range colours | Shared by widget, tile and watch app: red below urgent low and above very high (55 / 260 by default), orange low, green in range, yellow high, grey once stale |
| Health export | Health Connect, off by default ([below](#health-connect-export)) |
| Tests | JVM domain/sync policies, provider parsers and source-isolation tests; synthetic Dexcom inputs are not real-device validation |

### No support, and no promise to maintain

Published because it may be useful to someone with the same sensor, not as a product. There is no
support, no release schedule, and no commitment that it will keep working or be fixed if it
stops. It is built for one phone and one watch, and tested on those.

The part most likely to break is not this code: the glucose is read out of the vendor's
notification layout, and a vendor update can change that at any time, without warning. The app
detects when it has fallen back to positional parsing and says so on the status screen — treat
that as notice that it needs attention, not as something that will be handled for you.

If you rely on this, keep the official app as your source of truth, and be prepared to fix the
parser yourself. It is GPL-3.0: [forking](docs/adding-a-provider.md) is the expected response to
a need this repository does not meet.

## Phone 1.2: sources and home-screen widgets

**LinX has priority by default**, even if Dexcom readings arrive first or last. Both feeds are
collected into separate histories. The source for the watch and optional Health Connect export
changes only when explicitly saved in **Settings**; a temporary LinX outage never switches it.

Add one or more **CGM Glance** widgets from the launcher's widget picker. Configure each instance
for LinX, Dexcom ONE+, or **Follow selected source**. Every widget labels its actual source and
opens that source's official app when tapped. A fixed Dexcom widget does not change what reaches
the watch, so it can be used beside a LinX widget during the overlap period. Units follow the
phone settings. Age remains visible, and values/trends follow the shared freshness policy.
Widget refresh timing is subject to Android background/Doze scheduling; this is not a medical
alarm or a replacement for the official app.

Dexcom requires its **Quick Glance** notification and separate notification access for
**CGM Glance — Dexcom ONE+**. Basic mg/dL acquisition is checked on version 1.9.0, including its
invisible U+2060 unit separator. Failure states, timestamps and sustained operation still need
validation. Missing units and ambiguous layouts are rejected rather than guessed; see the
[provider specification](docs/providers/dexcom-one-plus.md).

## Not a medical device

This app mirrors a value the official app has already computed. It is not certified, not
validated, and must not be used for treatment decisions. Always confirm with the official app or
a blood glucose meter. It deliberately produces **no alarms and no vibration** for glucose
values — see [Non-goals](#non-goals).

## Health Connect export

Optionally writes each reading to Health Connect, from where Samsung Health — or anything else
the user has linked — picks it up. **Off by default**; switched on under *Settings*,
and it additionally needs the Health Connect write permission, granted from the status screen.

| | |
| --- | --- |
| What is written | `BloodGlucoseRecord`, mg/dL, specimen source *interstitial fluid* |
| How often | one reading per interval — 15 minutes by default, 5 to 180 allowed |
| Read back | never; the app holds the value already |
| Permission | `android.permission.health.WRITE_BLOOD_GLUCOSE`, write only |

**Why not Samsung's own SDK.** The Samsung Health Data SDK can insert into Samsung Health
directly, but only for apps Samsung has approved as partners: developer mode waives the signature
check, not the partnership, and the write path is gated behind an access code issued on approval,
with no exemption for personal use. Health Connect needs no such agreement. The export sits behind
a `GlucoseExporter` interface so a Samsung implementation can drop in if that ever changes.

**What a successful write means.** That the record reached *Health Connect*. Whether Samsung
Health then ingests it depends on the user having enabled the Health Connect link inside Samsung
Health and granted it permission to read glucose. This app cannot observe that, and the wording in
the UI never claims it.

This is not the cloud sync listed under [Non-goals](#non-goals): it is a local write to the
platform's own health store, on the user's explicit request, with no account and no network.

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
| [Changelog](CHANGELOG.md) | what changed per version |
| [Architecture](docs/architecture.md) | the chain, the three ideas that shape it, wire format, battery |
| [Decision log](docs/decisions.md) | what was chosen, what was rejected, and why |
| [LinX provider contract](docs/providers/linx.md) | what the vendor exposes, the exact layout, breakage log |
| [Dexcom ONE+ provider](docs/providers/dexcom-one-plus.md) | provisional notification grammar, source priority, permissions and device-validation checklist |
| [Phone widgets](docs/phone-widgets.md) | multiple source-specific widgets, app-launch taps and freshness limitations |
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

Debug keystores are generated **per machine**, so debug builds from two different computers do
not pair with each other, and replacing one side alone silently breaks the link. To pin one key
across machines and across build types, put a `keystore.properties` in the repository root — it
is gitignored, along with the keystore itself:

```properties
storeFile=/path/to/cgmglance-signing.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

Release builds are then signed with it; without the file they are produced unsigned, so a
checkout with no key still builds. A certificate change cannot be applied as an update — Android
rejects it with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and both apps have to be uninstalled first.

```bash
adb install -r phone/app/build/outputs/apk/debug/app-debug.apk
```

Then grant notification access: **Settings → Apps → Special access → Notification access**. The
app appears there twice, once per sensor: **CGM Glance — LinX** and **CGM Glance — Dexcom ONE+**.
Android grants this access per service, so enable the entry for each sensor you use.

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
| `:provider:linx` | Android library | Reads the LinX notification. Parsing logic is plain Kotlin. |
| `:provider:dexcom` | Android library | Dexcom ONE+ notification reader; strict parser, no sensor/Bluetooth access. |
| `:phone:app` | Android app | Acquisition, history, sync, health export, status and settings screens, home-screen widgets. |
| `:wear:app` | Android app | Complication, tile, watch app. |

Both apps share the application id `ro.smakeru.cgmglance`, which the Data Layer requires. Wear
version codes are offset by one million: phone **1.2** is `versionCode` 3, watch **1.2** is
1000003. Sync schema 2 only adds the very-high threshold, so either APK can be updated first; a 1.1
watch keeps working, it simply shows in range in white and has no very-high red. Both installed
APKs must use the same signing key.

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
