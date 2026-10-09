# AURA Vision & Universal USB Peripheral Platform

## Product goal
AURA should treat a connected USB webcam or supported UVC camera as an optional "eye" and expose a consistent capability interface. It should also support user-commanded virtual backgrounds on supported video-call workflows. This document defines the implementation target; it does not claim these capabilities are already implemented.

## 1. Plug-and-play camera architecture
- Prefer OS-native, class-compliant USB Video Class (UVC) devices so supported cameras work with built-in OS drivers. Do not promise zero-driver support for every USB camera: proprietary devices, unusual resolutions, audio interfaces, PTZ controls, and vendor-specific features may need an official driver or permission.
- Device Discovery enumerates connect/disconnect events, friendly name, vendor/product identifiers, supported formats/resolutions, and OS camera permission state.
- Camera Adapter exposes a stable interface: listDevices, open, close, listFormats, captureFrame, setResolution, and capability metadata.
- Hot-plug manager safely releases a disconnected device, retries only with bounded backoff, and never blocks the UI.
- Windows: use Media Foundation / Windows camera APIs where practical; Android: use Camera2/CameraX and USB-host APIs when device and permissions support it. Keep platform-specific adapters separate behind a shared capability contract.
- Do not install drivers silently or elevate privileges. Offer a clear setup message if a device requires a vendor driver.

## 2. Vision features
### MVP
- Camera preview with explicit start/stop and a clear camera-in-use indicator.
- Face detection and face count/localization, with processing on-device where feasible.
- User-authorized face-presence events (e.g. frame a face in a call), not identity recognition by default.
- Basic scene/object recognition through an explicitly selected model; show confidence and allow correction.
- QR/barcode and document capture modes with user initiation.
- Snapshot/video capture only after clear user action.

### Expression and fatigue signals
- Optional, on-device estimates of visible facial action/expression cues, with confidence and limitations. Do not claim to know a person's true emotional state.
- Optional fatigue/attention cues from repeated eye closure, blink patterns, head pose, and time-on-task only when camera position and lighting are adequate. Treat output as a weak signal, not a medical diagnosis or proof of impairment.
- Avoid continuous recording by default; process transient frames locally and discard them unless the user explicitly saves.
- Use neutral language: "Tanda mata tertutup berulang terdeteksi; ingin beristirahat?" Do not label the person lazy, depressed, drunk, or unfit to drive.
- Never use webcam fatigue estimates to determine driving fitness or operate a vehicle autonomously. In driving scenarios, defer to validated safety systems and encourage stopping safely if the user feels sleepy.
- No emotion/face analysis for children, hiring, education scoring, law enforcement, or other high-impact decisions.

## 3. Virtual background
- User command examples: "AURA, blur latar belakang", "ganti latar ke kantor", "pakai latar pantai", "kembalikan latar asli".
- Pipeline: camera frames -> person segmentation -> background blur/replacement -> virtual camera output to supported applications.
- On Windows, a true system-wide virtual camera may require a signed/installed virtual-camera component or integration with supported conferencing APIs; ordinary desktop UI cannot automatically replace the camera feed in every app.
- Provide a preview, edge-quality slider, background library, custom image picker, and instant restore-original action.
- Use local assets by default. Do not upload webcam frames or custom background images to cloud services without explicit consent.
- If virtual-camera integration is not available, offer supported app-specific background controls or a clearly labeled preview mode rather than claiming system-wide replacement.

## 4. Other useful vision capabilities
- Whiteboard/document capture with perspective correction and OCR.
- Translate text seen through camera, with a review step before acting on it.
- Read labels, diagrams, and objects aloud for accessibility.
- Desk/workspace context: detect whether a document or object is in view only on request.
- Gesture shortcuts (opt-in), such as open/close avatar or pause/resume, with false-trigger safeguards.
- Visual troubleshooting: user points camera at a device/error screen and AURA suggests safe next checks.
- Project progress capture: compare user-selected before/after images and annotate visible differences; do not infer exact dimensions without calibration.
- Multi-camera selection and device profiles for webcams, capture cards, document cameras, and supported USB endoscopes where platform APIs expose them.
- Camera health diagnostics: resolution, frame rate, dropped frames, permissions, privacy shutter, and device disconnect notices.

## 5. Permissions and privacy
- Camera is off until the user explicitly enables a feature or chooses an ongoing session.
- Persistent vision mode must be a separate opt-in with a visible indicator, pause button, and quiet hours.
- Keep detection local where possible. For cloud vision, show what will be sent and request consent.
- No covert recording, hidden camera activation, or background capture without a clear user-authorized feature.
- Provide a one-click "pause all sensors", recent processing indicator, data retention settings, and delete controls.
- Do not identify strangers or build face identity profiles by default. Store face templates only if a clearly explained feature specifically requires them and the user explicitly opts in.

## 6. Suggested architecture
- aura-vision-contract: platform-neutral camera/vision capability interfaces and typed events.
- aura-vision-windows: native camera enumeration and capture adapter.
- aura-vision-android: CameraX/Camera2 and supported USB host adapter.
- aura-vision-runtime: model registry, on-device inference, frame scheduler, privacy gate.
- aura-background-studio: segmentation, blur, image replacement, preview and export.
- aura-virtual-camera: optional OS-specific virtual-camera bridge; separately packaged, permissioned and signed where required.
- Capability registry advertises supported functions so AURA never promises a feature a device cannot provide.

## 7. Performance and reliability
- Frame-rate throttling and adaptive resolution to control CPU/GPU/battery.
- Pause inference when the camera is hidden or the user pauses the feature.
- Bounded frame queues so stale frames are dropped instead of building latency.
- Graceful fallback when no camera is connected or a model/GPU is unavailable.
- Tests for hot-plug, disconnect during capture, permission denied, unsupported format, low light, occlusion, and multiple cameras.

## 8. Acceptance criteria
- A UVC camera can be discovered and opened using OS support when compatible.
- Camera can be turned off immediately and visibly.
- Unplugging a device does not crash AURA or hang the UI.
- Vision results include confidence and appropriate limitations.
- No frames leave the device unless the user explicitly enables a cloud feature.
- Background replacement is only advertised as system-wide when the virtual camera route is actually installed and tested.
- Every platform advertises only capabilities it can genuinely provide.
