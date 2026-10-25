package dev.gdk.bumblebee.ble

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.MainActivity
import dev.gdk.bumblebee.ble.android.BleClient
import dev.gdk.bumblebee.ble.android.TelemetrySink
import dev.gdk.bumblebee.garage.GarageSnapshot
import dev.gdk.bumblebee.gps.PhoneGps
import dev.gdk.bumblebee.safety.CrashAlert
import dev.gdk.bumblebee.protocol.IdentityFrame
import dev.gdk.bumblebee.protocol.TelemetryFrame
import dev.gdk.bumblebee.protocol.decodeFrame
import dev.gdk.bumblebee.nav.MapsNavSource
import dev.gdk.bumblebee.widget.HomeWidget
import dev.gdk.bumblebee.widget.HomeWidgets
import dev.gdk.bumblebee.ride.AbandonAction
import dev.gdk.bumblebee.ride.GpsCapturePolicy
import dev.gdk.bumblebee.ride.RideContinuity
import dev.gdk.bumblebee.share.SharePrompt
import dev.gdk.bumblebee.ui.share.SharePrefs
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
    @Volatile private var pausedAtMs: Long? = null
    @Volatile private var pendingOdoCheck = false
    @Volatile private var deferTelemetry = false
    private var deferredNotify: ByteArray? = null
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
            app.weather.snapshot.collect { snap ->
                if (snap != null && app.capture.activeRideId != null) {
                    withContext(Dispatchers.IO) { app.capture.recordWeatherIfMissing(snap.tempC, snap.code) }
                }
            }
        }
        lifecycleScope.launch {
            app.capture.finishedEvents.collect { id ->
                if (!SharePrefs(app).promptEnabled) return@collect
                val highlight = withContext(Dispatchers.IO) { app.capture.highlightFor(id) } ?: return@collect
                if (!canPostHighlights()) return@collect
                val note = SharePrompt.notification(this@BikeBridgeService, id, highlight)
                val notificationId = (1000 + (id % 1000)).toInt()
                getSystemService(NotificationManager::class.java).notify(notificationId, note)
            }
        }

        lifecycleScope.launch {
            bleClient.state.collect { state ->
                app.connection.value = state
                dev.gdk.bumblebee.dev.DevLog.d("link", state.toString())
                refreshNotification()
                when (state) {
                    is ConnectionState.Ready -> {
                        val fresh = !lastWasReady
                        lastWasReady = true
                        finishRideJob?.cancel()
                        finishRideJob = null
                        deferTelemetry = true
                        lifecycleScope.launch {
                            val pausedAt = withContext(Dispatchers.IO) { app.capture.activePausedAt() }
                            val window = app.prefs.pauseWindowMin
                            val now = System.currentTimeMillis()
                            pendingOdoCheck = pausedAt != null &&
                                RideContinuity.needsFreshOdometer(window, pausedAt, now)
                            if (!pendingOdoCheck) {
                                pausedAtMs = null
                                cancelPauseAlarm()
                                withContext(Dispatchers.IO) {
                                    app.capture.onBikeBack(now, odoKm = null, windowMin = window)
                                }
                                noteWeather(app)
                            }
                            deferTelemetry = false
                            deferredNotify?.let { held ->
                                deferredNotify = null
                                acceptNotify(app, held)
                            }
                            refreshNotification()
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
                        if (lastWasReady || pendingOdoCheck) {
                            val wasReady = lastWasReady
                            lastWasReady = false
                            pendingOdoCheck = false
                            deferTelemetry = false
                            deferredNotify = null
                            armRidePause(app, justDisconnected = wasReady)
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
                if (deferTelemetry) {
                    deferredNotify = bytes
                } else {
                    runCatching { acceptNotify(app, bytes) }
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
            var ticks = 0
            while (isActive) {
                refreshNotification()
                // Once a minute while riding, so the widgets' live km keeps up.
                if (++ticks % 30 == 0 && (application as DashApp).capture.activeRideId != null) {
                    listOf(HomeWidget.START, HomeWidget.BIKE, HomeWidget.TODAY).forEach { HomeWidgets.refresh(this@BikeBridgeService, it) }
                }
                delay(2_000)
            }
        }
        fullyStarted = true
        running = true
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
            ACTION_RECONNECT -> {
                val mac = (application as DashApp).prefs.bikeMac
                if (mac != null) {
                    dev.gdk.bumblebee.dev.DevLog.d("link", "forced reconnect")
                    bleClient.disconnect()
                    lifecycleScope.launch {
                        delay(1_500)
                        bleClient.connect(mac)
                    }
                }
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
                        cancelPauseAlarm()
                        pausedAtMs = null
                        app.capture.startRide(System.currentTimeMillis(), gpsOnly = true)
                        noteWeather(app)
                    }
                    syncGps(app)
                    refreshNotification()
                }
            }
            ACTION_SKIP_AUTO_TODAY -> {
                val dash = application as DashApp
                dash.prefs.skipAutoConnectDate = java.time.LocalDate.now().toString()
                refreshNotification()
            }
            ACTION_END_RIDE -> {
                pendingOdoCheck = false
                deferTelemetry = false
                deferredNotify = null
                pausedAtMs = null
                cancelPauseAlarm()
                finishRideJob?.cancel()
                finishRideJob = null
                lifecycleScope.launch(Dispatchers.IO) {
                    val dash = application as DashApp
                    dash.capture.endRideNow(System.currentTimeMillis())
                    publishGarageWidget(dash)
                    withContext(Dispatchers.Main.immediate) {
                        syncGps(dash)
                        refreshNotification()
                    }
                }
            }
            ACTION_PAUSE_ELAPSED -> {
                val rideId = intent.getLongExtra(EXTRA_RIDE_ID, -1L)
                lifecycleScope.launch {
                    val dash = application as DashApp
                    val ended = if (rideId >= 0) {
                        withContext(Dispatchers.IO) {
                            dash.capture.finishIfPauseElapsed(
                                rideId = rideId,
                                now = System.currentTimeMillis(),
                                windowMin = dash.prefs.pauseWindowMin,
                                bikeReady = bleClient.state.value is ConnectionState.Ready,
                            )
                        }
                    } else {
                        false
                    }
                    if (ended) {
                        pendingOdoCheck = false
                        pausedAtMs = null
                        cancelPauseAlarm()
                        finishRideJob?.cancel()
                        finishRideJob = null
                        publishGarageWidget(dash)
                        syncGps(dash)
                    } else if (dash.capture.activePausedAt() != null) {
                        armRidePause(dash, justDisconnected = false)
                    }
                    refreshNotification()
                }
            }
            ACTION_STOP_GPS_RIDE -> {
                lifecycleScope.launch {
                    val app = application as DashApp
                    if (app.capture.activeRideId != null && !lastWasReady) {
                        cancelPauseAlarm()
                        pausedAtMs = null
                        finishRideJob?.cancel()
                        finishRideJob = null
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
                        val rideId = app.capture.activeRideId
                        if (app.prefs.pauseWindowMin > 0 && rideId != null) {
                            val existing = app.capture.activePausedAt()
                            val at = existing ?: System.currentTimeMillis().also { app.capture.pauseActiveRide(it) }
                            val stored = app.capture.activePausedAt() ?: at
                            val still = app.capture.activeRideId
                            if (still != null) {
                                schedulePauseAlarm(
                                    RideContinuity.pauseDeadlineMs(stored, app.prefs.pauseWindowMin),
                                    still,
                                )
                            }
                        } else {
                            cancelPauseAlarm()
                            runCatching { telemetrySink.onDisconnected(System.currentTimeMillis()) }
                        }
                    }
                }
            }
            running = false
            crashWatch?.stop()
            stopCellListener()
            bleClient.disconnect()
            bleClient.shutdown()
        }
        super.onDestroy()
    }

    private suspend fun noteWeather(app: DashApp) {
        val snap = app.weather.snapshot.value ?: return
        if (app.capture.activeRideId == null) return
        withContext(Dispatchers.IO) { app.capture.recordWeatherIfMissing(snap.tempC, snap.code) }
    }

    private fun canPostHighlights(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private suspend fun acceptNotify(app: DashApp, bytes: ByteArray) {
        dev.gdk.bumblebee.dev.DevLog.frame(bytes)
        val frame = runCatching { decodeFrame(bytes) }.getOrNull()
        if (frame is TelemetryFrame && pendingOdoCheck) {
            pendingOdoCheck = false
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                app.capture.onBikeBack(now, frame.odometerKm, app.prefs.pauseWindowMin)
            }
            noteWeather(app)
            pausedAtMs = null
            cancelPauseAlarm()
            refreshNotification()
        }
        telemetrySink.onNotify(bytes, System.currentTimeMillis())
        if (frame is TelemetryFrame) {
            app.latestTelemetry.value = frame
            ensureCrashWatch(app).onSpeed(frame.speedKmh, System.currentTimeMillis())
        }
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
                if (app.capture.activeRideId != null) {
                    fix.speedMps?.let { mps ->
                        ensureCrashWatch(app).onSpeed((mps * 3.6f).toInt(), System.currentTimeMillis(), fromGps = true)
                    }
                }
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
        lifecycleScope.launch {
            val paused = withContext(Dispatchers.IO) { app.capture.activePausedAt() != null }
            when (
                RideContinuity.abandonAction(
                    hasRide = app.capture.activeRideId != null,
                    gpsOnly = app.capture.isGpsOnly,
                    paused = paused,
                    bikeReady = bleClient.state.value is ConnectionState.Ready,
                )
            ) {
                AbandonAction.NONE -> Unit
                AbandonAction.RESTORE_PAUSE -> armRidePause(app, justDisconnected = false)
                AbandonAction.FINISH_UNPAUSED -> {
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
            }
        }
    }

    /**
     * Marks the ride paused immediately and ends it from a wall-clock alarm.
     * [delay] follows uptime, so it freezes in deep sleep and the window never elapses.
     */
    private fun armRidePause(app: DashApp, justDisconnected: Boolean) {
        finishRideJob?.cancel()
        finishRideJob = null
        if (justDisconnected && app.prefs.pauseWindowMin > 0 && pausedAtMs == null) {
            pausedAtMs = System.currentTimeMillis()
            refreshNotification()
        }
        lifecycleScope.launch {
            val window = app.prefs.pauseWindowMin
            val rideId = app.capture.activeRideId ?: run {
                pausedAtMs = null
                cancelPauseAlarm()
                refreshNotification()
                return@launch
            }
            if (window <= 0) {
                if (justDisconnected) {
                    pausedAtMs = null
                    schedulePauseAlarm(System.currentTimeMillis() + DISCONNECT_GRACE_MS, rideId)
                    refreshNotification()
                }
                return@launch
            }
            val existing = withContext(Dispatchers.IO) { app.capture.activePausedAt() }
            val at = existing ?: System.currentTimeMillis().also { ts ->
                withContext(Dispatchers.IO) { app.capture.pauseActiveRide(ts) }
            }
            val stored = withContext(Dispatchers.IO) { app.capture.activePausedAt() } ?: at
            pausedAtMs = stored
            val deadline = RideContinuity.pauseDeadlineMs(stored, window)
            val trigger = if (justDisconnected) {
                maxOf(deadline, System.currentTimeMillis() + DISCONNECT_GRACE_MS)
            } else {
                deadline
            }
            if (!justDisconnected && trigger <= System.currentTimeMillis()) {
                withContext(Dispatchers.IO) {
                    app.capture.finishIfPauseElapsed(rideId, System.currentTimeMillis(), window, bikeReady = false)
                }
                pausedAtMs = null
                cancelPauseAlarm()
            } else {
                schedulePauseAlarm(trigger, rideId)
            }
            refreshNotification()
        }
    }

    private fun schedulePauseAlarm(triggerAtMs: Long, rideId: Long) {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pi = pauseAlarmPending(rideId)
        try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }
    }

    private fun cancelPauseAlarm() {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            this,
            PAUSE_ALARM_REQUEST,
            Intent(this, PauseWindowReceiver::class.java).setAction(ACTION_PAUSE_ELAPSED),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    private fun pauseAlarmPending(rideId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            PAUSE_ALARM_REQUEST,
            Intent(this, PauseWindowReceiver::class.java)
                .setAction(ACTION_PAUSE_ELAPSED)
                .putExtra(EXTRA_RIDE_ID, rideId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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
            CrashAlert.start(this, verdict.speedBeforeKmh, gps?.lat, gps?.lng)
        }
        crashWatch = watch
        return watch
    }

    private fun refreshNotification() {
        val app = application as DashApp
        val linked = app.connection.value is ConnectionState.Ready
        val speed = app.latestTelemetry.value?.speedKmh
        val bars = app.latestTelemetry.value?.fuelBars
        val pausedFor = pausedAtMs?.let { RideContinuity.pausedMinutes(it, System.currentTimeMillis()) }
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
        private const val PAUSE_ALARM_REQUEST = 11

        @Volatile
        var running: Boolean = false

        const val ACTION_STOP = "dev.gdk.bumblebee.ble.STOP"
        const val ACTION_CONNECT = "dev.gdk.bumblebee.ble.CONNECT"
        const val ACTION_RECONNECT = "dev.gdk.bumblebee.ble.RECONNECT"
        const val ACTION_START_GPS_RIDE = "dev.gdk.bumblebee.ble.START_GPS_RIDE"
        const val ACTION_STOP_GPS_RIDE = "dev.gdk.bumblebee.ble.STOP_GPS_RIDE"
        const val ACTION_END_RIDE = "dev.gdk.bumblebee.ble.END_RIDE"
        const val ACTION_PAUSE_ELAPSED = "dev.gdk.bumblebee.ble.PAUSE_ELAPSED"
        const val ACTION_SKIP_AUTO_TODAY = "dev.gdk.bumblebee.ble.SKIP_AUTO_TODAY"
        const val EXTRA_MAC = "mac"
        const val EXTRA_RIDE_ID = "rideId"

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

        /** Drops the link and dials the saved bike again, for a link that's up but silent. */
        fun reconnect(context: Context) {
            launch(context, Intent(context, BikeBridgeService::class.java).setAction(ACTION_RECONNECT))
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

/**
 * Fires the pause-window deadline while the phone is asleep.
 * A running bridge service refreshes itself. If the process was dead, the ride
 * is closed here and the service is left stopped.
 */
class PauseWindowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BikeBridgeService.ACTION_PAUSE_ELAPSED) return
        val rideId = intent.getLongExtra(BikeBridgeService.EXTRA_RIDE_ID, -1L)
        if (rideId < 0) return
        if (BikeBridgeService.running) {
            val delivered = runCatching {
                context.startService(
                    Intent(context, BikeBridgeService::class.java)
                        .setAction(BikeBridgeService.ACTION_PAUSE_ELAPSED)
                        .putExtra(BikeBridgeService.EXTRA_RIDE_ID, rideId),
                )
            }.isSuccess
            if (delivered) return
        }
        val pending = goAsync()
        Thread {
            try {
                val app = context.applicationContext as? DashApp ?: return@Thread
                runBlocking(Dispatchers.IO) {
                    app.capture.finishIfPauseElapsed(
                        rideId = rideId,
                        now = System.currentTimeMillis(),
                        windowMin = app.prefs.pauseWindowMin,
                        bikeReady = false,
                    )
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
