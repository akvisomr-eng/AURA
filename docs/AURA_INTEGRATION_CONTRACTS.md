# AURA Integration Contracts (v1)

The first platform-neutral integration contract lives in `aura-core` under
`com.aura.integration`. It is deliberately independent of HTTP servers and JSON libraries so
Android, desktop, and future SDKs can share the same semantics without inheriting one transport.

## Request lifecycle

1. A host creates an `AuraIntegrationRequest` with a unique request ID, its application ID,
   a namespaced capability ID, an operation, timeout, and the permissions it is requesting.
2. The runtime validates the envelope using `AuraIntegrationContractValidator.validate`.
3. An adapter manifest is used to resolve whether the requested capability and operation exist.
4. The host OS permission state and explicit user consent must be checked separately by the runtime.
   Declaring a permission in the request is not a grant and never bypasses operating-system prompts.
5. The runtime returns an `AuraIntegrationResponse` with a stable status and, when needed, a
   machine-readable `AuraIntegrationErrorCode`.

## Example (Kotlin)

```kotlin
val capture = AuraCapabilityDeclaration(
    id = AuraCapabilityId("vision.capture"),
    operations = setOf("start", "stop", "capture"),
    requiredPermissions = setOf(AuraPermission.CAMERA)
)

val manifest = AuraAdapterManifest(
    adapterId = "aura.example.camera",
    version = "1.0.0",
    supportedPlatforms = setOf("windows"),
    minimumRuntimeVersion = "2.0.0",
    capabilities = setOf(capture)
)

val request = AuraIntegrationRequest(
    requestId = "request-123",
    capability = AuraCapabilityId("vision.capture"),
    operation = "start",
    hostApplicationId = "com.example.host",
    requestedPermissions = setOf(AuraPermission.CAMERA)
)

val error = AuraIntegrationContractValidator.resolve(request, manifest)
if (error == null) {
    // Continue through runtime authorization and adapter execution.
    // A null result does not mean the user granted camera permission.
}
```

## Current scope

Implemented here:
- version identifier and transport-neutral request/response models;
- namespaced capability IDs, adapter manifests, declared operations and permissions;
- stable error codes and basic envelope/capability validation;
- unit tests for valid requests and important rejection paths.

Not implemented by this contract alone:
- an HTTP/WebSocket server, authentication, JSON serialization, actual OS permission checks,
  user consent UI, rate limiting, adapter execution, SDK package publication, or remote hosting.
  Those belong in the gateway/runtime and platform SDK layers and must be tested separately.

## Compatibility rule

An adapter may advertise only capabilities it actually implements for a named platform. A passing
contract test does not prove hardware compatibility. Hardware integrations still require device tests.
