package com.ex2.enginestudio.vehicle

import android.content.Context
import android.util.Log

/**
 * Best-effort vehicle data reader for the Geely EX2 / IHU629G.
 *
 * Uses the Android Car APIs when available at runtime (via reflection)
 * so the project still compiles against a normal Android SDK that does
 * not ship the android.car package. On many EX2 units the Car service
 * exists but property IDs are mapped through a Geely adaptation layer.
 * We try the standard IDs first and fall back gracefully so the rest
 * of the app still works.
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

    private var car: Any? = null
    private var propManager: Any? = null
    @Volatile
    private var lastSnapshot = Snapshot(statusMessage = "Initializing…")

    fun connect() {
        try {
            val carClass = Class.forName("android.car.Car")
            val createCar = carClass.getMethod("createCar", Context::class.java)
            car = createCar.invoke(null, context)

            if (car != null) {
                val getCarManager = carClass.getMethod("getCarManager", String::class.java)
                // Car.PROPERTY_SERVICE == "property"
                propManager = getCarManager.invoke(car, "property")
            }

            if (propManager != null) {
                lastSnapshot = lastSnapshot.copy(
                    connected = true,
                    statusMessage = "Car service connected"
                )
                Log.i(TAG, "CarPropertyManager obtained via reflection")
            } else {
                lastSnapshot = lastSnapshot.copy(
                    connected = false,
                    statusMessage = "Car service unavailable – using fallback"
                )
                Log.w(TAG, "CarPropertyManager is null")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create Car (expected on non-Automotive SDKs)", t)
            lastSnapshot = lastSnapshot.copy(
                connected = false,
                statusMessage = "Car API not available: ${t.javaClass.simpleName}"
            )
        }
    }

    fun disconnect() {
        try {
            car?.javaClass?.getMethod("disconnect")?.invoke(car)
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
            // PERF_VEHICLE_SPEED is typically 291504647 (m/s on standard VHAL)
            val speedProp = getProperty(pm, 291504647)
            if (speedProp != null) {
                val value = (speedProp as? Number)?.toFloat()
                    ?: extractValue(speedProp)
                if (value != null) {
                    speed = (value * 3.6f).coerceAtLeast(0f) // m/s → km/h
                }
            }
        } catch (e: Exception) {
            msg = "Speed prop unavailable"
            Log.d(TAG, "Speed read failed: ${e.message}")
        }

        // Gear – GEAR_SELECTION is typically 289408000
        try {
            val gearProp = getProperty(pm, 289408000)
            if (gearProp != null) {
                val raw = (gearProp as? Number)?.toInt() ?: extractValue(gearProp)?.toInt()
                if (raw != null) {
                    // Common VHAL gear constants
                    gear = when (raw) {
                        2 -> -1          // GEAR_REVERSE
                        1 -> 0           // GEAR_NEUTRAL
                        4 -> 1           // GEAR_DRIVE / 1st
                        else -> raw.coerceIn(0, 8)
                    }
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

    private fun getProperty(pm: Any, propertyId: Int): Any? {
        return try {
            // CarPropertyManager.getProperty(Class, int, int) or getProperty(int, int)
            val methods = pm.javaClass.methods
            val m = methods.firstOrNull {
                it.name == "getProperty" && it.parameterTypes.size == 2
            } ?: methods.firstOrNull {
                it.name == "getProperty" && it.parameterTypes.size == 3
            }
            when {
                m == null -> null
                m.parameterTypes.size == 2 -> m.invoke(pm, propertyId, 0)
                else -> m.invoke(pm, Float::class.java, propertyId, 0)
            }
        } catch (e: Exception) {
            Log.d(TAG, "getProperty($propertyId) failed: ${e.message}")
            null
        }
    }

    private fun extractValue(prop: Any): Float? {
        return try {
            val field = prop.javaClass.getField("value")
            (field.get(prop) as? Number)?.toFloat()
        } catch (_: Exception) {
            try {
                val m = prop.javaClass.getMethod("getValue")
                (m.invoke(prop) as? Number)?.toFloat()
            } catch (_: Exception) {
                null
            }
        }
    }
}
