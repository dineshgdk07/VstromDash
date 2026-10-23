package dev.gdk.bumblebee.snapshot

enum class RowAction { ADD, REPLACE, KEEP }

data class Clash(val section: String, val label: String, val log: String)

data class ParentDecision(val sourceId: Long, val key: String, val action: RowAction)

data class ApplyPlan(
    val clashes: List<Clash>,
    val rides: List<ParentDecision>,
    val fuels: List<ParentDecision>,
    val documents: List<ParentDecision>,
    val services: List<ParentDecision>,
    val plans: List<ParentDecision>,
    val rollups: Map<String, RowAction>,
    val calibration: Map<String, RowAction>,
    val prefs: RowAction,
)

data class PackIssue(val message: String, val log: String)

fun rideKey(remoteId: String?, startTs: Long): String =
    if (!remoteId.isNullOrBlank()) "remote:$remoteId" else "start:$startTs"

fun fuelKey(remoteId: String?, ts: Long, odometerKm: Int, type: String): String =
    if (!remoteId.isNullOrBlank()) "remote:$remoteId" else "$ts|$odometerKm|$type"

fun documentKey(kind: String, title: String, number: String?): String =
    "$kind|$title|${number?.trim().orEmpty()}"

fun serviceKey(ts: Long, item: String, odometerKm: Int): String = "$ts|$item|$odometerKm"

fun planKey(kind: String, title: String): String = "$kind|$title"

fun SnapshotBundle.isEmptyPhone(): Boolean =
    rides.isEmpty() && fuel.isEmpty() && documents.isEmpty() && services.isEmpty() &&
        plans.isEmpty() && rollups.isEmpty() && calibration.isEmpty() && prefs.bikeMac == null

fun planApply(incoming: SnapshotBundle, here: SnapshotBundle, overwriteClashes: Boolean): ApplyPlan {
    val onClash = if (overwriteClashes) RowAction.REPLACE else RowAction.KEEP
    val clashes = mutableListOf<Clash>()

    val hereRides = here.rides.associateBy { rideKey(it.remoteId, it.startTs) }
    val rides = incoming.rides.map { row ->
        val key = rideKey(row.remoteId, row.startTs)
        val action = if (hereRides.containsKey(key)) onClash else RowAction.ADD
        if (action != RowAction.ADD) clashes += Clash(SnapshotSections.RIDES, key, "clash ride $key")
        ParentDecision(row.id, key, action)
    }

    val hereFuel = here.fuel.associateBy { fuelKey(it.remoteId, it.ts, it.odometerKm, it.type) }
    val fuels = incoming.fuel.map { row ->
        val key = fuelKey(row.remoteId, row.ts, row.odometerKm, row.type)
        val action = if (hereFuel.containsKey(key)) onClash else RowAction.ADD
        if (action != RowAction.ADD) clashes += Clash(SnapshotSections.FUEL, key, "clash fuel $key")
        ParentDecision(row.id, key, action)
    }

    val hereDocs = here.documents.associateBy { documentKey(it.kind, it.title, it.number) }
    val documents = incoming.documents.map { row ->
        val key = documentKey(row.kind, row.title, row.number)
        val action = if (hereDocs.containsKey(key)) onClash else RowAction.ADD
        if (action != RowAction.ADD) clashes += Clash(SnapshotSections.DOCUMENTS, key, "clash document $key")
        ParentDecision(row.id, key, action)
    }

    val hereServices = here.services.associateBy { serviceKey(it.ts, it.item, it.odometerKm) }
    val services = incoming.services.map { row ->
        val key = serviceKey(row.ts, row.item, row.odometerKm)
        val action = if (hereServices.containsKey(key)) onClash else RowAction.ADD
        if (action != RowAction.ADD) clashes += Clash(SnapshotSections.SERVICE, key, "clash service $key")
        ParentDecision(row.id, key, action)
    }

    val herePlans = here.plans.associateBy { planKey(it.kind, it.title) }
    val plans = incoming.plans.map { row ->
        val key = planKey(row.kind, row.title)
        val action = if (herePlans.containsKey(key)) onClash else RowAction.ADD
        if (action != RowAction.ADD) clashes += Clash(SnapshotSections.PLANS, key, "clash plan $key")
        ParentDecision(row.id, key, action)
    }

    val hereDates = here.rollups.map { it.date }.toSet()
    val rollups = incoming.rollups.associate { row ->
        val action = if (row.date in hereDates) onClash else RowAction.ADD
        if (action != RowAction.ADD) clashes += Clash(SnapshotSections.ROLLUPS, row.date, "clash rollup ${row.date}")
        row.date to action
    }

    val hereKeys = here.calibration.map { it.key }.toSet()
    val calibration = incoming.calibration.associate { row ->
        val action = if (row.key in hereKeys) onClash else RowAction.ADD
        if (action != RowAction.ADD) clashes += Clash(SnapshotSections.CALIBRATION, row.key, "clash calibration ${row.key}")
        row.key to action
    }

    val prefsClash = !here.isEmptyPhone() && !here.prefs.isDefaultBike() && !here.prefs.sameBikeFields(incoming.prefs)
    val prefs = if (prefsClash) onClash else RowAction.ADD
    if (prefsClash) clashes += Clash(SnapshotSections.PREFS, "prefs", "clash prefs")

    return ApplyPlan(clashes, rides, fuels, documents, services, plans, rollups, calibration, prefs)
}

fun packIssues(bundle: SnapshotBundle, missingFiles: List<String>): List<PackIssue> {
    val open = bundle.rides.filter { it.rideState == "ACTIVE" || it.rideState == "PAUSED" }
        .map { PackIssue("Finish or discard the open ride before packing.", it.rideState) }
    val missing = missingFiles.map { PackIssue("A paper is missing its scan.", it) }
    return open + missing
}

private fun SnapshotPrefs.isDefaultBike(): Boolean =
    bikeMac == null &&
        riderName == "RIDER" &&
        serviceIntervalKm == 0 &&
        lastServiceOdoKm == null &&
        rcExpiry == "" &&
        insuranceExpiry == "" &&
        pucExpiry == "" &&
        mapsNavToCluster &&
        tankCapacityL == 12f &&
        remindersEnabled &&
        remindDays == 14 &&
        remindKm == 200 &&
        pauseWindowMin == 45 &&
        autoConnect &&
        skipAutoConnectDate == "" &&
        crashSensitivity == "NORMAL" &&
        emergencyContacts == "" &&
        bloodGroup == "" &&
        medicalNote == "" &&
        lastReminderStamp == ""

private fun SnapshotPrefs.sameBikeFields(other: SnapshotPrefs): Boolean = copy(widget = other.widget) == other
