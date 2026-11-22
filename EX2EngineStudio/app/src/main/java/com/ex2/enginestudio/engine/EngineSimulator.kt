package com.ex2.enginestudio.engine

import kotlin.math.*

/**
 * Core engine simulation.
 * Takes real (or estimated) vehicle inputs and produces continuous
 * RPM, gear, throttle and load values for the audio engine.
 *
 * This is deliberately simple but responsive for the first prototype.
 * Later versions will add more sophisticated physics and sample layering.
 */
class EngineSimulator(
    private var profile: EngineProfile = EngineProfiles.FERRARI_V8
) {
    // Public state (read by UI and audio)
    var currentRpm: Float = profile.idleRpm
        private set
    var currentGear: Int = 1
        private set
    var throttlePercent: Float = 0f
        private set
    var isRunning: Boolean = false
        private set
    var load: Float = 0f
        private set

    // Internal
    private var targetRpm: Float = profile.idleRpm
    private var vehicleSpeedKmh: Float = 0f
    private var lastUpdateNs: Long = System.nanoTime()

    fun setProfile(newProfile: EngineProfile) {
        profile = newProfile
        if (isRunning) {
            currentRpm = currentRpm.coerceIn(profile.idleRpm, profile.maxRpm)
        } else {
            currentRpm = profile.idleRpm
        }
    }

    fun getProfile(): EngineProfile = profile

    fun start() {
        isRunning = true
        currentRpm = profile.idleRpm
        currentGear = 1
        targetRpm = profile.idleRpm
    }

    fun stop() {
        isRunning = false
        currentRpm = 0f
        throttlePercent = 0f
        load = 0f
    }

    /**
     * Main update. Call this at ~30-60 Hz from the service.
     *
     * @param speedKmh   real vehicle speed (or 0 if unavailable)
     * @param accelPedal 0.0–1.0 if available, otherwise -1 (unknown)
     * @param brakeActive true if brake is pressed
     * @param inReverse  true if gear is Reverse
     */
    fun update(
        speedKmh: Float,
        accelPedal: Float = -1f,
        brakeActive: Boolean = false,
        inReverse: Boolean = false
    ) {
        if (!isRunning) return

        val now = System.nanoTime()
        val dt = ((now - lastUpdateNs) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.1f)
        lastUpdateNs = now

        vehicleSpeedKmh = speedKmh.coerceAtLeast(0f)

        // Estimate throttle if real pedal is not available
        throttlePercent = if (accelPedal in 0f..1f) {
            accelPedal
        } else {
            // Simple speed-based estimation for prototype
            // (later we can improve with acceleration derivative)
            when {
                vehicleSpeedKmh < 1f -> 0.05f
                else -> (vehicleSpeedKmh / 140f).coerceIn(0.08f, 0.85f)
            }
        }

        if (brakeActive) {
            throttlePercent *= 0.3f
        }

        // Simple automatic gear logic based on speed + RPM
        updateGear(inReverse)

        // Target RPM from throttle + gear + speed
        val gearRatio = if (currentGear in 1..profile.gearRatios.size) {
            profile.gearRatios[currentGear - 1]
        } else {
            1.0f
        }

        // Base from speed through the gear
        val speedBasedRpm = (vehicleSpeedKmh * 80f * gearRatio).coerceIn(profile.idleRpm, profile.maxRpm)

        // Blend with throttle demand
        targetRpm = if (vehicleSpeedKmh < 2f) {
            // Standing / creep → idle + throttle
            profile.idleRpm + throttlePercent * (profile.redlineRpm - profile.idleRpm) * 0.7f
        } else {
            // Moving → blend speed-derived RPM with throttle
            val throttleTarget = profile.idleRpm + throttlePercent * (profile.maxRpm - profile.idleRpm)
            speedBasedRpm * 0.55f + throttleTarget * 0.45f
        }

        targetRpm = targetRpm.coerceIn(profile.idleRpm * 0.9f, profile.maxRpm)

        // Smooth approach to target
        val response = if (targetRpm > currentRpm) {
            profile.throttleResponse * 3.5f
        } else {
            // Deceleration / engine braking
            (2.0f + if (brakeActive) 2.5f else 0f)
        }

        currentRpm += (targetRpm - currentRpm) * (1f - exp(-response * dt))
        currentRpm = currentRpm.coerceIn(0f, profile.maxRpm)

        // Load estimate for audio (0 = free revving, 1 = heavy load)
        load = (throttlePercent * 0.7f + (currentRpm / profile.maxRpm) * 0.3f).coerceIn(0f, 1f)
    }

    private fun updateGear(inReverse: Boolean) {
        if (inReverse) {
            currentGear = -1
            return
        }
        if (vehicleSpeedKmh < 1f) {
            currentGear = 1
            return
        }

        // Very simple up/down shift points
        when {
            currentRpm > profile.redlineRpm * 0.92f && currentGear < profile.gearRatios.size -> {
                currentGear++
                // Drop RPM a bit on upshift for realism
                currentRpm *= 0.72f
            }
            currentRpm < profile.idleRpm * 1.35f && currentGear > 1 && vehicleSpeedKmh > 5f -> {
                currentGear--
                currentRpm *= 1.25f
            }
        }
        currentGear = currentGear.coerceIn(1, profile.gearRatios.size)
    }

    fun getNormalizedRpm(): Float {
        return if (profile.maxRpm > 0) currentRpm / profile.maxRpm else 0f
    }
}
