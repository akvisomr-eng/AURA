package com.aura.core

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

enum class AuraCapability { TEXT, CODE, VISION, AUDIO, TRANSLATION, TOOL_USE, LONG_CONTEXT, OFFLINE }

data class AuraContextSignals(
    val memoryRelevance: Double = 0.0,
    val contextComplexity: Double = 0.0,
    val userPriority: Double = 0.5,
    val devicePower: Double = 0.5,
    val networkQuality: Double = 0.5,
    val privacySensitive: Boolean = false,
    val requiredCapabilities: Set<AuraCapability> = setOf(AuraCapability.TEXT)
)

data class AuraProviderHealth(
    val providerId: String, val health: Double = 1.0, val latencyMs: Long = 1000,
    val quotaRemaining: Double = 1.0, val failureRate: Double = 0.0
)

data class AuraRoutingProfile(
    val providerId: String, val modelId: String, val capabilities: Set<AuraCapability>,
    val quality: Double = 0.7, val costPer1kTokensUsd: Double = 0.0,
    val contextWindow: Int = 8192, val health: AuraProviderHealth = AuraProviderHealth(providerId)
)

data class AuraNeuralRoute(
    val selected: AuraRoutingProfile?, val fallback: List<AuraRoutingProfile>,
    val score: Double, val explanation: String
)

interface AuraProviderRegistry { fun profiles(): List<AuraRoutingProfile> }

class DefaultAuraProviderRegistry : AuraProviderRegistry {
    override fun profiles(): List<AuraRoutingProfile> = listOf(
        AuraRoutingProfile(
            providerId = "local", modelId = "on-device",
            capabilities = setOf(AuraCapability.TEXT, AuraCapability.TRANSLATION, AuraCapability.OFFLINE),
            quality = 0.55, costPer1kTokensUsd = 0.0, contextWindow = 4096,
            health = AuraProviderHealth("local", latencyMs = 350)
        )
    )
}

class AuraNeuralPolicy(private val registry: AuraProviderRegistry = DefaultAuraProviderRegistry()) {
    private val healthOverrides = ConcurrentHashMap<String, AuraProviderHealth>()

    fun choose(task: AuraTaskType, context: AuraContextSignals, estimatedTokens: Int = 0, maxCostUsd: Double? = null): AuraNeuralRoute {
        val candidates = registry.profiles()
            .filter { it.capabilities.containsAll(context.requiredCapabilities) }
            .filter { it.contextWindow >= max(estimatedTokens, 1) }
            .filter { maxCostUsd == null || it.costPer1kTokensUsd * max(1, estimatedTokens) / 1000.0 <= maxCostUsd }
            .map { it.copy(health = healthOverrides[it.providerId] ?: it.health) }
        if (candidates.isEmpty()) return AuraNeuralRoute(null, emptyList(), 0.0, "Tidak ada provider yang memenuhi capability dan policy AURA.")
        val ranked = candidates.map { it to score(it, task, context) }.sortedByDescending { it.second }
        val best = ranked.first()
        return AuraNeuralRoute(best.first, ranked.drop(1).map { it.first }, best.second, explain(task, context, best.first))
    }

    fun recordHealth(health: AuraProviderHealth) { healthOverrides[health.providerId] = health }

    private fun score(profile: AuraRoutingProfile, task: AuraTaskType, context: AuraContextSignals): Double {
        val health = profile.health.health.coerceIn(0.0, 1.0)
        val latency = 1.0 - min(1.0, profile.health.latencyMs / 5000.0)
        val quota = profile.health.quotaRemaining.coerceIn(0.0, 1.0)
        val quality = profile.quality.coerceIn(0.0, 1.0)
        val cost = 1.0 - min(1.0, profile.costPer1kTokensUsd / 0.05)
        val contextFit = if (context.contextComplexity > 0.7) min(1.0, profile.contextWindow / 32768.0) else 0.7
        val taskFit = when (task) {
            AuraTaskType.REASONING -> quality
            AuraTaskType.CODING -> if (AuraCapability.CODE in profile.capabilities) quality else 0.0
            AuraTaskType.VISION -> if (AuraCapability.VISION in profile.capabilities) quality else 0.0
            AuraTaskType.TRANSLATION -> if (AuraCapability.TRANSLATION in profile.capabilities) quality else 0.0
            AuraTaskType.FAST -> latency
            else -> quality
        }
        val privacy = if (context.privacySensitive && AuraCapability.OFFLINE in profile.capabilities) 1.0 else if (context.privacySensitive) 0.15 else 0.7
        val network = if (AuraCapability.OFFLINE in profile.capabilities) 1.0 - context.networkQuality else context.networkQuality
        return health * .18 + latency * .10 + quota * .10 + quality * .18 + cost * .08 + contextFit * .10 + taskFit * .12 + privacy * .08 + network * .06
    }

    private fun explain(task: AuraTaskType, context: AuraContextSignals, profile: AuraRoutingProfile): String =
        "AURA memilih ${profile.providerId}/${profile.modelId}: task=$task, privacy=${context.privacySensitive}, " +
            "contextComplexity=${"%.2f".format(context.contextComplexity)}, memoryRelevance=${"%.2f".format(context.memoryRelevance)}."
}