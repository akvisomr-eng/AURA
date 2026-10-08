package com.aura.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuraGatewayAdaptersTest {
    @Test
    fun nineRouterCatalogExposesCompatibleProvider() {
        val model = AuraNineRouterCatalog().models().single()
        assertEquals("9router", model.providerId)
        assertEquals("auto", model.modelId)
        assertTrue(model.supportsIndonesian)
        assertTrue(model.supportsVision)
    }

    @Test
    fun contextOptimizerKeepsNewestContextWithinLimit() {
        val optimizer = AuraContextOptimizer(maxChars = 20)
        val result = optimizer.optimize(listOf("system" to "system", "user" to "1234567890", "assistant" to "abcdefghij"))
        assertTrue(result.sumOf { it.second.length } <= 20)
        assertEquals("system", result.first().first)
    }
}
