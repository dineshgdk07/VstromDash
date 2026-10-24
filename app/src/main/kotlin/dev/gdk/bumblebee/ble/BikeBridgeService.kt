package dev.gdk.bumblebee.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.MainActivity
import dev.gdk.bumblebee.ble.android.BleClient
import dev.gdk.bumblebee.ble.android.TelemetrySink
import dev.gdk.bumblebee.garage.GarageSnapshot
import dev.gdk.bumblebee.gps.PhoneGps
import dev.gdk.bumblebee.safety.CrashAlarmActivity
import dev.gdk.bumblebee.protocol.IdentityFrame
import dev.gdk.bumblebee.protocol.TelemetryFrame
import dev.gdk.bumblebee.protocol.decodeFrame
import dev.gdk.bumblebee.nav.MapsNavSource
import dev.gdk.bumblebee.ride.GpsCapturePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil

class BikeBridgeService : LifecycleService() {

    private lateinit var bleClient: BleClient
    private lateinit var frameWriter: FrameWriter
    private lateinit var telemetrySink: TelemetrySink
    private lateinit var heartbeatLoop: HeartbeatLoop
    private var drainJob: Job? = null
    private var heartbeatJob: Job? = null
    private var gpsJob: Job? = null
    private var phoneGps: PhoneGps? = null
    private var stationarySinceMs = 0L
    private var finishRideJob: Job? = null
    private var notifyJob: Job? = null
    private var lastWasReady = false
    private var fullyStarted = false
    private var cachedCellBars = 0
    @Suppress("DEPRECATION")
    private var cellListener: PhoneStateListener? = null
    private var pausedAtMs: Long? = null
    private var crashWatch: CrashWatch? = null

    override fun onCreate() {
        super.onCreate()
        if (!startInForeground()) {
            stopSelf()
            return
        }
        val app = application as DashApp
        bleClient = BleClient(applicationContext)
        frameWriter = FrameWriter()
        telemetrySink = TelemetrySink(app.capture)
        heartbeatLoop = HeartbeatLoop(
            phoneBatteryProvider = ::phoneBattery,
            signalBarsProvider = { cellBars() },
            smsPendingProvider = { false },
            callPendingProvider = { missedCalls() > 0 },
            weatherProvider = { weatherForCluster(app) },
        )

        listenCellBars()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                app.capture.restoreFuelDetector()
                app.capture.recoverUnfinishedRides(pauseWindowMin = app.prefs.pauseWindowMin)
            }
            withContext(Dispatchers.Main.immediate) {
                syncGps(app)
                armAbandonedBikeRide(app)
            }
        }

        lifecycleScope.launch {
            bleClient.state.collect { state ->
                app.connection.value = state
                refreshNotification()
                when (state) {
                    is ConnectionState.Ready -> {
                        val fresh = !lastWasReady
                        lastWasReady = true
                        pausedAtMs = null
                        finishRideJob?.cancel()
                        finishRideJob = null
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                app.capture.onBikeBack(
                                    System.currentTimeMillis(),
                                    app.latestTelemetry.value?.odometerKm,
                                    app.prefs.pauseWindowMin,
                                )
                            }
                            frameWriter.enqueue(
                                FrameWriter.Entry(
                                    FrameWriter.Priority.URGENT,
                                    IdentityFrame(name = app.prefs.riderName, isFresh = fresh).encode(),
                                    note = "identity",
                                ),
                            )
                            telemetrySink.onReady(System.currentTimeMillis())
                            startHeartbeat()
                            startDrain()
                            syncGps(app)
                        }
                    }
                    is ConnectionState.Disconnected, is ConnectionState.Failed, is ConnectionState.Idle -> {
                        drainJob?.cancel()
                        drainJob = null
                        if (lastWasReady) {
                            lastWasReady = false
                            finishRideJob?.cancel()
                            finishRideJob = lifecycleScope.launch {
                                delay(DISCONNECT_GRACE_MS)
                                val window = app.prefs.pauseWindowMin
                                if (window <= 0) {
                                    runCatching { telemetrySink.onDisconnected(System.currentTimeMillis()) }
                                } else {
                                    val at = System.currentTimeMillis()
                                    runCatching { app.capture.pauseActiveRide(at) }
                                    pausedAtMs = at
                                    refreshNotification()
                                    delay(window * 60_000L)
                                    pausedAtMs = null
                                    runCatching {
                                        app.capture.endRideNow(System.currentTimeMillis())
                                        publishGarageWidget(app)
                                    }
                                }
                                syncGps(app)
                                refreshNotification()
                            }
                        }
                        heartbeatJob?.cancel()
                        heartbeatJob = null
                    }
                    else -> Unit
                }
            }
        }

        lifecycleScope.launch {
            bleClient.notifications.collect { bytes ->
                runCatching {
                    telemetrySink.onNotify(bytes, System.currentTimeMillis())
                    val frame = decodeFrame(bytes)
                    if (frame is TelemetryFrame) {
                        app.latestTelemetry.value = frame
                        ensureCrashWatch(app).onSpeed(frame.speedKmh, System.currentTimeMillis())
                    }
                }
            }
        }

        lifecycleScope.launch {
            MapsNavSource.frame.collect { nav ->
                if (nav != null && bleClient.state.value is ConnectionState.Ready) {
                    frameWriter.enqueue(FrameWriter.Entry(FrameWriter.Priority.NAV, nav.encode(), "nav"))
                }
            }
        }
        lifecycleScope.launch {
            while (isActive) {
                delay(2_000)
                if (bleClient.state.value !is ConnectionState.Ready) continue
                val nav = MapsNavSource.frame.value ?: continue
                frameWriter.forceNav(nav.encode())
            }
        }

        notifyJob = lifecycleScope.launch {
            while (isActive) {
                refreshNotification()
                delay(2_000)
            }
        }
        fullyStarted = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!fullyStarted) return START_STICKY
        when (intent?.action) {
            ACTION_STOP -> {
                bleClient.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val mac = intent.getStringExtra(EXTRA_MAC)
                    ?: (application as DashApp).prefs.bikeMac
                if (mac != null) bleClient.connect(mac)
            }
            ACTION_START_GPS_RIDE -> {
                lifecycleScope.launch {
                    val app = application as DashApp
                    if (app.capture.activeRideId == null) {
                        app.capture.startRide(System.currentTimeMillis(), gpsOnly = true)
                    }
                    syncGps(app)
                }
            }
            ACTION_SKIP_AUTO_TODAY -> {
                val dash = application as DashApp
                dash.prefs.skipAutoConnectDate = java.time.LocalDate.now().toString()
                refreshNotification()
            }
            ACTION_END_RIDE -> {
                pausedAtMs = null
                lifecycleScope.launch(Dispatchers.IO) {
                    val dash = application as DashApp
                    dash.capture.endRideNow(System.currentTimeMillis())
                    publishGarageWidget(dash)
                    refreshNotification()
                }
            }
            ACTION_STOP_GPS_RIDE -> {
                lifecycleScope.launch {
                    val app = application as DashApp
                    if (app.capture.activeRideId != null && !lastWasReady) {
                        app.capture.finishRide(System.currentTimeMillis())
                    }
                    syncGps(app)
                }
            }
            else -> {
                (application as DashApp).prefs.bikeMac?.let { bleClient.connect(it) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (fullyStarted) {
            drainJob?.cancel()
            heartbeatJob?.cancel()
            stopGps()
            finishRideJob?.cancel()
            notifyJob?.cancel()
            runBlocking {
                withTimeoutOrNull(2_000) {
                    withContext(Dispatchers.IO) {
                        val app = application as DashApp
                        if (app.prefs.pauseWindowMin > 0 && app.capture.activeRideId != null) {
                            runCatching { app.capture.pauseActiveRide(System.currentTimeMillis()) }
                        } else {
                            runCatching { telemetrySink.onDisconnected(System.currentTimeMillis()) }
                        }
                    }
                }
            }
            crashWatch?.stop()
            stopCellListener()
            bleClient.disconnect()
            bleClient.shutdown()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun weatherForCluster(app: DashApp): Pair<Int, Int> {
        val w = app.weather.snapshot.value ?: return 0x01 to 0x00
        val fahrenheit = ceil(w.tempC * 9.0 / 5.0 + 32.0).toInt()
        return w.suzukiCode to (fahrenheit + 115).coerceIn(1, 255)
    }

    private fun syncGps(app: DashApp) {
        val linked = bleClient.state.value is ConnectionState.Ready
        if (GpsCapturePolicy.shouldCapture(linked, app.capture.isGpsOnly)) {
            startGps(app)
        } else {
            stopGps()
        }
    }

    private fun startGps(app: DashApp) {
        if (gpsJob?.isActive == true) return
        val gps = PhoneGps(applicationContext)
        if (!gps.hasPermission()) return
        phoneGps = gps
        stationarySinceMs = System.currentTimeMillis()
        gpsJob = lifecycleScope.launch {
            launch {
                app.latestTelemetry.collect { tel ->
                    applyPace(tel?.speedKmh, app.latestGps.value?.speedMps)
                }
            }
            gps.fixes().collect { fix ->
                applyPace(app.latestTelemetry.value?.speedKmh, fix.speedMps)
                app.latestGps.value = fix
                launch { app.weather.maybeRefresh(fix.lat, fix.lng) }
                if (app.capture.activeRideId != null) {
                    val linked = bleClient.state.value is ConnectionState.Ready
                    runCatching {
                        if (linked) app.capture.onGps(fix) else app.capture.feedPhoneGps(fix)
                    }
                }
            }
        }
    }

    private fun applyPace(bikeSpeedKmh: Int?, speedMps: Float?) {
        val now = System.currentTimeMillis()
        if (stationarySinceMs == 0L) stationarySinceMs = now
        if (GpsCapturePolicy.isMoving(speedMps, bikeSpeedKmh)) stationarySinceMs = now
        phoneGps?.setPace(GpsCapturePolicy.pace(now - stationarySinceMs))
        val app = application as DashApp
        val windowMin = app.prefs.pauseWindowMin
        val gpsOnly = app.capture.isGpsOnly
        if (gpsOnly && windowMin > 0 && now - stationarySinceMs >= windowMin * 60_000L) {
            stationarySinceMs = now
            lifecycleScope.launch(Dispatchers.IO) {
                app.capture.endRideNow(System.currentTimeMillis())
            }
        }
    }

    private fun stopGps() {
        gpsJob?.cancel()
        gpsJob = null
        phoneGps = null
        stationarySinceMs = 0L
    }

    private fun startDrain() {
        if (drainJob?.isActive == true) return
        drainJob = lifecycleScope.launch {
            while (isActive) {
                if (bleClient.state.value !is ConnectionState.Ready) {
                    delay(200)
                    continue
                }
                val entry = frameWriter.take()
                if (bleClient.state.value is ConnectionState.Ready) {
                    bleClient.write(entry.frame)
                }
            }
        }
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = lifecycleScope.launch {
            heartbeatLoop.run { frame ->
                frameWriter.enqueue(
                    FrameWriter.Entry(FrameWriter.Priority.HEARTBEAT, frame.encode()),
                )
            }
        }
    }

    private fun phoneBattery(): Pair<Int, Boolean> {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        (application as DashApp).phoneBattery.value = pct
        return pct to charging
    }

    private fun armAbandonedBikeRide(app: DashApp) {
        if (app.capture.activeRideId == null || app.capture.isGpsOnly) return
        if (bleClient.state.value is ConnectionState.Ready) return
        finishRideJob?.cancel()
        finishRideJob = lifecycleScope.launch {
            delay(DISCONNECT_GRACE_MS)
            if (bleClient.state.value !is ConnectionState.Ready && !app.capture.isGpsOnly) {
                runCatching { app.capture.finishAtLastSample() }
                syncGps(app)
                refreshNotification()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun listenCellBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return
        val tm = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager ?: return
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                cachedCellBars = barsFromLevel(signalStrength.level)
            }
        }
        cellListener = listener
        tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
    }

    @Suppress("DEPRECATION")
    private fun stopCellListener() {
        val listener = cellListener ?: return
        val tm = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
        tm?.listen(listener, PhoneStateListener.LISTEN_NONE)
        cellListener = null
    }

    private fun barsFromLevel(level: Int): Int = (level * 3 / 4).coerceIn(0, 3)

    private fun cellBars(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val tm = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager ?: return 0
            val level = runCatching { tm.signalStrength?.level }.getOrNull() ?: return 0
            return barsFromLevel(level)
        }
        return cachedCellBars
    }

    private fun missedCalls(): Int = 0

    private suspend fun publishGarageWidget(app: DashApp) {
        val tel = app.latestTelemetry.value
        val today = runCatching { app.capture.todayStats().km }.getOrDefault(0.0)
        val last = runCatching { app.capture.lastFinished() }.getOrNull()
        val lastKm = last?.let { ride ->
            val s = ride.odoStartKm
            val e = ride.odoEndKm
            if (s != null && e != null) (e - s).toDouble() else ride.gpsDistanceM / 1000.0
        } ?: 0.0
        val lastLabel = last?.let {
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(it.startTs))
        } ?: ""
        val prev = GarageSnapshot.read(app)
        GarageSnapshot.write(
            this,
            fuelBars = tel?.fuelBars ?: prev.fuelBars,
            rangeKm = prev.rangeKm,
            serviceDueKm = app.prefs.remainingServiceKm(tel?.odometerKm) ?: prev.serviceDueKm,
            alert = prev.alert,
            todayKm = today,
            lastRideKm = lastKm,
            lastRideLabel = lastLabel,
            linked = app.connection.value is ConnectionState.Ready,
        )
    }

    private fun ensureCrashWatch(app: DashApp): CrashWatch {
        val existing = crashWatch
        if (existing != null) return existing
        val watch = CrashWatch(this) { verdict ->
            val gps = app.latestGps.value
            CrashAlarmActivity.launch(
                this,
                speedBefore = verdict.speedBeforeKmh,
                lat = gps?.lat,
                lng = gps?.lng,
            )
        }
        crashWatch = watch
        return watch
    }

    private fun refreshNotification() {
        val app = application as DashApp
        val linked = app.connection.value is ConnectionState.Ready
        val speed = app.latestTelemetry.value?.speedKmh
        val bars = app.latestTelemetry.value?.fuelBars
        val pausedFor = pausedAtMs?.let { ((System.currentTimeMillis() - it) / 60_000L).toInt().coerceAtLeast(0) }
        val text = if (pausedFor != null) {
            "Ride paused · $pausedFor min · resumes when you start the bike"
        } else {
            buildList {
                add(if (linked) "Linked" else "Waiting")
                if (speed != null) add("$speed km/h")
                if (bars != null) add("$bars bars")
            }.joinToString(" · ")
        }
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, BikeBridgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val endRide = PendingIntent.getService(
            this, 3,
            Intent(this, BikeBridgeService::class.java).setAction(ACTION_END_RIDE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val skipToday = PendingIntent.getService(
            this, 4,
            Intent(this, BikeBridgeService::class.java).setAction(ACTION_SKIP_AUTO_TODAY),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val launch = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bumblebee")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(launch)
            .addAction(0, if (pausedFor != null) "End ride" else "Stop", if (pausedFor != null) endRide else stop)
            .addAction(0, "Not today", skipToday)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun startInForeground(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Bike link", NotificationManager.IMPORTANCE_LOW),
            )
        }
        refreshNotification()
        val notification = nm.getActiveNotifications()
            ?.firstOrNull { it.id == NOTIFICATION_ID }
            ?.notification
            ?: NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bumblebee")
                .setContentText("Starting…")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .build()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                try {
                    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
                } catch (_: SecurityException) {
                    ServiceCompat.startForeground(
                        this, NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                    )
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "cannot start foreground", e)
            false
        }
    }

    companion object {
        private const val TAG = "BikeBridge"
        private const val CHANNEL_ID = "ble"
        private const val NOTIFICATION_ID = 1
        private const val DISCONNECT_GRACE_MS = 20_000L
        const val ACTION_STOP = "dev.gdk.bumblebee.ble.STOP"
        const val ACTION_CONNECT = "dev.gdk.bumblebee.ble.CONNECT"
        const val ACTION_START_GPS_RIDE = "dev.gdk.bumblebee.ble.START_GPS_RIDE"
        const val ACTION_STOP_GPS_RIDE = "dev.gdk.bumblebee.ble.STOP_GPS_RIDE"
        const val ACTION_END_RIDE = "dev.gdk.bumblebee.ble.END_RIDE"
        const val ACTION_SKIP_AUTO_TODAY = "dev.gdk.bumblebee.ble.SKIP_AUTO_TODAY"
        const val EXTRA_MAC = "mac"

        fun start(context: Context) {
            launch(context, Intent(context, BikeBridgeService::class.java))
        }

        fun connect(context: Context, mac: String) {
            launch(
                context,
                Intent(context, BikeBridgeService::class.java)
                    .setAction(ACTION_CONNECT)
                    .putExtra(EXTRA_MAC, mac),
            )
        }

        fun startGpsRide(context: Context) {
            launch(
                context,
                Intent(context, BikeBridgeService::class.java).setAction(ACTION_START_GPS_RIDE),
            )
        }

        fun endRide(context: Context) {
            launch(context, Intent(context, BikeBridgeService::class.java).setAction(ACTION_END_RIDE))
        }

        fun stopGpsRide(context: Context) {
            context.startService(
                Intent(context, BikeBridgeService::class.java).setAction(ACTION_STOP_GPS_RIDE),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BikeBridgeService::class.java).setAction(ACTION_STOP),
            )
        }

        private fun launch(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
