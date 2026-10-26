package dev.gdk.bumblebee.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.dev.DevLog
import dev.gdk.bumblebee.ui.theme.Accent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the ride screen shows about a video export. */
sealed interface VideoExportState {
    data object Idle : VideoExportState
    data class Working(val rideId: Long, val step: String, val fraction: Float?) : VideoExportState
    data class Done(val rideId: Long, val uri: Uri) : VideoExportState
    data class Failed(val rideId: Long, val reason: String) : VideoExportState
}

/**
 * Renders a ride video in the background with a progress notification, so it carries on if the
 * rider leaves the app. One export at a time; a new request replaces nothing while one runs.
 */
class VideoExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                job?.cancel()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val rideId = intent.getLongExtra(EXTRA_RIDE, -1)
                val shape = intent.getStringExtra(EXTRA_SHAPE)?.let { s -> VideoShape.entries.firstOrNull { it.name == s } } ?: VideoShape.VERTICAL
                val length = intent.getIntExtra(EXTRA_LENGTH, 0).takeIf { it > 0 }
                if (!goForeground(progress(null, "Preparing"))) {
                    _state.value = VideoExportState.Failed(rideId, "Android didn't allow the background render. Try again with the app open.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (job?.isActive == true || rideId < 0) return START_NOT_STICKY
                job = scope.launch { export(rideId, VideoSettings(shape, length)) }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun export(rideId: Long, settings: VideoSettings) {
        val app = application as DashApp
        val out = File(cacheDir, "share/ride-video-$rideId.mp4")
        try {
            // Keeps the CPU going with the screen off; the render still works without it.
            wake = runCatching {
                getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bumblebee:video")?.apply { acquire(30 * 60_000L) }
            }.getOrNull()
            step(rideId, "Fetching terrain", null)
            val tiles = DemTiles(app)
            val plan = withContext(Dispatchers.IO) {
                val input = RideVideoSource.load(app, rideId) ?: error("This ride has no GPS track to fly over.")
                RideVideoPlan.build(input, settings, tiles::tile) ?: error("The ride is too short for a video once the start and end are hidden.")
            }
            DevLog.d("video", "plan ${"%.1f".format(plan.routeKm)} km, ${plan.totalS.toInt()} s, ${plan.timeline.moments.size} moments, flat=${plan.flat}, exag=${"%.2f".format(plan.exaggeration)}")
            if (plan.flat) step(rideId, "No terrain data (offline?): rendering flat", null)
            val look = VideoLook(plan.sun, Accent.color.toArgb())
            val started = System.currentTimeMillis()
            RideVideoExporter(app).render(plan, look, RideVideoSource.fonts(app), Accent.color.toArgb(), out) { p ->
                when (p) {
                    is VideoProgress.Rendering -> scope.launch { step(rideId, "Rendering", p.fraction) }
                    else -> {}
                }
            }
            DevLog.d("video", "rendered ${plan.frameCount} frames in ${(System.currentTimeMillis() - started) / 1000} s, ${out.length() / 1024} KB")
            step(rideId, "Saving", null)
            val name = "Bumblebee-ride-$rideId-${System.currentTimeMillis() / 1000}.mp4"
            val saved = withContext(Dispatchers.IO) { RideVideoExporter(app).saveToGallery(out, name) }
            val uri = saved ?: FileProvider.getUriForFile(this, "$packageName.share", out)
            tiles.trim()
            _state.value = VideoExportState.Done(rideId, uri)
            notifyDone(uri)
        } catch (e: CancellationException) {
            _state.value = VideoExportState.Idle
            cancelNotification()
        } catch (e: Throwable) {
            DevLog.d("video", "failed: ${e.javaClass.simpleName}: ${e.message}")
            val reason = e.message ?: e.javaClass.simpleName
            _state.value = VideoExportState.Failed(rideId, reason)
            notifyFailed(reason)
        } finally {
            wake?.let { if (it.isHeld) it.release() }
            wake = null
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private var lastShown: Pair<String, Int>? = null

    private fun step(rideId: Long, label: String, fraction: Float?) {
        _state.value = VideoExportState.Working(rideId, label, fraction)
        // Android drops notifications posted too often; update only when the whole percent moves.
        val shown = label to ((fraction ?: -0.01f) * 100).toInt()
        if (shown == lastShown) return
        lastShown = shown
        manager().notify(NOTIFICATION_ID, progress(fraction, label))
    }

    private fun goForeground(n: Notification): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }.isSuccess

    private fun manager() = getSystemService(NotificationManager::class.java)

    private fun channel() {
        manager().createNotificationChannel(NotificationChannel(CHANNEL, "Ride videos", NotificationManager.IMPORTANCE_LOW))
    }

    private fun progress(fraction: Float?, label: String): Notification {
        channel()
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, VideoExportService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Making ride video")
            .setContentText(if (fraction == null) label else "$label · ${(fraction * 100).toInt()}%")
            .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun notifyDone(uri: Uri) {
        channel()
        val share = Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Share ride video",
        )
        val open = PendingIntent.getActivity(this, 2, share, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager().notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Ride video ready")
                .setContentText(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Saved to Movies/Bumblebee. Tap to share." else "Tap to share.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun notifyFailed(reason: String) {
        channel()
        manager().notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Ride video failed")
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun cancelNotification() = manager().cancel(NOTIFICATION_ID)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ride_video"
        private const val NOTIFICATION_ID = 7_401
        private const val ACTION_START = "dev.gdk.bumblebee.video.START"
        private const val ACTION_CANCEL = "dev.gdk.bumblebee.video.CANCEL"
        private const val EXTRA_RIDE = "ride"
        private const val EXTRA_SHAPE = "shape"
        private const val EXTRA_LENGTH = "length"

        private val _state = MutableStateFlow<VideoExportState>(VideoExportState.Idle)
        val state: StateFlow<VideoExportState> = _state.asStateFlow()

        /** The screen has shown the result; go back to idle. */
        fun consume() {
            if (_state.value !is VideoExportState.Working) _state.value = VideoExportState.Idle
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, VideoExportService::class.java).setAction(ACTION_CANCEL))
        }

        fun start(context: Context, rideId: Long, shape: VideoShape = VideoShape.VERTICAL, lengthS: Int? = null) {
            if (_state.value is VideoExportState.Working) return
            _state.value = VideoExportState.Working(rideId, "Preparing", null)
            val intent = Intent(context, VideoExportService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RIDE, rideId)
                .putExtra(EXTRA_SHAPE, shape.name)
                .putExtra(EXTRA_LENGTH, lengthS ?: 0)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
