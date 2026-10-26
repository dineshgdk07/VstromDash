# Installing Bumblebee

Bumblebee isn't on the Play Store, so you install it from the APK on the
[Releases page](https://github.com/dineshgdk07/VstromDash/releases). It takes about two minutes.
Android 8.0 or newer.

## Why Play Protect blocks it

To show Google Maps' turn arrows on the bike's cluster, Bumblebee reads Maps' navigation
notification. That needs **notification access**. In India (and a few other countries), Google
Play Protect blocks any app installed from a download that asks for notification access, because
scam apps use it to read OTPs. It blocks on the permission alone, whoever made the app.

Bumblebee reads only Google Maps' navigation notification and sends nothing anywhere. The full
source is in this repository, and each release lists the APK's SHA-256 checksum so you can check
your download matches the one built here.

## Install

1. On your phone, open the latest release and download `Bumblebee-vX.Y.Z.apk`.
2. **Pause Play Protect scanning:** open the **Play Store** → tap your profile picture →
   **Play Protect** → ⚙️ (top right) → turn off **Scan apps with Play Protect**.
3. Open the downloaded APK (from the browser's downloads or your file manager).
   - If asked, allow your browser or file manager to **install unknown apps**.
   - Xiaomi, Redmi and POCO (HyperOS/MIUI) may show their own warning with a countdown: wait for
     it, then tap **Install anyway**.
4. **Turn Play Protect scanning back on** (same place as step 2). Bumblebee stays installed.

## Turn on notification access (Android 13 and newer)

Android greys out notification access for apps installed from a download and shows
**"Restricted setting"**. To allow it:

1. Open **Settings → Apps → Bumblebee** (or long-press the app icon → **App info**).
2. Tap **⋮** at the top right → **Allow restricted settings**, and confirm with your PIN or
   fingerprint.
3. Back in Bumblebee, turn on notification access when asked (Journey, or Garage → Cluster check
   → Permissions). The app walks you through this too.

## Updates

Download the new APK and install it over the old one; your rides and settings stay. Play Protect
may need pausing again for each update (steps 2 and 4).

## Without pausing Play Protect

Installing from a computer over USB is never blocked:

```
adb install -r Bumblebee-vX.Y.Z.apk
```

## Check your download (optional)

```
sha256sum Bumblebee-vX.Y.Z.apk
```

The result should match the `.sha256` file on the release page.
