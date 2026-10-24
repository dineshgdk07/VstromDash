# Component spec — `BikeHeroCard`

[Portrait Cockpit](../screens/cockpit-portrait.md) hero: bike identity + reconciled
status. This is where the mockup's impossible rows (Engine / Battery V / Oil Temp) get
replaced with real data.

## Layout
Bike image (per model) · model name + connection state · right-column status rows ·
footer: Odometer · Next Service · Health chip.

## Rows (reconciled — no engine sensors)
| Row | Value | Source |
|---|---|---|
| Fuel | N bars + est. range | 🟢 + 🟡 |
| ~~Engine~~ → Economy | km/L avg | 🟢 |
| ~~Battery V~~ → Link | BLE signal / link quality | 🔵 |
| ~~Oil Temp~~ → Trip | Trip A / last ride km | 🟢 |
| Odometer | km (all-time) | 🟢 |
| Next Service | km / days remaining | 🟡 |
| Health chip → "Link & Fuel OK" | composite, NOT ECU | 🟡 |

## States
| State | Render |
|---|---|
| `connected` | live values, yellow "Connected", success dot |
| `connecting` | shimmer on live rows, "Connecting…" |
| `disconnected` | last-known values + "last seen 2h ago", muted, Find-Bike CTA |
| `lowFuel` | fuel row amber (≤1 bar) |
| `serviceDue`/`overdue` | service row amber/red |

## API (stateless)
```
BikeHeroCard(
  model: String,               // Device Info; default "V-Strom 250 SX"
  imageAsset: BikeImage,
  connection: ConnectionState, // connected/connecting/disconnected(+lastSeen)
  fuelBars: Int?, rangeKmEst: Int?,
  economyKml: Float?,
  linkQuality: LinkQuality,    // strong/ok/weak/none
  tripKm: Float?,
  odometerKm: Int?,
  nextServiceKm: Int?, nextServiceDays: Int?,
)
```
All values nullable → render "—" (never fabricate). No field may imply an engine sensor.

## Test cases
connected full · disconnected last-seen · lowFuel · serviceOverdue · null/unknown values ·
long model string · missing bike image.
