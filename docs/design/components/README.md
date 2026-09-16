# Component inventory — Forge UI design system

Reusable building blocks for [Screen A (portrait Cockpit)](../screens/cockpit-portrait.md)
and [Screen B (landscape Rider HUD)](../screens/rider-hud-landscape.md). Every component
lists its data source using the [legend](../README.md#data-source-legend). Each
component must be **stateless-renderable** (data in as params) so it previews and
screenshot-tests without a live bike.

## 0. Foundation tokens (Forge UI — canonical values per committed spec §2 / foundation plan)

| Token group | Values |
|---|---|
| Color | `bg ForgeBlack #121212` · `surface ForgeGraphite #2B2B2B` · `text ForgeWhite #F5F5F5` · `accent SuzukiYellow #FFD100` · `success #2ECC71` · `warning #F5A623` · `error #E53935` — yellow is accent only, never a background wash (D-001/D-002) |
| Type | geometric technical sans; weights 400/600/700; tabular numerals mandatory on any ticking digit (speed, odo, trip) |
| Spacing | 4 / 8 / 12 / 16 / 24 / 32 |
| Radius | cards 8–12px, straight strong edges (mechanical, not playful) |
| Motion | mechanical feel — relay-click connect sequence, spring needle sweep, no bouncy fades |
| Elevation | flat by default; one hero element may lift per screen |

## 1. Atoms / status

| Component | Purpose | Data | States |
|---|---|---|---|
| `ConnectionDot` | BLE / GPS / Internet indicator | 🔵 | connected / connecting / off |
| `SourceDot` | shows speed source on a gauge | 🔵 | GPS (smooth) / BLE (stepped) |
| `WeatherChip` | temp + condition glyph | 🔵 | live / cached / unavailable |
| `PhoneBatteryChip` | phone battery % + charging | 🔵 | normal / low / charging |
| `ClockDate` | time + optional date | 🔵 | 12h / 24h |
| `StatusBar` | composes the above into the top bar | 🔵 | portrait / landscape variants |

## 2. Gauges

| Component | Purpose | Data | Notes / states |
|---|---|---|---|
| [`SpeedDial`](./speed-dial.md) | landscape centerpiece: analog ring (0–160) + digital + km/h | 🔵 GPS (🟢 fallback) | red arc = over-limit zone; idle / riding / over-limit; `SourceDot` inset |
| `SpeedReadout` | compact digital speed (portrait/HUD chips) | 🔵/🟢 | |
| `FuelArc` | 6-bar fuel mapped to F→E arc | 🟢 | 6→1 bars; low-fuel (≤1) warning tint |
| `FuelBar` | horizontal fuel bars + est. range label | 🟢 + 🟡 | range labeled "est." |
| `EconomyArc` | fuel economy (km/L) mini-arc | 🟢 | replaces old temp arc; no-data state when engine off |
| [`ReadinessRadial`](./readiness-radial.md) | Ride Readiness % dashed radial + shield (D-008) | 🟡 | pillars Fuel/Weather/Route/Link/Service-Docs (aligned with insights #65) |
| `CompassHeading` | GPS heading N/NE/E… (fills old gear slot) | 🔵 | blank when GPS lost |

## 3. Cards

| Component | Purpose | Data | Notes |
|---|---|---|---|
| [`BikeHeroCard`](./bike-hero-card.md) | bike image + model + connection + fuel/economy/link/trip rows + odo/service/health | 🟢/🔵/🟡 | reconciled rows (no engine temp / voltage / oil temp) |
| `MetricCard` | generic bento tile (icon, label, value, sub, chevron) | any | base for Fuel/Odo/Weather/etc. |
| `TodaysRideCard` | distance + duration + avg + sparkline | 🔵 | sparkline from ride log |
| `WeatherCard` | temp, condition, hi/lo | 🔵 | |
| `ServiceDueCard` | km + days remaining | 🟡 | overdue state |
| [`WarningsCard`](./warnings-card.md) | app/connection status (NOT ECU faults) | 🟡 | none / BLE drop / GPS lost / low fuel / service overdue / phone low |
| `RangeBar` | est. range progress from fuel + km/L | 🟡 | "est." qualifier |

## 4. Navigation & actions

| Component | Purpose | Data | Notes |
|---|---|---|---|
| `CockpitCore` | floating 5-pillar nav: Journey · Garage · [Cockpit] · Ride · Explorer | ⚫ | D-005: raised center Cockpit dial, NOT a Material `NavigationBar` (the Foundation build's bar is a placeholder); long-press quick menu; optional classic bottom bar per D-006 |
| `QuickActionButton` | Navigate / Find Bike / Share / Explore | 🔵 | Find Bike = last parked GPS |
| [`TurnByTurnOverlay`](./turn-by-turn-overlay.md) | maneuver arrow + dist + ETA (mirrors cluster) | 🔵→🟢 | drives `a531` to bike; degraded state when phone signal poor |
| [`ClusterPreview`](./cluster-preview.md) | phone mock of the bike TFT (nav / idle clock / call-SMS) | 🔵/🟢 | reuse concept from REDLINE |

## 5. Feedback & scaffolds

| Component | Purpose | Data |
|---|---|---|
| `ConnectSequence` | mechanical "machine coming alive" pair animation | 🔵 |
| `RelayToast` | subtle relay-click confirmations | ⚫ |
| `PortraitCockpitScaffold` | status bar + hero + bento grid + actions + bottom nav | — |
| `LandscapeHudScaffold` | status bar + side cards + `SpeedDial` center + action bar | — |

## Component → screen usage

- **Portrait Cockpit:** StatusBar · ReadinessRadial · BikeHeroCard · MetricCard×N · TodaysRideCard · WeatherCard · ServiceDueCard · WarningsCard · FuelBar/RangeBar · QuickActionButton×4 · CockpitCore
- **Landscape Rider HUD:** StatusBar · SpeedDial (+SourceDot) · FuelArc · EconomyArc · CompassHeading · SpeedReadout · side cards (Readiness/Fuel/TodaysRide/Weather/BikeHealth/Service/Warnings) · QuickActionButton×4 · TurnByTurnOverlay

## Build order (suggested)

1. Foundation tokens → 2. Atoms/status → 3. Gauges (SpeedDial first, it's the hero) → 4. Cards → 5. Nav/actions → 6. Scaffolds → 7. Feedback/motion.

Each component built stateless + preview/screenshot test before wiring to live BLE/GPS.
