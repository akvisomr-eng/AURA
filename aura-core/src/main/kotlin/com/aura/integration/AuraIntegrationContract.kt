package com.aura.integration

/**
 * Language-neutral contract types for applications and device adapters integrating with AURA.
 *
 * These are transport-independent models. HTTP/JSON serialization belongs to a gateway module,
 * so the core contract does not force a networking stack or JSON library on Android/desktop hosts.
 */
object AuraIntegrationApi {
    const val CURRENT_VERSION = "v1"
}

data class AuraCapabilityId(val value: String) {
    init {
        require(value.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9_-]*)+"))) {
            "Capability IDs must be namespaced, e.g. vision.capture"
        }
    }
}

data class AuraIntegrationRequest(
    val apiVersion: String = AuraIntegrationApi.CURRENT_VERSION,
    val requestId: String,
    val sessionId: String? = null,
    val capability: AuraCapabilityId,
    val operation: String,
    val input: Map<String, Any?> = emptyMap(),
    val locale: String? = null,
    val hostApplicationId: String,
    val requestedPermissions: Set<AuraPermission> = emptySet(),
    val timeoutMillis: Long = 15_000
)

data class AuraIntegrationResponse(
    val apiVersion: String = AuraIntegrationApi.CURRENT_VERSION,
    val requestId: String,
    val status: AuraResponseStatus,
    val data: Map<String, Any?> = emptyMap(),
    val error: AuraIntegrationError? = null
)

enum class AuraResponseStatus { ACCEPTED, SUCCEEDED, REJECTED, FAILED }

data class AuraIntegrationError(
    val code: AuraIntegrationErrorCode,
    val message: String,
    val retryable: Boolean = false
)

enum class AuraIntegrationErrorCode {
    INVALID_REQUEST,
    UNSUPPORTED_API_VERSION,
    UNSUPPORTED_CAPABILITY,
    UNSUPPORTED_OPERATION,
    PERMISSION_DENIED,
    USER_CONSENT_REQUIRED,
    ADAPTER_UNAVAILABLE,
    DEVICE_BUSY,
    TIMEOUT,
    RATE_LIMITED,
    INTERNAL_ERROR
}

enum class AuraPermission(val sensitive: Boolean) {
    CAMERA(true),
    MICROPHONE(true),
    LOCATION(true),
    FILE_READ(true),
    FILE_WRITE(true),
    SCREEN_CAPTURE(true),
    NOTIFICATIONS(false),
    DEVICE_CONTROL(true),
    BROWSER_ACCESS(true)
}

enum class AuraProcessingLocation { LOCAL, REMOTE, HYBRID }

data class AuraCapabilityDeclaration(
    val id: AuraCapabilityId,
    val operations: Set<String>,
    val requiredPermissions: Set<AuraPermission> = emptySet(),
    val optionalPermissions: Set<AuraPermission> = emptySet(),
    val processingLocation: AuraProcessingLocation = AuraProcessingLocation.LOCAL,
    val description: String = ""
)

data class AuraAdapterManifest(
    val adapterId: String,
    val version: String,
    val supportedPlatforms: Set<String>,
    val minimumRuntimeVersion: String,
    val capabilities: Set<AuraCapabilityDeclaration>,
    val supportsCancellation: Boolean = true
)

/** A pure validator shared by SDKs and gateway implementations. It does not grant permissions. */
object AuraIntegrationContractValidator {
    fun validate(request: AuraIntegrationRequest): AuraIntegrationError? {
        if (request.apiVersion != AuraIntegrationApi.CURRENT_VERSION) {
            return AuraIntegrationError(
                AuraIntegrationErrorCode.UNSUPPORTED_API_VERSION,
                "Unsupported API version: ${request.apiVersion}"
            )
        }
        if (request.requestId.isBlank()) {
            return AuraIntegrationError(AuraIntegrationErrorCode.INVALID_REQUEST, "requestId is required")
        }
        if (request.hostApplicationId.isBlank()) {
            return AuraIntegrationError(
                AuraIntegrationErrorCode.INVALID_REQUEST,
                "hostApplicationId is required"
            )
        }
        if (request.operation.isBlank()) {
            return AuraIntegrationError(AuraIntegrationErrorCode.INVALID_REQUEST, "operation is required")
        }
        if (request.timeoutMillis !in 1..300_000) {
            return AuraIntegrationError(
                AuraIntegrationErrorCode.INVALID_REQUEST,
                "timeoutMillis must be between 1 and 300000"
            )
        }
        if (request.sessionId != null && request.sessionId.isBlank()) {
            return AuraIntegrationError(
                AuraIntegrationErrorCode.INVALID_REQUEST,
                "sessionId must be omitted or non-blank"
            )
        }
        return null
    }

    fun resolve(
        request: AuraIntegrationRequest,
        manifest: AuraAdapterManifest
    ): AuraIntegrationError? {
        validate(request)?.let { return it }

        val declaration = manifest.capabilities.firstOrNull { it.id == request.capability }
            ?: return AuraIntegrationError(
                AuraIntegrationErrorCode.UNSUPPORTED_CAPABILITY,
                "Adapter ${manifest.adapterId} does not provide ${request.capability.value}"
            )

        if (request.operation !in declaration.operations) {
            return AuraIntegrationError(
                AuraIntegrationErrorCode.UNSUPPORTED_OPERATION,
                "Operation ${request.operation} is not supported by ${request.capability.value}"
            )
        }

        val missingDeclarations = declaration.requiredPermissions - request.requestedPermissions
        if (missingDeclarations.isNotEmpty()) {
            return AuraIntegrationError(
                AuraIntegrationErrorCode.INVALID_REQUEST,
                "Request must declare required permissions: ${missingDeclarations.joinToString()}"
            )
        }
        return null
    }
}
