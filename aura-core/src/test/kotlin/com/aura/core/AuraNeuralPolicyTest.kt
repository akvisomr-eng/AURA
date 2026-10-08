package com.aura.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AuraNeuralPolicyTest {
    private class Registry : AuraProviderRegistry {
        override fun profiles() = listOf(
            AuraRoutingProfile("cloud", "reasoner", setOf(AuraCapability.TEXT, AuraCapability.CODE, AuraCapability.LONG_CONTEXT), quality = 0.98, costPer1kTokensUsd = 0.02, contextWindow = 32768),
            AuraRoutingProfile("local", "on-device", setOf(AuraCapability.TEXT, AuraCapability.TRANSLATION, AuraCapability.OFFLINE), quality = 0.55, contextWindow = 4096)
        )
    }

    @Test fun privacyPrefersOfflineWhenAvailable() {
        val route = AuraNeuralPolicy(Registry()).choose(AuraTaskType.CHAT, AuraContextSignals(privacySensitive = true, networkQuality = 0.1))
        assertEquals("local", route.selected?.providerId)
    }

    @Test fun longContextPrefersLargeContextProfile() {
        val route = AuraNeuralPolicy(Registry()).choose(AuraTaskType.REASONING, AuraContextSignals(contextComplexity = 0.95), estimatedTokens = 12000)
        assertEquals("cloud", route.selected?.providerId)
    }
}