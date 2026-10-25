# Contributing

Thanks for wanting to help. This is a hobby project for one bike, so please open an issue to talk
about anything bigger than a small fix before you build it.

## Getting set up

1. Install JDK 17 and the Android SDK (compileSdk 35).
2. Clone the repo and run `./gradlew test` to make sure the unit tests pass.
3. `./gradlew :app:installDebug` installs a debug build next to any release build (it has a
   `.debug` suffix).

A V-Strom 250 SX isn't needed for most work: the Ride tab has a demo ride, and most logic lives in
`core:ride` with plain JVM tests.

## Making changes

- **Logic in `core`, UI in `app`.** Anything that can be a pure function (insights, parsing,
  maths) goes in a `core` module with a unit test.
- **Match the code around you:** naming, comment style and Compose patterns.
- **No secrets, ever.** Keystores, passwords, API keys, real phone numbers, bike Bluetooth
  addresses and home locations stay out of commits. Use made-up values in tests and previews
  (e.g. `00:11:22:33:44:55`, `+91 98765 43210`).
- **No third-party assets without a licence** that allows it. Credit new ones in
  [NOTICE.md](NOTICE.md).
- **`core/protocol`** isn't under this repo's licence (see NOTICE.md). Keep changes there minimal.

## Pull requests

- One topic per PR, with a short description of what and why.
- `./gradlew test` passes.
- Screenshots for UI changes.
- Commits in the style `feat(garage): …`, `fix(explorer): …`.

By contributing you agree your contribution is licensed under the MIT Licence.

## Code of conduct

Be kind and assume good intent. Harassment or abuse of any kind isn't welcome, and maintainers may
remove comments or contributors who don't follow this.
