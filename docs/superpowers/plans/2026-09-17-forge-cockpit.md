# Portrait Forge UI Cockpit Implementation Plan (Plan 5 of v1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder Cockpit list with a Forge UI portrait layout: status chips, Ride Readiness score, bike hero card, metric tiles, warnings, while keeping Scan/Pair/Connect and demo ride.

**Architecture:** Pure `ReadinessCalculator` (no Compose) computes D-008 score from known inputs. Stateless Compose widgets in `ui/cockpit/components/` take precomputed state. `CockpitViewModel` maps BLE/demo telemetry + connection into that state. Bottom nav stays the Foundation `NavigationBar` (CockpitCore is a later visual pass).

**Tech Stack:** Existing Compose Material3 + Forge tokens (`ForgeBlack`, `SuzukiYellow`, `SuccessGreen`, `WarningAmber`, `ErrorRed`). JUnit4 for scoring tests. No Roborazzi in this plan. No weather API, GPS, or service-interval storage yet — those pillars are `unknown` and excluded from the average.

**Spec:** `docs/design/screens/cockpit-portrait.md`, `docs/design/components/readiness-radial.md`, `docs/design/components/bike-hero-card.md`, `docs/design/components/warnings-card.md`. Parent: `docs/superpowers/specs/2026-07-12-bumblebee-dash-design.md` D-001/D-008/D-017.

## Global Constraints

- Branch `capture-pipeline`. JDK 17, Gradle 8.12 fallback as Plan 4.
- Never show RPM/gear/engine temp/voltage as live data.
- Readiness is not mechanical health. Health chip copy is **"Link & Fuel OK"** or honest warnings — never "Engine Normal".
- Unknown pillars excluded from average. Any **not-ready** pillar caps band at `caution` ("Check"), never "Good to Go".
- Keep BLE scan/pair/connect and demo ride reachable on Cockpit.
- One commit per task.

---

### Task 1: `ReadinessCalculator` (pure, tested)

**Files:**
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/cockpit/Readiness.kt`
- Create: `app/src/test/kotlin/dev/gdk/bumblebee/ui/cockpit/ReadinessCalculatorTest.kt`
- Modify: `app/build.gradle.kts` — `testImplementation(libs.junit)`

**Produces:**
```
enum class PillarStatus { READY, CAUTION, NOT_READY, UNKNOWN }
enum class ReadinessBand { READY, CAUTION, NOT_READY, COMPUTING }
data class ReadinessPillar(val name: String, val status: PillarStatus, val detail: String)
data class ReadinessScore(val percent: Int, val band: ReadinessBand, val label: String, val pillars: List<ReadinessPillar>)
object ReadinessCalculator {
  fun score(fuelBars: Int?, bleReady: Boolean, phoneBatteryPercent: Int?): ReadinessScore
}
```

v1 pillars in this function: Fuel, Link, Service/Docs (always UNKNOWN until Garage exists). Weather and Route UNKNOWN. Fuel: null→UNKNOWN, ≥3 READY, 2 CAUTION, ≤1 NOT_READY. Link: bleReady READY else NOT_READY; if bleReady && battery < 15 → CAUTION. Scoring: equal weights over non-UNKNOWN; empty set → COMPUTING, percent 0, label "…". Bands: ≥85 "Good to Go", 50–84 "Check", <50 "Not Ready". Cap: if any NOT_READY, band cannot be READY (label at most "Check").

Tests: all-ready 100% Good to Go; fuel 2 → caution; disconnected → not Good to Go; fuel null excluded; no data → computing.

- [x] Tests, implement, `./gradlew :app:testDebugUnitTest`, commit `feat(ui): add Ride Readiness scoring from fuel and BLE link`

---

### Task 2: Stateless Forge cards

**Files:**
- Create: `ui/cockpit/components/CockpitStatusBar.kt` — BLE/GPS/Net dots (GPS/Net greyed `off` this plan), clock optional skip, connection label
- Create: `ui/cockpit/components/ReadinessRadial.kt`
- Create: `ui/cockpit/components/BikeHeroCard.kt`
- Create: `ui/cockpit/components/MetricCard.kt`
- Create: `ui/cockpit/components/WarningsCard.kt`

No unit tests for Compose. `assembleDebug` is the gate.

ReadinessRadial: percent + band color (green/amber/red/graphite), label, four/five pillar chips.
BikeHeroCard: model `"V-Strom 250 SX"`, connection, fuel bars, economy "—", link text, trip "—", odo, service "—", chip "Link & Fuel OK" or "Check link".
MetricCard: label, value, sub.
WarningsCard: empty → "All clear · app & connection" (not engine). Else list titles.

- [x] Implement, assembleDebug, commit `feat(ui): add Forge Cockpit cards (readiness, hero, metrics, warnings)`

---

### Task 3: Wire CockpitScreen

**Files:** Modify `CockpitScreen.kt`, `CockpitViewModel.kt` (add `readiness: ReadinessScore`, `warnings`, `economyKml`, `tripAKm` from telemetry).

Layout: StatusBar → ReadinessRadial → BikeHeroCard → 2-column MetricCards (Fuel, Odo, Speed, Economy) → WarningsCard → Link controls (Scan/Connect/Disconnect/device list/Demo) at bottom.

Derive warnings: BLE not Ready → critical "BLE disconnected"; fuel ≤1 → "Low fuel"; else empty.

- [x] assembleDebug, commit `feat(ui): wire portrait Forge Cockpit to live and demo telemetry`
