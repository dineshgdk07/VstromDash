# Component spec — `WarningsCard`

Replaces the mockup's "Warnings — None / All Systems Normal". Honest scope: this reports
**app, phone, and connection status** — NOT bike ECU/DTC fault codes. The V-Strom exposes
no fault channel over BLE, so "All Systems Normal" must never imply engine diagnostics.

## Role
Surface actionable conditions the app can actually detect, sorted by severity. Portrait
card and landscape side card. Empty state = "All clear" scoped to app/connection.

## Warning sources (all knowable)
| Warning | Trigger | Severity | Action |
|---|---|---|---|
| BLE disconnected | link lost | critical | reconnect / pair |
| Weak BLE link | poor signal | warn | (info; may drop nav) |
| GPS lost | no fix | warn | check location / sky view |
| Low fuel | fuel ≤ 1 bar 🟢 | warn → critical at 0 | find fuel |
| Service overdue | odo/date past interval 🟡 | warn → critical | open Garage |
| Phone battery low | phone < 15% | warn | charge |
| Notification access off | permission revoked | warn | grant (nav/notifs need it) |
| Over-speed | speed > set limit 🔵 | info/warn | (transient) |

## States
| State | Render |
|---|---|
| `allClear` | success chip "All clear" (app & connection) |
| `warnings` | list sorted critical → warn → info, each with icon + message + action |

## API (stateless)
```
WarningsCard(
  warnings: List<Warning>,   // each: severity, title, detail, action?
)                            // empty list -> allClear
```
Warning derivation (thresholds, debounce) lives in the view-model.

## Honesty rules
- Scope label makes clear this is **app/connection**, not engine faults.
- Never fabricate an "engine OK" or DTC-style status.
- Debounce flapping conditions (BLE/GPS) so the list doesn't thrash.

## Test cases
allClear · single low-fuel · disconnected (critical) · multiple sorted · permission-off ·
service overdue · debounce flapping BLE.
