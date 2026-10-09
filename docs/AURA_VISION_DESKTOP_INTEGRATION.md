# AURA Desktop Camera Integration

This slice wires the shared camera manager into the desktop application through a provider SPI.

## Included
- `DesktopCameraBackendProvider` is a Java `ServiceLoader` extension point for native OS adapters.
- `DesktopCameraService` exposes backend availability and delegates lifecycle operations to `AuraCameraManager`.
- The desktop Status action reports whether a backend is installed.
- Tests verify that missing providers are reported honestly and that provider discovery does not list/open hardware automatically.

## Deliberate limitation
No native provider is bundled by this change. The UI therefore reports camera support as unavailable until a platform provider is shipped. This is intentional: the application must not probe cameras by opening them just to discover whether they exist.

Next implementation step: add a Windows provider backed by a native Windows camera API, including passive device enumeration, OS permission/error handling, and frame ownership/lifetime rules. Then validate on a Windows runner and a physical UVC webcam. Android should use a separate CameraX/Camera2 provider behind the same shared contract.
