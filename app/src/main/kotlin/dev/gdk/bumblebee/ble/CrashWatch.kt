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

/** Samples the accelerometer only while a ride has recently been at speed. */
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
    private var fired = false

    fun onSpeed(kmh: Int, now: Long) {
        lastSpeed = kmh.toDouble()
        speeds.addLast(now to lastSpeed)
        while (speeds.isNotEmpty() && now - speeds.first().first > 15_000L) speeds.removeFirst()
        if (CrashDetector.armed(speeds.toList(), now)) armedUntil = now + 60_000L
        if (now < armedUntil && !fired) start() else if (now >= armedUntil) stop()
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
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val g = sqrt(x * x + y * y + z * z) / 9.80665
        samples.addLast(CrashSample(now, g, lastSpeed))
        while (samples.isNotEmpty() && now - samples.first().ts > 45_000L) samples.removeFirst()
        val sensitivity = when (app.prefs.crashSensitivity) {
            "LOW" -> CrashSensitivity.LOW
            "HIGH" -> CrashSensitivity.HIGH
            else -> CrashSensitivity.NORMAL
        }
        val verdict = CrashDetector.evaluate(samples.toList(), sensitivity)
        if (verdict.kind == CrashVerdictKind.CRASH && !fired) {
            fired = true
            stop()
            onCrash(verdict)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
