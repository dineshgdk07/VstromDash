package dev.gdk.bumblebee.ui.garage

import dev.gdk.bumblebee.ride.CareBand

data class DocCard(
    val id: Long,
    val title: String,
    val kind: String,
    val number: String?,
    val expiryIso: String?,
    val filePath: String?,
    val mime: String?,
    val band: CareBand,
    val label: String,
    val note: String? = null,
)

data class PlanCard(
    val id: Long,
    val title: String,
    val kind: String,
    val intervalKm: Int?,
    val intervalDays: Int?,
    val dueOnIso: String?,
    val lastDoneOdoKm: Int?,
    val lastDoneOnIso: String?,
    val band: CareBand,
    val label: String,
)

data class ServiceCard(
    val id: Long,
    val whenLabel: String,
    val story: String,
    val filePath: String?,
    val mimeGuess: String? = null,
    val odometerKm: Int,
    val item: String,
    val rupees: Double?,
    val note: String?,
    val shop: String?,
    val planId: Long?,
    val ts: Long,
)

data class UpcomingItem(
    val key: String,
    val label: String,
    val band: CareBand,
)

internal fun prettyKind(kind: String): String = when (kind) {
    "RC" -> "RC"
    "INSURANCE" -> "Insurance"
    "PUC" -> "PUC"
    "WARRANTY" -> "Warranty"
    "INVOICE" -> "Invoice"
    "PERMIT" -> "Permit"
    "OIL" -> "Engine oil"
    "CHAIN" -> "Drive chain"
    "BRAKES" -> "Brake pads"
    "TYRES" -> "Tyres"
    "BATTERY" -> "Battery"
    "SERVICE" -> "Periodic service"
    else -> kind.lowercase().replaceFirstChar { it.uppercase() }
}
