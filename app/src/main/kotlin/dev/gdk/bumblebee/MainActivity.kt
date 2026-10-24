package dev.gdk.bumblebee

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dev.gdk.bumblebee.garage.GarageReminders
import dev.gdk.bumblebee.ui.nav.BumblebeeApp
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        GarageReminders.scheduleDaily(this)
        enableEdgeToEdge()
        setContent {
            BumblebeeTheme {
                BumblebeeApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { GarageReminders.refresh(application as DashApp) }
    }

    companion object {
        const val EXTRA_OPEN = "open"

        fun open(context: Context, destination: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, destination)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
