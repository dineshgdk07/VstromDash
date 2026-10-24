# Ride Share Card v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single flat share card with four selectable looks (Night Glow, Poster, Cockpit, Ride Pass), add ride naming, highlights, weather/sun, elevation, a ride-end share prompt and day-trip cards.

**Architecture:** All card decisions (headline, highlight, weather line, elevation samples, day merge, remembered settings) live as pure Kotlin in `:core:ride` and are unit-tested there. `:data:local` stores the new per-ride facts (places, weather, climb) and answers history questions (ride number, earlier rides). The app paints with `android.graphics.Canvas`. A shared pipeline draws the background (own / map / photo / transparent), then one `CardLook` per look draws everything else. A `ShareViewModel` assembles the model for a ride or a day, and a two-step Compose screen (pick → customise) replaces `ShareComposer`.

**Tech Stack:** Kotlin, JUnit4, Room + Robolectric (`@GraphicsMode(NATIVE)` for render tests), Jetpack Compose, `android.location.Geocoder`, Android notifications.

**Spec:** `docs/superpowers/specs/2026-10-24-share-card-v2-design.md`

## Global Constraints

- Sizes: Story 1080×1920, Feed 1080×1350, Square 1080×1080.
- Looks: `NIGHT_GLOW`, `POSTER`, `COCKPIT`, `RIDE_PASS`. Backgrounds: `OWN`, `MAP`, `PHOTO`, `TRANSPARENT`.
- Accents: `YELLOW #FAC816` (default), `RED_ORANGE #FF5A36`, `SKY #5EC8FF`, `GREEN #7CF29A`, `WHITE #F2F2F2`.
- Ride name: trimmed, max 30 characters, blank means no name.
- Stats: at most 3 (`ShareMetrics.MAX_STATS`).
- Headline order: ride name → "From → To" (places on and both known) → `"<Time of day> ride"` (existing `RideStory.timeOfDay`).
- `SharePrivacy` 500 m trim applies to every look, the sticker and day cards. Place names come from the trimmed route ends, locality level only.
- Climb threshold for the climb stat, climb highlight and elevation profile is 40 m.
- Sun window: ±30 minutes around local sunrise / sunset.
- Weather line format: `22°C · Clear Sky · sunrise ride`, with missing parts dropped.
- Ride pass number format: `No. 0147`.
- The share prompt never fires for demo rides or rides without a highlight. It is on by default.
- Remembered settings never include Photo, the ride name or the chosen photo.
- New fonts must be bundled OFL files in `app/src/main/res/font/`. Textures total under 300 KB.
- Room schema goes 8 → 9 with a migration. Snapshot `APP_SCHEMA` becomes 9, and schema 8 snapshots are still accepted (new fields default to null).
- No video export, no social APIs except the Instagram Stories intent for Sticker.

## Review Focus

1. **A long name or long place names** (30 chars, wide glyphs like `WWWW…`) in every look and size. Text should shrink to a floor, then ellipsize, never run off the canvas. → test in Task 7 (`ShareText.fit`) and the render matrix in Task 11.
2. **A ride with no drawable route** (under 1 km after the trim) in every look and background. It should render a clean card with no route and no crash. → render matrix in Task 11.
3. **A GPS-only ride in Cockpit.** There's no odometer or fuel, so those must be hidden rather than showing `0` or `—`. → test in Task 1 (model `odoKm == null`) and Task 10.
4. **Offline.** If Geocoder throws or the map tiles are missing, fall back to the headline, set the map-fallback flag and show the notice. → tests in Task 1 (`mapFallback`) and Task 12 (`PlaceNamer` returns null on exception).
5. **Remembered settings that no longer fit:** a remembered stat this ride lacks, a corrupt or old stored string, or Photo. Decode to safe defaults, drop unavailable stats and fall back to `OWN`. → tests in Task 1 (codec) and Task 12.

---

## File Map

| File | Responsibility |
| --- | --- |
| `core/ride/.../ride/ShareCard.kt` (modify) | v2 enums, `ShareSwitches`, `ShareCardModel`, `ShareCard.of`, `ShareCard.ofDay` |
| `core/ride/.../ride/RideNames.kt` (create) | name cleaning, headline choice, place codes |
| `core/ride/.../ride/ShareSwitchesCodec.kt` (create) | remembered settings ⇄ string |
| `core/ride/.../ride/RideHighlights.kt` (create) | badge choice against history |
| `core/ride/.../ride/SunTimes.kt` (create) | sunrise/sunset + weather line |
| `core/ride/.../ride/ShareTraces.kt` (create) | elevation profile + speed trace samples |
| `data/local/.../Entities.kt`, `Daos.kt`, `BumblebeeDb.kt`, `RideCaptureRepository.kt`, `SnapshotArchive.kt` (modify) | new columns, queries, writes, finished-ride events, snapshot fields |
| `core/snapshot/.../SnapshotModel.kt`, `SnapshotZip.kt`, `SnapshotInspect.kt` (modify) | carry new ride fields, accept schema 8 and 9 |
| `app/.../ui/sharecard/ShareCardPainter.kt` (rewrite) | background pipeline, dispatch to look |
| `app/.../ui/sharecard/ShareCardLayout.kt` (modify) | the three sizes |
| `app/.../ui/sharecard/ShareText.kt` (create) | text fitting |
| `app/.../ui/sharecard/ShareTextures.kt` (create) | grain, paper, contour lines, grid |
| `app/.../ui/sharecard/ShareCardRoute.kt` (modify) | route stroke parameterised by accent and style |
| `app/.../ui/sharecard/looks/CardLook.kt`, `NightGlowLook.kt`, `PosterLook.kt`, `CockpitLook.kt`, `RidePassLook.kt` (create) | one painter per look |
| `ShareCardChrome.kt`, `ShareCardBrush.kt` + `res/drawable/share_card_brush` (delete in Task 7) | v1 chrome |
| `app/.../ui/share/PlaceNamer.kt`, `SharePrefs.kt`, `ShareViewModel.kt`, `ShareScreen.kt`, `StickerShare.kt` (create) | the share flow |
| `app/.../ui/ride/ShareComposer.kt` (delete in Task 13) | v1 composer |
| `app/.../ui/ride/RideVisuals.kt`, `RideViewModel.kt` (modify) | rename action, open share |
| `app/.../ble/BikeBridgeService.kt` (modify) | record weather, highlight notification |
| `app/.../ui/nav/BumblebeeApp.kt` (modify) | `share:<rideId>` open route |
| `app/.../ui/explorer/ExplorerScreen.kt`, `ExplorerViewModel.kt` (modify) | Share day |
| `app/.../ui/garage/GarageSettingsScreen.kt`, `GarageSettingsViewModel.kt` (modify) | share-prompt toggle |

Paths below shorten `core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/` to `core/ride/.../`, and the app's `app/src/main/kotlin/dev/gdk/bumblebee/` to `app/.../`. Tests mirror those under `src/test/kotlin/`.

---

### Task 1: Card model v2, names, headline, remembered settings

**Files:**
- Modify: `core/ride/.../ShareCard.kt`
- Create: `core/ride/.../RideNames.kt`, `core/ride/.../ShareSwitchesCodec.kt`
- Test: `core/ride/src/test/.../ShareCardTest.kt` (extend), `RideNamesTest.kt`, `ShareSwitchesCodecTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class ShareLook { NIGHT_GLOW, POSTER, COCKPIT, RIDE_PASS }
  enum class ShareShape { STORY, FEED, SQUARE }
  enum class ShareBackground { OWN, MAP, PHOTO, TRANSPARENT }
  enum class ShareAccent(val argb: Int) { YELLOW(0xFFFAC816.toInt()), RED_ORANGE(0xFFFF5A36.toInt()), SKY(0xFF5EC8FF.toInt()), GREEN(0xFF7CF29A.toInt()), WHITE(0xFFF2F2F2.toInt()) }
  data class ShareSwitches(
      val look: ShareLook = ShareLook.NIGHT_GLOW,
      val shape: ShareShape = ShareShape.STORY,
      val background: ShareBackground = ShareBackground.OWN,
      val accent: ShareAccent = ShareAccent.YELLOW,
      val bigNumber: ShareMetricKind? = ShareMetricKind.DISTANCE,
      val stats: List<ShareMetricKind>? = null,
      val showPlaces: Boolean = true,
      val showHighlight: Boolean = true,
      val showWeather: Boolean = true,
  )
  data class ShareExtras(            // facts the caller looked up; all optional
      val rideName: String? = null,
      val startPlace: String? = null,
      val endPlace: String? = null,
      val rideNumber: Int? = null,
      val highlight: Highlight? = null,      // Task 2
      val weatherLine: String? = null,       // Task 3
      val odoEndKm: Int? = null,
      val gpsOnly: Boolean = false,
  )
  // ShareCardModel gains: look, accent, headline: String, fromCode/toCode/fromPlace/toPlace: String?,
  // rideNumberText: String?, highlight: Highlight?, weatherLine: String?, elevation: List<Float>,
  // speedTrace: List<Float>, odoKm: Int?, stops: List<GpsFix>, mapFallback: Boolean.
  // It keeps sentence, dateText, timeOfDay, available, hero, stats, plot, route, demo, needsTiles.
  // `bikeName` is removed (the spec's bug fix). The old `style` field stays until Task 11 deletes it.
  fun ShareCard.of(ride: RideInsightInput, track: List<GpsFix> = emptyList(), switches: ShareSwitches = ShareSwitches(),
                   climbM: Double? = null, tankKmPerL: Double? = null, extras: ShareExtras = ShareExtras(),
                   demo: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): ShareCardModel
  object RideNames {
      const val MAX_LEN = 30
      fun clean(input: String?): String?                   // trim, collapse inner whitespace, cut to 30, blank → null
      fun headline(name: String?, from: String?, to: String?, showPlaces: Boolean, timeOfDay: String): String
      fun code(locality: String): String                   // first 3 letters (A–Z after removing non-letters), uppercased
  }
  object ShareSwitchesCodec {
      fun encode(s: ShareSwitches): String                 // JSON via org.json is not on :core:ride; use "key=value;" pairs
      fun decode(raw: String?, available: List<ShareMetricKind>): ShareSwitches
  }
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
// RideNamesTest
@Test fun `clean trims, collapses and caps at 30`() {
    assertEquals("Nandi Hills sunrise", RideNames.clean("  Nandi   Hills sunrise "))
    assertEquals(30, RideNames.clean("x".repeat(45))!!.length)
    assertNull(RideNames.clean("   "))
}
@Test fun `headline prefers name, then places, then time of day`() {
    assertEquals("Nandi run", RideNames.headline("Nandi run", "Bengaluru", "Nandi Hills", true, "Morning"))
    assertEquals("Bengaluru → Nandi Hills", RideNames.headline(null, "Bengaluru", "Nandi Hills", true, "Morning"))
    assertEquals("Morning ride", RideNames.headline(null, "Bengaluru", "Nandi Hills", false, "Morning"))
    assertEquals("Morning ride", RideNames.headline(null, "Bengaluru", null, true, "Morning"))
}
@Test fun `code is first three letters uppercased`() {
    assertEquals("BEN", RideNames.code("Bengaluru"))
    assertEquals("STT", RideNames.code("St. Thomas"))
}

// ShareSwitchesCodecTest
@Test fun `round trips every field except photo`() {
    val s = ShareSwitches(ShareLook.POSTER, ShareShape.FEED, ShareBackground.MAP, ShareAccent.SKY,
        ShareMetricKind.MAX_SPEED, listOf(ShareMetricKind.DISTANCE, ShareMetricKind.MOVING), false, true, false)
    assertEquals(s, ShareSwitchesCodec.decode(ShareSwitchesCodec.encode(s), ShareMetricKind.values().toList()))
}
@Test fun `photo is never remembered`() {
    val raw = ShareSwitchesCodec.encode(ShareSwitches(background = ShareBackground.PHOTO))
    assertEquals(ShareBackground.OWN, ShareSwitchesCodec.decode(raw, emptyList()).background)
}
@Test fun `garbage and null decode to defaults`() {
    assertEquals(ShareSwitches(), ShareSwitchesCodec.decode("%%%;look=NOPE", emptyList()))
    assertEquals(ShareSwitches(), ShareSwitchesCodec.decode(null, emptyList()))
}
@Test fun `stats this ride lacks are dropped`() {
    val raw = ShareSwitchesCodec.encode(ShareSwitches(stats = listOf(ShareMetricKind.FUEL_ECONOMY, ShareMetricKind.DISTANCE)))
    assertEquals(listOf(ShareMetricKind.DISTANCE), ShareSwitchesCodec.decode(raw, listOf(ShareMetricKind.DISTANCE)).stats)
}

// ShareCardTest additions
@Test fun `headline uses the ride name and never puts it in the bike slot`() {
    val card = ShareCard.of(ride(), extras = ShareExtras(rideName = "Nandi run"), zone = zone)
    assertEquals("Nandi run", card.headline)
}
@Test fun `place codes and ride number are filled for ride pass`() {
    val card = ShareCard.of(ride(), extras = ShareExtras(startPlace = "Bengaluru", endPlace = "Nandi Hills", rideNumber = 147), zone = zone)
    assertEquals("BEN", card.fromCode); assertEquals("NAN", card.toCode); assertEquals("No. 0147", card.rideNumberText)
}
@Test fun `places off hides place fields`() {
    val card = ShareCard.of(ride(), switches = ShareSwitches(showPlaces = false),
        extras = ShareExtras(startPlace = "Bengaluru", endPlace = "Nandi Hills"), zone = zone)
    assertNull(card.fromPlace); assertNull(card.fromCode)
}
@Test fun `gps only ride has no odometer`() {
    val card = ShareCard.of(ride(), extras = ShareExtras(odoEndKm = 24816, gpsOnly = true), zone = zone)
    assertNull(card.odoKm)
}
@Test fun `missing tiles flag a map fallback instead of silently switching`() {
    val card = ShareCard.of(ride(km = 20.0), track = eastbound(20_000.0), switches = ShareSwitches(background = ShareBackground.MAP), zone = zone)
        .withTiles(complete = false)
    assertTrue(card.mapFallback); assertEquals(ShareBackground.OWN, card.background); assertEquals(ShareLook.NIGHT_GLOW, card.look)
}
@Test fun `feed shape is carried`() {
    assertEquals(ShareShape.FEED, ShareCard.of(ride(), switches = ShareSwitches(shape = ShareShape.FEED), zone = zone).shape)
}
```
Add an `eastbound(meters)` helper to `ShareCardTest` if it doesn't have one (copy the one in `app/src/test/.../ShareCardRenderTest.kt`). Update existing `ShareCardTest` cases that used `ShareStyle` / `ShareBackground.BLACK` to `ShareBackground.OWN`.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :core:ride:test --tests 'dev.gdk.bumblebee.ride.RideNamesTest' --tests 'dev.gdk.bumblebee.ride.ShareSwitchesCodecTest' --tests 'dev.gdk.bumblebee.ride.ShareCardTest'`
Expected: compilation failure (unresolved `ShareLook`, `RideNames`, …).

- [ ] **Step 3: Implement the types and functions above.** `ShareCard.of` rules:
  - `background = MAP` with no drawable route → `OWN`.
  - `withTiles(false)` → `background = OWN`, `mapFallback = true`, `needsTiles = false`. The look is unchanged.
  - `odoKm = extras.odoEndKm.takeUnless { extras.gpsOnly }`.
  - `elevation` and `speedTrace` stay `emptyList()` until Task 4.
  - `stops` stays empty until Task 5.
  - To keep the painter compiling until Task 11, keep the `ShareStyle` enum and a deprecated `style` field derived from the background: `MAP` → `ShareStyle.MAP`, else `INK`.
  - In `app/.../ui/ride/ShareComposer.kt`, replace `ShareBackground.BLACK` with `OWN` and stop passing `bikeName` (minimum edits to compile).

- [ ] **Step 4: Run tests to verify they pass**

Run: same command as Step 2, then `./gradlew :app:compileDebugKotlin`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/ride app/src/main/kotlin/dev/gdk/bumblebee/ui/ride/ShareComposer.kt app/src/main/kotlin/dev/gdk/bumblebee/ui/sharecard
git commit -m "feat(share): v2 card model with looks, accents, headline and remembered settings"
```

---

### Task 2: Highlights

**Files:**
- Create: `core/ride/.../RideHighlights.kt`
- Test: `core/ride/src/test/.../RideHighlightsTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class HighlightKind { CLUB_300, CLUB_200, CLUB_100, LONGEST, TOP_SPEED, BIGGEST_CLIMB, LONGEST_SADDLE }
  data class Highlight(val kind: HighlightKind, val title: String, val detail: String)
  data class HighlightStats(val km: Double, val maxSpeedKmh: Int, val climbM: Double?, val movingSec: Int)
  object RideHighlights { fun of(ride: HighlightStats, earlier: List<HighlightStats>): Highlight? }
  ```
- Priority (plan decision, deviating from the spec order; see the handoff note): clubs first (highest crossed), then longest, top speed, biggest climb (only if ≥ 40 m), longest saddle. Under the spec's order a club badge could never show, because the first ride over 100 km is always also the longest ride yet.
- Copy:
  - Titles: `"300 km club"`, `"200 km club"`, `"100 km club"`, `"Longest ride yet"`, `"New top speed"`, `"Biggest climb yet"`, `"Longest time in the saddle"`.
  - Detail: `"New top speed: 112 km/h"`, `"Longest ride yet: 86.4 km"`, `"Biggest climb yet: 640 m"`, `"Longest time in the saddle: 3h 05m"`, `"100 km club: 104.2 km"`. Use `ShareMetrics`' clock format for time.

- [ ] **Step 1: Write the failing tests**

```kotlin
private fun s(km: Double = 10.0, max: Int = 60, climb: Double? = null, moving: Int = 1200) = HighlightStats(km, max, climb, moving)
@Test fun `first ever ride gets nothing`() = assertNull(RideHighlights.of(s(km = 150.0), emptyList()))
@Test fun `first time over 100 km is a club badge, not longest`() =
    assertEquals(HighlightKind.CLUB_100, RideHighlights.of(s(km = 104.2), listOf(s(km = 80.0)))?.kind)
@Test fun `club badge only the first time`() =
    assertEquals(HighlightKind.LONGEST, RideHighlights.of(s(km = 140.0), listOf(s(km = 120.0)))?.kind)
@Test fun `crossing two thresholds at once shows the higher`() =
    assertEquals(HighlightKind.CLUB_200, RideHighlights.of(s(km = 210.0), listOf(s(km = 90.0)))?.kind)
@Test fun `top speed when not longest`() {
    val h = RideHighlights.of(s(km = 10.0, max = 112), listOf(s(km = 50.0, max = 104)))!!
    assertEquals(HighlightKind.TOP_SPEED, h.kind); assertEquals("New top speed: 112 km/h", h.detail)
}
@Test fun `climb under 40 m never counts`() =
    assertNull(RideHighlights.of(s(km = 5.0, max = 40, climb = 39.0, moving = 100), listOf(s(climb = 10.0))))
@Test fun `ties are not records`() = assertNull(RideHighlights.of(s(), listOf(s())))
```

- [ ] **Step 2: Run to verify they fail.** Run: `./gradlew :core:ride:test --tests 'dev.gdk.bumblebee.ride.RideHighlightsTest'`. Expected: compilation failure.
- [ ] **Step 3: Implement `RideHighlights.of`.** A record means strictly greater than the max over `earlier`. Then wire `ShareExtras.highlight` → `ShareCardModel.highlight`, set to null when `switches.showHighlight` is false.
- [ ] **Step 4: Run tests to verify they pass.** Same command plus `ShareCardTest`. Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(share): ride highlight badges"`

---

### Task 3: Sun times and weather line

**Files:**
- Create: `core/ride/.../SunTimes.kt`
- Test: `core/ride/src/test/.../SunTimesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class SunDay(val sunriseTs: Long?, val sunsetTs: Long?)   // null in polar day/night
  object SunTimes {
      fun of(date: LocalDate, lat: Double, lng: Double, zone: ZoneId): SunDay   // NOAA solar position, zenith 90.833°
      fun tag(startTs: Long, endTs: Long, sun: SunDay): String?                  // "sunrise ride" | "sunset ride" | null, ±30 min overlap; sunrise wins
      fun weatherLine(tempC: Double?, label: String?, sunTag: String?): String?  // "22°C · Clear Sky · sunrise ride", parts dropped, null if all missing
  }
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
private val ist = ZoneId.of("Asia/Kolkata")
@Test fun `bengaluru sunrise and sunset within 3 minutes of published times`() {
    val d = SunTimes.of(LocalDate.of(2026, 10, 24), 12.9716, 77.5946, ist)
    assertNear(LocalTime.of(6, 8), d.sunriseTs!!, ist, 3)   // reference: timeanddate.com, Bengaluru 24 Oct
    assertNear(LocalTime.of(17, 59), d.sunsetTs!!, ist, 3)
}
@Test fun `ride overlapping sunrise window is tagged`() {
    val sun = SunDay(sunriseTs = 1_000_000_000L, sunsetTs = null)
    assertEquals("sunrise ride", SunTimes.tag(sun.sunriseTs!! + 25 * 60_000, sun.sunriseTs!! + 90 * 60_000, sun))
    assertNull(SunTimes.tag(sun.sunriseTs!! + 31 * 60_000, sun.sunriseTs!! + 90 * 60_000, sun))
}
@Test fun `weather line drops missing parts`() {
    assertEquals("22°C · Clear Sky · sunrise ride", SunTimes.weatherLine(21.6, "Clear Sky", "sunrise ride"))
    assertEquals("Clear Sky", SunTimes.weatherLine(null, "Clear Sky", null))
    assertNull(SunTimes.weatherLine(null, null, null))
}
```
Before relying on the 06:08 / 17:59 reference values, check them against an online sunrise table for that date. If they differ, use the table's values.

- [ ] **Step 2: Run to verify they fail.** Run: `./gradlew :core:ride:test --tests 'dev.gdk.bumblebee.ride.SunTimesTest'`.
- [ ] **Step 3: Implement.** Use the standard NOAA sunrise equation (day of year → fractional year → equation of time and declination → hour angle at zenith 90.833°). Round the temperature to the nearest whole degree. Wire `ShareExtras.weatherLine` → `ShareCardModel.weatherLine`, set to null when `showWeather` is false.
- [ ] **Step 4: Run tests to verify they pass.**
- [ ] **Step 5: Commit** — `git commit -m "feat(share): sunrise/sunset tag and weather line"`

---

### Task 4: Elevation profile and speed trace samples

**Files:**
- Create: `core/ride/.../ShareTraces.kt`
- Modify: `core/ride/.../ShareCard.kt` (fill `elevation`, `speedTrace`)
- Test: `core/ride/src/test/.../ShareTracesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object ShareTraces {
      const val SAMPLES = 64
      fun elevation(route: List<GpsFix>, climbM: Double?): List<Float>  // empty if climbM == null || < 40 or < 4 altitudes; else SAMPLES values 0..1 by distance, moving-average window 5
      fun speed(route: List<GpsFix>): List<Float>                       // SAMPLES values 0..1 (÷ peak), by time; empty if < 2 fixes
  }
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `flat ride has no elevation profile`() = assertTrue(ShareTraces.elevation(climbTrack(gainM = 20.0), climbM = 20.0).isEmpty())
@Test fun `climb gives 64 normalised samples ending high`() {
    val e = ShareTraces.elevation(climbTrack(gainM = 600.0), climbM = 600.0)
    assertEquals(64, e.size); assertTrue(e.all { it in 0f..1f }); assertTrue(e.last() > 0.9f)
}
@Test fun `speed trace is normalised to the peak`() {
    val t = ShareTraces.speed(trackWithSpeeds(listOf(5f, 10f, 20f, 10f)))
    assertEquals(64, t.size); assertEquals(1f, t.max(), 0.001f)
}
```

- [ ] **Step 2: Run to verify they fail.** Run: `./gradlew :core:ride:test --tests 'dev.gdk.bumblebee.ride.ShareTracesTest'`.
- [ ] **Step 3: Implement.** Resample by linear interpolation along cumulative distance (elevation) or time (speed). Set `ShareCardModel.elevation = ShareTraces.elevation(route, climbM)` and `speedTrace = ShareTraces.speed(route)` from the privacy-trimmed `route`.
- [ ] **Step 4: Run tests to verify they pass.**
- [ ] **Step 5: Commit** — `git commit -m "feat(share): elevation and speed trace samples"`

---

### Task 5: Day-trip card model

**Files:**
- Modify: `core/ride/.../ShareCard.kt`
- Test: `core/ride/src/test/.../ShareCardTest.kt` (add day cases)

**Interfaces:**
- Consumes: Task 1 types.
- Produces:
  ```kotlin
  data class DayRide(val input: RideInsightInput, val track: List<GpsFix>, val climbM: Double?, val segmentBreaks: List<Long> = emptyList())
  fun ShareCard.ofDay(date: LocalDate, rides: List<DayRide>, switches: ShareSwitches = ShareSwitches(),
                      dayName: String? = null, zone: ZoneId = ZoneId.systemDefault()): ShareCardModel
  // Also: ShareCard.of(...) fills `stops` from a new optional `segmentBreaks: List<Long>` parameter (merged rides).
  ```
- Rules:
  - Each ride's track is trimmed separately, then concatenated in start order.
  - `stops` is the first fix of each ride after the first, plus the fix nearest each segment-break timestamp.
  - Sums: km, movingSec. Max: maxSpeedKmh. Climb: the sum of the non-null climbs.
  - Headline: `RideNames.clean(dayName)` or else `"SAT, 24 OCT"`. Use the date format already used by `dateText`, without the year.
  - No ride number, highlight or places.
  - The sentence is `"<n> rides · <km> km"`, e.g. `3 rides · 142.6 km`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `day card sums rides and marks stops between them`() {
    val a = DayRide(ride(km = 40.0, movingSec = 3600, max = 90), eastbound(40_000.0), 50.0)
    val b = DayRide(ride(km = 30.0, movingSec = 2400, max = 104), eastbound(30_000.0, startLng = 1.0), 30.0)
    val card = ShareCard.ofDay(LocalDate.of(2026, 10, 24), listOf(a, b), zone = zone)
    assertEquals("70.0", card.stats.first { it.kind == ShareMetricKind.DISTANCE }.value)
    assertEquals(1, card.stops.size); assertEquals("2 rides · 70.0 km", card.sentence)
    assertEquals("SAT, 24 OCT", card.headline); assertNull(card.rideNumberText)
}
@Test fun `day name overrides date headline`() =
    assertEquals("Coorg loop", ShareCard.ofDay(LocalDate.of(2026, 10, 24), listOf(dayRide(), dayRide()), dayName = " Coorg loop ", zone = zone).headline)
@Test fun `merged ride segment breaks become stops`() {
    val track = eastbound(40_000.0)
    val card = ShareCard.of(ride(km = 40.0), track = track, segmentBreaks = listOf(track[track.size / 2].ts), zone = zone)
    assertEquals(1, card.stops.size)
}
```

- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement `ofDay` and `segmentBreaks`.** Reuse the private `trim`, `plot` and `ShareMetrics.of`, with a synthetic `RideInsightInput` built from the sums (start = first start, end = last end).
- [ ] **Step 4: Run tests to verify they pass.** Run: `./gradlew :core:ride:test`. Expected: whole module PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(share): day-trip card model and stop markers"`

---

### Task 6: Storage — places, weather, climb, history queries, finished events, snapshot

**Files:**
- Modify: `data/local/.../Entities.kt`, `Daos.kt`, `BumblebeeDb.kt` (version 9, `MIGRATION_8_9`), `RideCaptureRepository.kt`, `SnapshotArchive.kt`
- Modify: `core/snapshot/.../SnapshotModel.kt` (`APP_SCHEMA = 9`, ride fields), `SnapshotZip.kt` (write and read with `opt*`), `SnapshotInspect.kt` (accept 8..9)
- Test: `data/local/src/test/.../RideCaptureRepositoryTest.kt`, new `Migration8to9Test.kt`, `core/snapshot/src/test/.../SnapshotInspectTest.kt`, `SnapshotZipTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  // RideEntity new nullable columns: startPlace: String?, endPlace: String?, weatherTempC: Double?, weatherCode: Int?, climbM: Double?
  // RideDao:
  @Query("SELECT COUNT(*) FROM rides WHERE endTs IS NOT NULL AND mergedIntoId IS NULL AND isDemo = 0 AND startTs <= :startTs")
  suspend fun rideNumber(startTs: Long): Int
  @Query("SELECT * FROM rides WHERE endTs IS NOT NULL AND mergedIntoId IS NULL AND isDemo = 0 AND startTs < :startTs")
  suspend fun finishedBefore(startTs: Long): List<RideEntity>
  @Query("SELECT * FROM rides WHERE endTs IS NOT NULL AND mergedIntoId IS NULL AND isDemo = 0 AND startTs >= :fromTs AND startTs < :toTs ORDER BY startTs")
  suspend fun finishedBetween(fromTs: Long, toTs: Long): List<RideEntity>
  // RideCaptureRepository:
  suspend fun renameRide(id: Long, name: String?)                         // stores RideNames.clean(name); keeps tags (wraps setRideMeta)
  suspend fun setPlaces(id: Long, start: String?, end: String?)
  suspend fun recordWeatherIfMissing(tempC: Double, code: Int)            // active ride only; no-op if already set or no ride
  suspend fun rideNumber(id: Long): Int?
  suspend fun highlightFor(id: Long): Highlight?                          // backfills climbM lazily for this ride and earlier rides via TrackInsights.elevationGainM
  suspend fun ridesOnDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<RideEntity>
  val finishedEvents: SharedFlow<Long>                                    // ride id, emitted after a non-empty, non-demo ride is saved in finishRideUnlocked
  ```
  `finishRideUnlocked` also stores `climbM` at finish. Use `MutableSharedFlow(extraBufferCapacity = 8)` with `tryEmit`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// RideCaptureRepositoryTest
@Test fun `rename cleans and clears`() = runTest {
    val id = finishedRide(startTs = 0L)
    repo.renameRide(id, "  Nandi   run  "); assertEquals("Nandi run", repo.rideById(id)!!.name)
    repo.renameRide(id, " "); assertNull(repo.rideById(id)!!.name)
}
@Test fun `weather is recorded once on the active ride`() = runTest {
    val id = repo.startRide(0L)
    repo.recordWeatherIfMissing(22.0, 0); repo.recordWeatherIfMissing(30.0, 61)
    assertEquals(22.0, repo.rideById(id)!!.weatherTempC!!, 0.0); assertEquals(0, repo.rideById(id)!!.weatherCode)
}
@Test fun `ride number counts finished real rides up to this one`() = runTest {
    finishedRide(startTs = 0L); demoRide(startTs = 1_000L); val third = finishedRide(startTs = 2_000L)
    assertEquals(2, repo.rideNumber(third))
}
@Test fun `finished events fire for real rides only`() = runTest {
    val seen = mutableListOf<Long>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { repo.finishedEvents.toList(seen) }
    val real = finishedRide(startTs = 0L); demoRide(startTs = 5_000L)
    assertEquals(listOf(real), seen); job.cancel()
}
@Test fun `highlight compares against earlier rides`() = runTest {
    finishedRide(startTs = 0L, maxSpeed = 90); val fast = finishedRide(startTs = 10_000L, maxSpeed = 112)
    assertEquals(HighlightKind.TOP_SPEED, repo.highlightFor(fast)?.kind)
}
// Migration8to9Test: create v8 DB with one ride row, migrate, assert new columns exist and are null and old fields intact.
// SnapshotInspectTest: schema 8 manifest is no longer rejected; schema 10 is rejected as newer.
// SnapshotZipTest: ride with startPlace/weather round-trips; a v8 ride JSON without those keys reads them as null.
```
`finishedRide` / `demoRide` are small test helpers. Each starts a ride, feeds two telemetry frames so the ride isn't empty, and finishes it. Write them in the test file.

- [ ] **Step 2: Run to verify they fail.**
  Run: `./gradlew :data:local:testDebugUnitTest --tests '*RideCaptureRepositoryTest' --tests '*Migration8to9Test' :core:snapshot:test`
  Expected: compilation failure.
- [ ] **Step 3: Implement.**
  - Migration: five `ALTER TABLE rides ADD COLUMN …` statements, `REAL` / `INTEGER` / `TEXT`, all nullable.
  - Update `SnapshotArchive` ride mapping (`toSnapshot` / back) with the five fields.
  - Update the Room schema JSON export if the module exports schemas: check `room.schemaLocation` in `data/local/build.gradle.kts`.
- [ ] **Step 4: Run tests to verify they pass.** Same command, plus `./gradlew :data:local:testDebugUnitTest :core:snapshot:test`. Expected: all PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(data): ride places, weather, climb, history queries and finished events"`

---

### Task 7: Paint pipeline, sizes, fonts, textures, text fitting

**Files:**
- Modify: `app/.../ui/sharecard/ShareCardLayout.kt`, `ShareCardPainter.kt`, `ShareCardRoute.kt`
- Create: `app/.../ui/sharecard/ShareText.kt`, `ShareTextures.kt`, `looks/CardLook.kt`
- Add fonts: `app/src/main/res/font/fraunces_extrabold.ttf`, `jetbrains_mono_medium.ttf` (OFL, from `github.com/google/fonts` `ofl/fraunces` and `ofl/jetbrainsmono`; static instances)
- Test: `app/src/test/.../ui/sharecard/ShareTextTest.kt`, update `ShareCardRenderTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class ShareCardLayout(val width: Float, val height: Float) {   // v1 position fields removed; looks lay out from width/height
      val shape: ShareShape
      companion object { fun forShape(shape: ShareShape): ShareCardLayout }  // 1080×1920 / 1080×1350 / 1080×1080
  }
  data class CardFonts(val body: Typeface, val display: Typeface, val script: Typeface, val serif: Typeface, val mono: Typeface)
  class LookContext(val context: Context, val card: ShareCardModel, val layout: ShareCardLayout, val fonts: CardFonts,
                    val accent: Int, val background: ShareBackground)  // background after fallback
  interface CardLook {
      fun drawOwnBackground(canvas: Canvas, ctx: LookContext)
      fun drawScrim(canvas: Canvas, ctx: LookContext)       // called on MAP and PHOTO only, for legibility
      fun draw(canvas: Canvas, ctx: LookContext)            // route, type, stats, badges; must handle card.plot/route empty
      fun drawSticker(canvas: Canvas, ctx: LookContext)     // route + stats only, transparent canvas
  }
  object ShareCardPainter {
      fun paint(context: Context, model: ShareCardModel, photo: Bitmap?, tiles: Map<TileId, Bitmap>?, sticker: Boolean = false): Bitmap
      fun lookFor(look: ShareLook): CardLook
      // decodePhoto / writePng unchanged
  }
  object ShareText {
      fun fit(paint: TextPaint, text: String, maxWidth: Float, minSize: Float): String  // shrinks textSize down to minSize, then ellipsizes; mutates paint.textSize
  }
  object ShareTextures {
      fun grain(width: Int, height: Int, seed: Long = 7L): Bitmap        // cached per size, procedural noise
      fun paper(width: Int, height: Int): Bitmap
      fun contours(canvas: Canvas, rect: RectF, color: Int, seed: Long)  // procedural offset sine bands
      fun grid(canvas: Canvas, rect: RectF, step: Float, color: Int)
  }
  object ShareCardRoute {
      fun drawRoute(canvas: Canvas, points: List<PointF>, accent: Int, style: RouteStyle)  // RouteStyle { GLOW, INK, THIN }
      fun project(card: ShareCardModel, cover: TileCover?, stage: RectF): List<PointF>     // plot or map projection, reusing ShareCardMapTransform
      fun drawStops(canvas: Canvas, points: List<PointF>, accent: Int)
  }
  ```
- Painter order:
  1. Create the bitmap.
  2. Draw the background:
     - `OWN` → `look.drawOwnBackground`.
     - `MAP` with a complete mosaic → map tiles (existing stitch, dim, fade), then `drawScrim`.
     - `PHOTO` → cover-fit photo, then `drawScrim`.
     - `TRANSPARENT` → nothing.
  3. `look.draw`. When `sticker` is true, the canvas stays transparent and only `drawSticker` runs.
- Until Tasks 8–11 land, `lookFor` returns a `PlaceholderLook`. It draws only the route (`GLOW`) and the headline, so the app keeps working.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ShareTextTest (Robolectric, NATIVE graphics)
@Test fun `long wide name shrinks then ellipsizes within width`() {
    val p = TextPaint().apply { textSize = 120f }
    val out = ShareText.fit(p, "W".repeat(30), maxWidth = 900f, minSize = 48f)
    assertTrue(p.measureText(out) <= 900f); assertEquals(48f, p.textSize); assertTrue(out.endsWith("…"))
}
@Test fun `short text keeps its size`() {
    val p = TextPaint().apply { textSize = 120f }
    assertEquals("NANDI", ShareText.fit(p, "NANDI", 900f, 48f)); assertEquals(120f, p.textSize)
}
// ShareCardRenderTest: replace v1 cases with
@Test fun `every shape renders at its exact size`() =
    ShareShape.values().forEach { shape ->
        val bmp = ShareCardPainter.paint(ctx, card(shape = shape), null, null)
        val l = ShareCardLayout.forShape(shape); assertEquals(l.width.toInt(), bmp.width); assertEquals(l.height.toInt(), bmp.height)
    }
@Test fun `transparent leaves corners clear`() =
    assertEquals(0, Color.alpha(ShareCardPainter.paint(ctx, card(background = ShareBackground.TRANSPARENT), null, null).getPixel(2, 2)))
```

- [ ] **Step 2: Run to verify they fail.** Run: `./gradlew :app:testDebugUnitTest --tests '*ShareTextTest' --tests '*ShareCardRenderTest'`
- [ ] **Step 3: Implement the interfaces above and the placeholder look.**
  - Delete `ShareCardChrome.kt`, `ShareCardBrush.kt` and `res/drawable/share_card_brush.*` here. They depend on the removed v1 layout fields. Move `CardFonts` into `ShareText.kt`.
  - `ShareCardMapTransform` takes a `portrait` flag. Pass `layout.height > layout.width`, so Feed counts as portrait.
  - Update `ShareComposer.kt` to the new `paint` signature.
- [ ] **Step 4: Run tests to verify they pass.** Then `./gradlew :app:assembleDebug`. Expected: PASS, BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `git commit -m "feat(share): paint pipeline, three sizes, fonts, textures and text fitting"`

---

### Tasks 8–11: The four looks

Each look task has the same shape. The look's spec paragraph (spec "Goals" item 1) and the brainstorm mockup (`.superpowers/brainstorm/1045693-1792839628/content/styles.html`) describe the look. Exact layout per size is the builder's call. Every look must:

- lay out for all three sizes from `layout.width/height`;
- fit the headline with `ShareText.fit` (min size 48 px for Story, 40 for Feed and Square);
- draw at most 3 stats from `card.stats`, plus the hero when `card.hero != null`;
- draw `card.highlight`, `card.weatherLine`, `card.elevation` and `card.stops` when present (the placement per look is below);
- draw nothing route-related when `card.plot`/`card.route` is empty, so the layout still looks complete;
- implement `drawScrim` so white text is legible over a bright photo;
- implement `drawSticker` (route + stats, no background, no headline).

Each look's test lives in `app/src/test/.../ui/sharecard/looks/<Look>Test.kt` (Robolectric, NATIVE):

```kotlin
@Test fun `renders every shape and background without throwing`() =
    ShareShape.values().forEach { shape -> ShareBackground.values().forEach { bg ->
        ShareCardPainter.paint(ctx, card(look = LOOK, shape = shape, background = bg), photo = solid(Color.WHITE), tiles = null)
    } }
@Test fun `no route still draws headline area`() {   // pixel in headline band is not background colour
    val bmp = ShareCardPainter.paint(ctx, card(look = LOOK, track = emptyList()), null, null)
    assertTrue(bandHasInk(bmp, HEADLINE_BAND))
}
@Test fun `own background is opaque`() =
    assertEquals(255, Color.alpha(ShareCardPainter.paint(ctx, card(look = LOOK), null, null).getPixel(2, 2)))
```
Put the shared helpers (`card(...)`, `solid`, `bandHasInk`) in `app/src/test/.../ui/sharecard/ShareTestCards.kt`, created in Task 8. Each look defines its `HEADLINE_BAND` rect per its layout.

Each look task: write the test → run (fails, `lookFor` still placeholder) → implement → run (pass) → render one PNG per shape to `build/share-preview/<look>-<shape>.png` from the test (behind a `-Dshare.preview=true` system property) and eyeball them → commit. Task 8 forwards the property to the test JVM: in `app/build.gradle.kts` `testOptions.unitTests.all { it.systemProperty("share.preview", System.getProperty("share.preview") ?: "false") }`.

**Task 8: Night Glow** (`looks/NightGlowLook.kt`)
- **Own background:**
  - a radial gradient centred about (0.7w, 0.3h): `#2A2208` → `#120F06` (35%) → `#060608`, with the warm stop tinted toward the accent at 20%;
  - `ShareTextures.contours` in the accent at about 8% alpha;
  - a grain overlay at about 18% alpha.
- **Route:** `RouteStyle.GLOW` in the accent, with white start and accent end markers.
- **Headline:** Anton.
- **Weather line:** muted under the headline.
- **Highlight:** an accent pill above the headline.
- **Stats:** a frosted panel (white at 6%, accent border at 25%) at the bottom.
- **Elevation:** a faint silhouette at the bottom of the route stage.
- **Scrim:** a bottom-up black gradient over 55% of the height.
- Commit: `feat(share): Night Glow look`

**Task 9: Poster** (`looks/PosterLook.kt`)
- **Own background:** cream `#F4ECD8` + `ShareTextures.paper`. Ink is `#141414`.
- **Route:** `RouteStyle.INK`, with a hollow start dot and an accent-filled end dot.
- **Hero:** the hero number in `serif` at about 0.11 × height (Story), with its unit label in Rajdhani.
- **Stats band:** a solid accent band at the bottom, about 0.07h, with 3 stats in `#141414`.
- **Elevation:** a silhouette just above the band.
- **Place line:** "From → To · date" right-aligned.
- **Highlight:** a rotated accent stamp.
- **Text colours on Map/Photo/Transparent:** white ink with shadow on Map and Photo; `#141414` on Transparent. The band always stays.
- Commit: `feat(share): Poster look`

**Task 10: Cockpit** (`looks/CockpitLook.kt`)
- **Own background:** a vertical gradient `#0B1016` → `#05070A` + `ShareTextures.grid` (step 18 px × scale, `#50A0DC` at 7%).
- **Route:** `RouteStyle.THIN` in `ShareAccent.SKY` when the accent is YELLOW; otherwise in the accent. The dial and trace use YELLOW-or-accent.
- **Dial:** a speed dial arc showing `card.hero` when it is MAX_SPEED, otherwise max speed from `available`.
- **Odometer:** boxed digits from `card.odoKm`, hidden when null.
- **Speed trace:** a polyline strip from `card.speedTrace` along the bottom. The elevation trace sits beside it when present.
- **Stats:** in `mono`. Fuel stats are hidden when `card.odoKm == null` (GPS-only).
- **Highlight:** a `● REC`-style accent tag at the top.
- Extra test: `gps-only card draws no odometer boxes` (sample the odometer rect: it is background coloured).
- Commit: `feat(share): Cockpit look`

**Task 11: Ride Pass** (`looks/RidePassLook.kt`) + v1 removal
- **Own background:** `#1B1B1F`.
- **Ticket:** a rounded accent ticket inset by 4% of the width.
- **Header:** `Ride pass` and `card.rideNumberText`.
- **From/to:** the codes (Anton) with the place names under them. When the codes are null, show the headline as the ticket title instead.
- **Route:** `RouteStyle.INK` inside the ticket.
- **Tear line:** dashed at about 62% of the ticket height, with side notches cut in the background colour. On Transparent, punch the notches with `PorterDuff.Mode.CLEAR`.
- **Stub:** distance, moving, date, then the third stat. A decorative barcode at the bottom.
- **Highlight:** a circular rubber stamp over the ticket's top-right.
- **Weather:** a small line in the stub.
- **On Map/Photo:** the ticket floats over the image with a drop shadow.
- **v1 removal:**
  - Delete `ShareStyle` and the deprecated `style` field.
  - Delete the placeholder look.
  - Remove the related v1 tests.
- **Render matrix:** add the cross-look test to `ShareCardRenderTest`. For all 4 looks × 3 shapes × 4 backgrounds, render with a 30-char `W` name and with an empty track. Both must render without throwing.
- Commit: `feat(share): Ride Pass look; remove v1 chrome`

---

### Task 12: Share data layer — places, prefs, view model

**Files:**
- Create: `app/.../ui/share/PlaceNamer.kt`, `SharePrefs.kt`, `ShareViewModel.kt`
- Test: `app/src/test/.../ui/share/PlaceNamerTest.kt`, `SharePrefsTest.kt`, `ShareViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1 (`ShareCard.of`, `ShareExtras`, `ShareSwitchesCodec`), Task 2 (`Highlight`), Task 3 (`SunTimes`), Task 5 (`ofDay`, `DayRide`), Task 6 repository methods, `WmoCode.label` (`app/.../weather/WeatherSnapshot.kt`).
- Produces:
  ```kotlin
  class PlaceNamer(private val geocode: suspend (Double, Double) -> String?) {   // default: Geocoder locality ?: subAdminArea, on Dispatchers.IO
      suspend fun locality(lat: Double, lng: Double): String?                    // null on any exception or blank
      companion object { fun android(context: Context): PlaceNamer }
  }
  class SharePrefs(context: Context) {                                           // SharedPreferences "bumblebee_share"
      fun load(available: List<ShareMetricKind>): ShareSwitches
      fun save(switches: ShareSwitches)
      var promptEnabled: Boolean                                                  // default true
  }
  sealed interface ShareSubject { data class Ride(val rideId: Long) : ShareSubject; data class Day(val date: LocalDate) : ShareSubject }
  data class ShareUi(val switches: ShareSwitches, val name: String, val previews: Map<ShareLook, ShareCardModel>,
                     val selected: ShareLook?, val photo: Bitmap?, val mapFallback: Boolean, val canName: Boolean, val busy: Boolean)
  class ShareViewModel(app: Application, subject: ShareSubject) : AndroidViewModel(app) {
      val ui: StateFlow<ShareUi>
      fun pick(look: ShareLook); fun back()
      fun update(transform: (ShareSwitches) -> ShareSwitches)   // persists via SharePrefs.save
      fun rename(name: String)                                  // Ride: repo.renameRide on commit; Day: kept in memory only
      fun commitName()
      fun setPhoto(bitmap: Bitmap?)
  }
  ```
- Loading a Ride subject:
  - Load the ride, its track, its segments (as breaks), climb, tank and odometer as `ShareComposer` does today.
  - Resolve places once: if `startPlace` is null and the trimmed route exists, geocode the first and last trimmed fixes, then `repo.setPlaces`.
  - Get the ride number and `highlightFor`.
  - Build the weather line from the ride's `weatherTempC`/`weatherCode` with `WmoCode.label`, plus `SunTimes.tag` at the route midpoint.
- Loading a Day subject: use `repo.ridesOnDay`, then `ShareCard.ofDay`.
- Build `previews` for all four looks with the current switches. `mapFallback` comes from the model after `withTiles`; the screen loads tiles exactly as `ShareComposer` does today.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `place namer swallows geocoder failures`() = runTest {
    assertNull(PlaceNamer { _, _ -> throw java.io.IOException("offline") }.locality(12.9, 77.6))
    assertNull(PlaceNamer { _, _ -> "  " }.locality(12.9, 77.6))
}
@Test fun `prefs round trip and never return photo`() {
    val prefs = SharePrefs(ctx); prefs.save(ShareSwitches(look = ShareLook.COCKPIT, background = ShareBackground.PHOTO))
    val back = prefs.load(ShareMetricKind.values().toList())
    assertEquals(ShareLook.COCKPIT, back.look); assertEquals(ShareBackground.OWN, back.background)
}
@Test fun `prompt defaults on`() = assertTrue(SharePrefs(ctx).promptEnabled)
@Test fun `ride subject builds four previews with the ride name as headline`() = runTest {
    val id = seedRide(name = "Nandi run")
    val vm = ShareViewModel(app, ShareSubject.Ride(id)); advanceUntilIdle()
    assertEquals(4, vm.ui.value.previews.size); assertTrue(vm.ui.value.previews.values.all { it.headline == "Nandi run" })
}
@Test fun `rename commits to the ride`() = runTest {
    val id = seedRide(); val vm = ShareViewModel(app, ShareSubject.Ride(id)); advanceUntilIdle()
    vm.rename("Coorg"); vm.commitName(); advanceUntilIdle()
    assertEquals("Coorg", (app as DashApp).capture.rideById(id)!!.name)
}
```
`seedRide` inserts a finished ride with a 20 km track through `DashApp.capture`. Match how `app/src/test` already gets a `DashApp`; check the existing `ui/garage` tests.

- [ ] **Step 2: Run to verify they fail.** Run: `./gradlew :app:testDebugUnitTest --tests '*PlaceNamerTest' --tests '*SharePrefsTest' --tests '*ShareViewModelTest'`
- [ ] **Step 3: Implement.** Write a small `ViewModelProvider.Factory` for the subject argument, following the factory pattern other screens use (check `TransferViewModel`).
- [ ] **Step 4: Run tests to verify they pass.**
- [ ] **Step 5: Commit** — `git commit -m "feat(share): share view model, place names and remembered settings"`

---

### Task 13: Share screen (pick → customise), Sticker, Share/Save

**Files:**
- Create: `app/.../ui/share/ShareScreen.kt`, `StickerShare.kt`
- Modify: `app/.../ui/ride/RideVisuals.kt` (`ShareCardButton` opens `ShareScreen(ShareSubject.Ride(id))`)
- Delete: `app/.../ui/ride/ShareComposer.kt` (move `shareCardImage` and photo-picker code into the new files)
- Test: `app/src/test/.../ui/share/StickerShareTest.kt`

**Interfaces:**
- Consumes: `ShareViewModel` (Task 12), `ShareCardPainter.paint(..., sticker)` (Task 7), `ShareCardGallery.savePng`.
- Produces:
  ```kotlin
  @Composable fun ShareScreen(subject: ShareSubject, onDismiss: () -> Unit)   // full-screen Dialog like today
  object StickerShare {
      const val IG_PACKAGE = "com.instagram.android"
      fun intent(context: Context, stickerUri: Uri, installed: Boolean): Intent
      // installed → Intent("com.instagram.share.ADD_TO_STORY").setType("image/png").putExtra("interactive_asset_uri", uri), setPackage(IG_PACKAGE), FLAG_GRANT_READ_URI_PERMISSION
      // else → ACTION_SEND chooser with the PNG
      fun instagramInstalled(context: Context): Boolean
  }
  ```
- **Screen 1:**
  - The name field (`canName`), max 30 characters, with the headline fallback as placeholder text. The name commits on focus loss and on Share.
  - A 2×2 grid of previews. Each preview is painted from `previews[look]` at 1/4 scale on `Dispatchers.Default`.
  - The size chips: Story / 4:5 / Square.
- **Screen 2:**
  - A large preview.
  - Background chips: `<Look's name>` / Map / Photo / Transparent. Photo opens the picker.
  - The accent swatches.
  - Stat chips, with a max of 3. Selecting a 4th does nothing and shows the existing "Up to 3" hint style.
  - Toggles for places, highlight and weather. Each is shown only when the model has that fact.
  - When `mapFallback` is true, the notice "Map unavailable offline, showing route only".
  - Actions: Save / Sticker / Share. Share sends the image plus `card.sentence` as the caption.
- Use the existing `ui/components` (`PillChip`, `ChunkyButton`) and theme tokens, as `ShareComposer` does. Add the manifest `<queries><package android:name="com.instagram.android"/></queries>` entry so `instagramInstalled` works on Android 11+.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `sticker goes to instagram stories when installed`() {
    val i = StickerShare.intent(ctx, Uri.parse("content://x/s.png"), installed = true)
    assertEquals("com.instagram.share.ADD_TO_STORY", i.action); assertEquals("com.instagram.android", i.`package`)
    assertEquals(Uri.parse("content://x/s.png"), i.getParcelableExtra<Uri>("interactive_asset_uri"))
}
@Test fun `sticker falls back to the share sheet`() =
    assertEquals(Intent.ACTION_CHOOSER, StickerShare.intent(ctx, Uri.parse("content://x/s.png"), installed = false).action)
```

- [ ] **Step 2: Run to verify it fails.** Run: `./gradlew :app:testDebugUnitTest --tests '*StickerShareTest'`
- [ ] **Step 3: Implement the screen and `StickerShare`.** Remove `ShareComposer.kt`.
- [ ] **Step 4: Verify.**
  - Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS, BUILD SUCCESSFUL.
  - Install on the device and run through the screen manually:
    - Open a long ride, then pick each look.
    - Switch sizes, backgrounds and accents.
    - Rename the ride.
    - Save, and check the image in the gallery.
    - Use Sticker with Instagram installed.
    - Turn on airplane mode, choose Map, and confirm the notice appears.
- [ ] **Step 5: Commit** — `git commit -m "feat(share): two-step share screen with sticker export"`

---

### Task 14: Rename a ride from ride detail

**Files:**
- Modify: `app/.../ui/ride/RideVisuals.kt` (an edit icon next to the ride title, opening a dialog with the text field, max 30), `RideViewModel.kt`
- Test: `app/src/test/.../ui/ride/RideRenameTest.kt`

**Interfaces:**
- Consumes: `RideCaptureRepository.renameRide` (Task 6).
- Produces: `fun RideViewModel.renameRide(id: Long, name: String)`. It calls the repo, then refreshes the open detail and the archive, so the title and row update.

- [ ] **Step 1: Write the failing test.** `renameRide updates open detail title`: seed a ride, `openRide(id)`, `renameRide(id, "Nandi run")`, `advanceUntilIdle()`, then assert `ui.value.detail!!.row.name == "Nandi run"`. Match the actual state property names in `RideViewModel`.
- [ ] **Step 2: Run to verify it fails.** Run: `./gradlew :app:testDebugUnitTest --tests '*RideRenameTest'`
- [ ] **Step 3: Implement the view model method and the dialog.** An empty field clears the name; the title then falls back to `formatWhen` (existing behaviour at `RideVisuals.kt:386`).
- [ ] **Step 4: Run tests to verify they pass.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ride): rename a ride from ride detail"`

---

### Task 15: Record weather, ride-end share prompt, open route, setting

**Files:**
- Modify: `app/.../ble/BikeBridgeService.kt`, `app/.../ui/nav/BumblebeeApp.kt`, `app/.../ui/ride/RideViewModel.kt`, `app/.../ui/garage/GarageSettingsScreen.kt`, `GarageSettingsViewModel.kt`
- Create: `app/.../share/SharePrompt.kt`
- Test: `app/src/test/.../share/SharePromptTest.kt`

**Interfaces:**
- Consumes: `repo.finishedEvents`, `repo.highlightFor`, `repo.recordWeatherIfMissing` (Task 6), `SharePrefs.promptEnabled` (Task 12), `MainActivity.open`.
- Produces:
  ```kotlin
  object SharePrompt {
      const val CHANNEL_ID = "ride_highlights"     // IMPORTANCE_DEFAULT, name "Ride highlights"
      const val OPEN_PREFIX = "share:"             // MainActivity EXTRA_OPEN value "share:<rideId>"
      fun notification(context: Context, rideId: Long, highlight: Highlight): Notification   // title = highlight.detail, text = "Share it?"
      fun parse(open: String?): Long?              // "share:42" → 42, else null
  }
  fun RideViewModel.openShare(rideId: Long)        // opens ride detail and sets a one-shot flag that shows ShareScreen
  ```
- **Service:**
  - Collect `app.weather.snapshot` and call `recordWeatherIfMissing(it.tempC, it.code)` whenever there's an active ride. Also call it right after a ride starts in the Ready and START_GPS_RIDE paths, if a snapshot already exists.
  - Collect `capture.finishedEvents`. If `prefs.promptEnabled` and `highlightFor(id) != null`, post the `SharePrompt.notification` with a notification id of `1000 + (rideId % 1000)`.
  - Skip posting if `POST_NOTIFICATIONS` isn't granted on Android 13+.
- **BumblebeeApp:** handle `SharePrompt.parse(open)`. Navigate to `Pillar.RIDE.route`, then call `RideViewModel.openShare(id)` (the activity-scoped view model the Ride pillar already uses).
- **Settings:** a "Share prompt after record rides" switch bound to `SharePrefs.promptEnabled`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `parse reads share route`() { assertEquals(42L, SharePrompt.parse("share:42")); assertNull(SharePrompt.parse("garage")); assertNull(SharePrompt.parse("share:x")) }
@Test fun `notification says the highlight and opens share`() {
    val n = SharePrompt.notification(ctx, 42, Highlight(HighlightKind.TOP_SPEED, "New top speed", "New top speed: 112 km/h"))
    assertEquals("New top speed: 112 km/h", n.extras.getString(Notification.EXTRA_TITLE))
    assertEquals("Share it?", n.extras.getString(Notification.EXTRA_TEXT))
    assertEquals("share:42", shadowOf(n.contentIntent).savedIntent.getStringExtra(MainActivity.EXTRA_OPEN))
}
```

- [ ] **Step 2: Run to verify they fail.** Run: `./gradlew :app:testDebugUnitTest --tests '*SharePromptTest'`
- [ ] **Step 3: Implement.** Each notification's `PendingIntent` needs a distinct request code (the ride id) so prompts don't overwrite each other's extras. Use `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`.
- [ ] **Step 4: Verify.**
  - Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS.
  - On the device: finish a GPS ride faster than any earlier ride. The notification appears, and tapping it opens the share screen for that ride.
  - Turn the setting off, repeat, and confirm no notification appears.
- [ ] **Step 5: Commit** — `git commit -m "feat(share): weather per ride and ride-end highlight prompt"`

---

### Task 16: Share day from Explorer

**Files:**
- Modify: `app/.../ui/explorer/ExplorerViewModel.kt`, `ExplorerScreen.kt`
- Test: `app/src/test/.../ui/explorer/ExplorerShareDayTest.kt`

**Interfaces:**
- Consumes: `ShareScreen(ShareSubject.Day(date))` (Task 13), `repo.ridesOnDay` (Task 6).
- Produces: `ExplorerUi.canShareDay: Boolean`. It is true when `selectedDate` has two or more finished, non-demo, non-merged-child rides.

- [ ] **Step 1: Write the failing test.**
  - `share day appears only with two rides that day`: seed one ride on 24 Oct, `selectDate("2026-10-24")`, then assert `canShareDay` is false.
  - Seed a second ride that day, reselect, then assert `canShareDay` is true.
- [ ] **Step 2: Run to verify it fails.** Run: `./gradlew :app:testDebugUnitTest --tests '*ExplorerShareDayTest'`
- [ ] **Step 3: Implement.** Compute `canShareDay` where `selectedDate` details are built (around `ExplorerViewModel.kt:166`). In `ExplorerScreen`, show a `ChunkyButton("Share day")` in the selected-day panel that opens `ShareScreen(ShareSubject.Day(LocalDate.parse(date)))`. On a Day subject the name field is labelled "Name this day" and isn't saved.
- [ ] **Step 4: Verify.** Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Then on the device, check that a day with two rides gives a combined card with a stop dot between the rides.
- [ ] **Step 5: Commit** — `git commit -m "feat(share): share a whole day from Explorer"`

---

## Final verification

- [ ] Run: `./gradlew :core:ride:test :core:snapshot:test :data:local:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`. Expected: all PASS, BUILD SUCCESSFUL.
- [ ] Generate preview PNGs: `./gradlew :app:testDebugUnitTest --tests '*ShareCardRenderTest' -Dshare.preview=true`. Check all 4 looks × 3 sizes in `app/build/share-preview/` for clipping, legibility, and the empty-route case.
