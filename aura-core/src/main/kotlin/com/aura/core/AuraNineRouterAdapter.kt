package com.aura.core

class AuraNineRouterAdapter(config: AuraGatewayConfig = AuraGatewayConfig()) {
    private val gateway = AuraNeuralGateway(config, AuraNineRouterCatalog())

    fun execute(
        request: AuraRoutingRequest,
        messages: List<Pair<String, String>>,
        callback: (AuraGatewayResult) -> Unit
    ) = gateway.execute(request, messages, callback)
}

class AuraNineRouterCatalog : AuraModelCatalog {
    override fun models(): List<AuraModelProfile> = listOf(
        AuraModelProfile(
            providerId = "9router",
            modelId = "auto",
            quality = 0.88,
            latencyMs = 900,
            costPer1kTokensUsd = 0.0,
            quotaRemaining = 1.0,
            health = 1.0,
            taskFit = 0.90,
            supportsVision = true,
            supportsIndonesian = true
        )
    )
}
