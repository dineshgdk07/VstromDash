# Forge Block — visual design system (v2)

The visual layer for Bumblebee Dash's portrait screens: tokens, components and
per-screen direction. It sits on top of the Forge UI system in [`README.md`](./README.md).
That README still owns **what data may appear**. This file owns **how it looks**.

- **Reference images:** [`design/reference/matiks/`](../../design/reference/matiks/), screenshots of
  the Matiks app (dark, colour-blocked, chunky). We borrow its *visual language*, not its content,
  icons, illustrations or copy.
- **Golden rule still applies:** if a value can't be sourced 🟢/🔵/🟡 (see
  [`data-availability.md`](./data-availability.md)), it doesn't appear as live data, however good it
  would look in a tile.
- **Code:** tokens live in `app/src/main/kotlin/dev/gdk/bumblebee/ui/theme/`. Shared components live
  in `app/src/main/kotlin/dev/gdk/bumblebee/ui/components/` (new package).

## 1. Design principles

1. **Dark canvas, loud blocks.** The screen is a near-black canvas. Emphasis comes from flat
   pastel *blocks*, not from gradients, glows or borders.
2. **One shout per screen.** Each screen gets at most one huge condensed display headline (e.g.
   "READY TO RIDE"). Everything else is calm.
3. **Pressable means raised.** Anything tappable that isn't a list row gets the chunky offset
   shadow (§4.1). Static content is flat. Users should be able to tell what's tappable from that alone.
4. **Lime = selected / go.** Lime is reserved for selected state, progress and "go" affordances.
   Don't use it for decoration.
5. **Bento, not lists.** Group navigation and quick actions as tiles of mixed size (2×2, 1×2,
   1×1) inside rounded section cards, as in `menu-sheet.jpeg` and `more-menu.jpeg`.
6. **Glanceable beats playful on the bike.** See §7, HUD exceptions.

## 2. Tokens

### 2.1 Colour

Hex values were sampled from the reference images and live in `Color.kt`. The old Forge
colours live in `HudColors.kt` (§7). Portrait screens still using them are migrated one by one (§8).

| Token | Hex | Use | Ref |
|---|---|---|---|
| `Canvas` | `#171717` | App background, behind everything | all |
| `Surface` | `#222222` | Section cards, list containers, dialogs | `home.jpeg` |
| `SurfaceRaised` | `#2A2A2A` | Icon tiles, unselected tiles, stat pills, chunky-button face (neutral) | `more-menu.jpeg` |
| `SurfaceSunken` | `#111111` | The chunky "shadow" slab under raised neutral elements; inputs | `more-menu.jpeg` |
| `Stroke` | `#3A3A3A` | Hairline dividers, unselected chip outline, input outline | `insights.jpeg` |
| `TextPrimary` | `#F4F5F7` | Primary text on dark | — |
| `TextSecondary` | `#9A9A9A` | Subtitles, section labels, inactive tabs | — |
| `TextDisabled` | `#5A5A5A` | Disabled chips ("13.64% IN TIES") | `insights.jpeg` |
| `OnBlock` | `#111111` | Text/icons **on any pastel block** (always black, never white) | `menu-sheet.jpeg` |
| `Lime` | `#A6FF6A` | Selected chip/tab, progress fill, "go" chevrons, success | `insights.jpeg` |
| `LimeDeep` | `#5E9E2E` | Chunky shadow under lime buttons | `empty-state.jpeg` |
| `BlockYellow` | `#F7DD48` | Yellow block tiles; selected duel tile | `home.jpeg` |
| `BlockLavender` | `#A39CE9` | Lavender block tiles (insights/stats) | `menu-sheet.jpeg` |
| `BlockMint` | `#82D6C6` | Mint block tiles (tips/guides) | `menu-sheet.jpeg` |
| `BlockCoral` | `#E17A57` | Coral block tiles (compete/ride) | `menu-sheet.jpeg` |
| `BlockPink` | `#F08CE0` | Rare: one special nav/CTA slot only | `home.jpeg` |
| `Danger` | `#E06B6B` | Destructive buttons ("EXIT"), defeat/error values | `dialog.jpeg` |
| `Warning` | `#FF8A2A` | Warnings, low fuel | `insights.jpeg` |
| `Scrim` | `#000000` @ 70% | Behind dialogs and sheets | `dialog.jpeg` |

**Brand note:** `SuzukiYellow #FAC816` stays the brand colour on the HUD and share cards. On
portrait screens use `BlockYellow`, which is softer and pastel, so it sits with the other blocks.

**Block colour assignment is semantic and fixed.** Pick by meaning, never at random:

| Block | Means | Bumblebee examples |
|---|---|---|
| Yellow | The bike / primary identity | Bike hero band, Garage entry |
| Lavender | Stats & insights | Ride stats, efficiency trends |
| Mint | Guides, tips, setup | Pairing help, permissions setup |
| Coral | Go ride / navigation | Start ride, Journey, routes |

Don't put more than 4 blocks on one screen, and don't put the same block colour on two adjacent tiles.

### 2.2 Typography

Two families. **Anton** is already in `res/font` and is our stand-in for the reference's
condensed display face. Add **Nunito** (Regular 400, SemiBold 600, Bold 700, ExtraBold 800,
from Google Fonts, OFL) as `nunito_*.ttf` for all UI text. Rajdhani and Permanent Marker stay for
the HUD and share cards only.

| Role | Family / weight | Size / line height | Tracking | Case | Ref |
|---|---|---|---|---|---|
| `displayLarge` (hero shout) | Anton | 48sp / 50sp | 0 | UPPER | "SPRINT DUELS" |
| `displayMedium` | Anton | 36sp / 38sp | 0 | UPPER | "START HERE", "DAILY CHALLENGES" |
| `headlineMedium` (tile title) | Anton | 28sp / 30sp | 0 | UPPER | "INSIGHTS", "VS BOTS" |
| `headlineSmall` (dialog title) | Anton | 26sp / 28sp | 0 | UPPER | "HOLD ON!" |
| `titleLarge` (top-bar title) | Nunito SemiBold | 20sp / 26sp | 0 | Title | "Insights" |
| `titleMedium` (card title) | Nunito Bold | 17sp / 22sp | 0 | Title | "Dinesh G.D.K" |
| `bodyLarge` | Nunito Regular | 16sp / 22sp | 0 | Sentence | dialog body |
| `bodyMedium` | Nunito Regular | 14sp / 20sp | 0 | Sentence | "Complete to earn rewards" |
| `labelLarge` (button) | Nunito ExtraBold | 15sp / 20sp | 0.6sp | UPPER | "ADD FRIENDS" |
| `labelMedium` (section label, chip) | Nunito Bold | 13sp / 16sp | 1.4sp | UPPER | "GAMES PLAYED", "OVERVIEW" |
| `labelSmall` (caption under value) | Nunito Bold | 11sp / 14sp | 1.0sp | UPPER | "IN WINS", "MATH" |
| `numberLarge` (custom) | Nunito ExtraBold, `tnum` | 32sp / 36sp | 0 | — | big stat values |
| `numberMedium` (custom) | Nunito Bold, `tnum` | 20sp / 24sp | 0 | — | "63.64%", "275" |

Rules: Anton is **only** used UPPERCASE and at 26sp or larger. Every number that can change uses
`fontFeatureSettings = "tnum"`. No italics.

### 2.3 Shape

| Token | Radius | Use |
|---|---|---|
| `ShapeXs` | 6dp | Tag chips ("MATH" tag on a card) |
| `ShapeSm` | 12dp | Icon tiles, inputs, small buttons |
| `ShapeMd` | 16dp | Chunky buttons, dialogs' buttons, stat pills (rect) |
| `ShapeLg` | 20dp | Section cards, block tiles, dialogs |
| `ShapeXl` | 28dp | Bottom sheets (top corners only) |
| `ShapePill` | 50% | Filter chips, stat pills, progress bars |

### 2.4 Spacing & sizing

4dp grid. Screen gutter **20dp**. Gap between section cards **16dp**. Card inner padding **20dp**.
Gap inside a bento grid **12dp**. Icon tiles **64dp** square with a 28dp icon. Minimum touch target
48dp. Top bar height 64dp; back button is a 48dp chunky square.

### 2.5 Elevation & motion

- **No Material tonal elevation, no blur shadows, no gradients** on portrait UI. The only depth
  device is the chunky offset (§4.1).
- Press: face translates down by the offset depth (the slab stays still) over 80ms, and haptic
  `LongPress`/`TextHandleMove` light tick. Release springs back (`spring(dampingRatio = 0.6f)`).
- Screen transitions keep the existing `NavMotion.kt`. Tiles entering a screen may stagger-fade
  (40ms per tile, max 6).
- Honour reduced-motion: if animations are off, skip stagger and press travel.

## 3. Iconography & illustration

- Icons: Material Symbols **Rounded**, weight 500, 24dp (28dp in icon tiles). White on dark, black on
  blocks. Never multi-colour icons except inside a block illustration.
- Illustrations: blocks may hold **one** spot illustration that bleeds off the tile's right or bottom
  edge (see "TIPS & TRICKS" cards). For Bumblebee, use simple flat motorcycle-themed vectors
  (helmet, fuel can, route line, the V-Strom silhouette from `vstrom_hero.png`). **Don't copy
  Matiks artwork or its mascot.**
- Line charts inside blocks: thick 3dp black line, area fill = 20% darker shade of the block, final
  point marked with a lime dot with a black 2dp ring (`menu-sheet.jpeg` INSIGHTS tile).

## 4. Components

All live in `ui/components/`. Each one gets a `@Preview` on `Canvas` background showing every state.

### 4.1 `ChunkyButton`

The signature element: a face sitting on a solid slab offset straight down.

```kotlin
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ChunkyStyle = ChunkyStyle.Neutral, // Neutral | Lime | Danger | Outline
    icon: ImageVector? = null,
    enabled: Boolean = true,
)
```

| Style | Face | Slab | Border | Text |
|---|---|---|---|---|
| Neutral | `SurfaceRaised` | `SurfaceSunken` | none | `TextPrimary` |
| Lime | `Lime` | `LimeDeep` | none | `OnBlock` |
| Danger | `Danger` | `Danger` darkened 30% | none | `TextPrimary` |
| Outline | `Canvas` | `Lime` | 1.5dp `Lime` | `TextPrimary` ("ADD FRIENDS" in `empty-state.jpeg`) |

Height 56dp (44dp for `compact = true`), slab depth **5dp** (4dp compact), `ShapeMd`, text
`labelLarge`. Disabled: face `SurfaceRaised`, text `TextDisabled`, no slab. Implement it as a `Box`
with the slab drawn behind via `drawBehind`, not with `Modifier.shadow`.

### 4.2 `ChunkyIconButton`

A 48dp square (`ShapeSm`) Neutral chunky with a 24dp icon. Used for back, close and top-bar actions.
The close button on sheets is a 56×48dp pill variant (`menu-sheet.jpeg`).

### 4.3 `BlockTile`

A pastel bento tile.

```kotlin
@Composable
fun BlockTile(
    title: String,                 // rendered Anton UPPER, OnBlock
    color: BlockColor,             // Yellow | Lavender | Mint | Coral | Pink
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,    // e.g. BlockBadge("▲ RATING")
    art: (@Composable BoxScope.() -> Unit)? = null, // bleeds off bottom/right, clipped by tile
)
```

`ShapeLg`, padding 20dp, title `headlineMedium` top-left, max 2 lines. Tiles are **flat** (no slab).
Press feedback is a scale to 0.97. `BlockBadge` is a black pill (`OnBlock` background, `TextPrimary`
text, `labelSmall`).

### 4.4 `SectionCard`

`Surface` background, `ShapeLg`, 20dp padding. An optional header is either a `SectionLabel` (small)
or an Anton `headlineMedium` title ("PLAY", "SOCIAL" in `more-menu.jpeg`).

### 4.5 `IconTile`

A 64dp `SurfaceRaised` square (`ShapeSm`) with a 3dp `SurfaceSunken` slab, holding a white icon.
Its caption sits **below, outside** the tile in `labelSmall` `TextSecondary` UPPER. Lay them out in
4-column grids. Selected variant (as in `home.jpeg` "MATH"): face `BlockYellow`, 2dp `BlockYellow`
outer ring with a 3dp gap, caption `BlockYellow`.

### 4.6 `PillChip` (filter / segmented)

36dp high, `ShapePill`, horizontal padding 16dp, `labelMedium`. Unselected: 1dp `Stroke` border
with `TextDisabled` text. Selected: 1.5dp `Lime` border, `Lime` text, background `Lime` @ 8%. Rows
scroll horizontally with 20dp edge padding and a 10dp gap.

### 4.7 `DropdownLabel`

`labelMedium` `TextPrimary` UPPER with a chevron-down, no background ("MATH ⌄  SPRINT DUEL ⌄").
Use it for the top-of-screen scope filters.

### 4.8 `StatPill`

A pill-shaped `SurfaceRaised` with a 1dp `Stroke` border, 40dp high, a 22dp leading icon and a
`numberMedium` value (`home.jpeg` "275", "5 / 7", "434 XP"). The value may be tinted to match its
meaning (Warning orange for a streak/fuel).

### 4.9 `StatTriple`

Three columns, each a `numberMedium` value over a `labelSmall` caption. The value colour encodes
meaning: `Lime` good, `Warning`/`Danger` bad, `TextDisabled` neutral (`insights.jpeg`). Above it
goes a `SectionLabel` plus a `numberLarge` headline value.

### 4.10 `SectionLabel`

`labelMedium`, `TextSecondary`, UPPER. Optional trailing count in `TextDisabled` and an optional
hairline that fills the rest of the row ("Friends 0 ———", `empty-state.jpeg`).

### 4.11 `ProgressPill`

A 40dp-high track in `SurfaceRaised` with a `Lime` fill. Its leading segment is a lime chunky
capsule containing a `numberMedium` "0/7" in `OnBlock`, with an optional trailing icon
(`home.jpeg` DAILY CHALLENGES).

### 4.12 `ChunkyDialog`

`Surface`, `ShapeLg`, 24dp padding, width = screen − 2×40dp, over a `Scrim`. The title is
`headlineSmall` Anton, the body `bodyLarge`, then two `ChunkyButton`s side by side (Neutral cancel
on the left, Lime or Danger confirm on the right). Use it for **every** confirm dialog; no stock
`AlertDialog`.

### 4.13 `EmptyState`

Centred: a spot illustration (max 200dp), a `displayMedium` Anton headline, a one-line
`bodyMedium` `TextSecondary` subtitle with personality, an Outline `ChunkyButton`, and optionally a
text-link action (`labelLarge` `TextSecondary` with a leading icon).

### 4.14 `ForgeTopBar`

64dp high: a `ChunkyIconButton` back, a `titleLarge` title, and trailing slot(s) taking either a
compact `ChunkyButton` or a `StatPill`. No background, no elevation; it sits on `Canvas`.

### 4.15 `ForgeBottomNav`

Sits on `Canvas` with no top border, 72dp high, 5 slots (the five pillars). Each slot is a 26dp
icon over a `labelSmall` label. Inactive is `TextSecondary`. Active is `Lime` icon and label with
no indicator pill. Badges are a `Danger` dot or a small `Danger` count pill.

## 5. Screen direction

Restyle one screen per PR, in this order. "Ref" names the reference image(s) to open alongside.

| # | Screen | File | Ref | Direction |
|---|---|---|---|---|
| 1 | Shell / bottom nav | `ui/nav/BumblebeeApp.kt` | `home.jpeg` | `ForgeBottomNav` on `Canvas`; screens get the 20dp gutter. |
| 2 | Cockpit (portrait home) | `ui/cockpit/CockpitScreen.kt` | `home.jpeg`, `menu-sheet.jpeg` | Top row of `StatPill`s (connection, fuel est., odo). A Yellow-band bike hero card (band top 40%, bike art bleeding over the band, name and status below on `Surface`, lime chevron button). The readiness verdict as the one Anton shout. Then a bento of `BlockTile`s: Start ride (Coral, 1×2), Ride stats (Lavender, 2×2 with line chart), Setup/pairing help (Mint), Garage (Yellow). Warnings become a `SectionCard` list, not a block. |
| 3 | Ride / ride detail | `ui/ride/RideScreen.kt` | `insights.jpeg` | `ForgeTopBar` + `DropdownLabel` scope row (bike · period). `PillChip` tabs (Overview, Speed, Fuel, Route). A `StatTriple` for distance/time/avg. Charts follow the `dataviz` rules: flat bars, `Stroke` grid, `labelSmall` axes, block colours as series. |
| 4 | Journey | `ui/journey/JourneyScreen.kt` | `home.jpeg` | Destination entry as a big `SectionCard` with a Coral "GO" chunky CTA. The cluster preview keeps its look (it mirrors the bike TFT). |
| 5 | Garage | `ui/garage/GarageScreen.kt` | `more-menu.jpeg` | A bike profile `SectionCard` at the top, a horizontal carousel of short Block promo cards (service due, add widget), then a "BIKE" `SectionCard` holding an `IconTile` grid (Fuel log, Service, Documents, Prefs). |
| 6 | Fuel log | `ui/fuel/FuelLogScreen.kt` | `insights.jpeg` | Same pattern as Ride: `StatTriple` (km/l avg, last fill, cost) plus a bar chart plus a list in a `SectionCard`. |
| 7 | Explorer | `ui/explorer/ExplorerScreen.kt` | `more-menu.jpeg` | `PillChip` filters plus `SectionCard` results; an `EmptyState` when there's nothing. |
| 8 | Dialogs, empties, permissions | everywhere | `dialog.jpeg`, `empty-state.jpeg` | Replace every `AlertDialog` with `ChunkyDialog`. Every empty list gets an `EmptyState` with rider-flavoured copy ("NO RIDES YET" / "the road's not going to ride itself"). |

## 6. Do / don't

**Do**
- Use tokens and components only. If something is missing, add a token or component first, then use it.
- Keep black text on every pastel block.
- Keep one Anton shout per screen, and put Anton titles on block tiles.
- Leave generous space between sections: let the canvas breathe.
- Mark estimated values with "est." in `labelSmall` (golden rule).

**Don't**
- Hard-code `Color(0x…)`, `.dp` radii, or `fontSize` in screen files.
- Use gradients, glows, blur shadows, outlined cards or Material `Card` elevation on portrait screens.
- Use white text on pastel blocks, or lime as decoration.
- Use Anton for body copy, lowercase or sizes under 26sp.
- Copy Matiks artwork, mascots, product copy or the "JELO"-style branded slot.
- Show RPM, gear, temps or fault codes, at any size or colour.

## 7. HUD exceptions (landscape Rider HUD, share cards)

`ui/hud/**` and `ui/sharecard/**` **keep the Forge cluster language** (`design/hud-landscape/tokens.json`,
`screens/horizontal-home-mock.png`). The rider reads it at a glance while riding, so it stays
high-contrast, has no pastel blocks, no illustrations, no press-travel animation, and keeps
Rajdhani tabular numerals and the existing `Forge*` colour tokens. The only borrowings allowed there:
`Lime` may replace `SuccessGreen` for "OK" states, and dialogs opened from the HUD use `ChunkyDialog`.

When `Color.kt` is rewritten, move the HUD-only colours into `ui/theme/HudColors.kt` (`ForgeBlack`,
`ForgeCard`, `ForgeBezel*`, `SuzukiYellow*`, `RedlineGlow`, …) so the two languages don't mix.

## 8. Implementation phases

1. **Tokens — done (2026-10-23).** `Color.kt` holds the §2.1 tokens. The old Forge colours moved
   unchanged to `HudColors.kt`. `Type.kt` has `ForgeTypography` (portrait), `HudTypography`,
   `NumberLarge` and `NumberMedium`. `Shape.kt` has `ShapeXs…ShapePill`, `ForgeShapes` and
   `HudShapes`. `Theme.kt` has `BumblebeeTheme` (portrait) and `HudTheme`, which wraps
   `RiderHudScreen` in `BumblebeeApp.kt`. Portrait screens still reference `HudColors.kt` names
   until phase 3 migrates them.
2. **Component kit:** build `ui/components/*` from §4, each with previews. Components use only
   `Color.kt` tokens, never `HudColors.kt`. Don't touch screens yet.
3. **Screens:** one PR per row of §5, in order, using the `restyle-screen` skill. Each PR moves its
   screen (and that screen's private `components/`) off every `HudColors.kt` name, using the
   mapping below. Each PR includes an actual-device screenshot in `screens/` named
   `<screen>-v2-actual.png`.
4. **Sweep:** the migration check below must print nothing for any file outside `ui/hud/`,
   `ui/sharecard/` and `ui/theme/` (`ui/ride/ShareComposer.kt` may keep brand colours inside the
   share-card preview itself). Then grep for leftover `Color(0x`, `RoundedCornerShape(`, `fontSize =`
   and `AlertDialog` in the same scope; there should be none.

**HudColors → portrait token mapping** (pick by what the colour *does* on that element):

| Old (`HudColors.kt`) | New (`Color.kt`) |
|---|---|
| `ForgeBlack` | `Canvas` |
| `ForgeCard` | `Surface` |
| `ForgeCardTop`, `ForgeGraphite` | `SurfaceRaised` (fills) or `Stroke` (borders, dividers) |
| `ForgeBezel` | `Stroke` |
| `ForgeBezelHi`, `ForgeWhite` | `TextPrimary` |
| `LabelMute` | `TextSecondary` |
| `SuzukiYellow`, `SuzukiYellowDim` | `BlockYellow` (bike identity) or `Lime` (selected / go / progress) |
| `SuccessGreen` | `Lime` |
| `WarningAmber`, `RedlineGlow` | `Warning` |
| `ErrorRed` | `Danger` |

**Migration check** (prints any leftover HUD colour in the given files):

```bash
grep -nwE 'ForgeBlack|ForgeCard|ForgeCardTop|ForgeGraphite|ForgeBezel|ForgeBezelHi|ForgeWhite|SuzukiYellow|SuzukiYellowDim|SuccessGreen|WarningAmber|RedlineGlow|ErrorRed|LabelMute' <files>
```

## 9. Review checklist (per screen PR)

- [ ] Side-by-side with the named reference image: canvas, block colours, radii and type roles match.
- [ ] Only §2 tokens and §4 components are used; there are no literals in the screen file.
- [ ] At most one Anton shout; at most 4 blocks; block colours follow the §2.1 semantics.
- [ ] Every tappable element is chunky or a block/icon tile; static content is flat.
- [ ] Text contrast is ≥ 4.5:1 (black on every block passes; check `TextSecondary` on `Surface`).
- [ ] Touch targets are ≥ 48dp, and font scale 1.3× doesn't clip Anton titles (they wrap to 2 lines max).
- [ ] No data violates the golden rule; estimates are labelled "est.".
- [ ] `./gradlew :app:assembleDebug` and unit tests pass.
