# Ride video export: design

Date: 2026-10-25
Status: design agreed, not built

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

## The video, scene by scene

Times below are for a 30 s video and scale with the chosen length.

| Scene | Share | What happens |
| --- | --- | --- |
| **Intro** | ~12% | High overview of the whole region, route ghosted faintly. Title (ride name or auto name), date, from → to place names. Camera dives toward the start of the trimmed route. |
| **Ride** | ~70% | Chase camera above and behind the rider marker. The path draws in the accent colour with a soft glow; the route still ahead stays ghosted. The camera's time is distance-based and slows down around moments. |
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
- **Rider marker:** a glowing dot with a short fading trail, and a small accent pin at each moment.
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
- The mesh is sampled from the tiles into a grid of at most 512×512 vertices over the route's bounds
  plus a margin. Altitudes on the path come from the terrain, not GPS, so the ribbon sits on the
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
| `core/ride` `VideoPlan` | Length recommendation, moment selection, scene timeline, time remap, camera keyframes. Pure Kotlin, unit-tested. |
| new `core/terrain` | Terrarium decode, tile math, mesh build, exaggeration choice, path draping. Pure Kotlin, unit-tested. |
| `app/video` | `DemTiles` (fetch and cache), GL renderer and shaders, `VideoEncoder` (codec, muxer, audio), `VideoExportService`, setup screen and preview. |

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
