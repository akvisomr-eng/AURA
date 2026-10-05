package com.aura.core
data class AuraRuntimeStatus(val version: String = "1.6.4", val policyBoundaryActive: Boolean = true)
class AuraRuntime { fun status(): AuraRuntimeStatus = AuraRuntimeStatus() }
