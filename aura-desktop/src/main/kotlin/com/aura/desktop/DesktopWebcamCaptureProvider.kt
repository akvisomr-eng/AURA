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
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Cross-platform desktop camera provider backed by Webcam Capture.
 *
 * Provider discovery does not enumerate or reserve hardware. Camera discovery
 * and capture only happen after the user invokes the corresponding UI action.
 * Actual device permissions and driver availability remain OS-controlled.
 */
class DesktopWebcamCaptureProvider : DesktopCameraBackendProvider {
    override fun isSupported(): Boolean {
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        return os.startsWith("windows") || os.contains("linux") || os.startsWith("mac")
    }

    override fun createAdapter(): AuraCameraAdapter = DesktopWebcamCaptureAdapter()

    override fun createPermissionGate(): AuraCameraPermissionGate =
        AuraCameraPermissionGate {
            // Desktop OS camera permissions are enforced by the OS/device backend
            // when a user explicitly starts capture; this is not an Android prompt.
            AuraCameraPermission.GRANTED
        }

    override fun displayName(): String = "Desktop Webcam Capture backend"
}

/** Adapter for built-in and USB webcams exposed by the host OS camera stack. */
class DesktopWebcamCaptureAdapter(
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

        try {
            if (!webcam.open()) {
                throw IllegalStateException(
                    "Sistem operasi tidak dapat membuka kamera. Periksa izin privasi, apakah kamera sedang dipakai aplikasi lain, driver, dan koneksi perangkat."
                )
            }
            activeWebcam = webcam
        } catch (error: Exception) {
            runCatching { webcam.close() }
            throw error
        }
    }

    override suspend fun close() {
        val webcam = activeWebcam
        activeWebcam = null
        if (webcam != null && webcam.isOpen) webcam.close()
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
