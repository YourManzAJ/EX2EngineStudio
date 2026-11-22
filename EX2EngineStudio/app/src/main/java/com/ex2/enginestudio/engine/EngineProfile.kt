package com.ex2.enginestudio.engine

/**
 * Defines the character of a simulated petrol engine.
 * All values are tunable. Profiles are inspired by real engines
 * but use original synthesis parameters (no copyrighted samples).
 */
data class EngineProfile(
    val id: String,
    val displayName: String,
    val idleRpm: Float = 850f,
    val maxRpm: Float = 8500f,
    val redlineRpm: Float = 8000f,
    val cylinders: Int = 8,
    val layout: EngineLayout = EngineLayout.V8,
    /** How aggressively RPM rises with throttle (higher = snappier) */
    val throttleResponse: Float = 1.0f,
    /** Natural RPM decay rate when throttle is lifted */
    val decayRate: Float = 0.92f,
    /** Simulated gear ratios (final drive included conceptually) */
    val gearRatios: FloatArray = floatArrayOf(3.5f, 2.2f, 1.6f, 1.2f, 1.0f, 0.85f),
    /** Base volume character 0..1 */
    val baseIntensity: Float = 0.75f,
    /** Exhaust "bark" factor */
    val exhaustCharacter: Float = 0.6f
) {
    enum class EngineLayout { INLINE4, INLINE6, V6, V8, V10, V12, FLAT6 }
}

object EngineProfiles {
    val FERRARI_V8 = EngineProfile(
        id = "ferrari_v8",
        displayName = "Ferrari-inspired V8",
        idleRpm = 900f,
        maxRpm = 8800f,
        redlineRpm = 8500f,
        cylinders = 8,
        layout = EngineProfile.EngineLayout.V8,
        throttleResponse = 1.25f,
        decayRate = 0.90f,
        gearRatios = floatArrayOf(3.6f, 2.3f, 1.7f, 1.3f, 1.05f, 0.88f),
        exhaustCharacter = 0.85f
    )

    val LAMBORGHINI_V10 = EngineProfile(
        id = "lambo_v10",
        displayName = "Lamborghini-inspired V10",
        idleRpm = 950f,
        maxRpm = 8700f,
        redlineRpm = 8400f,
        cylinders = 10,
        layout = EngineProfile.EngineLayout.V10,
        throttleResponse = 1.35f,
        decayRate = 0.88f,
        gearRatios = floatArrayOf(3.4f, 2.15f, 1.55f, 1.18f, 0.95f, 0.80f),
        exhaustCharacter = 0.95f
    )

    val PORSCHE_FLAT6 = EngineProfile(
        id = "porsche_flat6",
        displayName = "Porsche-inspired Flat-6",
        idleRpm = 800f,
        maxRpm = 7800f,
        redlineRpm = 7400f,
        cylinders = 6,
        layout = EngineProfile.EngineLayout.FLAT6,
        throttleResponse = 1.15f,
        decayRate = 0.93f,
        gearRatios = floatArrayOf(3.7f, 2.4f, 1.75f, 1.35f, 1.1f, 0.92f),
        exhaustCharacter = 0.70f
    )

    val AMG_V8 = EngineProfile(
        id = "amg_v8",
        displayName = "AMG-inspired V8",
        idleRpm = 750f,
        maxRpm = 7200f,
        redlineRpm = 6800f,
        cylinders = 8,
        layout = EngineProfile.EngineLayout.V8,
        throttleResponse = 1.10f,
        decayRate = 0.94f,
        gearRatios = floatArrayOf(3.8f, 2.5f, 1.8f, 1.4f, 1.1f, 0.90f),
        exhaustCharacter = 0.80f
    )

    val ALL = listOf(FERRARI_V8, LAMBORGHINI_V10, PORSCHE_FLAT6, AMG_V8)
}
