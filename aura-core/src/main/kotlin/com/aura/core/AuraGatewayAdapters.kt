package com.aura.core

data class AuraModelRequest(val model: String, val messages: List<Pair<String, String>>, val maxTokens: Int? = null)

data class AuraModelResponse(val text: String, val model: String)

interface AuraModelGateway {
    val providerId: String
    fun complete(request: AuraModelRequest): AuraModelResponse
    fun models(): List<String> = emptyList()
}

class AuraContextOptimizer(private val maxChars: Int = 24000) {
    fun optimize(messages: List<Pair<String, String>>): List<Pair<String, String>> {
        if (messages.sumOf { it.second.length } <= maxChars) return messages
        if (messages.isEmpty()) return messages
        val first = messages.first()
        val out = ArrayDeque<Pair<String, String>>()
        out.add(first)
        var used = first.second.length
        for (m in messages.drop(1).asReversed()) {
            val remaining = maxChars - used
            if (remaining <= 0) break
            val kept = m.second.take(remaining)
            out.addFirst(m.first to kept)
            used += kept.length
        }
        return out.toList()
    }
}
