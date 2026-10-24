package dev.gdk.bumblebee.ui.nav

import android.Manifest
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.gdk.bumblebee.BuildConfig
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.MainActivity
import dev.gdk.bumblebee.share.SharePrompt
import dev.gdk.bumblebee.ui.ride.RideViewModel
import dev.gdk.bumblebee.ble.BikeBridgeService
import dev.gdk.bumblebee.gps.PhoneGps
import dev.gdk.bumblebee.ui.cockpit.CockpitScreen
import dev.gdk.bumblebee.ui.explorer.ExplorerScreen
import dev.gdk.bumblebee.ui.explorer.RecapPeriod
import dev.gdk.bumblebee.ui.explorer.RecapScreen
import dev.gdk.bumblebee.ui.fuel.FuelLogScreen
import dev.gdk.bumblebee.ui.garage.documents.DocumentsScreen
import dev.gdk.bumblebee.ui.garage.GarageScreen
import dev.gdk.bumblebee.ui.garage.GarageSettingsScreen
import dev.gdk.bumblebee.ui.garage.ServiceScreen
import dev.gdk.bumblebee.ui.hud.RiderHudScreen
import dev.gdk.bumblebee.ui.journey.JourneyScreen
import dev.gdk.bumblebee.ui.ride.RideScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.gdk.bumblebee.data.local.SnapshotArchive
import dev.gdk.bumblebee.garage.GarageSnapshot
import dev.gdk.bumblebee.snapshot.SnapshotWidget
import dev.gdk.bumblebee.transfer.BringIn
import dev.gdk.bumblebee.transfer.TransferRepository
import dev.gdk.bumblebee.transfer.WidgetStore
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.transfer.TransferScreen
import dev.gdk.bumblebee.ui.transfer.TransferViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import dev.gdk.bumblebee.ui.theme.HudTheme
import dev.gdk.bumblebee.ui.theme.ScreenGutter

/** Portrait gutter (§2.4). Applied once around the nav host, not inside each screen. */
val ShellContentPadding = PaddingValues(horizontal = ScreenGutter)

@Composable
fun BumblebeeApp() {
    GpsBinder()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val navController = rememberNavController()
    if (landscape) {
        HudTheme { RiderHudScreen() }
        return
    }
    val activity = LocalContext.current as? ComponentActivity
    val open = activity?.intent?.getStringExtra(MainActivity.EXTRA_OPEN)
    val rideModel: RideViewModel? = activity?.let { viewModel(viewModelStoreOwner = it) }
    LaunchedEffect(open) {
        val shareId = SharePrompt.parse(open)
        if (shareId != null) {
            navController.navigate(Pillar.RIDE.route) {
                popUpTo(Pillar.COCKPIT.route) { saveState = true }
                launchSingleTop = true
            }
            rideModel?.openShare(shareId)
            return@LaunchedEffect
        }
        val route = when (open) {
            "fuel" -> "fuel"
            "garage" -> Pillar.GARAGE.route
            "ride" -> Pillar.RIDE.route
            else -> return@LaunchedEffect
        }
        navController.navigate(route) {
            popUpTo(Pillar.COCKPIT.route) { saveState = true }
            launchSingleTop = true
        }
    }
    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            val coreRoute = when (currentRoute) {
                "fuel", "transfer", "garage/documents", "garage/service", "garage/settings" -> Pillar.GARAGE.route
                else -> currentRoute
            }
            if (currentRoute?.startsWith("explorer/recap") != true) {
                CockpitCore(
                    currentRoute = coreRoute,
                    onSelect = { pillar ->
                        navController.navigate(pillar.route) {
                            popUpTo(Pillar.COCKPIT.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Pillar.COCKPIT.route,
            modifier = Modifier
                .padding(innerPadding)
                .padding(ShellContentPadding),
            enterTransition = { pillarEnter() },
            exitTransition = { pillarExit() },
            popEnterTransition = { pillarPopEnter() },
            popExitTransition = { pillarPopExit() },
        ) {
            Pillar.entries.forEach { pillar ->
                composable(pillar.route) {
                    when (pillar) {
                        Pillar.COCKPIT -> {
                            val dash = LocalContext.current.applicationContext as DashApp
                            fun openRideTab(rideId: Long?) {
                                dash.pendingRideId.value = rideId
                                navController.navigate(Pillar.RIDE.route) {
                                    popUpTo(Pillar.COCKPIT.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            CockpitScreen(
                                onStartRide = {
                                    navController.navigate(Pillar.JOURNEY.route) {
                                        popUpTo(Pillar.COCKPIT.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onLiveRide = { openRideTab(null) },
                                onOpenLastRide = { openRideTab(it) },
                                onFuel = { navController.navigate("fuel") },
                                onService = { navController.navigate("garage/service") },
                                onSettings = { navController.navigate("garage/settings") },
                            )
                        }
                        Pillar.JOURNEY -> JourneyScreen()
                        Pillar.RIDE -> RideScreen(
                            onOpenExplorer = {
                                navController.navigate(Pillar.EXPLORER.route) {
                                    popUpTo(Pillar.COCKPIT.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                        Pillar.EXPLORER -> ExplorerScreen(
                            onOpenRecap = { year -> navController.navigate("explorer/recap/$year") },
                            onOpenMonthRecap = { month -> navController.navigate("explorer/recap/${month.year}/${month.monthValue}") },
                        )
                        Pillar.GARAGE -> GarageScreen(
                            onOpenFuel = { navController.navigate("fuel") },
                            onOpenTransfer = { navController.navigate("transfer") },
                            onOpenDocuments = { navController.navigate("garage/documents") },
                            onOpenService = { navController.navigate("garage/service") },
                            onOpenSettings = { navController.navigate("garage/settings") },
                        )
                    }
                }
            }
            composable("fuel") {
                FuelLogScreen(onBack = { navController.popBackStack() })
            }
            composable("transfer") {
                TransferRoute(onBack = { navController.popBackStack() })
            }
            composable("garage/documents") {
                DocumentsScreen(onBack = { navController.popBackStack() })
            }
            composable("garage/service") {
                ServiceScreen(onBack = { navController.popBackStack() })
            }
            composable("garage/settings") {
                GarageSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "explorer/recap/{year}",
                arguments = listOf(navArgument("year") { type = NavType.IntType }),
            ) { entry ->
                RecapScreen(
                    period = RecapPeriod.Year(entry.arguments?.getInt("year") ?: java.time.Year.now().value),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "explorer/recap/{year}/{month}",
                arguments = listOf(
                    navArgument("year") { type = NavType.IntType },
                    navArgument("month") { type = NavType.IntType },
                ),
            ) { entry ->
                val now = java.time.YearMonth.now()
                RecapScreen(
                    period = RecapPeriod.Month(
                        java.time.YearMonth.of(
                            entry.arguments?.getInt("year") ?: now.year,
                            entry.arguments?.getInt("month") ?: now.monthValue,
                        ),
                    ),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun TransferRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DashApp
    val scope = rememberCoroutineScope()
    val repository = remember {
        val widget = object : WidgetStore {
            override fun read(): SnapshotWidget {
                val snap = GarageSnapshot.read(context)
                return SnapshotWidget(
                    fuelBars = snap.fuelBars,
                    rangeKm = snap.rangeKm,
                    serviceDueKm = snap.serviceDueKm,
                    alert = snap.alert,
                    todayKm = snap.todayKm,
                    lastRideKm = snap.lastRideKm,
                    lastRideLabel = snap.lastRideLabel,
                    linked = snap.linked,
                    open = snap.open,
                    atMs = snap.atMs,
                )
            }

            override fun write(widget: SnapshotWidget) {
                GarageSnapshot.write(
                    context,
                    widget.fuelBars,
                    widget.rangeKm,
                    widget.serviceDueKm,
                    widget.alert,
                    widget.todayKm,
                    widget.lastRideKm,
                    widget.lastRideLabel,
                    widget.linked,
                    widget.open,
                )
            }
        }
        TransferRepository(
            SnapshotArchive(app.database, File(context.filesDir, "garage")),
            app.prefs,
            widget,
            BuildConfig.BUILD_TYPE,
        )
    }
    val model: TransferViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TransferViewModel(repository) as T
            }
        },
    )
    LaunchedEffect(Unit) {
        model.onPackLoaded(withContext(Dispatchers.IO) { repository.preview() })
    }
    LaunchedEffect(model) {
        model.commits.collect { overwrite ->
            val bundle = model.pending ?: return@collect
            try {
                withContext(Dispatchers.IO) { repository.commit(bundle, overwrite) }
                model.onCommitted()
            } catch (error: Exception) {
                model.onCommitFailed(error.message ?: "Import failed.")
            }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { repository.writeTo(it) }
                }
            } catch (error: Exception) {
                model.onCommitFailed(error.message ?: "Pack failed.")
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { repository.inspect(it) }
                } ?: return@launch
                when (result) {
                    is BringIn.Ready -> {
                        model.onInspected(result)
                        withContext(Dispatchers.IO) { repository.commit(result.bundle, false) }
                        model.onCommitted()
                    }
                    is BringIn.Held -> model.onInspected(result)
                    is BringIn.Failed -> model.onInspected(result)
                }
            } catch (error: Exception) {
                model.onCommitFailed(error.message ?: "Import failed.")
            }
        }
    }
    val stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    TransferScreen(
        model = model,
        onBack = onBack,
        onSave = { save.launch("bumblebee-snapshot-$stamp.bbpack") },
        onOpen = { open.launch(arrayOf("*/*")) },
    )
}

@Composable
private fun GpsBinder() {
    val context = LocalContext.current
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
        BikeBridgeService.start(context)
    }
}
