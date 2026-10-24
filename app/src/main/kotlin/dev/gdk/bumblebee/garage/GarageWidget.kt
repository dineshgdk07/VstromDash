package dev.gdk.bumblebee.garage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import dev.gdk.bumblebee.MainActivity
import dev.gdk.bumblebee.R
import dev.gdk.bumblebee.ble.BikeBridgeService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Last-known garage numbers. The widget never polls; publishers write this snapshot. */
object GarageSnapshot {
    private const val PREFS = "garage_widget"
    private const val FUEL = "fuel"
    private const val RANGE = "range"
    private const val SERVICE = "service"
    private const val ALERT = "alert"
    private const val TODAY = "today"
    private const val LAST_KM = "last_km"
    private const val LAST_LABEL = "last_label"
    private const val LINKED = "linked"
    private const val AT = "at"
    private const val TARGET = "target"
    const val OPEN_GARAGE = "garage"
    const val OPEN_FUEL = "fuel"

    fun write(
        context: Context,
        fuelBars: Int?,
        rangeKm: Int?,
        serviceDueKm: Int?,
        alert: String,
        todayKm: Double,
        lastRideKm: Double = read(context).lastRideKm,
        lastRideLabel: String = read(context).lastRideLabel,
        linked: Boolean = read(context).linked,
        open: String = read(context).open,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(FUEL, fuelBars ?: -1)
            .putInt(RANGE, rangeKm ?: -1)
            .putInt(SERVICE, serviceDueKm ?: Int.MIN_VALUE)
            .putString(ALERT, alert)
            .putFloat(TODAY, todayKm.toFloat())
            .putFloat(LAST_KM, lastRideKm.toFloat())
            .putString(LAST_LABEL, lastRideLabel)
            .putBoolean(LINKED, linked)
            .putString(TARGET, open)
            .putLong(AT, System.currentTimeMillis())
            .apply()
        GarageWidgetProvider.refresh(context)
    }

    fun read(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            fuelBars = p.getInt(FUEL, -1).takeIf { it >= 0 },
            rangeKm = p.getInt(RANGE, -1).takeIf { it >= 0 },
            serviceDueKm = p.getInt(SERVICE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
            alert = p.getString(ALERT, "") ?: "",
            todayKm = p.getFloat(TODAY, 0f).toDouble(),
            lastRideKm = p.getFloat(LAST_KM, 0f).toDouble(),
            lastRideLabel = p.getString(LAST_LABEL, "") ?: "",
            linked = p.getBoolean(LINKED, false),
            open = p.getString(TARGET, OPEN_GARAGE) ?: OPEN_GARAGE,
            atMs = p.getLong(AT, 0L),
        )
    }

    data class Snapshot(
        val fuelBars: Int?,
        val rangeKm: Int?,
        val serviceDueKm: Int?,
        val alert: String,
        val todayKm: Double,
        val lastRideKm: Double = 0.0,
        val lastRideLabel: String = "",
        val linked: Boolean = false,
        val open: String = OPEN_GARAGE,
        val atMs: Long,
    )
}

class GarageWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, render(context, appWidgetManager.getAppWidgetOptions(id)))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, render(context, newOptions))
    }

    companion object {
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, GarageWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) mgr.updateAppWidget(id, render(context, mgr.getAppWidgetOptions(id)))
        }

        private fun render(context: Context, options: Bundle?): RemoteViews {
            val snap = GarageSnapshot.read(context)
            val fuel = when {
                snap.fuelBars == null -> "Fuel —"
                snap.rangeKm != null -> "${snap.fuelBars}/6 · ${snap.rangeKm} km"
                else -> "${snap.fuelBars}/6"
            }
            val service = when (val km = snap.serviceDueKm) {
                null -> "Service —"
                else -> if (km < 0) "Service overdue" else "Service in $km km"
            }
            val asOf = if (snap.atMs > 0 && System.currentTimeMillis() - snap.atMs > 60 * 60_000L) {
                val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
                "as of ${clock.format(Instant.ofEpochMilli(snap.atMs))}"
            } else {
                ""
            }
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags,
            )
            val alert = PendingIntent.getActivity(
                context, 1, MainActivity.open(context, snap.open), flags,
            )
            val start = PendingIntent.getForegroundService(
                context, 4,
                Intent(context, BikeBridgeService::class.java).setAction(BikeBridgeService.ACTION_START_GPS_RIDE),
                flags,
            )
            val wide = (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0) >= 180
            return RemoteViews(context.packageName, R.layout.widget_garage).apply {
                setTextViewText(R.id.widget_fuel, fuel)
                setTextViewText(R.id.widget_service, service)
                setTextViewText(R.id.widget_alert, snap.alert)
                setTextViewText(R.id.widget_today, if (snap.todayKm > 0) "Today ${snap.todayKm.toInt()} km" else "")
                setTextViewText(
                    R.id.widget_last,
                    if (snap.lastRideLabel.isNotBlank()) {
                        "Last ${snap.lastRideKm.toInt()} km · ${snap.lastRideLabel}"
                    } else {
                        ""
                    },
                )
                setTextViewText(R.id.widget_asof, asOf)
                setTextViewText(R.id.widget_link, if (snap.linked) "● linked" else "○ parked")
                setViewVisibility(R.id.widget_start, if (wide) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(R.id.widget_root, open)
                setOnClickPendingIntent(R.id.widget_alert, alert)
                setOnClickPendingIntent(R.id.widget_start, start)
            }
        }
    }
}
