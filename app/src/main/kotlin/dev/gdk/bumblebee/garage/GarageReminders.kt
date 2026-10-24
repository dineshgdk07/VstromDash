package dev.gdk.bumblebee.garage

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.BikeAutoConnect
import dev.gdk.bumblebee.ble.BikeBridgeService
import dev.gdk.bumblebee.MainActivity
import dev.gdk.bumblebee.data.local.toRecord
import dev.gdk.bumblebee.ride.GarageCare
import dev.gdk.bumblebee.ride.RideRhythm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

object GarageReminders {
    const val CHANNEL = "garage_care"
    const val ACTION_CHECK = "dev.gdk.bumblebee.garage.CHECK"
    private const val NOTIFY_ID = 42

    suspend fun refresh(app: DashApp) {
        if (!app.prefs.remindersEnabled) return
        val lines = withContext(Dispatchers.IO) {
            app.capture.ensureGarageDefaults(
                app.prefs.rcExpiry,
                app.prefs.insuranceExpiry,
                app.prefs.pucExpiry,
                app.prefs.serviceIntervalKm,
                app.prefs.lastServiceOdoKm,
            )
            val today = LocalDate.now()
            val odo = app.latestTelemetry.value?.odometerKm
            val remindDays = app.prefs.remindDays
            val remindKm = app.prefs.remindKm
            val rides = app.capture.finishedRides().map { it.toRecord() }
            val kmPerDay = RideRhythm.paceKmPerDay(rides)
            val docs = app.capture.allDocuments().mapNotNull { doc ->
                val insight = GarageCare.documentInsight(doc.expiryIso, today, doc.title, remindDays)
                insight.takeIf { GarageCare.shouldRemind(it.band, null, it.daysRemaining, remindKm, remindDays) }?.label
            }
            val plans = app.capture.allPlans().filter { it.active }.mapNotNull { plan ->
                val insight = GarageCare.planInsight(
                    title = plan.title,
                    currentOdoKm = odo,
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
                insight.takeIf {
                    GarageCare.shouldRemind(it.band, it.kmRemaining, it.daysRemaining, remindKm, remindDays)
                }?.label
            }
            docs + plans
        }
        if (lines.isEmpty()) return
        val stamp = LocalDate.now().toString() + "|" + lines.joinToString("|")
        if (stamp == app.prefs.lastReminderStamp) return
        app.prefs.lastReminderStamp = stamp
        post(app, lines)
    }

    fun scheduleDaily(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, GarageCheckReceiver::class.java).setAction(ACTION_CHECK)
        val pi = PendingIntent.getBroadcast(
            context,
            7,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    private fun post(context: Context, lines: List<String>) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val inbox = NotificationCompat.InboxStyle().setBigContentTitle("Bike care")
        lines.take(5).forEach { inbox.addLine(it) }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Bike care")
            .setContentText(lines.first())
            .setStyle(inbox)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFY_ID, n) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL, "Garage reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}

class GarageCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != GarageReminders.ACTION_CHECK && action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        Thread {
            try {
                val app = context.applicationContext as? DashApp ?: return@Thread
                if (action == Intent.ACTION_BOOT_COMPLETED && BikeAutoConnect.shouldStart(context)) {
                    BikeAutoConnect.observe(context)
                    BikeBridgeService.start(context)
                }
                runBlocking { GarageReminders.refresh(app) }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
