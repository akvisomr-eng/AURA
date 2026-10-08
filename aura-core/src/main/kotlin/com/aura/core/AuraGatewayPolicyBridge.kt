package com.aura.core

class AuraGatewayPolicyBridge(
    private val gateways: Map<String, AuraNineRouterAdapter>,
    private val policy: AuraNeuralPolicy = AuraNeuralPolicy(
        AuraGatewayProviderRegistry()
    )
) {
    fun execute(
        task: AuraTaskType,
        messages: List<Pair<String, String>>,
        context: AuraContextSignals,
        estimatedTokens: Int = 0,
        maxCostUsd: Double? = null,
        callback: (AuraGatewayResult) -> Unit
    ) {
        val route = policy.choose(task, context, estimatedTokens, maxCostUsd)
        val selected = route.selected
        if (selected == null) {
            callback(
                AuraGatewayResult.Failure(
                    route.explanation,
                    AuraRoutingDecision(null, emptyList(), route.explanation, emptyList())
                )
            )
            return
        }

        val ordered = listOf(selected) + route.fallback
        executeNext(ordered, 0, task, messages, context, callback, route.explanation)
    }

    private fun executeNext(
        ordered: List<AuraRoutingProfile>,
        index: Int,
        task: AuraTaskType,
        messages: List<Pair<String, String>>,
        context: AuraContextSignals,
        callback: (AuraGatewayResult) -> Unit,
        policyExplanation: String
    ) {
        if (index >= ordered.size) {
            val decision = AuraRoutingDecision(
                selected = null,
                candidates = ordered.map(::toModelProfile),
                reason = policyExplanation,
                fallbackChain = emptyList()
            )
            callback(AuraGatewayResult.Failure("Semua gateway yang dipilih policy AURA gagal.", decision))
            return
        }

        val profile = ordered[index]
        val gateway = gateways[profile.providerId]
        if (gateway == null) {
            executeNext(ordered, index + 1, task, messages, context, callback, policyExplanation)
            return
        }

        val request = AuraRoutingRequest(
            task = task,
            mode = when (task) {
                AuraTaskType.FAST -> AuraRoutingMode.FAST
                else -> AuraRoutingMode.BALANCED
            },
            estimatedInputTokens = messages.sumOf { it.second.length } / 4,
            maxCostUsd = null,
            requiresVision = AuraCapability.VISION in context.requiredCapabilities,
            requiresIndonesian = true
        )

        gateway.execute(request, AuraContextOptimizer().optimize(messages)) { result ->
            when (result) {
                is AuraGatewayResult.Success -> {
                    val selectedProfile = result.decision.selected ?: toModelProfile(profile)
                    callback(
                        AuraGatewayResult.Success(
                            text = result.text,
                            decision = AuraRoutingDecision(
                                selected = selectedProfile,
                                candidates = emptyList(),
                                reason = policyExplanation,
                                fallbackChain = ordered.drop(index + 1).map(::toModelProfile)
                            )
                        )
                    )
                }
                is AuraGatewayResult.Failure -> {
                    executeNext(
                        ordered,
                        index + 1,
                        task,
                        messages,
                        context,
                        callback,
                        policyExplanation
                    )
                }
            }
        }
    }

    private fun toModelProfile(profile: AuraRoutingProfile): AuraModelProfile =
        AuraModelProfile(
            providerId = profile.providerId,
            modelId = profile.modelId,
            quality = profile.quality,
            latencyMs = profile.health.latencyMs,
            costPer1kTokensUsd = profile.costPer1kTokensUsd,
            quotaRemaining = profile.health.quotaRemaining,
            health = profile.health.health,
            taskFit = profile.quality,
            supportsVision = AuraCapability.VISION in profile.capabilities,
            supportsIndonesian = true
        )
}

class AuraGatewayProviderRegistry : AuraProviderRegistry {
    override fun profiles(): List<AuraRoutingProfile> = listOf(
        AuraRoutingProfile(
            providerId = "9router",
            modelId = "auto",
            capabilities = setOf(
                AuraCapability.TEXT,
                AuraCapability.CODE,
                AuraCapability.VISION,
                AuraCapability.TRANSLATION,
                AuraCapability.TOOL_USE,
                AuraCapability.LONG_CONTEXT
            ),
            quality = 0.88,
            costPer1kTokensUsd = 0.0,
            contextWindow = 32768,
            health = AuraProviderHealth("9router", latencyMs = 900)
        )
    )
}
