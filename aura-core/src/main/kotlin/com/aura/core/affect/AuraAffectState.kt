package com.aura.core.affect

/**
 * Expressive state for AURA's interface. This models presentation, not literal
 * human feelings or a diagnosis of the user's emotional state.
 */
enum class AuraMood {
    NEUTRAL, JOY, CALM, CURIOUS, FOCUSED, EMPATHETIC, PLAYFULLY_ANNOYED, CONCERN, CELEBRATION, REST
}

enum class AuraMoodSource {
    ASSISTANT_RESPONSE, TASK_STATE, USER_PREFERENCE, IDLE_CYCLE, SECURITY_EVENT
}

data class AuraAffectState(
    val mood: AuraMood = AuraMood.NEUTRAL,
    val intensity: Float = 0.35f,
    val confidence: Float = 1.0f,
    val source: AuraMoodSource = AuraMoodSource.ASSISTANT_RESPONSE,
    val reason: String = "default",
    val expiresAtEpochMillis: Long? = null
) {
    init {
        require(intensity in 0.0f..1.0f) { "intensity must be between 0 and 1" }
        require(confidence in 0.0f..1.0f) { "confidence must be between 0 and 1" }
    }

    fun effectiveMood(nowEpochMillis: Long): AuraMood =
        if (expiresAtEpochMillis != null && nowEpochMillis >= expiresAtEpochMillis) AuraMood.NEUTRAL else mood
}

/**
 * Conservative deterministic policy based on assistant message intent.
 * This deliberately avoids claiming to infer the user's internal emotions.
 */
object AuraAffectPolicy {
    fun fromAssistantText(text: String): AuraAffectState {
        val normalized = text.lowercase()
        val (mood, reason) = when {
            listOf("turut prihatin", "aku paham", "saya mengerti", "pasti berat", "maaf kamu", "saya ikut sedih").any(normalized::contains) ->
                AuraMood.EMPATHETIC to "empathetic_response"
            listOf("berhasil", "selamat", "hebat", "mantap", "keren", "sukses").any(normalized::contains) ->
                AuraMood.CELEBRATION to "positive_outcome"
            listOf("risiko tinggi", "phishing", "ancaman keamanan", "berbahaya", "waspada").any(normalized::contains) ->
                AuraMood.CONCERN to "safety_warning"
            listOf("gagal", "error", "bermasalah", "tidak bisa", "belum berhasil").any(normalized::contains) ->
                AuraMood.FOCUSED to "problem_solving"
            listOf("menarik", "mari kita telusuri", "coba kita cari", "bagaimana jika").any(normalized::contains) ->
                AuraMood.CURIOUS to "exploration"
            else -> AuraMood.NEUTRAL to "neutral_response"
        }
        return AuraAffectState(
            mood = mood,
            intensity = when (mood) {
                AuraMood.CELEBRATION -> 0.8f
                AuraMood.CONCERN -> 0.85f
                AuraMood.EMPATHETIC -> 0.55f
                AuraMood.FOCUSED -> 0.65f
                AuraMood.CURIOUS -> 0.5f
                else -> 0.3f
            },
            confidence = 0.55f,
            source = if (mood == AuraMood.CONCERN) AuraMoodSource.SECURITY_EVENT else AuraMoodSource.ASSISTANT_RESPONSE,
            reason = reason
        )
    }
}
