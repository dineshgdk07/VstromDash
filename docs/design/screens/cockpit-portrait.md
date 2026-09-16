# Screen A — Portrait "Cockpit" (stationary companion)

Hero + bento cards, matte black / graphite / Suzuki-yellow accent. This is where the
mockup's impossible rows (Engine / Battery V / Oil Temp) get replaced with real data.

See the [data-source legend](../README.md#data-source-legend).

## Reconciled widget map

| Widget | Source | Notes |
|---|---|---|
| Status bar: BLE / GPS / Internet dots | 🔵 | live link state |
| Status bar: weather °C, phone battery, clock | 🔵 | |
| Ride Readiness % (Fuel/Weather/Route/Link/Service-Docs pillars, per D-008 + insights #65) | 🟡 | "Bike" pillar → **Link + Fuel + Service/Docs**, not mechanical health — see [`ReadinessRadial`](../components/readiness-radial.md) |
| Bike card — Fuel (bars) | 🟢 | |
| Bike card — ~~Engine Normal~~ → **Fuel economy (km/L)** | 🟢 | replaces impossible engine status |
| Bike card — ~~Battery 12.6 V~~ → **Link / signal quality** | 🔵 | gates whether nav reaches cluster |
| Bike card — ~~Oil Temp~~ → **Trip A / last ride** | 🟢 | |
| Bike card — Odometer · Next Service | 🟢 / 🟡 | |
| Bike card — Health chip → **"Link & Fuel OK"** | 🟡 | reworded; no ECU faults |
| Cards: Fuel + Est. Range | 🟢 / 🟡 | |
| Cards: Today's Ride (dist / time / avg) | 🔵 | ride log |
| Cards: Weather | 🔵 | |
| Cards: Odometer | 🟢 | |
| Cards: Warnings → **App/connection status** | 🟡 | see [`WarningsCard`](../components/warnings-card.md) |
| Cards: Service Due | 🟡 | |
| Actions: Navigate / Find Bike / Share / Explore | 🔵 | Find Bike = last parked phone GPS, not live bike GPS |
| Bottom nav: Journey · Garage · Cockpit · Ride · Explorer | — | see [information architecture](../information-architecture.md) |

## Components used
`StatusBar` · [`ReadinessRadial`](../components/readiness-radial.md) ·
[`BikeHeroCard`](../components/bike-hero-card.md) · `MetricCard`×N · `TodaysRideCard` ·
`WeatherCard` · `ServiceDueCard` · [`WarningsCard`](../components/warnings-card.md) ·
`FuelBar`/`RangeBar` · `QuickActionButton`×4 · `CockpitCore`
