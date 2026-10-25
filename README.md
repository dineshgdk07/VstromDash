# VstromDash (Bumblebee)

An unofficial, local-first companion app for the **Suzuki V-Strom 250 SX**. It talks to the bike's
Bluetooth instrument cluster to show live telemetry on your phone, push turn-by-turn arrows from
Google Maps to the cluster, and keep a private log of every ride, fill and service.

> **Not affiliated with Suzuki.** "Suzuki", "V-Strom" and "Suzuki Ride Connect" are trademarks of
> Suzuki Motor Corporation. This is a personal hobby project built for one rider's own bike.
> It is not a certified safety device. See [Disclaimer](#disclaimer).

## What it does

| Tab | Highlights |
| --- | --- |
| **Cockpit** | Readiness check (fuel, weather, link, service), live trip computer, ride suggestions, odometer milestones, "on this day", where you parked |
| **Journey** | Turn-by-turn on the cluster from Google Maps notifications (no key needed), or the app's own navigation with your Google or OpenRouteService key; trip log, saved places, arrow fixes |
| **Ride** | Live splits, fuel ahead, break nudges, ghost of your usual pace; afterwards a ride score, replay map, speed and elevation chart, and share cards |
| **Explorer** | Calendar of rides, monthly goal, 6-month bars, personal records, year heatmap, territory "fog" map, badges and challenges, riding DNA, ride compare |
| **Garage** | Live bike panel and health score, fuel log with calendar and insights, documents with expiry reminders and a checkpost view, service plans with forecasts, parts wear, cost of ownership, bike passport |

Also:
- **Crash alerts:** an "I'm OK" countdown (30 s by default), then an SMS with your location to up to three contacts.
- **Share cards:** four designs, map/photo/transparent backgrounds, stickers.
- **Transfer:** a single-file backup and restore of everything.
- **Advanced settings:** off-route and re-route tuning, 24-hour cluster clock, crash countdown, map
  cache size, and clean-up tools (demo or misdated rides, daily totals, fuel gauge, arrow fixes).
  Tap the version line seven times for developer options: live link and raw frames, force
  reconnect, database counts, and a log you can share as a debug report (no keys, contacts or places).

## Privacy

Everything stays on the phone. There is no account, no server and no analytics. The app only goes
online for:

- **Map tiles** from [OpenStreetMap](https://www.openstreetmap.org/copyright) (© OpenStreetMap contributors)
- **Weather** from [Open-Meteo](https://open-meteo.com/)
- **Routing and place search** from Google or [OpenRouteService](https://openrouteservice.org/), only if you
  add your own key and choose that navigation mode
- **SMS** to your own emergency contacts, only after a crash alarm is not cancelled, or when you send a test

Sensitive permissions and why:

| Permission | Used for |
| --- | --- |
| Bluetooth | Linking to the cluster |
| Location | Ride tracks, parked spot, weather, crash alert location |
| Notification access | Reading Google Maps' navigation notification to drive the cluster arrows |
| SMS | Crash alerts to your chosen contacts |
| Full-screen intent | Showing the crash alarm over the lock screen |

## Install

Download the latest APK from [Releases](https://github.com/dineshgdk07/VstromDash/releases) on your
phone and allow installs from your browser or file manager. Android 8.0 or newer. Updates install
over the previous version and keep your data.

## Build

Requirements: JDK 17 and the Android SDK (compileSdk 35, minSdk 26).

```bash
./gradlew :app:installDebug        # build and install on a connected phone
./gradlew test                      # JVM unit tests for all modules
```

Release builds are signed only when these environment variables are set; otherwise the APK comes out
unsigned (add `-PdebugSignRelease` to debug-sign one for testing). Never commit keystores or
passwords; `.gitignore` blocks the usual files. Tagged releases are built by CI; see
[docs/RELEASING.md](docs/RELEASING.md).

```bash
export BUMBLEBEE_STORE_FILE=/path/to/release.jks
export BUMBLEBEE_STORE_PASSWORD=...
export BUMBLEBEE_KEY_ALIAS=...
export BUMBLEBEE_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

## Project layout

| Module | What lives there |
| --- | --- |
| `app` | Jetpack Compose UI, services (Bluetooth bridge, crash alert, reminders), share card rendering |
| `core:protocol` | Cluster frame encoding and decoding. **See [Credits](#credits): not covered by this repo's licence** |
| `core:ble` | Suzuki GATT surface |
| `core:ble-android` | Android Bluetooth client, scanning, preferences |
| `core:nav` | Google Maps notification parsing to cluster arrows |
| `core:ride` | Pure Kotlin ride, fuel, garage and insight logic, with most of the unit tests |
| `core:snapshot` | Backup and restore format |
| `data:local` | Room database and the capture repository |

Design notes live in [`docs/`](docs/).

## Credits

- **Cluster protocol:** the Bluetooth protocol work, and the code in `core/protocol`, come from
  [mrwick1/suzuki-connect-re](https://github.com/mrwick1/suzuki-connect-re) (REDLINE) by Arjun.
  That project has no licence yet, so `core/protocol` is **not** covered by this repo's MIT licence
  and remains its author's work. Permission has been requested; see [NOTICE.md](NOTICE.md).
- **Map data:** © OpenStreetMap contributors, under the ODbL.
- **Weather:** Open-Meteo.
- **Fonts:** Anton, Fraunces, JetBrains Mono, Nunito, Permanent Marker and Rajdhani, under the SIL
  Open Font License or Apache 2.0 (details in [NOTICE.md](NOTICE.md)).

## Contributing

Issues and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first, and
report security problems privately as described in [SECURITY.md](SECURITY.md).

## Licence

The original code in this repository is under the [MIT Licence](LICENSE), except where
[NOTICE.md](NOTICE.md) says otherwise (notably `core/protocol`, the bike images and the fonts).

## Disclaimer

This app is provided as is, with no warranty. Crash detection is a best-effort aid that can miss a
crash or raise a false alarm; it does not replace emergency services. Don't interact with the phone
while riding. Use of the bike's Bluetooth interface is unofficial and at your own risk.
