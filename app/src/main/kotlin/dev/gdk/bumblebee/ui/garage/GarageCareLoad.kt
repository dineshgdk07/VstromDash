package dev.gdk.bumblebee.ui.garage

import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.data.local.toRecord
import dev.gdk.bumblebee.garage.GarageReminders
import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ride.GarageCare
import dev.gdk.bumblebee.ride.RideRhythm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class GarageCareSnapshot(
    val tankKmPerL: Double?,
    val rupeesPerKm: Double?,
    val rangeKm: Double?,
    val docs: List<DocCard>,
    val plans: List<PlanCard>,
    val logs: List<ServiceCard>,
    val upcoming: List<UpcomingItem>,
    val runningRupeesPerKm: Double?,
    val runningPartial: Boolean,
)

/** Same care queries the old garage screen ran in one pass. */
internal suspend fun reloadGarage(app: DashApp, fuelBars: Int?, odometerKm: Int?): GarageCareSnapshot {
    val snap = withContext(Dispatchers.IO) {
        app.capture.ensureGarageDefaults(
            app.prefs.rcExpiry,
            app.prefs.insuranceExpiry,
            app.prefs.pucExpiry,
            app.prefs.serviceIntervalKm,
            app.prefs.lastServiceOdoKm,
        )
        val eco = app.capture.bikeEconomics(fuelBars)
        val rides = app.capture.finishedRides().map { it.toRecord() }
        val kmPerDay = RideRhythm.paceKmPerDay(rides)
        val today = LocalDate.now()
        val remindDays = app.prefs.remindDays
        val remindKm = app.prefs.remindKm
        val docs = app.capture.allDocuments().map { doc ->
            val insight = GarageCare.documentInsight(doc.expiryIso, today, doc.title, remindDays)
            DocCard(
                doc.id, doc.title, doc.kind, doc.number, doc.expiryIso, doc.filePath, doc.mime,
                insight.band, insight.label, doc.note,
            )
        }
        val plans = mapPlans(app, odometerKm, today, kmPerDay, remindKm, remindDays)
        val cards = mapLogs(app)
        val upcoming = (docs.map { UpcomingItem("d${it.id}", it.label, it.band) } +
            plans.map { UpcomingItem("p${it.id}", it.label, it.band) })
            .filter { it.band == CareBand.OVERDUE || it.band == CareBand.DUE_SOON }
            .sortedBy { if (it.band == CareBand.OVERDUE) 0 else 1 }
        val running = app.capture.runningCost()
        GarageCareSnapshot(
            tankKmPerL = eco.tankKmPerL,
            rupeesPerKm = eco.cost?.rollingRupeesPerKm,
            rangeKm = eco.rangeKm,
            docs = docs,
            plans = plans,
            logs = cards,
            upcoming = upcoming,
            runningRupeesPerKm = running?.rupeesPerKm,
            runningPartial = running?.let { it.fuelFillsPriced < it.fuelFillsTotal || it.servicesPriced < it.servicesTotal } == true,
        )
    }
    GarageReminders.refresh(app)
    return snap
}

internal data class ServiceCareSnapshot(
    val plans: List<PlanCard>,
    val logs: List<ServiceCard>,
    val runningRupeesPerKm: Double?,
    val runningPartial: Boolean,
    val remindKm: Int,
    val remindDays: Int,
)

/** Plans, history, and running cost. Documents and fuel economics stay on the hub load. */
internal suspend fun reloadService(app: DashApp, odometerKm: Int?): ServiceCareSnapshot {
    val snap = withContext(Dispatchers.IO) {
        app.capture.ensureGarageDefaults(
            app.prefs.rcExpiry,
            app.prefs.insuranceExpiry,
            app.prefs.pucExpiry,
            app.prefs.serviceIntervalKm,
            app.prefs.lastServiceOdoKm,
        )
        val rides = app.capture.finishedRides().map { it.toRecord() }
        val kmPerDay = RideRhythm.paceKmPerDay(rides)
        val today = LocalDate.now()
        val remindDays = app.prefs.remindDays
        val remindKm = app.prefs.remindKm
        val running = app.capture.runningCost()
        ServiceCareSnapshot(
            plans = mapPlans(app, odometerKm, today, kmPerDay, remindKm, remindDays),
            logs = mapLogs(app),
            runningRupeesPerKm = running?.rupeesPerKm,
            runningPartial = running?.let { it.fuelFillsPriced < it.fuelFillsTotal || it.servicesPriced < it.servicesTotal } == true,
            remindKm = remindKm,
            remindDays = remindDays,
        )
    }
    GarageReminders.refresh(app)
    return snap
}

private suspend fun mapPlans(
    app: DashApp,
    odometerKm: Int?,
    today: LocalDate,
    kmPerDay: Double,
    remindKm: Int,
    remindDays: Int,
): List<PlanCard> = app.capture.allPlans().filter { it.active }.map { plan ->
    val insight = GarageCare.planInsight(
        title = plan.title,
        currentOdoKm = odometerKm,
        lastDoneOdoKm = plan.lastDoneOdoKm,
        intervalKm = plan.intervalKm,
        lastDoneOn = plan.lastDoneOnIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        intervalDays = plan.intervalDays,
        dueOn = plan.dueOnIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        today = today,
        kmPerDay = kmPerDay,
        remindKm = remindKm,
        remindDays = remindDays,
    )
    PlanCard(
        plan.id, plan.title, plan.kind, plan.intervalKm, plan.intervalDays, plan.dueOnIso,
        plan.lastDoneOdoKm, plan.lastDoneOnIso, insight.band, insight.label,
    )
}

private suspend fun mapLogs(app: DashApp): List<ServiceCard> {
    val logs = app.capture.allServices()
    return logs.mapIndexed { index, log ->
        val newer = logs.getOrNull(index + 1)
        val since = newer?.let { log.odometerKm - it.odometerKm }?.takeIf { it > 0 }
        ServiceCard(
            id = log.id,
            whenLabel = DateTimeFormatter.ofPattern("d MMM yyyy").format(
                Instant.ofEpochMilli(log.ts).atZone(ZoneId.systemDefault()),
            ),
            story = GarageCare.historyStory(log.item, log.odometerKm, log.rupees, log.shop, since),
            filePath = log.filePath,
            odometerKm = log.odometerKm,
            item = log.item,
            rupees = log.rupees,
            note = log.note,
            shop = log.shop,
            planId = log.planId,
            ts = log.ts,
        )
    }
}
