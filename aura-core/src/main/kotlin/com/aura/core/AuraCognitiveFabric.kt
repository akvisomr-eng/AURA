package com.aura.core

data class AuraKnowledgeItem(
    val id: String,
    val text: String,
    val source: String,
    val importance: Double = 0.5,
    val timestampMs: Long = System.currentTimeMillis(),
    val tags: Set<String> = emptySet()
)

data class AuraKnowledgeHit(val item: AuraKnowledgeItem, val relevance: Double)

interface AuraKnowledgeStore {
    fun upsert(item: AuraKnowledgeItem)
    fun search(query: String, limit: Int = 6): List<AuraKnowledgeHit>
    fun recent(limit: Int = 8): List<AuraKnowledgeItem>
}

class InMemoryAuraKnowledgeStore : AuraKnowledgeStore {
    private val items = ArrayDeque<AuraKnowledgeItem>()

    override fun upsert(item: AuraKnowledgeItem) {
        items.removeAll { it.id == item.id }
        items.addLast(item)
        while (items.size > 500) items.removeFirst()
    }

    override fun search(query: String, limit: Int): List<AuraKnowledgeHit> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return recent(limit).map { AuraKnowledgeHit(it, it.importance) }
        return items.map { item ->
            val haystack = tokenize(item.text + " " + item.tags.joinToString(" "))
            val overlap = tokens.count { it in haystack }.toDouble() / tokens.size
            val exact = if (item.text.contains(query, ignoreCase = true)) 0.35 else 0.0
            AuraKnowledgeHit(item, (overlap * 0.65 + exact + item.importance * 0.15).coerceIn(0.0, 1.0))
        }.filter { it.relevance > 0.05 }
            .sortedByDescending { it.relevance }
            .take(limit)
    }

    override fun recent(limit: Int): List<AuraKnowledgeItem> = items.takeLast(limit).reversed()

    private fun tokenize(value: String): Set<String> =
        value.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }.toSet()
}

data class AuraCognitiveContext(
    val userText: String,
    val intent: AuraIntent,
    val memoryHits: List<AuraKnowledgeHit>,
    val worldEntities: List<WorldEntity>,
    val affectiveState: AffectiveState,
    val privacySensitive: Boolean,
    val complexity: Double,
    val requiredCapabilities: Set<AuraCapability>
)

class AuraKnowledgeFabric(private val store: AuraKnowledgeStore = InMemoryAuraKnowledgeStore()) {
    fun ingestMemory(entry: MemoryEntry, id: String = "memory-${System.nanoTime()}") {
        store.upsert(
            AuraKnowledgeItem(
                id = id,
                text = entry.text,
                source = "conversation:${entry.role}",
                importance = if (entry.role == "user") 0.65 else 0.45,
                tags = setOf(entry.role)
            )
        )
    }

    fun ingestWorldEntity(entity: WorldEntity) {
        store.upsert(
            AuraKnowledgeItem(
                id = "world-${entity.id}",
                text = listOfNotNull(
                    entity.label,
                    entity.type,
                    entity.attributes.entries.joinToString(" ") { "${it.key} ${it.value}" }
                ).joinToString(" "),
                source = "world-model",
                importance = 0.70,
                tags = setOf("world", entity.type)
            )
        )
    }

    fun context(
        userText: String,
        intent: AuraIntent,
        memory: List<MemoryEntry>,
        world: List<WorldEntity>,
        affectiveState: AffectiveState
    ): AuraCognitiveContext {
        memory.takeLast(12).forEachIndexed { index, entry ->
            ingestMemory(entry, "session-$index-${entry.role}")
        }
        world.forEach(::ingestWorldEntity)
        val hits = store.search(userText)
        val privacy = detectPrivacy(userText)
        val complexity = estimateComplexity(userText, hits, world)
        return AuraCognitiveContext(
            userText = userText,
            intent = intent,
            memoryHits = hits,
            worldEntities = world,
            affectiveState = affectiveState,
            privacySensitive = privacy,
            complexity = complexity,
            requiredCapabilities = requiredCapabilities(intent, userText)
        )
    }

    fun recentKnowledge(limit: Int = 8): List<AuraKnowledgeItem> = store.recent(limit)

    private fun detectPrivacy(text: String): Boolean =
        listOf("password", "kata sandi", "otp", "pin", "rekening", "nomor kartu", "rahasia", "private")
            .any { text.contains(it, ignoreCase = true) }

    private fun estimateComplexity(text: String, hits: List<AuraKnowledgeHit>, world: List<WorldEntity>): Double {
        val lengthScore = (text.length / 500.0).coerceIn(0.0, 1.0)
        val memoryScore = (hits.size / 6.0).coerceIn(0.0, 1.0)
        val worldScore = (world.size / 12.0).coerceIn(0.0, 1.0)
        return (lengthScore * 0.35 + memoryScore * 0.35 + worldScore * 0.30).coerceIn(0.0, 1.0)
    }

    private fun requiredCapabilities(intent: AuraIntent, text: String): Set<AuraCapability> {
        val result = linkedSetOf(AuraCapability.TEXT)
        if (intent == AuraIntent.VISION_QUERY || text.contains("kamera", true) || text.contains("lihat", true)) result += AuraCapability.VISION
        if (text.contains("terjemah", true) || text.contains("translate", true)) result += AuraCapability.TRANSLATION
        if (text.contains("kode", true) || text.contains("program", true) || text.contains("coding", true)) result += AuraCapability.CODE
        if (text.contains("suara", true) || text.contains("dengar", true)) result += AuraCapability.AUDIO
        return result
    }
}

data class AuraAgentProfile(
    val id: String,
    val skills: Set<AuraIntent>,
    val capabilities: Set<AuraCapability>,
    val priority: Double = 0.5
)

interface AuraAgentRegistry { fun agents(): List<AuraAgentProfile> }

class DefaultAuraAgentRegistry : AuraAgentRegistry {
    override fun agents(): List<AuraAgentProfile> = listOf(
        AuraAgentProfile("conversation-agent", setOf(AuraIntent.GREETING, AuraIntent.UNKNOWN, AuraIntent.HELP), setOf(AuraCapability.TEXT)),
        AuraAgentProfile("memory-agent", setOf(AuraIntent.MEMORY_QUERY), setOf(AuraCapability.TEXT)),
        AuraAgentProfile("vision-agent", setOf(AuraIntent.VISION_QUERY), setOf(AuraCapability.TEXT, AuraCapability.VISION)),
        AuraAgentProfile("capability-agent", setOf(AuraIntent.CAPABILITY_REQUEST), setOf(AuraCapability.TEXT, AuraCapability.TOOL_USE)),
        AuraAgentProfile("self-agent", setOf(AuraIntent.SELF_QUERY, AuraIntent.STATUS_QUERY), setOf(AuraCapability.TEXT))
    )
}

data class AuraCognitiveDecision(
    val context: AuraCognitiveContext,
    val agent: AuraAgentProfile?,
    val neuralRoute: AuraNeuralRoute,
    val nextAction: String
)

class AuraAgentOrchestrator(
    private val registry: AuraAgentRegistry = DefaultAuraAgentRegistry(),
    private val neuralPolicy: AuraNeuralPolicy = AuraNeuralPolicy()
) {
    fun plan(context: AuraCognitiveContext): AuraCognitiveDecision {
        val agent = registry.agents()
            .filter { context.intent in it.skills }
            .filter { it.capabilities.containsAll(context.requiredCapabilities) }
            .maxByOrNull { it.priority }

        val route = neuralPolicy.choose(
            task = taskFor(context.intent),
            context = AuraContextSignals(
                memoryRelevance = context.memoryHits.maxOfOrNull { it.relevance } ?: 0.0,
                contextComplexity = context.complexity,
                userPriority = if (context.affectiveState != AffectiveState()) 0.7 else 0.5,
                devicePower = 0.5,
                networkQuality = 0.5,
                privacySensitive = context.privacySensitive,
                requiredCapabilities = context.requiredCapabilities
            ),
            estimatedTokens = estimateTokens(context),
            maxCostUsd = if (context.privacySensitive) 0.0 else null
        )

        val action = when {
            agent == null -> "minta klarifikasi atau gunakan fallback conversation"
            route.selected == null -> "jalankan agent secara lokal atau minta klarifikasi"
            else -> "jalankan ${agent.id} melalui ${route.selected.providerId}/${route.selected.modelId}"
        }
        return AuraCognitiveDecision(context, agent, route, action)
    }

    private fun taskFor(intent: AuraIntent): AuraTaskType = when (intent) {
        AuraIntent.VISION_QUERY -> AuraTaskType.VISION
        AuraIntent.MEMORY_QUERY, AuraIntent.CAPABILITY_REQUEST, AuraIntent.UNKNOWN -> AuraTaskType.REASONING
        else -> AuraTaskType.CHAT
    }

    private fun estimateTokens(context: AuraCognitiveContext): Int =
        ((context.userText.length +
            context.memoryHits.sumOf { it.item.text.length } +
            context.worldEntities.sumOf { (it.label ?: "").length }) / 4).coerceIn(1, 32768)
}
