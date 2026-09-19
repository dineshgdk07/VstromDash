# Forge Landscape HUD Visual Pass (Plan 9 of v1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans.

**Goal:** Landscape Rider HUD matches the cluster mock layout (status, side cards, analog 0–160 speedo, action bar) with GPS speed, fuel, economy, heading — never RPM/gear/temp/voltage.

**Architecture:** Restyle `SpeedDial` (ticks/labels), compose `RiderHudScreen` as three columns + bottom actions. Reuse Cockpit status bar and readiness scoring.

**Spec:** `screens/horizontal-home-mock.png`, `docs/design/screens/rider-hud-landscape.md`.

## Constraints

- Honest "—" for weather/service/today. Phone battery + BLE instead of 12.6 V.
- assembleDebug, commit `feat(ui): restyle landscape Rider HUD to Forge cluster layout`
