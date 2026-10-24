# Snapshot transfer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the rider save a complete bike snapshot to a folder they choose and add it into a fresh install, with an explicit overwrite choice when records clash.

**Architecture:** A JVM module (`:core:snapshot`) owns the file format, the safety checks, and the add/replace/keep plan. `:data:local` applies that plan to Room and garage files in one transaction. The app module adds BikePrefs and the Garage entry screen. The screen never decides identity or whether a write is safe; it renders the plan and the repository result.

**Tech Stack:** Kotlin 17, JUnit4, `org.json:json`, `java.util.zip`, Room with Robolectric, Jetpack Compose Forge Block.

**Spec:** `docs/superpowers/specs/2026-10-23-data-transfer-design.md`

## Global Constraints

- File magic is `bumblebee-snapshot`. Format version written by this app is `1`. Room schema written and read by this app is `8`.
- Extension is `.bbpack`. Build type is a label only and never a reason to reject.
- Shouts are exactly `PACK THE BIKE`, `BRING IT IN`, and `NOT APPLIED`.
- Empty phone: no rides, fuel events, documents, service logs, plans, rollups, or calibration rows, and `bikeMac` is null. Default prefs are not rider data.
- Clash identity is the spec table, never a Room row id.
- Children travel with the parent ride. Fuel, documents, service logs, plans, rollups, and calibration clash on their own.
- Overwrite adds non-clashes and replaces clashes. Keep adds non-clashes and leaves clashes, their children, and their files.
- An open ride (`ACTIVE` or `PAUSED`) blocks packing. A missing scan blocks packing and blocks import.
- Map tiles and the weather cache are not in the file.
- Nothing is written until pack checks are green, or until the rider taps overwrite or keep. A failed safety check has no write action.
- Garage files are staged and swapped only after the database commit. A failure before commit deletes the stage and leaves the previous files.
- Portrait UI uses `Color.kt` tokens, `ForgeTypography`, `Shape*` tokens, and `ui/components/`. No `Color(0x…)`, `fontSize =`, `RoundedCornerShape(`, or `Modifier.shadow` in the new screen. One Anton shout (`displayMedium`). Text on `BlockYellow` and `BlockCoral` is `OnBlock`.
- Do not add a sixth bottom-nav pillar. Do not restyle Garage beyond the new entry block.

## Review Focus

These are the inputs a wrong guess would mishandle. Each one has a test in the task named here.

- Two different rides that both have Room id `1` must both be added. Task 2, `same room id is not a clash`.
- Replacing a ride must drop that ride's old track points and keep the incoming ones. Keeping it must leave the old points and must not attach the incoming points. Task 4, `overwrite replaces tracks and keep does not`.
- A bad checksum must leave the database and the garage folder untouched. Task 4, `rejected snapshot does not write`.
- A zip entry that escapes `files/` must be rejected and not written to disk. Task 3, `zip entry that escapes files is rejected`.
- Default BikePrefs on an otherwise empty phone must not be a clash. Task 2, `default prefs are not a clash`.

---

### Task 1: Snapshot module and manifest gate

**Files:**
- Create: `core/snapshot/build.gradle.kts`
- Create: `core/snapshot/src/main/kotlin/dev/gdk/bumblebee/snapshot/SnapshotModel.kt`
- Create: `core/snapshot/src/main/kotlin/dev/gdk/bumblebee/snapshot/SnapshotInspect.kt`
- Modify: `settings.gradle.kts`
- Test: `core/snapshot/src/test/kotlin/dev/gdk/bumblebee/snapshot/SnapshotInspectTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `SnapshotFormat.MAGIC = "bumblebee-snapshot"`, `FORMAT = 1`, `APP_SCHEMA = 8`, `EXTENSION = "bbpack"`
  - `SnapshotSections` constants and `ORDER`: `rides`, `ride_segments`, `telemetry_samples`, `track_points`, `fuel_events`, `daily_rollups`, `calibration`, `service_logs`, `bike_documents`, `maintenance_plans`
  - Record types `SnapshotRide`, `SnapshotSegment`, `SnapshotTelemetry`, `SnapshotTrack`, `SnapshotFuel`, `SnapshotRollup`, `SnapshotCalibration`, `SnapshotService`, `SnapshotDocument`, `SnapshotPlan`. Each primary-constructor property matches the matching entity in `data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/Entities.kt`, same name and nullability, including `id`.
  - `SnapshotPrefs` with every `BikePrefs` property (`bikeMac` through `lastReminderStamp`) plus `widget: SnapshotWidget` (`fuelBars`, `rangeKm`, `serviceDueKm`, `alert`, `todayKm`, `lastRideKm`, `lastRideLabel`, `linked`, `open`, `atMs`)
  - `data class SnapshotBundle(rides, segments, telemetry, tracks, fuel, rollups, calibration, services, documents, plans, prefs, files: List<SnapshotFile>)`
  - `data class SnapshotFile(val relativePath: String, val bytes: ByteArray)`
  - `data class SnapshotManifest(magic, format, schema, exportedAt: Long, buildType: String, counts: Map<String, Int>, sha256: String)`
  - `sealed class SnapshotVerdict` with `Ok` and `Rejected(val message: String, val log: List<String>)`
  - `fun inspectManifest(manifest: SnapshotManifest, payloadSha256: String, actualCounts: Map<String, Int>): SnapshotVerdict`

The JVM module copies `:core:ride`'s Gradle plugins and Java 17 toolchain. Dependencies: `implementation("org.json:json:20240303")`, `testImplementation` JUnit. No Android plugin. `settings.gradle.kts` includes `:core:snapshot`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `current manifest is accepted`() {
    val verdict = inspectManifest(goodManifest(), payloadSha256 = "abc", actualCounts = goodManifest().counts)
    assertTrue(verdict is SnapshotVerdict.Ok)
}

@Test
fun `debug label does not reject`() {
    val verdict = inspectManifest(goodManifest().copy(buildType = "debug"), "abc", goodManifest().counts)
    assertTrue(verdict is SnapshotVerdict.Ok)
}

@Test
fun `wrong magic is rejected`() {
    val verdict = inspectManifest(goodManifest().copy(magic = "zip"), "abc", goodManifest().counts)
    assertEquals("This file is not a Bumblebee snapshot.", (verdict as SnapshotVerdict.Rejected).message)
}

@Test
fun `newer format is rejected`() {
    val verdict = inspectManifest(goodManifest().copy(format = 2), "abc", goodManifest().counts)
    assertEquals("This file was made by a newer app.", (verdict as SnapshotVerdict.Rejected).message)
}

@Test
fun `newer schema is rejected`() {
    val verdict = inspectManifest(goodManifest().copy(schema = 9), "abc", goodManifest().counts)
    assertEquals("This file was made by a newer app.", (verdict as SnapshotVerdict.Rejected).message)
}

@Test
fun `older schema is rejected until a decoder exists`() {
    val verdict = inspectManifest(goodManifest().copy(schema = 7), "abc", goodManifest().counts)
    assertEquals(
        "This file was made by an older app. Pack it again with this version.",
        (verdict as SnapshotVerdict.Rejected).message,
    )
}

@Test
fun `checksum mismatch names the damage`() {
    val verdict = inspectManifest(goodManifest(), payloadSha256 = "nope", actualCounts = goodManifest().counts)
    assertEquals("The file is damaged. The checksum does not match.", (verdict as SnapshotVerdict.Rejected).message)
}

@Test
fun `count mismatch names the section`() {
    val counts = goodManifest().counts.toMutableMap().apply { this["rides"] = 2 }
    val verdict = inspectManifest(goodManifest(), "abc", counts)
    val rejected = verdict as SnapshotVerdict.Rejected
    assertEquals("The file is damaged. The rides count does not match.", rejected.message)
    assertTrue(rejected.log.any { it.contains("rides") })
}
```

`goodManifest()` uses magic, format `1`, schema `8`, sha256 `"abc"`, buildType `"release"`, and a count of `1` for every `SnapshotSections.ORDER` entry plus `prefs`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:snapshot:test --tests dev.gdk.bumblebee.snapshot.SnapshotInspectTest`
Expected: FAIL, unresolved `inspectManifest` or missing module.

- [ ] **Step 3: Implement the model and `inspectManifest`**

Reject in this order: magic, format `> FORMAT`, schema `> APP_SCHEMA`, schema `< APP_SCHEMA`, sha256, then the first section count that differs. Log line for a count is `rides manifest 1 actual 2` using the section name. `SnapshotFile.bytes` is compared by content in `equals` if a data-class equals is used later; override `equals` and `hashCode` on `SnapshotFile` to use `bytes.contentEquals`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:snapshot:test --tests dev.gdk.bumblebee.snapshot.SnapshotInspectTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts core/snapshot
git commit -m "$(cat <<'EOF'
Add the snapshot manifest gate.

EOF
)"
```

### Task 2: Identity, empty phone, and the apply plan

**Files:**
- Create: `core/snapshot/src/main/kotlin/dev/gdk/bumblebee/snapshot/SnapshotPlan.kt`
- Test: `core/snapshot/src/test/kotlin/dev/gdk/bumblebee/snapshot/SnapshotPlanTest.kt`

**Interfaces:**
- Consumes: `SnapshotBundle`, record types, `SnapshotSections` from Task 1
- Produces:
  - `enum class RowAction { ADD, REPLACE, KEEP }`
  - `data class Clash(val section: String, val label: String, val log: String)`
  - `data class ParentDecision(val sourceId: Long, val key: String, val action: RowAction)`
  - `data class ApplyPlan(val clashes: List<Clash>, val rides: List<ParentDecision>, val fuels: List<ParentDecision>, val documents: List<ParentDecision>, val services: List<ParentDecision>, val plans: List<ParentDecision>, val rollups: Map<String, RowAction>, val calibration: Map<String, RowAction>, val prefs: RowAction)`
  - `fun planApply(incoming: SnapshotBundle, here: SnapshotBundle, overwriteClashes: Boolean): ApplyPlan`
  - `fun packIssues(bundle: SnapshotBundle, missingFiles: List<String>): List<PackIssue>`
  - `data class PackIssue(val message: String, val log: String)`
  - `fun SnapshotBundle.isEmptyPhone(): Boolean`
  - `fun rideKey(remoteId: String?, startTs: Long): String`
  - `fun fuelKey(remoteId: String?, ts: Long, odometerKm: Int, type: String): String`
  - `fun documentKey(kind: String, title: String, number: String?): String`
  - `fun serviceKey(ts: Long, item: String, odometerKm: Int): String`
  - `fun planKey(kind: String, title: String): String`

- [ ] **Step 1: Write the failing test**

Cover these cases with fixtures that set only the identity fields and leave the other entity fields at the same defaults as the Room entities:

- `same room id is not a clash`: incoming and here each have one ride, both `id = 1`, `startTs` 10 and 20, both `remoteId` null. `planApply(..., overwriteClashes = false)` has empty `clashes` and that incoming ride's action is `ADD`.
- `same start time clashes`: both `startTs = 10`, ids 1 and 99. One clash, section `rides`. `overwriteClashes = false` → `KEEP`. `overwriteClashes = true` → `REPLACE`. The other incoming ride with `startTs = 30` is `ADD` in both calls.
- `remote id wins over start time`: both `remoteId = "r1"`, start times differ. One clash.
- `fuel clashes on its own key`: same `ts`, `odometerKm`, and `type`, different ids, null `remoteId`. Section `fuel_events`. A different type is `ADD`.
- `blank document numbers are the same key`: here `number = null`, incoming `number = " "`. One document clash.
- `rollup date and calibration key clash`: the same `date` and the same calibration `key` are clashes. A different date and a different key are `ADD`. `overwriteClashes = false` keeps the clashing date and key.
- `default prefs are not a clash`: here prefs are the BikePrefs defaults (`bikeMac` null, `riderName` `"RIDER"`, `serviceIntervalKm` 0, `tankCapacityL` 12f, `remindDays` 14, `remindKm` 200, `pauseWindowMin` 45, `mapsNavToCluster` true, `remindersEnabled` true, `autoConnect` true, `crashSensitivity` `"NORMAL"`, every string field `""`) and here has no rows. Incoming prefs set `bikeMac`. `isEmptyPhone()` is true and `prefs` is `ADD`.
- `a saved bike mac can clash`: here `bikeMac = "AA"`, incoming `bikeMac = "BB"`. `prefs` is `KEEP` when `overwriteClashes` is false and `REPLACE` when true.
- `open ride blocks packing`: a bundle whose ride `rideState` is `ACTIVE`, then one `PAUSED`. `packIssues` message is `Finish or discard the open ride before packing.`
- `missing scan blocks packing`: `missingFiles = listOf("files/rc.pdf")`. Message is `A paper is missing its scan.` Log is that path.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:snapshot:test --tests dev.gdk.bumblebee.snapshot.SnapshotPlanTest`
Expected: FAIL, unresolved `planApply`.

- [ ] **Step 3: Implement keys and `planApply`**

Key rules: ride uses `remote:<id>` when `remoteId` is non-blank, otherwise `start:<startTs>`. Fuel uses `remote:<id>` when non-blank, otherwise `ts|odometerKm|type`. Document key trims `number` and treats null or blank as empty. Prefs are default only when every field matches the defaults in the test above and `widget` may differ; widget differences alone are not a clash. If here is not empty and here prefs are non-default and any prefs field differs, `prefs` is `REPLACE` or `KEEP` with the other clashes. If prefs do not clash, `prefs` is `ADD`. A clashing ride's incoming segments, telemetry, and tracks are not separate decisions; Task 4 applies them only when that ride's action is `ADD` or `REPLACE`. `packIssues` also reports an open ride when `missingFiles` is empty, and reports each missing path.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:snapshot:test --tests dev.gdk.bumblebee.snapshot.SnapshotPlanTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/snapshot/src/main/kotlin/dev/gdk/bumblebee/snapshot/SnapshotPlan.kt core/snapshot/src/test/kotlin/dev/gdk/bumblebee/snapshot/SnapshotPlanTest.kt
git commit -m "$(cat <<'EOF'
Plan snapshot adds, clashes, and pack blocks.

EOF
)"
```

### Task 3: Zip codec

**Files:**
- Create: `core/snapshot/src/main/kotlin/dev/gdk/bumblebee/snapshot/SnapshotZip.kt`
- Test: `core/snapshot/src/test/kotlin/dev/gdk/bumblebee/snapshot/SnapshotZipTest.kt`

**Interfaces:**
- Consumes: Task 1 types and `inspectManifest`
- Produces:
  - `fun writeSnapshot(out: OutputStream, bundle: SnapshotBundle, buildType: String, exportedAt: Long)`
  - `fun readSnapshot(input: InputStream): SnapshotRead`
  - `sealed class SnapshotRead` with `Ready(val bundle: SnapshotBundle, val manifest: SnapshotManifest)` and `Rejected(val message: String, val log: List<String>)`

Write one zip entry per section, named `<section>.json`, a `prefs.json`, a `files/<relative>` entry for each `SnapshotFile`, then `manifest.json`. JSON objects use the record property names. Hash the uncompressed entry bytes in `SnapshotSections.ORDER`, then `prefs.json`, then file entries sorted by path. Hex lowercase SHA-256 goes in the manifest. Do not hash `manifest.json`.

`readSnapshot` rejects, before any caller can apply it, when `inspectManifest` rejects, when a document or service `filePath` inside the JSON has no matching `files/` entry, or when any zip name is absolute, contains `..`, or is outside the known entry names. A missing scan uses the message `A paper is missing its scan.`

- [ ] **Step 1: Write the failing test**

- Round-trip a bundle with one ride, one segment pointing at that ride id, one document, and one `SnapshotFile`. `readSnapshot` is `Ready` and the ride `startTs`, segment `rideId`, and file bytes match.
- Flip one byte of `rides.json` inside a copy of the zip. `readSnapshot` message is `The file is damaged. The checksum does not match.`
- `zip entry that escapes files is rejected`: a hand-built zip with entry `files/../../tmp/pwned`. Message is `This file is not a Bumblebee snapshot.` Log contains the entry name. The test's temp directory has no `pwned` file written by the reader.
- A document whose JSON `filePath` is `files/rc.pdf` with no such entry. Message is `A paper is missing its scan.`

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:snapshot:test --tests dev.gdk.bumblebee.snapshot.SnapshotZipTest`
Expected: FAIL, unresolved `writeSnapshot`.

- [ ] **Step 3: Implement `writeSnapshot` and `readSnapshot`**

Stream each JSON array to its zip entry from the bundle list. Do not build a second full copy of the track list to hash it; hash the bytes just written. Reader extracts file entries only after the name check, into the returned `SnapshotFile.bytes`, not onto a caller-supplied directory.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:snapshot:test --tests dev.gdk.bumblebee.snapshot.SnapshotZipTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/snapshot/src/main/kotlin/dev/gdk/bumblebee/snapshot/SnapshotZip.kt core/snapshot/src/test/kotlin/dev/gdk/bumblebee/snapshot/SnapshotZipTest.kt
git commit -m "$(cat <<'EOF'
Read and write the snapshot zip.

EOF
)"
```

### Task 4: Room capture and apply

**Files:**
- Create: `data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/SnapshotArchive.kt`
- Modify: `data/local/build.gradle.kts` (add `implementation(project(":core:snapshot"))`)
- Test: `data/local/src/test/kotlin/dev/gdk/bumblebee/data/local/SnapshotArchiveTest.kt`

**Interfaces:**
- Consumes: `writeSnapshot`, `readSnapshot`, `planApply`, `packIssues`, `SnapshotBundle`, `RowAction`
- Produces:
  - `class SnapshotArchive(private val db: BumblebeeDb, private val garageRoot: File)`
  - `data class Capture(val bundle: SnapshotBundle, val missingFiles: List<String>)`
  - `suspend fun capture(): Capture`
  - `suspend fun apply(bundle: SnapshotBundle, overwriteClashes: Boolean)`
  - `fun schemaVersion(): Int`

`capture` reads every entity table. Document and service `filePath` values in the bundle are the zip-relative `files/...` paths. The bytes come from `garageRoot`. A path that is set but missing is omitted from `files` and returned in `Capture.missingFiles`.

`apply` calls `planApply`. For `ADD` and `REPLACE` rides, insert the ride, remember `sourceId -> newId`, and insert that ride's segments, telemetry, and tracks with the new id. For `KEEP`, insert nothing for that ride or its children. For `REPLACE`, delete the local ride's segments, telemetry, and tracks, then the ride, before the insert. Rewrite `rideId` on local fuel rows that were `KEEP` and pointed at the deleted ride id. Apply fuel, documents, services, and plans by their own actions. Rollups and calibration use `date` and `key` as the conflict target: `REPLACE` overwrites the row, `KEEP` skips, `ADD` inserts. Prefs are not written here.

Stage incoming document and service bytes under `garageRoot/../snapshot-stage/<session>/` and store the intended final path `garageRoot/<folder>/<newId>_<name>` on the row inside `db.withTransaction`. After the transaction commits, move staged files onto those final paths and delete the stage. If the transaction throws, delete the stage and rethrow. Do not delete a previous garage file until the move onto its path succeeds.

- [ ] **Step 1: Write the failing test**

Robolectric, `BumblebeeDb.inMemory`, temp `garageRoot`, same runner as `RideSessionTest`.

- `schema constant matches the open database`: `archive.schemaVersion()` equals `SnapshotFormat.APP_SCHEMA` and equals `db.openHelper.readableDatabase.version`.
- Empty database `capture` then `writeSnapshot` then `readSnapshot` then `apply` into a second empty database. Ride count, track count, and document file bytes match. The second database's ride id may differ. The segment's `rideId` equals the new ride id.
- `overwrite replaces tracks and keep does not`: both databases have a ride with `startTs = 10`. Here it has one track point at lat `1.0`. Incoming has one track point at lat `2.0` and a second ride `startTs = 20`. `apply(overwriteClashes = true)` leaves lat `2.0` on the clashing ride and adds the second ride. A fresh pair of databases with the same setup and `apply(overwriteClashes = false)` still has lat `1.0` on the kept ride, has the second ride, and has no track at lat `2.0`.
- `rejected snapshot does not write`: call `readSnapshot` on a zip with a broken checksum. Assert it is `Rejected` and do not call `apply`. Assert the destination ride count is unchanged. Also assert `apply` is not required for this test. Add a second assertion that a document file already in `garageRoot` is still the original bytes after a failed `apply` whose transaction throws: pass a bundle whose document file bytes are fine, and force the throw by using a `garageRoot` file that is not a directory so staging fails before commit. The pre-existing sibling file passed in as an already-saved scan must still equal its original bytes.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:local:testDebugUnitTest --tests dev.gdk.bumblebee.data.local.SnapshotArchiveTest`
Expected: FAIL, unresolved `SnapshotArchive`.

- [ ] **Step 3: Implement `SnapshotArchive`**

Map entity fields one-to-one onto the snapshot records. Insert through the existing DAOs. Keep the Room transaction around the row writes only.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:local:testDebugUnitTest --tests dev.gdk.bumblebee.data.local.SnapshotArchiveTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add data/local/build.gradle.kts data/local/src/main/kotlin/dev/gdk/bumblebee/data/local/SnapshotArchive.kt data/local/src/test/kotlin/dev/gdk/bumblebee/data/local/SnapshotArchiveTest.kt
git commit -m "$(cat <<'EOF'
Apply a snapshot onto the local database.

EOF
)"
```

### Task 5: Prefs and the transfer repository

**Files:**
- Modify: `core/ble-android/src/main/kotlin/dev/gdk/bumblebee/ble/android/BikePrefs.kt`
- Modify: `core/ble-android/src/test/kotlin/dev/gdk/bumblebee/ble/android/BikePrefsTest.kt`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/transfer/TransferRepository.kt`
- Modify: `app/build.gradle.kts` (add `implementation(project(":core:snapshot"))`)
- Test: `app/src/test/kotlin/dev/gdk/bumblebee/transfer/TransferRepositoryTest.kt`

**Interfaces:**
- Consumes: `SnapshotArchive`, `writeSnapshot`, `readSnapshot`, `packIssues`, `planApply`, `BikePrefs`, `GarageSnapshot`
- Produces:
  - `fun BikePrefs.toSnapshotPrefs(widget: SnapshotWidget): SnapshotPrefs`
  - `fun BikePrefs.writeSnapshotPrefs(prefs: SnapshotPrefs)`
  - `class TransferRepository(private val archive: SnapshotArchive, private val prefs: BikePrefs, private val widget: WidgetStore, private val buildType: String)`
  - `fun interface WidgetStore { fun read(): SnapshotWidget; fun write(widget: SnapshotWidget) }`
  - `data class PackPreview(val bundle: SnapshotBundle, val issues: List<PackIssue>, val rideCount: Int, val distanceKm: Int, val fillCount: Int)`
  - `sealed class BringIn` with `Ready(val bundle: SnapshotBundle)`, `Held(val bundle: SnapshotBundle, val clashes: List<Clash>)`, `Failed(val message: String, val log: List<String>)`
  - `suspend fun preview(): PackPreview`
  - `suspend fun writeTo(out: OutputStream)` — throws `IllegalStateException` with the first pack issue message if `issues` is not empty, and writes nothing
  - `suspend fun inspect(input: InputStream): BringIn`
  - `suspend fun commit(bundle: SnapshotBundle, overwriteClashes: Boolean)` — writes prefs only when the plan's `prefs` action is `ADD` or `REPLACE`

`distanceKm` is the sum of `gpsDistanceM` divided by `1000` with the fraction truncated. `fillCount` counts fuel events whose `type` is `REFUEL`.

- [ ] **Step 1: Write the failing test**

BikePrefs test: set `bikeMac`, `riderName`, and `tankCapacityL`, call `toSnapshotPrefs` with a widget, clear the prefs, `writeSnapshotPrefs`, assert those three values and that `writeSnapshotPrefs` does not require a widget write inside BikePrefs.

Repository test, Robolectric, in-memory db, temp garage dir, real `BikePrefs` on the application context:

- `preview` on a database with one finished ride of `gpsDistanceM = 2000` and one `REFUEL` reports `rideCount = 1`, `distanceKm = 2`, `fillCount = 1`, empty issues.
- `writeTo` with an `ACTIVE` ride throws `IllegalStateException` whose message is `Finish or discard the open ride before packing.` and the output stream length stays `0`.
- `inspect` of a snapshot whose ride `startTs` matches an existing ride returns `Held` with that clash and does not change the local track latitude.
- `commit(..., overwriteClashes = false)` then adds a non-clashing ride and leaves the clashing ride's track latitude.
- `commit(..., overwriteClashes = true)` replaces the track latitude and writes an incoming `bikeMac` onto `BikePrefs`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:ble-android:testDebugUnitTest --tests dev.gdk.bumblebee.ble.android.BikePrefsTest :app:testDebugUnitTest --tests dev.gdk.bumblebee.transfer.TransferRepositoryTest`
Expected: FAIL, unresolved `toSnapshotPrefs` or `TransferRepository`.

- [ ] **Step 3: Implement prefs mapping and `TransferRepository`**

`writeSnapshotPrefs` writes every BikePrefs property from the snapshot. `WidgetStore.write` is called from `commit` in the app repository, not from BikePrefs. `inspect` returns `Failed` unchanged from `readSnapshot`'s `Rejected`. It returns `Ready` when `planApply(..., overwriteClashes = false).clashes` is empty, otherwise `Held`. It does not call `archive.apply`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:ble-android:testDebugUnitTest --tests dev.gdk.bumblebee.ble.android.BikePrefsTest :app:testDebugUnitTest --tests dev.gdk.bumblebee.transfer.TransferRepositoryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/ble-android/src/main/kotlin/dev/gdk/bumblebee/ble/android/BikePrefs.kt core/ble-android/src/test/kotlin/dev/gdk/bumblebee/ble/android/BikePrefsTest.kt app/build.gradle.kts app/src/main/kotlin/dev/gdk/bumblebee/transfer/TransferRepository.kt app/src/test/kotlin/dev/gdk/bumblebee/transfer/TransferRepositoryTest.kt
git commit -m "$(cat <<'EOF'
Add the snapshot transfer repository.

EOF
)"
```

### Task 6: Transfer screen on Garage

**Files:**
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/transfer/TransferModels.kt`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/transfer/TransferViewModel.kt`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/transfer/TransferScreen.kt`
- Modify: `app/src/main/kotlin/dev/gdk/bumblebee/ui/garage/GarageScreen.kt` (add `onOpenTransfer: () -> Unit = {}` and one entry control)
- Modify: `app/src/main/kotlin/dev/gdk/bumblebee/ui/nav/BumblebeeApp.kt` (route `transfer`, pass the garage callback)
- Test: `app/src/test/kotlin/dev/gdk/bumblebee/ui/transfer/TransferViewModelTest.kt`

**Interfaces:**
- Consumes: `TransferRepository`, `PackPreview`, `BringIn`, `Clash`, `PackIssue`
- Produces:
  - `enum class TransferPhase { PACK, BRING_IN, HELD }`
  - `enum class CheckState { WAITING, RUNNING, DONE, WARNING, FAILED }`
  - `data class CheckRow(val title: String, val detail: String, val state: CheckState)`
  - `data class TransferUi(val phase: TransferPhase, val shout: String, val headline: String, val rows: List<CheckRow>, val message: String?, val log: List<String>, val logOpen: Boolean, val canSave: Boolean, val canOverwrite: Boolean, val canKeep: Boolean, val overwriteLabel: String)`
  - `class TransferViewModel(private val repository: TransferRepository)`
  - `fun TransferViewModel.ui: StateFlow<TransferUi>` exposed as a public `val ui`
  - `fun onPackLoaded(preview: PackPreview)`, `fun onInspected(result: BringIn)`, `fun toggleLog()`, `fun chooseOverwrite()`, `fun chooseKeep()`
  - `@Composable fun TransferScreen(model: TransferViewModel, onBack: () -> Unit, onSave: () -> Unit, onOpen: () -> Unit)`

The screen calls `onSave` only when `canSave` is true. The composable's caller in `BumblebeeApp` launches `ActivityResultContracts.CreateDocument("application/octet-stream")` with the suggested name `bumblebee-snapshot-<yyyyMMdd>.bbpack`, and `ActivityResultContracts.OpenDocument` with `arrayOf("*/*")`. Those launchers call `repository.writeTo` and `repository.inspect`. The ViewModel does not touch URIs.

Pack rows, in order, titles exactly: `Rides and tracks`, `Fuel log`, `Daily rollups`, `Service and plans`, `Garage papers`, `Paired bike`. Detail is the count, or `yes` / `no` for the paired bike from `prefs.bikeMac`. Shout `PACK THE BIKE`. Headline text is `"$rideCount rides"`. The line under it is `"$distanceKm km · $fillCount fills"`. `canSave` is true only when `issues` is empty. When issues exist, phase stays `PACK`, `message` is the first issue message, and `canSave` is false.

Bring-in rows, in order, titles exactly: `File is a Bumblebee snapshot`, `This install can read it`, `Checksum and counts`, `Phone is empty`, `Fuel log`, `Rides and tracks`, `Rollups and calibration`, `Garage papers`, `Paired bike`. `onInspected(Ready)` sets phase `BRING_IN`, shout `BRING IT IN`, rows 1–4 `DONE`, rows 5–9 `WAITING`, `message` null. `fun onCommitted()` marks rows 5–9 `DONE`. `onInspected(Held)` sets phase `HELD`, shout `NOT APPLIED`, message `"$n already live here. Nothing was replaced."` where `n` is `clashes.size`, `canOverwrite` and `canKeep` true, `overwriteLabel` exactly `Overwrite those $n`, and the log equal to `clashes.map { it.log }` while `logOpen` starts false. `onInspected(Failed)` sets phase `HELD`, shout `NOT APPLIED`, `message` from the failure, `canOverwrite` and `canKeep` false. `toggleLog` flips `logOpen`. `chooseOverwrite` and `chooseKeep` emit on `val commits: SharedFlow<Boolean>`, `true` for overwrite and `false` for keep. The screen collects `commits`, calls `repository.commit`, then `onCommitted()`. A thrown commit sets phase `HELD` with that exception message and both write actions false.

Visuals: `ForgeTopBar` with the back callback. Shout is `MaterialTheme.typography.displayMedium` and `TextPrimary`. Headline slab is `background(BlockYellow, ShapeMd)`, text `OnBlock`, numbers `NumberLarge`. Held message slab is `background(BlockCoral, ShapeMd)`. Checklist sits in a `SectionCard`. Progress during bring-in uses `ProgressPill`. Save and overwrite are `ChunkyButton` with `ChunkyStyle.Lime`. Keep is `ChunkyButton` with `ChunkyStyle.Outline` and the text `Keep what's here`. Save text is `Choose where to save`. Log is a `SectionCard` toggled by a `TextButton` labelled `Log`, shown only when `log` is non-empty. `@Preview` on `Canvas` for pack, bring-in, and held.

Garage entry: one `ChunkyButton` labelled `Transfer the bike` calling `onOpenTransfer`. Do not restyle the rest of `GarageScreen`. Route is `transfer`, pushed like `fuel`, and the bottom bar treats `transfer` as Garage the same way it treats `fuel`.

- [ ] **Step 1: Write the failing test**

A `TransferViewModel` with a fake repository. `onPackLoaded` of a preview with no issues yields shout `PACK THE BIKE`, `canSave == true`, and the six pack titles. A preview with one open-ride issue yields `canSave == false` and that message. `onInspected(Failed("The file is damaged. The checksum does not match.", listOf("sha256")))` yields shout `NOT APPLIED`, `canOverwrite == false`, and `logOpen == false` until `toggleLog()`. `onInspected(Held(bundle, listOf(Clash("rides", "12", "clash ride"))))` yields `overwriteLabel == "Overwrite those 1"`, message `1 already live here. Nothing was replaced.`, and `canKeep == true`. `onInspected(Ready)` leaves the last five rows `WAITING` until `onCommitted()`, which marks them `DONE`. `chooseKeep()` emits `false` on `commits`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests dev.gdk.bumblebee.ui.transfer.TransferViewModelTest`
Expected: FAIL, unresolved `TransferViewModel`.

- [ ] **Step 3: Implement the models, ViewModel, screen, and navigation**

Keep the screen free of repository and picker code. Wire the pickers in `BumblebeeApp` next to the `transfer` composable.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests dev.gdk.bumblebee.ui.transfer.TransferViewModelTest`
Expected: PASS

Then: `./gradlew :core:snapshot:test :data:local:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/gdk/bumblebee/ui/transfer app/src/main/kotlin/dev/gdk/bumblebee/ui/garage/GarageScreen.kt app/src/main/kotlin/dev/gdk/bumblebee/ui/nav/BumblebeeApp.kt app/src/test/kotlin/dev/gdk/bumblebee/ui/transfer/TransferViewModelTest.kt
git commit -m "$(cat <<'EOF'
Show snapshot packing and import on the Garage screen.

EOF
)"
```
