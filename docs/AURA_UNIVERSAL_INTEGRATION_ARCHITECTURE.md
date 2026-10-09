# AURA Universal Integration Architecture

Status: architecture baseline; implementation is staged and must be validated per adapter/platform.

## Product goal

AURA should be usable in two ways:

1. **Embedded in an application** — the host app adds an AURA SDK/component and exposes selected AURA capabilities inside its own UI.
2. **Connected as a runtime/service** — an app, browser, desktop process, or device connects to an AURA runtime over a documented local or remote API.

The goal is broad interoperability, not the false promise that one binary can run unchanged on every OS or that every host app permits arbitrary plugins. Each integration must respect the host platform's sandbox, permissions, lifecycle, signing, and distribution rules.

## Architecture: stable core, replaceable adapters

```text
Host applications
  Android / iOS / Web / Windows / macOS / Linux / enterprise apps / devices
             | SDK, component, plugin, or API client
             v
AURA Integration Gateway
  Auth + consent + capability discovery + versioned API + events
             |
             v
AURA Runtime
  orchestration / memory / policy / tool routing / model selection
             |
             v
Capability contracts (platform-neutral)
  voice | vision | files | notifications | browser | automation | sensors | context
             |
             v
Platform & device adapters
  Android APIs | Apple APIs | browser APIs | desktop APIs | USB/UVC | vendor SDKs
```

The integration gateway is a boundary, not a second AI brain. It should route requests to the same AURA runtime and enforce the same permission and policy rules.

## Integration surfaces

| Surface | Best fit | Distribution target |
|---|---|---|
| Native Android SDK | Android apps that need in-process integration | Android AAR, Kotlin/Java API |
| Apple SDK | iOS/iPadOS apps | Swift API and XCFramework; build on macOS/Xcode |
| Web SDK | Websites and browser-based apps | TypeScript package and optional Web Component |
| Desktop SDK | JVM apps and supported desktop hosts | JVM library plus platform-specific adapters |
| HTTP + WebSocket API | Apps in any language, plugins, enterprise systems | OpenAPI-described HTTP endpoints and versioned event stream |
| Device adapter SPI | USB cameras, microphones, sensors, smart glasses, vehicle/robot systems | Explicitly versioned provider interface and capability manifest |
| CLI / local bridge | Scripts, developer tools, local automation | Stable command interface and localhost IPC/API |

These are separate delivery artifacts sharing one protocol and capability model. A platform is only marked supported after its adapter has tests and a documented verification status.

## Cross-platform contract

All host integrations should use a stable, language-neutral request/event model.

### Request envelope

```json
{
  "apiVersion": "v1",
  "requestId": "caller-generated-id",
  "sessionId": "optional-session-id",
  "capability": "vision.capture",
  "operation": "start",
  "input": {},
  "context": {
    "locale": "id-ID",
    "hostApp": "example-app"
  },
  "permissions": ["camera"],
  "timeoutMs": 15000
}
```

### Response envelope

```json
{
  "apiVersion": "v1",
  "requestId": "same-request-id",
  "status": "accepted",
  "data": {},
  "error": null
}
```

Errors must use stable machine-readable codes, including unsupported capability, permission denied, user consent required, adapter unavailable, device busy, timeout, and incompatible API version. Streaming events must include an event ID, session ID, timestamp, capability, and payload schema version. Never expose arbitrary local file paths, secrets, or raw device access to a host app by default.

## Capability discovery and plugin model

Every adapter publishes a manifest containing:

- unique adapter ID, version, supported OS/architecture, and minimum runtime version;
- capability IDs and operations;
- required permissions, optional permissions, and data sensitivity;
- whether processing is local, remote, or mixed;
- supported device transports/formats, if applicable;
- health status, cancellation support, and known limitations.

AURA discovers capabilities rather than assuming that all devices support them. A plugin can only request declared capabilities; the runtime checks policy and user consent before execution. Adapter installation must not silently install drivers, open a camera/microphone, or grant permissions. USB/UVC support is a device path, not a guarantee that every vendor-specific device will work without its driver or SDK.

## Security and privacy requirements

- Default-deny capability access; request the minimum permission for each operation.
- Camera, microphone, screen, location, files, and automation require explicit permission and visible state.
- Bind local APIs to loopback by default; require authentication and TLS for remote access.
- Do not expose arbitrary code execution as a general plugin capability.
- Keep host-app identity, user consent, and audit events in the authorization context.
- Provide cancellation, timeout, rate limits, revocation, and clear errors.
- Sensitive vision/audio analysis should be opt-in, with local processing preferred when practical; do not present emotion or fatigue estimates as medical diagnosis.
- The host app retains control over its UI and lifecycle. AURA cannot force overlays, background execution, or cross-app control where an OS forbids it.

## Compatibility tiers

Use evidence-based status labels in documentation and releases:

- **Tier A — CI verified:** compiles and automated tests pass for the declared target.
- **Tier B — device verified:** Tier A plus tests on at least one physical device for the declared OS/device class.
- **Tier C — interoperability verified:** Tier B plus a sample host application and documented integration test.
- **Experimental:** implementation exists but is not yet validated to the above standard.
- **Planned:** no implementation claim.

Do not describe AURA as universally compatible until each target has been independently verified.

## Delivery plan

### Phase 1 — Stable integration contract
- Publish API versioning, capability IDs, request/response/event envelopes, error codes, adapter manifests, and permission semantics.
- Add a sample host app and contract tests.
- Document local-runtime versus embedded-SDK trade-offs.

### Phase 2 — First usable SDKs
- Keep the existing Android app and Windows desktop app working.
- Add a documented local API/bridge so a second app can invoke approved AURA capabilities without depending on AURA's UI.
- Package a TypeScript web SDK and an Android library once their contracts are tested.
- Add a small reference host application for each published SDK.

### Phase 3 — Additional platform adapters
- Add Apple/iOS, macOS, and Linux integrations with native permission and lifecycle handling.
- Add browser extension APIs only where browser policy permits them.
- Add device providers (camera, microphone, sensors, smart glasses) behind the same capability contracts.

### Phase 4 — Ecosystem
- Publish SDK packages, semantic versioning, compatibility matrix, sample apps, adapter certification tests, and developer documentation.
- Add optional enterprise gateway and remote runtime modes with authentication and tenant isolation.

## Current known baseline and gaps

- The repository currently contains an Android application and a JVM desktop application; the Windows desktop camera provider is a platform-specific implementation.
- The Windows camera implementation does not establish Linux/macOS or Android camera support. Android CameraX/Camera2 and Apple camera providers remain separate work.
- No claim is made here that an SDK for every platform, an API gateway, or third-party app embedding is already implemented. This document defines the compatibility contract to implement and verify incrementally.

## Definition of done for an integration

An integration is not complete until it has: published API/package docs; explicit permissions and lifecycle behavior; a sample host; contract and failure-path tests; CI for its target; a documented compatibility tier; and, where hardware is involved, physical-device verification. A passing build alone is not proof that all devices work.
