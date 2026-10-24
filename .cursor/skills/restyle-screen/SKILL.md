---
name: restyle-screen
description: Restyle one Bumblebee Dash Compose screen to the Forge Block design system (docs/design/DESIGN_SYSTEM.md). Use when asked to restyle, redesign, reskin or migrate a screen such as Cockpit, Ride, Journey, Garage, Fuel log, Explorer, the nav shell, or dialogs/empty states. Not for the landscape HUD or share cards.
---

# Restyle one screen to Forge Block

Do exactly **one** screen per run. If asked for several, do the first and stop.

## 0. Preconditions (stop if any fail)

- `ui/theme/Color.kt` contains the Forge Block tokens (`Canvas`, `Surface`, `Lime`, `BlockYellow` …). If they're missing, phase 1 isn't done: tell the user and stop.
- `ui/components/` exists with the §4 kit. If it's missing entirely, phase 2 isn't done: tell the user and stop. (One or two missing components is fine; step 3 handles that.)
- The target isn't under `ui/hud/` or `ui/sharecard/`. Those are excluded by spec §7: refuse and explain why.
- `./gradlew :app:assembleDebug` passes **before** you start. If it doesn't, report the failure and stop. Don't fix unrelated breakage silently.

## 1. Gather context

1. Find the screen's row in `docs/design/DESIGN_SYSTEM.md` §5. Note its file, reference image(s) and direction.
2. Open the reference image(s) in `design/reference/matiks/` and look at them.
3. Read the screen file, its ViewModel and its private `components/` folder. Read the screen doc in `docs/design/screens/` if one exists.
4. List every piece of data the screen shows and where it comes from. Check each against `docs/design/data-availability.md`. You restyle how data is shown; you never add, remove or invent data sources.

## 2. Write a layout plan (show it before editing)

Write a short plan for the user:

- A top-to-bottom section list, mapping each section to a §4 component and naming the block colour it uses (semantic per §2.1, max 4 blocks).
- Which text is the screen's single Anton shout.
- Components it needs that don't exist yet in `ui/components/`.
- The files you'll touch. That should be only this screen's file, its private `components/`, new files in `ui/components/`, and new tokens in `ui/theme/` if one is truly missing.
- Each `HudColors.kt` colour the screen uses, and the `Color.kt` token that replaces it (from the mapping table in spec §8, chosen by what the colour does on that element).

If you're in Plan mode, stop here for approval.

## 3. Missing components first

Build each missing component in `ui/components/` exactly as in spec §4, with a `@Preview` (on `Canvas` background) showing every state. Don't put screen-specific logic in shared components. Build once components are done.

## 4. Restyle the screen

- Rebuild the layout from components and tokens only. No `Color(0x…)`, `RoundedCornerShape(n.dp)`, `fontSize =` or `Modifier.shadow` in the screen file.
- Replace every `HudColors.kt` colour with its `Color.kt` token. Use `MaterialTheme.typography` roles plus `NumberLarge` / `NumberMedium`, and `Shape*` tokens.
- Keep all ViewModel wiring, state, callbacks, navigation and behaviour identical. This is a visual change. Don't rename public composables that other files call.
- Replace any `AlertDialog` with `ChunkyDialog`, and empty states with `EmptyState`, using rider-flavoured copy.
- Delete private components that are now dead, and only those.
- Add or keep `@Preview`s for the screen with representative fake state (connected / disconnected / empty).

## 5. Verify

1. `./gradlew :app:assembleDebug` passes, and `./gradlew :app:testDebugUnitTest` passes.
2. Run the **migration check** from spec §8 on the screen's files; it must print nothing.
3. Run this grep on the screen's files; it should print nothing:
   `grep -nE 'Color\(0x|RoundedCornerShape\(|fontSize *=|AlertDialog|Modifier\.shadow' <files>`
4. If a device or emulator is connected (`adb devices`), install with `./gradlew :app:installDebug`, open the screen, and save a screenshot:
   `adb exec-out screencap -p > screens/<screen>-v2-actual.png`
   If none is connected, say so and ask the user to capture it. Don't claim visual verification you didn't do.
5. Put the screenshot next to the reference image(s) and compare canvas, block colours, radii, type roles, spacing and chunky depth. Fix clear mismatches, then re-run steps 1–4.

## 6. Report

End with:

- Files changed, plus new components added.
- The spec §9 checklist, with each item marked ✅ / ❌ and a one-line reason for each ❌.
- Remaining visual differences from the reference that you chose not to fix, and why.
- Deviations from the spec (if any) that the user should decide on.
- The next screen in §5 order.

Don't commit unless the user asks.
