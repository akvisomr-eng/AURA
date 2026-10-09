# AURA Desktop Camera Integration

AURA Vision's desktop camera path now includes a Windows provider backed by the Webcam Capture library's built-in native driver.

## Included
- `DesktopCameraBackendProvider` is a Java `ServiceLoader` extension point; providers declare platform compatibility.
- `WindowsWebcamCaptureProvider` is selected on Windows only and uses `WindowsWebcamCaptureAdapter` to enumerate cameras, open a selected device, capture frames, and close the device.
- Camera enumeration occurs only after the user presses **Pindai Kamera**. Capture starts only after **Mulai Kamera** and an explicit device selection.
- The desktop UI includes **Pindai Kamera**, **Mulai Kamera**, and **Hentikan Kamera** controls plus a live preview window.
- Closing the preview stops the camera session. Frame capture errors are surfaced and the shared manager handles session errors.
- Unsupported platforms report the backend as unavailable rather than claiming camera support.
- Unit tests cover the provider SPI, unsupported-platform filtering, and the no-implicit-open contract.

## Platform and privacy notes
- The Windows provider is not selected on Linux or macOS. Those platforms need their own native provider.
- The adapter reports transport as unknown because this library does not reliably identify USB versus built-in cameras across drivers.
- The library exposes supported image sizes but not a trustworthy supported-FPS list, so AURA does not fabricate FPS capabilities. Requested FPS is not guaranteed by this adapter.
- Desktop JVM apps do not receive Android-style runtime camera permission prompts. Windows camera privacy settings, driver failures, and devices held by other apps may prevent opening; those errors are shown to the user.
- The webcam library's native driver is a dependency, not a guarantee that every webcam or Windows security configuration will work.

## Verification still required
CI verifies compilation, automated tests, and Windows packaging. A physical UVC webcam test on a real Windows machine is still required to verify native library loading, device enumeration, actual frame capture, and unplug/reconnect behavior before claiming hardware validation. Android requires a separate CameraX/Camera2 provider behind the same shared contract.
