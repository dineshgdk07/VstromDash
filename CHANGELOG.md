# Changelog

All notable changes to Bumblebee. Versions follow [Semantic Versioning](https://semver.org/);
0.x releases are betas.

## [Unreleased]

## [0.2.1] - 2026-10-26

### Changed
- **Unlocking notification access and SMS:** on Android 13+, a downloaded install has both
  locked ("App was denied access"). The app now guides you through it with a picture of where to
  tap, opens App info for you, and carries on by itself once they're unlocked.

## [0.2.0] - 2026-10-26

### Added
- **Ride video (beta):** Share → Share video makes a 3D flyover of the ride on the phone: real
  terrain, light from the ride's time of day, a camera that dives in, chases the route, pauses on
  highlights and circles the finish, with live stats and a closing card. 9:16, 16:9 or 1:1, saved to
  Movies/Bumblebee.
- **Ride Video Studio:** styles, live preview, and settings for story (cold open, chapters, map
  reveal), camera, world (time of day, clouds, water, town names) and you (your bike as the
  marker, a cluster-style dash, ride tags, your photos as postcards).
- **3D bike in the ride video:** the default marker is now a 3D bike in your paint, with an
  optional rider. It points along the road, leans into bends based on your real speed, casts a
  soft shadow and turns its headlamp on after dark. The glow dot and the bike photo are still
  options.
- **Ride video motion and music:** the trail can be coloured by your speed or the gradient,
  with a key; titles type themselves on and numbers count up; round distances and heights get a
  burst of light. Add a song from your phone: the app finds its beat, starts it so the drop hits
  as the camera dives in, lands the cuts on the beat and pulses the trail with it, with volume,
  fades and start point adjustable. The preview plays it in time.
- **Share card photos:** move and zoom your photo behind the card (drag and pinch, with a thirds
  grid), and reset it with one tap.
- **Install help:** a step-by-step install guide, and in-app help when Android greys out
  notification access ("Restricted setting") for a downloaded install.

## [0.1.0] - 2026-10-25

First public beta.

### Added
- **Cockpit:** readiness check (fuel, weather, link, service), live trip computer, ride
  suggestions, odometer milestones, "on this day" and where you parked.
- **Ride:** recording over the cluster link or GPS only, live splits, fuel ahead, break nudges and
  a ghost of your usual pace; afterwards a ride score, replay map, speed and elevation chart.
- **Journey:** cluster turn arrows from Google Maps notifications, with arrow fixes you can teach;
  optional in-app navigation with your own Google or OpenRouteService key, with a key health check;
  trip log, saved places, arrival memory, night and rain layout.
- **Explorer:** ride calendar, monthly goal, records, year heatmap, territory map, badges,
  challenges, riding DNA and ride compare.
- **Garage:** bike health, fuel log and insights, documents with expiry reminders, service plans
  and history, parts wear, cost of ownership and a bike passport.
- **Safety:** crash alerts with an "I'm OK" countdown, editable SOS message with a preview, up to
  three emergency contacts, practice alarm and test SMS.
- **Onboarding:** bike colour with a matching accent (or your own), registration number, contacts.
- **Home-screen widgets:** seven widgets in the new look.
- **Share cards:** four designs with map, photo or transparent backgrounds.
- **Transfer:** single-file backup and restore.
- **Advanced settings and developer options:** nav and alarm tuning, map cache, data clean-up,
  live link view, database counts and a shareable debug report.

### Known limitations
- Tested on the V-Strom 250 SX cluster only.
- Crash detection is a best-effort aid, not a certified emergency device.
