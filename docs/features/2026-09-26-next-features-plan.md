# Bumblebee Dash — Next Features Plan

**Date:** 2026-09-26 · **Status:** Phase A in progress · **Owner:** dineshkumar

The next release adds six rider features. Continuous rides comes first because it fixes a daily pain: every ignition off/on starts a new ride today, so one long trip gets split into many rides.

| # | Feature | Rider problem it solves | Size |
| --- | --- | --- | --- |
| 1 | Continuous rides and ride merging | A tea or fuel stop splits one trip into several rides | M |
| 2 | Live location sharing | Pillion, family or group can't see where the rider is | L |
| 3 | Crash / fall detection with SOS | Nobody is alerted if the rider goes down alone | L |
| 4 | Tank-to-tank fuel economy | Economy is wrong when a fill isn't a full tank | S |
| 5 | Garage home-screen widget | Fuel, service and document status need the app opened | S |
| 6 | Auto-connect when the bike is nearby | The rider has to open the app to link the bike | M |

Size is for one developer: **S** = up to 3 days, **M** = about 1 week, **L** = about 2 weeks.

Each section covers what the rider gets, how it behaves, what we build on in the code today, and acceptance criteria. The bug fixes from the 2026-09-26 audit are tracked separately. Features 1, 3 and 6 depend on some of those fixes; see [Sequencing](#sequencing-and-dependencies).

Built-in Google Maps navigation is a separate proposal: [2026-09-26-google-maps-navigation.md](2026-09-26-google-maps-navigation.md).

---

## 1. Continuous rides and ride merging

A stop of up to 45 minutes should not end a ride. When the bike reconnects within that window, the app continues the same ride. The rider can also merge or split rides by hand.

**Today**

- A ride finishes 20 s after the bike link drops (`DISCONNECT_GRACE_MS` in `BikeBridgeService`). Every ignition-off therefore starts a new ride.
- Explorer shows "Merge N into one" cards from `JourneyDetector`. The rules are: gap up to 120 min, odometer continues within 1 km, same calendar day, at least 15 km in total.
- The rider can only accept a suggestion as a whole. There's no manual selection and no undo.
- `RideCaptureRepository.mergeRides` averages the per-ride average speeds. That's wrong; it must weight by moving time.

### What the rider gets

1. **Auto-continue (on by default).** Turning the bike off puts the ride in a **Paused** state instead of finishing it. The Ride screen and the service notification show "Ride paused · 12 min · resumes when you start the bike".
2. **End ride now.** One button in the paused state finishes the ride immediately, for when the rider knows the trip is over.
3. **Manual merge.** In the ride list, long-press a ride to enter selection mode, tick two or more rides, and tap **Merge**. Show a preview (total km, riding time, stops) before saving.
4. **Split / unmerge.** A merged ride's detail screen lists its segments, with a **Split back** action.
5. **Smarter suggestions.** Keep the existing journey cards, and let them cover trips that cross midnight.

### Behaviour rules

| Situation | Result |
| --- | --- |
| Reconnect within the pause window, odometer within 1 km of the last value | Same ride continues; the pause is recorded as a stop |
| Reconnect within the window, but the odometer jumped more than 1 km | New ride (the bike moved without the app, e.g. phone left at home) |
| No reconnect within the window | Ride finishes; its end time is the **last telemetry**, not the timeout |
| Rider taps **End ride now** | Ride finishes immediately |
| Phone rebooted or app killed during the pause | On restart the paused ride is restored from the database |
| GPS-only ride (no bike), phone stationary | Same rules, using "no movement for N min" instead of link loss |

The pause window is a Garage setting: 15 / 30 / **45 (default)** / 60 / 120 min, or Off.

### Stats on a continuous ride

- **Moving time** excludes pauses. **Elapsed time** includes them. **Stops** lists each pause with its duration and a map pin.
- **Average speed** = total distance ÷ total moving time.
- **Max speed**, histogram and fuel bars are combined across all segments.
- The map draws one continuous track, with a stop marker at each pause.
- The daily summary counts the ride on the day it started. A ride crossing midnight shows a "continued overnight" note.

### Data model

- Add a `ride_segments` table: `id, rideId, startTs, endTs, odoStartKm, odoEndKm`. One bike session = one segment; a ride has one or more segments.
- Telemetry and track rows keep their `rideId`. Splitting reassigns them by timestamp.
- Save the ride state (`ACTIVE`, `PAUSED`, `FINISHED`) and `pausedAt` on the `rides` row, so a killed process can resume. This also fixes audit item 4.
- Room migration **5 → 6** turns existing `mergedIntoId` children into segments of their parent, then drops `mergedIntoId` use in queries. It needs a migration test.
- `DailyRollupEntity` is rebuilt for the affected dates after any merge or split, instead of being patched incrementally.

### Acceptance criteria

- [ ] A 150 km ride with three 10–30 min engine-offs is saved as **one** ride with 3 stops, with no rider action
- [ ] Force-stopping the app during a pause, then restarting it, resumes the same ride
- [ ] Merging 3 rides and splitting them back gives the same three rides and totals as before
- [ ] Daily totals, personal-best speed and route stats are unchanged by merge + split
- [x] Average speed of a merged ride equals distance ÷ moving time (unit test)
- [x] Unit tests cover every row of the behaviour-rules table
- [x] Journey cards include trips that cross midnight
- [x] Manual merge shows km, moving time and stop count before saving
- [x] Pause window is a Garage setting (15 / 30 / 45 / 60 / 120 / Off)

---

## 2. Live location sharing

The rider taps **Share ride** and sends one link. Anyone with the link sees the bike's live position, speed, ETA and ride state in a browser until the ride ends.

**Today:** the app is local-only with no server. The Cockpit **Share** tile shares a one-off location.

### What the rider gets

- A **Share ride** button on the Ride screen and in the service notification. It opens the Android share sheet (WhatsApp, SMS, …) with a link.
- Viewers open the link in any browser, with no app or login. They see:
    - a map with the bike marker and the trail so far
    - current speed, riding time, and "updated 20 s ago"
    - ETA and distance left, when navigation is active
    - **Paused** / **Ride ended** states
- **Auto-share contacts (optional).** Chosen contacts get the link automatically when a ride starts. It can be limited to rides that go beyond 30 km from home.
- The link expires when the ride finishes, or after 12 hours at most. **Stop sharing** is one tap.

### Behaviour

- Send an update every 15 s while moving and every 60 s while paused.
- With no signal, keep only the newest point and send it when back online. Don't upload a backlog.
- The viewer page shows "Last seen 4 min ago" in amber after 2 min without updates, so nobody assumes the rider is still moving.

### What we need to build

| Part | Proposed choice | Why |
| --- | --- | --- |
| Realtime store | Firebase Realtime Database (Spark free plan) | Push updates, no server to run, free for this volume |
| Viewer page | One static HTML page on Firebase Hosting, OSM tiles | Opens on any phone, no install |
| Share ID | Random 128-bit ID; no name, phone or bike in the URL | Can't be guessed or enumerated |
| Publisher | `BikeBridgeService` publishes the latest point | Keeps working with the screen off |
| Auth | Firebase anonymous auth; write rules limited to the rider's own share | Viewers can only read |

### Privacy

- Off by default. Sharing starts only when the rider taps it, or through an auto-share rule the rider created.
- The server keeps only active shares and deletes a share 24 h after it expires.
- **Privacy zone:** the first and last 500 m of every ride are hidden, so the home address isn't exposed.

### Acceptance criteria

- [ ] The link opens on a phone browser in under 3 s and shows a position within 30 s of sharing starting
- [ ] Updates keep arriving for a 2-hour ride with the phone screen off
- [ ] After the ride finishes the page shows **Ride ended** and stops updating
- [ ] Extra mobile data is under 5 MB per riding hour
- [ ] No position inside the privacy zone ever reaches the server (unit test on the publisher)

---

## 3. Crash / fall detection with SOS

If the app detects a probable crash, it sounds a 30-second alarm on the phone. If the rider doesn't cancel it, the app sends their location to up to 3 emergency contacts.

> **Positioning:** this is a best-effort safety aid, not a certified emergency device. The app and the onboarding screen must say so.

### Detection

The detector is armed only while a ride is active **and** the speed was at least 20 km/h in the last 10 s. This keeps parking, walking and a phone dropped at home from triggering it.

A crash candidate needs **all three** of these, in order:

| Step | Signal | Threshold (initial, tune from logs) |
| --- | --- | --- |
| 1. Impact | Phone accelerometer peak | ≥ 4 g, or free-fall (< 0.3 g for ≥ 300 ms) |
| 2. Sudden stop | GPS speed (1 Hz) | Drops below 5 km/h within 5 s of the impact |
| 3. No recovery | GPS speed and accelerometer | No riding movement for 20 s after the stop |

An extra signal, **to verify on the bike first:** the V-Strom's tip-over sensor cuts the engine. If the BLE link or telemetry drops at speed together with an impact, confidence goes up. We don't depend on it until it's tested.

Bike telemetry comes about every 5 s, which is too slow for detection. It's only used to confirm speed afterwards.

### What happens after detection

1. **Alarm (30 s):** full-screen activity over the lock screen, loud siren at maximum volume, strong vibration, and a large **I'm OK** button (at least 50 % of the screen, usable with gloves).
2. **SOS**, if it isn't cancelled:
    - An SMS to up to 3 contacts: "Possible crash detected for <rider>. Location: <maps link> at <time>. Speed before: 62 km/h." It includes the live share link (Feature 2) if available.
    - Optionally, a phone call to contact 1.
    - The screen shows "Help alerted" with the contacts and a **Cancel — I'm OK** button that sends a follow-up SMS.
3. Every trigger, including cancelled ones, is logged with the sensor window so we can tune thresholds.

### Settings

- Emergency contacts (max 3), rider name, blood group and medical note (included in the SMS if the rider opts in).
- Sensitivity: Low / Normal / High (changes the thresholds in the table).
- **Test SOS:** sends a message clearly marked "TEST".

### Technical notes

- Detection runs in `BikeBridgeService` (foreground). It needs the service-level GPS from audit items 8–9.
- Sample the accelerometer at `SENSOR_DELAY_GAME` (about 50 Hz) only while armed, to save battery.
- `SEND_SMS` and `CALL_PHONE` are fine for our sideloaded build. **Google Play only allows SMS permissions for default SMS apps and a few exceptions.** If we ever publish on Play, SOS must switch to a server-sent SMS (e.g. through Feature 2's backend).
- The full-screen alarm needs `USE_FULL_SCREEN_INTENT`, which requires a user grant on Android 14+.

### Acceptance criteria

- [ ] **Zero false alarms** over 500 km of normal riding, including speed breakers, potholes and hard braking (checked from the trigger log)
- [ ] Replayed sensor recordings of simulated crashes trigger in 100 % of cases (replay-based unit tests)
- [ ] The alarm appears over the lock screen within 2 s of detection
- [ ] The SMS reaches contacts within 60 s of the alarm ending, with a working map link
- [ ] Battery cost while armed is under 3 % per riding hour

---

## 4. Tank-to-tank fuel economy

The app should report true km per litre from real fill-ups and handle partial fills correctly. It should also show how far off the bike's own economy figure is.

**Today:** `FuelEconomics.perTankKmPerL` divides km since the previous fill by the litres of the current fill. That's only correct when **every fill is a full tank**. A ₹200 top-up makes the next result very wrong. The cluster's economy value (`fuelEconKml`) is shown without any check.

### What the rider gets

- A **Full tank** switch in the refuel form, on by default.
- **Partial fills** are carried forward: economy is calculated only between two full-tank fills. All litres in between are added up.

  ```
  km/L = (odo at full fill B − odo at full fill A) ÷ (sum of litres of every fill after A, up to and including B)
  ```

- A **per-tank history** list: date, km, litres, km/L, ₹/km, and a coloured delta against your average.
- A **trend chart** of km/L over the last 12 tanks.
- A **Cluster accuracy** figure: "Bike shows 38.2 km/L; your tanks say 34.9 km/L (cluster +9 %)". This factor then corrects the range estimate.
- **Outlier guard:** a tank more than 30 % off the median is flagged "Check this fill?" and left out of averages until the rider confirms it.
- **Missed fill hint:** if the odometer jumped more than the tank's range between two fills, show "Looks like a fill is missing between these dates".

### Data model

- Add `isFullTank` (default `true`) to `fuel_events` in the same **5 → 6** migration as Feature 1.
- Optionally add `pricePerLitre` for fuel-price history. Cost stats can already use `cost`.

### Acceptance criteria

- [x] Full → partial → partial → full gives the correct km/L using the total litres (unit test)
- [x] A single partial fill never produces a km/L value on its own
- [x] The cluster accuracy factor needs at least 3 full-to-full tanks before it's shown
- [x] Existing fills migrate as full-tank and the current numbers don't change

---

## 5. Garage home-screen widget

A home-screen widget shows the bike's status at a glance: fuel and range, service due, the next document expiry, and today's km.

### Layout

| Size | Content |
| --- | --- |
| **2×2** | Fuel bars + estimated range · service due in km · one alert line (e.g. "PUC expires in 6 days") |
| **4×2** | Adds today's km, last ride (km, time), link status dot, and a **Start ride** button |

- Forge style: near-black background, Suzuki yellow accents, large numbers.
- Alert line priority: an overdue document or service first, then low fuel, then anything due within 7 days.
- Tapping the widget opens the Cockpit. Tapping the alert line opens the matching Garage section.

### How it updates

- Build it with **Jetpack Glance** (current ship is `AppWidgetProvider` + RemoteViews last-known snapshot; Glance rewrite can wait).
- The widget reads a small **last-known snapshot** (fuel bars, odometer, range, service due, next expiry). The app saves this snapshot on each ride finish, fuel log, service log or document edit.
- It refreshes on those events plus the existing daily `GarageReminders` job. It never polls. While riding it updates at most once a minute.
- Show "as of 18:40" when the data is older than 1 hour, so a stale fuel reading isn't taken as live.

### Acceptance criteria

- [ ] The widget shows correct values after a reboot, with no app launch
- [ ] It updates within 5 s of logging a fuel fill or finishing a ride
- [ ] No measurable battery use from the widget in Android battery stats over 24 h

---

## 6. Auto-connect when the bike is nearby

Turning on the bike's ignition should link the phone within about 10 s, with the app closed and the phone in a pocket.

**Today:** the link starts only when the rider opens Garage and taps Connect. It then relies on `autoConnect=true` while the service stays alive. If Android or MIUI kills the service, the next ride isn't captured.

### Approach

| Android version | Mechanism | Notes |
| --- | --- | --- |
| **12+ (API 31+)** | `CompanionDeviceManager` association + `startObservingDevicePresence` → our `CompanionDeviceService.onDeviceAppeared` | The system wakes us when the bike appears. Starting a foreground service from the background is allowed for companion apps. |
| **8–11 (API 26–30)** | `BluetoothLeScanner.startScan(filter by MAC, PendingIntent)` | The system scans for us in low-power mode and wakes a receiver on a match |

**Pairing changes to the CDM picker.** "Pair bike" uses the Companion Device Manager system dialog instead of our own scan list. That also removes the need for the scan and location permissions just to pair.

### What the rider gets

- Onboarding: "Auto-connect when I turn on the bike" (on by default), plus a one-time guide to allow **Autostart** and set **No restrictions** for battery (MIUI, ColorOS and similar).
- Notification when linked: "Linked to V-Strom · recording ride". It includes **Share ride** and **Stop** actions.
- A **Do not auto-connect today** action, e.g. when someone else is riding the bike.

### Technical notes

- Depends on audit fix 3 (the service must accept a connect request in `onStartCommand`).
- With Feature 1, an auto-connect inside the pause window resumes the paused ride.
- Existing users with a saved MAC are asked once to re-pair through the CDM dialog.

### Acceptance criteria

- [ ] Ignition on → cluster shows the phone link within 15 s, 9 times out of 10, with the app swiped away
- [ ] Works after a phone reboot without opening the app
- [ ] Standby battery cost is under 1 % per day with the bike off
- [ ] Tested on the Redmi Note 7 (Android 10, legacy path) and one Android 13+ phone (CDM path)

---

## Sequencing and dependencies

| Phase | Work | Needs from the audit fixes |
| --- | --- | --- |
| **A** | 1 Continuous rides · 4 Tank-to-tank · 5 Widget | Item 4 (ride persistence), item 7 (capture threading), item 5 (demo rides out of the real DB) |
| **B** | 6 Auto-connect → 3 Crash detection | Item 3 (connect in `onStartCommand`), items 8–9 (GPS in the service) |
| **C** | 2 Live sharing (its link also improves the Feature 3 SOS SMS) | Firebase project set up |
| Parallel | Built-in Google Maps navigation (separate doc) | Decide before Phase C, since both touch the map and backend choices |

Features 1 and 4 share the **5 → 6** Room migration. Ship them together, or plan the migration once up front.

## Open questions

- [ ] **Pause window default:** is 45 min right for your usual stops, or should it be 30 or 60 min?
- [ ] **Crash signal:** does the tip-over sensor drop the BLE link or telemetry? Test with the bike on its side stand at idle.
- [ ] **Distribution:** will the app ever go on Google Play? That decides SMS SOS (on-device vs server).
- [ ] **Backend:** is Firebase acceptable for Features 2 and 3, or should nothing leave the phone except through the rider's own messaging apps?
- [ ] **Emergency contacts:** should SOS also go to a WhatsApp group? That can't be automated, but a pre-filled share could be.
