package com.aura.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuraCognitiveFabricTest {
    @Test fun memoryIsRetrievedIntoCognitiveContext() {
        val fabric = AuraKnowledgeFabric()
        val context = fabric.context(
            userText = "proyek rumah saya",
            intent = AuraIntent.UNKNOWN,
            memory = listOf(MemoryEntry("user", "proyek rumah saya memakai desain minimalis")),
            world = emptyList(),
            affectiveState = AffectiveState()
        )
        assertTrue(context.memoryHits.isNotEmpty())
    }

    @Test fun visionIntentRequiresVisionCapability() {
        val fabric = AuraKnowledgeFabric()
        val context = fabric.context(
            userText = "apa yang kamu lihat dari kamera?",
            intent = AuraIntent.VISION_QUERY,
            memory = emptyList(),
            world = emptyList(),
            affectiveState = AffectiveState()
        )
        assertTrue(AuraCapability.VISION in context.requiredCapabilities)
    }

    @Test fun runtimeExposesCognitiveDecision() {
        val runtime = AuraRuntime()
        val decision = runtime.cognitivePlan("apa yang kamu lihat?")
        assertEquals(AuraIntent.VISION_QUERY, decision.context.intent)
        assertEquals("vision-agent", decision.agent?.id)
    }
}
