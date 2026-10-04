package com.example.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt

/**
 * Utility functions and constants for FEAT-36 Shake Gesture detection.
 */
object ShakeDetectorUtils {
    const val SHAKE_THRESHOLD_G_FORCE = 2.5f
    const val SHAKE_COOLDOWN_MS = 1200L
    const val GRAVITY_EARTH = SensorManager.GRAVITY_EARTH

    const val ACTION_OFF = "OFF"
    const val ACTION_TRANSACTION = "TRANSACTION"
    const val ACTION_STEALTH = "STEALTH"

    fun calculateGForce(x: Float, y: Float, z: Float): Float {
        val gX = x / GRAVITY_EARTH
        val gY = y / GRAVITY_EARTH
        val gZ = z / GRAVITY_EARTH
        return sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()
    }

    fun isShake(gForce: Float): Boolean = gForce > SHAKE_THRESHOLD_G_FORCE
}

/**
 * Lifecycle-aware Shake Gesture Detector Composable (FEAT-36).
 * Registers SensorEventListener strictly on ON_RESUME and unregisters on ON_PAUSE or onDispose.
 * Does not listen or drain battery while app is backgrounded.
 */
@Composable
fun rememberShakeDetector(
    enabled: Boolean = true,
    onShake: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnShake = rememberUpdatedState(onShake)

    DisposableEffect(lifecycleOwner, context, enabled) {
        if (!enabled) {
            return@DisposableEffect onDispose {}
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || accelerometer == null) {
            return@DisposableEffect onDispose {}
        }

        var lastShakeTime = 0L
        var isRegistered = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    val gForce = ShakeDetectorUtils.calculateGForce(
                        event.values[0],
                        event.values[1],
                        event.values[2]
                    )

                    if (ShakeDetectorUtils.isShake(gForce)) {
                        val now = System.currentTimeMillis()
                        if (now - lastShakeTime > ShakeDetectorUtils.SHAKE_COOLDOWN_MS) {
                            lastShakeTime = now
                            currentOnShake.value()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        fun register() {
            if (!isRegistered) {
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
                isRegistered = true
            }
        }

        fun unregister() {
            if (isRegistered) {
                sensorManager.unregisterListener(listener)
                isRegistered = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> {}
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }
}
