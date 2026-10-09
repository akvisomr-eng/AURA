# AURA Vision Camera Manager

The shared `AuraCameraManager` in `aura-core` coordinates camera lifecycle without coupling core policy to a platform SDK.

## Shared lifecycle behavior
- Passive device discovery; scanning never opens a camera.
- Explicit user action is required to start capture.
- Permission gate runs before the adapter opens the device.
- Format validation is applied when the adapter advertises supported formats.
- The manager keeps a visible-capture-indicator requirement in session state.
- Pause closes the capture handle; resuming requires another explicit start.
- Disconnect and capture errors transition to explicit states.

## Implemented platform path
The Windows desktop application now registers `WindowsWebcamCaptureProvider` using Webcam Capture's native driver. The desktop UI provides camera scan, explicit device selection, start/stop controls, and a live preview window. The adapter reports frame data through the shared `AuraVisionFrame` contract.

## Boundaries
- CI can validate compilation, automated lifecycle tests, and Windows packaging, but it cannot prove compatibility with a particular physical webcam.
- Windows transport type is reported as unknown where the native library cannot reliably distinguish USB UVC from built-in cameras.
- Requested frame rate is not guaranteed because the library does not expose a reliable supported-FPS list.
- Android CameraX/Camera2 and USB-host support are not implemented by the Windows provider and require a separate Android adapter.

## Validation
Run:
```sh 
gradle :aura-core:test --no-daemon
gradle :aura-desktop:test --no-daemon
```
