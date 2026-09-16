# Information architecture

The **Five Pillars** from the committed spec (§4), mapped to real data. Same five tabs;
visual order places Cockpit as the raised center dial: Journey · Garage · **[Cockpit]** ·
Ride · Explorer. (The Foundation code's `Pillar` enum order is logical, not visual.)

| Pillar | Question it answers (spec §4) | Real data it shows |
|---|---|---|
| **Cockpit** (home, D-004) | "What's happening right now?" | fuel, odo, trip, range, weather, readiness |
| **Journey** | "Where am I going?" | Google Maps → cluster turn arrows (`a531`) |
| **Garage** | "How is my motorcycle?" | model/firmware, odo, service intervals, fuel log, documents |
| **Ride** | "What's happening during my ride?" | GPS speed, live trip, GPS track → saved rides |
| **Explorer** | "Where have I been?" | trip history, distance & fuel trends, routes |

Navigation component: [`CockpitCore`](./components/README.md#4-navigation--actions) —
floating raised center Cockpit dial with long-press quick menu (D-005), with an optional
classic bottom bar for accessibility (D-006). The Cockpit's hero zone (~40%) is **dynamic**
per D-011: Readiness before a ride, Navigation during, Journey Summary after — while the
surrounding layout and navigation stay stable.

## Orientation
- **Portrait** → [Cockpit companion](./screens/cockpit-portrait.md) (stationary use).
- **Landscape** → [Rider HUD](./screens/rider-hud-landscape.md) (handlebar mount, live).
