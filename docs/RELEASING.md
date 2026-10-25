# Releasing

Releases are APKs on [GitHub Releases](https://github.com/dineshgdk07/VstromDash/releases), built
and signed by the `Release` workflow when a `v*` tag is pushed.

## One-time setup

1. **Create the release keystore** on your own machine, outside the repo, and back it up somewhere
   safe (a password manager or an encrypted drive). Losing it means users can't update; they'd have
   to uninstall and lose their data.

   ```bash
   keytool -genkeypair -v -keystore ~/keys/bumblebee-release.jks \
     -alias bumblebee -keyalg RSA -keysize 4096 -validity 10000
   ```

2. **Add repository secrets** (Settings → Secrets and variables → Actions):

   | Secret | Value |
   | --- | --- |
   | `BUMBLEBEE_KEYSTORE_BASE64` | `base64 -w0 ~/keys/bumblebee-release.jks` |
   | `BUMBLEBEE_STORE_PASSWORD` | keystore password |
   | `BUMBLEBEE_KEY_ALIAS` | `bumblebee` |
   | `BUMBLEBEE_KEY_PASSWORD` | key password |
   | `MAPS_API_KEY` | optional: Maps SDK key for the Google map display |

3. **If you use a Google key with an Android restriction,** add the release certificate's SHA-1
   alongside the debug one:

   ```bash
   keytool -list -v -keystore ~/keys/bumblebee-release.jks -alias bumblebee | grep SHA1
   ```

## Each release

1. **Test the minified build on a phone** before tagging. R8 only runs on release builds, so this
   is where missing keep rules show up:

   ```bash
   ./gradlew :app:installRelease -PdebugSignRelease
   ```

   Ride checklist:
   - [ ] Pair and link; live speed, fuel and odometer on Cockpit
   - [ ] Record a ride; replay map, chart and share card afterwards
   - [ ] Maps notifications drive the cluster arrows; teach an arrow fix
   - [ ] In-app navigation with a key (if set up); off-route re-plan
   - [ ] Practice crash alarm and test SMS
   - [ ] Every home-screen widget draws and opens the right screen
   - [ ] Transfer: export, then restore
   - [ ] Settings → Advanced tools and the developer page

   The debug-signed build has a different signature from the release one; uninstall it before
   installing a real release.

2. **Update `CHANGELOG.md`:** move items from Unreleased into a new `## [x.y.z] - date` section.
   That section becomes the release notes.

3. **Tag and push:**

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

   Or, with no laptop: Actions → Release → Run workflow, and enter the version (`v0.1.0`).
   GitHub creates the tag on the commit it built.

   The workflow runs the unit tests, builds `Bumblebee-v0.1.0.apk` with its SHA-256, and publishes
   the release. Tags starting `v0.` or containing `-` (like `v1.0.0-rc1`) are marked pre-release.

## Versions

The tag sets the version: `v1.2.3` gives versionName `1.2.3` and versionCode `10203`. Always bump
the tag; Android refuses an update with a lower or equal versionCode. A suffix doesn't count, so
`v1.0.0-rc1` and `v1.0.0` share a code: test candidates with `-PdebugSignRelease` instead of tagging them.
