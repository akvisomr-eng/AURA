# AURA Vision Camera Manager vertical slice

This slice adds a platform-neutral lifecycle coordinator to `aura-core`. It is intentionally independent of camera SDKs so Windows and Android adapters can implement the existing `AuraCameraAdapter` contract without duplicating lifecycle policy.

## Included behavior

- Passive discovery; scanning never opens a camera.
- Explicit user action required to start capture.
- Permission gate runs before opening the adapter.
- Optional format validation against discovered device capabilities.
- Visible capture indicator remains required in state.
- Pause closes the capture handle; resuming requires an explicit start.
- Device unplug and frame-capture errors transition to explicit states.
- Fake-adapter tests cover discovery, consent, permissions, format rejection, pause, unplug, and capture failure.

## Current boundary

This is a tested orchestration layer, not a hardware adapter. The repository still needs native implementations: Windows Media Foundation or a supported camera library, and Android CameraX/Camera2 plus USB-host handling where available. Real webcam enumeration, live preview rendering, hot-plug event subscriptions, and end-to-end hardware tests are not provided by this slice.

## Validation

Run:

```sh
gradle :aura-core:test --no-daemon
gradle :aura-desktop:test --no-daemon
```

The GitHub Actions Windows workflow also tests the core and desktop modules before packaging.
