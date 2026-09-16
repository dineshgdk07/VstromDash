# Landscape Rider HUD + Phone GPS Implementation Plan (Plan 6 of v1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add phone GPS (~1 Hz) into `DashApp` (live HUD + archive when a ride is open) and replace landscape with a D-017 Rider HUD: GPS speed dial, fuel/economy arcs, compass, odo/trip, BLE/battery — no RPM/gear/temp, no Maps/`a531`.

**Architecture:** Pure `SpeedResolver` + `CompassCardinal` in `core:ride`. Android `PhoneGps` uses `LocationManager` (no Play Services). `DashApp.latestGps` is the live bus; archive via `RideCaptureRepository.onGps` only when `activeRideId != null`. Landscape `Configuration` swaps `BumblebeeApp` to `RiderHudScreen`; portrait Cockpit stays. Turn-by-turn overlay, speed-limit settings, haptics, and HUD side cards are out of this plan.

**Tech Stack:** Existing Kotlin/Compose, JUnit4 in `core:ride`, `LocationManager.GPS_PROVIDER`. JDK 17, Gradle 8.12 fallback.

**Spec:** `docs/design/screens/rider-hud-landscape.md`, `docs/design/components/speed-dial.md`, parent D-017 in `docs/superpowers/specs/2026-07-12-bumblebee-dash-design.md`.

## Global Constraints

- Branch `capture-pipeline`. Same env as Plan 4/5 (`source .../env.sh`, Gradle 8.12 fallback).
- Never show RPM/gear/engine temp/voltage as live data.
- GPS preferred for speed; BLE `a537` only if GPS stale/inaccurate/missing. Clamp GPS `< 3 km/h` to `0`.
- `onGps` throws without an active ride — never call it unless `activeRideId != null`.
- No Play Services. No location FGS type (HUD is foreground Activity).
- One commit per task.

---

### Task 1: Speed resolver + compass (pure, tested)

**Files:**
- Create: `core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/SpeedResolver.kt`
- Create: `core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/SpeedResolverTest.kt`

**Produces:**
```
enum class SpeedSource { GPS, BLE, NONE }
data class ResolvedSpeed(val speedKmh: Int, val source: SpeedSource)
object SpeedResolver {
  fun resolve(gps: GpsFix?, nowMs: Long, bleSpeedKmh: Int?): ResolvedSpeed
}
object CompassCardinal {
  fun label(headingDeg: Float?): String?  // N NE E SE S SW W NW, null if heading null
}
```

GPS usable when `gps != null`, `speedMps != null`, `nowMs - gps.ts <= 5000`, and `accuracyM == null || accuracyM <= 25f`. Then `kmh = (speedMps * 3.6).toInt()` then if `< 3` → `0`. Else if `bleSpeedKmh != null` use BLE (also clamp `< 3` to `0`). Else `NONE` / `0`.

- [x] Tests + implement + `./gradlew :core:ride:test` + commit `feat(ride): resolve HUD speed from GPS with BLE fallback`

---

### Task 2: Phone GPS into DashApp + archive

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` — `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/gps/PhoneGps.kt`
- Modify: `DashApp.kt` — `latestGps: MutableStateFlow<GpsFix?>`
- Modify: `MainActivity.kt` — with permission, collect GPS into `latestGps`; if `capture.activeRideId != null` call `onGps`

`PhoneGps.fixes(): Flow<GpsFix>` via `callbackFlow` + `LocationManager.requestLocationUpdates(GPS_PROVIDER, 1000L, 0f, ...)`. Map `Location` → `GpsFix` (`hasSpeed`/`hasBearing`/`hasAltitude`/`hasAccuracy`).

MainActivity: request `ACCESS_FINE_LOCATION` once; start collection in `lifecycleScope` while started.

- [x] assembleDebug + commit `feat(gps): stream phone GPS into DashApp and ride archive`

---

### Task 3: Landscape Rider HUD UI + rotate

**Files:**
- Create: `ui/hud/HudViewModel.kt`, `ui/hud/RiderHudScreen.kt`
- Create: `ui/hud/components/SpeedDial.kt`, `FuelArc.kt`, `EconomyArc.kt`, `CompassHeading.kt`
- Modify: `BumblebeeApp.kt` — if landscape, `RiderHudScreen` and no bottom bar
- Modify: `CockpitStatusBar` + `CockpitScreen` — GPS dot from `latestGps` freshness (optional, same `SpeedResolver` gps-ok)

HudViewModel maps `latestGps` + `latestTelemetry` + `connection` through `SpeedResolver`. Digital `--` when `NONE`. Heading from `gps.bearingDeg`. Fuel/economy/odo/trip from telemetry. Phone battery via `BatteryManager`. BLE chip from `ConnectionState`.

SpeedDial: Canvas 270° sweep, 0–160, needle from speed (peg at 160, digital true value). SourceDot GPS/BLE/NONE.

- [x] assembleDebug + install + rotate phone + commit `feat(ui): add landscape Rider HUD with GPS speed dial`

---

**Out of plan:** `a531` TBT overlay, user speed limit, haptic, HUD side cards, Play fused location, background GPS after Activity stop.
