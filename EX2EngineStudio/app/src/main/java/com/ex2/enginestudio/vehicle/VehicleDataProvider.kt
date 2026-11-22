package com.ex2.enginestudio.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * Best-effort vehicle data reader for the Geely EX2 / IHU629G.
 *
 * Uses the Android Car APIs when available. On many EX2 units the
 * Car service exists but property IDs are mapped through a Geely
 * adaptation layer. We try the standard IDs first and fall back
 * gracefully so the rest of the app still works.
 */
class VehicleDataProvider(private val context: Context) {

    companion object {
        private const val TAG = "VehicleData"
    }

    data class Snapshot(
        val speedKmh: Float = 0f,
        val accelPedal: Float = -1f,   // -1 = unavailable
        val brakeActive: Boolean = false,
        val gear: Int = 0,             // 0=unknown, 1..N, -1=R
        val connected: Boolean = false,
        val statusMessage: String = "Not connected"
    )

    private var car: Car? = null
    private var propManager: CarPropertyManager? = null
    @Volatile
    private var lastSnapshot = Snapshot(statusMessage = "Initializing…")

    fun connect() {
        try {
            car = Car.createCar(context)
            propManager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            if (propManager != null) {
                lastSnapshot = lastSnapshot.copy(
                    connected = true,
                    statusMessage = "Car service connected"
                )
                Log.i(TAG, "CarPropertyManager obtained")
            } else {
                lastSnapshot = lastSnapshot.copy(
                    connected = false,
                    statusMessage = "Car service unavailable – using fallback"
                )
                Log.w(TAG, "CarPropertyManager is null")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create Car", t)
            lastSnapshot = lastSnapshot.copy(
                connected = false,
                statusMessage = "Car API error: ${t.javaClass.simpleName}"
            )
        }
    }

    fun disconnect() {
        try {
            car?.disconnect()
        } catch (_: Exception) {}
        car = null
        propManager = null
    }

    fun getSnapshot(): Snapshot {
        val pm = propManager ?: return lastSnapshot.copy(
            connected = false,
            statusMessage = "No CarPropertyManager – speed fallback only"
        )

        var speed = 0f
        var pedal = -1f
        var brake = false
        var gear = 0
        var msg = "OK"

        try {
            // PERF_VEHICLE_SPEED is in m/s on standard VHAL
            val speedProp = pm.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
            if (speedProp != null) {
                speed = (speedProp.value * 3.6f).coerceAtLeast(0f) // m/s → km/h
            }
        } catch (e: Exception) {
            msg = "Speed prop unavailable"
            Log.d(TAG, "Speed read failed: ${e.message}")
        }

        // Accelerator – many units do not expose this cleanly
        try {
            val pedalProp = pm.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY, 0)
            // Some firmwares reuse or map differently; leave as -1 if unsure
        } catch (_: Exception) {}

        // Gear
        try {
            val gearProp = pm.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)
            if (gearProp != null) {
                gear = when (gearProp.value) {
                    VehiclePropertyIds.GEAR_REVERSE -> -1
                    VehiclePropertyIds.GEAR_NEUTRAL -> 0
                    VehiclePropertyIds.GEAR_DRIVE -> 1
                    // Higher gears may appear as 2,3… depending on mapping
                    else -> gearProp.value.coerceIn(0, 8)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Gear read failed: ${e.message}")
        }

        lastSnapshot = Snapshot(
            speedKmh = speed,
            accelPedal = pedal,
            brakeActive = brake,
            gear = gear,
            connected = true,
            statusMessage = msg
        )
        return lastSnapshot
    }
}
