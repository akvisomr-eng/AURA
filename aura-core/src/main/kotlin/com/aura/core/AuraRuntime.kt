package com.aura.core

data class AuraRuntimeStatus(
    val version: String = "2.0.0",
    val policyBoundaryActive: Boolean = true,
    val memoryReady: Boolean = true,
    val agentRouterReady: Boolean = true,
    val worldModelReady: Boolean = true,
    val affectiveEngineReady: Boolean = true,
    val selfModelReady: Boolean = true,
    val capabilityEngineReady: Boolean = true,
    val deviceFabricReady: Boolean = true
)

data class AuraTurn(
    val userText: String,
    val responseText: String,
    val intent: AuraIntent,
    val affectiveState: AffectiveState = AffectiveState()
)

enum class AuraIntent {
    GREETING, VISION_QUERY, STATUS_QUERY, MEMORY_QUERY, HELP,
    CAPABILITY_REQUEST, SELF_QUERY, UNKNOWN
}

data class MemoryEntry(val role: String, val text: String)

class SessionMemory(private val maxEntries: Int = 40) {
    private val entries = ArrayDeque<MemoryEntry>()
    fun add(role: String, text: String) {
        entries.addLast(MemoryEntry(role, text))
        while (entries.size > maxEntries) entries.removeFirst()
    }
    fun recent(limit: Int = 8): List<MemoryEntry> = entries.takeLast(limit)
    fun clear() = entries.clear()
}

class IntentRouter {
    fun route(text: String): AuraIntent {
        val value = text.lowercase()
        return when {
            value.contains("halo") || value.contains("hai") ||
                value.contains("selamat pagi") || value.contains("selamat malam") -> AuraIntent.GREETING
            value.contains("apa yang kamu lihat") || value.contains("apa yang kau lihat") ||
                value.contains("lihat apa") || value.contains("di depan saya") -> AuraIntent.VISION_QUERY
            value.contains("status") || value.contains("kondisi aura") -> AuraIntent.STATUS_QUERY
            value.contains("ingat") || value.contains("memory") || value.contains("percakapan kita") -> AuraIntent.MEMORY_QUERY
            value.contains("bantuan") || value.contains("help") || value.contains("apa yang bisa kamu lakukan") -> AuraIntent.HELP
            value.contains("jadilah") || value.contains("buatkan kemampuan") ||
                value.contains("hubungkan perangkat") || value.contains("jadikan kamu") -> AuraIntent.CAPABILITY_REQUEST
            value.contains("siapa kamu") || value.contains("apa yang bisa kamu") ||
                value.contains("apa kemampuanmu") -> AuraIntent.SELF_QUERY
            else -> AuraIntent.UNKNOWN
        }
    }
}

class AuraRuntime(
    private val memory: SessionMemory = SessionMemory(),
    private val router: IntentRouter = IntentRouter(),
    private val affectiveEngine: AffectiveEngine = AffectiveEngine(),
    private val personalityEngine: PersonalityEngine = PersonalityEngine(),
    private val capabilityEngine: CapabilityEngine = CapabilityEngine(),
    private val selfModel: AuraSelfModel = AuraSelfModel(),
    private val worldModel: AuraWorldModel = AuraWorldModel()
) {
    private var affectiveState = AffectiveState()

    fun status(): AuraRuntimeStatus = AuraRuntimeStatus()

    fun rememberUser(text: String) { memory.add("user", text) }
    fun rememberAssistant(text: String) { memory.add("assistant", text) }
    fun recentMemory(limit: Int = 8): List<MemoryEntry> = memory.recent(limit)
    fun currentAffectiveState(): AffectiveState = affectiveState
    fun selfModel(): AuraSelfModel = selfModel
    fun worldModel(): AuraWorldModel = worldModel
    fun capabilities(): List<CapabilityManifest> = capabilityEngine.all()

    fun respond(userText: String, visionContext: String): AuraTurn {
        val intent = router.route(userText)
        val event = when (intent) {
            AuraIntent.CAPABILITY_REQUEST -> AffectiveEvent.CURIOUS
            AuraIntent.VISION_QUERY -> if (visionContext.isBlank()) AffectiveEvent.UNCERTAIN else AffectiveEvent.SUCCESS
            else -> if (userText.length > 180) AffectiveEvent.USER_STRESSED else AffectiveEvent.USER_CASUAL
        }
        affectiveState = affectiveEngine.update(affectiveState, event)

        val response = when (intent) {
            AuraIntent.GREETING ->
                "Halo. Saya AURA. Saya siap membantu Anda."
            AuraIntent.VISION_QUERY ->
                if (visionContext.isBlank()) "Saya belum mendapatkan gambaran yang cukup jelas. Coba arahkan kamera ke objek yang ingin Anda tanyakan."
                else visionContext
            AuraIntent.STATUS_QUERY ->
                "Saya aktif. Penglihatan, suara, ingatan percakapan, world model, kepribadian, dan pengarah kemampuan saya siap digunakan."
            AuraIntent.MEMORY_QUERY -> {
                val remembered = memory.recent(4).filter { it.role == "user" }.map { it.text }
                if (remembered.isEmpty()) "Belum ada percakapan yang saya ingat dalam sesi ini."
                else "Sejauh ini, saya ingat Anda mengatakan: " + remembered.joinToString(" • ")
            }
            AuraIntent.HELP ->
                "Saya bisa melihat melalui kamera, mendengarkan Anda, menjawab dengan suara, mengingat percakapan, memahami konteks, dan menyiapkan kemampuan baru melalui sistem capability."
            AuraIntent.CAPABILITY_REQUEST -> {
                val plan = capabilityEngine.plan(CapabilityRequest(userText))
                "Saya bisa merencanakan kemampuan baru untuk permintaan itu. Langkah saya: " +
                    plan.steps.joinToString(", ") +
                    if (plan.requiresAuthorization) ". Karena menyangkut tindakan sensitif, otorisasi tambahan diperlukan." else "."
            }
            AuraIntent.SELF_QUERY ->
                "Saya AURA. Saat ini saya memiliki percakapan, memory sesi, konteks visual, suara Indonesia, world model, affective state, self-model, dan capability engine. Saya juga tahu batas kemampuan saya."
            AuraIntent.UNKNOWN ->
                "Baik, saya mengerti. Anda mengatakan: $userText. Saya akan menghubungkan permintaan ini ke kemampuan yang sesuai."
        }

        val prefix = personalityEngine.prefix(affectiveState)
        val suffix = personalityEngine.suffix(affectiveState)
        val spoken = listOf(prefix, response, suffix).filter { it.isNotBlank() }.joinToString(" ").trim()

        memory.add("user", userText)
        memory.add("assistant", spoken)
        return AuraTurn(userText, spoken, intent, affectiveState)
    }
}
