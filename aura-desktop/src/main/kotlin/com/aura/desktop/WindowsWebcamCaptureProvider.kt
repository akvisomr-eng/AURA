package com.aura.desktop

import com.aura.core.vision.AuraCameraAdapter
import com.aura.core.vision.AuraCameraDevice
import com.aura.core.vision.AuraCameraFormat
import com.aura.core.vision.AuraCameraPermission
import com.aura.core.vision.AuraCameraPermissionGate
import com.aura.core.vision.AuraCameraTransport
import com.aura.core.vision.AuraVisionCapability
import com.aura.core.vision.AuraVisionFrame
import com.github.sarxos.webcam.Webcam
import java.awt.Dimension
import java.util.concurrent.TimeUnit

/**
 * Windows desktop camera provider backed by Webcam Capture's built-in native driver.
 *
 * Device enumeration is separate from open(): constructing the provider does not
 * enumerate, reserve, or capture from a camera. Capture begins only through the
 * shared manager's explicit start action.
 */
class WindowsWebcamCaptureProvider : DesktopCameraBackendProvider {
    override fun isSupported(): Boolean =
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    override fun createAdapter(): AuraCameraAdapter = WindowsWebcamCaptureAdapter()

    override fun createPermissionGate(): AuraCameraPermissionGate =
        AuraCameraPermissionGate {
            // Desktop JVM apps do not have Android-style runtime camera permission
            // prompts. The manager reaches this gate only after an explicit user
            // action; Windows privacy policy / device access failures are surfaced
            // when the native backend attempts to open the selected camera.
            AuraCameraPermission.GRANTED
        }

    override fun displayName(): String = "Windows native webcam backend"
}

/**
 * Adapter for USB UVC and built-in cameras visible to the Windows camera stack.
 * Webcam Capture exposes image sizes but not a reliable supported-FPS list, so this
 * adapter does not invent advertised frame-rate capabilities.
 */
class WindowsWebcamCaptureAdapter(
    private val discover: () -> List<Webcam> = {
        Webcam.getWebcams(5, TimeUnit.SECONDS)
    }
) : AuraCameraAdapter {
    private var devicesById: Map<String, Webcam> = emptyMap()
    private var activeWebcam: Webcam? = null

    override suspend fun listDevices(): List<AuraCameraDevice> {
        val discovered = discover()
        val nameCounts = mutableMapOf<String, Int>()
        val mapped = linkedMapOf<String, Webcam>()
        val result = discovered.map { webcam ->
            val name = webcam.name.ifBlank { "Webcam" }
            val index = nameCounts.getOrDefault(name, 0)
            nameCounts[name] = index + 1
            val id = if (index == 0) name else "$name#$index"
            mapped[id] = webcam
            AuraCameraDevice(
                id = id,
                displayName = name,
                transport = AuraCameraTransport.UNKNOWN,
                isConnected = true,
                supportedFormats = emptyList(),
                capabilities = setOf(AuraVisionCapability.PREVIEW)
            )
        }
        devicesById = mapped
        return result
    }

    override suspend fun open(deviceId: String, format: AuraCameraFormat?) {
        close()
        val webcam = devicesById[deviceId]
            ?: throw IllegalArgumentException("Kamera tidak ditemukan. Pindai ulang perangkat lalu coba lagi.")

        if (format != null) {
            val requestedSize = Dimension(format.width, format.height)
            if (requestedSize !in webcam.viewSizes) {
                throw IllegalArgumentException(
                    "Resolusi ${format.width}x${format.height} tidak didukung kamera."
                )
            }
            webcam.viewSize = requestedSize
        }

        if (!webcam.open()) {
            throw IllegalStateException(
                "Windows tidak dapat membuka kamera. Periksa izin privasi, aplikasi lain yang memakai kamera, dan koneksi perangkat."
            )
        }
        activeWebcam = webcam
    }

    override suspend fun close() {
        val webcam = activeWebcam
        activeWebcam = null
        if (webcam != null && webcam.isOpen) {
            webcam.close()
        }
    }

    override suspend fun captureFrame(): AuraVisionFrame? {
        val webcam = activeWebcam ?: return null
        val image = webcam.image
            ?: throw IllegalStateException("Kamera tidak menghasilkan frame. Periksa apakah perangkat terputus.")
        return AuraVisionFrame(
            width = image.width,
            height = image.height,
            capturedAtEpochMillis = System.currentTimeMillis(),
            platformImage = image
        )
    }
}
