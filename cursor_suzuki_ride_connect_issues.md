# Suzuki Ride Connect issues
_Exported on 12/07/2026 at 2:40:36 GMT+5:30 from Cursor (3.8.24)_

---

**User**

This is the app I use to connect to my bike and use turn-by-turn nav @Suzuki+Ride+Connect_7.6_APKPure.xapk as it has a lot of lag and issues I want to update it 

---

**Cursor**

Exploring the project and the Suzuki Ride Connect package to understand what we're working with and how we can address the lag and issues.


I've looked at your workspace and the Suzuki Ride Connect v7.6 XAPK. Here's what we're working with.

## What you have

**`bumblebee-dash`** is essentially empty right now — only the XAPK file, no Flutter project yet.

**Suzuki Ride Connect 7.6** (`suzuki.com.suzuki`) is a native **Kotlin / Jetpack Compose** Android app, not Flutter. It uses:

- **Mappls** for maps (`libmappls-gl.so` — ~11 MB native map engine)
- **Realm** for local storage
- **Bluetooth LE** for bike cluster communication
- A large permission set (location, calls, SMS, notifications, etc.)

That Mappls stack plus all the background services is a common source of **lag and jank** on mid-range phones.

## What "updating" it realistically means

You can't meaningfully patch Suzuki's closed-source app to fix performance. Practical paths are:

| Approach | What it is | Pros | Cons |
|----------|------------|------|------|
| **A. Build a replacement** (e.g. `bumblebee-dash`) | New app that talks to your bike over the same BLE protocol | Faster, cleaner UX; you control nav (Google Maps, etc.) | Requires reverse-engineering the bike protocol |
| **B. Use an existing replacement** | Projects like [suzuki-connect-re / REDLINE](https://github.com/mrwick1/suzuki-connect-re) already document the BLE protocol | Protocol work largely done; Google Maps → cluster nav | Kotlin/Android, not Flutter; may need tweaks for your exact bike model |
| **C. Official update only** | Install latest from Play Store | Easiest, supported | Unlikely to fix lag; still Mappls-based |

There's already community work on the **30-byte BLE frame protocol** (GATT service `0xFFF0`, write `0xFFF1`, notify `0xFFF2`) — turn-by-turn nav, telemetry, heartbeats, etc. A replacement app intercepts nav instructions and encodes them for the cluster instead of running the heavy Mappls UI.

---

To steer the design in the right direction, I need one thing from you first:

**What is your main goal?**

- **A)** Build a new replacement app in this `bumblebee-dash` Flutter project  
- **B)** Fork/adapt an existing project like suzuki-connect-re (Kotlin)  
- **C)** Try to mod/patch the Suzuki APK itself  
- **D)** Something else (describe briefly)

Also helpful: **which Suzuki bike/model/year** do you have? Protocol details can vary by cluster generation.

---

**User**

I want to build a app but which lang it should be Flutter or Kotlin, definitely we can check suzuki-connect-re for the help and reference we need, take inspirations of the features if any introduced. 

---

**Cursor**

Comparing Flutter vs Kotlin for this bike-connect app — I'll pull suzuki-connect-re's architecture and the BLE/background requirements from the Suzuki APK.


**Recommendation: Kotlin (native Android)** — for this app, it’s the better fit than Flutter, even though your folder is named `bumblebee-dash`.

## Why Kotlin wins here

This isn’t mainly a UI app. Most of the hard work is **Android system integration**:

| Critical capability | Why it matters | Kotlin | Flutter |
|---------------------|----------------|--------|---------|
| `NotificationListenerService` | Read Google Maps turn-by-turn notifications | Native, first-class | Custom plugin required |
| Foreground BLE service (1 Hz heartbeat) | Keep cluster alive while riding | Direct `BluetoothGatt` control | `flutter_blue_plus` + native foreground service |
| 30-byte frame timing & priority queue | Nav must beat heartbeats on the wire | Exact control in REDLINE | Platform channel adds latency/complexity |
| Boot receiver, DND, SMS/call hooks | Match stock Suzuki behavior | Built-in Android APIs | Each needs a plugin |
| Background reliability (Android 14+) | App must not die mid-ride | Well-understood patterns | Possible, but more fragile |

**suzuki-connect-re / REDLINE** is already **130 Kotlin files, ~14k LOC, 205 tests**, with the protocol ported and working. You’d be adapting proven code, not starting from zero.

Flutter would still need a **large Kotlin layer** underneath for BLE + notifications + foreground services. You’d end up with hybrid complexity without much benefit, since:

- The bike cluster is **Android-only** (no iOS target)
- Lag was your main pain — native avoids Mappls *and* Flutter bridge overhead on timing-sensitive BLE writes
- The reference implementation is Kotlin, not Dart

**Use Flutter if** you strongly prefer Dart for UI *and* accept building/maintaining custom Android plugins for everything above. That’s a valid choice, but it’s more work for this specific problem.

---

## Features worth taking from REDLINE

From [suzuki-connect-re](https://github.com/mrwick1/suzuki-connect-re), these are the high-value pieces to inherit or mirror:

### Core (must-have)
- **Google Maps → cluster nav** — intercept Maps notifications, encode to `a531` frames
- **BLE bridge** — GATT `0xFFF0`, 1 Hz phone heartbeat (`a533`), telemetry read (`a537`)
- **Foreground service** — persistent connection while riding
- **Pairing wizard** — scan, save bike MAC, auto-reconnect

### Phone dashboard (fixes what Suzuki does poorly)
- Live **speed / fuel / odo / trip** from telemetry frames
- **Ride logger** — auto-record rides, GPX/CSV export
- **Last parked location** — snapshot GPS on disconnect
- **Stats & analytics** — speed histogram, calendar heatmap, personal bests

### Nice extras (REDLINE already built)
- **Idle cluster display** — clock + weather when not navigating
- **Call/SMS forwarding** to cluster (like stock app)
- **Crash detection + SOS SMS**
- **Fuel-fill log** — true km/L tracking
- **Frame inspector** — dev tool for protocol debugging (very useful early on)
- **Demo mode** — synthetic telemetry so you can build UI without the bike

### Deliberately skip for v1 (REDLINE deferred these too)
- Cloud sync, multi-bike, Play Store release, iOS, Tasker integration

---

## Suggested approach (3 options)

| | Approach | Effort | Outcome |
|---|----------|--------|---------|
| **A (recommended)** | **Fork/adapt REDLINE in Kotlin**, rebrand as `bumblebee-dash` | Lowest | Fastest path to a working, low-lag app |
| **B** | **Kotlin from scratch**, use `suzuki-connect-re` only for protocol docs + `tools/protocol.py` | Medium | Clean codebase, more RE risk |
| **C** | **Flutter UI + Kotlin service layer** | Highest | Pretty UI, but two stacks to maintain |

**I recommend A**: clone the protocol layer and BLE/nav architecture from REDLINE, strip what you don’t need, rename/rebrand, then iterate on your bike.

---

## Proposed v1 scope for `bumblebee-dash`

1. Pair with bike over BLE  
2. Google Maps nav → cluster (the main reason you’re building this)  
3. Live dashboard (speed, fuel, odo)  
4. Ride logging + trip history  
5. Settings (permissions, bike profile, notification access)  

Phase 2: idle clock/weather, SOS, fuel mileage, call/SMS mirroring.

---

One question before we write a design spec:

**Which Suzuki bike do you have (model + year)?**  
Protocol details can differ by cluster generation — REDLINE was built for a **2023 Gixxer SF 150**. If yours is different, we’ll need to verify frame compatibility on your hardware first.

Also: **are you comfortable working in Kotlin**, or do you strongly want Dart/Flutter for the UI even if the core stays native? That affects whether we go with A or C.

---

**User**

Okay then I guess it's better to decide if I can access REDLINE UI, I cloned it here /home/gdk/CodeHollow/Flutter/suzuki-connect-re

---

**Cursor**

Checking the cloned repo for the REDLINE Android app and how to build and open its UI.


Yes — the REDLINE UI is fully in your clone. It isn’t a separate download; it’s the Android app under `android/`.

## What you have locally

The UI lives here:

```
/home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/app/src/main/kotlin/dev/mrwick/redline/ui/
```

**83 Compose screens/components**, including:

| Tab / area | Key files |
|------------|-----------|
| **Home** | `ui/home/HomeScreen.kt`, `ActiveRideCard.kt`, `ClusterPreview.kt` |
| **Dashboard** | `ui/dashboard/DashboardScreen.kt` — live speed/fuel/odo |
| **Stats** | `ui/stats/StatsScreen.kt` + charts |
| **Trips** | `ui/trips/TripsScreen.kt`, `PostRideSummary.kt` |
| **Settings** | `ui/settings/SettingsScreen.kt` + pairing, developer tools |
| **Theme** | `ui/theme/GixxerTokens.kt` — dark premium design system |

Design direction: dark Linear-style UI, Inter + Geist Mono fonts, Suzuki-red accent — documented in `docs/superpowers/specs/2026-05-25-ui-overhaul-design.md`.

---

## Can you see it running right now?

**Not yet on this machine.** Your environment is missing the Android build toolchain:

| Requirement | Your machine |
|-------------|--------------|
| JDK 17 | Not installed |
| Android SDK | `ANDROID_HOME=/home/gdk/Android` but folder doesn’t exist |
| Phone/emulator | No Android device connected |

---

## Three ways to access the UI

### 1. Browse source (works now)
Open the clone in Android Studio or Cursor and read the Compose files. No build needed.

### 2. Run on your Android phone (best — full interactive UI)
Once JDK + SDK are set up:

```bash
# 1. Create local.properties pointing at your SDK
cp android/local.properties.example android/local.properties
# Edit: sdk.dir=/path/to/Android/Sdk

# 2. Build & install
cd /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.mrwick.redline.debug/dev.mrwick.redline.MainActivity
```

**No bike required for UI exploration:** after onboarding, go to **Settings → Developer → enable “Demo mode”**. That feeds synthetic telemetry so Home/Dashboard/Stats populate with fake ride data.

### 3. Generate UI screenshots on your laptop (no phone)
The repo has Roborazzi screenshot tests. With JDK 17 only:

```bash
cd android
./gradlew :app:recordRoborazziDebug
```

That writes PNGs of Home, Stats, theme specimens, etc. under `android/app/src/test/`.

---

## Fastest path to get running

**Option A — Android Studio (recommended)**  
1. Install [Android Studio](https://developer.android.com/studio)  
2. Open `/home/gdk/CodeHollow/Flutter/suzuki-connect-re/android`  
3. Let it install SDK + JDK 17  
4. Connect your phone (USB debugging) or start an emulator  
5. Click Run ▶  

**Option B — CLI only**

```bash
sudo apt install openjdk-17-jdk
# Install Android cmdline-tools, set ANDROID_HOME, accept licenses
# Then build as above
```

---

## What the app looks like in use

1. **Onboarding** — welcome → permissions → pair bike → start (you can skip pairing for UI-only with demo mode later)  
2. **5 bottom tabs** — Home / Dashboard / Stats / Trips / Settings  
3. **Developer tools** — Settings → Developer → Frame Inspector, Demo mode, diagnostics  

Package name when installed: `dev.mrwick.redline.debug`

---

## For your `bumblebee-dash` fork

You don’t need to rebuild the UI from scratch. The practical path is:

1. Get REDLINE running on your phone (evaluate the UI)  
2. Fork/rebrand the `android/` project into `bumblebee-dash`  
3. Customize theme, rename app, adapt for your bike model  

Want me to set up the Android build environment on this machine (install JDK 17 + Android SDK and try a first build)? Or walk you through opening it in Android Studio step by step?

---

**User**

Can you help me to to setup ? I have a mobile connected and I did  cp android/local.properties.example android/local.properties but not installed JDK or SDK

---

**Cursor**

Checking your environment and setting up JDK 17 and the Android SDK so we can build and install REDLINE on your phone.


Phone detected over USB. Installing JDK 17 and the Android SDK, then configuring the project and building.


sudo needs your password, so we'll install JDK and the Android SDK in your home directory instead.


Phone connected (Redmi Note 7). Updating project config and building the app.


Build succeeded. Installing on your phone and launching the app.


Setup is complete. REDLINE should be open on your **Redmi Note 7** now.

## What was installed

| Component | Location |
|-----------|----------|
| **JDK 17** (Temurin) | `/home/gdk/.local/jdk-17` |
| **Android SDK** | `/home/gdk/Android/Sdk` |
| **adb** | `/home/gdk/Android/Sdk/platform-tools/adb` |

## Project config updated

- `android/local.properties` → `sdk.dir=/home/gdk/Android/Sdk`
- `android/gradle.properties` → Java home pointed to your JDK (the original path was for the original author's machine)
- `android/env.sh` → quick env setup for future builds

## Build & install

- APK built successfully (~6 min first build)
- Installed as **REDLINE** (`dev.mrwick.redline.debug`)
- Permissions pre-granted: Bluetooth, location, notifications, SMS
- App launched on your phone

---

## On your phone right now

You should see the **onboarding wizard**:

1. Welcome → grant any remaining permissions if prompted  
2. Pair bike → you can **skip for now** if you just want to explore the UI  
3. After onboarding: **Settings → Developer → turn on “Demo mode”**  
   - Simulates bike telemetry so Home/Dashboard/Stats populate without a real bike connection

---

## Rebuild later (copy-paste)

```bash
source /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/env.sh
cd /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.mrwick.redline.debug/dev.mrwick.redline.MainActivity
```

Add this to your `~/.bashrc` so new terminals pick up the tools:

```bash
export JAVA_HOME="/home/gdk/.local/jdk-17"
export ANDROID_HOME="/home/gdk/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

---

## Optional: Android Studio

You can also open `/home/gdk/CodeHollow/Flutter/suzuki-connect-re/android` in Android Studio and point it at the same SDK path — useful for live UI editing with Compose.

---

Is the app showing on your phone? If you hit any crash or permission screen, tell me what you see and we can fix it.

---

**User**

The app is opened I can see the app, I browsed/explored it

---

**Cursor**

Great — that means the dev setup is working and you’ve seen what REDLINE gives you as a Suzuki Connect replacement.

**What you’ve validated:**
- Dark dashboard UI (Home, Stats, Trips, Settings)
- Much lighter feel than stock Suzuki + Mappls
- A solid base to fork into your own app (`bumblebee-dash`)

**When you’re ready to go further, the natural order is:**

1. **Demo mode** (no bike) — Settings → Developer → “Demo mode” to see live dashboard/stats with fake telemetry  
2. **Real bike test** — pair via Settings, force-stop Suzuki Ride Connect so they don’t fight for BLE  
3. **Fork & rebrand** — copy the `android/` project into `bumblebee-dash`, rename app/icon/theme  

What do you want to tackle next?

- **A)** Fork it into `bumblebee-dash` and rebrand  
- **B)** Test with your actual Suzuki bike  
- **C)** Pick specific features to add/remove first  

Also, which **Suzuki model/year** do you have? REDLINE was built for a 2023 Gixxer SF 150 — we should confirm compatibility with yours before a real bike test.

---

**User**

It doesn't show any Google map screen to test/access map

---

**Cursor**

REDLINE likely doesn't embed Google Maps — checking how navigation is wired in the app.


That’s expected — **REDLINE does not include an in-app map screen**.

Unlike Suzuki Ride Connect (which embeds Mappls maps), REDLINE is built differently on purpose.

## How navigation works in REDLINE

```
┌─────────────────────┐         ┌──────────────────────┐
│  Google Maps app    │  ──→    │  Your bike cluster   │
│  (separate app)     │  BLE    │  (turn arrows, etc.) │
└─────────────────────┘         └──────────────────────┘
         ↑
   REDLINE reads Maps notifications
   and sends turn data to the bike
```

REDLINE is a **bridge**, not a map app. It does not render maps itself.

## What you can use in the app today

| Where | What it does |
|-------|----------------|
| **Home → “Nav” button** | Opens the **Google Maps** app on your phone |
| **Quick destinations** (Home/Work) | Starts navigation in Google Maps to a saved address |
| **Last parked → “Open in Maps”** | Opens that location in Google Maps |
| **Cluster preview** (Home) | Simulates what the **bike display** would show — not a full map |

You need **Google Maps installed** on the phone. Tap **Nav** on the Home screen; it should switch you to Google Maps.

---

## Important: Google Maps → bike sync is currently disabled

In this codebase, the feature that reads Google Maps turn-by-turn notifications and sends them to the bike was **turned off** on 2026-06-04 (“parked”). The code is still there but commented out in `NotificationDispatcher.kt`.

So right now REDLINE can:
- Open Google Maps externally
- Show dashboard, stats, trips, demo telemetry

But it **cannot** yet pipe live Google Maps directions to your Suzuki cluster until that code is re-enabled (and you’re connected to the bike).

---

## Compared to Suzuki Ride Connect

| | Suzuki Ride Connect | REDLINE |
|---|---------------------|---------|
| In-app map | Yes (Mappls) | No |
| Navigation UI | Inside the app | Google Maps app |
| Bike cluster nav | Via Mappls | Intended via Maps notifications → BLE |
| Lag | Often heavy (Mappls SDK) | Lighter phone app |

---

## What do you want for `bumblebee-dash`?

**A)** Re-enable Google Maps → cluster passthrough (no in-app map; same model as REDLINE, but working)

**B)** Add an embedded map screen (like Suzuki — more work; Mappls or Google Maps SDK)

**C)** Both — embedded map on phone + directions sent to the cluster

If you tell me A, B, or C, we can plan the next step. For a quick check now: install Google Maps if needed, open REDLINE, tap **Nav** on Home — you should leave REDLINE and land in Google Maps.

---

**User**

Also I want to completely rebuild the UI and Have more features, later we may use dedicated backend too ? Is it better to develop from scratch but fot the protocols to communiate with bike can refer REDLINE and for few features too ?

---

**Cursor**

Checking how tightly REDLINE couples its protocol layer to the UI — that determines whether a greenfield rebuild with borrowed protocol code is practical.


**Yes — that’s the right approach.** For what you want (new UI, more features, optional backend later), don’t fork REDLINE’s UI. Build a **new app** and treat REDLINE as a **protocol + reference library**, not as the product codebase.

## Recommended strategy

```
bumblebee-dash (new project)
├── core/protocol/     ← port from REDLINE + tools/protocol.py (keep as-is)
├── core/ble/          ← port BleClient, FrameWriter, HeartbeatLoop, GATT UUIDs
├── core/nav/          ← port GoogleMapsParser, NavFrame encoding (re-enable nav)
├── data/local/        ← Room — rides, settings, bike profile
├── data/remote/       ← empty stub now; API client later
├── feature/           ← your new features, one module each
└── app/ui/            ← brand-new Compose UI (your design)
```

| Layer | Build from scratch? | Use REDLINE? |
|-------|---------------------|--------------|
| **UI / UX** | Yes — full rebuild | Inspiration only (what screens exist, not how they look) |
| **BLE protocol** (30-byte frames) | No — don’t re-reverse-engineer | Port `protocol/` + `tools/protocol.py` |
| **BLE connection** (GATT, heartbeat) | Adapt, don’t copy blindly | Port `ble/` as a library module |
| **Maps → cluster nav** | Re-implement cleanly | Port `nav/` (currently disabled in REDLINE — you’d re-enable it) |
| **Ride logging, stats** | Redesign data model | Borrow ideas; rewrite for your schema + future sync |
| **Backend** | Design interfaces now | Not needed for v1 |

---

## Why not fork REDLINE?

REDLINE was built fast (many parallel agents). It works, but:

- **`AppGraph` singletons** tie UI, BLE, and storage together — hard to extend cleanly
- **UI is entangled** with REDLINE’s design system — a “complete rebuild” means fighting existing code
- **Google Maps nav is disabled** — you’d fix that anyway
- **No backend hooks** — rides/settings are local-only with no sync layer

Forking saves a week now and costs months when you add features + backend.

---

## Why not from scratch without REDLINE?

The bike protocol is the risky part — 30-byte frames, checksums, heartbeat timing, response-driven GATT. REDLINE already documented and tested that. Re-doing it from zero adds weeks and real risk on the bike.

**Reuse the protocol layer. Rebuild everything else.**

---

## Backend later — design for it from day 1

You don’t need a backend for v1, but structure the app so adding one is straightforward:

```kotlin
interface RideRepository {
    fun observeRides(): Flow<List<Ride>>
    suspend fun syncRides(): Result<Unit>  // no-op until backend exists
}

class RideRepositoryImpl(
    private val local: RideDao,
    private val remote: RideApi?,  // null in v1
) : RideRepository
```

**Backend could later provide:**
- Cloud ride history & sync across devices  
- Shared bike profiles / fleet (if multiple bikes)  
- OTA config (maneuver maps, feature flags)  
- Analytics dashboard (web)  
- User accounts & premium features  

**Stack options when you’re ready:** Supabase/Firebase (fast MVP), or Kotlin Ktor / Go (more control).

Local-first keeps the app working offline on rides; sync when online.

---

## Suggested phases for `bumblebee-dash`

| Phase | Goal |
|-------|------|
| **v0** | New project scaffold + port protocol/BLE modules + connect to bike |
| **v1** | Your new UI + Google Maps → cluster nav + live dashboard |
| **v2** | Extra features (your list) + ride export + better analytics |
| **v3** | Backend + sync + maybe a web dashboard |

---

## What to borrow vs rewrite from REDLINE

**Borrow (port almost as-is):**
- `protocol/` — all 7 frame types  
- `ble/SuzukiGatt.kt`, `BleClient`, `FrameWriter`, `HeartbeatLoop`  
- `tools/protocol.py` — tests & reference  
- `nav/GoogleMapsParser.kt`, `ManeuverMap.kt` (after re-enabling)  

**Rewrite:**
- All `ui/` screens & design system  
- `AppGraph` → proper DI (Hilt/Koin) + repositories  
- Settings, onboarding, navigation structure  
- Data models if you want backend sync fields (`remoteId`, `syncedAt`, etc.)  

**Inspire from REDLINE features:**
- Demo mode, frame inspector (dev tools)  
- Ride logger, GPX export, last parked  
- Call/SMS → cluster, idle clock/weather  
- Crash detection / SOS  

---

## Bottom line

| Approach | Verdict |
|----------|---------|
| Fork REDLINE and reskin | Too tangled for a full UI rebuild |
| 100% from scratch | Wastes protocol work already done |
| **New app + ported protocol/BLE + new UI** | Best fit for your goals |

---

To shape v1, two things would help:

1. **Which features matter most** beyond nav + dashboard? (e.g. embedded map, social rides, maintenance reminders, multi-bike)  
2. **Your Suzuki model/year** — to confirm protocol compatibility before we port the BLE layer  

When you’re ready, we can scaffold `bumblebee-dash` as a clean Kotlin multi-module project and port the protocol layer first.

---

**User**

First I need to know what are all we have to start the UI planning right ? Can you let me know what detail we can get from biike, how frequest, what are we can push to cluster

---

**Cursor**

Gathering the full bike protocol spec from REDLINE's documentation and source so we can plan the UI accurately.


This is the **bike ↔ phone capability map** you need before UI planning. It comes from REDLINE’s reverse-engineered protocol (`NOTES.md`, `tools/protocol.py`). Reference bike: **2023 Gixxer SF 150** — your model may differ slightly.

---

## Connection basics

The bike exposes **one BLE channel**:

| GATT | Direction | Purpose |
|------|-----------|---------|
| Service `0xFFF0` | — | Suzuki vendor service |
| Char `0xFFF1` | Phone → bike | **Write** commands |
| Char `0xFFF2` | Bike → phone | **Notify** telemetry |

Every message is **exactly 30 bytes**: `0xA5` header + type byte + payload + checksum + `0x7F` terminator. No encryption.

**Important behavior:**
- Bike **does not push data on its own** — it starts sending only after the phone writes an **identity frame (a536)** on connect.
- Phone must send **heartbeat (a533) ~1×/sec** to keep the link alive.
- Bike has **no SIM / no cloud** — all cluster data flows through the phone over BLE.

**Also readable (Device Info service):** model `"XF"`, serial, firmware `0.1.5.8`, manufacturer `"Suzuki"`.

---

## What you GET from the bike (a537 telemetry)

**Direction:** Bike → phone (notify on `0xFFF2`)  
**Frequency:** ~every **5 seconds** (after identity handshake)  
**Only message type** the bike sends.

| Field | Format | Example UI use |
|-------|--------|----------------|
| **Speed** | 3 digits, km/h | Live speedometer, ride stats |
| **Odometer** | 6 digits, km | Total km, maintenance reminders |
| **Trip A** | XXXXX.X km | Trip meter A |
| **Trip B** | XXXXX.X km | Trip meter B |
| **Fuel level** | 1–6 bars (`'1'`–`'6'`) | Fuel gauge, range estimate |
| **Fuel economy** | byte 25 ÷ 2 ≈ km/L (Gixxer) | Avg efficiency display |

**What you do NOT get from the bike over BLE:**
- GPS / location (use phone GPS)
- Engine RPM, throttle, coolant (not in a537 for Gixxer)
- Turn-by-turn state (phone pushes that *to* the cluster)
- Real-time map tiles

**UI implication:** Dashboard = **speed, odo, trips, fuel**. Location/routing = **phone-side only**.

---

## What you PUSH to the cluster (phone → bike)

Six frame types on `0xFFF1`:

### 1. a531 — Navigation (turn-by-turn on cluster TFT)

| When | ~2–3/sec during active nav |
|------|----------------------------|

| Field | Cluster shows |
|-------|---------------|
| **Turn arrow** (maneuver ID, byte 2) | Left/right/U-turn/roundabout icon (~55 arrow types) |
| **Distance to next turn** (4 chars + unit) | e.g. `0080` + `M` → 80 m |
| **ETA** (6 chars) | e.g. `0530PM` or `001730` |
| **Total distance remaining** (4 chars + unit) | e.g. `0348` + `K` → 348 km |
| **Status** (byte 23) | `'1'` = normal; `'0'/'4'/'6'` = degraded → cluster may show “Searching for network” and hide arrow |

**Creative reuse (REDLINE):** same frame slots can show **clock, weather, now-playing, welcome message** when not navigating — cluster treats them as text fields.

**UI implication:** No embedded map required on phone if you use **Google Maps externally** and mirror turns to the cluster. Phone UI = nav status + cluster preview, not a full map (unless you add one).

---

### 2. a533 — Heartbeat / phone status dashboard

| When | **~1 Hz** (once per second), continuous while connected |
|------|----------------------------------------------------------|

| Field | Cluster may show |
|-------|------------------|
| Phone battery (0–3 buckets) | Battery icon on cluster |
| Charging Y/N | Charging indicator |
| Phone cell signal (0–3) | Signal bars — **critical**: bad signal → nav arrows hidden |
| Current time (hhmmss) | Clock on cluster |
| SMS pending Y/N | Notification dot |
| Call pending Y/N | Call indicator (may be unused on some bikes) |
| Weather code (0–11) | Weather icon (sunny/rain/snow/etc.) |
| Outdoor temp (°C) | Temperature |

**UI implication:** Settings for rider name, weather source, notification mirroring. Cluster gets **phone environment**, not bike engine data.

---

### 3. a536 — Identity (on connect)

| When | **Once** on pair/reconnect (must send within ~30 s or link drops) |
|------|---------------------------------------------------------------------|

| Field | Purpose |
|-------|---------|
| Rider name (up to 20 chars) | Shown on cluster |
| Fresh `'F'` vs reconnect `'R'` | First-time vs returning pair |

**UI implication:** Onboarding → “Your name on the cluster”, bike pairing screen.

---

### 4. a532 — Incoming call

| When | Event-driven (call rings) |
|------|---------------------------|

| Field | Cluster shows |
|-------|---------------|
| Caller number (20 chars) | Incoming call popup |
| WhatsApp vs cellular | `'W'` / `'N'` |

---

### 5. a534 — Missed call

| When | Event-driven |
|------|--------------|

| Field | Cluster shows |
|-------|---------------|
| Caller name | Missed call alert |
| Missed count | Badge count |

---

### 6. a535 — SMS / WhatsApp / app notifications

| When | Event-driven |
|------|--------------|

| Field | Cluster shows |
|-------|---------------|
| Sender name (20 chars) | Who messaged |
| Message count | Unread count |
| Silenced Y/N | Do-not-disturb style |

**UI implication:** Notification access permission, allowlist (which apps mirror to cluster).

---

## Frequency summary (for UI refresh rates)

| Data source | Update rate | UI poll suggestion |
|-------------|-------------|-------------------|
| Bike telemetry (a537) | ~5 s | Poll every 1–2 s; animate on change |
| Heartbeat to bike (a533) | 1 Hz | Background service (not UI thread) |
| Nav to bike (a531) | ~2–3/s during nav | Driven by Maps notifications |
| Calls/SMS (a532/a534/a535) | Instant | Notification listener |
| Phone GPS | 1–5 s | For ride logging, last parked |
| Weather | ~30 min | Cache; optional refresh |

---

## UI planning matrix — what screens can show what

| Screen idea | Bike BLE | Phone only | Push to cluster |
|-------------|----------|------------|-----------------|
| **Live dashboard** | Speed, fuel, odo, trips | — | Optional idle clock/range |
| **Navigation** | — | Google Maps (external or embedded) | Turn arrow, dist, ETA |
| **Ride history** | — | GPS track + logged a537 samples | — |
| **Fuel / range** | Fuel bars + km/L | Range estimate math | Optional range on cluster |
| **Last parked** | — | Phone GPS on disconnect | — |
| **Calls / SMS** | — | Android notifications | Caller/sender on cluster |
| **Settings / pair** | Device info | Permissions, MAC, name | Identity frame |
| **Maintenance** | Odometer | Your reminders | — |
| **Social / backend sync** | — | Future API | — |

---

## Cluster display modes (what the rider sees on the bike TFT)

```
┌─────────────────────────────────────┐
│  NAV MODE (a531, status='1')         │
│  [arrow]  200 m    ETA 5:30 PM      │
│           12.4 km remaining         │
├─────────────────────────────────────┤
│  IDLE MODE (a531 reused)            │
│  14:32    28°C    ☀                  │
│  or NOW PLAYING track title         │
├─────────────────────────────────────┤
│  CALL / SMS (a532/a534/a535)        │
│  Incoming: +91 98xxx                │
│  WhatsApp: John (2)                 │
└─────────────────────────────────────┘
```

Phone app UI ≠ cluster UI. Plan **phone dashboard** + **cluster preview widget** (REDLINE has `ClusterPreview` for this).

---

## Hard limits to respect in UI design

1. **No free text on cluster** — only fixed slots (dist, ETA, name, etc.), max widths enforced.
2. **Nav arrows need good phone signal** — status byte `'1'`; otherwise cluster shows placeholder / “Searching for network”.
3. **No bike GPS** — “where is my bike” = last phone GPS when disconnected.
4. **Fuel economy on Gixxer** — use `byte25 / 2`, not the generic formula.
5. **Model variants** — Access-TFT, Burgman, e-ACCESS decode fuel/telemetry differently; confirm for your bike.

---

## What REDLINE already maps to UI (feature reference)

| REDLINE feature | Protocol used |
|-----------------|---------------|
| Dashboard speed/fuel/odo | a537 |
| Google Maps → cluster | a531 (disabled in current build) |
| Idle clock + weather on cluster | a531 + a533 |
| Call/SMS on cluster | a532, a534, a535 |
| Rider name on connect | a536 |
| Ride logging | a537 samples + phone GPS |
| Frame inspector (dev) | All TX/RX frames |

---

## Suggested UI modules for `bumblebee-dash` v1 planning

1. **Connect** — pair, name, connection state  
2. **Ride dashboard** — speed, fuel, odo, trips (from a537)  
3. **Navigation** — external Maps + cluster mirror (a531)  
4. **Cluster preview** — phone mock of what bike shows  
5. **Notifications** — call/SMS/app allowlist → cluster  
6. **Trips & stats** — phone GPS + logged telemetry  
7. **Settings** — permissions, demo mode, developer tools  

Backend later: sync **rides, settings, bike profile** — not live BLE (that stays phone ↔ bike).

---

If you share your **exact Suzuki model/year**, we can flag anything that might differ from Gixxer SF 150 before you lock the UI spec. After that, we can turn this into a screen-by-screen wireframe list for `bumblebee-dash`.

---

**User**

V Stromx 250 SX 2024 model

---

**Cursor**

Checking protocol compatibility for the 2024 V-Strom 250 SX versus REDLINE's Gixxer reference.


**2024 V-Strom 250 SX** is a strong match for the same Suzuki Connect stack REDLINE documents. Here’s what that means for your UI plan.

## Compatibility with REDLINE protocol

| Aspect | V-Strom 250 SX 2024 | Confidence |
|--------|---------------------|------------|
| Same app | Suzuki Ride Connect (`suzuki.com.suzuki`) | **Confirmed** |
| BLE service | `0xFFF0` / write `0xFFF1` / notify `0xFFF2` | **Very likely** (same app → same protocol family) |
| Frame format | 30-byte frames, 7 message types | **Very likely** |
| Platform | Based on **Gixxer 250** (not Gixxer SF 150) | Same electronics generation |
| Bike SIM / cloud telemetry | No SIM; BLE-only for live data | **Confirmed** (Suzuki India spec) |

REDLINE was tested on a **2023 Gixxer SF 150**. Your bike is a **sibling platform** (Gixxer 250–based adventure model), not a Burgman / Access-TFT / e-ACCESS variant. Expect the **default petrol decode path**, not the special TFT/EV formulas.

---

## What your V-Strom cluster supports (from Suzuki spec)

These map directly to protocol frames:

| Suzuki feature | Protocol | UI you can plan |
|----------------|----------|-----------------|
| Turn-by-turn nav icons | **a531** | Nav screen + cluster preview |
| ETA | **a531** | Nav + trip planning |
| Incoming / missed calls | **a532 / a534** | Notification settings |
| SMS / WhatsApp alerts | **a535** | App allowlist |
| Speed warning | Phone-side (compare a537 speed vs limit) | **New feature** — not in REDLINE, easy to add |
| Phone battery on cluster | **a533** | Auto (heartbeat) |
| Fuel / odo / trips | **a537** (~every 5 s) | Dashboard, stats, maintenance |

---

## Data you’ll get from *your* bike (a537, ~5 s)

Same fields as Gixxer family:

```
Speed (km/h)     → live speedometer, speed alerts
Odometer (km)    → service intervals, trip totals  
Trip A / Trip B  → dual trip meters (cluster has both)
Fuel (1–6 bars)  → gauge, range estimate
Fuel economy     → likely byte25÷2 km/L (verify on bike)
```

**Not from bike BLE:** GPS, map, RPM, lean angle, tyre pressure.

---

## What you push to *your* cluster

Same six phone → bike frame types:

| Frame | Rate | V-Strom use |
|-------|------|-------------|
| **a531** Nav | ~2–3/s when navigating | Turn arrows, distance, ETA |
| **a533** Heartbeat | 1/s | Time, phone battery, signal, weather |
| **a536** Identity | On connect | Rider name on cluster |
| **a532/a534/a535** | On events | Calls, SMS, WhatsApp |

**Nav note:** Cluster only shows real turn arrows when phone signal status is good (`status = '1'`). Bad signal → “Searching for network” / placeholder arrow — same as Gixxer.

---

## Likely differences vs Gixxer SF 150 (verify on first connect)

| Item | Risk | First-bike test |
|------|------|-----------------|
| Maneuver arrow mapping | Low — default branch expected | Maneuver sweep / real Maps nav |
| Fuel economy formula | Low — use `byte25 ÷ 2` | Compare to cluster avg km/L |
| Trip A/B encoding | Medium | Compare cluster vs app readings |
| Idle display layout | Medium | Clock/weather on cluster slots |
| Device Info model string | Unknown until paired | Read on pair (Settings → About) |

On first pair, log **Device Info** (model, serial, firmware). That tells us which decode branch to use.

---

## UI planning tailored to V-Strom 250 SX

Adventure / touring bias:

### Must-have screens (v1)
1. **Connect** — pair, rider name, connection status  
2. **Ride dashboard** — speed, fuel bars, odo, Trip A/B, range estimate  
3. **Navigation** — Google Maps (external or embedded later) + cluster mirror  
4. **Cluster preview** — mock of what the TFT shows  
5. **Trips** — GPS track + logged telemetry (long rides matter on a V-Strom)  
6. **Settings** — permissions, notifications, speed limit alert  

### Strong V-Strom extras (v2)
- **Speed warning** — Suzuki advertises this; compare live speed (a537) to user limit  
- **Range / refuel planner** — fuel bars + km/L + tank capacity estimate  
- **Service by odometer** — chain, oil, valve intervals from a537 odo  
- **Last parked location** — phone GPS when you disconnect  
- **Long-trip stats** — distance, duration, avg speed, fuel used (estimated)  

### Backend later (fits touring well)
- Cloud ride history & sync  
- Multi-device access  
- Service reminders from odo  
- Route sharing / repeat routes  

---

## Protocol reuse for `bumblebee-dash`

| Reuse from REDLINE | Notes for V-Strom |
|--------------------|-------------------|
| `protocol/` (all 7 frames) | Port as-is |
| `ble/` connection handshake | Port as-is |
| Telemetry decode | Default petrol; add `fuel_econ_kml_v2` (byte25÷2) |
| Nav → cluster | Re-enable Google Maps path |
| ManeuverMap | Default branch; confirm after first pair |
| Call/SMS mirroring | Same |

---

## First real-bike test checklist (when you’re ready)

1. Pair with ignition ON, Suzuki Connect **force-stopped**  
2. Confirm a537: speed, odo, fuel bars match cluster  
3. Start Google Maps nav → check turn arrow on cluster  
4. Test incoming call → cluster popup  
5. Note Device Info model/firmware in app logs  

---

**Bottom line:** Your **2024 V-Strom 250 SX** should work with the same BLE protocol REDLINE documents. Plan UI around **a537 dashboard + a531 nav + notifications**; treat Gixxer-specific details as **90% reusable**, **10% verify on your bike**.

When you want to move forward, we can turn this into a **screen-by-screen wireframe list** for `bumblebee-dash` (Home, Dashboard, Nav, Trips, Settings) with exact data bindings per field.

