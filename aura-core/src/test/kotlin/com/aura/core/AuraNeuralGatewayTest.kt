package com.aura.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuraNeuralGatewayTest {
    private class Catalog : AuraModelCatalog {
        override fun models() = listOf(
            AuraModelProfile("fast", "fast-model", latencyMs = 200, quality = 0.65, taskFit = 0.70),
            AuraModelProfile("quality", "quality-model", latencyMs = 1800, quality = 0.98, taskFit = 0.95)
        )
    }

    @Test
    fun qualityModePrefersQualityModel() {
        val decision = AuraAutoRouter(Catalog()).route(AuraRoutingRequest(AuraTaskType.REASONING, AuraRoutingMode.QUALITY))
        assertEquals("quality", decision.selected?.providerId)
    }

    @Test
    fun fastModePrefersFastModel() {
        val decision = AuraAutoRouter(Catalog()).route(AuraRoutingRequest(AuraTaskType.FAST, AuraRoutingMode.FAST))
        assertEquals("fast", decision.selected?.providerId)
    }

    @Test
    fun budgetFiltersExpensiveCandidates() {
        val catalog = object : AuraModelCatalog {
            override fun models() = listOf(
                AuraModelProfile("free", "free", costPer1kTokensUsd = 0.0),
                AuraModelProfile("paid", "paid", costPer1kTokensUsd = 0.10)
            )
        }
        val decision = AuraAutoRouter(catalog).route(AuraRoutingRequest(AuraTaskType.CHAT, maxCostUsd = 0.01, estimatedInputTokens = 1000))
        assertEquals("free", decision.selected?.providerId)
        assertTrue(decision.candidates.none { it.providerId == "paid" })
    }
}