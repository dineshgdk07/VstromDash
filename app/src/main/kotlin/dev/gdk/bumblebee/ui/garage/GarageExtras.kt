package dev.gdk.bumblebee.ui.garage

import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.data.local.RideEntity
import dev.gdk.bumblebee.data.local.toRecord
import dev.gdk.bumblebee.ride.BikeHealth
import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ride.CareDate
import dev.gdk.bumblebee.ride.ChainLube
import dev.gdk.bumblebee.ride.CostPlan
import dev.gdk.bumblebee.ride.EfficiencyAlert
import dev.gdk.bumblebee.ride.FuelEconomics
import dev.gdk.bumblebee.ride.GarageCare
import dev.gdk.bumblebee.ride.GarageInsights
import dev.gdk.bumblebee.ride.GarageRide
import dev.gdk.bumblebee.ride.GarageYear
import dev.gdk.bumblebee.ride.OdoStory
import dev.gdk.bumblebee.ride.Ownership
import dev.gdk.bumblebee.ride.PartWear
import dev.gdk.bumblebee.ride.PassportEvent
import dev.gdk.bumblebee.ride.PlanState
import dev.gdk.bumblebee.ride.RideRhythm
import dev.gdk.bumblebee.ui.explorer.toFact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Everything the Garage analytics read, from one pass over the stored history. */
data class GarageExtras(
    val health: BikeHealth,
    val ownership: Ownership?,
    val careDates: List<CareDate>,
    val parts: List<PartWear>,
    val chain: ChainLube?,
    val chainPlanId: Long?,
    val efficiency: EfficiencyAlert?,
    val years: List<GarageYear>,
    val plan: CostPlan,
    val odo: OdoStory,
    val passport: List<PassportEvent>,
    val checkStreak: Int,
    val checkedToday: Boolean,
    val odoKm: Int?,
)

internal suspend fun loadGarageExtras(app: DashApp, liveOdoKm: Int?): GarageExtras = withContext(Dispatchers.IO) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val entities = app.capture.finishedRides().filter { !it.isDemo && it.mergedIntoId == null }
    val rides = entities.map(RideEntity::toFact)
    val odo = liveOdoKm ?: app.capture.latestKnownOdometerKm()
    val fills = app.capture.fuelFills()
    val services = app.capture.allServices()
    val docs = app.capture.allDocuments()
    val kmPerDay = RideRhythm.paceKmPerDay(entities.map { it.toRecord() })
    val remindKm = app.prefs.remindKm
    val remindDays = app.prefs.remindDays

    val plansRaw = app.capture.allPlans().filter { it.active }
    val plans = plansRaw.map { p ->
        val lastOn = p.lastDoneOnIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val dueOn = p.dueOnIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val insight = GarageCare.planInsight(
            p.title, odo, p.lastDoneOdoKm, p.intervalKm, lastOn, p.intervalDays, dueOn, today, kmPerDay, remindKm, remindDays,
        )
        // Calendar days only; the forecast adds pace itself.
        val every = p.intervalDays
        val calendar = listOfNotNull(
            if (every != null && every > 0 && lastOn != null) {
                ChronoUnit.DAYS.between(today, lastOn.plusDays(every.toLong())).toInt()
            } else {
                null
            },
            dueOn?.let { ChronoUnit.DAYS.between(today, it).toInt() },
        ).minOrNull()
        PlanState(p.title, p.kind, insight.band, insight.kmRemaining, calendar, p.lastDoneOdoKm, lastOn, p.intervalKm)
    }
    val docBands = docs.map { it.title to GarageCare.documentInsight(it.expiryIso, today, it.title, remindDays).band }

    val garageRides = entities.zip(rides) { e, f -> GarageRide(e.id, e.startTs, f.km, f.label, e.odoEndKm, e.weatherCode) }
    val chainPlan = plansRaw.firstOrNull { it.kind == "CHAIN" }
    val chain = chainPlan?.let { p ->
        val lastTs = p.lastDoneOnIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        GarageInsights.chainLube(p.lastDoneOdoKm, lastTs, odo, garageRides, p.intervalKm ?: 500, zone)
    }

    val firstOdo = entities.mapNotNull { r -> r.odoStartKm?.let { r.startTs to it } }.minByOrNull { it.first }?.second
        ?: fills.minByOrNull { it.ts }?.odometerKm
    val firstTs = listOfNotNull(entities.minOfOrNull { it.startTs }, fills.minOfOrNull { it.ts }, services.minOfOrNull { it.ts }).minOrNull()
    val parts = GarageInsights.partsLife(services, odo, firstOdo, firstTs, today, zone)
    val oilOverdue = plans.any { it.kind == "OIL" && it.band == CareBand.OVERDUE }
    val efficiency = GarageInsights.efficiency(fills, rides, oilOverdue, chain?.due == true)
    val fallbackKm = entities.mapNotNull { r ->
        val s = r.odoStartKm
        val e = r.odoEndKm
        if (s != null && e != null) e - s else null
    }.sum().takeIf { it > 0 }

    val price = fills.sortedByDescending { it.ts }
        .firstOrNull { (it.litres ?: 0.0) > 0 && (it.rupees ?: 0.0) > 0 }
        ?.let { it.rupees!! / it.litres!! }
    val yearsSeen = (fills.map { it.ts } + services.map { it.ts } + entities.map { it.startTs })
        .map { Instant.ofEpochMilli(it).atZone(zone).year }
        .toSortedSet()
        .reversed()
    val checks = GaragePrefs(app).checkDays
    val rideDays = entities.map { Instant.ofEpochMilli(it.startTs).atZone(zone).toLocalDate() }.toSet()

    GarageExtras(
        health = GarageInsights.health(plans, docBands, parts, efficiency, chain),
        ownership = GarageInsights.ownership(fills, services, fallbackKm, today, zone),
        careDates = GarageInsights.careDates(plans, kmPerDay, today),
        parts = parts,
        chain = chain,
        chainPlanId = chainPlan?.id,
        efficiency = efficiency,
        years = yearsSeen.map { GarageInsights.year(it, fills, services, rides, zone) },
        plan = GarageInsights.costPlan(plans, services, kmPerDay, FuelEconomics.averageKmPerL(fills), price, today),
        odo = GarageInsights.odoStory(rides, firstOdo, odo, today, zone),
        passport = GarageInsights.passport(services, fills, docs.map { it.title to it.createdTs }, garageRides, zone),
        checkStreak = GarageInsights.checkStreak(checks, rideDays, today),
        checkedToday = today in checks,
        odoKm = odo,
    )
}
