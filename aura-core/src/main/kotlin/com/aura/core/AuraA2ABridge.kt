package com.aura.core

data class AuraAgentDescriptor(
    val id: String,
    val skills: Set<String>,
    val endpoint: String,
    val enabled: Boolean = true
)

data class AuraAgentTask(
    val skill: String,
    val messages: List<Pair<String, String>>,
    val metadata: Map<String, String> = emptyMap()
)

interface AuraAgentBridge {
    fun discover(endpoint: String): AuraAgentDescriptor?
    fun execute(agent: AuraAgentDescriptor, task: AuraAgentTask, callback: (Result<String>) -> Unit)
}

class AuraA2ABridge(private val http: AuraHttpTransport) : AuraAgentBridge {
    override fun discover(endpoint: String): AuraAgentDescriptor? {
        return try {
            val json = http.get(endpoint.trimEnd('/') + "/.well-known/agent.json")
            AuraAgentDescriptor(
                id = json.substringAfter("\"name\":\"").substringBefore('"').ifBlank { endpoint },
                skills = setOf("smart-routing", "provider-discovery", "health-report", "cost-analysis"),
                endpoint = endpoint
            )
        } catch (_: Exception) { null }
    }

    override fun execute(agent: AuraAgentDescriptor, task: AuraAgentTask, callback: (Result<String>) -> Unit) {
        try {
            val body = "{\"jsonrpc\":\"2.0\",\"id\":\"aura-${System.currentTimeMillis()}\",\"method\":\"message/send\",\"params\":{\"skill\":\"${escape(task.skill)}\",\"messages\":[${task.messages.joinToString(",") { "{\"role\":\"${escape(it.first)}\",\"content\":\"${escape(it.second)}\"}" }}]}}"
            callback(Result.success(http.post(agent.endpoint.trimEnd('/') + "/a2a", body)))
        } catch (e: Exception) { callback(Result.failure(e)) }
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}

interface AuraHttpTransport {
    fun get(url: String): String
    fun post(url: String, body: String): String
}