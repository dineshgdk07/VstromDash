# Review-fix completion ledger

Living status for the P0 / P1 / UI / hygiene list. Update **Status** and **What we did** as each item lands.

Statuses: `todo` · `in progress` · `done` · `deferred`

| ID | Item | Status | What we did |
|----|------|--------|-------------|
| P0.1 | JPEG-as-PNG `vstrom_hero.png`; unused `hud_hayabusa_mock.png` | done | Converted `res/drawable/vstrom_hero.png` to real PNG (1280×720 RGBA). Deleted unused `drawable-nodpi/hud_hayabusa_mock.png` so it no longer ships. |
| P0.2 | POST_NOTIFICATIONS before garage notify (API 33+) | done | `GarageReminders.post()` returns early when API 33+ and `POST_NOTIFICATIONS` is not granted. Garage screen also requests the permission. |
| P0.3 | CONNECT action + MAC in `onStartCommand` | done | `BikeBridgeService.ACTION_CONNECT` + `EXTRA_MAC`. Garage `pair()` calls `BikeBridgeService.connect()`. Default `onStartCommand` still reconnects the saved MAC. |
| P0.4 | Recover unfinished rides after process death | done | `RideCaptureRepository.recoverUnfinishedRides()` replays samples/GPS through `RideRecorder`. Fresh rides (&lt;5 min since last sample) stay active; older ones are finalized. `DashApp` and the service call recovery on start. `onDestroy` uses `runBlocking` so finish is not cancelled with `lifecycleScope`. |
| P0.5 | Demo rides must not train calibration or history | done | Room v6 `rides.isDemo`. History/stats DAOs filter `isDemo = 0`. Demo path uses `RideSession.run(..., isDemo = true)`. Calibration skip for demo rides. Product demo button is debug-only. |
| P0.6 | `Locale.ROOT` on bike protocol strings | done | `core/protocol/RootFormat.kt` `rootFormat()`. Used on heartbeat time, telemetry trip bytes, Maps distance/ETA, HUD odo/trips. Cluster frames no longer follow the phone locale. |
| P1.7 | Serialize capture; close startRide/telemetry race | done | All capture mutations go through a `Mutex` (`serial { }`). `ingestTelemetry`/`ensureRideUnlocked` open a ride if none is active, so a frame during `onReady` cannot create a second ride or throw uncaught. |
| P1.8 | Single GPS source; accuracy filter before store | done | Service owns `PhoneGps` (Fused, GPS-only fallback). UI `GpsBinder` only requests permission and starts the service. Fixes are filtered by accuracy and timestamp before archive/distance. |
| P1.9 | GPS-only recording in the foreground service | done | `ACTION_START_GPS_RIDE` / `ACTION_STOP_GPS_RIDE`. GPS-only rides call `feedPhoneGps` in the FGS so screen-off still records. Ride screen no longer writes GPS. |
| P1.10 | Weather refresh at app/service, not HUD-only | done | `DashApp` refreshes from last parked fix. Service refreshes on each GPS fix. HUD/Cockpit only collect `weather.snapshot`. |
| P1.11 | Maps nav: category/key, miles, `1,5 km`, HUD units | done | Treat as nav if `CATEGORY_NAVIGATION` or remaining-distance progress. Track `activeKey` so a different Maps notification cannot clear the active turn. Miles/feet convert to metres. Comma decimals (`1,5 km`) parse. HUD shows `250 m` / `1.2 km` (`formatHudDistance`). |
| P1.12 | Merge average speed weighted by moving time | done | `weightedAvgSpeed()` uses Σ(speed × movingSec) / Σ(movingSec). Covered in `RideCaptureRepositoryTest`. |
| P1.13 | Persist fuel detector last stable bars | done | Calibration KV `fuel_stable_bars`. Restored in `DashApp` / service startup so a refill while the process was dead is still detected. |
| P1.14 | HUD readiness includes service; live phone battery | done | HUD and Cockpit both call `ReadinessCalculator.score(..., serviceDueKm, ...)`. Heartbeat writes `DashApp.phoneBattery`; both screens collect it. |
| PERF | Room Flow, calibration on fuel change, indexes, simplify tracks, pause BLE send | done | HUD `combine()` of live streams; Ride uses Flow + 1 s ticker only while recording; Cockpit refreshes economics on `observeFuel()` and today stats on `observeFinishedRides()`. Track indexes `(rideId, ts)` and `ts`. `TrackSimplify.douglasPeucker` before route clustering. Drain/nav resend skip unless `ConnectionState.Ready`. |
| UI.HUD | Speed 0–140, fuel+range, nav; drop Hayabusa/RPM/gear/coolant/V | done | `SpeedDial` is a 0–140 km/h ring plus fuel-bar subdial, range, odo, trips, and nav label. Hayabusa branding, RPM, gear, coolant, and bike-voltage gauges removed. |
| UI.DEAD | Long-press hint, sun-only weather, AUTO / –V | done | Removed “LONG PRESS CENTER…” (no handler). Weather icon follows Suzuki/WMO mapping (sun vs cloud vs warning). AUTO headlight and “– V” dropped. Dock shows live link/GPS/nav instead of fake actions. |
| UI.ALARM | Neutral link before ignition; pair onboarding; drop empty cards | done | Unlinked-but-paired is `UNKNOWN` (“waiting for bike”), not a red/orange failure. Unpaired shows “Pair your bike”. Failed handshake is the only link `NOT_READY`. HUD warnings stay mute until linked. |
| UI.NAV | `rememberNavController` above landscape branch | done | Controller is created before the landscape `if` in `BumblebeeApp`, so rotation no longer resets the tab. |
| UI.INSETS | `navigationBarsPadding` on CockpitCore | done | Bottom bar uses `navigationBarsPadding()` so gesture nav does not cover tab labels. |
| UI.GLOVES | 64 dp hold-to-stop; demo off production | done | Hold-to-confirm stop is 64 dp / ~800 ms. Demo is `BuildConfig.DEBUG` only, labeled “Developer: demo ride”. |
| UI.RIDE | Live vs last session, icons, GPS copy, chevrons, card colour | done | Live speed hero only in `RidePhase.LIVE`. Cards use `ForgeCard`. Duplicate “GPS · GPS lock” copy and unused chevrons removed. |
| UI.A11Y | Merged tab semantics; `250 m` / `1.2 km` | done | Tabs: merged semantics, `Role.Tab`, `selected`. Distances use `formatHudDistance`. |
| HYG.ICON | App icon, label, splash, live FGS notification | done | `android:icon` / round icon, label **Bumblebee**, splash theme. FGS notification: Linked/Waiting · speed · fuel · Stop. |
| HYG.TEST | ViewModel/coroutine tests; Room 1→6 migration | done | `Migration1to6Test`, recover/demo/merge tests on the repository, `ReadinessCalculatorTest` for service/onboarding/link, nav pipeline tests. Full AndroidViewModel tests still blocked by `DashApp` globals (see deferred DI). |
| HYG.BUILD | Signing, R8, catalog lifecycle, GarageCheckReceiver action | done | Release minify/shrink; signing from `BUMBLEBEE_STORE_*` env (falls back to debug keystore). `lifecycle-runtime-ktx` in the version catalog. Receiver checks `ACTION_CHECK` and `BOOT_COMPLETED`. |
| HYG.BLE | Real cell bars; missed-call when permitted | done | Heartbeat uses `TelephonyManager.signalStrength.level` mapped to 0–3. Missed-call stays `false` unless we later request `READ_CALL_LOG` (not requested). SMS still unused. |

## Rulings

- Demo *fixture* tests still write real rides (`isDemo=false`). Only the in-app **Start demo ride** path sets `isDemo=true`.
- Unfinished rides whose last sample is older than 5 minutes are finalized on startup; fresher ones are restored as the active recorder so a kill-and-reopen mid-ride can continue.
- Maps notifications without `CATEGORY_NAVIGATION` are ignored unless they carry a remaining-distance progress bar (Maps sometimes omits the category).
- `READ_CALL_LOG` is not requested at runtime; missed-call count is not sent.
- Manual DI instead of `DashApp` `StateFlow`s is noted, not done.

## Verification

- `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:data:local:test`, `:core:nav:test`, `:core:protocol:test`, `:core:ride:test` all passed after Ride/HUD/Cockpit polish.

## Review of the fixes (2026-09-26)

Full run: `test testDebugUnitTest lintDebug assembleRelease`. **177 tests, 0 failures. Release APK builds (3.2 MB).** `lintDebug` **fails with 2 errors** (R1 below). The remaining 31 `DefaultLocale` warnings are UI display text only; no protocol path is left.

Deferrals accepted: missed-call count (no `READ_CALL_LOG`), and manual DI. Schedule the DI before feature Phase A, since it is what blocks ViewModel tests.

Reopened, most severe first:

| ID | Reopens | Finding | Fix |
|----|---------|---------|-----|
| R1 | HYG.BUILD | Lint errors: location permission; signal bars API 28 | done | `PhoneGps.listenFused` checks `ACCESS_FINE_LOCATION` at the call site. Cell bars use `TelephonyManager.signalStrength` on API 28+ and a `PhoneStateListener` cache on API 26–27. Also cleared four later lint errors: companion-device service `tools:targetApi="31"`, crash-alarm lock/screen APIs gated at O_MR1, `telephony` feature not required. |
| R2 | P0.3 | Rotation reconnects and drops the live link | done | `BleReconnect.needed()` skips `connect()` for the same MAC while Ready/Connecting/Discovering. `BleReconnectTest` covers the cases. |
| R3 | P0.5 | Demo ride poisons fuel detector, Find bike, merge | done | Demo frames skip the fuel detector and stable-bar writes; finishing a demo restores the saved bars. `trackDao.latest()` joins `isDemo = 0`. Journey suggestions drop demo rides. |
| R4 | P0.4 | Recovered bike ride never ends; walking GPS counts | done | Rides store `gpsOnly` (Room v8). Phone GPS is archived only for rider-started GPS rides. After recovery, GNSS stays off unless linked; a 20 s timer finishes a recovered bike ride at its last sample if the cluster does not return. |
| R5 | P0.4 | Two unfinished rides; onReady races recovery | done | Recovery is one-shot (`recovered` flag) and runs before `startRide`/`ensureRide`. Older unfinished rides are finished; only the newest stays active. `TelemetrySink.onReady` recovers first. |
| R6 | P1.7 | Fuel log/edit/delete and merge outside the lock | done | `logRefuel`, `updateFuel`, `deleteFuel`, and `mergeRides` run inside `serial { }`. |
| R7 | P1.11 | Non-nav Maps post clears the turn | done | `MapsNavSource.update(null, key)` ignores keys that are not the active nav notification. Tests cover post-while-navigating and remove-active-key. |
| R8 | PERF | Stable bars written every telemetry frame | done | Persist only when `lastStableBars` changes. |
| R9 | — | Empty rides still listed | done | Empty rides (no samples, no motion) are deleted instead of saved. |
| R10 | — | DB error in notify can crash the service | done | `TelemetrySink` and the GATT collector wrap ingest in `runCatching`. `onDestroy` bounds DB work with a 2 s timeout. |

Verified as described: P0.1, P0.2, P0.6, P1.8, P1.9, P1.10, P1.12, P1.13 (restore path), the drain/nav-resend pause in PERF, and `Migration1to6Test` present. The UI.* rows were not re-checked on a device in this review.

**Follow-up (R1–R10):** `:app:compileDebugKotlin`, `:data:local:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:lintDebug` all passed. Lint is error-free (warnings remain, including `DefaultLocale` on UI text). Room schema is at v8 (`gpsOnly`).

**Commit status:** still uncommitted. Commit in reviewable slices when asked.
