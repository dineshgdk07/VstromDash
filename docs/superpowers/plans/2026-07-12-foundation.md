# bumblebee-dash Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the bumblebee-dash Android project — a Gradle multi-module Kotlin/Compose app with the REDLINE BLE frame protocol ported into an isolated `core:protocol` module, the Forge UI theme, and a five-pillar navigation shell that builds and installs on a phone.

**Architecture:** Gradle multi-module. `core:protocol` is a pure Kotlin/JVM library (no Android deps) holding the ported 30-byte frame codec and its unit tests. `app` is the Android application: Forge UI theme + a stable bottom-nav shell with five placeholder pillar screens (Cockpit, Journey, Ride, Explorer, Garage). This is the foundation only — BLE connection, telemetry display, and nav come in later plans.

**Tech Stack:** Kotlin 2.1.0, AGP 8.10.0, Jetpack Compose (BOM 2026.05.00), navigation-compose, JUnit4, Gradle version catalog. JDK 17.

## Global Constraints

- **JDK:** 17, at `/home/gdk/.local/jdk-17` (`JAVA_HOME`). Android SDK at `/home/gdk/Android/Sdk` (`ANDROID_HOME`). Installed platforms: android-33/34/35/36. Use `source /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/env.sh` OR export both before any gradle command.
- **Versions (pin exactly):** AGP `8.10.0`, Kotlin `2.1.0`, compose-bom `2026.05.00`. `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`. Java/JVM target `17`.
- **Base package / namespace:** `dev.gdk.bumblebee`. App applicationId `dev.gdk.bumblebee` with debug suffix `.debug`.
- **REDLINE source of truth (read-only reference for ports):** `/home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/app/src/main/kotlin/dev/mrwick/redline/protocol/` (main) and `.../src/test/kotlin/dev/mrwick/redline/protocol/` (tests). Referred to below as `$REDLINE_PROTO_MAIN` and `$REDLINE_PROTO_TEST`.
- **Port rule:** when porting a REDLINE file, rewrite the package `dev.mrwick.redline.protocol` → `dev.gdk.bumblebee.protocol`, and **strip the Compose `@Immutable` annotation and its `import androidx.compose.runtime.Immutable`** so `core:protocol` stays a pure Kotlin/JVM library with zero Android dependencies. Change nothing else.
- **Design tokens (Forge UI, from spec §2):** matte black `#121212`, graphite `#2B2B2B`, Suzuki yellow `#FFD100`, white `#F5F5F5`, success green `#2ECC71`, warning amber `#F5A623`, error red `#E53935`. Card radius `10.dp` (8–12 range). Dark-first.
- **Commit style:** one commit per task, conventional-commit subject.

Working directory for all commands unless stated otherwise: `/home/gdk/CodeHollow/Flutter/bumblebee-dash`.

---

### Task 1: Gradle multi-module scaffold that builds an empty app

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/MainActivity.kt`
- Create: `core/protocol/build.gradle.kts`
- Create: `local.properties`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: two Gradle modules `:app` and `:core:protocol`; a version catalog with aliases `libs.plugins.android.application`, `libs.plugins.kotlin.android`, `libs.plugins.kotlin.jvm`, `libs.plugins.kotlin.compose`, `libs.androidx.core.ktx`, `libs.androidx.activity.compose`, `libs.androidx.compose.bom`, `libs.androidx.compose.ui`, `libs.androidx.compose.material3`, `libs.androidx.navigation.compose`, `libs.junit`. A runnable debug APK.

- [ ] **Step 1: Copy the Gradle wrapper from REDLINE (known-good in this environment)**

```bash
cd /home/gdk/CodeHollow/Flutter/bumblebee-dash
cp /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/gradlew .
cp /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/gradlew.bat .
mkdir -p gradle/wrapper
cp /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/
cp /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
chmod +x gradlew
```

- [ ] **Step 2: Write `local.properties` (git-ignored) pointing at the SDK**

```properties
sdk.dir=/home/gdk/Android/Sdk
```

- [ ] **Step 3: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
org.gradle.caching=true
org.gradle.configuration-cache=false
```

- [ ] **Step 4: Write `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.10.0"
kotlin = "2.1.0"
coreKtx = "1.13.1"
activityCompose = "1.9.3"
composeBom = "2026.05.00"
navigationCompose = "2.8.5"
lifecycle = "2.8.7"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 5: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "bumblebee-dash"
include(":app")
include(":core:protocol")
```

- [ ] **Step 6: Write root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 7: Write `core/protocol/build.gradle.kts` (pure Kotlin/JVM library)**

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
    testImplementation(libs.junit)
}
```

- [ ] **Step 8: Write `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.gdk.bumblebee"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.gdk.bumblebee"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

- [ ] **Step 9: Write `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="bumblebee-dash"
        android:theme="@android:style/Theme.Material.NoActionBar"
        android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 10: Write a minimal `MainActivity.kt`**

```kotlin
package dev.gdk.bumblebee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("bumblebee-dash")
                }
            }
        }
    }
}
```

- [ ] **Step 11: Build the debug APK**

Run:
```bash
source /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/env.sh
cd /home/gdk/CodeHollow/Flutter/bumblebee-dash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. First run downloads Gradle + dependencies (several minutes).

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -m "chore: scaffold gradle multi-module app + core:protocol"
```

---

### Task 2: Port the BLE frame protocol into `core:protocol` (TDD via ported tests)

**Files:**
- Create: `core/protocol/src/main/kotlin/dev/gdk/bumblebee/protocol/*.kt` (9 files: `Frame.kt`, `FrameCodec.kt`, `NavFrame.kt`, `CallFrame.kt`, `HeartbeatFrame.kt`, `MissedCallFrame.kt`, `SmsFrame.kt`, `IdentityFrame.kt`, `TelemetryFrame.kt`)
- Create: `core/protocol/src/test/kotlin/dev/gdk/bumblebee/protocol/*.kt` (6 files: `FrameTest.kt`, `NavFrameTest.kt`, `HeartbeatFrameTest.kt`, `IdentityFrameTest.kt`, `TelemetryFrameTest.kt`, `RoundTripTest.kt`)

**Interfaces:**
- Consumes: `:core:protocol` module from Task 1.
- Produces (public API other plans depend on):
  - `enum class FrameType { NAV, CALL, HEARTBEAT, MISSED_CALL, SMS, IDENTITY, TELEMETRY }` with `code: Byte` and `fromByte(Byte): FrameType?`.
  - `fun decodeFrame(bytes: ByteArray): Frame` — dispatch codec.
  - `sealed interface Frame { val raw: ByteArray? }`.
  - `data class TelemetryFrame(speedKmh: Int, odometerKm: Int, tripAKm: Double, tripBKm: Double, fuelBars: Int?, fuelEconKml: Double?, byte23: Int, raw: ByteArray?)` with `val fuelEconKmlV2: Double?` and `fun encode(): ByteArray`.
  - `data class IdentityFrame(name: String, isFresh: Boolean, raw: ByteArray?)` with `fun encode(): ByteArray` and `companion object { fun decode(ByteArray): IdentityFrame }`.
  - `data class NavFrame(...)`, `HeartbeatFrame(...)`, `CallFrame(...)`, `MissedCallFrame(...)`, `SmsFrame(...)` — each with `encode()` / `decode()` as in REDLINE.
  - Constants `FRAME_LEN=30`, `HEADER=0xA5`, `TERMINATOR=0x7F`; helpers `checksum`, `isWellFormed`, `finalize`.

- [ ] **Step 1: Port the test files first (they define the behavior)**

```bash
cd /home/gdk/CodeHollow/Flutter/bumblebee-dash
DEST=core/protocol/src/test/kotlin/dev/gdk/bumblebee/protocol
mkdir -p "$DEST"
SRC=/home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/app/src/test/kotlin/dev/mrwick/redline/protocol
for f in FrameTest NavFrameTest HeartbeatFrameTest IdentityFrameTest TelemetryFrameTest RoundTripTest; do
  sed -e 's/dev\.mrwick\.redline\.protocol/dev.gdk.bumblebee.protocol/g' \
      -e '/import androidx\.compose\.runtime\.Immutable/d' \
      -e '/@Immutable/d' \
      "$SRC/$f.kt" > "$DEST/$f.kt"
done
ls "$DEST"
```

- [ ] **Step 2: Run the tests to verify they fail (no source yet)**

Run:
```bash
source /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/env.sh
./gradlew :core:protocol:test
```
Expected: FAIL — compilation errors, `unresolved reference: FrameType` / `decodeFrame` / `TelemetryFrame` etc.

- [ ] **Step 3: Port the main source files (strip `@Immutable`, rewrite package)**

```bash
cd /home/gdk/CodeHollow/Flutter/bumblebee-dash
DEST=core/protocol/src/main/kotlin/dev/gdk/bumblebee/protocol
mkdir -p "$DEST"
SRC=/home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/app/src/main/kotlin/dev/mrwick/redline/protocol
for f in Frame FrameCodec NavFrame CallFrame HeartbeatFrame MissedCallFrame SmsFrame IdentityFrame TelemetryFrame; do
  sed -e 's/dev\.mrwick\.redline\.protocol/dev.gdk.bumblebee.protocol/g' \
      -e '/import androidx\.compose\.runtime\.Immutable/d' \
      -e '/@Immutable/d' \
      "$SRC/$f.kt" > "$DEST/$f.kt"
done
ls "$DEST"
```

- [ ] **Step 4: Verify no stray Compose/Android imports remain**

Run:
```bash
grep -rn 'androidx' core/protocol/src || echo "CLEAN: no androidx refs"
```
Expected: `CLEAN: no androidx refs`. If any line prints, open that file and remove the offending import (the type is only an `@Immutable` stability hint and is not otherwise used).

- [ ] **Step 5: Run the tests to verify they pass**

Run:
```bash
./gradlew :core:protocol:test
```
Expected: `BUILD SUCCESSFUL`, all ported tests green (REDLINE ships ~6 protocol test classes covering encode/decode round-trips for every frame type). If a test references a symbol you did not port, port that file too from `$REDLINE_PROTO_MAIN`.

- [ ] **Step 6: Commit**

```bash
git add core/protocol/src
git commit -m "feat(protocol): port REDLINE 30-byte frame codec into core:protocol"
```

---

### Task 3: Forge UI theme (design tokens)

**Files:**
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/theme/Color.kt`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/theme/Shape.kt`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/theme/Theme.kt`
- Modify: `app/src/main/kotlin/dev/gdk/bumblebee/MainActivity.kt` (wrap content in `BumblebeeTheme`)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `@Composable fun BumblebeeTheme(content: @Composable () -> Unit)`; color vals `ForgeBlack`, `ForgeGraphite`, `SuzukiYellow`, `ForgeWhite`, `SuccessGreen`, `WarningAmber`, `ErrorRed`.

- [ ] **Step 1: Write `ui/theme/Color.kt`**

```kotlin
package dev.gdk.bumblebee.ui.theme

import androidx.compose.ui.graphics.Color

val ForgeBlack = Color(0xFF121212)
val ForgeGraphite = Color(0xFF2B2B2B)
val SuzukiYellow = Color(0xFFFFD100)
val ForgeWhite = Color(0xFFF5F5F5)
val SuccessGreen = Color(0xFF2ECC71)
val WarningAmber = Color(0xFFF5A623)
val ErrorRed = Color(0xFFE53935)
```

- [ ] **Step 2: Write `ui/theme/Shape.kt`**

```kotlin
package dev.gdk.bumblebee.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ForgeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
)
```

- [ ] **Step 3: Write `ui/theme/Theme.kt`**

```kotlin
package dev.gdk.bumblebee.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ForgeColorScheme = darkColorScheme(
    primary = SuzukiYellow,
    onPrimary = ForgeBlack,
    background = ForgeBlack,
    onBackground = ForgeWhite,
    surface = ForgeGraphite,
    onSurface = ForgeWhite,
    error = ErrorRed,
    onError = ForgeWhite,
)

@Composable
fun BumblebeeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ForgeColorScheme,
        shapes = ForgeShapes,
        content = content,
    )
}
```

- [ ] **Step 4: Wrap `MainActivity` content in the theme**

Replace the `setContent { ... }` block in `MainActivity.kt` with:

```kotlin
        setContent {
            BumblebeeTheme {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    androidx.compose.material3.Text("bumblebee-dash")
                }
            }
        }
```

Add the import `import dev.gdk.bumblebee.ui.theme.BumblebeeTheme` and remove the now-unused `MaterialTheme` import if the linter flags it.

- [ ] **Step 5: Build to verify the theme compiles**

Run:
```bash
source /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/env.sh
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "feat(ui): add Forge UI dark theme (colors, shapes)"
```

---

### Task 4: Five-pillar bottom-navigation shell

**Files:**
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/nav/Pillar.kt`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/nav/BumblebeeApp.kt`
- Create: `app/src/main/kotlin/dev/gdk/bumblebee/ui/nav/PlaceholderScreen.kt`
- Modify: `app/src/main/kotlin/dev/gdk/bumblebee/MainActivity.kt` (render `BumblebeeApp()`)

**Interfaces:**
- Consumes: `BumblebeeTheme` (Task 3).
- Produces: `enum class Pillar(route, label, icon)`; `@Composable fun BumblebeeApp()` — a `Scaffold` with a `NavigationBar` (5 items) and `NavHost` switching between placeholder screens; `@Composable fun PlaceholderScreen(title: String)`.

- [ ] **Step 1: Write `ui/nav/Pillar.kt`**

```kotlin
package dev.gdk.bumblebee.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector

enum class Pillar(val route: String, val label: String, val icon: ImageVector) {
    COCKPIT("cockpit", "Cockpit", Icons.Filled.Speed),
    JOURNEY("journey", "Journey", Icons.Filled.Map),
    RIDE("ride", "Ride", Icons.Filled.DirectionsBike),
    EXPLORER("explorer", "Explorer", Icons.Filled.Explore),
    GARAGE("garage", "Garage", Icons.Filled.Garage),
}
```

- [ ] **Step 2: Write `ui/nav/PlaceholderScreen.kt`**

```kotlin
package dev.gdk.bumblebee.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
    }
}
```

- [ ] **Step 3: Write `ui/nav/BumblebeeApp.kt`**

```kotlin
package dev.gdk.bumblebee.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun BumblebeeApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            NavigationBar {
                Pillar.entries.forEach { pillar ->
                    NavigationBarItem(
                        selected = currentRoute == pillar.route,
                        onClick = {
                            navController.navigate(pillar.route) {
                                popUpTo(Pillar.COCKPIT.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(pillar.icon, contentDescription = pillar.label) },
                        label = { Text(pillar.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Pillar.COCKPIT.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            Pillar.entries.forEach { pillar ->
                composable(pillar.route) { PlaceholderScreen(pillar.label) }
            }
        }
    }
}
```

- [ ] **Step 4: Render `BumblebeeApp` from `MainActivity`**

Replace the `setContent { ... }` block with:

```kotlin
        setContent {
            BumblebeeTheme {
                BumblebeeApp()
            }
        }
```

Add imports `import dev.gdk.bumblebee.ui.nav.BumblebeeApp` and keep `import dev.gdk.bumblebee.ui.theme.BumblebeeTheme`. Remove now-unused `Surface`/`Text`/`MaterialTheme` imports if flagged.

- [ ] **Step 5: Build, install, and launch on the connected phone**

Run:
```bash
source /home/gdk/CodeHollow/Flutter/suzuki-connect-re/android/env.sh
cd /home/gdk/CodeHollow/Flutter/bumblebee-dash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.gdk.bumblebee.debug/dev.gdk.bumblebee.MainActivity
```
Expected: `BUILD SUCCESSFUL`, app launches on the phone showing a dark screen with a 5-tab bottom bar (Cockpit / Journey / Ride / Explorer / Garage); tapping each tab switches the centered placeholder label.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "feat(ui): add five-pillar bottom-navigation shell"
```

---

## Verification (whole plan)

After Task 4:
- `./gradlew :core:protocol:test` — all ported protocol tests pass.
- `./gradlew :app:assembleDebug` — app builds.
- App installs and runs on the Redmi Note 7; five pillars navigate; theme is dark with yellow accent on the selected tab.

This foundation is what the next plans build on:
- **Plan 2 — BLE connection + pairing + demo mode** consumes `decodeFrame`, `IdentityFrame.encode()`, `TelemetryFrame`, and the heartbeat frame from `core:protocol`.
- **Plan 3 — Cockpit** fills the Cockpit placeholder using `TelemetryFrame` fields (`speedKmh`, `odometerKm`, `fuelBars`, `fuelEconKmlV2`).
