# Ride share card v2

**Date:** 2026-10-24 · **Status:** Design, awaiting approval
**Replaces the look of:** `2026-09-26-ride-share-card-design.md` (that card's model, privacy trim, map tiles and painter pipeline stay as the base)

## Why

Riders share ride images to several places: Instagram stories and feed, WhatsApp, riding groups. The current card has one fixed design. Its Map and Transparent backgrounds work, but the default black background is a flat `#08080A` fill and looks plain and boring. Changing the options changes the background, not the look, so every export looks the same.

The goal is to make the image something a rider is proud to post. That means several distinct looks to choose from, richer content drawn from data only this app has (bike telemetry, ride history), and a share flow that remembers your preferences.

Mockups from the brainstorm (four looks): `docs/superpowers/specs/mockups/share-card-v2-styles.html`. The cards are 270×480, so one mockup unit is 4 px on a 1080 story; the looks size everything in these units. The share-flow mockup was not kept. They are direction, not pixel specs.

## Goals

1. Four looks, all available. The rider picks one per export:
   - **Night Glow** replaces flat black. It has a deep warm radial gradient (a yellow-brown glow behind the route fading to near-black), faint contour lines, film grain, and the route drawn with a soft glow and a gradient stroke. Stats sit on a frosted, translucent panel with a thin accent border.
   - **Poster** is a cream paper texture with the route as a bold ink line. One huge number uses a heavy serif display face, and a solid accent band carries the stats along the bottom.
   - **Cockpit** looks like the bike's own dash: a dark faint grid, the route in a cool accent, a speed dial arc for max speed, odometer digits in boxes, and a speed trace strip along the bottom. Text uses monospaced numerals.
   - **Ride Pass** presents the ride as a boarding pass: a ticket in the accent colour on a dark surround. It has from → to codes and place names, a ride number, the route, a dashed tear line with side notches, a stub with the stats, and a decorative barcode.
2. **Ride names.** The rider can name a ride in two places: on the ride detail screen (an edit action next to the ride title) and at the top of the share screen. Both edit the same `rides.name` column, via the existing `RideCaptureRepository.setRideMeta`. The name is used on the image and shows in the archive, which already displays it when set (`RideVisuals.kt`). Clearing the field removes the name.
3. **Highlights.** An automatic badge appears when the ride earns one.
4. **Weather and sun.** A line like "22°C · Clear · sunrise ride".
5. **Elevation profile.** A small hill silhouette appears when the ride climbed meaningfully.
6. **Share prompt at ride end.** It fires only when the ride earned a highlight.
7. **Day-trip card.** One image covers all rides on one day, with stops marked.
8. Share flow: pick a look from four live previews, then customise it (details below).

## Non-goals (this round)

- Animated / video export of the route drawing itself (Relive-style). Planned later as its own project.
- Posting through social network APIs or accounts. Sharing stays on the Android share sheet. The one exception is the Instagram Stories intent described below.
- Any server-side rendering. Everything is still painted on the phone.

## Anti-goals (failure even if it "works")

- Unreadable text on a busy photo or map background. Every look must keep its text legible on Map and Photo, using scrims, shadows or panels as that look needs.
- Leaking where the rider lives. The existing `SharePrivacy` 500 m trim at both ends stays for every look. Place names are locality level only (area / town), never streets. The rider can turn place names off.
- Silent downgrades. Today, missing map tiles quietly turn a Map card into Corridor-on-black (`ShareCardModel.withTiles`). The new flow must tell the rider when this happens.
- A nagging share prompt. It never fires for an ordinary ride.

## Share flow

Entry points: the existing **Share card** button on a finished ride (`RideVisuals.kt`). There is a new **Share day** entry for day-trip cards (see below), and the ride-end prompt.

### Screen 1: pick a look

- **Ride name field** at the top. It is pre-filled with the ride's name if it has one, otherwise empty, with the fallback headline shown as a placeholder. Edits are saved to the ride (`RideCaptureRepository.setRideMeta`) when the rider leaves the field or shares. The name is capped at 30 characters.
- **Four live previews** of this ride, one per look, in a 2×2 grid. They are rendered with the real ride data and the current settings.
- **Size switch** under the grid. It applies to all four previews and the final export:
  - Story 1080×1920
  - Feed 4:5, 1080×1350 (new)
  - Square 1080×1080
- Tapping a preview opens screen 2 for that look.

### Screen 2: customise

- A large preview of the chosen look.
- **Background:** the look's own, Map, Photo or Transparent. Each look adapts to each background:
  - On Transparent, a look keeps only its route and type. For example, Poster drops the paper, and Ride Pass keeps the ticket.
  - On Photo and Map, a look adds whatever it needs to stay readable.
- **Accent colour:** Bumblebee yellow (`#FAC816`, default), plus four alternatives: red-orange, sky blue, green and white. Each look applies the accent in its own way (route, band, ticket body, dial).
- **Stats:** the rider can pick up to 3 of the available stats. This uses the existing `ShareMetrics` (distance, moving time, max speed, average speed, climb, km/L, duration). The existing big-number choice stays.
- **Toggles:**
  - Place names on/off
  - Highlight badge on/off (only shown when the ride has one)
  - Weather line on/off (only when weather is known)
- **Actions:**
  - **Save** writes to the gallery (`ShareCardGallery`).
  - **Sticker** exports a transparent PNG with only the route and stats in the current look's style. If Instagram is installed, it opens Instagram Stories with the image as a sticker. Otherwise it goes to the share sheet.
  - **Share** opens the share sheet with the image and the ride sentence as the caption (as today).
- **Remember last settings.** Persist the last look, size, background, accent, stats choice and toggles. The next time the share screen opens, it starts on that look with those settings. Photo is never remembered; it falls back to the look's own background. Per-ride things (name, chosen photo) are not part of this.
- If map tiles could not be loaded, show a short notice in the customise screen ("Map unavailable offline, showing route only"). Don't switch looks silently.

## Card content (all looks)

**Headline.** The first available of:

1. The ride name.
2. "From → To" place names, when place names are on and both are known.
3. The current time-of-day headline ("Morning ride").

Today the ride name is passed into the card as `bikeName` (`ShareComposer.kt`), so it lands in the header's bike slot. Fix this. The ride name is the headline only. The bike slot shows a bike name only if one exists. There's no bike-name setting today, so omit the slot until there is.

**Place names.** Use `android.location.Geocoder` to look up the locality of the first and last points of the privacy-trimmed route, not the raw start and end. Cache the result on the ride (new nullable columns, e.g. `startPlace` and `endPlace`) so each ride is looked up once. If the lookup fails (offline, no result), show no place names. Ride Pass then shows the headline in place of the from/to codes. The three-letter codes are airport style: the first letter, then consonants ("Bengaluru" → BNG); a multi-word name ends on its last word's initial ("Nandi Hills" → NDH); short names fill with their remaining letters ("Goa" → GOA).

**Ride number** (Ride Pass): the ride's position among finished, non-demo, non-merged-child rides, oldest = 1. It is shown as `No. 0147`.

**Highlights.** Compare the ride against all earlier finished, non-demo rides. Show at most one badge, the first match in this order:

1. "Longest ride yet" (distance)
2. "New top speed" (max speed)
3. "Biggest climb yet" (elevation gain, only if at least 40 m)
4. "Longest time in the saddle" (moving time)
5. Distance club milestones, the first time a ride crosses 100, 200 or 300 km ("100 km club")

The first-ever ride gets no badge. Each look shows the badge in its own way, for example as a stamp on Ride Pass or a small pill on Night Glow.

**Weather and sun.**
- **Weather:** going forward, when a ride starts, store the current weather (temperature, WMO code) on the ride. `WeatherRepository` already fetches it during rides. For older rides with no stored weather, the line is simply hidden. There is no back-fill this round.
- **Sun:** "sunrise ride" or "sunset ride" when the ride's moving time overlaps the 30 minutes either side of local sunrise or sunset at the route's midpoint. Compute this on the phone with a standard solar formula, with no network.
- **Format:** `22°C · Clear · sunrise ride`, with any missing part dropped.

**Elevation profile.** A smoothed altitude silhouette from the GPS track, drawn small. Poster puts it above the band and Cockpit beside the speed trace. Night Glow and Ride Pass may include it where it fits. Show it only when climb is at least 40 m, the same threshold the climb stat already uses.

**Rides without a track** (under 1 km after the trim, or GPS-only gaps): every look must still produce a good image without a route. It uses the headline, stats and look decoration only, as v1 does today.

**Cockpit on GPS-only rides:** hide the odometer and any fuel stats. Keep the dial (GPS max speed) and the speed trace.

## What each look shows

The rider's stat choice is one ordered list shared by all looks. Each look shows the first stats on that list it has room for, skipping what it already shows in its own place, and the customise screen only offers those (`ShareLookRules`).

| Look | Big number | Built in, not offered as a stat | Stat slots | Odometer |
|---|---|---|---|---|
| Night Glow | rider picks | the big number | 3 | — |
| Poster | rider picks | the big number | 3 | — |
| Cockpit | — | max speed (the dial) | 3 | boxed digits, top right |
| Ride Pass | — | distance, moving time | 1 | boxed digits beside the barcode |

The odometer is hidden on GPS-only rides.

## Share prompt at ride end

When a ride finishes (bike disconnect timeout, End ride, or GPS ride stop) and it earned a highlight, post a normal notification like "New top speed: 112 km/h. Share it?". Tapping it opens the share screen for that ride. It does not fire for demo rides or rides without a highlight, and there is a setting to turn it off (on by default). It needs the notification permission, which the app already uses for the bike link notification.

## Day-trip card

Shows all finished rides on one calendar day as one image:

- The route is all rides' tracks, each trimmed by `SharePrivacy`, drawn in order.
- The gaps between rides are marked as stop dots.
- Stats are summed: distance, moving time, max of max speed.
- The headline is the day's date, or a name the rider types. The name isn't saved anywhere, since a day has no row.
- It uses the same four looks, sizes, backgrounds and accents.
- Entry is a **Share day** action on a day selected in the Explorer calendar, shown when that day has two or more rides. A merged ride is already one ride, so it uses the normal flow. Its segment boundaries are drawn as stop dots too.

## Constraints

- Android, Kotlin, Jetpack Compose. Painting stays on `android.graphics.Canvas` in `ui/sharecard/` (`ShareCardPainter` and friends). Card logic and data stay in `core/ride`, pure Kotlin and unit-tested, as v1 does.
- Fonts already in the app: Anton, Rajdhani, Permanent Marker. Poster's serif and Cockpit's monospace need new bundled fonts with an open license (e.g. Fraunces, JetBrains Mono). They must be bundled, not downloaded.
- Textures (grain, paper) are generated or bundled small images. They must not noticeably bloat the APK; a few hundred KB total is fine.
- Room schema changes (place names, ride weather) need a migration.

## Decided in conversation

- All four looks ship; the rider picks per export.
- The current flat black is replaced by Night Glow.
- Map and Transparent backgrounds are kept.
- Riders can name rides; the name goes on the image and is saved to the ride.
- Share flow: four live previews side by side, then customise.
- Accent colour, Sticker export, remembering last settings, and a 3-stat maximum are all in.
- Highlights, weather and sun, elevation profile, the ride-end share prompt and day-trip cards are in this round. The animated export is not.

## Claude's calls (check these)

- The v1 Ink / Corridor / Map styles go away as a separate choice. Map becomes a background, and Corridor (a faint wide road under the route) becomes the fallback drawing when tiles fail.
- Feed size is 4:5 at 1080×1350.
- The ride name limit is 30 characters.
- Place names are locality level, looked up from the trimmed route ends, cached on the ride, and can be switched off.
- Weather is stored at ride start from now on, with no back-fill for old rides.
- Only one highlight badge per image, in the priority order above.
- The ride-end prompt is on by default, with a setting to turn it off.
- Day-trip names are not saved.
- The Instagram Stories intent is used for Sticker when Instagram is installed.

## Left to the builder

- Exact layouts, spacing and type sizes per look and per size. The mockups are direction only.
- How each look adapts to the Map, Photo and Transparent backgrounds, within the readability anti-goal.
- The exact accent hex values beyond Bumblebee yellow.
- How textures and contour lines are produced (procedural vs bundled).
- The preview rendering strategy, such as down-scaled renders for the 2×2 grid and caching.
- Where in Settings the share-prompt toggle lives.
