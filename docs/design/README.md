# Bumblebee Dash — UI Design System ("Forge UI")

Data-grounded UI design system for the V-Strom 250 SX companion app. Every screen and
component here is reconciled against what the bike **actually** exposes over BLE — no
widget may imply a sensor the bike does not transmit.

## Where this fits

- **Product source of truth:** [`../superpowers/specs/2026-07-12-bumblebee-dash-design.md`](../superpowers/specs/2026-07-12-bumblebee-dash-design.md)
  (vision, brand "Forge UI", IA/five pillars, BLE capability map §5, roadmap, decision log D-001…).
  Where anything here disagrees with that spec, the spec wins unless a newer dated decision
  supersedes it.
- **This folder** is the **UI design-system layer** that spec calls for (the
  `2026-07-1x-ui-design-system` deliverable in
  [`../superpowers/specs/DESIGN-NOTES-cockpit-mocks.md`](../superpowers/specs/DESIGN-NOTES-cockpit-mocks.md)):
  screen reconciliation + component specs.
- **Ride Engine data** it can draw on: [`../superpowers/specs/2026-07-28-ride-insights-catalog-design.md`](../superpowers/specs/2026-07-28-ride-insights-catalog-design.md).
- **Conventions:** design language **Forge UI**; app package `dev.gdk.bumblebee`; native
  Kotlin + Jetpack Compose (D-013). The `bumblee_dash_plan.md` at the repo root is the
  older brand/discussion narrative.

## Landscape status (updated 2026-07-28)

The earlier "v1 is portrait-only, landscape parked" call (2026-07-14) is **reversed**: the
landscape **Rider HUD is reinstated as a first-class v1 screen** (D-017, confirmed
2026-07-28) — redesigned as a GPS-speed HUD, not a fake engine cluster. See
[Rider HUD](./screens/rider-hud-landscape.md) and [`SpeedDial`](./components/speed-dial.md).

## Data-source legend

- 🟢 real bike (`a537` telemetry, ~every 5 s)
- 🔵 real phone (GPS, clock, weather, battery, notifications)
- 🟡 estimated / computed (label "est." in UI)
- ⚫ static / config
- ⛔ impossible on this bike (removed or repurposed)

## Index

- [Data availability (the hard constraint)](./data-availability.md)
- [Information architecture](./information-architecture.md)
- Screens
  - [Cockpit — portrait](./screens/cockpit-portrait.md)
  - [Rider HUD — landscape](./screens/rider-hud-landscape.md)
- [Component inventory](./components/README.md)
  - [`SpeedDial`](./components/speed-dial.md) — hero speedometer (repurposed RPM dial)
  - [`TurnByTurnOverlay`](./components/turn-by-turn-overlay.md) — the only writer to the bike (`a531`)
  - [`BikeHeroCard`](./components/bike-hero-card.md) — portrait bike identity + status
  - [`ReadinessRadial`](./components/readiness-radial.md) — Ride Readiness score
  - [`WarningsCard`](./components/warnings-card.md) — app/connection status (not ECU faults)
  - [`ClusterPreview`](./components/cluster-preview.md) — phone mock of the bike TFT

## Golden rule

If a value can't be sourced 🟢/🔵/🟡, it does not appear as live data. RPM, gear,
engine/oil temp, battery voltage, throttle, and fault codes are **not transmitted**
by this bike and must never be shown as live gauges.
