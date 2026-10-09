package com.aura.core.vision

/**
 * Platform-neutral description of a camera and the capabilities the current
 * platform adapter can genuinely provide. This contract does not open hardware.
 */
data class AuraCameraDevice(
    val id: String,
    val displayName: String,
    val transport: AuraCameraTransport,
    val isConnected: Boolean = true,
    val supportedFormats: List<AuraCameraFormat> = emptyList(),
    val capabilities: Set<AuraVisionCapability> = emptySet()
)

enum class AuraCameraTransport { BUILT_IN, USB_UVC, USB_VENDOR_SPECIFIC, UNKNOWN }

data class AuraCameraFormat(val width: Int, val height: Int, val framesPerSecond: Int) {
    init {
        require(width > 0 && height > 0 && framesPerSecond > 0)
    }
}

enum class AuraVisionCapability {
    PREVIEW,
    FACE_DETECTION,
    SCENE_RECOGNITION,
    OCR,
    QR_BARCODE,
    PERSON_SEGMENTATION,
    VIRTUAL_BACKGROUND,
    PTZ_CONTROL
}

enum class AuraCameraPermission { NOT_REQUESTED, GRANTED, DENIED, RESTRICTED }

data class AuraVisionSessionState(
    val selectedDeviceId: String? = null,
    val permission: AuraCameraPermission = AuraCameraPermission.NOT_REQUESTED,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val visibleIndicatorRequired: Boolean = true
) {
    init {
        require(!isRunning || permission == AuraCameraPermission.GRANTED) {
            "Camera session cannot run without granted permission"
        }
        require(!isRunning || selectedDeviceId != null) {
            "A running camera session must have a selected device"
        }
    }
}

/**
 * Shared adapter contract. Platform-specific modules implement device
 * discovery and capture using native OS camera APIs.
 */
interface AuraCameraAdapter {
    suspend fun listDevices(): List<AuraCameraDevice>
    suspend fun open(deviceId: String, format: AuraCameraFormat? = null)
    suspend fun close()
    suspend fun captureFrame(): AuraVisionFrame?
}

data class AuraVisionFrame(
    val width: Int,
    val height: Int,
    val capturedAtEpochMillis: Long,
    /** Opaque platform-owned image; consumers must not retain it implicitly. */
    val platformImage: Any
)
