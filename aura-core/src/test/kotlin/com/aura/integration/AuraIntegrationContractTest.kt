package com.aura.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuraIntegrationContractTest {
    private val visionCapture = AuraCapabilityDeclaration(
        id = AuraCapabilityId("vision.capture"),
        operations = setOf("start", "stop", "capture"),
        requiredPermissions = setOf(AuraPermission.CAMERA)
    )

    private val manifest = AuraAdapterManifest(
        adapterId = "aura.test-camera",
        version = "1.0.0",
        supportedPlatforms = setOf("windows"),
        minimumRuntimeVersion = "2.0.0",
        capabilities = setOf(visionCapture)
    )

    private fun request(
        apiVersion: String = AuraIntegrationApi.CURRENT_VERSION,
        requestId: String = "request-1",
        operation: String = "start",
        permissions: Set<AuraPermission> = setOf(AuraPermission.CAMERA),
        timeoutMillis: Long = 15_000
    ) = AuraIntegrationRequest(
        apiVersion = apiVersion,
        requestId = requestId,
        capability = AuraCapabilityId("vision.capture"),
        operation = operation,
        hostApplicationId = "com.example.host",
        requestedPermissions = permissions,
        timeoutMillis = timeoutMillis
    )

    @Test
    fun validRequestPassesValidationAndCapabilityResolution() {
        assertNull(AuraIntegrationContractValidator.validate(request()))
        assertNull(AuraIntegrationContractValidator.resolve(request(), manifest))
    }

    @Test
    fun unsupportedApiVersionIsRejected() {
        val error = AuraIntegrationContractValidator.validate(request(apiVersion = "v99"))
        assertNotNull(error)
        assertEquals(AuraIntegrationErrorCode.UNSUPPORTED_API_VERSION, error.code)
    }

    @Test
    fun blankRequestIdIsRejected() {
        val error = AuraIntegrationContractValidator.validate(request(requestId = " "))
        assertNotNull(error)
        assertEquals(AuraIntegrationErrorCode.INVALID_REQUEST, error.code)
    }

    @Test
    fun invalidTimeoutIsRejected() {
        val error = AuraIntegrationContractValidator.validate(request(timeoutMillis = 0))
        assertNotNull(error)
        assertEquals(AuraIntegrationErrorCode.INVALID_REQUEST, error.code)
    }

    @Test
    fun undeclaredCapabilityIsRejected() {
        val request = request().copy(capability = AuraCapabilityId("voice.synthesis"))
        val error = AuraIntegrationContractValidator.resolve(request, manifest)
        assertNotNull(error)
        assertEquals(AuraIntegrationErrorCode.UNSUPPORTED_CAPABILITY, error.code)
    }

    @Test
    fun unsupportedOperationIsRejected() {
        val error = AuraIntegrationContractValidator.resolve(request(operation = "delete"), manifest)
        assertNotNull(error)
        assertEquals(AuraIntegrationErrorCode.UNSUPPORTED_OPERATION, error.code)
    }

    @Test
    fun requiredPermissionMustBeDeclaredByCaller() {
        val error = AuraIntegrationContractValidator.resolve(
            request(permissions = emptySet()),
            manifest
        )
        assertNotNull(error)
        assertEquals(AuraIntegrationErrorCode.INVALID_REQUEST, error.code)
    }

    @Test
    fun sensitivePermissionIsExplicitlyMarked() {
        assertEquals(true, AuraPermission.CAMERA.sensitive)
        assertEquals(false, AuraPermission.NOTIFICATIONS.sensitive)
    }
}
