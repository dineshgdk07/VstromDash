# Component spec — `TurnByTurnOverlay`

The actual point of the app: mirror Google Maps directions onto the bike's TFT cluster.
This is the only component that **writes** to the bike (`a531`). It is two things at
once: (1) a phone-side nav strip, (2) the encoder that pushes maneuver frames to the
cluster. Protocol reference: REDLINE `nav/` (currently "parked"/disabled there — we
re-enable and rebuild cleanly).

## Data flow
```
Google Maps (turn-by-turn notification)
  → NotificationListener (needs Notification Access permission)
  → GoogleMapsParser: android.title (instruction), android.subText (dist),
    progress/progressMax (fraction), largeIcon (maneuver bitmap)
  → ManeuverClassifier → Mappls maneuver id → cluster byte (ManeuverMap, default branch)
  → NavFrame (a531).encode()  [30 bytes]
  → FrameWriter (priority NAV) → BleClient.write(0xFFF1)   ~2–3 frames/sec while navigating
Phone strip renders the same ParsedNavData for the rider.
```

## Inputs it renders / encodes
| Field | Phone strip | Cluster slot (`a531`) | Source |
|---|---|---|---|
| Maneuver icon | arrow glyph | `byte2` maneuver id (~55 arrows) | 🔵 Maps |
| Distance to next | "200 m" | `bytes4-7` 4-char + `byte8` unit | 🔵 Maps |
| ETA | "5:30 PM" | `bytes9-14` 6-char | 🔵 Maps |
| Total remaining | "12 km" | `bytes18-21` 4-char + `byte22` unit | 🔵 Maps |
| Road / step text | full text | — (no free-text slot on cluster) | 🔵 Maps |
| Nav status | active badge | `byte23` status digit | 🔵/computed |

## Hard cluster constraints (must design around)
- **No free text on cluster** — only fixed-width slots; road names live on the phone only.
- **Arrows require good phone signal**: cluster shows a real arrow only when `byte23='1'`
  (`/'3'/'5'`). On poor signal the OEM firmware forces `0x2e` and shows "Searching for
  network". So the overlay must surface a **"cluster degraded — poor signal"** hint to
  the rider, or they'll think the app is broken.
- Distance overflow: 4-char slot; ≥1000 formats differ (see protocol NOTES). Reuse
  REDLINE's `normalizeDistance` rules; don't pass raw strings.
- Maneuver id mapping is the **default (non-TFT-Access/Burgman) branch** for the V-Strom —
  VERIFY on first pair via a maneuver sweep; misses fall back to a generic arrow.

## States
| State | Trigger | Phone | Cluster |
|---|---|---|---|
| `hidden` | no active Maps nav | overlay collapsed | idle frames (clock) |
| `active` | Maps nav + good signal | full strip | real arrow + dist + ETA |
| `degraded` | Maps nav + poor phone signal | strip + "cluster degraded" hint | placeholder / "Searching for network" |
| `rerouting` | Maps recalculating | subtle "rerouting" | last frame held |
| `arrived` | destination reached | "Arrived" | nav teardown hint then idle |
| `blocked` | no Notification Access / Maps not installed | CTA to grant / install | idle |

## API (stateless)
```
TurnByTurnOverlay(
  nav: ParsedNavData?,   // null = hidden; carries maneuver, distNext, eta, distTotal, roadText
  phoneSignalGood: Boolean,   // gates the degraded hint + a531 status byte upstream
  navPermissionGranted: Boolean,
  mapsInstalled: Boolean,
)
```
Encoding/writing (`NavFrame`, `FrameWriter`, status-byte selection) lives in the nav
view-model / service, NOT the composable. The overlay is render-only; the same
`ParsedNavData` feeds [`ClusterPreview`](./cluster-preview.md).

## Edge cases
- Notification Access revoked mid-ride → `blocked` + persistent re-grant CTA.
- Maps killed / swipes away notification → fall to `hidden` after stale watchdog (~60 s).
- GPS/phone signal flapping → debounce `degraded` so the hint doesn't flicker.
- Non-English Maps strings → maneuver classifier may miss → generic arrow (log for sweep).

## Test cases
active/degraded/hidden/blocked · dist 50 m / 900 m / 12 km / 1200 km · ETA 12h & 24h ·
maneuver: left/right/uturn/roundabout/arrive · permission off · Maps absent.
