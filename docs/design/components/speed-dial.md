# Component spec — `SpeedDial`

The hero of the [landscape Rider HUD](../screens/rider-hud-landscape.md) and the biggest
departure from the mockup: the RPM tach is rebuilt as a **speedometer**. Analog ring +
digital readout show one metric (speed) in agreement. Built stateless (all inputs as
params) so it previews and screenshot-tests with no live bike.

## Role
Primary live-speed instrument (landscape center). A smaller `SpeedReadout` reuses the
same value binding for portrait/HUD chips.

## Data binding & sourcing
- **Primary: phone GPS speed** — ~1 Hz, smooth, accurate. Drives the needle.
- **Fallback: bike `a537` speed** — ~5 s, stepped. Used only when GPS is unavailable.
- `source` param (`GPS` / `BLE` / `NONE`) renders the inset `SourceDot` so a stepped
  needle (BLE) reads as expected behavior, not a bug.
- **Smoothing:** interpolate between GPS samples over the ~1 s interval with a spring
  (critically-ish damped) so the needle sweeps rather than snaps. On BLE fallback, ramp
  over ~800 ms between the 5 s samples (clearly "assisted", still legible).
- **Standstill jitter:** clamp GPS speed `< 3 km/h` to `0` to kill GPS noise at rest.

## Geometry / scale
- Range **0–160 km/h** (V-Strom 250 SX tops ~135; 160 gives headroom + a redline feel).
- Sweep **270°**, gap at bottom; major ticks every **20**, minor every **10**; labels at
  majors. Tabular numerals.
- Digital readout centered: large speed integer + `km/h` beneath; matches needle value exactly.
- Heading (`CompassHeading`) sits in the old "GEAR" slot below the number; fuel arc left,
  economy arc right (see [inventory](./README.md)).

## Over-limit zone (the repurposed "redline")
- User sets a **speed limit** (Settings; default off). Bound as `limitKmh?`.
- Ring segment from `limitKmh → 160` renders in **error red**.
- When `speed > limitKmh`: needle tip + digital readout tint red, optional one-shot haptic
  + subtle `RelayToast`, and (if enabled) push the speed-warning to the cluster. This
  realizes the speed-exceeding warning Suzuki advertises for this bike.
- No limit set → no red zone; ring stays neutral/yellow accent.

## States
| State | Trigger | Render |
|---|---|---|
| `idle` | connected, speed 0 | needle at 0, dial dim-lit |
| `riding` | speed > 0, GPS | smooth needle, yellow accent |
| `overLimit` | speed > limit | red accent + warning |
| `bleFallback` | GPS lost, bike speed present | `SourceDot=BLE`, ramped needle |
| `noData` | no GPS and no BLE speed | needle 0, digital "--", muted, `SourceDot=NONE` |

## API (stateless params)
```
SpeedDial(
  speedKmh: Int,          // resolved value (GPS or BLE) already selected upstream
  source: SpeedSource,    // GPS | BLE | NONE  -> SourceDot + smoothing profile
  limitKmh: Int?,         // null = no over-limit zone
  maxKmh: Int = 160,      // scale max
  fuelBars: Int?,         // for left FuelArc composition (0..6, null=unknown)
  economyKml: Float?,     // for right EconomyArc (null=engine off/no data)
  headingDeg: Float?,     // CompassHeading; null=GPS lost -> blank
)
```
Speed-source selection (GPS-preferred, BLE-fallback, jitter clamp) lives in the
view-model, NOT the composable — keeps the widget pure and testable.

## Responsive
- Landscape: fills center column, ~min(height*0.8) diameter.
- Portrait/compact: degrade to `SpeedReadout` (digital + thin arc), same bindings.
- Glove-readable: min tick contrast, large digits, no sub-14sp text on the dial.

## Edge cases
- `speed > maxKmh` → peg needle at max, digital shows true value.
- GPS reacquire after tunnel → cross-fade `SourceDot` BLE→GPS, resync needle with spring.
- Rapid limit changes → red zone animates width; no needle disruption.

## Test cases (screenshot + unit)
- 0 / 40 / 135 / 175 (peg) km/h · limit=80 under & over · GPS vs BLE source · noData ·
  fuel 6→0 · economy present/null · heading present/null.
