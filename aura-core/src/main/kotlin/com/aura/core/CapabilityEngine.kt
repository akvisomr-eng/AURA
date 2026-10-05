package com.aura.core

enum class CapabilityLifecycle { DISCOVERED, PLANNED, BUILDING, TESTING, SANDBOXED, AUTHORIZED, ACTIVE, UPDATED, DEPRECATED }

data class CapabilityManifest(
    val id: String,
    val version: String,
    val description: String,
    val lifecycle: CapabilityLifecycle = CapabilityLifecycle.DISCOVERED,
    val permissions: Set<String> = emptySet(),
    val actions: Set<String> = emptySet()
)

data class CapabilityRequest(val goal: String, val target: String? = null)
data class CapabilityPlan(val request: CapabilityRequest, val steps: List<String>, val requiresAuthorization: Boolean)

class CapabilityEngine(private val registry: MutableMap<String, CapabilityManifest> = linkedMapOf()) {
    fun plan(request: CapabilityRequest): CapabilityPlan {
        val sensitive = request.goal.contains("buka", true) || request.goal.contains("kendaraan", true) || request.goal.contains("listrik", true)
        return CapabilityPlan(request, listOf(
            "identifikasi perangkat/lingkungan",
            "temukan interface atau protocol yang sah",
            "bangun adapter capability",
            "uji di sandbox",
            "verifikasi permission dan risiko",
            "daftarkan capability"
        ), sensitive)
    }
    fun register(manifest: CapabilityManifest): CapabilityManifest {
        registry[manifest.id] = manifest.copy(lifecycle = CapabilityLifecycle.ACTIVE)
        return registry.getValue(manifest.id)
    }
    fun find(id: String) = registry[id]
    fun all() = registry.values.toList()
}
