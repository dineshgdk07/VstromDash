# BLE Heartbeat, Ride Session & Demo Capture Implementation Plan (Plan 3 of v1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the BLE building blocks (GATT constants, connection state, 1 Hz heartbeat, prioritized frame writer) and a ride-session player that feeds `RideCaptureRepository` from `DemoRideSource`, then surface live facts on Cockpit so capture works on a phone without the bike.

**Architecture:** `core:ble` is a pure Kotlin/JVM library (depends on `core:protocol` + coroutines only) — same isolation rule as `core:protocol`. Real Android GATT stays out of this plan; a later plan ports `BleClient` into an Android `core:ble-android` or `app` service. `RideSession` in `data:local` interleaves telemetry + GPS ticks into the existing repository. The app process holds a singleton `BumblebeeDb` and a `CockpitViewModel` that can start/stop a demo ride.

**Tech Stack:** Existing catalog (Kotlin 2.1, Room, Compose, coroutines). Cached Gradle 8.12 if wrapper 8.11.1 cannot download.

**Spec:** `docs/superpowers/specs/2026-07-12-bumblebee-dash-design.md` §5–7; capture schema already in `data:local`.

## Global Constraints

- JDK 17 (`JAVA_HOME=/home/gdk/.local/jdk-17`). Source env: `source /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/env.sh`.
- `core:ble` has **zero Android dependencies**. Strip Compose `@Immutable` from REDLINE `ConnectionState`.
- Heartbeat interval **1000 ms**. Identity/nav/call frames are **not** in this plan.
- Real Fused Location / BLE scan / foreground service: **next plan**. Demo GPS from `DemoRideSource` is enough to prove capture-on-device.
- Gradle: prefer `./gradlew`; fallback `/home/gdk/.gradle/wrapper/dists/gradle-8.12-all/ejduaidbjup3bmmkhw3rie4zb/gradle-8.12/bin/gradle`.
- One commit per task. Working dir: `/home/gdk/CodeHollow/Flutter/bumblebee-dash`. Branch: `capture-pipeline`.

---

### Task 1: `core:ble` — GATT constants, ConnectionState, HeartbeatLoop

**Files:**
- Modify: `settings.gradle.kts` — `include(":core:ble")`
- Create: `core/ble/build.gradle.kts` (same shape as `core/ride`)
- Create: `core/ble/src/main/kotlin/dev/gdk/bumblebee/ble/SuzukiGatt.kt`
- Create: `core/ble/src/main/kotlin/dev/gdk/bumblebee/ble/ConnectionState.kt`
- Create: `core/ble/src/main/kotlin/dev/gdk/bumblebee/ble/HeartbeatLoop.kt`
- Test: `core/ble/src/test/kotlin/dev/gdk/bumblebee/ble/HeartbeatLoopTest.kt`

**Interfaces:**
- Produces: `SuzukiGatt` UUID strings (fff0/fff1/fff2 + CCC); `ConnectionState` sealed type; `HeartbeatLoop.buildFrame()` and `HEARTBEAT_INTERVAL_MS = 1000L`.

Port HeartbeatLoop from REDLINE (`suzuki-connect-re/.../ble/HeartbeatLoop.kt`): battery buckets 75/50/25 → `"3"`/`"2"`/`"1"`/`"0"`; 12-hour `timeHhmmss`; charging `Y`/`N`; empty `speedStr`. Inject `clock: () -> LocalTime`.

Tests: 80% battery → bucket `"3"`; 10% → `"0"`; charging true → `"Y"`; fixed clock `14:30:05` → time `"023005"` (12-hour).

---

### Task 2: `FrameWriter` priority queues

**Files:**
- Create: `core/ble/src/main/kotlin/dev/gdk/bumblebee/ble/FrameWriter.kt`
- Test: `core/ble/src/test/kotlin/dev/gdk/bumblebee/ble/FrameWriterTest.kt`

Port from REDLINE: URGENT / HEARTBEAT / NAV channels; NAV drop-oldest + identical-content skip; `take()` prefers URGENT > HEARTBEAT > NAV.

Test with `runTest`: enqueue NAV then HEARTBEAT then URGENT → `take()` order URGENT, HEARTBEAT, NAV. Duplicate NAV bytes skipped.

---

### Task 3: `RideSession` — interleave ticks into the repository

**Files:**
- Create: `data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/RideSession.kt`
- Test: `data/local/src/test/kotlin/dev/gdk/bumblebee/data/local/RideSessionTest.kt`

`CaptureTick.Telemetry(ts, frame)` / `CaptureTick.Gps(fix)`. `DemoRideSource.asTicks()` merges lists sorted by timestamp. `RideSession(repo).run(ticks, endTs)` starts ride at first tick ts, applies ticks in order, finishes.

Robolectric test: demo source through `RideSession` yields 120 telemetry, 600 GPS, one BAR_DROP (same as repository e2e).

---

### Task 4: Cockpit demo ride (live facts, no BLE hardware)

**Files:**
- Modify: `app/build.gradle.kts` — `implementation(project(":data:local"))`, `implementation(project(":core:ride"))`, `implementation(project(":core:ble"))`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/BumblebeeApp.kt` (`Application`, lazy `BumblebeeDb.create`)
- Modify: `AndroidManifest.xml` — `android:name=".BumblebeeApp"`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/cockpit/CockpitViewModel.kt`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/cockpit/CockpitScreen.kt`
- Modify: `BumblebeeApp.kt` (nav) to show CockpitScreen for Cockpit route

ViewModel: `startDemo()` launches `RideSession` against process DB; `uiState` = last speed / fuel bars / odo / connection Ready vs Idle. Fast playback (no per-tick delay) so a tap completes in the test/emulator quickly; optional `realtime` later.

Manual check: install debug APK, tap **Start demo ride** on Cockpit, see speed/fuel/odo update then ride complete.

---

## Out of scope

- Real `BluetoothGatt` / scan / pairing MAC persistence
- FusedLocationProvider
- Foreground `BikeBridgeService`, identity handshake on a real bike, nav frames, call/SMS
- Hilt
