# bumblebee-dash — Design Specification

**Status:** Living document · **Created:** 2026-07-12 · **Working name:** bumblebee-dash (final name TBD)
**Target hardware:** Suzuki V-Strom 250 SX (2024) · **Platform:** Android (native)

This spec consolidates the prior planning conversations (`bumblebee-dash-plan-discussion.md`,
`cursor_suzuki_ride_connect_issues.md`) into one source of truth. It is the blueprint every
future work session and implementation plan should build from. Where the two source documents
disagreed, the decisions recorded here win.

**UI design system (added 2026-07-28):** the screen-level and component-level design specs
(the deliverable proposed in `DESIGN-NOTES-cockpit-mocks.md`) live in **`docs/design/`** —
data-availability reconciliation, portrait Cockpit + landscape Rider HUD screens, and Forge UI
component specs (`SpeedDial`, `ReadinessRadial`, `TurnByTurnOverlay`, …). That folder is
subordinate to this spec; conflicts resolve in this spec's favor unless a newer dated decision
(e.g. D-017) supersedes it.

---

## 1. Product Vision

A native-Android companion app for the **Suzuki V-Strom 250 SX** that replaces the official
**Suzuki Ride Connect** app. It mirrors **Google Maps** turn-by-turn navigation to the bike's
TFT cluster, presents a premium at-a-glance dashboard, and makes fuller use of the data the bike
exposes over BLE — all with the reliability and polish the stock app lacks.

**Mission:** Combine navigation, motorcycle connectivity, ride insights, and vehicle information
into one seamless, dependable experience — eliminating the frustrations of the official app.

**North Star:** *Every new feature must help the rider explore with confidence.* If a proposed
feature does not serve this, question whether it belongs.

### Problem statement (why this exists)
The official Suzuki Ride Connect app:
- Uses **Mappls** instead of Google Maps — less accurate, less familiar navigation.
- Has an outdated, poorly organized UI; shows duplicate screens (a bug).
- Re-prompts for battery-optimization exclusion on every launch even when already granted.
- Under-uses the bike's available BLE data and visualizes it weakly.
- Feels slow and dated.

### Target audience
- **Primary (v1):** the author's own Suzuki V-Strom 250 SX.
- **Future:** any V-Strom 250 SX owner, then other Suzuki motorcycles on the same BLE stack.

### What this product will never become
Social media · cartoonish/playful · cluttered with unnecessary features · ad-filled ·
distracting while riding.

---

## 2. Brand Identity — "Forge UI"

A Bumblebee-inspired design **language** (not fan art). Three influences, in priority order:
adventure-motorcycle TFT displays → industrial sci-fi interfaces → Bumblebee's mechanical
styling → premium automotive dashboards.

**Personality (five traits guiding every decision):** Adventurous · Premium · Sporty ·
Confident · Rugged.

**Design philosophy — "Engineered for Adventure":** every screen should feel like it belongs on
the motorcycle — a premium digital instrument cluster, not a typical smartphone app. The rider
should feel the app was *engineered, not decorated*, and that the motorcycle is the hero.

### Color palette (dark-first — D-001)
| Token | Hex | Use |
|-------|-----|-----|
| Matte Black | `#121212` | Primary background (engine/asphalt) |
| Graphite | `#2B2B2B` | Cards, containers, bottom sheets |
| Suzuki Yellow | `#FFD100` | **Accent only** — active nav, connected status, primary actions, current ride |
| White | `#F5F5F5` | Primary text |
| Electric Green | (success) | BLE connected, bike healthy |
| Amber | (warning) | Low fuel, service soon |
| Red | (error) | Critical faults only |

Yellow signals **action and focus** — never a background wash.

### Other brand rules
- **Shape:** 8–12px radius, strong straight edges, large gloved-friendly targets, bold type.
- **Typography:** clean geometric, technical, highly legible outdoors/at a glance (instrument-
  cluster feel, not lifestyle app). Final family TBD.
- **Motion:** mechanical — panels slide into place, connection pulses like "systems powering on,"
  initialization sequences instead of generic spinners.
- **Icons:** dashboard indicators / engineering drawings, not illustrations.
- **Sound (optional):** subtle relay clicks, soft confirmation chirps; nothing playful.
- **Material feel:** matte metal, powder-coated steel, brushed aluminum, precision-machined.
- **Companion voice (D-007):** communicates like a trusted riding companion — confident,
  helpful, technical, concise; never judgmental.

---

## 3. Product Principles

1. **Rider First** — never interrupt the rider unnecessarily.
2. **One Glance (3-Second Rule)** — key info understandable in under two seconds.
3. **Adventure Ready** — useful for daily commute *and* long-distance touring.
4. **Confidence** — the rider always knows where they are, where the bike is, how it's doing,
   and what comes next.
5. **Mechanical Elegance** — engineered over decorative.
6. **Premium Simplicity** — powerful without becoming complicated (Progressive Disclosure).

Every screen must also satisfy: understandable in <2s · usable one-handed beside the bike ·
readable in bright sunlight · no information without purpose · every element answers "why am I
here?" · the motorcycle is the hero.

---

## 4. Information Architecture — The Five Pillars

Stable bottom navigation (D-005/D-006: floating Cockpit identity with an optional classic bottom
bar for accessibility). The **navigation stays put**; the **Cockpit content adapts** to the
rider's state (before/during/after ride).

| Pillar | Question it answers | v1 contents |
|--------|---------------------|-------------|
| **Cockpit** (D-004 — "Cockpit" not "Home") | "What's happening right now?" | Ride Readiness, connection, weather, fuel, odometer, today's riding, alerts, quick-start nav |
| **Journey** | "Where am I going?" | Google Maps, search, saved destinations, route preview, fuel stops, weather-along-route |
| **Ride** | "What's happening during my ride?" | Active ride only: speed, distance, live stats, recording controls |
| **Explorer** | "Where have I been?" | Ride list + basic per-ride summary (rich memories/analytics = later) |
| **Garage** | "How is my motorcycle?" | Bike profile, pairing, documents, settings, permissions |

**Dynamic Cockpit hero (D-011):** the hero zone (~40% of the Cockpit) transforms by context —
**Ride Readiness** before a ride, **Navigation** during, **Journey Summary** after — while the
surrounding layout and navigation stay stable.

### Signature concepts (locked; depth phased per §6)
- **Ride Readiness Score (D-008)** — answers "Am I good to go?" at a glance (e.g. `96% · Ready`).
  In v1 this is a computed score on the Cockpit; the tap-through **detailed readiness screen**
  (pre-flight-checklist style) is v1.5.
- **Ride Briefing (D-009)** — a concise pre-ride summary.
- **Digital Twin** — the Cockpit is motorcycle-centric; the bike is visually the hero.
- **Ride → Journey data model** — a **Ride** is one engine/BLE session; a **Journey** is a
  collection of rides. **Automatic journey detection** and auto-naming are v1.5.

---

## 5. Motorcycle Capability Map (BLE protocol)

Source: REDLINE reverse-engineering (`suzuki-connect-re`), reference bike 2023 Gixxer SF 150.
The V-Strom 250 SX (Gixxer 250 platform) is expected ~90% compatible; the remaining ~10% is
**verified on first real-bike connect** (see §9).

**Transport:** one BLE channel. Service `0xFFF0`; **write** `0xFFF1` (phone→bike); **notify**
`0xFFF2` (bike→phone). Every message is exactly **30 bytes**: `0xA5` header + type + payload +
checksum + `0x7F`. No encryption. The bike sends nothing until the phone writes an **identity
frame (a536)** on connect, and the phone must send a **heartbeat (a533) ~1 Hz** to keep the link
alive. No SIM/cloud on the bike — all cluster data flows through the phone.

### Data received FROM the bike — `a537` telemetry (~every 5s, the only frame the bike sends)
Speed (km/h) · Odometer (km) · Trip A · Trip B · Fuel level (1–6 bars) · Fuel economy
(≈ `byte25 / 2` km/L on Gixxer family — **verify on V-Strom**).
**Not available over BLE:** GPS/location, RPM, throttle, coolant, tyre pressure, map tiles →
these come from the **phone** or are out of scope.

### Data pushed TO the cluster (phone→bike)
| Frame | Rate | Purpose |
|-------|------|---------|
| `a531` Navigation | ~2–3/s during nav | Turn arrow (~55 maneuver types), distance-to-turn, ETA, total remaining, status byte. Reused for idle clock/weather when not navigating. |
| `a533` Heartbeat | ~1 Hz continuous | Phone battery, charging, **cell signal** (bad signal hides nav arrows), time, SMS/call pending, weather code, temperature. |
| `a536` Identity | once on connect (within ~30s) | Rider name on cluster; fresh vs reconnect flag. |
| `a532` Incoming call · `a534` Missed call · `a535` SMS/WhatsApp | event-driven | Caller/sender + counts on cluster. |

**Hard limits to respect in UI:** cluster has fixed text slots only (no free text, max widths) ·
nav arrows require good phone signal (status `'1'`) · "where is my bike" = last phone GPS on
disconnect (no bike GPS) · use `byte25/2` fuel-economy path for this platform.

---

## 6. v1 Scope (Lean Core)

**In v1:**
- **Pairing & connection:** scan, save bike MAC, identity handshake (a536), telemetry read
  (a537), 1 Hz heartbeat (a533), auto-reconnect, foreground service for a stable link while
  riding.
- **Cockpit:** Ride Readiness score, connection state, fuel, odometer, today's distance, weather;
  dynamic hero.
- **Journey:** Google Maps → cluster nav (a531) re-enabled and working; search / saved
  destinations / route preview.
- **Ride:** record a session (moving vs stopped time, live speed/distance), post-ride summary.
- **Landscape "Rider HUD" (D-017):** handlebar-mount cluster mode — GPS `SpeedDial`, fuel arc,
  economy arc, compass heading, turn-by-turn overlay (`docs/design/screens/rider-hud-landscape.md`).
- **Explorer:** ride list + basic per-ride summary.
- **Garage:** bike profile, pairing management, settings, **one-time** progressive permissions
  (no repeated battery-exclusion nagging — a direct fix for a stock-app pain point).
- **Call/SMS → cluster** (a532/a534/a535) via notification listener.
- **Demo mode:** synthetic telemetry to build/test UI without the bike.

**Explicitly NOT in v1 (→ roadmap):** detailed Ride Readiness screen, automatic journey
detection & auto-naming, stats/analytics (histograms, heatmaps, PBs), ride memories
(photos/voice notes), service/maintenance tracking, SOS/crash detection, fuel-fill log,
customization/themes, idle clock/weather-on-cluster polish, backend/cloud sync, multi-bike, iOS,
Wear OS, Android Auto.

### Roadmap
| Release | Focus |
|---------|-------|
| **v1.0** | Lean core above (pairing, Cockpit, nav→cluster, ride recording, ride list, Garage, landscape Rider HUD) |
| **v1.5** | Readiness deep-dive screen, journey auto-detect + naming, richer stats, service reminders, fuel-fill log |
| **v2.0** | Ride memories, SOS/crash detection, customization, idle cluster display polish |
| **v3.0** | Backend + cloud sync, web dashboard, route sharing; possibly Wear OS / multi-bike |

---

## 7. Technical Architecture

**Stack:** Native **Kotlin + Jetpack Compose** (decided 2026-07-12). Reuse REDLINE's proven
protocol/BLE/nav code almost verbatim; build all UI, data, and features fresh. Rationale: the
hard parts (NotificationListenerService, foreground BLE service, exact 30-byte frame timing,
boot/DND hooks, Android 14+ background reliability) are native-first, the cluster is Android-only,
and the reference implementation is already Kotlin — a Flutter bridge would add latency and a
large native layer for no benefit.

### Module layout (Gradle multi-module)
```
app/                 Compose UI, navigation, DI wiring (Hilt)
  ui/{cockpit, journey, ride, explorer, garage, theme(ForgeUI)}
core/
  protocol/          PORT from REDLINE as-is: 30-byte frames, 7 message types, checksums
  ble/               PORT: GATT client (0xFFF0), FrameWriter, HeartbeatLoop, foreground service
  nav/               PORT + re-enable: GoogleMapsParser (notification listener), ManeuverMap
data/
  local/             Room: rides, bike profile, settings
  remote/            empty stub — backend later
feature/*            repositories behind interfaces (RideRepository, BikeRepository, …)
```

**Design-for-isolation rules:**
- `core/protocol`, `core/ble`, `core/nav` are ported once and isolated — no UI/storage
  dependencies, so we never re-touch the reverse-engineered layer.
- Repositories sit behind interfaces (e.g. `RideRepository` with a `syncRides()` no-op in v1) so
  a backend is a drop-in later without touching features or UI.
- The UI layer is entirely ours (Forge UI), not REDLINE's design system.
- Data models carry future-sync fields (`remoteId`, `syncedAt`) from day one even though v1 is
  local-only.

### What to port vs. rewrite from REDLINE
| Port (near as-is) | Rewrite (ours) |
|-------------------|----------------|
| `protocol/` (all frame types) + its ~205 tests | All `ui/` screens & Forge UI design system |
| `ble/` GATT, FrameWriter, HeartbeatLoop | DI (Hilt) + repository layer (replaces REDLINE's `AppGraph` singletons) |
| `nav/` GoogleMapsParser, ManeuverMap (**re-enable** — disabled in REDLINE) | Onboarding, navigation structure, settings |
| `tools/protocol.py` as reference/oracle | Room schema with sync-ready fields |

### Testing strategy
- **Unit:** port REDLINE's protocol decode/encode tests; add V-Strom-specific fuel-economy case.
- **Screenshot:** Roborazzi for Cockpit and key screens (JDK-only, no device).
- **Demo mode:** synthetic a537 telemetry feed so Cockpit/Ride/Explorer populate without a bike.
- **On-bike verification:** the §9 checklist gates protocol-decode correctness for the V-Strom.

---

## 8. Design Decision Log

| ID | Decision | Why |
|----|----------|-----|
| D-001 | Dark-first UI | Outdoor visibility; matches TFT dashboards |
| D-002 | Bumblebee-inspired "Forge UI" language | Rugged, premium identity without copying the franchise |
| D-003 | Google Maps for navigation | Familiar, accurate, trusted |
| D-004 | "Cockpit" instead of "Home" | Motorcycle-first philosophy |
| D-005 | Floating Cockpit navigation identity | Unique identity, bike at the center |
| D-006 | Optional classic bottom nav | Accessibility for conventional users |
| D-007 | Companion tone of voice | App speaks like a trusted riding companion |
| D-008 | Ride Readiness Score | Answers "Am I good to go?" at a glance |
| D-009 | Ride Briefing | Concise pre-ride summary |
| D-010 | North Star: explore with confidence | Feature-gating principle |
| D-011 | Dynamic Cockpit hero (~40%) | Right info for the moment; layout stays stable |
| D-012 | Ride→Journey model + auto-detection | Rich engineering model, simple to the user |
| **D-013** | **Native Kotlin + Compose** (not Flutter) | Native-first system integration; reuse REDLINE Kotlin protocol code; Android-only cluster |
| **D-014** | **Spec-first, then scaffold** | One source of truth before code |
| **D-015** | **v1 = Lean Core** | Ship nav + dashboard reliably first; grow from there |
| **D-016** | **Multi-module + repository interfaces** | Isolate ported protocol layer; make backend a drop-in |
| **D-017** | **Landscape "Rider HUD" reinstated — in v1** (2026-07-28; reverses the 2026-07-14 portrait-only call) | Owner decision after new mock review; redesigned around real data — GPS `SpeedDial` replaces the impossible RPM tach (no gear/coolant/voltage). Spec: `docs/design/screens/rider-hud-landscape.md`. Release placement **confirmed v1** by the owner (2026-07-28) |

---

## 9. First Real-Bike Verification Checklist

Confirms the ~10% that may differ from the Gixxer reference. Run with ignition ON and Suzuki Ride
Connect **force-stopped** (so the two apps don't fight for BLE).
1. Pair and connect; send identity (a536); confirm telemetry starts.
2. Verify `a537` decode: speed, odometer, fuel bars, Trip A/B match the cluster.
3. Confirm **fuel economy** formula (`byte25/2` vs cluster avg km/L).
4. Start Google Maps nav → confirm correct turn arrow on the cluster (maneuver map / default
   branch).
5. Test incoming call → cluster popup (a532).
6. Log **Device Info** (model string, serial, firmware) to identify the decode branch.

---

## 10. Open Questions
- Final product name (bumblebee-dash is the working title) and logo concept.
- Final typography family and icon set.
- Sound design.
- V-Strom telemetry deltas from the Gixxer reference (resolved by §9).
- Gestures on the Cockpit beyond tap/swipe.
- Future: Wear OS, Android Auto, backend stack (Supabase/Firebase vs Ktor/Go).

## 11. Future Ideas (not committed)
AI ride insights · group rides · dashcam integration · smartwatch support · cloud backup ·
GPX route sharing · riding challenges (only if aligned with the philosophy).
