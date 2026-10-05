package com.aura.core

data class AuraRuntimeStatus(
    val version: String = "1.9.0",
    val policyBoundaryActive: Boolean = true,
    val memoryReady: Boolean = true,
    val agentRouterReady: Boolean = true
)

data class AuraTurn(
    val userText: String,
    val responseText: String,
    val intent: AuraIntent
)

enum class AuraIntent {
    GREETING,
    VISION_QUERY,
    STATUS_QUERY,
    MEMORY_QUERY,
    HELP,
    UNKNOWN
}

data class MemoryEntry(
    val role: String,
    val text: String
)

class SessionMemory(private val maxEntries: Int = 40) {
    private val entries = ArrayDeque<MemoryEntry>()

    fun add(role: String, text: String) {
        entries.addLast(MemoryEntry(role, text))
        while (entries.size > maxEntries) entries.removeFirst()
    }

    fun recent(limit: Int = 8): List<MemoryEntry> =
        entries.takeLast(limit)

    fun clear() = entries.clear()
}

class IntentRouter {
    fun route(text: String): AuraIntent {
        val value = text.lowercase()
        return when {
            value.contains("halo") || value.contains("hai") ||
                value.contains("selamat pagi") || value.contains("selamat malam") ->
                AuraIntent.GREETING
            value.contains("apa yang kamu lihat") ||
                value.contains("apa yang kau lihat") ||
                value.contains("lihat apa") ||
                value.contains("di depan saya") ->
                AuraIntent.VISION_QUERY
            value.contains("status") || value.contains("kondisi aura") ->
                AuraIntent.STATUS_QUERY
            value.contains("ingat") || value.contains("memory") ||
                value.contains("percakapan kita") ->
                AuraIntent.MEMORY_QUERY
            value.contains("bantuan") || value.contains("help") ||
                value.contains("apa yang bisa kamu lakukan") ->
                AuraIntent.HELP
            else -> AuraIntent.UNKNOWN
        }
    }
}

class AuraRuntime(
    private val memory: SessionMemory = SessionMemory(),
    private val router: IntentRouter = IntentRouter()
) {
    fun status(): AuraRuntimeStatus = AuraRuntimeStatus()

    fun rememberUser(text: String) {
        memory.add("user", text)
    }

    fun rememberAssistant(text: String) {
        memory.add("assistant", text)
    }

    fun recentMemory(limit: Int = 8): List<MemoryEntry> =
        memory.recent(limit)

    fun respond(userText: String, visionContext: String): AuraTurn {
        val intent = router.route(userText)
        val response = when (intent) {
            AuraIntent.GREETING ->
                "Halo. Saya AURA. Saya siap membantu Anda."
            AuraIntent.VISION_QUERY ->
                if (visionContext.isBlank()) "Saya belum mendapatkan gambaran yang cukup jelas. Coba arahkan kamera ke objek yang ingin Anda tanyakan." else visionContext
            AuraIntent.STATUS_QUERY ->
                "Saya aktif. Penglihatan, suara, ingatan percakapan, dan pengarah tugas saya siap digunakan."
            AuraIntent.MEMORY_QUERY -> {
                val remembered = memory.recent(4)
                    .filter { it.role == "user" }
                    .map { it.text }
                if (remembered.isEmpty()) {
                    "Belum ada percakapan yang saya ingat dalam sesi ini."
                } else {
                    "Sejauh ini, saya ingat Anda mengatakan: " + remembered.joinToString(" • ")
                }
            }
            AuraIntent.HELP ->
                "Saya bisa melihat melalui kamera, mendengarkan Anda, menjawab dengan suara, dan mengingat percakapan selama sesi ini."
            AuraIntent.UNKNOWN ->
                "Baik, saya mengerti. Anda mengatakan: $userText. Saya akan menghubungkan permintaan ini ke kemampuan yang sesuai."
        }

        memory.add("user", userText)
        memory.add("assistant", response)
        return AuraTurn(userText, response, intent)
    }
}
