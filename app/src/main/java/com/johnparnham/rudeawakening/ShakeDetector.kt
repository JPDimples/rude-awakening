package com.johnparnham.rudeawakening

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Counts vigorous shakes from the accelerometer.
 * A shake is a g-force spike above threshold, debounced so one
 * flail does not count as five shakes.
 */
class ShakeDetector(
    private val onShake: () -> Unit
) : SensorEventListener {

    private var lastShakeMs = 0L
    private var lastAccel = 9.81f
    private var accel = 9.81f

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val current = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        // High-pass: sudden change matters, not steady gravity.
        val delta = current - lastAccel
        accel = accel * 0.9f + delta
        lastAccel = current
        val now = System.currentTimeMillis()
        if (accel > SHAKE_THRESHOLD && now - lastShakeMs > SHAKE_SLOP_MS) {
            lastShakeMs = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val SHAKE_THRESHOLD = 2.7f
        const val SHAKE_SLOP_MS = 300L

        fun register(manager: SensorManager, detector: ShakeDetector) {
            val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensor != null) {
                manager.registerListener(detector, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        fun unregister(manager: SensorManager, detector: ShakeDetector) {
            manager.unregisterListener(detector)
        }
    }
}
