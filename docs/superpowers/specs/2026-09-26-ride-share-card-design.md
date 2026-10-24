# Ride share card

**Date:** 2026-09-26 · **Status:** Implemented

After a finished ride, the rider shares a picture: the path, one sentence, and three stats. It opens already filled in. A few switches change the look. The picture is painted on the phone and handed to the Android share sheet. No server.

This is separate from live location sharing in `docs/features/2026-09-26-next-features-plan.md`.

## What the rider gets

On a finished ride, next to the existing **Share recap** action, **Share card** opens a composer.

Each time the composer opens, the switches reset. The card starts as a story (9:16), Ink style, stats on a dark bar, forge black background. From there:

| Switch | Options | Default |
| --- | --- | --- |
| Shape | Story 1080×1920, Square 1080×1080 | Story |
| Style | Ink, Corridor, Map | Ink |
| Stats | Bar, On photo | Bar |
| Photo | Gallery image as the underlay | None |
| Save transparent PNG | Background left empty | Off |

Photo and transparent PNG apply to Ink and Corridor only. Map always shares a flat image. It can still use Bar or On photo.

For Ink and Corridor, if neither a photo nor a transparent PNG is chosen, the background is black (`#08080A`). Choosing a photo replaces that black. Choosing a transparent PNG leaves the background empty. The stat bar, when Bar is selected, stays a solid dark strip in all three cases so the numbers stay readable. On photo draws the sentence and stats directly on the picture, with a shadow.

**Share** writes the PNG and opens the system share sheet with that image and the sentence as the caption. **Save transparent PNG** writes the same layout with a clear background so the rider can overlay it on another picture outside the app.

## Card contents

**Sentence.** The first sentence of `RideStory.of` for this ride (text up to the first period). The painter wraps it to at most two lines at the card’s text width and ellipsizes the rest. Story and square use the same arrangement: the path fills the area above, and the sentence plus stats sit along the bottom. Square is that arrangement on a shorter canvas.

**Stats.** Three slots:

1. Distance, when `km > 0`, one decimal, label `km`.
2. Moving time, when `movingSec > 0`, pauses excluded, clock form used by `RideStory` (`42 min` or `1h 42m`), label `moving`.
3. The first match below:
   - max speed, when `maxSpeedKmh >= 100`, label `km/h`
   - climb, when the supplied `climbM` is at least 40, label `m`
   - tank km/L, when the caller supplies a figure because this ride closed a full-to-full tank, one decimal, label `km/L`
   - otherwise average moving speed, when `avgMovingSpeedKmh > 0`, rounded, label `km/h`

A slot that does not qualify is skipped. The card shows however many qualify, up to three. It does not invent zeros.

**Path.** GPS points are trimmed with `SharePrivacy` (first and last 500 m dropped). The remaining points are normalized with `TrackPlot` and drawn as one stroke. The card opens on Map when a track can be drawn. Tiles are darkened and desaturated, then the route is drawn over them in three passes: an orange jacket, a yellow body colored by pace, and a white core. A giant low-opacity distance sits behind the route. The headline is the time of day plus "RIDE"; the ride sentence sits under it. Ink and Corridor use the same chrome without the map. A photo gets a dark gradient at the bottom before the type. A transparent PNG skips the background. Corridor adds a faint wider road under that stroke and leaves the rest of the card dark.

**Chrome.** `BUMBLEBEE`, the day and time of day (`RideStory.timeOfDay`), and the bike name already stored for the bike. Omit the bike name when it is blank. A demo ride adds a small `Demo` mark.

**No path.** If the ride is under 1 km, `SharePrivacy.retainAfterRide` returns nothing. The card shows the sentence and stats on black, with no stroke and no map. A map of a short hop would be the block around home. The same applies when the trim leaves fewer than two points.

## Pieces

### 1. Share card model — `core/ride`

New type next to `RideStory` and `TrackPlot`. Pure Kotlin. No Android, no bitmaps, no network.

Inputs:

- `RideInsightInput`
- track: `List<GpsFix>`
- switches: shape, style, stats mode, background (black, photo, transparent)
- optional `climbM`, which the composer sets from `TrackInsights.elevationGainM(track)`
- optional `tankKmPerL`
- `demo: Boolean`

Output, a `ShareCardModel`:

- the switches, after fallbacks below
- sentence
- up to three stat lines (value + label)
- plot points in 0–1 space, each with a pace fraction 0–1 for color
- `demo`
- whether a map tile cover is required

Fallbacks live here so tests can lock them:

- Photo or transparent is rejected when style is Map. The stored background becomes black, which here means “no photo”, not “paint over the map”. With a drawable track the pixels are still the map tiles.
- Style Map with no drawable track becomes Ink on black, and no tile cover is required.
- Style Map with a drawable track sets `needsTiles`. The model does not know whether tiles exist. If any tile is missing, the painter paints Corridor on black instead of a partial map.

### 2. Painter — app module

Android canvas. Inputs: `ShareCardModel`, optional photo bitmap, optional OSM tile bitmaps from `OsmTileStore`. Output: a PNG file under the existing cache `share/` directory, via the existing `FileProvider` authority (`${packageName}.share`).

- Story is 1080×1920. Square is 1080×1080.
- Black and map cards are opaque. Transparent cards are `ARGB_8888` with a clear background. The stat bar is still painted opaque when Stats is Bar.
- A photo is decoded with subsampling so the bitmap is about the card size, then drawn into the new image. The PNG is a new bitmap, so the gallery file’s location metadata is not copied.
- Map draws cached tiles for the trimmed track’s cover, then the yellow stroke on top. If any required tile is missing, the painter does not draw a partial map. It paints Corridor on black with the trimmed track instead.
- Palette matches the forge tokens: black `#08080A`, yellow `#FAC816`, muted label `#8A8E96`.

The on-screen preview is this same painter at display size, so the picture they see is the picture they send.

### 3. Composer — ride detail

Lives with the current share actions in `RideVisuals` / the ride detail screen. Finished rides only. Controls are the switches in the table above, a preview, **Share**, and **Save transparent PNG** (hidden for Map).

Changing a switch rebuilds the model and repaints the preview.

### 4. Share sheet

The existing `shareRide` path writes a cache file and starts `ACTION_SEND`. The card send uses `image/png` plus `EXTRA_TEXT` set to the sentence. GPX and CSV share stay as they are.

## When something goes wrong

| Situation | Result |
| --- | --- |
| Photo fails to decode | Stay on the black card. The composer says the photo could not be used. Share still works. |
| Map tiles are not all on disk | Corridor on black, trimmed track. No map with holes. |
| Ride under 1 km, or trim leaves no line | Sentence and stats on black. No path, no map. |
| PNG cannot be painted or saved | Share falls back to the existing text recap. |
| Demo ride | Card is allowed, with a `Demo` mark. |
| Chat app flattens a transparent PNG | Out of our control. We still write real transparency. |

## Tests

JVM unit tests on the model, beside the other `core/ride` tests:

- Distance and moving time appear when the ride has them.
- The third stat follows the order: max speed at or above 100 km/h, then climb of at least 40 m, then tank km/L, then average speed.
- A ride under 1 km yields no plot points.
- A longer ride’s plot points come only from samples outside the first and last 500 m.
- Map plus a drawable track sets `needsTiles`. Map with no drawable track becomes Ink on black and does not need tiles.
- Photo or transparent requested with Map is stored as black.
- A demo ride is flagged on the model.

No new device pixel-test framework. The painter is a thin canvas over the model. On a phone, before calling the feature done: a black story card shares, a photo replaces the black, a transparent PNG opens elsewhere with an empty background, a short ride shares with no path, and Map without tiles shares as Corridor on black.

## Out of scope

- Flyover video
- Rendering the card on a server
- Live location sharing
- Letting the rider pick which three stats appear
- Publishing home: the 500 m cut stays, and a missing track never becomes a map of the start
