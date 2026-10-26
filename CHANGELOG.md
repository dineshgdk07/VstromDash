# Changelog

All notable changes to Bumblebee. Versions follow [Semantic Versioning](https://semver.org/);
0.x releases are betas.

## [Unreleased]

### Added
- **Ride video (beta):** Share → Share video makes a 3D flyover of the ride on the phone: real
  terrain, light from the ride's time of day, a camera that dives in, chases the route, pauses on
  highlights and circles the finish, with live stats and a closing card. 9:16, saved to
  Movies/Bumblebee.
- **Ride Video Studio:** styles, live preview, and settings for story (cold open, chapters, map
  reveal), camera, world (time of day, clouds, water, town names) and you (your bike as the
  marker, a cluster-style dash, ride tags, your photos as postcards).
- **3D bike in the ride video:** the default marker is now a 3D bike in your paint, with an
  optional rider. It points along the road, leans into bends based on your real speed, casts a
  soft shadow and turns its headlamp on after dark. The glow dot and the bike photo are still
  options.

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
