package com.aura.core

/**
 * Device-agnostic voice presentation layer.
 *
 * Keeps spoken phrasing separate from the UI response so a future neural/cloud
 * Indonesian TTS provider can replace Android TTS without changing cognition.
 */
data class SpokenTurn(
    val text: String,
    val rate: Float = 0.92f,
    val pitch: Float = 1.02f
)

class IndonesianVoiceRuntime {
    fun prepare(text: String): SpokenTurn {
        val spoken = text
            .replace("AURA dua titik nol aktif.", "AURA satu titik sembilan aktif.")
            .replace("AURA satu titik delapan aktif.", "AURA satu titik delapan aktif.")
            .replace(Regex("""\s*[•·]\s*"""), ", ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Small conversational pauses improve intelligibility without requiring
        // a specific TTS engine. The provider remains replaceable.
        val natural = spoken
            .replace(Regex("""([.!?])\s+"""), "$1 ")
            .replace("Saya AURA.", "Saya AURA.")
        return SpokenTurn(natural)
    }
}
