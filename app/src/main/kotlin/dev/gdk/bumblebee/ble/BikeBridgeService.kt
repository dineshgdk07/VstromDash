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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.MainActivity
import dev.gdk.bumblebee.ble.android.BleClient
import dev.gdk.bumblebee.ble.android.TelemetrySink
import dev.gdk.bumblebee.protocol.IdentityFrame
import dev.gdk.bumblebee.protocol.TelemetryFrame
import dev.gdk.bumblebee.protocol.decodeFrame
import dev.gdk.bumblebee.nav.MapsNavSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BikeBridgeService : LifecycleService() {

    private lateinit var bleClient: BleClient
    private lateinit var frameWriter: FrameWriter
    private lateinit var telemetrySink: TelemetrySink
    private lateinit var heartbeatLoop: HeartbeatLoop
    private var drainJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lastWasReady = false
    private var fullyStarted = false

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
            signalBarsProvider = { 3 },
            smsPendingProvider = { false },
            callPendingProvider = { false },
            weatherProvider = { 0x01 to 0x00 },
        )

        lifecycleScope.launch {
            bleClient.state.collect { state ->
                app.connection.value = state
                val now = System.currentTimeMillis()
                when (state) {
                    is ConnectionState.Ready -> {
                        val fresh = !lastWasReady
                        lastWasReady = true
                        frameWriter.enqueue(
                            FrameWriter.Entry(
                                FrameWriter.Priority.URGENT,
                                IdentityFrame(name = app.prefs.riderName, isFresh = fresh).encode(),
                                note = "identity",
                            ),
                        )
                        telemetrySink.onReady(now)
                        startHeartbeat()
                    }
                    is ConnectionState.Disconnected, is ConnectionState.Failed, is ConnectionState.Idle -> {
                        if (lastWasReady) {
                            telemetrySink.onDisconnected(now)
                            lastWasReady = false
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
                telemetrySink.onNotify(bytes, System.currentTimeMillis())
                val frame = runCatching { decodeFrame(bytes) }.getOrNull()
                if (frame is TelemetryFrame) app.latestTelemetry.value = frame
            }
        }

        drainJob = lifecycleScope.launch {
            while (isActive) {
                val entry = frameWriter.take()
                bleClient.write(entry.frame)
            }
        }

        lifecycleScope.launch {
            MapsNavSource.frame.collect { nav ->
                if (nav != null) {
                    frameWriter.enqueue(FrameWriter.Entry(FrameWriter.Priority.NAV, nav.encode(), "nav"))
                }
            }
        }
        lifecycleScope.launch {
            while (isActive) {
                delay(2_000)
                val nav = MapsNavSource.frame.value ?: continue
                frameWriter.forceNav(nav.encode())
            }
        }

        app.prefs.bikeMac?.let { bleClient.connect(it) }
        fullyStarted = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            if (fullyStarted) bleClient.disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (fullyStarted) {
            drainJob?.cancel()
            heartbeatJob?.cancel()
            bleClient.disconnect()
            bleClient.shutdown()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
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
        return pct to charging
    }

    private fun startInForeground(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Bike link", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val launch = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("bumblebee-dash")
            .setContentText("Bike link running")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
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
        const val ACTION_STOP = "dev.gdk.bumblebee.ble.STOP"

        fun start(context: Context) {
            val intent = Intent(context, BikeBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BikeBridgeService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
