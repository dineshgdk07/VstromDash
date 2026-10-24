package dev.gdk.bumblebee.safety

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.ForgeBlack
import dev.gdk.bumblebee.ui.theme.SuzukiYellow
import java.text.DateFormat
import java.util.Date

/**
 * Best-effort safety aid, not a certified emergency device.
 * A 30 second alarm. If it is not cancelled, SMS goes to the saved contacts.
 */
class CrashAlarmActivity : ComponentActivity() {
    private var tone: ToneGenerator? = null
    private var secondsLeft by mutableIntStateOf(30)
    private var sent = false
    private val ticker = object : Runnable {
        override fun run() {
            if (secondsLeft <= 0) {
                sendSms(test = false, followUp = false)
                secondsLeft = -1
                return
            }
            secondsLeft -= 1
            window.decorView.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
        tone?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK)
        window.decorView.postDelayed(ticker, 1_000L)
        setContent {
            BumblebeeTheme {
                Box(Modifier.fillMaxSize().background(ForgeBlack), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { cancel(sent) },
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuzukiYellow),
                    ) {
                        Text(
                            if (secondsLeft >= 0) "I'm OK\n${secondsLeft}s" else "Help alerted\nCancel — I'm OK",
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    }
                }
            }
        }
    }

    private fun cancel(alreadySent: Boolean) {
        window.decorView.removeCallbacks(ticker)
        tone?.stopTone()
        if (alreadySent) sendSms(test = false, followUp = true)
        finish()
    }

    private fun sendSms(test: Boolean, followUp: Boolean) {
        if (sent && !followUp) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val app = application as DashApp
        val numbers = app.prefs.emergencyContacts.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        if (numbers.isEmpty()) return
        val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
        val speed = intent.getIntExtra(EXTRA_SPEED, 0)
        val whenText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        val map = if (lat.isNaN() || lng.isNaN()) "location unavailable" else "https://maps.google.com/?q=$lat,$lng"
        val body = when {
            test -> "TEST from Bumblebee. This is not an emergency."
            followUp -> "Update: ${app.prefs.riderName} is OK. Earlier alert can be ignored."
            else -> buildString {
                append("Possible crash detected for ${app.prefs.riderName}. Location: $map at $whenText. Speed before: $speed km/h.")
                app.prefs.bloodGroup.trim().takeIf { it.isNotEmpty() }?.let { append(" Blood: $it.") }
                app.prefs.medicalNote.trim().takeIf { it.isNotEmpty() }?.let { append(" Note: $it.") }
                append(" This is a best-effort alert, not a certified emergency service.")
            }
        }
        val sms = SmsManager.getDefault()
        for (number in numbers) {
            runCatching { sms.sendTextMessage(number, null, body, null, null) }
        }
        sent = !followUp
    }

    override fun onDestroy() {
        tone?.release()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SPEED = "speed"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LNG = "lng"

        fun launch(context: Context, speedBefore: Int, lat: Double?, lng: Double?) {
            context.startActivity(
                Intent(context, CrashAlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_SPEED, speedBefore)
                    .putExtra(EXTRA_LAT, lat ?: Double.NaN)
                    .putExtra(EXTRA_LNG, lng ?: Double.NaN),
            )
        }
    }
}
