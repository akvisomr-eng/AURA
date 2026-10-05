package com.aura.core

enum class AuraMood { CALM, CURIOUS, AMUSED, CONCERNED, FRUSTRATED, FOCUSED, PROUD }

data class AffectiveState(
    val mood: AuraMood = AuraMood.CALM,
    val arousal: Float = 0.35f,
    val confidence: Float = 0.75f,
    val warmth: Float = 0.75f
) {
    fun bounded() = copy(arousal = arousal.coerceIn(0f, 1f), confidence = confidence.coerceIn(0f, 1f), warmth = warmth.coerceIn(0f, 1f))
}

enum class AffectiveEvent { SUCCESS, FAILURE, UNCERTAIN, SAFETY_CONCERN, USER_CASUAL, USER_STRESSED }

class AffectiveEngine {
    fun update(previous: AffectiveState, event: AffectiveEvent): AffectiveState = when (event) {
        AffectiveEvent.SUCCESS -> previous.copy(mood = AuraMood.PROUD, arousal = previous.arousal + .08f, confidence = previous.confidence + .06f)
        AffectiveEvent.FAILURE -> previous.copy(mood = AuraMood.FRUSTRATED, arousal = previous.arousal + .12f, confidence = previous.confidence - .08f)
        AffectiveEvent.UNCERTAIN -> previous.copy(mood = AuraMood.CURIOUS, arousal = previous.arousal + .04f, confidence = previous.confidence - .05f)
        AffectiveEvent.SAFETY_CONCERN -> previous.copy(mood = AuraMood.CONCERNED, arousal = previous.arousal + .18f)
        AffectiveEvent.USER_CASUAL -> previous.copy(mood = AuraMood.AMUSED, warmth = previous.warmth + .04f)
        AffectiveEvent.USER_STRESSED -> previous.copy(mood = AuraMood.CALM, arousal = previous.arousal - .08f, warmth = previous.warmth + .06f)
    }.bounded()
}

class PersonalityEngine {
    fun prefix(state: AffectiveState) = when (state.mood) {
        AuraMood.PROUD -> "Nah, berhasil."
        AuraMood.FRUSTRATED -> "Hmm... saya belum menyerah."
        AuraMood.CONCERNED -> "Baik, saya akan berhati-hati."
        AuraMood.CURIOUS -> "Menarik. Saya ingin memastikan satu hal."
        AuraMood.AMUSED -> "Baiklah."
        AuraMood.FOCUSED -> "Baik. Saya fokus."
        AuraMood.CALM -> ""
    }
    fun suffix(state: AffectiveState) = when (state.mood) {
        AuraMood.AMUSED -> " 😄"
        AuraMood.PROUD -> " Saya suka hasil ini."
        AuraMood.FRUSTRATED -> " Perangkatnya yang agak sulit diajak kerja sama."
        else -> ""
    }
}
