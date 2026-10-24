# Screen B — Landscape "Rider HUD" (handlebar mount, live)

Repurposed from the mockup's RPM cluster. Same layout, real feeds. The dead RPM tach
becomes a GPS speedometer; dead gauge **shapes** may remain for visual balance, but
each now carries a **real** metric.

See the [data-source legend](../README.md#data-source-legend).

## Reconciled widget map

| Widget | Was ⛔ | Now | Source |
|---|---|---|---|
| Center ring + digital number | RPM + speed | **GPS speed** (analog ring 0–160 + digital), red arc = over-speed-limit zone — see [`SpeedDial`](../components/speed-dial.md) | 🔵 |
| Left mini-arc (F/E) | fuel | **Fuel** (6 bars → F/E) | 🟢 |
| Right mini-arc (H/C) | engine temp | **Fuel economy** (km/L) | 🟢 |
| "N GEAR" slot | gear | **Compass heading** (N/NE/E…) | 🔵 |
| ODO / Trip readout | — | Odometer + Trip | 🟢 |
| Bottom-left "12.6 v" + headlight AUTO | voltage / lights | **Phone battery + BLE link** (or drop for clean bar) | 🔵 |
| Side cards (readiness, fuel, today's ride, weather, health, service, warnings) | mixed | same reconciliation as [portrait](./cockpit-portrait.md) | 🟢/🔵/🟡 |
| Center action bar: Navigate / Find Bike / Share / Explore | — | + [turn-by-turn overlay](../components/turn-by-turn-overlay.md) when navigating | 🔵 |

Design rule honored: no widget pretends to be an engine sensor (temp-arc → economy,
gear slot → heading).

## Components used
`StatusBar` · [`SpeedDial`](../components/speed-dial.md) (+`SourceDot`) · `FuelArc` ·
`EconomyArc` · `CompassHeading` · `SpeedReadout` · side cards
(Readiness/Fuel/TodaysRide/Weather/BikeHealth/Service/Warnings) · `QuickActionButton`×4 ·
[`TurnByTurnOverlay`](../components/turn-by-turn-overlay.md)
