package dev.gdk.bumblebee.ble

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ride.CrashDetector
import dev.gdk.bumblebee.ride.CrashSample
import dev.gdk.bumblebee.ride.CrashSensitivity
import dev.gdk.bumblebee.ride.CrashVerdict
import dev.gdk.bumblebee.ride.CrashVerdictKind
import kotlin.math.sqrt

/**
 * Samples the accelerometer only while a ride has recently been at speed. Speed comes from the
 * bike, or from phone GPS when the bike has gone quiet (a crash can drop the link).
 */
class CrashWatch(
    context: Context,
    private val onCrash: (CrashVerdict) -> Unit,
) : SensorEventListener {
    private val app = context.applicationContext as DashApp
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accel = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val samples = ArrayDeque<CrashSample>()
    private val speeds = ArrayDeque<Pair<Long, Double>>()
    private var listening = false
    private var armedUntil = 0L
    private var lastSpeed = 0.0
    private var lastSpeedAt = 0L
    private var lastBikeAt = 0L
    private var lastEval = 0L
    private var firedAt: Long? = null

    /** A speed from the bike, or from GPS with [fromGps]. GPS is ignored while the bike is talking. */
    fun onSpeed(kmh: Int, now: Long, fromGps: Boolean = false) {
        if (!app.prefs.crashDetection) {
            stop()
            return
        }
        if (fromGps && now - lastBikeAt < STALE_MS) return
        if (!fromGps) lastBikeAt = now
        lastSpeed = kmh.toDouble()
        lastSpeedAt = now
        speeds.addLast(now to lastSpeed)
        while (speeds.isNotEmpty() && now - speeds.first().first > 15_000L) speeds.removeFirst()
        val armedNow = CrashDetector.armed(speeds.toList(), now)
        if (armedNow) armedUntil = now + ARM_HOLD_MS
        // After an alarm, wait until the rider is clearly riding again before watching anew.
        val fired = firedAt
        if (fired != null && armedNow && now - fired > REARM_MS) {
            firedAt = null
            samples.clear()
        }
        if (now < armedUntil && firedAt == null) start() else if (now >= armedUntil) stop()
    }

    fun stop() {
        if (!listening) return
        sensors?.unregisterListener(this)
        listening = false
    }

    private fun start() {
        if (listening || accel == null) return
        sensors?.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        listening = true
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()
        // No speed for a while means the link dropped and GPS has nothing: count it as stopped.
        if (now - lastSpeedAt > STALE_MS) lastSpeed = 0.0
        if (now > armedUntil + ARM_HOLD_MS) {
            stop()
            return
        }
        val x = event.values[0].toDouble() / G
        val y = event.values[1].toDouble() / G
        val z = event.values[2].toDouble() / G
        samples.addLast(CrashSample(now, sqrt(x * x + y * y + z * z), lastSpeed, x, y, z))
        while (samples.isNotEmpty() && now - samples.first().ts > BUFFER_MS) samples.removeFirst()
        if (now - lastEval < EVAL_EVERY_MS) return
        lastEval = now
        val sensitivity = when (app.prefs.crashSensitivity) {
            "LOW" -> CrashSensitivity.LOW
            "HIGH" -> CrashSensitivity.HIGH
            else -> CrashSensitivity.NORMAL
        }
        val verdict = CrashDetector.evaluate(samples.toList(), sensitivity)
        if (verdict.kind == CrashVerdictKind.CRASH && firedAt == null) {
            firedAt = now
            samples.clear()
            stop()
            onCrash(verdict)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val G = 9.80665
        const val STALE_MS = 3_000L
        const val ARM_HOLD_MS = 60_000L
        const val REARM_MS = 120_000L
        const val BUFFER_MS = 60_000L
        const val EVAL_EVERY_MS = 500L
    }
}
