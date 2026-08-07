# Adding a CGM provider

If a competent stranger cannot add a new sensor by following this page, the architecture's claim
to be extensible is false. Treat a gap here as a defect.

## Before writing anything: find out what the vendor exposes

Every clean route is worth more than the best scraping. Check them in this order, and record what
you find in `docs/providers/<name>.md` even when the answer is "absent" — knowing something was
checked is worth as much as the result.

```bash
# What is installed, and which build
adb shell pm list packages | grep -i <vendor>
adb shell dumpsys package <package> > dump.txt
```

In that dump, look for:

| Section | You are hoping for |
| --- | --- |
| `Receiver Resolver Table` | a custom action — a broadcast is the best realistic outcome |
| `Provider Resolver Table` | a content provider that is not a `FileProvider` |
| `Service Resolver Table` | a bindable service, i.e. an SDK |
| `requested permissions` | `android.permission.health.*`, meaning Health Connect |

If any of those exist, use them and skip the rest of this page: they are stable contracts, and
scraping is not.

If none do, capture the notification instead:

```bash
adb shell dumpsys notification --noredact | grep -A40 <package>
# then, with the shade open on the device:
adb shell uiautomator dump /sdcard/shade.xml && adb pull /sdcard/shade.xml
```

`android.text` non-empty means a plain string to parse. `contentView` with
`android.contains.customView` means a `RemoteViews` tree; reuse `RemoteViewsTextExtractor`.

## Then: write the parser as plain Kotlin

The parser takes data and returns a domain object. It must not touch Android — that is what makes
it testable against layouts you captured months ago.

Model it on `LinxNotificationParser`:

```kotlin
class VendorParser {
    fun parse(views: List<VendorTextView>, postTimeMillis: Long): ParseResult
}
```

Non-negotiable rules, each of which exists because breaking it is dangerous rather than untidy:

- **Never infer the unit.** Read it, or refuse the reading. Guessing turns 6.2 mmol/L into
  6 mg/dL.
- **Never take the timestamp from displayed text.** Vendor apps render ages relatively and in
  their own language. Use `StatusBarNotification.postTime`.
- **Return a reason on failure**, not just null. The status screen shows it, and it is how the
  next breakage gets diagnosed.
- **Build readings through `GlucoseReading.create`**, so the plausibility bounds apply to every
  source equally.

## Then: the Android edge

A `NotificationListenerService` (or broadcast receiver, if you were lucky) that:

- drops anything not from your package **in the first line**, before reading or logging it — the
  system delivers every notification on the device to you;
- handles `onListenerConnected` by processing `activeNotifications`, or the app shows nothing
  until the vendor next updates;
- handles `onListenerDisconnected` with `requestRebind`, or it silently stops working after a
  few days.

Add the package to `<queries>` in the module manifest. Never `QUERY_ALL_PACKAGES`.

## Then: tests

At minimum, mirroring `LinxNotificationParserTest`:

- the exact layout the shipping app produces, as captured;
- both decimal separators;
- a missing unit is refused, not guessed;
- an implausible value is refused;
- the fallback path does not mistake a clock for a value;
- renamed ids still parse, flagged as fallback.

**Use invented numbers.** Real readings are health data and never enter the repository; fixtures
reproduce the *format* of real data, not its content.

## Finally: write the provider document

Copy the shape of `docs/providers/linx.md`. Include the breakage log table even while it is
empty — it is the part that will matter in two years, and nobody adds it later.
