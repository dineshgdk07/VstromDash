# Ride Intelligence — Insights Catalog & Data Capture Plan

**Status:** Design catalog · **Created:** 2026-07-28
**Parent spec:** `2026-07-12-bumblebee-dash-design.md` (§5 capability map, §6 roadmap, D-012)

This document answers one question exhaustively: **given everything we have, everything the
bike sends, and everything we can save, what is the maximum intelligence this app can ever
produce?** It is the master menu for the Ride Engine. Individual releases pick from it;
nothing here is committed to v1 unless the parent spec already says so.

**The one rule that makes all of this possible:**

> **Insights ship later, but raw data capture ships in v1.** Every insight below is
> retroactively computable *only if* the raw telemetry, GPS track, and events were logged from
> the first ride. Storage is cheap; missed rides are gone forever.

---

## Part A — Data Inventory (what we have to work with)

### A1. From the bike (`a537`, ~every 5 s — the only frame it sends)

| Signal | Form | Notes |
|--------|------|-------|
| Speed | km/h | Cluster-accurate, but 0.2 Hz — GPS fills the gaps |
| Odometer | km, lifetime | Ground truth for all distance math; immune to GPS dropouts |
| Trip A / Trip B | km | Rider-managed counters; useful as user intent signals |
| Fuel level | 1–6 bars | Coarse, but bar *transitions* + odometer = real consumption data |
| Fuel economy | ≈ byte25/2 km/L | Cluster's own average; our cross-check, not our source |

**Implicit signals (free, nobody uses them):**

| Signal | How we get it |
|--------|---------------|
| Engine session start/end | BLE connect/disconnect + telemetry starting/stopping = Ride boundaries |
| Ignition-on time | Time between session start and first movement (warm-up habit) |
| Idle vs moving | speed == 0 while connected |
| Bar-drop odometer marks | Odometer reading at the exact moment fuel drops a bar |
| Refuel events | Fuel bars *increase* between or during sessions |
| Cluster-vs-app drift | Our GPS distance vs odometer delta per ride (wheel/speedo error factor) |

### A2. From the phone (the real sensor suite)

| Source | Signals | Rate |
|--------|---------|------|
| GPS | lat/lng, altitude, bearing, speed, accuracy | 1 Hz while riding |
| Accelerometer + gyroscope | acceleration, braking, lean/cornering, vibration | event-sampled (see B3) |
| Barometer | precise relative elevation | 1 Hz |
| Weather API | temp, wind speed/direction, rain, humidity | per ride + hourly |
| Google Maps | routes, traffic, road names, POIs, fuel stations, geocoding | on demand |
| Clock/calendar | time of day, weekday, season | free |
| Phone state | battery, cell signal (already in heartbeat) | free |

### A3. From the rider (low-friction, optional inputs that multiply insight value)

| Input | When asked | Unlocks |
|-------|-----------|---------|
| Litres + cost at refuel | Auto-detected refuel → one optional prompt | True L/100km, cost/km, tank calibration |
| Service records | Garage | Maintenance predictions, cost of ownership |
| Document dates (insurance, PUC) | Garage, once | Expiry reminders, readiness input |
| Journey names/notes/photos | Post-ride | Memories, richer summaries |
| Home/work locations | Settings or learned | Commute detection, travel radius |

---

## Part B — What we save (the capture schema)

Room tables, all carrying `remoteId`/`syncedAt` per D-016. Estimated volume for a heavy year
(300 h riding): ~216k telemetry rows + ~1.1M GPS points ≈ **60–80 MB** — trivial for SQLite.

### B1. Raw streams (append-only, per ride)

- **`telemetry_samples`** — `(rideId, ts, speedKmh, odometerKm, tripA, tripB, fuelBars, econRaw)`
  every `a537` frame, verbatim. Never aggregated away; this is the archive.
- **`track_points`** — `(rideId, ts, lat, lng, altitude, bearing, gpsSpeed, accuracy)` at 1 Hz,
  thinned to ~0.2 Hz when stationary.

### B2. Derived events (computed live, cheap to query forever)

- **`fuel_events`** — `(ts, rideId?, odometerKm, barsBefore, barsAfter, type: BAR_DROP | REFUEL,
  litres?, cost?, lat/lng?)` — the backbone of all fuel intelligence.
- **`ride_events`** — `(rideId, ts, type, magnitude, lat/lng)` where type ∈
  `HARSH_BRAKE, HARD_ACCEL, OVERSPEED, LONG_IDLE, PB_BROKEN, NO_BREAK_2H, …` — we store the
  *event*, not the raw IMU stream.

### B3. Sensor policy (IMU)

Raw accelerometer/gyro at 50–100 Hz is too heavy to keep. Policy: process in memory during the
ride → persist only (a) detected events with a ±5 s raw snippet, (b) per-minute summary stats
(peak lateral g, peak decel, vibration RMS). This keeps behavior/safety insights possible
without gigabytes.

### B4. Session and rollup records

- **`rides`** — start/end ts + location + geocoded place, odometer start/end, GPS distance,
  moving/stopped/idle time, max & avg moving speed, speed-band histogram (JSON), elevation
  gain/max, weather snapshot, fuel bars start/end.
- **`journeys`** — name, ride list, aggregate stats, notes/photos refs.
- **`daily_rollups`** — `(date, km, rides, movingMin, maxSpeed, fuelBarsUsed)` — makes
  calendar heatmaps, streaks, and trends O(days) instead of O(samples).
- **`calibration`** — learned constants: km-per-bar curve (per bar position — bars are rarely
  linear), odometer-vs-GPS factor, typical warm-up time. The app gets smarter per ride.

---

## Part C — The Insights Catalog

Each insight lists: what the rider sees → data needed → tier (①=v1, ②=v1.5, ③=v2, ④=v3/cloud).
Voice per D-007: companion, factual, never judgmental. Facts and Insights stay visually
separated per the plan discussion (mechanical vs narrative styling).

### C1. Speed & Pace

| # | Insight | Data | Tier |
|---|---------|------|------|
| 1 | Top speed today / this month / all-time PB ("Today's top speed was 84 km/h") | telemetry | ① |
| 2 | Speed-band distribution ("You spent 72% of this ride cruising 50–70 km/h") | histogram per ride | ② |
| 3 | Pace smoothness score (speed variance; "steady pace throughout") | telemetry | ② |
| 4 | Urban vs highway split per ride (speed profile + stop frequency + GPS road class) | telemetry + track | ② |
| 5 | Cruising sweet spot ("Your natural cruising speed is 62 km/h") — across all rides | histograms | ② |
| 6 | Best acceleration runs (GPS 0→60 estimates) | track + IMU | ③ |
| 7 | Speed discipline: % of time over posted limits, overspeed map | track + Maps limits | ③ |
| 8 | Personalized ETA ("Google says 4h10m; at your pace on roads like this, expect 4h35m") | ride history + route | ③ |

### C2. Fuel Intelligence (the star category — 6 bars becomes a fuel computer)

| # | Insight | Data | Tier |
|---|---------|------|------|
| 9 | Real km-per-bar, learned per bar position ("Bar 3 lasts you ~48 km") | fuel_events + odometer | ② |
| 10 | True range prediction ("Fuel stop needed around 265 km — near Sira on this route") | calibration + route + Maps stations | ②/③ |
| 11 | Auto fuel-fill log (refuel detected; optional litres/cost prompt) | fuel_events | ② |
| 12 | True economy from fills (km between full-tank refuels ÷ litres) vs cluster average | fuel log | ② |
| 13 | Economy vs speed band ("You get your best mileage holding 55–65 km/h") | telemetry + fuel_events | ③ |
| 14 | Economy trend over 1,000 km — engine-health early warning ("efficiency down 8% since last service") | fuel history | ③ |
| 15 | Economy vs conditions (rain, heat, headwind, elevation) | fuel + weather + track | ③ |
| 16 | Last-bar behavior ("You typically ride 38 km after the last bar starts blinking") | fuel_events | ② |
| 17 | Fuel spend: cost/km, monthly fuel budget, cost per journey | fuel log with cost | ② |
| 18 | Your fuel stations map (refuel GPS clusters; "you refuel at Indian Oil, Hosur Rd 80% of the time") | fuel_events + geocode | ③ |

### C3. Distance, Usage & Milestones

| # | Insight | Data | Tier |
|---|---------|------|------|
| 19 | Today / week / month / year km; this-month-vs-last comparisons | rollups | ① basic, ② trends |
| 20 | Odometer milestones ("Your V-Strom crossed 15,000 km today") | telemetry | ① |
| 21 | Riding calendar heatmap + streaks ("You've ridden 12 days in a row") | rollups | ② |
| 22 | Longest ride / longest day / biggest journey PBs ("This is your longest ride this month") | rides | ② |
| 23 | Odometer projection ("At this pace you'll cross 20,000 km by October") | rollups | ③ |
| 24 | Odometer-vs-GPS honesty check ("Your speedo reads ~4% optimistic — real distance was 96 km") | calibration | ③ |
| 25 | Trip A/B awareness ("Trip A has run 412 km since you last reset it — tracking something?") | telemetry | ③ |

### C4. Time & Habit Patterns

| # | Insight | Data | Tier |
|---|---------|------|------|
| 26 | Moving vs stopped vs idle per ride ("Nearly 20% of your journey was breaks") | telemetry | ① |
| 27 | Start-time habits ("Most of your rides this month start between 8:00–9:00 AM") | rides | ② |
| 28 | Commute detection (repeated route + time) → commute vs adventure classification | track history | ③ |
| 29 | Break analysis: typical break length, favorite stop spots (GPS clusters of ride gaps) | rides + track | ③ |
| 30 | Night/day riding share; seasonal patterns ("You ride 3× more in winter mornings") | rides + rollups | ③ |
| 31 | Traffic-light tax ("You spent 14 minutes stationary in traffic today") | telemetry + track | ② |
| 32 | Warm-up habit ("You usually idle 90 s before moving off") | telemetry | ③ |

### C5. Route & Exploration

| # | Insight | Data | Tier |
|---|---------|------|------|
| 33 | Lifetime riding heatmap ("your map") — every road ever ridden | track | ② |
| 34 | New-roads detection + Explorer Score ("28 km of today's ride was on roads you've never ridden") | track history | ③ |
| 35 | Travel radius ("Farthest you've been from home: 212 km — Penukonda") | track + home | ② |
| 36 | Places counter (districts/states visited on the bike) | track + geocode | ③ |
| 37 | Elevation story: gain, max altitude, ghat-section detection ("You climbed 1,240 m today") | track + barometer | ② |
| 38 | Twistiness score / corner count (bearing-change density; "134 corners on this ride") | track | ③ |
| 39 | Where-is-my-bike: last parked location + walk-to-bike directions | last GPS at disconnect | ① |
| 40 | Route familiarity ("This is your 23rd time on this road") | track history | ③ |

### C6. Riding Behavior & Safety (phone IMU — mount-dependent, always framed as information, never scolding)

| # | Insight | Data | Tier |
|---|---------|------|------|
| 41 | Harsh-brake / hard-accel events with map pins | ride_events | ③ |
| 42 | Ride smoothness score + long-term trend | IMU summaries | ③ |
| 43 | Cornering/lean estimates and PBs (fun, clearly labeled as estimates) | gyro + track | ③ |
| 44 | Road-quality/vibration profile of routes ("the Mysuru road stretch is rough after km 40") | vibration RMS + track | ③/④ |
| 45 | Fatigue guard ("2 h 10 m without a break — longest stretch this month") | rides | ② |
| 46 | Crash/fall detection → SOS flow (already roadmapped v2) | IMU + GPS | ③ |
| 47 | Rain-riding log ("You rode 45 min in rain today") — feeds maintenance | weather + rides | ② |

### C7. Motorcycle Health & Ownership

| # | Insight | Data | Tier |
|---|---------|------|------|
| 48 | Service countdown by km *and* time, from real odometer ("Oil change in ~380 km") | odometer + service records | ② |
| 49 | Chain-care nudges triggered by actual conditions ("500 km and one rainy ride since last lube") | odometer + weather log | ③ |
| 50 | Consumables life tracking (tyres, brake pads, battery age) by km + months | odometer + records | ③ |
| 51 | Economy-degradation alert as health proxy (see #14) | fuel history | ③ |
| 52 | Cost of ownership: ₹/km all-in (fuel + service + consumables), monthly report | fuel log + records | ③ |
| 53 | Document expiry countdown (insurance, PUC) as readiness input | documents | ② |
| 54 | BLE link quality diagnostics (drop frequency, reconnect times) — for us and power users | connection log | ② |

### C8. Weather Intelligence

| # | Insight | Data | Tier |
|---|---------|------|------|
| 55 | Ride-weather log ("Hottest ride this year: 41 °C, Chitradurga") | weather per ride | ② |
| 56 | Weather-along-route with departure-window advice ("Leave before 3 PM to beat the rain at Tumkur") | route + hourly forecast | ② |
| 57 | Headwind effect ("Strong headwind today — that's why economy dropped") | wind + bearing + fuel | ④ |
| 58 | Personal comfort profile ("Your longest rides happen at 22–28 °C") | rides + weather | ③ |

### C9. Journey Narrative & Memories

| # | Insight | Data | Tier |
|---|---------|------|------|
| 59 | Journey story timeline: rides + stops + places + weather, auto-assembled ("Penukonda Adventure: 396 km, 5 sessions, 2h18m of breaks") | journeys | ② |
| 60 | Auto journey naming from geocoded farthest point | track + geocode | ② |
| 61 | Photo/note pins on the journey map | user input + track | ③ |
| 62 | Monthly Ride Report — one narrative card: km, PBs, new roads, efficiency, next service | all rollups | ③ |
| 63 | **Year in Review** ("Your 2026 with the V-Strom") — total km, biggest day, farthest point, corners, weather extremes, shareable | everything | ③ |
| 64 | Achievements aligned with the philosophy (First 400 km day, 100 rides, 5 states) — engineered badges, not gamification confetti | rollups | ③ |

### C10. Predictive & Readiness (where everything converges)

| # | Insight | Data | Tier |
|---|---------|------|------|
| 65 | Ride Readiness Score inputs get real: fuel range vs planned distance, weather window, service due, documents, phone battery/signal | calibration + all | ① score, ② depth |
| 66 | Pre-ride Briefing: "Fuel covers this route with one stop (suggested: Sira). Rain possible after 4 PM. Chain lube due." | route + all | ② |
| 67 | Break suggestions on long routes matched to your habits + good stop spots | habits + route | ③ |
| 68 | Post-ride debrief: 3 headline insights max, auto-chosen by notability (PB > anomaly > trend) | ride engine | ② |
| 69 | Anomaly whispers ("Idle time unusually high today", "First ride in 12 days — tyres/pressure check?") | history baselines | ③ |

---

## Part D — How this maps to the roadmap

| Release | From this catalog |
|---------|-------------------|
| **v1.0** | **Full capture pipeline (Part B — non-negotiable)** + free insights: #1, 19, 20, 26, 39, 65-score. Post-ride summary shows facts + one headline insight. |
| **v1.5** | Histograms & habits (#2–5, 21–22, 27, 31, 45), fuel intelligence core (#9–12, 16–17), maintenance (#48, 53), journey stories (#59–60), briefing (#66), debrief (#68), heatmap (#33), elevation (#37), weather log (#55–56), link diagnostics (#54). |
| **v2.0** | IMU behavior suite (#41–43, 46–47), exploration (#34–36, 38, 40), predictions (#10 full, 13–15, 23–24, 49–52, 58, 67, 69), reports & Wrapped (#62–64). |
| **v3.0+** | Cloud-dependent: cross-device history, road-quality crowdsourcing (#44), headwind modeling (#57), sharing. |

**Selection principles when building any release:**
1. An insight ships only when its data has been captured long enough to be *true* (e.g. km-per-bar needs ~3 full tank cycles before it speaks).
2. Insights speak the companion voice — one sentence, no raw numbers without meaning, never judgmental (D-007).
3. Max 3 insights per surface (post-ride debrief rule) — notability-ranked, silence is acceptable.
4. Facts (from the bike) and Insights (from the Ride Engine) stay visually distinct.

---

## Open questions

- IMU reliability depends on phone mount rigidity — behavior insights (#41–43) need a real-world
  noise-floor test before committing UX.
- Fuel bar hysteresis on the V-Strom (bars flickering on slopes/braking) needs a debounce
  strategy — verify during the §9 first-bike checklist.
- Speed-limit data source for #7 (Google's limits API is restricted; OSM is the likely fallback).
- Refuel litres prompt: notification vs next-app-open card — decide at v1.5 design time.
