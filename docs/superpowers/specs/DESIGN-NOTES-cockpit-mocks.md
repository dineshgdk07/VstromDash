# Design Notes — Cockpit Mocks (hand-off for the design-doc session)

**Created:** 2026-07-14 · **Status:** input for the next brainstorm (UI/UX design doc), not yet decided.

Two mocks were added in `screens/`:
- `vertical-home-mock.png` — portrait **Cockpit home**.
- `horizontal-home-mock.png` — landscape **instrument-cluster / ride mode** (Hayabusa-style tach).

These are the first concrete visuals. They refine (and in places conflict with) the committed spec
`2026-07-12-bumblebee-dash-design.md`. Reconcile them in the design doc before any UI is built.
The source discussion `bumblebee-dash-plan-discussion.md` was extended (updated 2026-07-13) with a
Cockpit design sprint that produced these mocks — read the portion after the earlier "Cockpit V0"
section fresh.

## What the mocks confirm (fold into the design system)
- Dark TFT aesthetic, Suzuki-yellow accent, matte black — matches Forge UI (D-002).
- **Cockpit home layout (portrait):** status bar (BLE/GPS/Internet dots, weather, battery, time) →
  Ride Readiness hero (arc gauge + shield + Fuel/Weather/Bike/Nav checks, "96% Good to Go") →
  bike hero card (photo, "V-Strom 250 SX / Connected", right rail of stats, odo/next-service/health)
  → data-tile grid (Fuel, Today's Ride w/ speed graph, Weather, Odometer, Warnings, Service Due) →
  quick actions (Navigate, Find Bike, Share, Explore) → floating **Cockpit Core** center dial
  ("COCKPIT — Tap to Open", long-press for quick menu) flanked by Journey/Garage/Ride/Explorer.
- The bottom nav is a **custom floating Cockpit Core**, NOT a Material NavigationBar (confirms D-005).
  → The Foundation's current `BumblebeeApp` NavigationBar is a placeholder to be replaced.
- **Landscape "cluster mode"** is a new, first-class concept: full instrument cluster with central
  tach/speedo, fuel + temp gauges, gear, ODO, headlight/indicator row. Implies a mounted/ride mode.

## CRITICAL open decision — data availability mismatch
The bike's BLE `a537` telemetry gives ONLY: speed, odometer, trip A/B, fuel bars, fuel economy
(see spec §5; V-Strom = Gixxer 250 platform, verify on first connect). The mocks show data the bike
does NOT expose over BLE:
- RPM / tachometer (landscape center) — NOT available
- Gear position ("N") — NOT available
- Coolant / oil temp — NOT available
- Battery voltage ("12.6 V") — NOT available
- "Engine Normal" bike-health — nothing to derive it from

**Decide per field:** drop it · label "—/if available" · or source elsewhere. Do NOT design
high-fidelity screens around unpopulatable data. The landscape tach especially: if RPM/gear are
unavailable, landscape mode should be re-conceived around what we DO have (large speed, fuel, odo,
nav) rather than mimicking a full engine cluster.

## Other decisions the design doc must make
- Portrait vs landscape: is landscape a distinct "ride/mounted mode," and what triggers it?
- Yellow usage: mocks lean on yellow more than "restrained accent" — set the exact rule + component states.
- Component-state spec (this is also a known Foundation finding): selected nav / active states must be
  **Suzuki yellow** — the current build shows the Material default (purple) on the selected tab.
- Bike hero photo asset (licensed/owned image of the V-Strom 250 SX).
- Map the mock's fields to data sources: BLE (a537) · phone (GPS/weather/battery) · computed
  (range, readiness) · user-entered (service intervals, documents).

## Design-doc agenda (proposed) — produce `docs/superpowers/specs/2026-07-1x-ui-design-system.md`
1. Reconcile mocks vs data availability (the critical decision above) — field-by-field data-source table.
2. Screen inventory + wireframe intent per pillar (Cockpit, Journey, Ride, Explorer, Garage) + landscape mode.
3. Component library & states (cards, tiles, gauges, Ride Readiness arc, Cockpit Core, status bar, nav).
4. Design tokens beyond color: spacing scale, typography scale + font choice, elevation, motion specs.
5. Empty / loading / error / disconnected states for each surface.
6. Map the tokens/components back to the Compose theme so the build matches the doc.
