# Data availability — the hard constraint

Reconciles the two high-fidelity mockups (portrait Cockpit + landscape Rider HUD)
against what the **V-Strom 250 SX actually exposes over BLE**. Every widget in the
screen/component docs is tagged with a real data source. Nothing on screen may imply
a sensor the bike does not transmit.

> Canonical protocol reference: committed spec **§5 Motorcycle Capability Map** in
> `../superpowers/specs/2026-07-12-bumblebee-dash-design.md`; richer derived/implicit
> signals in `../superpowers/specs/2026-07-28-ride-insights-catalog-design.md` Part A.
> The open items below mirror that spec's **§9 first-bike verification checklist**.

## From the bike — `a537` telemetry frame, ~once every 5 seconds
- Speed (km/h) · Odometer (km) · Trip A · Trip B · Fuel level (6 bars) · Fuel economy (≈ km/L)
- Once at connect (Device Info): model, serial, firmware, manufacturer

## From the phone
- GPS: location, **speed (smooth 1 Hz)**, heading/compass · Time & date · Phone battery
- Weather (API + GPS) · Connectivity (BLE / GPS / Internet) · Notifications (call / SMS)
- Ride logging (GPS track + logged telemetry samples)

## Computed / estimated (label as "est.")
- Range (fuel model × km/L) · Avg speed & ride duration · Service-due countdown
  (odo + user interval) · Ride Readiness score

## NOT OBTAINABLE on this bike (must not appear as live gauges)
- RPM · Gear · Engine/coolant temp · Oil temp · Battery voltage · Throttle ·
  Fault/DTC codes · Headlight state

## Speed sourcing rule (applies to every speedometer)
- **Primary: phone GPS speed** (1 Hz, smooth) drives all needles/readouts.
- **Fallback: bike `a537` speed** (5 s, stepped) when GPS is unavailable — show a
  subtle "GPS" vs "BLE" source dot so a stepped needle is explained, not a bug.

## Open verification (confirm on first real pair with the V-Strom)
- Device Info model/firmware string → confirms which telemetry decode branch applies
- Fuel-economy scaling (Gixxer uses `byte25 / 2`) matches cluster's avg km/L
- Trip A/B encoding matches cluster readout
