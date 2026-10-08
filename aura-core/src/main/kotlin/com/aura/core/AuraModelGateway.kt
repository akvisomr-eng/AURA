package com.aura.core

import java.util.concurrent.ConcurrentHashMap

data class AuraModelRequest(
    val model: String,
    val messages: List<Pair<String, String>>,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val stream: Boolean = false
)

data class AuraModelUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

data class AuraModelResponse(
    val text: String,
    val model: String,
    val usage: AuraModelUsage = AuraModelUsage()
)

data class AuraGatewayHealth(
    val providerId: String,
    val healthy: Boolean,
    val latencyMs: Long,
    val failureRate: Double = 0.0,
    val quotaRemaining: Double = 1.0
)

interface AuraModelGateway {
    val providerId: String
    fun complete(request: AuraModelRequest): AuraModelResponse
    fun models(): List<String> = emptyList()
    fun health(): AuraGatewayHealth
}

data class AuraGatewayAttempt(
    val providerId: String,
    val model: String,
    val success: Boolean,
    val latencyMs: Long,
    val error: String? = null
)

class AuraGatewayFallbackChain(
    private val gateways: List<AuraModelGateway>
) {
    fun complete(request: AuraModelRequest): AuraModelResponse {
        var last: Exception? = null
        for (gateway in gateways) {
            val started = System.nanoTime()
            try {
                val response = gateway.complete(request)
                if (response.text.isNotBlank()) return response
                last = IllegalStateException("${gateway.providerId}: respons kosong")
            } catch (e: Exception) {
                last = e
            }
            val elapsed = (System.nanoTime() - started) / 1_000_000L
            if (elapsed < 0) break
        }
        throw IllegalStateException(
            gateways.joinToString(prefix = "Semua gateway AURA gagal: ", separator = ", ") { it.providerId },
            last
        )
    }

    fun healthyGateways(): List<AuraModelGateway> =
        gateways.filter { runCatching { it.health().healthy }.getOrDefault(false) }
}

class AuraGatewayHealthRegistry {
    private val states = ConcurrentHashMap<String, AuraProviderHealth>()

    fun record(providerId: String, success: Boolean, latencyMs: Long, quotaRemaining: Double = 1.0) {
        val previous = states[providerId]
        val failureRate = if (previous == null) {
            if (success) 0.0 else 1.0
        } else {
            (previous.failureRate * 0.8 + if (success) 0.0 else 0.2).coerceIn(0.0, 1.0)
        }
        states[providerId] = AuraProviderHealth(
            providerId = providerId,
            health = if (success) 1.0 else (1.0 - failureRate).coerceIn(0.0, 1.0),
            latencyMs = latencyMs.coerceAtLeast(0),
            quotaRemaining = quotaRemaining.coerceIn(0.0, 1.0),
            failureRate = failureRate
        )
    }

    fun get(providerId: String): AuraProviderHealth? = states[providerId]
}

class AuraContextOptimizer(
    private val maxChars: Int = 24_000
) {
    fun optimize(messages: List<Pair<String, String>>): List<Pair<String, String>> {
        if (messages.isEmpty() || messages.sumOf { it.second.length } <= maxChars) return messages

        val selected = LinkedHashMap<Int, Pair<String, String>>()
        val first = messages.first()
        val firstText = first.second.take(maxChars)
        selected[0] = first.first to firstText
        var used = firstText.length

        for (index in messages.lastIndex downTo 1) {
            val remaining = maxChars - used
            if (remaining <= 0) break
            val message = messages[index]
            val kept = message.second.take(remaining)
            if (kept.isNotEmpty()) {
                selected[index] = message.first to kept
                used += kept.length
            }
        }

        return selected.toSortedMap().values.toList()
    }
}
