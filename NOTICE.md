# Notices

The [MIT Licence](LICENSE) covers the original code in this repository. The material below is
**not** covered by it and keeps its own terms.

## Cluster protocol (`core/protocol`)

The Suzuki cluster Bluetooth protocol was reverse-engineered by Arjun in
[mrwick1/suzuki-connect-re](https://github.com/mrwick1/suzuki-connect-re) (REDLINE). The code in
`core/protocol` is ported from that project with only small changes. That project has not
published a licence, so this code remains its author's work and is included here with credit while
permission is requested. If you are that author and want it changed or removed, please open an
issue and it will be done promptly.

The facts of the protocol (byte layouts, characteristic UUIDs) are documented in the upstream
project's `NOTES.md`.

## Motorcycle images and launcher icon

`app/src/main/res/drawable-nodpi/bike_*.webp` and `app/src/main/res/mipmap-*/ic_launcher.png` are
reduced versions of Suzuki Motor Corporation's official product photos of the V-Strom 250 SX. They
are used only so a non-commercial, personal companion app can show the rider which paint they own.
All rights remain with Suzuki. They will be replaced with original artwork if requested.

"Suzuki", "V-Strom", "Suzuki Ride Connect" and the Suzuki "S" logo are trademarks of Suzuki Motor
Corporation. This project is not affiliated with, endorsed or sponsored by Suzuki.

## Terrain data (ride videos)

Ride videos use elevation from [AWS Terrain Tiles](https://registry.opendata.aws/terrain-tiles/)
(Mapzen's Terrarium tiles), fetched at export time and cached on the phone. They are built from
SRTM, GMTED2010, ETOPO1, NED and other public sources; see the
[attribution list](https://github.com/tilezen/joerd/blob/master/docs/attribution.md). Each video's
closing card credits them.

## Fonts (`app/src/main/res/font`)

| Font | Licence | Source |
| --- | --- | --- |
| Anton | SIL Open Font License 1.1 | Vernon Adams, Google Fonts |
| Fraunces | SIL Open Font License 1.1 | Undercase Type, Google Fonts |
| JetBrains Mono | SIL Open Font License 1.1 | JetBrains |
| Nunito | SIL Open Font License 1.1 ([text](third_party/nunito/OFL.txt)) | The Nunito Project Authors |
| Permanent Marker | Apache License 2.0 | Font Diner, Google Fonts |
| Rajdhani | SIL Open Font License 1.1 | Indian Type Foundry, Google Fonts |

The SIL Open Font License text is at https://openfontlicense.org and the Apache License 2.0 text at
https://www.apache.org/licenses/LICENSE-2.0.

## Online services

- Map tiles: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, available
  under the Open Database License. Tiles are fetched from `tile.openstreetmap.org` under its
  [tile usage policy](https://operations.osmfoundation.org/policies/tiles/), with an identifying
  User-Agent and on-screen attribution.
- Weather: [Open-Meteo](https://open-meteo.com/), under CC BY 4.0.

## Libraries

Android Jetpack, Kotlin and Google Play services location are used under their own licences
(Apache License 2.0 for AndroidX and Kotlin).
