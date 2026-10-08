package com.aura.core

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

enum class AuraTaskType { CHAT, CODING, VISION, TRANSLATION, FAST, REASONING, UNKNOWN }
enum class AuraRoutingMode { BALANCED, QUALITY, FAST, CHEAP, RELIABLE, OFFLINE }

data class AuraModelProfile(
    val providerId: String,
    val modelId: String,
    val quality: Double = 0.7,
    val latencyMs: Long = 1200,
    val costPer1kTokensUsd: Double = 0.0,
    val quotaRemaining: Double = 1.0,
    val health: Double = 1.0,
    val taskFit: Double = 0.7,
    val supportsVision: Boolean = false,
    val supportsIndonesian: Boolean = true
)

data class AuraRoutingRequest(
    val task: AuraTaskType,
    val mode: AuraRoutingMode = AuraRoutingMode.BALANCED,
    val estimatedInputTokens: Int = 0,
    val maxCostUsd: Double? = null,
    val requiresVision: Boolean = false,
    val requiresIndonesian: Boolean = true
)

data class AuraRoutingDecision(
    val selected: AuraModelProfile?,
    val candidates: List<AuraModelProfile>,
    val reason: String,
    val fallbackChain: List<AuraModelProfile>
)

sealed class AuraGatewayResult {
    data class Success(val text: String, val decision: AuraRoutingDecision) : AuraGatewayResult()
    data class Failure(val message: String, val decision: AuraRoutingDecision) : AuraGatewayResult()
}

interface AuraModelCatalog { fun models(): List<AuraModelProfile> }

class DefaultAuraModelCatalog : AuraModelCatalog {
    override fun models(): List<AuraModelProfile> = listOf(
        AuraModelProfile(
            providerId = "aura-local", modelId = "local", quality = 0.55, latencyMs = 350,
            costPer1kTokensUsd = 0.0, quotaRemaining = 1.0, health = 1.0, taskFit = 0.70,
            supportsIndonesian = true
        )
    )
}

class AuraAutoRouter(private val catalog: AuraModelCatalog = DefaultAuraModelCatalog()) {
    @Volatile private var lastKnownGood: String? = null

    fun route(request: AuraRoutingRequest): AuraRoutingDecision {
        val pool = catalog.models()
            .filter { it.health > 0.15 }
            .filter { !request.requiresVision || it.supportsVision }
            .filter { !request.requiresIndonesian || it.supportsIndonesian }
            .filter { request.maxCostUsd == null || it.costPer1kTokensUsd * max(1, request.estimatedInputTokens) / 1000.0 <= request.maxCostUsd }
        if (pool.isEmpty()) return AuraRoutingDecision(null, emptyList(), "Tidak ada model yang memenuhi policy AURA.", emptyList())
        val ranked = pool.sortedByDescending { score(it, request) }
        val sticky = lastKnownGood?.let { key -> ranked.firstOrNull { "${it.providerId}/${it.modelId}" == key } }
        val selected = sticky ?: ranked.first()
        val chain = listOf(selected) + ranked.filter { it != selected }
        return AuraRoutingDecision(selected, ranked, reasonFor(request.mode, selected), chain)
    }

    fun markSuccess(model: AuraModelProfile) { lastKnownGood = "${model.providerId}/${model.modelId}" }

    private fun score(model: AuraModelProfile, request: AuraRoutingRequest): Double {
        val latency = 1.0 - min(1.0, model.latencyMs / 5000.0)
        val cost = 1.0 - min(1.0, model.costPer1kTokensUsd / 0.05)
        val quota = model.quotaRemaining
        val taskFit = when (request.task) {
            AuraTaskType.CODING -> model.taskFit
            AuraTaskType.VISION -> if (model.supportsVision) model.taskFit else 0.0
            AuraTaskType.TRANSLATION -> if (model.supportsIndonesian) model.taskFit else 0.0
            AuraTaskType.FAST -> latency
            AuraTaskType.REASONING -> model.quality
            else -> model.taskFit
        }
        val weights = when (request.mode) {
            AuraRoutingMode.QUALITY -> doubleArrayOf(.10, .10, .05, .10, .35, .30)
            AuraRoutingMode.FAST -> doubleArrayOf(.08, .35, .03, .14, .20, .20)
            AuraRoutingMode.CHEAP -> doubleArrayOf(.10, .08, .40, .20, .12, .10)
            AuraRoutingMode.RELIABLE -> doubleArrayOf(.15, .10, .05, .35, .15, .20)
            AuraRoutingMode.OFFLINE -> doubleArrayOf(.25, .08, .05, .40, .07, .15)
            AuraRoutingMode.BALANCED -> doubleArrayOf(.16, .15, .14, .18, .20, .17)
        }
        return model.health * weights[0] + latency * weights[1] + cost * weights[2] + quota * weights[3] + model.quality * weights[4] + taskFit * weights[5]
    }

    private fun reasonFor(mode: AuraRoutingMode, model: AuraModelProfile): String =
        "Mode ${mode.name.lowercase()} memilih ${model.providerId}/${model.modelId} berdasarkan health=${"%.2f".format(model.health)}, latency=${model.latencyMs}ms, quota=${"%.0f".format(model.quotaRemaining * 100)}%."
}

class AuraOmniRouteCatalog : AuraModelCatalog {
    override fun models(): List<AuraModelProfile> = listOf(
        AuraModelProfile(
            providerId = "omniroute", modelId = "auto", quality = 0.90, latencyMs = 900,
            costPer1kTokensUsd = 0.0, quotaRemaining = 1.0, health = 1.0, taskFit = 0.92,
            supportsVision = true, supportsIndonesian = true
        )
    )
}
data class AuraGatewayConfig(val baseUrl: String = "", val apiKey: String = "", val timeoutMs: Int = 20_000) {
    val enabled: Boolean get() = baseUrl.isNotBlank()
}

class AuraNeuralGateway(
    private val config: AuraGatewayConfig = AuraGatewayConfig(),
    private val catalog: AuraModelCatalog = DefaultAuraModelCatalog()
) {
    private val router = AuraAutoRouter(catalog)
    private val executor = Executors.newCachedThreadPool()

    fun execute(request: AuraRoutingRequest, messages: List<Pair<String, String>>, callback: (AuraGatewayResult) -> Unit) {
        val decision = router.route(request)
        val selected = decision.selected
        if (!config.enabled || selected == null || selected.providerId == "aura-local") {
            callback(AuraGatewayResult.Failure("Gateway eksternal belum dikonfigurasi.", decision))
            return
        }
        executor.execute {
            var lastError = "Tidak ada model yang berhasil."
            for (candidate in decision.fallbackChain) {
                try {
                    val result = callOpenAiCompatible(candidate, messages)
                    if (result.isNotBlank()) {
                        router.markSuccess(candidate)
                        callback(AuraGatewayResult.Success(result, decision.copy(selected = candidate)))
                        return@execute
                    }
                    lastError = "Respons kosong dari ${candidate.modelId}."
                } catch (e: Exception) { lastError = e.message ?: "Kesalahan gateway." }
            }
            callback(AuraGatewayResult.Failure(lastError, decision))
        }
    }

    private fun callOpenAiCompatible(model: AuraModelProfile, messages: List<Pair<String, String>>): String {
        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = config.timeoutMs; readTimeout = config.timeoutMs; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (config.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        val payloadMessages = messages.joinToString(",") { """{"role":"${jsonEscape(it.first)}","content":"${jsonEscape(it.second)}"}""" }
        val body = """{"model":"${jsonEscape(model.modelId)}","messages":[$payloadMessages],"stream":false}"""
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        connection.disconnect()
        if (status !in 200..299) throw IllegalStateException("HTTP $status: ${response.take(300)}")
        return extractAssistantContent(response)
    }

    private fun extractAssistantContent(json: String): String {
        val marker = "\"content\":"
        val start = json.indexOf(marker)
        if (start < 0) return ""
        var i = start + marker.length
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '"') return ""
        i++
        val out = StringBuilder(); var escaped = false
        while (i < json.length) {
            val c = json[i++]
            if (escaped) {
                out.append(when (c) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; '\\' -> '\\'; '"' -> '"'; else -> c })
                escaped = false
            } else if (c == '\\') escaped = true else if (c == '"') break else out.append(c)
        }
        return out.toString()
    }

    private fun jsonEscape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}