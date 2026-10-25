package dev.gdk.bumblebee.safety

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.gdk.bumblebee.DashApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
import java.util.Date

/** Where a crash alert stands. */
data class CrashAlertState(
    val secondsLeft: Int,
    val sent: Boolean = false,
    val sentTo: Int = 0,
    /** Why help can't be texted, shown on the alarm so the rider knows. */
    val problem: String? = null,
    /** A practice run from Settings: contacts get a TEST text, never a real alert. */
    val test: Boolean = false,
)

/**
 * Best-effort safety aid, not a certified emergency device.
 *
 * Owns the countdown, the alarm sound and the SMS, so they run from the bike service whether or
 * not the alarm screen manages to open. The screen and a full-screen notification both offer
 * "I'm OK". When the countdown ends, the saved contacts get a text with the location.
 */
object CrashAlert {
    const val CHANNEL = "crash_alarm"
    private const val NOTIFICATION_ID = 7_301
    private const val COUNTDOWN_S = 30

    private val _state = MutableStateFlow<CrashAlertState?>(null)
    val state: StateFlow<CrashAlertState?> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private var appContext: Context? = null
    private var speedBefore = 0
    private var lat: Double? = null
    private var lng: Double? = null

    private val tick = object : Runnable {
        override fun run() {
            val s = _state.value ?: return
            val ctx = appContext ?: return
            if (s.secondsLeft <= 1) {
                sendHelp(ctx)
                return
            }
            _state.value = s.copy(secondsLeft = s.secondsLeft - 1)
            main.postDelayed(this, 1_000L)
        }
    }

    fun start(context: Context, speedBeforeKmh: Int, lat: Double?, lng: Double?, test: Boolean = false) {
        if (_state.value != null) return
        val ctx = context.applicationContext
        appContext = ctx
        speedBefore = speedBeforeKmh
        this.lat = lat
        this.lng = lng
        _state.value = CrashAlertState(COUNTDOWN_S, problem = problem(ctx), test = test)
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
        tone?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK)
        vibrate(ctx)
        notifyAlarm(ctx)
        // Opens straight away when Android allows it; the full-screen notification covers the rest.
        runCatching { ctx.startActivity(CrashAlarmActivity.intent(ctx)) }
        main.postDelayed(tick, 1_000L)
    }

    /** The rider is fine. If help was already texted, say so to the same people. */
    fun cancel(context: Context) {
        val s = _state.value ?: return
        val ctx = context.applicationContext
        main.removeCallbacks(tick)
        stopNoise(ctx)
        if (s.sent && !s.test) text(ctx, followUp = true)
        _state.value = null
        ctx.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** Skip the countdown. */
    fun sendNow(context: Context) {
        if (_state.value?.sent != false) return
        main.removeCallbacks(tick)
        sendHelp(context.applicationContext)
    }

    private fun sendHelp(ctx: Context) {
        val count = text(ctx, followUp = false, test = _state.value?.test == true)
        stopNoise(ctx)
        val s = _state.value ?: return
        _state.value = s.copy(
            secondsLeft = 0,
            sent = count > 0,
            sentTo = count,
            problem = if (count > 0) null else (problem(ctx) ?: "Couldn't send the text"),
        )
        notifyAlarm(ctx)
    }

    /** What stops an alert from going out, or null when it can. */
    fun problem(ctx: Context): String? {
        val app = ctx.applicationContext as DashApp
        return when {
            numbers(app).isEmpty() -> "No emergency numbers saved in Settings"
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ->
                "SMS permission is off, so no one can be texted"
            else -> null
        }
    }

    fun numbers(app: DashApp): List<String> =
        app.prefs.emergencyContacts.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(3)

    /** Texts every contact; returns how many it reached the phone's SMS queue for. */
    fun text(ctx: Context, followUp: Boolean, test: Boolean = false): Int {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return 0
        val app = ctx.applicationContext as DashApp
        val numbers = numbers(app)
        if (numbers.isEmpty()) return 0
        // The freshest fix wins: GPS may have caught up since the impact.
        val fix = app.latestGps.value
        val la = fix?.lat ?: lat
        val ln = fix?.lng ?: lng
        val map = if (la == null || ln == null) "location unavailable" else "https://maps.google.com/?q=$la,$ln"
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        val body = when {
            test -> "TEST from Bumblebee for ${app.prefs.riderName}. This is not an emergency. Crash alerts will come from this number."
            followUp -> "Update: ${app.prefs.riderName} is OK. Please ignore the earlier crash alert."
            else -> buildString {
                append("Possible crash: ${app.prefs.riderName} may need help. $map at $time. Speed before: $speedBefore km/h.")
                app.prefs.bloodGroup.trim().takeIf { it.isNotEmpty() }?.let { append(" Blood: $it.") }
                app.prefs.medicalNote.trim().takeIf { it.isNotEmpty() }?.let { append(" Note: $it.") }
                append(" Sent automatically by Bumblebee.")
            }
        }
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return 0
        // Longer than one SMS once a location and notes are in, so always send in parts.
        val parts = sms.divideMessage(body)
        return numbers.count { number ->
            runCatching { sms.sendMultipartTextMessage(number, null, parts, null, null) }.isSuccess
        }
    }

    private fun vibrate(ctx: Context) {
        val vibrator = ctx.getSystemService(Vibrator::class.java) ?: return
        val pattern = longArrayOf(0, 800, 400)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        }
    }

    private fun stopNoise(ctx: Context) {
        tone?.stopTone()
        tone?.release()
        tone = null
        runCatching { ctx.getSystemService(Vibrator::class.java)?.cancel() }
    }

    private fun notifyAlarm(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Crash alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Full-screen alarm after a possible crash"
                    setBypassDnd(true)
                    setSound(null, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                },
            )
        }
        val s = _state.value ?: return
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(ctx, 0, CrashAlarmActivity.intent(ctx), flags)
        val ok = PendingIntent.getActivity(ctx, 1, CrashAlarmActivity.intent(ctx).setAction(CrashAlarmActivity.ACTION_OK), flags)
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .addAction(0, "I'm OK", ok)
        if (s.sent) {
            builder.setContentTitle("Help alerted")
                .setContentText("Texted ${s.sentTo} contact${if (s.sentTo == 1) "" else "s"} with your location. Tap I'm OK to tell them you're fine.")
        } else {
            builder.setContentTitle("Possible crash. Are you OK?")
                .setContentText(s.problem ?: "Emergency contacts will be texted in ${COUNTDOWN_S} s unless you tap I'm OK.")
                .setFullScreenIntent(open, true)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() + s.secondsLeft * 1_000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(true)
        }
        runCatching { nm.notify(NOTIFICATION_ID, builder.build()) }
    }
}
