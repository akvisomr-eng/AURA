package com.aura.core

data class AuraSelfModel(
    val capabilities: Set<String> = setOf("conversation", "session-memory", "vision-context", "indonesian-voice"),
    val knownLimitations: Set<String> = setOf(
        "No autonomous vehicle control",
        "No unauthorized identity lookup",
        "Sensor measurements require an available instrument"
    ),
    val confidence: Float = 0.75f,
    val currentTask: String? = null
) {
    fun can(capability: String) = capability in capabilities
    fun describeCapability(capability: String) =
        if (can(capability)) "Kemampuan $capability tersedia." else "Kemampuan $capability belum tersedia."
}
