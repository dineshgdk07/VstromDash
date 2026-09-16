# Real Android BLE Link Implementation Plan (Plan 4 of v1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the phone to a Suzuki V-Strom 250 SX over BLE: scan, save MAC, run a foreground `BikeBridgeService` that handshakes (a536), heartbeats (a533 ~1 Hz), and archives every a537 into `RideCaptureRepository` while Cockpit shows live speed/fuel/odo.

**Architecture:** Keep JVM `core:ble` (FrameWriter, HeartbeatLoop, ConnectionState, SuzukiGatt) unchanged. Add Android library `:core:ble-android` for `BleClient` + `BleScanner` + `BikePrefs` (no UI). `app` owns `BikeBridgeService` (composition root: identity, heartbeat drain, telemetry sink) and a still-minimal Cockpit that can Scan / Pair / Connect plus the existing demo ride. No Hilt. No Forge UI redesign. No Google Maps nav. No Fused Location.

**Tech Stack:** Existing catalog (AGP 8.10.0, Kotlin 2.1.0, Compose BOM 2026.05.00, Room 2.7.1, coroutines 1.10.1, Robolectric 4.14.1). Add `androidx.lifecycle:lifecycle-service` at lifecycle `2.8.7`.

**Spec:** `docs/superpowers/specs/2026-07-12-bumblebee-dash-design.md` §5 (BLE capability map), §6 pairing/connection + demo, §7 module layout, §9 first-bike checklist items 1–2. Capture API already in `RideCaptureRepository`. REDLINE source (read-only): `/home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/app/src/main/kotlin/dev/mrwick/redline/ble/`.

## Global Constraints

- **JDK:** 17 at `/home/gdk/.local/jdk-17`. SDK `/home/gdk/Android/Sdk`. Source ` /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/env.sh` before Gradle.
- **Gradle:** prefer `./gradlew`; fallback `/home/gdk/.gradle/wrapper/dists/gradle-8.12-all/ejduaidbjup3bmmkhw3rie4zb/gradle-8.12/bin/gradle`.
- **Versions (pin exactly):** same as existing catalog. `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`, JVM 17.
- **Packages:** `dev.gdk.bumblebee.ble` (JVM), `dev.gdk.bumblebee.ble.android` (`core:ble-android`), `dev.gdk.bumblebee` (app).
- **`core:ble` purity:** remains Kotlin/JVM — zero Android. Do not move FrameWriter/HeartbeatLoop into the Android module.
- **Port rule for BleClient:** rewrite `dev.mrwick.redline.ble` → `dev.gdk.bumblebee.ble.android` (or keep class in `.ble.android` and import JVM `dev.gdk.bumblebee.ble.ConnectionState` / `SuzukiGatt`). Replace `AppLog` with `android.util.Log`. Skip Device Info / `BikeInfo` in this plan (defer §9 item 6). Do not port NavMux, crash detection, DND, idle clock, or Maps.
- **Heartbeat:** 1000 ms via existing `HeartbeatLoop`; battery from `BatteryManager`; signal bars default `3`; sms/call pending `false`; weather `(0x01, 0x00)`.
- **Identity:** `IdentityFrame(name = BikePrefs.riderName, isFresh = !lastWasReady).encode()` enqueued URGENT on first `ConnectionState.Ready`.
- **Capture:** first a537 after Ready → `startRide` if idle; every TelemetryFrame → `onTelemetry`; disconnect/Failed → `finishRide` if a ride is active. Demo ride and live BLE must not run at the same time.
- **Permissions (this plan only):** `BLUETOOTH`, `BLUETOOTH_ADMIN` (maxSdk 30), `BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_CONNECT`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS`. No location, SMS, notification-listener, or boot receiver.
- **Branch:** `capture-pipeline`. One conventional-commit per task.
- **Working directory:** `/home/gdk/CodeHollow/Flutter/bumblebee-dash`.

---

### Task 0: Commit design-doc consolidation already sitting uncommitted

**Files:**
- Add: `docs/design/**`
- Modify: `docs/superpowers/specs/2026-07-12-bumblebee-dash-design.md`, `docs/superpowers/specs/DESIGN-NOTES-cockpit-mocks.md`
- Do **not** add: `.serena/`, `bumblee_dash_plan.md`, `core/ble/build/` artifacts

**Interfaces:** none (docs only). Records D-017 landscape-in-v1 so Plan 4 does not fight a portrait-only spec.

- [ ] **Step 1: Stage and commit**

```bash
git add docs/design docs/superpowers/specs/2026-07-12-bumblebee-dash-design.md \
  docs/superpowers/specs/DESIGN-NOTES-cockpit-mocks.md
git commit -m "$(cat <<'EOF'
docs: land Forge UI design system and D-017 landscape HUD in v1

EOF
)"
```

Skip `bumblebee-dash-plan-discussion.md` unless the diff is clearly the same reconciliation (do not dump ChatGPT export).

---

### Task 1: `BikePrefs`, Suzuki scan ranking, `TelemetrySink`

**Files:**
- Modify: `settings.gradle.kts` — `include(":core:ble-android")`
- Modify: `gradle/libs.versions.toml` — add `androidx-lifecycle-service`
- Modify: root `build.gradle.kts` if needed (android-library already applied false)
- Create: `core/ble-android/build.gradle.kts`
- Create: `core/ble-android/src/main/AndroidManifest.xml` (`<manifest />`)
- Create: `core/ble-android/src/main/kotlin/dev/gdk/bumblebee/ble/android/BikePrefs.kt`
- Create: `core/ble-android/src/main/kotlin/dev/gdk/bumblebee/ble/android/DiscoveredBike.kt`
- Create: `core/ble-android/src/main/kotlin/dev/gdk/bumblebee/ble/android/TelemetrySink.kt`
- Test: `core/ble-android/src/test/kotlin/dev/gdk/bumblebee/ble/android/BikePrefsTest.kt`
- Test: `core/ble-android/src/test/kotlin/dev/gdk/bumblebee/ble/android/DiscoveredBikeTest.kt`
- Test: `core/ble-android/src/test/kotlin/dev/gdk/bumblebee/ble/android/TelemetrySinkTest.kt`
- Test: `core/ble-android/src/test/resources/robolectric.properties` (`sdk=34`)

**Interfaces:**
- Consumes: `RideCaptureRepository`, `TelemetryFrame.encode()` / `decodeFrame`, `BumblebeeDb.inMemory`.
- Produces: `BikePrefs(context)` with `var bikeMac: String?`, `var riderName: String` (default `"RIDER"`); `DiscoveredBike(mac, name, rssi, bonded)` with `val looksLikeSuzuki: Boolean`; `TelemetrySink(repo)` with `suspend fun onReady(ts: Long)`, `suspend fun onNotify(bytes: ByteArray, ts: Long)`, `suspend fun onDisconnected(ts: Long)`.

`looksLikeSuzuki` is true when `name` starts with `"SBM"` (ignore case) **or** MAC OUI is `74:B8:39` (Suzuki cluster TI, case-insensitive, colon-separated).

- [ ] **Step 1: Register module**

`settings.gradle.kts` add:

```kotlin
include(":core:ble-android")
```

`gradle/libs.versions.toml` libraries:

```toml
androidx-lifecycle-service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycle" }
```

`core/ble-android/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "dev.gdk.bumblebee.ble.android"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:ble"))
    implementation(project(":core:protocol"))
    implementation(project(":data:local"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: Write failing tests** (full files)

`BikePrefsTest`: Robolectric; `bikeMac` null by default; set/get round-trip; `riderName` defaults `"RIDER"`; persist across two `BikePrefs` instances on same context.

`DiscoveredBikeTest`: `SBM 123` → true; `Pixel Buds` + MAC `AA:BB:CC:DD:EE:FF` → false; name null + MAC `74:B8:39:00:11:22` → true.

`TelemetrySinkTest`: in-memory DB; `onNotify` of encoded `TelemetryFrame` **before** `onReady` is ignored (no ride); `onReady` then two telemetry frames → `telemetryDao().countForRide == 2` and `activeRideId` set; `onDisconnected` finalizes ride (`endTs` non-null, `activeRideId` null); malformed 30-byte buffer does not throw.

- [ ] **Step 3: Run tests — expect FAIL** (unresolved types)

Run: `source .../env.sh && ./gradlew :core:ble-android:testDebugUnitTest` (or Gradle 8.12 fallback).

- [ ] **Step 4: Implement** `BikePrefs` (SharedPreferences `"bumblebee_ble"`, keys `bike_mac`, `rider_name`), `DiscoveredBike`, `TelemetrySink`.

`TelemetrySink` rules:
- Ignore notify until a ride is active **or** until `onReady` has been called (call `startRide` in `onReady` immediately so the first a537 is not lost). Prefer: `onReady` always `startRide` if `activeRideId == null`.
- `onNotify`: if `bytes.size != 30` return; `decodeFrame` in try/catch; only `TelemetryFrame` forwarded; skip if no active ride.
- `onDisconnected`: if `activeRideId != null` call `finishRide`.

- [ ] **Step 5: Tests PASS. Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml core/ble-android
git commit -m "feat(ble): add BikePrefs, Suzuki scan ranking, and TelemetrySink"
```

---

### Task 2: Port `BleClient` + simplified `BleScanner`

**Files:**
- Create: `core/ble-android/src/main/kotlin/dev/gdk/bumblebee/ble/android/BleClient.kt`
- Create: `core/ble-android/src/main/kotlin/dev/gdk/bumblebee/ble/android/BleScanner.kt`
- Test: `core/ble-android/src/test/kotlin/dev/gdk/bumblebee/ble/android/BleClientApiTest.kt` (compile-time / constructor smoke — no live GATT)

**Interfaces:**
- Consumes: `SuzukiGatt` + `ConnectionState` from `:core:ble`.
- Produces: `class BleClient(context: Context)` with `val state: StateFlow<ConnectionState>`, `val notifications: SharedFlow<ByteArray>`, `fun connect(mac: String)`, `fun disconnect()`, `fun shutdown()`, `suspend fun write(frame: ByteArray): Boolean`.
- Produces: `class BleScanner(context: Context)` with `val results: StateFlow<Map<String, DiscoveredBike>>`, `fun start()`, `fun stop()`. Unfiltered LE scan; seed bonded devices; EMA RSSI 0.3/0.7 as REDLINE. **No** diag JSONL file, **no** `BleVendor`.

- [ ] **Step 1: Port BleClient from** `$REDLINE/BleClient.kt`

Transformations (do these mechanically; do not invent a simpler handshake):
1. Package `dev.gdk.bumblebee.ble.android`.
2. Import `dev.gdk.bumblebee.ble.ConnectionState` and `dev.gdk.bumblebee.ble.SuzukiGatt`.
3. Replace every `AppLog.i/w/e/d` with `Log.i/w/e/d` (tag string unchanged).
4. Remove `_bikeInfo`, `BikeInfo`, `readDeviceInfo`, and the `scope.launch { readDeviceInfo }` at end of handshake. Keep MTU 65, 500 ms gap, CCC wait, `failHandshake` 3× linear backoff, write busy-retry 2 s, `WRITE_TYPE_DEFAULT`, `autoConnect=true`, `TRANSPORT_LE`.
5. `write` still requires **exactly 30 bytes**.

- [ ] **Step 2: Implement BleScanner** without vendor/diag. `start()` clears results, seeds `adapter.bondedDevices`, starts `bluetoothLeScanner.startScan(null, ScanSettings.Builder().setScanMode(SCAN_MODE_LOW_LATENCY).build(), callback)`. `stop()` stops scan. Callback maps to `DiscoveredBike`.

- [ ] **Step 3: `BleClientApiTest`** — constructing `BleClient(ApplicationProvider.getApplicationContext())` does not throw; `state.value` is `Idle`. Robolectric.

- [ ] **Step 4: `./gradlew :core:ble-android:testDebugUnitTest` PASS. Commit**

```bash
git commit -am "feat(ble): port BleClient handshake and BleScanner without vendor diag"
```

---

### Task 3: `BikeBridgeService` — identity, heartbeat, capture

**Files:**
- Modify: `app/build.gradle.kts` — `implementation(project(":core:ble-android"))`, `implementation(libs.androidx.lifecycle.service)`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ble/BikeBridgeService.kt`
- Modify: `app/src/main/AndroidManifest.xml` — permissions + service + `targetSdk` already 35
- Modify: `app/src/main/kotlin/dev/gdk/bumblebee/DashApp.kt` — expose `BikePrefs` lazy + `RideCaptureRepository` lazy
- Test: none instrumented. Verify compile with `:app:assembleDebug`. Manual checklist at end of plan.

**Interfaces:**
- Consumes: `BleClient`, `FrameWriter`, `HeartbeatLoop`, `TelemetrySink`, `BikePrefs`, `IdentityFrame`, `DashApp.database`.
- Produces: `class BikeBridgeService : LifecycleService()`. Actions: `ACTION_START`, `ACTION_STOP`. Sticky FGS type `connectedDevice`, notification channel `ble`, id `1`. Companion `fun start(context)`, `fun stop(context)`.

Service `onCreate` algorithm:
1. `startForeground` with type `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` (API 29+). If `SecurityException` (no BLUETOOTH_CONNECT on 14+), `stopSelf()`.
2. Construct `bleClient`, `frameWriter`, `telemetrySink`, `heartbeatLoop` (battery via `BatteryManager.BATTERY_PROPERTY_CAPACITY` + `EXTRA_STATUS` charging).
3. Collect `bleClient.state`: on `Ready` enqueue identity URGENT (`isFresh = !lastWasReady`), set `lastWasReady = true`, `telemetrySink.onReady(now)`; on `Disconnected`/`Failed`/`Idle` after having been Ready: `telemetrySink.onDisconnected(now)`, `lastWasReady = false`.
4. Collect `bleClient.notifications` → `telemetrySink.onNotify(bytes, now)`.
5. Launch `heartbeatLoop.run { frameWriter.enqueue(Entry(HEARTBEAT, it.encode())) }` only while `state is Ready` (cancel job otherwise).
6. Drain loop: `while (isActive) { val e = frameWriter.take(); bleClient.write(e.frame) }`.
7. If `BikePrefs.bikeMac` non-null, `bleClient.connect(mac)`.
8. `onStartCommand`: `ACTION_STOP` → disconnect + `stopForeground` + `stopSelf`. Else START_STICKY.
9. `onDestroy`: `bleClient.disconnect(); bleClient.shutdown()`.

Manifest permissions (copy exactly):

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Service:

```xml
<service
    android:name=".ble.BikeBridgeService"
    android:exported="false"
    android:foregroundServiceType="connectedDevice" />
```

- [ ] **Step 1: Implement service + manifest + DashApp accessors**
- [ ] **Step 2: `./gradlew :app:assembleDebug` SUCCESS**
- [ ] **Step 3: Commit**

```bash
git commit -am "feat(ble): add BikeBridgeService with identity, heartbeat, and ride capture"
```

---

### Task 4: Cockpit scan / pair / connect (keep demo)

**Files:**
- Modify: `app/src/main/kotlin/dev/gdk/bumblebee/ui/cockpit/CockpitViewModel.kt`
- Modify: `app/src/main/kotlin/dev/gdk/bumblebee/ui/cockpit/CockpitScreen.kt`
- Modify: `app/src/main/kotlin/dev/gdk/bumblebee/MainActivity.kt` if permission launcher must live on Activity (prefer ViewModel + `rememberLauncherForActivityResult` in the Screen)

**Interfaces:**
- `CockpitUiState` gains: `bikeMac: String?`, `scanning: Boolean`, `devices: List<DiscoveredBike>`, `permissionNeeded: Boolean`.
- ViewModel: `fun startScan()`, `fun stopScan()`, `fun pair(mac: String)` (saves prefs + `BikeBridgeService.start`), `fun disconnectBike()` (`BikeBridgeService.stop`), keep `startDemo()` but **no-op / status warning** if `connection is Ready` from live BLE.
- Screen: list devices (Suzuki-looking first, then RSSI desc), buttons Scan, Connect (uses saved MAC), Disconnect, Start demo ride. Show live `connection` from a process-wide `StateFlow`.

**Connection state for UI:** `DashApp` holds `MutableStateFlow<ConnectionState>` updated by the service (`DashApp.connection`). Service writes it. ViewModel collects it. Avoid AppGraph.

- [ ] **Step 1: Extend DashApp**

```kotlin
val connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
val prefs by lazy { BikePrefs(this) }
val capture by lazy { RideCaptureRepository(database) }
```

Service publishes `bleClient.state` into `(application as DashApp).connection`. Use `application.capture` for TelemetrySink so demo and BLE share one repository instance (critical: two `RideCaptureRepository` objects would each think they have no active ride).

**Ruling baked into this task:** CockpitViewModel must use `(application as DashApp).capture`, not `RideCaptureRepository(database)` locally. Refactor demo path onto the singleton.

- [ ] **Step 2: Wire scan + permissions in CockpitScreen**

Request `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` on API 31+, plus `POST_NOTIFICATIONS` on API 33+. On grant, `startScan()`. Pairing a row calls `pair(mac)`.

- [ ] **Step 3: assembleDebug SUCCESS. Commit**

```bash
git commit -am "feat(ui): Cockpit scan, pair, and live BLE connection status"
```

---

## Out of scope (explicit)

- Forge UI components (`SpeedDial`, `ReadinessRadial`, landscape HUD)
- Google Maps → `a531`
- Fused Location / GPS into `onGps`
- Hilt, boot auto-start, call/SMS frames, Device Info 0x180A, BLE MAC history diag
- Merging `capture-pipeline` to `master` (ask after this plan)

## Manual on-bike check (after Task 4)

Force-stop official Suzuki Ride Connect. Grant BLE permissions. Scan → pair SBM device → ignition ON → Cockpit shows Ready, speed/fuel/odo update, heartbeat keeps link. Disconnect archives the ride. Demo still works with BLE stopped.

---

## Spec coverage (self-review)

| Spec item | Task |
|-----------|------|
| Scan, save MAC | 1 ranking + 2 scanner + 4 pair |
| Identity a536 | 3 |
| Telemetry a537 + archive | 1 sink + 3 |
| Heartbeat a533 1 Hz | 3 |
| Foreground service | 3 |
| Demo mode still works | 4 (guard vs live Ready) |
| Device Info / fuel-econ verify / nav / call | deferred |
| Landscape HUD / Forge UI | deferred (docs already landed in Task 0) |
