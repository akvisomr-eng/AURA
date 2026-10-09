package com.aura.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechConfigurationTest {
    @Test
    fun wakeWordLengthIsNotDiscardedByAnOverlyHighAudioGate() {
        // 6,400 samples at 16 kHz is 400 ms, short enough for a spoken wake word.
        assertEquals(6_400, ContinuousSpeechListener.MIN_SPEECH_SAMPLES)
    }

    @Test
    fun microphoneCaptureFallsBackToCommonWindowsSampleRates() {
        assertEquals(listOf(16_000f, 44_100f, 48_000f, 22_050f), ContinuousSpeechListener.CAPTURE_SAMPLE_RATES)
    }

    @Test
    fun sessionKeyCanBeSetAndClearedWithoutPersistence() {
        SpeechCredentialStore.clearSessionKey()
        SpeechCredentialStore.setSessionKey("  test-session-key  ")
        assertEquals("test-session-key", SpeechCredentialStore.apiKey())
        SpeechCredentialStore.clearSessionKey()
        // The environment may intentionally provide a key, so only assert no test key remains.
        assertEquals(false, SpeechCredentialStore.apiKey() == "test-session-key")
    }
}
