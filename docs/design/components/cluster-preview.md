# Component spec — `ClusterPreview`

A phone-side **mock of what the bike's TFT cluster is currently showing** (or would show).
Mirrors the `a531` producer state so the rider/dev can confirm what we push matches the
cluster — useful when the bike isn't in view and for debugging nav. Concept reused from
REDLINE's `ClusterPreview`.

## Role
Faithful preview of the cluster's fixed-slot layout, respecting the same constraints the
real firmware enforces. It is a **simulation**, not a live readback (the bike does not
send its display state back) — label it as such.

## Modes
The cluster reuses `a531` slots for different producers; discriminated by the
`distTotalUnit` byte (per REDLINE protocol notes):

| Mode | Shows | Trigger |
|---|---|---|
| `nav` | maneuver arrow + dist-next + ETA + total | active [turn-by-turn](./turn-by-turn-overlay.md) |
| `idleClock` | clock + weather glyph + temp | no nav, connected |
| `nowPlaying` | first chars of track title | media playing (optional) |
| `call` | caller number/name | incoming call (`a532`) |
| `sms` | sender + count | message (`a535`) |
| `degraded` | placeholder arrow / "Searching for network" | poor phone signal (`byte23`≠good) |

## Rendering fidelity (respect cluster limits)
- Fixed-width slots only; no free text (matches [data constraints](../data-availability.md)).
- Arrow glyph resolved from maneuver id (~55 `ic_step_N`); unknown id → generic arrow.
- Distances shown with the same normalization used on the wire (4-char slots).
- Degraded mode reproduces the OEM "Searching for network" behavior.

## States
`nav` · `idleClock` · `nowPlaying` · `call` · `sms` · `degraded` · `disconnected` (no frames).

## API (stateless)
```
ClusterPreview(
  frame: ClusterFrame?,   // resolved current/would-be frame: mode + slot values
)                         // null -> disconnected placeholder
```
The frame is produced by the nav mux / producers (view-model/service); the widget only
renders the mirror.

## Honesty rules
- Labeled as a **preview/simulation**, not the bike's actual pixels.
- Degraded/unknown states shown truthfully (don't render a clean arrow when the real
  cluster would show the placeholder).

## Test cases
nav (each maneuver) · idleClock · nowPlaying · call · sms · degraded · disconnected ·
unknown maneuver id → generic arrow.
