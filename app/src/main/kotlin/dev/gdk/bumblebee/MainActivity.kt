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
        openRequests.value = intent?.getStringExtra(EXTRA_OPEN)
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
        // The app is already showing: hand the request to it, since it only read the first intent.
        intent.getStringExtra(EXTRA_OPEN)?.let { openRequests.value = it }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { GarageReminders.refresh(application as DashApp) }
    }

    companion object {
        const val EXTRA_OPEN = "open"

        /** Where a widget, notification or shortcut asked to go; the app clears it once it has gone there. */
        val openRequests = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

        fun open(context: Context, destination: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, destination)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
