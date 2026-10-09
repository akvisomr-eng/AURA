package com.aura.core.vision

/**
 * Coordinates discovery, permission, and capture lifecycle without depending on
 * Android, Windows, or a particular camera SDK. Platform adapters own hardware I/O.
 *
 * Discovery is intentionally passive: calling refreshDevices() never opens a camera.
 */
class AuraCameraManager(
    private val adapter: AuraCameraAdapter,
    private val permissionGate: AuraCameraPermissionGate
) {
    private var current = AuraCameraManagerState()

    fun state(): AuraCameraManagerState = current

    suspend fun refreshDevices(): AuraCameraManagerState {
        return try {
            val devices = adapter.listDevices().filter { it.isConnected }
            val selected = current.selectedDeviceId
            if (selected != null && devices.none { it.id == selected }) {
                runCatching { adapter.close() }
                current = current.copy(
                    devices = devices,
                    selectedDeviceId = null,
                    status = AuraCameraManagerStatus.DEVICE_DISCONNECTED,
                    lastError = "Kamera terputus. Pilih perangkat yang tersedia untuk melanjutkan."
                )
            } else {
                current = current.copy(
                    devices = devices,
                    status = if (current.status == AuraCameraManagerStatus.DEVICE_DISCONNECTED ||
                        current.status == AuraCameraManagerStatus.ERROR) AuraCameraManagerStatus.READY
                    else current.status,
                    lastError = null
                )
            }
            current
        } catch (error: Exception) {
            current = current.copy(
                status = AuraCameraManagerStatus.ERROR,
                lastError = error.message ?: "Gagal memindai perangkat kamera."
            )
            current
        }
    }

    /**
     * Starts capture only after an explicit user action and a granted OS permission.
     * A platform UI should call this from its visible "Start camera" control.
     */
    suspend fun start(
        deviceId: String,
        userInitiated: Boolean,
        format: AuraCameraFormat? = null
    ): AuraCameraManagerState {
        if (!userInitiated) {
            return fail("Kamera hanya dapat dimulai melalui tindakan eksplisit pengguna.")
        }

        val discovered = refreshDevices()
        val device = discovered.devices.firstOrNull { it.id == deviceId }
            ?: return fail("Perangkat kamera tidak ditemukan atau sudah terputus.")

        if (format != null && device.supportedFormats.isNotEmpty() && format !in device.supportedFormats) {
            return fail("Format kamera yang diminta tidak didukung perangkat.")
        }

        current = current.copy(
            selectedDeviceId = device.id,
            status = AuraCameraManagerStatus.REQUESTING_PERMISSION,
            lastError = null
        )

        val permission = try {
            permissionGate.requestPermission(device)
        } catch (error: Exception) {
            return fail(error.message ?: "Permintaan izin kamera gagal.")
        }
        current = current.copy(permission = permission)

        if (permission != AuraCameraPermission.GRANTED) {
            current = current.copy(
                selectedDeviceId = null,
                status = AuraCameraManagerStatus.PERMISSION_REQUIRED,
                lastError = when (permission) {
                    AuraCameraPermission.DENIED -> "Izin kamera ditolak."
                    AuraCameraPermission.RESTRICTED -> "Akses kamera dibatasi oleh sistem."
                    else -> "Izin kamera belum diberikan."
                }
            )
            return current
        }

        return try {
            adapter.open(device.id, format)
            current = current.copy(
                selectedDeviceId = device.id,
                status = AuraCameraManagerStatus.RUNNING,
                lastError = null,
                visibleIndicatorRequired = true
            )
            current
        } catch (error: Exception) {
            runCatching { adapter.close() }
            fail(error.message ?: "Gagal membuka kamera.")
        }
    }

    /** Pausing closes the capture handle immediately; resuming requires start() again. */
    suspend fun pause(): AuraCameraManagerState {
        if (current.status == AuraCameraManagerStatus.RUNNING) {
            runCatching { adapter.close() }
            current = current.copy(status = AuraCameraManagerStatus.PAUSED)
        }
        return current
    }

    suspend fun stop(): AuraCameraManagerState {
        val closeResult = runCatching { adapter.close() }
        current = current.copy(
            selectedDeviceId = null,
            status = if (closeResult.isSuccess) AuraCameraManagerStatus.READY else AuraCameraManagerStatus.ERROR,
            lastError = closeResult.exceptionOrNull()?.message
        )
        return current
    }

    suspend fun captureFrame(): AuraVisionFrame? {
        if (current.status != AuraCameraManagerStatus.RUNNING ||
            current.permission != AuraCameraPermission.GRANTED ||
            current.selectedDeviceId == null
        ) return null

        return try {
            adapter.captureFrame()
        } catch (error: Exception) {
            current = current.copy(
                status = AuraCameraManagerStatus.ERROR,
                lastError = error.message ?: "Pengambilan frame gagal."
            )
            runCatching { adapter.close() }
            null
        }
    }

    private fun fail(message: String): AuraCameraManagerState {
        current = current.copy(status = AuraCameraManagerStatus.ERROR, lastError = message)
        return current
    }
}

fun interface AuraCameraPermissionGate {
    suspend fun requestPermission(device: AuraCameraDevice): AuraCameraPermission
}

enum class AuraCameraManagerStatus {
    READY,
    REQUESTING_PERMISSION,
    PERMISSION_REQUIRED,
    RUNNING,
    PAUSED,
    DEVICE_DISCONNECTED,
    ERROR
}

data class AuraCameraManagerState(
    val devices: List<AuraCameraDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val permission: AuraCameraPermission = AuraCameraPermission.NOT_REQUESTED,
    val status: AuraCameraManagerStatus = AuraCameraManagerStatus.READY,
    val lastError: String? = null,
    val visibleIndicatorRequired: Boolean = true
)
