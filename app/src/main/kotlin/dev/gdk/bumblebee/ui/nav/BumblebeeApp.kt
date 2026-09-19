package dev.gdk.bumblebee.ui.nav

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.gps.PhoneGps
import dev.gdk.bumblebee.ui.cockpit.CockpitScreen
import dev.gdk.bumblebee.ui.hud.RiderHudScreen
import dev.gdk.bumblebee.ui.journey.JourneyScreen
import dev.gdk.bumblebee.ui.theme.ForgeBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BumblebeeApp() {
    GpsBinder()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        RiderHudScreen()
        return
    }
    val navController = rememberNavController()
    Scaffold(
        containerColor = ForgeBlack,
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            CockpitCore(
                currentRoute = currentRoute,
                onSelect = { pillar ->
                    navController.navigate(pillar.route) {
                        popUpTo(Pillar.COCKPIT.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Pillar.COCKPIT.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            Pillar.entries.forEach { pillar ->
                composable(pillar.route) {
                    when (pillar) {
                        Pillar.COCKPIT -> CockpitScreen(
                            onNavigate = {
                                navController.navigate(Pillar.JOURNEY.route) {
                                    popUpTo(Pillar.COCKPIT.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onExplore = {
                                navController.navigate(Pillar.EXPLORER.route) {
                                    popUpTo(Pillar.COCKPIT.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                        Pillar.JOURNEY -> JourneyScreen()
                        else -> PlaceholderScreen(pillar.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun GpsBinder() {
    val context = LocalContext.current
    val app = context.applicationContext as DashApp
    val gps = remember { PhoneGps(context) }
    var granted by remember { mutableStateOf(gps.hasPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        gps.fixes().collect { fix ->
            app.latestGps.value = fix
            if (app.capture.activeRideId != null) {
                withContext(Dispatchers.IO) {
                    runCatching { app.capture.onGps(fix) }
                }
            }
        }
    }
}
