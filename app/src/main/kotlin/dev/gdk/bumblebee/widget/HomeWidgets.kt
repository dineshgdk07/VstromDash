package dev.gdk.bumblebee.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.MainActivity
import dev.gdk.bumblebee.R
import dev.gdk.bumblebee.bike.BikeProfile
import dev.gdk.bumblebee.ble.BikeBridgeService
import dev.gdk.bumblebee.data.local.RideEntity
import dev.gdk.bumblebee.garage.GarageSnapshot
import dev.gdk.bumblebee.journey.JourneyPrefs
import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ride.GarageInsights
import dev.gdk.bumblebee.ride.JourneyInsights
import dev.gdk.bumblebee.ride.RideHistory
import dev.gdk.bumblebee.ride.TrackPlot
import dev.gdk.bumblebee.ui.explorer.ExplorerPrefs
import dev.gdk.bumblebee.ui.explorer.toFact
import dev.gdk.bumblebee.ui.garage.loadGarageExtras
import dev.gdk.bumblebee.ui.garage.reloadGarage
import dev.gdk.bumblebee.ui.sharecard.ShareText
import dev.gdk.bumblebee.widget.art.BikeWidget
import dev.gdk.bumblebee.widget.art.CareLine
import dev.gdk.bumblebee.widget.art.CareWidget
import dev.gdk.bumblebee.widget.art.FuelWidget
import dev.gdk.bumblebee.widget.art.LastRideWidget
import dev.gdk.bumblebee.widget.art.PlacesWidget
import dev.gdk.bumblebee.widget.art.TodayWidget
import dev.gdk.bumblebee.widget.art.WidgetArt
import dev.gdk.bumblebee.widget.art.WidgetLook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Which widget; each is its own provider so it shows separately in the launcher's picker. */
enum class HomeWidget { BIKE, FUEL, START, TODAY, CARE, LAST_RIDE, PLACES }

/**
 * Draws and updates every home-screen widget. Each is one image in the app's style, plus tap
 * targets. Data is read fresh from the ride store whenever a widget updates; publishers (ride
 * end, a fill, the bike link) just call [refreshAll].
 */
object HomeWidgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun providerOf(kind: HomeWidget): Class<out AppWidgetProvider> = when (kind) {
        HomeWidget.BIKE -> dev.gdk.bumblebee.garage.GarageWidgetProvider::class.java
        HomeWidget.FUEL -> FuelWidgetProvider::class.java
        HomeWidget.START -> StartWidgetProvider::class.java
        HomeWidget.TODAY -> TodayWidgetProvider::class.java
        HomeWidget.CARE -> CareWidgetProvider::class.java
        HomeWidget.LAST_RIDE -> LastRideWidgetProvider::class.java
        HomeWidget.PLACES -> PlacesWidgetProvider::class.java
    }

    fun refreshAll(context: Context) {
        dev.gdk.bumblebee.dev.DevLog.d("widget", "refresh all")
        HomeWidget.entries.forEach { refresh(context, it) }
    }

    fun refresh(context: Context, kind: HomeWidget) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val ids = mgr.getAppWidgetIds(ComponentName(app, providerOf(kind)))
        if (ids.isEmpty()) return
        scope.launch { update(app, mgr, kind, ids) }
    }

    internal suspend fun update(context: Context, mgr: AppWidgetManager, kind: HomeWidget, ids: IntArray) {
        val app = context.applicationContext as DashApp
        val look = look(app)
        val data = runCatching { load(app, kind) }.getOrNull()
        for (id in ids) {
            val (w, h) = sizePx(app, mgr.getAppWidgetOptions(id), kind)
            val views = runCatching { render(app, kind, data, look, w, h) }.getOrNull() ?: continue
            runCatching { mgr.updateAppWidget(id, views) }
        }
    }

    private fun look(context: Context): WidgetLook {
        val density = context.resources.displayMetrics.density.coerceAtMost(2.6f)
        return WidgetLook(BikeProfile(context).accent.toArgb(), ShareText.fonts(context), density)
    }

    /** The widget's current size in pixels: portrait width and height, at our capped density. */
    private fun sizePx(context: Context, options: Bundle?, kind: HomeWidget): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density.coerceAtMost(2.6f)
        val (dw, dh) = defaultDp(kind)
        val wDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 } ?: dw
        val hDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 } ?: dh
        return (wDp * density).toInt().coerceIn(60, 1100) to (hDp * density).toInt().coerceIn(60, 800)
    }

    private fun defaultDp(kind: HomeWidget) = when (kind) {
        HomeWidget.BIKE, HomeWidget.TODAY, HomeWidget.CARE, HomeWidget.LAST_RIDE -> 320 to 160
        HomeWidget.FUEL, HomeWidget.START -> 80 to 80
        HomeWidget.PLACES -> 320 to 70
    }

    // ---- data ----

    private sealed interface Data
    private data class Bike(val v: BikeWidget) : Data
    private data class Fuel(val v: FuelWidget) : Data
    private data class Start(val liveKm: Double?) : Data
    private data class Today(val v: TodayWidget) : Data
    private data class Care(val v: CareWidget) : Data
    private data class Last(val v: LastRideWidget?, val rideId: Long?) : Data
    private data class Places(val v: PlacesWidget, val queries: List<String>) : Data

    private suspend fun liveKm(app: DashApp): Double? = app.capture.liveSnapshot()?.let { s ->
        val a = s.odoStartKm
        val b = s.odoEndKm
        if (a != null && b != null) (b - a).toDouble() else s.gpsDistanceM / 1000.0
    }

    private suspend fun load(app: DashApp, kind: HomeWidget): Data {
        val snap = GarageSnapshot.read(app)
        val zone = ZoneId.systemDefault()
        return when (kind) {
            HomeWidget.BIKE -> {
                val profile = BikeProfile(app)
                val odo = app.latestTelemetry.value?.odometerKm ?: app.capture.latestKnownOdometerKm()
                val bike = runCatching {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeResource(app.resources, profile.variant.image, opts)
                }.getOrNull()
                val health = runCatching { loadGarageExtras(app, odo).health.score }.getOrNull()
                Bike(
                    BikeWidget(
                        "V-Strom 250 SX", profile.regNo, app.latestTelemetry.value?.fuelBars ?: snap.fuelBars,
                        snap.rangeKm, odo, app.connection.value is dev.gdk.bumblebee.ble.ConnectionState.Ready, health, liveKm(app), bike,
                    ),
                )
            }
            HomeWidget.FUEL -> {
                val bars = app.latestTelemetry.value?.fuelBars ?: snap.fuelBars
                val eco = app.capture.bikeEconomics(bars)
                val lastFill = app.capture.fuelFills().maxByOrNull { it.ts }
                val since = lastFill?.let {
                    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(it.ts).atZone(zone).toLocalDate(), LocalDate.now(zone))
                    when (days) {
                        0L -> "Filled today"
                        1L -> "Filled yesterday"
                        else -> "Filled $days days ago"
                    }
                }
                Fuel(FuelWidget(bars, eco.rangeKm?.toInt() ?: snap.rangeKm, eco.tankKmPerL, since))
            }
            HomeWidget.START -> Start(liveKm(app))
            HomeWidget.TODAY -> {
                val rides = app.capture.finishedRides().filter { !it.isDemo && it.mergedIntoId == null }.map(RideEntity::toFact)
                val today = LocalDate.now(zone)
                val byDay = rides.groupBy { Instant.ofEpochMilli(it.startTs).atZone(zone).toLocalDate() }.mapValues { (_, r) -> r.sumOf { it.km } }
                val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
                val live = liveKm(app) ?: 0.0
                val week = days.map { (byDay[it] ?: 0.0) + if (it == today) live else 0.0 }
                var streak = 0
                var d = if ((byDay[today] ?: 0.0) > 0 || live > 0) today else today.minusDays(1)
                while ((byDay[d] ?: 0.0) > 0 || (d == today && live > 0)) {
                    streak++
                    d = d.minusDays(1)
                }
                val month = RideHistory.monthTotals(rides, YearMonth.now(zone), 1, zone).lastOrNull()?.km ?: 0.0
                Today(
                    TodayWidget(
                        week.last(), week, days.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
                        streak, month + live, ExplorerPrefs(app).monthlyGoalKm,
                    ),
                )
            }
            HomeWidget.CARE -> {
                val odo = app.latestTelemetry.value?.odometerKm ?: app.capture.latestKnownOdometerKm()
                val garage = reloadGarage(app, snap.fuelBars, odo)
                val urgent = garage.upcoming.map { item ->
                    val title = item.label.substringBefore(" · ").substringBefore(" renew").substringBefore(" expire")
                    val caption = item.label.substringAfter(" · ", item.label.removePrefix(title).trim())
                    CareLine(title, caption, if (item.band == CareBand.OVERDUE) 1f else 0.85f, item.band == CareBand.OVERDUE)
                }
                val later = runCatching { loadGarageExtras(app, odo).careDates }.getOrDefault(emptyList())
                    .filter { d -> urgent.none { it.title.equals(d.title, ignoreCase = true) } }
                    .map { d ->
                        val days = ChronoUnit.DAYS.between(LocalDate.now(zone), d.date).coerceAtLeast(0)
                        CareLine(d.title, GarageInsights.forecastLabel(d.date, d.fromPace, LocalDate.now(zone)), (1f - days / 90f).coerceIn(0.05f, 1f), false)
                    }
                Care(CareWidget((urgent + later).take(3)))
            }
            HomeWidget.LAST_RIDE -> {
                val last = app.capture.finishedRides().firstOrNull { !it.isDemo && it.mergedIntoId == null }
                if (last == null) {
                    Last(null, null)
                } else {
                    val fact = last.toFact()
                    val track = TrackPlot.downsample(app.capture.trackForRide(last.id), 160)
                    val day = Instant.ofEpochMilli(last.startTs).atZone(zone).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
                    Last(
                        LastRideWidget(fact.label, day, fact.km, fact.movingSec / 60, fact.maxSpeedKmh, WidgetArt.normalise(track.map { it.lat to it.lng })),
                        last.id,
                    )
                }
            }
            HomeWidget.PLACES -> {
                val places = JourneyPrefs(app).places.take(3)
                Places(PlacesWidget(places.map { it.name }), places.map { it.query })
            }
        }
    }

    // ---- views ----

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun openApp(context: Context, where: String, code: Int): PendingIntent =
        PendingIntent.getActivity(context, code, MainActivity.open(context, where), FLAGS)

    private fun render(context: Context, kind: HomeWidget, data: Data?, look: WidgetLook, w: Int, h: Int): RemoteViews {
        val art: Bitmap = when (data) {
            is Bike -> WidgetArt.bike(w, h, look, data.v)
            is Fuel -> WidgetArt.fuel(w, h, look, data.v)
            is Start -> WidgetArt.start(w, h, look, data.liveKm)
            is Today -> WidgetArt.today(w, h, look, data.v)
            is Care -> WidgetArt.care(w, h, look, data.v)
            is Last -> WidgetArt.lastRide(w, h, look, data.v)
            is Places -> WidgetArt.places(w, h, look, data.v)
            null -> WidgetArt.care(w, h, look, CareWidget(emptyList()))
        }
        if (data is Places) {
            return RemoteViews(context.packageName, R.layout.widget_art_three).apply {
                setImageViewBitmap(R.id.widget_art, art)
                val zones = listOf(R.id.widget_zone_1, R.id.widget_zone_2, R.id.widget_zone_3)
                zones.forEachIndexed { i, zone ->
                    val query = data.queries.getOrNull(i)
                    val intent = if (query != null) {
                        PendingIntent.getActivity(
                            context, 300 + i,
                            Intent(Intent.ACTION_VIEW, Uri.parse(JourneyInsights.navigateUri(query)))
                                .setPackage("com.google.android.apps.maps")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            FLAGS,
                        )
                    } else {
                        openApp(context, "journey", 310 + i)
                    }
                    setOnClickPendingIntent(zone, intent)
                }
            }
        }
        val tap = when (data) {
            is Start -> if (data.liveKm != null) {
                openApp(context, "ride", 200)
            } else {
                PendingIntent.getForegroundService(
                    context, 201,
                    Intent(context, BikeBridgeService::class.java).setAction(BikeBridgeService.ACTION_START_GPS_RIDE),
                    FLAGS,
                )
            }
            is Fuel -> openApp(context, "fuel", 202)
            is Today -> openApp(context, "explorer", 203)
            is Care -> openApp(context, "service", 204)
            is Last -> openApp(context, data.rideId?.let { "ride:$it" } ?: "ride", 205)
            is Bike -> openApp(context, if (data.v.liveKm != null) "ride" else "cockpit", 206)
            else -> openApp(context, "garage", 207)
        }
        return RemoteViews(context.packageName, R.layout.widget_art).apply {
            setImageViewBitmap(R.id.widget_art, art)
            setOnClickPendingIntent(R.id.widget_root, tap)
        }
    }
}

/** Shared plumbing: update off the main thread when the launcher asks. */
abstract class ArtWidgetProvider(private val kind: HomeWidget) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HomeWidgets.update(context, appWidgetManager, kind, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }
}

class FuelWidgetProvider : ArtWidgetProvider(HomeWidget.FUEL)
class StartWidgetProvider : ArtWidgetProvider(HomeWidget.START)
class TodayWidgetProvider : ArtWidgetProvider(HomeWidget.TODAY)
class CareWidgetProvider : ArtWidgetProvider(HomeWidget.CARE)
class LastRideWidgetProvider : ArtWidgetProvider(HomeWidget.LAST_RIDE)
class PlacesWidgetProvider : ArtWidgetProvider(HomeWidget.PLACES)
