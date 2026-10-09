package com.aura.desktop

/**
 * Speech credentials are intentionally not persisted by the desktop app.
 * Users may supply a key for the current process through the UI or environment.
 */
object SpeechCredentialStore {
    @Volatile private var sessionKey: String? = null

    fun apiKey(): String? =
        sessionKey?.takeIf { it.isNotBlank() }
            ?: System.getenv("AURA_SPEECH_API_KEY")?.takeIf { it.isNotBlank() }

    fun setSessionKey(value: String) {
        sessionKey = value.trim().takeIf { it.isNotBlank() }
    }

    fun clearSessionKey() {
        sessionKey = null
    }
}
