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
include(":core:ride")
include(":core:snapshot")
include(":core:ble")
include(":data:local")
include(":core:ble-android")
include(":core:nav")
include(":core:video")
