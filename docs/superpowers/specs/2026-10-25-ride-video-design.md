# Ride video export: design

Date: 2026-10-25
Status: phase 1 (planning, terrain and camera maths) built and tested

## Why

A share card shows a ride as a single picture. A ride is better told as a journey: where it began, how
it climbed, where you stopped, how far you went. Relive-style flyovers are what riders share, but
those apps need an account, upload the track to a server, and use the same look for every ride.
Bumblebee already holds a 1 Hz GPS track with altitude, speed and heading, plus the ride timeline
and highlights. It can render the flyover itself, on the phone, in its own style.

## Goals

- Export an MP4 of a ride: a camera flies over **3D terrain** along the route while the path draws
  itself from start to finish.
- Show the **story**: the start, the moments in between (top speed, highest point, biggest climb,
  long stops, refuels, distance-club badges), the finish, and a closing stats card.
- **Length** follows the ride: a recommended length from distance and number of moments, adjustable
  by the rider up to a hard maximum.
- Three shapes: **9:16** (1080×1920), **16:9** (1920×1080), **1:1** (1080×1080).
- Optional **music from the rider's own files**, trimmed and faded to the video length. Silent by
  default.
- Everything renders **on the phone**. No account, no upload, no server.
- A **live preview** before export, scrubbable, at reduced resolution.

## Non-goals (this round)

- Satellite or photo imagery. The look is stylised terrain only, so there are no imagery licences
  or keys.
- Bundled music. Rider's own track or silence.
- Multi-ride or day-trip videos. One ride per video; day trips can follow.
- Photos or clips from the ride spliced into the video.
- Editing the video afterwards (cuts, text edits). Settings are chosen before rendering.
- Cloud rendering of any kind.

## Anti-goals (failure even if it "works")

- **Leaking where the rider lives.** The `SharePrivacy` 500 m trim applies to the drawn path, the
  camera and every marker. Nothing is drawn, labelled or framed inside the trimmed zones. Place
  names are locality level only, as on share cards.
- **Silent downgrades.** If elevation data can't be fetched (offline, no cache), the rider is told
  before rendering and offered a flat-terrain version. The app never ships a flat video while saying
  it's 3D.
- **Inventing events.** Every callout comes from recorded data. A GPS gap is never drawn as a
  straight "road" across terrain; it is drawn dashed.
- **Stutter or tearing in the file.** Frames are rendered offline at fixed timestamps, so the output
  is smooth even if the phone renders slower than real time.
- **Cooking the phone.** Long renders run in a foreground service with progress, can be cancelled,
  and back off when the phone reports thermal throttling.
- **Unreadable overlays.** Every text overlay sits on a scrim or panel and stays legible over bright
  and dark terrain.

## What makes it better than the usual flyover

Most ride-video apps draw a line on a flat map with a fixed overhead camera, so every video looks the
same. This one is built like a short film:

- **Real light from the real ride.** The sun's direction and height come from the ride's actual time
  and place, so morning rides glow low and warm, noon rides are crisp, and evening rides get long
  shadows and a golden sky. Night rides switch to a night palette with the path as the light source.
- **A directed camera, not a fixed one.** It opens with a reveal, flies low through valleys and
  climbs with the road, rises before a hairpin section so the bends read, slows down and circles at
  the highest point, and ends with an orbit around the whole route. Shots vary with the terrain
  instead of following one rule.
- **Speed ramping.** Dull straight stretches go fast; twisty sections, climbs and moments slow down,
  so the time goes on the interesting parts.
- **A path that shows the ride.** The line glows in the accent colour, with an option to colour it by
  speed or climb. A bright comet head with a fading trail, and a soft light where the marker passes.
- **Depth and atmosphere.** Height-based fog in the valleys, haze toward the horizon, a sky gradient
  that matches the time of day, and a gentle bloom on the path and marker.
- **Motion that feels filmed.** Eased camera moves with no sudden turns, slight motion blur when
  moving fast (several sub-frames blended), and a subtle film grain and vignette.
- **Designed type.** Cards and numbers animate in (counters roll, labels slide) using the share-card
  fonts, so a frame grabbed from the video looks like a poster.
- **Peaks and passes named.** The highest point gets a label with its height, and the route's
  climb shows on the elevation strip as it happens.

## The video, scene by scene

Times below are for a 30 s video and scale with the chosen length.

| Scene | Share | What happens |
| --- | --- | --- |
| **Intro** | ~15% | High overview of the whole region, route ghosted faintly. Title (ride name or auto name), date, from → to place names. Camera dives toward the start of the trimmed route. |
| **Ride** | ~67% | Chase camera above and behind the rider marker. The path draws in the accent colour with a soft glow; the route still ahead stays ghosted. The camera's time is distance-based and slows down around moments. |
| **Moments** | inside Ride | At each chosen moment the camera eases, a callout card pops near the marker ("Top speed 112 km/h", "Highest point 2,140 m", "Stopped 25 min", "Refuelled"), holds about 1.5 s, then the ride continues. |
| **Finish** | ~18% | Marker reaches the end; camera pulls back and orbits to frame the whole route. Stats card: distance, moving time, average speed, total climb, highest point, plus any distance-club badge. Small app mark and data attribution. |

**Always-on HUD** during the Ride scene: distance counter, time of day, current speed, altitude, and a
thin elevation profile strip along the bottom with a moving playhead.

### Moment selection

Candidates come from existing analysis (`RideTimeline` events, `RideHighlights`, `TrackInsights`):

1. Highest point and lowest point, if the ride climbs at least 150 m.
2. Biggest single climb (start and top).
3. Top speed.
4. Stops of 10 min or more, and refuels.
5. Distance-club badge (100/200/300 km), shown on the stats card, not mid-ride.

Rules: at most one moment per 4 s of Ride scene; moments closer than 8% of the route merge into the
more important one; none inside the privacy trim zones. Priority follows the list order.

### Length

| Ride distance | Recommended | Rider can choose |
| --- | --- | --- |
| under 20 km | 15 s | 10 to 30 s |
| 20 to 60 km | 20 to 30 s | 10 to 45 s |
| 60 to 150 km | 30 to 45 s | 15 to 60 s |
| 150 to 300 km | 45 to 60 s | 20 to 90 s |
| over 300 km | 60 to 75 s | 30 to 90 s |

Inside a band, the recommendation moves toward its upper end as the number of moments grows. **90 s
is the hard maximum**: it is the Instagram Reels limit, it keeps render time reasonable, and a longer
flyover stops being watched. When the chosen length is over 60 s, the screen notes that YouTube
Shorts allows only 60 s.

## Look

- **Terrain:** a mesh built from elevation tiles, shaded in the shader by:
  - hypsometric tint: low ground dark, high ground lighter, in the app's dark palette;
  - hillshade from the surface normals, with a fixed low sun;
  - faint contour lines every 100 m, or 50 m on gentle terrain;
  - distance fog fading into a sky gradient at the horizon.
- **Vertical exaggeration** adapts to the ride: about 1.3× in the mountains, up to 2.5× on flat rides
  so the ground still has shape. The stats always report true heights.
- **Path:** a ribbon draped on the terrain, offset a little above it. The ridden part is in the
  accent colour (the rider's bike or custom accent) with a glow. The part not yet ridden is a thin
  translucent line. GPS gaps are drawn dashed.
- **Rider marker:** a glowing comet head with a fading trail, and a small accent pin at each moment.
- **Light:** the sun position from the ride's start time and place drives the hillshade, the sky
  gradient and the fog colour. A night palette is used when the sun is below the horizon.
- **Finish:** gentle bloom on bright parts, motion blur from blended sub-frames on fast camera moves,
  then a light grain and vignette over the frame.
- **Type:** the share-card fonts (Anton for numbers, Rajdhani for labels).
- **Flat fallback:** the same style on flat ground with hillshade off, used only when the rider
  accepts it after the elevation-data warning.

## Export flow

1. Open from **Ride detail → Share → Video**, next to the share cards.
2. **Setup screen:**
   - live preview, scrubbable;
   - shape (9:16 / 16:9 / 1:1);
   - length slider with the recommended mark;
   - title (prefilled from the ride name);
   - toggles: place names, HUD, moments;
   - music: none, or pick a file, with a start-offset picker.
3. **Render:** runs in a foreground service with a progress notification and Cancel. It continues if
   the rider leaves the app.
4. **Done:** saved to `Movies/Bumblebee/` through MediaStore, then the share sheet opens. A failed
   render explains why (storage full, encoder error, cancelled) and leaves no partial file.

The last-used shape, toggles and music choice are remembered.

## Technical design

### Elevation data

- **AWS Terrain Tiles** (Mapzen "Terrarium" PNG encoding, open data on AWS), fetched per web-mercator
  tile at a zoom chosen from the ride's bounding box, typically z10 to z13. Attribution goes on the
  closing card and in `NOTICE.md`.
- Cached on disk next to the map tiles, under its own size limit, which is shown in Settings →
  Advanced alongside the tile cache.
- Two layers: a wide square base (the route's size plus 90% margin each side) so the terrain reaches
  the horizon, and a sharper layer over the route itself that blends into the base at its edge.
  Each is at most 512×512 vertices. The outer 12% of the base fades into the sky haze. Altitudes on the path come from the terrain, not GPS, so the ribbon sits on the
  ground (GPS altitude is noisy).

### Rendering and encoding

- **OpenGL ES 3.0** on an EGL context bound to a `MediaCodec` input surface. Every frame is rendered
  and given its exact presentation time, so output timing doesn't depend on render speed.
- **H.264** (High profile where available), 30 fps, about 12 Mb/s at 1080p, written with
  `MediaMuxer` to MP4. The resolution falls back to 720p if the encoder can't do 1080p.
- **Overlays** (HUD, callouts, cards) are painted with `android.graphics.Canvas` into a bitmap,
  uploaded as a texture and composited, reusing the share-card painting code and fonts. They are only
  redrawn when their content changes.
- **Audio:** the chosen file is decoded with `MediaExtractor` and `MediaCodec`, trimmed from the
  start offset, faded in over 1 s and out over 2 s, then re-encoded to AAC and muxed in.
- **Service:** foreground service type `mediaProcessing` on Android 15+, `dataSync` below. It holds a
  wake lock only while rendering and watches `PowerManager` thermal status to slow down when the
  phone gets hot.

### Camera and timing (pure Kotlin, unit-tested)

- The trimmed track is simplified and smoothed (existing `TrackSimplify`), then resampled by
  distance.
- **Time remap:** the video clock maps to route distance through an easing curve that slows down
  near moments and speeds up on long straight stretches.
- **Camera:** a chase position behind the marker along a smoothed heading. Pitch and height scale
  with how fast the route is moving on screen. Intro and finish are spline moves between key poses.
- All of this is deterministic: the same ride and settings always give the same video.

### Code layout

| Where | What |
| --- | --- |
| `core/video` (pure Kotlin, unit-tested) | Terrain: `Terrarium` decode and tile maths, `ElevationGrid` (with spike filter), `LayeredElevation` (wide base plus sharp route layer), `TerrainMesh`, `Exaggeration`, `RoutePath`, `SunPosition`. Story: `VideoLength`, `Moments`, `VideoTimeline`. Camera: `CameraDirector` with chase, hold orbit, dive and climb-away moves, speed-linked follow distance, ridge lift and time smoothing. |
| `app/video` | `DemTiles` (fetch and cache), GL renderer and shaders (following the harness shading model), `VideoEncoder` (codec, muxer, audio), `VideoExportService`, setup screen and preview. |

Terrain, story and camera share one module because the camera needs the timeline and the timeline
needs route distances; splitting them only added glue.

### Performance targets

- **Render time:** 30 s at 1080p in under 60 s on a mid-range phone (Snapdragon 7-series), and
  under 2.5 minutes for a 90 s video.
- **Preview:** at least 24 fps at 540p while scrubbing.
- **Memory:** under 200 MB peak during render.
- **APK size:** under 1 MB added (shaders and code; no bundled assets beyond what share cards use).

## Build phases

1. **Plan and terrain math** (`VideoPlan`, `core/terrain`) with tests. Still frames from the
   harness to check camera framing.
2. **Silent 9:16 render:** terrain, path and camera, then encode and save. First end-to-end video.
3. **Story:** HUD, moment callouts, intro and finish cards, privacy trim checks.
4. **Shapes and audio:** 16:9 and 1:1 framing, the music picker and audio mixing.
5. **Setup screen:** live preview, remembered settings, offline warning and flat fallback, the cache
   setting in Advanced.
6. **Polish:** thermal back-off, failure messages, render-time tuning on real phones.

## Ride Video Studio (v2 direction)

After the first export the rider asked for every creative idea, each one configurable, in one
screen that takes the settings, shows progress, then offers save, share and other sizes.

- **Layers, not a wall of switches:** style presets (Cinematic, Adventure, Data, Minimal) set
  every parameter; grouped sections (Story, Overlays, Camera and land, Trail and finish) tune them;
  any change makes the preset Custom. All values live in `VideoConfig`, clamped to safe ranges,
  stored as text, remembered between rides (minus per-ride title and length).
- **Live preview:** the export renderer itself in a `GLSurfaceView` at view size, scrubbable and
  playable, rebuilt 350 ms after the last change. Tiles stay in memory for the visit.
- **Render screen:** percentage, step, time left, Cancel, and "leave it running".
- **Result screen:** plays the video; Save to gallery, Share, another size, or back to the style.

Roadmap, each item landing with its own Studio control:

| Phase | Adds |
| --- | --- |
| A (built) | Studio, presets, live preview, render and result screens; title, story and overlay toggles; camera distance, hill height, haze, contours, trail width, glow, vignette, grain |
| B (built) | Cold-open hook with a flash cut, chapters with title cards (climb, descent, twisties, open road, after the break), map-reveal finale, a shot per moment type, banking into turns, rising over hairpins |
| C (built) | Light that follows the ride's clock with a smooth day-to-night palette and stars, drifting clouds with ground shadows, lakes, reservoirs and rivers from OpenStreetMap (plus sea from the elevation) with reflections and sun glint, towns pinned in 3D along the route |
| D (built) | The rider's bike as the marker in its paint colour, turning with the road; a cluster-style HUD (big speed, fuel bars, odometer, altitude); ride tags (ride number, weather, sunrise or sunset); ride photos as postcards, placed by their location, else their time, else by hand, with the method shown and the hidden start and finish respected |
| D+ (built) | A 3D bike as the default marker: a flat-shaded procedural adventure bike (about 1,850 triangles, built in `BikeModel`), paint from the bike profile, an optional rider whose helmet takes the accent colour. `RideVideoPlan.bikeFrame` places it on the path, points it 15 m ahead and leans it by atan(v²·κ/g), averaged over ±24 m and capped at 40°. It keeps real size up close and grows with distance so it stays about a tenth of the frame tall; it has a soft ground shadow and a headlamp glow at dusk. Marker choices: glow dot, 3D bike, bike photo |
| E (built) | **Trail colour** by speed (5th–95th percentile of the ride, blue → red) or gradient (±8% over 80 m, blue downhill → red uphill) or accent; the same ramp (`TrailRamp`) colours the ribbon shader, the elevation strip and a small key under the clock. **Kinetic type**: titles and chapter names type on letter by letter, moment values and closing stats count up. **Milestones**: round odometer distances (step chosen for one to four per ride) and first time above each 1,000 m, kept 2.5 s clear of highlights; each gets a spark burst in 3D and a big number that punches in. **Music**: one track the rider picks, copied in and analysed once (`BeatTracker`: log-energy and bass onsets, tempo by autocorrelation with a 120 BPM prior, then a joint period-and-phase fit so the grid doesn't drift, downbeats from the bass). Automatic start puts the track's biggest four-second lift in loudness (the drop) on the dive-in; with beat sync the cold open is whole beats, the intro whole bars, and the start is nudged so a bar lands exactly on the cut. Volume, fade, manual start and pulse strength are all adjustable; the trail glow, marker and a flash under the bike pulse on beats. The video renders silent, then the stretch is decoded, faded, encoded to AAC and muxed in; if that fails the silent video is kept and the rider is told. The Studio preview plays the track in sync |

## Decided in conversation

- Stylised terrain from open elevation data, not satellite imagery.
- All three shapes: 9:16, 16:9 and 1:1.
- Music is the rider's own file; silent by default; no bundled tracks.
- Rendered on the phone.

## Claude's calls (check these)

- **Elevation source:** AWS Terrain Tiles (Terrarium), which needs no key and allows sharing.
- **Video settings:** 30 fps, 1080 px short side, H.264, MP4.
- **Length:** 90 s hard maximum and the length bands in the table above.
- **Moments:** the priority list and spacing rules above.
- **Exaggeration:** adaptive, from 1.3× to 2.5×.
- **Path height:** taken from the terrain, not GPS altitude.
- **Where it lives:** Ride detail → Share → Video. Saved to `Movies/Bumblebee/`.

## Left to the builder

- Exact palette values, sun angle, fog distance and glow strength.
- Camera easing curves and chase distances per terrain type.
- Overlay layouts for each shape.
- Preview strategy (shared renderer at lower resolution, or a separate light path).
