# Component spec — `ReadinessRadial`

The "Ride Readiness 96% — Good to Go" hero gauge (dashed radial + shield). A **synthetic
go/no-go score** computed from real inputs only. It must stay honest: it is NOT a
mechanical health check — the bike exposes no engine diagnostics.

## Role
Confidence-at-a-glance before a ride. Hero on the [portrait Cockpit](../screens/cockpit-portrait.md);
also a side card on the [landscape HUD](../screens/rider-hud-landscape.md). Tapping opens
a breakdown sheet showing each pillar.

## Pillars (all from knowable data)

Input set aligned with **D-008** and insights-catalog **#65** (fuel range vs planned
distance, weather window, service due, documents, phone battery/signal, BLE link).
v1 shows the score; the tap-through detailed readiness screen is v1.5 per the roadmap.

| Pillar | Input | Ready / Caution / Not-ready |
|---|---|---|
| **Fuel** 🟢/🟡 | fuel bars (0–6); v1.5: learned range vs planned route distance | ≥3 ready · 2 caution · ≤1 not-ready |
| **Weather** 🔵 | forecast for now + route window | clear/cloudy ready · rain caution · storm/extreme not-ready |
| **Route** 🔵 | GPS lock + destination set (optional) | GPS fix ready · acquiring caution · no fix not-ready |
| **Link** 🔵 | BLE connected + phone signal + phone battery | connected+signal ready · weak/low-battery caution · disconnected not-ready |
| **Service/Docs** 🟡 | service due (odo + user intervals) + document expiry (user-entered) | ok ready · due-soon caution · overdue/expired not-ready; **unknown until the rider enters data** |

> The mockup's "Bike" pillar is replaced by **Link** (+ Fuel, + Service/Docs). There is no
> mechanical/ECU pillar because the bike sends no engine health data.

## Scoring
- Each pillar → 0–100 (ready=100, caution≈50, not-ready=0), weighted (default equal).
- Overall % = weighted average, rounded. Label bands: **≥85 "Good to Go"** (green) ·
  **50–84 "Check"** (amber) · **<50 "Not Ready"** (red).
- Any single **not-ready** pillar caps the label at "Check" regardless of average
  (don't show "Good to Go" while disconnected).
- Weights + thresholds are config, not hardcoded in the widget.

## States
| State | Render |
|---|---|
| `ready` | green radial ~full, shield check, "Good to Go" |
| `caution` | amber radial partial, "Check", offending pillars highlighted |
| `notReady` | red radial low, "Not Ready", blocking pillar called out |
| `computing` | skeleton radial until first data arrives |

## API (stateless)
```
ReadinessRadial(
  overallPercent: Int,            // precomputed upstream
  band: ReadinessBand,            // ready | caution | notReady
  pillars: List<Pillar>,          // each: name, status(ready/caution/notReady), detail
)
```
Scoring/weighting lives in the view-model; the widget only renders the number, band,
and pillar chips.

## Honesty rules
- Never imply engine/mechanical readiness.
- If a pillar's input is unavailable (e.g., weather offline), mark it **unknown** and
  exclude from the average rather than assuming "ready".

## Test cases
100% all-ready · 96% (mockup) · low-fuel caution · disconnected caps to Check ·
weather-unknown excluded · computing skeleton.
