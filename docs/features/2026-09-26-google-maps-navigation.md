# Bumblebee Dash — Built-in Google Maps Navigation

**Date:** 2026-09-26 · **Status:** Proposed · **Owner:** dineshkumar

Add turn-by-turn navigation inside the app using the **Google Navigation SDK for Android**, with our own GCP key. The SDK's turn-by-turn data feed would drive the bike cluster directly. This replaces guessing turns from Google Maps notification text.

For one rider, expected usage stays well inside Google's monthly free caps (see [Cost](#cost-and-free-quota)).

**Short answer to "will this help?"**

- **Turn-by-turn accuracy: yes, a lot.** We get a typed maneuver (e.g. "roundabout, 3rd exit"), exact metres to the turn about once a second, lane guidance and reroute events. Today we parse English notification text.
- **Ride insights: partly.** Most insights (speed, stops, fuel, moving time) already come from our own GPS and bike data. Google adds:
    - a planned-vs-actual route
    - reroute count
    - time lost in traffic
    - road names per stretch
    - optionally, a snapped track and elevation

---

## 1. Problem with today's approach

`NotificationCaptureService` reads Google Maps' navigation notification. `GoogleMapsParser` and `MapplsIdGuesser` then turn it into an `a531` frame for the cluster.

| Limitation today | Effect for the rider |
| --- | --- |
| Maneuver guessed from English keywords (`"slight right" in s`) | Wrong or default arrow for phrasings we didn't anticipate, or when Maps is in Hindi or Kannada |
| Roundabout exit number not reliably parsed | Cluster shows a generic roundabout |
| Distance only as fresh as the notification (Maps updates it irregularly) | Distance to the turn lags, especially in the last 100 m |
| Any Maps notification can be mistaken for navigation (audit item 11) | Navigation cleared or overwritten unexpectedly |
| Google Maps must run in parallel | Two navigation apps, more battery; the ride and the route aren't linked |
| No route data | No planned-vs-actual, ETA accuracy or reroute insights |

## 2. What the rider gets

1. **Search and go from the Journey tab.**
    - Destination search with autocomplete, plus recent and saved places (Home, Office, favourite cafés).
    - A route preview with up to 3 alternatives: time, km, and "via NH 48".
    - The **two-wheeler** travel mode, which Google offers in India.
    - A **Start** button.
2. **Guidance on the phone.**
    - Portrait: Google's navigation map with our Forge header (speed, fuel, range).
    - Landscape: our Rider HUD with a turn banner and a mini map.
    - Voice prompts, if the rider wants them over a helmet headset.
3. **Guidance on the bike cluster.** It shows the correct arrow, exact distance to the turn, ETA and distance remaining, updated every second. This also works with the phone in a tank bag and the screen off.
4. **Fuel-aware routing hint.** If the estimated range is shorter than the route, warn before starting: "Range 85 km, route 120 km — add a fuel stop?"
5. **Route stored with the ride.** The ride detail shows the planned route next to the ridden track, plus ETA accuracy, reroutes and traffic delay.
6. **Fallback.** A setting chooses between **Built-in navigation** (default) and **Follow Google Maps app** (today's notification path). The fallback stays for offline use, or when the key or quota is unavailable.

## 3. Architecture

```
Journey UI ── search (Places Autocomplete) ── route preview ── Start
                                                       │
                                       Navigation SDK (Navigator)
                                                       │ registerServiceForNavUpdates (~1 Hz)
                                                       ▼
                                    NavUpdatesService ─ NavInfoMapper ─► NavFrame (a531)
                                                                            │
      Google Maps notification path (fallback) ─► NavSource ◄───────────────┘
                                                       │
                                           BikeBridgeService → FrameWriter NAV lane → cluster
```

- **`NavSource` interface.** `MapsNavSource` becomes one implementation, `NotificationNavSource`. The new one is `BuiltInNavSource`. `BikeBridgeService` reads whichever source is active, so the cluster code doesn't change.
- **New module `core:navigation-android`.** It wraps the Navigation SDK: initialisation, the terms dialog, destinations, starting and stopping guidance. That keeps the SDK out of `app` UI code.
- **`NavInfoMapper` (pure Kotlin, unit-tested).** It converts `NavInfo` / `StepInfo` into `ParsedNavData`:
    - maneuver → Mappls ID → cluster byte, through the existing `ManeuverMap`
    - distance to the step → `distNext` + unit
    - time and distance remaining → `eta`, `distTotal`
    - `REROUTING` → status byte for "calculating"
    - `STOPPED` → clear navigation
- **Maneuver table.** One explicit row per SDK `Maneuver` constant, for example `TURN_LEFT`, `TURN_SLIGHT_RIGHT`, `TURN_SHARP_LEFT`, `TURN_U_TURN_*`, `ROUNDABOUT_*` (with exit number), `FORK_*`, `MERGE_*`, `ON_RAMP_*`, `OFF_RAMP_*`, `DESTINATION_*`, `FERRY_*`. The build fails a test if a new SDK constant has no mapping.
- **Background running.** Guidance must keep running with the screen off. Use the SDK's foreground-service hooks so riders see **one** notification (ours) rather than two. Confirm the exact API during the spike.
- **Maps SDK conflict.** The Navigation SDK replaces the Maps SDK; both can't be in one app. We don't use the Maps SDK today (Explorer uses our own OSM tiles), so there's no conflict. Any future Google map view must come from the Navigation SDK.

## 4. More ride insights (optional APIs)

| Insight | Source | Needed? |
| --- | --- | --- |
| Planned vs ridden route, detour km | Route from the Navigation SDK + our GPS track | Core — no extra API |
| ETA accuracy ("arrived 6 min later than first ETA") | `NavInfo` time remaining over the ride | Core |
| Reroute count, time lost in traffic | `NavInfo` state changes + remaining-time jumps | Core |
| Road names per stretch ("42 km on NH 48") | `StepInfo` road names while guiding | Core, only for guided rides |
| Clean, snapped track and exact distance | Roads API **Snap to Roads** | Optional |
| Climb / descent, highest point | Elevation API (sampled path, 1 request per ride) | Optional |
| Fuel stations along the route | Places API (search near route) | Optional, later |
| Posted speed limits | Roads API Speed Limits | Probably not; check eligibility first |

## 5. Cost and free quota

From the [Google Maps Platform pricing page](https://developers.google.com/maps/billing-and-pricing/pricing) and the [Navigation SDK billing page](https://developers.google.com/maps/documentation/navigation/android-sdk/usage-and-billing), checked 2026-09-26.

Free usage is now a **per-API monthly free cap**. The page no longer mentions the old flat $200 monthly credit.

| API (SKU) | Free per month | Price after free cap (first tier) | Our expected use per month | Within free? |
| --- | --- | --- | --- | --- |
| Navigation SDK — Navigation Request (billed **per destination**; reroutes free) | 1,000 | $25 / 1,000 | ~60–120 (2–4 trips a day) | Yes |
| Places — Autocomplete Requests | 10,000 | $2.83 / 1,000 | ~300–1,000 keystroke requests | Yes |
| Places — Autocomplete Session Usage | Unlimited | — | — | Yes |
| Geocoding | 10,000 | $5.00 / 1,000 | < 100 | Yes |
| Elevation | 5,000 | $5.00 / 1,000 | ~60 (1 per ride) | Yes |
| Roads — Snap to Roads (100 points per request) | **Check in the console** (Nearest Road lists 5,000) | — | ~1,500 (40 riding hours at 1 Hz) | Likely; verify |
| Compute Routes Essentials (only if we preview routes without the SDK) | 10,000 | $5.00 / 1,000 | 0 (the SDK returns routes) | — |

**Guardrails (must do before the first build):**

- [ ] Restrict the API key to our **Android apps** (package `dev.gdk.bumblebee` and `dev.gdk.bumblebee.debug` + SHA-1 fingerprints), and only to the APIs above
- [ ] Set **per-API daily quota caps** in the GCP console at or below free-cap ÷ 30 (e.g. Navigation 30/day). A bug can then never spend money.
- [ ] Add a **budget alert** at ₹100 and ₹500 on the billing account
- [ ] Keep the key in `local.properties` and inject it with a manifest placeholder. Never commit it; add a CI check that fails if a key pattern appears in the repo.

## 6. Terms and constraints

- Billing must be enabled on the GCP project, even when usage is free.
- The SDK shows Google's **terms and conditions dialog** before first use, and it must be accepted. Google attribution must stay visible on the map.
- **Storing Google data:** Maps Platform terms limit caching of Google content (routes, snapped points, place details). Before the Roads API or route geometry is stored with rides, check the current service terms. Our **own** GPS track has no such limit. Plan for now: store planned routes only while the ride is active, plus derived numbers (detour km, reroutes). Store Google geometry permanently only if the terms allow it.
- The Navigation SDK needs a data connection to fetch routes and handles poor coverage only partly. The notification fallback (and later, offline maps) covers dead zones.
- The SDK adds several MB to the APK. Check the size once it's integrated.

## 7. Phases

| Phase | Scope | Size |
| --- | --- | --- |
| **0. Spike (2–3 days)** | GCP project, key restrictions, quotas; Navigation SDK hello-world on the Redmi Note 7; register the TBT feed and log `NavInfo` on a real ride | S |
| **1. Guidance on the phone** | Journey search, route preview (two-wheeler), start and stop, voice, background running with one notification | M |
| **2. Guidance on the cluster** | `NavSource` interface, `NavInfoMapper` + full maneuver table with tests, fallback switch, reroute and arrival handling | M |
| **3. Ride linkage and insights** | Route attached to the active ride; planned-vs-actual, ETA accuracy, reroutes, traffic delay in ride detail; optional Snap to Roads / Elevation | M |
| **4. Polish** | Saved places, fuel-range warning, HUD mini map, error states (no key, quota hit, offline) | S |

Relation to the audit fixes: phase 2 replaces the notification parsing problems in audit item 11 and the locale formatting in item 6 for the built-in path. The fallback path still needs those fixes.

## 8. Acceptance criteria

- [ ] On three test routes (city, highway, roundabout-heavy), the cluster arrow matches Google's maneuver on **100 %** of steps (from a logged comparison)
- [ ] Distance to the turn on the cluster is within 20 m of the SDK value, refreshed at least every 2 s
- [ ] Guidance and cluster updates keep running for 1 hour with the phone screen off
- [ ] Reroutes update the cluster within 5 s and aren't billed (confirmed in the GCP console)
- [ ] With navigation mode set to **Follow Google Maps app**, today's behaviour is unchanged
- [ ] A month of normal use shows **₹0** billed in the GCP console

## 9. Open questions

- [ ] Does **two-wheeler mode** give sensible routes for the V-Strom on highways? Two-wheeler routing sometimes avoids expressways that 250 cc bikes may use. Compare with car mode in the spike.
- [ ] Do we want voice guidance at all, or cluster + phone only?
- [ ] Do we keep planned routes permanently (needs the terms check) or only derived stats?
- [ ] Should saved places live only on the phone, or sync later with Feature 2's backend?

## Sources

- [Google Maps Platform pricing](https://developers.google.com/maps/billing-and-pricing/pricing)
- [Navigation SDK for Android — overview](https://developers.google.com/maps/documentation/navigation/android-sdk/overview)
- [Navigation SDK for Android — usage and billing](https://developers.google.com/maps/documentation/navigation/android-sdk/usage-and-billing)
- [Navigation SDK for Android — turn-by-turn data feed](https://developers.google.com/maps/documentation/navigation/android-sdk/tbt-feed)
