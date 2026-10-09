# AURA Vision Platform — staged delivery plan

## Stage 1: Device foundation
- Discover camera devices and monitor hot-plug events.
- Use OS-native UVC drivers where compatible.
- Add camera permission state, explicit start/stop, preview, device selection, and diagnostics.
- Add a capability registry so unsupported operations are not shown as working features.

## Stage 2: Local vision MVP
- Face detection and face presence, without identity recognition.
- On-device scene/object model integration with confidence labels.
- User-initiated OCR, QR/barcode, document capture, and visual troubleshooting.
- Explicit retention rules: transient frames discarded unless user saves them.

## Stage 3: Background Studio
- Person segmentation, blur, background replacement, preview and restore.
- Integrate with supported call applications.
- Package an OS-specific virtual-camera bridge only after security review, signing requirements, installation flow, and tests are addressed.
- Do not claim that a normal desktop overlay changes webcam output in all applications.

## Stage 4: Optional wellbeing cues
- Local, opt-in indicators for prolonged eye closure or repeated blinking.
- Confidence/lighting/pose checks and clear false-positive caveats.
- Gentle break reminders only; never medical diagnosis or driving fitness determination.

## Stage 5: Cross-platform adapters
- Windows Media Foundation / camera API adapter.
- Android CameraX/Camera2 adapter and USB-host support where hardware/API permit.
- Shared typed events and capability contract in AURA Core.

## Safety gates
- Visible sensor indicator and immediate pause.
- No camera start without user-authorized feature state.
- No cloud upload of frames without explicit consent.
- No covert recording or face identification by default.
- Validate disconnects, denied permissions, low light, unsupported formats, and performance before release.
