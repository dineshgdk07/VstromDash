package dev.gdk.bumblebee.ui.cockpit

enum class WarningSeverity { CRITICAL, WARN, INFO }

enum class WarningFix { Settings, Fuel, Service }

data class CockpitWarning(
    val severity: WarningSeverity,
    val title: String,
    val detail: String,
    val fix: WarningFix,
)
