# Forge Portrait Cockpit Visual Pass (Plan 8 of v1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans.

**Goal:** Make portrait Cockpit recognizably the Forge mock: tokens, readiness hero, bike card, bento grid, quick actions, Cockpit Core — without fake engine sensors.

**Architecture:** Theme tokens + typography. Restyle existing cockpit composables. Replace Material `NavigationBar` with `CockpitCore`. Link/scan controls collapse under a Link section so they do not own the screen.

**Tech Stack:** Compose Material3, existing Forge colors, Icons Extended, optional drawable bike hero.

**Spec:** `screens/vertical-home-mock.png`, `docs/design/screens/cockpit-portrait.md`, `docs/design/components/README.md` foundation tokens. Honest data only.

## Global Constraints

- capture-pipeline. No RPM/gear/temp/voltage. Weather/service/range show "—" until real sources exist.
- Yellow accent only. Selected nav is Suzuki yellow.
- One commit per task.

---

### Task 1: Tokens + CockpitCore

Type.kt (tabular numerals), wire Theme. `CockpitCore.kt`. `BumblebeeApp` uses it; visual order Journey · Garage · Cockpit · Ride · Explorer.

### Task 2: Restyle Cockpit surfaces

StatusBar (clock, battery). Readiness left % / right dashed arc + shield. BikeHero layout + image if present. MetricCard bento + icons. QuickAction row. Collapse BLE scan/demo under Link.

- [x] Type.kt + CockpitCore + restyle Cockpit. assembleDebug + install.
