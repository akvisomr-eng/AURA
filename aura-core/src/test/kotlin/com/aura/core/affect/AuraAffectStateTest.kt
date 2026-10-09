package com.aura.core.affect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuraAffectStateTest {
    @Test
    fun expiredMoodReturnsToNeutral() {
        val state = AuraAffectState(
            mood = AuraMood.CELEBRATION,
            expiresAtEpochMillis = 100L
        )
        assertEquals(AuraMood.CELEBRATION, state.effectiveMood(99L))
        assertEquals(AuraMood.NEUTRAL, state.effectiveMood(100L))
    }

    @Test
    fun intensityAndConfidenceAreBounded() {
        assertFailsWith<IllegalArgumentException> { AuraAffectState(intensity = 1.1f) }
        assertFailsWith<IllegalArgumentException> { AuraAffectState(confidence = -0.1f) }
    }

    @Test
    fun securityWarningsUseConcernMood() {
        val state = AuraAffectPolicy.fromAssistantText("Peringatan: risiko tinggi terdeteksi.")
        assertEquals(AuraMood.CONCERN, state.mood)
        assertEquals(AuraMoodSource.SECURITY_EVENT, state.source)
        assertTrue(state.intensity >= 0.8f)
    }

    @Test
    fun successUsesCelebrationMood() {
        assertEquals(AuraMood.CELEBRATION, AuraAffectPolicy.fromAssistantText("Build berhasil!").mood)
    }
}
