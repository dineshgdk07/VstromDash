# Snapshot transfer

The debug install has accumulated the rider's real history. A fresh production install, or a fresh debug install, should be able to become that same bike: rides, fuel, the analysis stored from them, garage papers, and the paired bike. The rider picks where the file is saved and picks that file again on the way in.

## Why

Debug and production are separate installs. Uninstalling one deletes its database. The file has to live somewhere the rider chooses, outside the app, so it survives the gap between the two installs.

## Goals

- One snapshot file that round-trips debug → production and production → debug.
- The fresh-install path adds every record in the file. That is the path this is built for.
- If the destination already has records that clash, nothing is written until the rider chooses. They can overwrite the clashes and add the rest, or add only what is new and leave the clashes alone.
- A file that is not safe to apply never changes the phone. The screen says what is wrong in plain language and can open a log for debugging.
- The rider can see what is about to move, what is moving, and what arrived.

## Non-goals

- Cloud sync, accounts, or merging two different histories field by field.
- Replacing the existing single-ride CSV and GPX export.
- Copying map tiles or the weather cache. Those rebuild.
- A sixth bottom-nav pillar.

## Anti-goals

- An import that looks finished while rides have lost their tracks, fuel, or segments.
- Replacing existing records without an explicit tap when the phone is not empty.
- Refusing a file because it was made by the other build type.
- A half-written database or garage folder if the import fails.

## What the rider decided

- The cargo is the whole stored history. Nothing they would miss after a fresh install stays behind, except the rebuildable caches called out above.
- Save uses the system picker. Import uses the system picker. The app does not choose a hidden folder.
- Empty destination: add.
- Clash: the rider can overwrite. The feature is aimed at a fresh install, and that add-or-overwrite rule is enough for the rare case where data is already there.
- An unsafe file shows an error or warning with a real message, plus an expandable log.
- The three phones they accepted are the screen: Pack, Bring in, and Held.

## The three states

One screen, `TransferScreen`, not three destinations. Forge Block, same rules as `docs/design/DESIGN_SYSTEM.md`. One Anton shout per state. No raw colours, radii, or font sizes in the screen file.

**Pack.** Shout: PACK THE BIKE. A yellow block states the headline (ride count, distance, fill count). A checklist lists each section with its count. The lime button is "Choose where to save" and opens the system create-document picker. The file is written only after every pack check is green.

**Bring in.** Shout: BRING IT IN. The rider has already picked a file. A progress pill and a "n of m" line show the step. Checklist rows tick in order. An empty phone adds. The write is one transaction: if a later check fails, nothing from this attempt remains.

**Held.** Shout: NOT APPLIED. Used when the file is readable but must not be applied yet. A coral block states the warning in one or two sentences. The checklist shows what passed and what stopped it. The log is collapsed by default and expands to the debug lines (ids, timestamps, schema, the reason). Two chunky actions when the stop is a clash: "Overwrite those N" and "Keep what's here". A file that failed a safety check has no write action.

## What is in the file

A zip the picker saves with a `.bbpack` name the rider can change. Inside:

- `manifest.json` — magic `bumblebee-snapshot`, format version, Room schema version at export (`BumblebeeDb` is 8 today), export time, build type as a label only, per-section counts, sha256 of the payload.
- One JSON array per Room table: `rides`, `ride_segments`, `telemetry_samples`, `track_points`, `fuel_events`, `daily_rollups`, `calibration`, `service_logs`, `bike_documents`, `maintenance_plans`. Rows keep the ids they had in the source database so links can be remapped.
- `files/` — bytes for every `filePath` on a document or service log, stored under a path relative to the zip. The JSON stores that relative path, not the source absolute path.
- `prefs.json` — the `BikePrefs` (`bumblebee_ble`) values and the garage-widget preferences. Package name is not part of the file.

Left out: OSM tile store, weather cache, and anything that is only a running process.

## Checks

Pack refuses to write, with a message on the checklist, when:

- A ride is `ACTIVE` or `PAUSED`. The rider finishes or discards it first. An open ride must not be restored as a live ride on the other install.
- A garage or service file path is set but the bytes are missing. That is a pack failure, not a silent skip. The log names the path.

Bring-in refuses to write, with a message and a log, when:

- The zip is not a snapshot, or a declared entry is missing.
- The format version is newer than this app.
- The schema version is newer than this app.
- The checksum does not match, or a manifest count does not match the array length.
- A `files/` entry named by a row is missing. Same rule as pack: the paper without its scan is not a successful import.

An older schema is readable when this app still has a decoder for that shape. The builder owns the decoder. A current app always writes the current shape.

## Empty, add, and clash

The phone is empty when it has no rides, fuel events, documents, service logs, plans, rollups, or calibration rows, and `BikePrefs.bikeMac` is null. Default preference values do not count as rider data.

On an empty phone, Bring in adds everything. Inserted rows get new local ids. Foreign keys are remapped from the ids stored in the file: segments, telemetry, and track points follow their ride; fuel `rideId` follows its ride when set; service `planId` follows its plan; garage files are written under `filesDir/garage/` and the new absolute path is stored on the row.

A clash is not "the autoincrement id matches". Both installs start ids at 1, so that would call unrelated rides the same ride.

| Record | Same record when |
|---|---|
| Ride | `remoteId` equal and both set, otherwise `startTs` equal |
| Fuel event | `remoteId` equal and both set, otherwise `ts` + `odometerKm` + `type` |
| Document | `kind` + `title` + `number` |
| Service log | `ts` + `item` + `odometerKm` |
| Plan | `kind` + `title` |
| Rollup | `date` |
| Calibration | `key` |
| Prefs | The destination has a non-null `bikeMac` or any non-default rider field, and the file's prefs differ |

Children travel with the parent. Overwriting a ride replaces its segments, telemetry, and track points with the file's set. Keeping a ride leaves those children untouched. A fuel event that clashes is its own row, not a child.

If any clash exists, the screen is Held and nothing is written yet.

- **Overwrite those N** adds rows that do not clash and replaces rows that do, including their children and files.
- **Keep what's here** adds rows that do not clash and leaves clashing rows, their children, and their files as they are. Prefs that clash stay as they are.

Both choices are one transaction. Garage files for rows that will be replaced are staged and swapped only after the database commit. A failure deletes the stage and leaves the previous files in place.

## Where it lives

`TransferScreen` is pushed from the Garage screen. The bottom nav stays the five pillars. Garage already holds the long-lived bike records, so the entry sits there as one block. Cockpit is unchanged.

## Left to the builder

- JSON vs another encoding inside the zip, as long as the manifest, counts, checksum, and relative file paths behave as written.
- How progress is reported across a large track table without holding every row in memory twice.
- The exact checklist copy, as long as each row is one of the sections above and the shout text stays PACK THE BIKE, BRING IT IN, and NOT APPLIED.
- Tests: round-trip on an empty database, clash then overwrite, clash then keep, corrupt checksum, newer format, missing garage bytes, and an active ride blocking pack.

## Calls made here

- Entry is Garage, not a new nav item.
- Identity is the table above, not Room row ids.
- An open ride blocks packing.
- A missing scan blocks packing and blocks import.
- Map tiles and weather stay out.
- "Keep what's here" still adds non-clashing rows. It does not cancel the whole file.
- The file extension is `.bbpack`.
