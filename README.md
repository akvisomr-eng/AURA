# AURA

AURA is a cross-platform assistant project with an Android runtime and a Windows desktop companion.

## Windows desktop package

The Windows workflow builds a native installer and a portable package from the current `main` branch. The latest verified package run is available from [AURA Windows Actions](https://github.com/akvisomr-eng/AURA/actions/workflows/windows.yml). Open the latest successful run and download either:

- `aura-windows-installer` — Windows `.exe` installer.
- `aura-windows-portable` — portable ZIP package.

The workflow currently packages the desktop app as version `2.1.0`. Action artifacts are temporary build outputs and expire; use a GitHub Release for a durable public release when one is published.

## AURA Vision (desktop)

1. Launch AURA. The camera backend is discovered without opening a camera at startup.
2. Choose **Pindai Kamera** to discover built-in or USB webcams.
3. Choose **Mulai Kamera** and select a device to open the live preview.
4. Close the preview window or choose **Hentikan Kamera** to release the device.

Camera availability depends on the host operating system, privacy settings, drivers, and whether another app owns the device. See [the AURA Vision desktop smoke-test checklist](docs/AURA_VISION_DESKTOP_SMOKE_TEST.md) before considering a build release-ready.

## Development

The project uses Kotlin and Gradle with JDK 17. The Windows Actions workflow runs core and desktop tests, creates the Windows app image and EXE installer, and uploads both packages as workflow artifacts.
