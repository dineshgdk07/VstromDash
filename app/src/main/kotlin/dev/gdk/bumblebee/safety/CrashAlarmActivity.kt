package dev.gdk.bumblebee.safety

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.ForgeBlack
import dev.gdk.bumblebee.ui.theme.SuzukiYellow

/**
 * The full-screen face of [CrashAlert]: a countdown, a huge "I'm OK", and "Send help now".
 * Closing this screen doesn't stop the countdown; only "I'm OK" does.
 */
class CrashAlarmActivity : ComponentActivity() {

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (handleOk(intent)) return
        setContent {
            BumblebeeTheme {
                val state by CrashAlert.state.collectAsState()
                LaunchedEffect(state == null) { if (state == null) finish() }
                val s = state ?: return@BumblebeeTheme
                Column(
                    Modifier.fillMaxSize().background(ForgeBlack).safeDrawingPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        when {
                            s.test -> "Practice alarm"
                            s.sent -> "Help alerted"
                            else -> "Possible crash"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                    )
                    Text(
                        when {
                            s.test && s.sent -> "Sent a TEST text to ${s.sentTo} contact${if (s.sentTo == 1) "" else "s"}. This is what a real alarm looks like."
                            s.test -> "In ${s.secondsLeft} s your contacts get a TEST text, not a real alert. Tap I'm OK to stop."
                            s.sent -> "Texted ${s.sentTo} contact${if (s.sentTo == 1) "" else "s"} with your location."
                            else -> "Your emergency contacts get a text with your location in ${s.secondsLeft} s."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                    s.problem?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFFF8A80), textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = { CrashAlert.cancel(this@CrashAlarmActivity) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SuzukiYellow, contentColor = Color.Black),
                    ) {
                        Text(
                            if (s.sent) "I'm OK\ntell them" else "I'm OK\n${s.secondsLeft}",
                            style = MaterialTheme.typography.displayMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (!s.sent) {
                        OutlinedButton(
                            onClick = { CrashAlert.sendNow(this@CrashAlarmActivity) },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        ) {
                            Text("Send help now", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOk(intent)
    }

    /** The notification's "I'm OK" lands here. */
    private fun handleOk(intent: Intent?): Boolean {
        if (intent?.action != ACTION_OK) return false
        CrashAlert.cancel(this)
        finish()
        return true
    }

    companion object {
        const val ACTION_OK = "dev.gdk.bumblebee.CRASH_OK"

        fun intent(context: Context): Intent =
            Intent(context, CrashAlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
