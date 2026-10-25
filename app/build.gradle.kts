import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Google's map display reads its key at build time only. Put MAPS_API_KEY=... in
 * local.properties (git-ignored); without it the app builds and simply hides the Google map.
 */
val mapsApiKey: String = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}.getProperty("MAPS_API_KEY").orEmpty()

android {
    namespace = "dev.gdk.bumblebee"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.gdk.bumblebee"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("boolean", "HAS_MAPS_KEY", mapsApiKey.isNotBlank().toString())
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    signingConfigs {
        getByName("debug")
        create("release") {
            val store = System.getenv("BUMBLEBEE_STORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = System.getenv("BUMBLEBEE_STORE_PASSWORD")
                keyAlias = System.getenv("BUMBLEBEE_KEY_ALIAS")
                keyPassword = System.getenv("BUMBLEBEE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (System.getenv("BUMBLEBEE_STORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("share.preview", System.getProperty("share.preview") ?: "false")
        }
    }
}

dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:ride"))
    implementation(project(":core:ble"))
    implementation(project(":core:ble-android"))
    implementation(project(":core:nav"))
    implementation(project(":data:local"))
    implementation(project(":core:snapshot"))
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.room.ktx)
}
