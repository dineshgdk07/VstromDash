# Ride Data Capture Pipeline Implementation Plan (Plan 2 of v1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the v1 data capture pipeline from the insights catalog spec (Part B of
`docs/superpowers/specs/2026-07-28-ride-insights-catalog-design.md`): a pure-Kotlin Ride Engine
capture layer (ride aggregation, fuel-event detection, bar calibration, demo feed) plus a Room
database that archives every telemetry frame, GPS point, and fuel event from ride one.

**Architecture:** Two new Gradle modules. `core:ride` is a pure Kotlin/JVM library (like
`core:protocol`) holding domain models and streaming aggregators — `RideRecorder`,
`FuelEventDetector`, `BarCalibrator`, `DemoRideSource` — all testable with plain JUnit.
`data:local` is an Android library with Room entities/DAOs mirroring the spec's capture schema
(sync-ready `remoteId`/`syncedAt` fields per D-016) and a `RideCaptureRepository` that streams
raw data to disk while feeding the aggregators. No UI, no BLE, no real GPS in this plan — those
plans consume these modules later through `RideCaptureRepository` and `Flow<TelemetryFrame>` /
`Flow<GpsFix>` interfaces. Everything is verified end-to-end by driving `DemoRideSource` through
the repository in a Robolectric test.

**Tech Stack:** Kotlin 2.1.0, Room 2.7.1 + KSP 2.1.0-1.0.29, kotlinx-coroutines 1.10.1,
JUnit4, Robolectric 4.14.1, androidx.test core 1.6.1.

## Global Constraints

- **JDK:** 17, at `/home/gdk/.local/jdk-17` (`JAVA_HOME`). Android SDK at `/home/gdk/Android/Sdk` (`ANDROID_HOME`). Use `source /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/env.sh` OR export both before any gradle command.
- **Versions (pin exactly):** AGP `8.10.0`, Kotlin `2.1.0`, compose-bom `2026.05.00` (existing); add Room `2.7.1`, KSP `2.1.0-1.0.29`, kotlinx-coroutines `1.10.1`, Robolectric `4.14.1`, androidx-test-core `1.6.1`. `compileSdk = 35`, `minSdk = 26`, JVM target 17.
- **Base package:** `dev.gdk.bumblebee`. New packages: `dev.gdk.bumblebee.ride` (core:ride), `dev.gdk.bumblebee.data.local` (data:local).
- **`core:ride` purity rule:** pure Kotlin/JVM — may depend on `core:protocol` and `kotlinx-coroutines-core` only. Zero Android dependencies.
- **Spec constants (from insights spec Part B):** fuel-bar debounce = value stable for **3 consecutive frames**; GPS fixes rejected when `accuracyM > 25`; GPS distance segments < 3 m ignored; telemetry `dt` capped at 10 s; "moving" means `speedKmh >= 3`.
- **Timestamps:** epoch milliseconds (`Long`) everywhere.
- **Commit style:** one commit per task, conventional-commit subject.

Working directory for all commands: `/home/gdk/CodeHollow/Flutter/bumblebee-dash`.

Gradle invocations below assume: `source /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/env.sh` has been run in the shell first.

---

### Task 1: `core:ride` module with domain models and SpeedHistogram

**Files:**
- Modify: `settings.gradle.kts` (add include)
- Modify: `gradle/libs.versions.toml` (add coroutines)
- Create: `core/ride/build.gradle.kts`
- Create: `core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/Models.kt`
- Create: `core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/SpeedHistogram.kt`
- Test: `core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/SpeedHistogramTest.kt`
- Test: `core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/TelemetrySampleTest.kt`

**Interfaces:**
- Consumes: `dev.gdk.bumblebee.protocol.TelemetryFrame` (fields `speedKmh: Int`, `odometerKm: Int`, `tripAKm: Double`, `tripBKm: Double`, `fuelBars: Int?`, `fuelEconKml: Double?`, property `fuelEconKmlV2: Double?`).
- Produces: `TelemetrySample(ts, speedKmh, odometerKm, tripAKm, tripBKm, fuelBars, fuelEconKml)` with `TelemetrySample.fromFrame(frame, ts)`; `GpsFix(ts, lat, lng, altitudeM, bearingDeg, speedMps, accuracyM)`; `SpeedHistogram` with `add(speedKmh, durationSec)`, `secondsIn(band)`, `toJson()`, `SpeedHistogram.fromJson(json)`; `enum SpeedBand { IDLE, B1_30, B30_50, B50_70, B70_90, B90_PLUS }`.

- [ ] **Step 1: Register the module and add the coroutines dependency**

Append to `settings.gradle.kts`:

```kotlin
include(":core:ride")
```

In `gradle/libs.versions.toml` add under `[versions]`:

```toml
coroutines = "1.10.1"
```

and under `[libraries]`:

```toml
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

Create `core/ride/build.gradle.kts` (same shape as `core/protocol/build.gradle.kts`):

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    jvmToolchain(17)
}
dependencies {
    implementation(project(":core:protocol"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: Write the failing tests**

`core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/SpeedHistogramTest.kt`:

```kotlin
package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedHistogramTest {

    @Test
    fun `buckets speeds into the right bands`() {
        val h = SpeedHistogram()
        h.add(speedKmh = 0, durationSec = 60)    // IDLE
        h.add(speedKmh = 2, durationSec = 10)    // IDLE (< 3 km/h is idle)
        h.add(speedKmh = 25, durationSec = 30)   // B1_30
        h.add(speedKmh = 30, durationSec = 20)   // B30_50 (lower bound inclusive)
        h.add(speedKmh = 65, durationSec = 120)  // B50_70
        h.add(speedKmh = 84, durationSec = 15)   // B70_90
        h.add(speedKmh = 120, durationSec = 5)   // B90_PLUS

        assertEquals(70, h.secondsIn(SpeedBand.IDLE))
        assertEquals(30, h.secondsIn(SpeedBand.B1_30))
        assertEquals(20, h.secondsIn(SpeedBand.B30_50))
        assertEquals(120, h.secondsIn(SpeedBand.B50_70))
        assertEquals(15, h.secondsIn(SpeedBand.B70_90))
        assertEquals(5, h.secondsIn(SpeedBand.B90_PLUS))
    }

    @Test
    fun `json round trip preserves all bands`() {
        val h = SpeedHistogram()
        h.add(55, 300)
        h.add(0, 45)
        val restored = SpeedHistogram.fromJson(h.toJson())
        for (band in SpeedBand.entries) {
            assertEquals(h.secondsIn(band), restored.secondsIn(band))
        }
    }

    @Test
    fun `empty histogram serializes and restores to all zeros`() {
        val restored = SpeedHistogram.fromJson(SpeedHistogram().toJson())
        for (band in SpeedBand.entries) {
            assertEquals(0, restored.secondsIn(band))
        }
    }
}
```

`core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/TelemetrySampleTest.kt`:

```kotlin
package dev.gdk.bumblebee.ride

import dev.gdk.bumblebee.protocol.TelemetryFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetrySampleTest {

    @Test
    fun `fromFrame copies fields and timestamps`() {
        val frame = TelemetryFrame(
            speedKmh = 62, odometerKm = 15234, tripAKm = 412.5, tripBKm = 88.0,
            fuelBars = 4, fuelEconKml = 41.2,
        )
        val s = TelemetrySample.fromFrame(frame, ts = 1_000L)
        assertEquals(1_000L, s.ts)
        assertEquals(62, s.speedKmh)
        assertEquals(15234, s.odometerKm)
        assertEquals(412.5, s.tripAKm, 0.0)
        assertEquals(88.0, s.tripBKm, 0.0)
        assertEquals(4, s.fuelBars)
        // No raw bytes -> fuelEconKmlV2 is null -> falls back to legacy field.
        assertEquals(41.2, s.fuelEconKml!!, 0.0)
    }

    @Test
    fun `fromFrame prefers the V2 fuel economy decode when raw bytes exist`() {
        // Build a frame whose raw byte 25 = 96 -> V2 economy 48.0 km/L.
        val encoded = TelemetryFrame(
            speedKmh = 40, odometerKm = 100, tripAKm = 1.0, tripBKm = 2.0,
            fuelBars = 5, fuelEconKml = null,
        ).encode()
        encoded[25] = 96
        val decoded = TelemetryFrame(
            speedKmh = 40, odometerKm = 100, tripAKm = 1.0, tripBKm = 2.0,
            fuelBars = 5, fuelEconKml = null, raw = encoded,
        )
        val s = TelemetrySample.fromFrame(decoded, ts = 5L)
        assertEquals(48.0, s.fuelEconKml!!, 0.0)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :core:ride:test`
Expected: FAIL — unresolved references `SpeedHistogram`, `TelemetrySample`.

- [ ] **Step 4: Write the implementation**

`core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/Models.kt`:

```kotlin
package dev.gdk.bumblebee.ride

import dev.gdk.bumblebee.protocol.TelemetryFrame

/** One archived a537 frame with the phone timestamp it arrived at. */
data class TelemetrySample(
    val ts: Long,
    val speedKmh: Int,
    val odometerKm: Int,
    val tripAKm: Double,
    val tripBKm: Double,
    val fuelBars: Int?,
    val fuelEconKml: Double?,
) {
    companion object {
        fun fromFrame(frame: TelemetryFrame, ts: Long): TelemetrySample = TelemetrySample(
            ts = ts,
            speedKmh = frame.speedKmh,
            odometerKm = frame.odometerKm,
            tripAKm = frame.tripAKm,
            tripBKm = frame.tripBKm,
            fuelBars = frame.fuelBars,
            // V2 (byte25/2, Gixxer-family validated) when raw bytes exist; legacy otherwise.
            fuelEconKml = frame.fuelEconKmlV2 ?: frame.fuelEconKml,
        )
    }
}

/** One GPS fix from the phone. Units: degrees, meters, m/s. */
data class GpsFix(
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val altitudeM: Double? = null,
    val bearingDeg: Float? = null,
    val speedMps: Float? = null,
    val accuracyM: Float? = null,
)
```

`core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/SpeedHistogram.kt`:

```kotlin
package dev.gdk.bumblebee.ride

enum class SpeedBand { IDLE, B1_30, B30_50, B50_70, B70_90, B90_PLUS }

/** Seconds spent per speed band. Bands: idle <3, [3,30), [30,50), [50,70), [70,90), >=90 km/h. */
class SpeedHistogram {
    private val seconds = IntArray(SpeedBand.entries.size)

    fun add(speedKmh: Int, durationSec: Int) {
        val band = when {
            speedKmh < 3 -> SpeedBand.IDLE
            speedKmh < 30 -> SpeedBand.B1_30
            speedKmh < 50 -> SpeedBand.B30_50
            speedKmh < 70 -> SpeedBand.B50_70
            speedKmh < 90 -> SpeedBand.B70_90
            else -> SpeedBand.B90_PLUS
        }
        seconds[band.ordinal] += durationSec
    }

    fun secondsIn(band: SpeedBand): Int = seconds[band.ordinal]

    /** Stable JSON: {"IDLE":70,"B1_30":30,...} — hand-rolled to avoid a JSON dependency. */
    fun toJson(): String = SpeedBand.entries.joinToString(
        separator = ",", prefix = "{", postfix = "}",
    ) { "\"${it.name}\":${seconds[it.ordinal]}" }

    companion object {
        fun fromJson(json: String): SpeedHistogram {
            val h = SpeedHistogram()
            Regex("\"([A-Z0-9_]+)\":(\\d+)").findAll(json).forEach { m ->
                val band = SpeedBand.entries.firstOrNull { it.name == m.groupValues[1] } ?: return@forEach
                h.seconds[band.ordinal] = m.groupValues[2].toInt()
            }
            return h
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:ride:test`
Expected: PASS (5 tests). Also run `./gradlew :core:protocol:test` — still green.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml core/ride
git commit -m "feat(ride): add core:ride module with telemetry/GPS models and speed histogram"
```

---

### Task 2: RideRecorder streaming aggregator

**Files:**
- Create: `core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/Geo.kt`
- Create: `core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/RideRecorder.kt`
- Test: `core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/RideRecorderTest.kt`

**Interfaces:**
- Consumes: `TelemetrySample`, `GpsFix`, `SpeedHistogram` from Task 1.
- Produces: `RideRecorder` with `start(startTs: Long)`, `onTelemetry(sample: TelemetrySample)`, `onGps(fix: GpsFix)`, `stop(endTs: Long): RideAggregates`, `val isActive: Boolean`; `data class RideAggregates(startTs, endTs, odoStartKm: Int?, odoEndKm: Int?, gpsDistanceM: Double, movingSec: Int, idleSec: Int, maxSpeedKmh: Int, avgMovingSpeedKmh: Double, fuelBarsStart: Int?, fuelBarsEnd: Int?, histogram: SpeedHistogram)`; `Geo.haversineMeters(lat1, lng1, lat2, lng2): Double`.

- [ ] **Step 1: Write the failing tests**

`core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/RideRecorderTest.kt`:

```kotlin
package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRecorderTest {

    private fun sample(ts: Long, speed: Int, odo: Int = 1000, bars: Int? = 4) =
        TelemetrySample(ts, speed, odo, 0.0, 0.0, bars, null)

    @Test
    fun `aggregates a simple ride`() {
        val r = RideRecorder()
        r.start(startTs = 0L)
        assertTrue(r.isActive)

        // 5s-spaced frames: idle, then moving, then idle again.
        r.onTelemetry(sample(ts = 0L, speed = 0, odo = 1000, bars = 4))
        r.onTelemetry(sample(ts = 5_000L, speed = 40, odo = 1000, bars = 4))
        r.onTelemetry(sample(ts = 10_000L, speed = 60, odo = 1001, bars = 4))
        r.onTelemetry(sample(ts = 15_000L, speed = 84, odo = 1001, bars = 3))
        r.onTelemetry(sample(ts = 20_000L, speed = 0, odo = 1002, bars = 3))

        val agg = r.stop(endTs = 25_000L)
        assertFalse(r.isActive)

        assertEquals(0L, agg.startTs)
        assertEquals(25_000L, agg.endTs)
        assertEquals(1000, agg.odoStartKm!!)
        assertEquals(1002, agg.odoEndKm!!)
        assertEquals(84, agg.maxSpeedKmh)
        assertEquals(4, agg.fuelBarsStart!!)
        assertEquals(3, agg.fuelBarsEnd!!)
        // First frame has no predecessor (dt unknown -> skipped for time buckets);
        // frames 2..5 each contribute dt=5s: moving 40,60,84 = 15s; idle speed 0 = 5s.
        assertEquals(15, agg.movingSec)
        assertEquals(5, agg.idleSec)
        // Time-weighted moving average: (40+60+84)*5 / 15 = 61.33
        assertEquals(61.33, agg.avgMovingSpeedKmh, 0.01)
        assertEquals(10, agg.histogram.secondsIn(SpeedBand.B30_50) + agg.histogram.secondsIn(SpeedBand.B50_70))
    }

    @Test
    fun `caps dt at 10 seconds across telemetry gaps`() {
        val r = RideRecorder()
        r.start(0L)
        r.onTelemetry(sample(0L, 50))
        r.onTelemetry(sample(60_000L, 50)) // 60s gap -> counts as 10s only
        val agg = r.stop(60_000L)
        assertEquals(10, agg.movingSec)
    }

    @Test
    fun `accumulates gps distance and skips bad fixes`() {
        val r = RideRecorder()
        r.start(0L)
        // ~111m per 0.001 deg latitude at the equator.
        r.onGps(GpsFix(ts = 0L, lat = 0.0, lng = 0.0, accuracyM = 5f))
        r.onGps(GpsFix(ts = 1_000L, lat = 0.001, lng = 0.0, accuracyM = 5f))
        r.onGps(GpsFix(ts = 2_000L, lat = 0.002, lng = 0.0, accuracyM = 90f))  // rejected: accuracy > 25
        r.onGps(GpsFix(ts = 3_000L, lat = 0.001000001, lng = 0.0, accuracyM = 5f)) // < 3m -> ignored
        r.onGps(GpsFix(ts = 4_000L, lat = 0.002, lng = 0.0, accuracyM = 5f))
        val agg = r.stop(5_000L)
        assertEquals(222.4, agg.gpsDistanceM, 1.0) // two ~111.2m segments
    }

    @Test(expected = IllegalStateException::class)
    fun `stop before start throws`() {
        RideRecorder().stop(0L)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:ride:test --tests "dev.gdk.bumblebee.ride.RideRecorderTest"`
Expected: FAIL — unresolved reference `RideRecorder`.

- [ ] **Step 3: Write the implementation**

`core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/Geo.kt`:

```kotlin
package dev.gdk.bumblebee.ride

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }
}
```

`core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/RideRecorder.kt`:

```kotlin
package dev.gdk.bumblebee.ride

/** Final aggregates for one ride (one BLE/engine session). */
data class RideAggregates(
    val startTs: Long,
    val endTs: Long,
    val odoStartKm: Int?,
    val odoEndKm: Int?,
    val gpsDistanceM: Double,
    val movingSec: Int,
    val idleSec: Int,
    val maxSpeedKmh: Int,
    val avgMovingSpeedKmh: Double,
    val fuelBarsStart: Int?,
    val fuelBarsEnd: Int?,
    val histogram: SpeedHistogram,
)

/**
 * Streaming per-ride aggregator. Pure state machine: the caller owns persistence and
 * session boundaries (a Ride = one BLE session per the design spec).
 */
class RideRecorder {
    companion object {
        const val MOVING_THRESHOLD_KMH = 3
        const val MAX_DT_SEC = 10
        const val MIN_GPS_SEGMENT_M = 3.0
        const val MAX_GPS_ACCURACY_M = 25f
    }

    var isActive: Boolean = false
        private set

    private var startTs = 0L
    private var lastTelemetryTs: Long? = null
    private var odoStartKm: Int? = null
    private var odoEndKm: Int? = null
    private var fuelBarsStart: Int? = null
    private var fuelBarsEnd: Int? = null
    private var maxSpeedKmh = 0
    private var movingSec = 0
    private var idleSec = 0
    private var movingSpeedSecSum = 0.0
    private var gpsDistanceM = 0.0
    private var lastFix: GpsFix? = null
    private var histogram = SpeedHistogram()

    fun start(startTs: Long) {
        check(!isActive) { "recorder already active" }
        this.startTs = startTs
        lastTelemetryTs = null
        odoStartKm = null; odoEndKm = null
        fuelBarsStart = null; fuelBarsEnd = null
        maxSpeedKmh = 0; movingSec = 0; idleSec = 0; movingSpeedSecSum = 0.0
        gpsDistanceM = 0.0; lastFix = null
        histogram = SpeedHistogram()
        isActive = true
    }

    fun onTelemetry(sample: TelemetrySample) {
        check(isActive) { "recorder not active" }
        if (odoStartKm == null) odoStartKm = sample.odometerKm
        odoEndKm = sample.odometerKm
        if (fuelBarsStart == null && sample.fuelBars != null) fuelBarsStart = sample.fuelBars
        if (sample.fuelBars != null) fuelBarsEnd = sample.fuelBars
        if (sample.speedKmh > maxSpeedKmh) maxSpeedKmh = sample.speedKmh

        val prevTs = lastTelemetryTs
        lastTelemetryTs = sample.ts
        // The first frame has no interval; time buckets start from the second frame.
        if (prevTs != null) {
            val dt = (((sample.ts - prevTs) / 1000).toInt()).coerceIn(0, MAX_DT_SEC)
            if (sample.speedKmh >= MOVING_THRESHOLD_KMH) {
                movingSec += dt
                movingSpeedSecSum += sample.speedKmh.toDouble() * dt
            } else {
                idleSec += dt
            }
            histogram.add(sample.speedKmh, dt)
        }
    }

    fun onGps(fix: GpsFix) {
        check(isActive) { "recorder not active" }
        if (fix.accuracyM != null && fix.accuracyM > MAX_GPS_ACCURACY_M) return
        val prev = lastFix
        if (prev == null) {
            lastFix = fix
            return
        }
        val d = Geo.haversineMeters(prev.lat, prev.lng, fix.lat, fix.lng)
        if (d < MIN_GPS_SEGMENT_M) return
        gpsDistanceM += d
        lastFix = fix
    }

    fun stop(endTs: Long): RideAggregates {
        check(isActive) { "recorder not active" }
        isActive = false
        return RideAggregates(
            startTs = startTs,
            endTs = endTs,
            odoStartKm = odoStartKm,
            odoEndKm = odoEndKm,
            gpsDistanceM = gpsDistanceM,
            movingSec = movingSec,
            idleSec = idleSec,
            maxSpeedKmh = maxSpeedKmh,
            avgMovingSpeedKmh = if (movingSec > 0) movingSpeedSecSum / movingSec else 0.0,
            fuelBarsStart = fuelBarsStart,
            fuelBarsEnd = fuelBarsEnd,
            histogram = histogram,
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:ride:test`
Expected: PASS (all Task 1 + Task 2 tests).

- [ ] **Step 5: Commit**

```bash
git add core/ride
git commit -m "feat(ride): add RideRecorder streaming aggregator with GPS distance and time buckets"
```

---

### Task 3: FuelEventDetector with debounce

**Files:**
- Create: `core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/FuelEventDetector.kt`
- Test: `core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/FuelEventDetectorTest.kt`

**Interfaces:**
- Consumes: `TelemetrySample` from Task 1.
- Produces: `FuelEventDetector(stabilityFrames: Int = 3)` with `onSample(sample: TelemetrySample): FuelEvent?` and `reset()`; `data class FuelEvent(ts: Long, odometerKm: Int, barsBefore: Int, barsAfter: Int, type: FuelEventType)`; `enum FuelEventType { BAR_DROP, REFUEL }`. Detector state survives across rides within a process (caller decides when to `reset()`); Task 6 persists events.

- [ ] **Step 1: Write the failing tests**

`core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/FuelEventDetectorTest.kt`:

```kotlin
package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelEventDetectorTest {

    private fun sample(ts: Long, bars: Int?, odo: Int = 500) =
        TelemetrySample(ts, 40, odo, 0.0, 0.0, bars, null)

    @Test
    fun `emits BAR_DROP only after the new value is stable for 3 frames`() {
        val d = FuelEventDetector()
        // Establish stable baseline of 4 bars.
        assertNull(d.onSample(sample(0L, 4)))
        assertNull(d.onSample(sample(5_000L, 4)))
        assertNull(d.onSample(sample(10_000L, 4)))
        // Drop to 3 — needs 3 consecutive frames before the event fires.
        assertNull(d.onSample(sample(15_000L, 3, odo = 548)))
        assertNull(d.onSample(sample(20_000L, 3, odo = 548)))
        val e = d.onSample(sample(25_000L, 3, odo = 549))
        assertEquals(FuelEventType.BAR_DROP, e!!.type)
        assertEquals(4, e.barsBefore)
        assertEquals(3, e.barsAfter)
        assertEquals(549, e.odometerKm)
        assertEquals(25_000L, e.ts)
        // No duplicate event afterwards.
        assertNull(d.onSample(sample(30_000L, 3)))
    }

    @Test
    fun `flicker shorter than 3 frames is ignored`() {
        val d = FuelEventDetector()
        d.onSample(sample(0L, 4)); d.onSample(sample(5_000L, 4)); d.onSample(sample(10_000L, 4))
        // One-frame flicker to 3 (slope/braking slosh) then back to 4.
        assertNull(d.onSample(sample(15_000L, 3)))
        assertNull(d.onSample(sample(20_000L, 4)))
        assertNull(d.onSample(sample(25_000L, 4)))
        assertNull(d.onSample(sample(30_000L, 4)))
    }

    @Test
    fun `emits REFUEL when bars increase, even across a reset`() {
        val d = FuelEventDetector()
        d.onSample(sample(0L, 2)); d.onSample(sample(5_000L, 2)); d.onSample(sample(10_000L, 2))
        d.reset() // ride ended; rider refuels with engine off
        // Next ride starts with 6 bars: first stable value after reset with a known
        // previous stable value of 2 -> REFUEL.
        assertNull(d.onSample(sample(100_000L, 6)))
        assertNull(d.onSample(sample(105_000L, 6)))
        val e = d.onSample(sample(110_000L, 6, odo = 700))
        assertEquals(FuelEventType.REFUEL, e!!.type)
        assertEquals(2, e.barsBefore)
        assertEquals(6, e.barsAfter)
    }

    @Test
    fun `null bars are skipped without breaking state`() {
        val d = FuelEventDetector()
        d.onSample(sample(0L, 4)); d.onSample(sample(5_000L, 4)); d.onSample(sample(10_000L, 4))
        assertNull(d.onSample(sample(15_000L, null)))
        assertNull(d.onSample(sample(20_000L, 3)))
        assertNull(d.onSample(sample(25_000L, 3)))
        val e = d.onSample(sample(30_000L, 3))
        assertEquals(FuelEventType.BAR_DROP, e!!.type)
    }

    @Test
    fun `very first stable value emits no event`() {
        val d = FuelEventDetector()
        assertNull(d.onSample(sample(0L, 5)))
        assertNull(d.onSample(sample(5_000L, 5)))
        assertNull(d.onSample(sample(10_000L, 5)))
        assertNull(d.onSample(sample(15_000L, 5)))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:ride:test --tests "dev.gdk.bumblebee.ride.FuelEventDetectorTest"`
Expected: FAIL — unresolved reference `FuelEventDetector`.

- [ ] **Step 3: Write the implementation**

`core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/FuelEventDetector.kt`:

```kotlin
package dev.gdk.bumblebee.ride

enum class FuelEventType { BAR_DROP, REFUEL }

data class FuelEvent(
    val ts: Long,
    val odometerKm: Int,
    val barsBefore: Int,
    val barsAfter: Int,
    val type: FuelEventType,
)

/**
 * Detects fuel-bar transitions from the a537 stream. Bars flicker on slopes and under
 * braking, so a new value must persist for [stabilityFrames] consecutive non-null frames
 * (~15 s at the bike's 5 s cadence) before it is accepted as a transition.
 *
 * [reset] clears the debounce candidate but keeps the last stable value, so a refuel
 * between rides (bars jump up across sessions) is still detected on the next ride.
 */
class FuelEventDetector(private val stabilityFrames: Int = 3) {

    private var stableBars: Int? = null
    private var candidateBars: Int? = null
    private var candidateCount = 0

    fun reset() {
        candidateBars = null
        candidateCount = 0
    }

    fun onSample(sample: TelemetrySample): FuelEvent? {
        val bars = sample.fuelBars ?: return null

        if (bars == stableBars) {
            candidateBars = null
            candidateCount = 0
            return null
        }

        if (bars == candidateBars) {
            candidateCount++
        } else {
            candidateBars = bars
            candidateCount = 1
        }
        if (candidateCount < stabilityFrames) return null

        val previous = stableBars
        stableBars = bars
        candidateBars = null
        candidateCount = 0

        if (previous == null) return null // first ever stable reading; nothing to compare
        return FuelEvent(
            ts = sample.ts,
            odometerKm = sample.odometerKm,
            barsBefore = previous,
            barsAfter = bars,
            type = if (bars < previous) FuelEventType.BAR_DROP else FuelEventType.REFUEL,
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:ride:test`
Expected: PASS (all tests so far).

- [ ] **Step 5: Commit**

```bash
git add core/ride
git commit -m "feat(ride): add debounced fuel bar-drop and refuel detection"
```

---

### Task 4: BarCalibrator — learned km-per-bar

**Files:**
- Create: `core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/BarCalibrator.kt`
- Test: `core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/BarCalibratorTest.kt`

**Interfaces:**
- Consumes: `FuelEvent`, `FuelEventType` from Task 3.
- Produces: `BarCalibrator` with `update(events: List<FuelEvent>)` (full history, chronological), `kmPerBar(bar: Int): Double?` (bar = the bar position that was consumed, 1..6), `averageKmPerBar(): Double?`, `estimatedRangeKm(currentBars: Int): Double?`.

- [ ] **Step 1: Write the failing tests**

`core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/BarCalibratorTest.kt`:

```kotlin
package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarCalibratorTest {

    private fun drop(odo: Int, from: Int) =
        FuelEvent(ts = odo.toLong(), odometerKm = odo, barsBefore = from, barsAfter = from - 1, type = FuelEventType.BAR_DROP)

    private fun refuel(odo: Int, from: Int, to: Int) =
        FuelEvent(ts = odo.toLong(), odometerKm = odo, barsBefore = from, barsAfter = to, type = FuelEventType.REFUEL)

    @Test
    fun `learns km per bar from consecutive drops`() {
        val c = BarCalibrator()
        // Bar 5 lasted 1040-1000=40km; bar 4 lasted 1088-1040=48km.
        c.update(listOf(drop(1000, from = 6), drop(1040, from = 5), drop(1088, from = 4)))
        assertEquals(40.0, c.kmPerBar(5)!!, 0.001)
        assertEquals(48.0, c.kmPerBar(4)!!, 0.001)
        assertNull(c.kmPerBar(3)) // never observed
        assertEquals(44.0, c.averageKmPerBar()!!, 0.001)
    }

    @Test
    fun `a refuel breaks the consecutive-drop chain`() {
        val c = BarCalibrator()
        c.update(listOf(
            drop(1000, from = 3),
            refuel(1010, from = 2, to = 6),
            drop(1100, from = 6), // NOT paired with the pre-refuel drop
            drop(1145, from = 5),
        ))
        assertEquals(45.0, c.kmPerBar(5)!!, 0.001)
        assertNull(c.kmPerBar(2))
        assertNull(c.kmPerBar(6)) // needs the *next* drop after 1100->? chain start
    }

    @Test
    fun `averages multiple observations of the same bar`() {
        val c = BarCalibrator()
        c.update(listOf(
            drop(1000, from = 5), drop(1040, from = 4),   // bar 4: 40 km
            refuel(1050, from = 3, to = 6),
            drop(1200, from = 5), drop(1248, from = 4),   // bar 4: 48 km
        ))
        assertEquals(44.0, c.kmPerBar(4)!!, 0.001)
    }

    @Test
    fun `estimates range from current bars`() {
        val c = BarCalibrator()
        c.update(listOf(drop(1000, from = 6), drop(1040, from = 5), drop(1088, from = 4)))
        // 3 bars remaining x 44.0 average = 132 km.
        assertEquals(132.0, c.estimatedRangeKm(3)!!, 0.001)
        assertNull(BarCalibrator().estimatedRangeKm(3)) // no data yet -> silent (spec principle 1)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:ride:test --tests "dev.gdk.bumblebee.ride.BarCalibratorTest"`
Expected: FAIL — unresolved reference `BarCalibrator`.

- [ ] **Step 3: Write the implementation**

`core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/BarCalibrator.kt`:

```kotlin
package dev.gdk.bumblebee.ride

/**
 * Learns how many km each fuel-bar position lasts from the BAR_DROP history.
 *
 * A bar's consumption is the odometer distance between the drop that *started* it
 * (barsAfter == b) and the next drop (barsBefore == b). Only consecutive drops count;
 * a REFUEL between them invalidates the pair. Bars are rarely linear (spec C2#9), so
 * observations are kept per bar position.
 */
class BarCalibrator {

    private val observations = mutableMapOf<Int, MutableList<Double>>()

    /** Rebuild from the full chronological fuel-event history. */
    fun update(events: List<FuelEvent>) {
        observations.clear()
        var chainStart: FuelEvent? = null // last BAR_DROP, valid only if no refuel since
        for (e in events) {
            when (e.type) {
                FuelEventType.REFUEL -> chainStart = null
                FuelEventType.BAR_DROP -> {
                    val prev = chainStart
                    if (prev != null && prev.barsAfter == e.barsBefore) {
                        val km = (e.odometerKm - prev.odometerKm).toDouble()
                        if (km > 0) observations.getOrPut(e.barsBefore) { mutableListOf() }.add(km)
                    }
                    chainStart = e
                }
            }
        }
    }

    fun kmPerBar(bar: Int): Double? = observations[bar]?.average()

    fun averageKmPerBar(): Double? {
        val all = observations.values.flatten()
        return if (all.isEmpty()) null else all.average()
    }

    fun estimatedRangeKm(currentBars: Int): Double? {
        val avg = averageKmPerBar() ?: return null
        return currentBars * avg
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:ride:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/ride
git commit -m "feat(ride): add BarCalibrator learning km-per-bar from fuel event history"
```

---

### Task 5: DemoRideSource — synthetic telemetry and GPS feed

**Files:**
- Create: `core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/DemoRideSource.kt`
- Test: `core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/DemoRideSourceTest.kt`

**Interfaces:**
- Consumes: `TelemetryFrame` (constructs instances), `GpsFix`.
- Produces: `DemoRideSource(startOdoKm: Int = 15_000, startBars: Int = 5, startTs: Long = 0L)` with `fun telemetry(): List<Pair<Long, TelemetryFrame>>` (ts→frame, 5 s cadence) and `fun gps(): List<GpsFix>` (1 s cadence) for a scripted ~10-minute urban ride: idle warm-up → 30–60 km/h cruise with two stops → one bar drop mid-ride → idle cool-down. Deterministic (no randomness) so tests can assert exact counts. This is the seed of the v1 demo mode; later plans wrap it in a `Flow` with real delays.

- [ ] **Step 1: Write the failing tests**

`core/ride/src/test/kotlin/dev/gdk/bumblebee/ride/DemoRideSourceTest.kt`:

```kotlin
package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoRideSourceTest {

    @Test
    fun `produces a 10 minute ride at the right cadences`() {
        val demo = DemoRideSource()
        val frames = demo.telemetry()
        val fixes = demo.gps()
        assertEquals(120, frames.size)      // 600s / 5s
        assertEquals(600, fixes.size)       // 600s / 1s
        assertEquals(0L, frames.first().first)
        assertEquals(595_000L, frames.last().first)
    }

    @Test
    fun `ride shape has idle bookends, movement, and one bar drop`() {
        val demo = DemoRideSource(startOdoKm = 15_000, startBars = 5)
        val frames = demo.telemetry().map { it.second }
        assertEquals(0, frames.first().speedKmh)
        assertEquals(0, frames.last().speedKmh)
        assertTrue(frames.any { it.speedKmh in 30..60 })
        assertTrue(frames.none { it.speedKmh > 60 })
        assertEquals(5, frames.first().fuelBars)
        assertEquals(4, frames.last().fuelBars)
        // Odometer never decreases.
        frames.zipWithNext().forEach { (a, b) -> assertTrue(b.odometerKm >= a.odometerKm) }
    }

    @Test
    fun `gps track moves while telemetry says moving`() {
        val demo = DemoRideSource()
        val fixes = demo.gps()
        val d = Geo.haversineMeters(fixes.first().lat, fixes.first().lng, fixes.last().lat, fixes.last().lng)
        assertTrue("expected net movement > 500m, was $d", d > 500)
    }

    @Test
    fun `is deterministic`() {
        val a = DemoRideSource().telemetry().map { it.second }
        val b = DemoRideSource().telemetry().map { it.second }
        assertEquals(a, b)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:ride:test --tests "dev.gdk.bumblebee.ride.DemoRideSourceTest"`
Expected: FAIL — unresolved reference `DemoRideSource`.

- [ ] **Step 3: Write the implementation**

`core/ride/src/main/kotlin/dev/gdk/bumblebee/ride/DemoRideSource.kt`:

```kotlin
package dev.gdk.bumblebee.ride

import dev.gdk.bumblebee.protocol.TelemetryFrame

/**
 * Deterministic scripted ~10-minute urban demo ride (spec §6 demo mode seed).
 * Speed script (per 5 s frame, 120 frames): warm-up idle, two cruise legs with a
 * mid-ride stop, cool-down idle. One fuel-bar drop at the 60% mark.
 */
class DemoRideSource(
    private val startOdoKm: Int = 15_000,
    private val startBars: Int = 5,
    private val startTs: Long = 0L,
) {
    companion object {
        const val DURATION_SEC = 600
        const val TELEMETRY_PERIOD_SEC = 5
        private const val BAR_DROP_AT_FRAME = 72 // 60% of 120 frames

        /** Speed in km/h for second [t] of the ride. Piecewise trapezoid profile. */
        internal fun speedAt(t: Int): Int = when {
            t < 30 -> 0                                    // warm-up idle
            t < 60 -> (t - 30) * 2                         // ramp 0 -> 60
            t < 240 -> 45 + ((t / 10) % 3) * 5             // urban cruise 45-55
            t < 270 -> maxOf(0, 50 - (t - 240) * 2)        // slow to stop (signal)
            t < 300 -> 0                                   // stopped
            t < 330 -> minOf(60, (t - 300) * 2)            // pull away
            t < 540 -> 50 + ((t / 15) % 3) * 5             // cruise 50-60
            t < 570 -> maxOf(0, 60 - (t - 540) * 2)        // arrive
            else -> 0                                      // cool-down idle
        }
    }

    fun telemetry(): List<Pair<Long, TelemetryFrame>> {
        val frames = mutableListOf<Pair<Long, TelemetryFrame>>()
        var odoMeters = 0.0
        for (i in 0 until DURATION_SEC / TELEMETRY_PERIOD_SEC) {
            val t = i * TELEMETRY_PERIOD_SEC
            val speed = speedAt(t)
            odoMeters += speed / 3.6 * TELEMETRY_PERIOD_SEC
            val bars = if (i >= BAR_DROP_AT_FRAME) startBars - 1 else startBars
            frames += (startTs + t * 1000L) to TelemetryFrame(
                speedKmh = speed,
                odometerKm = startOdoKm + (odoMeters / 1000).toInt(),
                tripAKm = odoMeters / 1000,
                tripBKm = odoMeters / 1000,
                fuelBars = bars,
                fuelEconKml = 42.0,
            )
        }
        return frames
    }

    fun gps(): List<GpsFix> {
        val fixes = mutableListOf<GpsFix>()
        var lat = 12.9716 // Bengaluru-ish start point
        val lng = 77.5946
        for (t in 0 until DURATION_SEC) {
            val speedMps = speedAt(t) / 3.6
            lat += speedMps / 111_320.0 // ~meters per degree latitude; ride heads due north
            fixes += GpsFix(
                ts = startTs + t * 1000L,
                lat = lat,
                lng = lng,
                altitudeM = 900.0,
                bearingDeg = 0f,
                speedMps = speedMps.toFloat(),
                accuracyM = 5f,
            )
        }
        return fixes
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:ride:test`
Expected: PASS. If the "cruise 45-55 / none above 60" assertions fail, fix the *script*
(`speedAt`) rather than loosening the tests — the shape is the contract.

- [ ] **Step 5: Commit**

```bash
git add core/ride
git commit -m "feat(ride): add deterministic DemoRideSource for bike-free testing"
```

---

### Task 6: `data:local` module — Room capture schema

**Files:**
- Modify: `settings.gradle.kts` (add include)
- Modify: `gradle/libs.versions.toml` (add room/ksp/robolectric/test deps)
- Create: `data/local/build.gradle.kts`
- Create: `data/local/src/main/AndroidManifest.xml`
- Create: `data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/Entities.kt`
- Create: `data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/Daos.kt`
- Create: `data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/BumblebeeDb.kt`
- Test: `data/local/src/test/kotlin/dev/gdk/bumblebee/data/local/CaptureSchemaTest.kt`

**Interfaces:**
- Consumes: nothing from other tasks (entities are standalone; the repository in Task 7 maps core:ride types onto them).
- Produces: Room database `BumblebeeDb` (version 1) with entities `RideEntity`, `TelemetrySampleEntity`, `TrackPointEntity`, `FuelEventEntity`, `DailyRollupEntity`, `CalibrationEntity`; DAOs `RideDao`, `TelemetryDao`, `TrackDao`, `FuelDao`, `RollupDao`, `CalibrationDao` (methods listed in the code below); `BumblebeeDb.inMemory(context)` test factory.

- [ ] **Step 1: Register the module and dependencies**

Append to `settings.gradle.kts`:

```kotlin
include(":data:local")
```

In `gradle/libs.versions.toml` add under `[versions]`:

```toml
room = "2.7.1"
ksp = "2.1.0-1.0.29"
robolectric = "4.14.1"
androidxTestCore = "1.6.1"
```

under `[libraries]`:

```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
```

under `[plugins]`:

```toml
android-library = { id = "com.android.library", version.ref = "agp" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

Create `data/local/src/main/AndroidManifest.xml`:

```xml
<manifest />
```

Create `data/local/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.gdk.bumblebee.data.local"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:ride"))
    implementation(project(":core:protocol"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: Write the failing test**

`data/local/src/test/kotlin/dev/gdk/bumblebee/data/local/CaptureSchemaTest.kt`:

```kotlin
package dev.gdk.bumblebee.data.local

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureSchemaTest {

    private lateinit var db: BumblebeeDb

    @Before
    fun setUp() {
        db = BumblebeeDb.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `ride lifecycle - insert open ride, finalize, read back`() = runTest {
        val rideId = db.rideDao().insert(RideEntity(startTs = 1_000L))
        val open = db.rideDao().byId(rideId)!!
        assertNull(open.endTs)

        db.rideDao().update(
            open.copy(
                endTs = 601_000L, odoStartKm = 15_000, odoEndKm = 15_007,
                gpsDistanceM = 7_150.0, movingSec = 500, idleSec = 95,
                maxSpeedKmh = 60, avgMovingSpeedKmh = 51.4,
                fuelBarsStart = 5, fuelBarsEnd = 4,
                speedHistogramJson = "{\"IDLE\":95}",
            )
        )
        val closed = db.rideDao().byId(rideId)!!
        assertEquals(601_000L, closed.endTs)
        assertEquals(15_007, closed.odoEndKm)
        assertNull(closed.remoteId) // sync fields exist and default to null (D-016)
    }

    @Test
    fun `telemetry and track points are archived per ride and counted`() = runTest {
        val rideId = db.rideDao().insert(RideEntity(startTs = 0L))
        db.telemetryDao().insertAll(
            (0 until 10).map {
                TelemetrySampleEntity(
                    rideId = rideId, ts = it * 5_000L, speedKmh = 40,
                    odometerKm = 15_000, tripAKm = 0.0, tripBKm = 0.0,
                    fuelBars = 5, fuelEconKml = 42.0,
                )
            }
        )
        db.trackDao().insertAll(
            (0 until 50).map {
                TrackPointEntity(
                    rideId = rideId, ts = it * 1_000L, lat = 12.97, lng = 77.59,
                    altitudeM = 900.0, bearingDeg = 0f, speedMps = 11f, accuracyM = 5f,
                )
            }
        )
        assertEquals(10, db.telemetryDao().countForRide(rideId))
        assertEquals(50, db.trackDao().countForRide(rideId))
    }

    @Test
    fun `fuel events store both types and read back chronologically`() = runTest {
        db.fuelDao().insert(FuelEventEntity(ts = 2_000L, rideId = null, odometerKm = 15_100, barsBefore = 4, barsAfter = 3, type = "BAR_DROP"))
        db.fuelDao().insert(FuelEventEntity(ts = 1_000L, rideId = null, odometerKm = 15_050, barsBefore = 5, barsAfter = 4, type = "BAR_DROP"))
        val events = db.fuelDao().allChronological()
        assertEquals(listOf(1_000L, 2_000L), events.map { it.ts })
        assertNull(events[0].litres) // rider inputs are optional
    }

    @Test
    fun `daily rollup upserts by date`() = runTest {
        db.rollupDao().upsert(DailyRollupEntity(date = "2026-07-28", km = 7.0, rides = 1, movingSec = 500, maxSpeedKmh = 60, fuelBarsUsed = 1))
        db.rollupDao().upsert(DailyRollupEntity(date = "2026-07-28", km = 19.0, rides = 2, movingSec = 1400, maxSpeedKmh = 84, fuelBarsUsed = 1))
        val day = db.rollupDao().byDate("2026-07-28")!!
        assertEquals(19.0, day.km, 0.0)
        assertEquals(2, day.rides)
    }

    @Test
    fun `calibration is a string key-value store`() = runTest {
        db.calibrationDao().put(CalibrationEntity(key = "km_per_bar", value = "{\"4\":48.0}"))
        db.calibrationDao().put(CalibrationEntity(key = "km_per_bar", value = "{\"4\":44.0}"))
        assertEquals("{\"4\":44.0}", db.calibrationDao().get("km_per_bar")!!.value)
        assertNull(db.calibrationDao().get("missing"))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :data:local:testDebugUnitTest`
Expected: FAIL — unresolved references (entities, DAOs, `BumblebeeDb`).
Note: the first run downloads Room/Robolectric artifacts; Robolectric also fetches an
android-all jar on first use.

- [ ] **Step 4: Write the implementation**

`data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/Entities.kt`:

```kotlin
package dev.gdk.bumblebee.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One ride = one BLE/engine session (spec §4). Sync-ready fields per D-016. */
@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTs: Long,
    val endTs: Long? = null,
    val odoStartKm: Int? = null,
    val odoEndKm: Int? = null,
    val gpsDistanceM: Double = 0.0,
    val movingSec: Int = 0,
    val idleSec: Int = 0,
    val maxSpeedKmh: Int = 0,
    val avgMovingSpeedKmh: Double = 0.0,
    val fuelBarsStart: Int? = null,
    val fuelBarsEnd: Int? = null,
    val speedHistogramJson: String? = null,
    val remoteId: String? = null,
    val syncedAt: Long? = null,
)

/** Raw a537 archive — every frame, verbatim (insights spec B1). */
@Entity(tableName = "telemetry_samples", indices = [Index("rideId")])
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val ts: Long,
    val speedKmh: Int,
    val odometerKm: Int,
    val tripAKm: Double,
    val tripBKm: Double,
    val fuelBars: Int?,
    val fuelEconKml: Double?,
)

/** 1 Hz GPS archive (insights spec B1). */
@Entity(tableName = "track_points", indices = [Index("rideId")])
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val altitudeM: Double?,
    val bearingDeg: Float?,
    val speedMps: Float?,
    val accuracyM: Float?,
)

/** Fuel-bar transitions + refuels; litres/cost are optional rider input (insights spec B2). */
@Entity(tableName = "fuel_events", indices = [Index("ts")])
data class FuelEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val rideId: Long?,
    val odometerKm: Int,
    val barsBefore: Int,
    val barsAfter: Int,
    val type: String, // "BAR_DROP" | "REFUEL" — mirrors ride.FuelEventType
    val litres: Double? = null,
    val cost: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val remoteId: String? = null,
    val syncedAt: Long? = null,
)

/** Per-day aggregates so trends stay O(days) (insights spec B4). date = ISO yyyy-MM-dd. */
@Entity(tableName = "daily_rollups")
data class DailyRollupEntity(
    @PrimaryKey val date: String,
    val km: Double,
    val rides: Int,
    val movingSec: Int,
    val maxSpeedKmh: Int,
    val fuelBarsUsed: Int,
)

/** Learned constants (km-per-bar curve, odo-vs-GPS factor, ...) as a KV store (spec B4). */
@Entity(tableName = "calibration")
data class CalibrationEntity(
    @PrimaryKey val key: String,
    val value: String,
)
```

`data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/Daos.kt`:

```kotlin
package dev.gdk.bumblebee.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RideDao {
    @Insert suspend fun insert(ride: RideEntity): Long
    @Update suspend fun update(ride: RideEntity)
    @Query("SELECT * FROM rides WHERE id = :id") suspend fun byId(id: Long): RideEntity?
    @Query("SELECT * FROM rides ORDER BY startTs DESC") suspend fun allNewestFirst(): List<RideEntity>
}

@Dao
interface TelemetryDao {
    @Insert suspend fun insertAll(samples: List<TelemetrySampleEntity>)
    @Insert suspend fun insert(sample: TelemetrySampleEntity)
    @Query("SELECT COUNT(*) FROM telemetry_samples WHERE rideId = :rideId")
    suspend fun countForRide(rideId: Long): Int
    @Query("SELECT * FROM telemetry_samples WHERE rideId = :rideId ORDER BY ts")
    suspend fun forRide(rideId: Long): List<TelemetrySampleEntity>
}

@Dao
interface TrackDao {
    @Insert suspend fun insertAll(points: List<TrackPointEntity>)
    @Insert suspend fun insert(point: TrackPointEntity)
    @Query("SELECT COUNT(*) FROM track_points WHERE rideId = :rideId")
    suspend fun countForRide(rideId: Long): Int
    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY ts")
    suspend fun forRide(rideId: Long): List<TrackPointEntity>
}

@Dao
interface FuelDao {
    @Insert suspend fun insert(event: FuelEventEntity): Long
    @Query("SELECT * FROM fuel_events ORDER BY ts") suspend fun allChronological(): List<FuelEventEntity>
}

@Dao
interface RollupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(rollup: DailyRollupEntity)
    @Query("SELECT * FROM daily_rollups WHERE date = :date") suspend fun byDate(date: String): DailyRollupEntity?
}

@Dao
interface CalibrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entry: CalibrationEntity)
    @Query("SELECT * FROM calibration WHERE `key` = :key") suspend fun get(key: String): CalibrationEntity?
}
```

`data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/BumblebeeDb.kt`:

```kotlin
package dev.gdk.bumblebee.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RideEntity::class,
        TelemetrySampleEntity::class,
        TrackPointEntity::class,
        FuelEventEntity::class,
        DailyRollupEntity::class,
        CalibrationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BumblebeeDb : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun trackDao(): TrackDao
    abstract fun fuelDao(): FuelDao
    abstract fun rollupDao(): RollupDao
    abstract fun calibrationDao(): CalibrationDao

    companion object {
        private const val DB_NAME = "bumblebee.db"

        fun create(context: Context): BumblebeeDb =
            Room.databaseBuilder(context, BumblebeeDb::class.java, DB_NAME).build()

        /** In-memory instance for tests. */
        fun inMemory(context: Context): BumblebeeDb =
            Room.inMemoryDatabaseBuilder(context, BumblebeeDb::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :data:local:testDebugUnitTest`
Expected: PASS (5 tests). Confirm `data/local/schemas/dev.gdk.bumblebee.data.local.BumblebeeDb/1.json` was generated (schema export for future migrations).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml data/local
git commit -m "feat(data): add Room capture schema - rides, telemetry, track, fuel, rollups, calibration"
```

---

### Task 7: RideCaptureRepository + end-to-end demo ride test

**Files:**
- Create: `data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/RideCaptureRepository.kt`
- Test: `data/local/src/test/kotlin/dev/gdk/bumblebee/data/local/RideCaptureRepositoryTest.kt`

**Interfaces:**
- Consumes: `BumblebeeDb` + DAOs (Task 6); `RideRecorder`/`RideAggregates` (Task 2); `FuelEventDetector`/`FuelEvent`/`FuelEventType` (Task 3); `BarCalibrator` (Task 4); `TelemetrySample.fromFrame` and `GpsFix` (Task 1); `DemoRideSource` (Task 5); `TelemetryFrame` (core:protocol).
- Produces: `RideCaptureRepository(db: BumblebeeDb)` with `suspend fun startRide(ts: Long): Long`, `suspend fun onTelemetry(frame: TelemetryFrame, ts: Long)`, `suspend fun onGps(fix: GpsFix)`, `suspend fun finishRide(ts: Long): RideEntity`, `val activeRideId: Long?`. This is the single entry point the future BLE foreground service will call.

- [ ] **Step 1: Write the failing test**

`data/local/src/test/kotlin/dev/gdk/bumblebee/data/local/RideCaptureRepositoryTest.kt`:

```kotlin
package dev.gdk.bumblebee.data.local

import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.ride.DemoRideSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RideCaptureRepositoryTest {

    private lateinit var db: BumblebeeDb
    private lateinit var repo: RideCaptureRepository

    @Before
    fun setUp() {
        db = BumblebeeDb.inMemory(ApplicationProvider.getApplicationContext())
        repo = RideCaptureRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `captures a full demo ride end to end`() = runTest {
        val demo = DemoRideSource(startOdoKm = 15_000, startBars = 5, startTs = 0L)

        val rideId = repo.startRide(ts = 0L)
        assertEquals(rideId, repo.activeRideId)
        for ((ts, frame) in demo.telemetry()) repo.onTelemetry(frame, ts)
        for (fix in demo.gps()) repo.onGps(fix)
        val ride = repo.finishRide(ts = 600_000L)
        assertNull(repo.activeRideId)

        // Raw archive: every frame and every fix persisted (the golden rule).
        assertEquals(120, db.telemetryDao().countForRide(rideId))
        assertEquals(600, db.trackDao().countForRide(rideId))

        // Aggregates on the ride row.
        assertEquals(600_000L, ride.endTs)
        assertEquals(15_000, ride.odoStartKm)
        assertEquals(60, ride.maxSpeedKmh)
        assertEquals(5, ride.fuelBarsStart)
        assertEquals(4, ride.fuelBarsEnd)
        assertTrue("moving time should dominate", ride.movingSec > ride.idleSec)
        assertTrue("gps distance > 5km, was ${ride.gpsDistanceM}", ride.gpsDistanceM > 5_000)
        assertNotNull(ride.speedHistogramJson)

        // The demo ride's one bar drop became a persisted fuel event.
        val fuelEvents = db.fuelDao().allChronological()
        assertEquals(1, fuelEvents.size)
        assertEquals("BAR_DROP", fuelEvents[0].type)
        assertEquals(5, fuelEvents[0].barsBefore)
        assertEquals(4, fuelEvents[0].barsAfter)
        assertEquals(rideId, fuelEvents[0].rideId)

        // Daily rollup updated for the ride's start date.
        val date = Instant.ofEpochMilli(0L).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val rollup = db.rollupDao().byDate(date)!!
        assertEquals(1, rollup.rides)
        assertEquals(60, rollup.maxSpeedKmh)
        assertEquals(1, rollup.fuelBarsUsed)
        assertTrue(rollup.km > 5.0)
    }

    @Test
    fun `two rides on the same day accumulate in one rollup`() = runTest {
        val demo1 = DemoRideSource(startTs = 0L)
        repo.startRide(0L)
        for ((ts, frame) in demo1.telemetry()) repo.onTelemetry(frame, ts)
        repo.finishRide(600_000L)

        val demo2 = DemoRideSource(startTs = 1_000_000L, startBars = 4)
        repo.startRide(1_000_000L)
        for ((ts, frame) in demo2.telemetry()) repo.onTelemetry(frame, ts)
        repo.finishRide(1_600_000L)

        val date = Instant.ofEpochMilli(0L).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        assertEquals(2, db.rollupDao().byDate(date)!!.rides)
    }

    @Test(expected = IllegalStateException::class)
    fun `starting a ride while one is active throws`() = runTest {
        repo.startRide(0L)
        repo.startRide(1L)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:local:testDebugUnitTest --tests "dev.gdk.bumblebee.data.local.RideCaptureRepositoryTest"`
Expected: FAIL — unresolved reference `RideCaptureRepository`.

- [ ] **Step 3: Write the implementation**

`data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/RideCaptureRepository.kt`:

```kotlin
package dev.gdk.bumblebee.data.local

import dev.gdk.bumblebee.protocol.TelemetryFrame
import dev.gdk.bumblebee.ride.BarCalibrator
import dev.gdk.bumblebee.ride.FuelEvent
import dev.gdk.bumblebee.ride.FuelEventDetector
import dev.gdk.bumblebee.ride.FuelEventType
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideRecorder
import dev.gdk.bumblebee.ride.TelemetrySample
import java.time.Instant
import java.time.ZoneId

/**
 * Single entry point for ride capture. The BLE foreground service (later plan) calls
 * [startRide] on session start, [onTelemetry]/[onGps] as data streams in, and
 * [finishRide] on disconnect. Raw data is archived immediately (golden rule: capture
 * everything from ride one); aggregates land on the ride row at finish.
 */
class RideCaptureRepository(private val db: BumblebeeDb) {

    private val recorder = RideRecorder()
    private val fuelDetector = FuelEventDetector()
    private val calibrator = BarCalibrator()

    var activeRideId: Long? = null
        private set

    suspend fun startRide(ts: Long): Long {
        check(activeRideId == null) { "a ride is already active" }
        val id = db.rideDao().insert(RideEntity(startTs = ts))
        recorder.start(ts)
        fuelDetector.reset() // keep last stable bars; catches refuels between rides
        activeRideId = id
        return id
    }

    suspend fun onTelemetry(frame: TelemetryFrame, ts: Long) {
        val rideId = checkNotNull(activeRideId) { "no active ride" }
        val sample = TelemetrySample.fromFrame(frame, ts)

        db.telemetryDao().insert(
            TelemetrySampleEntity(
                rideId = rideId, ts = sample.ts, speedKmh = sample.speedKmh,
                odometerKm = sample.odometerKm, tripAKm = sample.tripAKm,
                tripBKm = sample.tripBKm, fuelBars = sample.fuelBars,
                fuelEconKml = sample.fuelEconKml,
            )
        )
        recorder.onTelemetry(sample)
        fuelDetector.onSample(sample)?.let { persistFuelEvent(it, rideId) }
    }

    suspend fun onGps(fix: GpsFix) {
        val rideId = checkNotNull(activeRideId) { "no active ride" }
        db.trackDao().insert(
            TrackPointEntity(
                rideId = rideId, ts = fix.ts, lat = fix.lat, lng = fix.lng,
                altitudeM = fix.altitudeM, bearingDeg = fix.bearingDeg,
                speedMps = fix.speedMps, accuracyM = fix.accuracyM,
            )
        )
        recorder.onGps(fix)
    }

    suspend fun finishRide(ts: Long): RideEntity {
        val rideId = checkNotNull(activeRideId) { "no active ride" }
        val agg = recorder.stop(ts)
        val ride = checkNotNull(db.rideDao().byId(rideId)).copy(
            endTs = agg.endTs,
            odoStartKm = agg.odoStartKm,
            odoEndKm = agg.odoEndKm,
            gpsDistanceM = agg.gpsDistanceM,
            movingSec = agg.movingSec,
            idleSec = agg.idleSec,
            maxSpeedKmh = agg.maxSpeedKmh,
            avgMovingSpeedKmh = agg.avgMovingSpeedKmh,
            fuelBarsStart = agg.fuelBarsStart,
            fuelBarsEnd = agg.fuelBarsEnd,
            speedHistogramJson = agg.histogram.toJson(),
        )
        db.rideDao().update(ride)
        updateRollup(ride)
        updateCalibration()
        activeRideId = null
        return ride
    }

    private suspend fun persistFuelEvent(event: FuelEvent, rideId: Long) {
        db.fuelDao().insert(
            FuelEventEntity(
                ts = event.ts, rideId = rideId, odometerKm = event.odometerKm,
                barsBefore = event.barsBefore, barsAfter = event.barsAfter,
                type = event.type.name,
            )
        )
    }

    private suspend fun updateRollup(ride: RideEntity) {
        val date = Instant.ofEpochMilli(ride.startTs)
            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val km = ride.odoDeltaKm() ?: (ride.gpsDistanceM / 1000.0)
        val barsUsed = maxOf(0, (ride.fuelBarsStart ?: 0) - (ride.fuelBarsEnd ?: 0))
        val prev = db.rollupDao().byDate(date)
        db.rollupDao().upsert(
            DailyRollupEntity(
                date = date,
                km = (prev?.km ?: 0.0) + km,
                rides = (prev?.rides ?: 0) + 1,
                movingSec = (prev?.movingSec ?: 0) + ride.movingSec,
                maxSpeedKmh = maxOf(prev?.maxSpeedKmh ?: 0, ride.maxSpeedKmh),
                fuelBarsUsed = (prev?.fuelBarsUsed ?: 0) + barsUsed,
            )
        )
    }

    /** Re-learn the km-per-bar curve from full fuel history and persist it (spec B4). */
    private suspend fun updateCalibration() {
        val events = db.fuelDao().allChronological().map {
            FuelEvent(
                ts = it.ts, odometerKm = it.odometerKm, barsBefore = it.barsBefore,
                barsAfter = it.barsAfter, type = FuelEventType.valueOf(it.type),
            )
        }
        calibrator.update(events)
        val json = (1..6).mapNotNull { bar ->
            calibrator.kmPerBar(bar)?.let { "\"$bar\":$it" }
        }.joinToString(",", "{", "}")
        db.calibrationDao().put(CalibrationEntity(key = "km_per_bar", value = json))
    }

    private fun RideEntity.odoDeltaKm(): Double? {
        val s = odoStartKm ?: return null
        val e = odoEndKm ?: return null
        return (e - s).toDouble()
    }
}
```

- [ ] **Step 4: Run the full test suite**

Run: `./gradlew :core:protocol:test :core:ride:test :data:local:testDebugUnitTest`
Expected: ALL PASS. This proves the whole pipeline: demo frames → sample archive + streaming
aggregation + fuel events → finalized ride row → daily rollup → calibration KV.

Also run `./gradlew :app:assembleDebug` — the app module is untouched, but this confirms the
new modules don't break the build graph.

- [ ] **Step 5: Commit**

```bash
git add data/local
git commit -m "feat(data): add RideCaptureRepository - end-to-end capture from telemetry to rollups"
```

---

## Out of scope for this plan (explicitly deferred)

- **BLE service wiring** (foreground service calling the repository) — next plan, per the
  parent spec §6 pairing/connection scope.
- **Real GPS source** (FusedLocationProvider) — arrives with the BLE/ride-recording plan;
  the repository already accepts `GpsFix`.
- **Hilt DI** — parent spec plans Hilt; until the app wires these modules, manual
  construction (`RideCaptureRepository(BumblebeeDb.create(context))`) is sufficient.
- **`ride_events` table (IMU events)** — tier ③ in the insights spec; the schema gains a
  table in a v2 migration (schema export is enabled to make that safe).
- **`journeys` table** — journey grouping is v1.5 in the parent spec; add via migration then.
- **Ride-row weather snapshot, geocoded start/end place, elevation gain/max** (spec B4) —
  these need the weather API and geocoding integrations. Nothing is lost by deferring:
  start/end locations and elevation are derivable from the archived `track_points`, and
  historical weather can be fetched later by timestamp + location. Columns arrive with the
  integrations via migration.
- **Track-point thinning when stationary** (spec B1) — an optimization for the real GPS
  source; the demo feed doesn't need it and the repository API doesn't change.
- **Any UI** — Cockpit/Explorer read models come with their pillar plans.
