package com.aura.core

data class WorldEntity(val id: String, val type: String, val label: String? = null, val attributes: Map<String, String> = emptyMap())

class AuraWorldModel {
    private val entities = linkedMapOf<String, WorldEntity>()
    fun upsert(entity: WorldEntity) { entities[entity.id] = entity }
    fun find(id: String) = entities[id]
    fun snapshot() = entities.values.toList()
}

data class SpatialObservation(val source: String, val description: String, val confidence: Float)
enum class EvidenceType { MEASURED, OBSERVED, INFERRED, HYPOTHESIS }
data class PhysicalObservation(val parameter: String, val value: String, val evidence: EvidenceType, val confidence: Float)
