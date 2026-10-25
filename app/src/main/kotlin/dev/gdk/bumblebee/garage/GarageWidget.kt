package dev.gdk.bumblebee.garage

import android.content.Context
import dev.gdk.bumblebee.widget.ArtWidgetProvider
import dev.gdk.bumblebee.widget.HomeWidget
import dev.gdk.bumblebee.widget.HomeWidgets

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

/**
 * The bike status widget. Kept under its original name so widgets already on a home screen
 * carry on and pick up the new design.
 */
class GarageWidgetProvider : ArtWidgetProvider(HomeWidget.BIKE) {
    companion object {
        fun refresh(context: Context) = HomeWidgets.refreshAll(context)
    }
}
