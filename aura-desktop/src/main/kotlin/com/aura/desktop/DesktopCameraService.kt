package com.aura.desktop

import com.aura.core.vision.AuraCameraAdapter
import com.aura.core.vision.AuraCameraPermissionGate
import com.aura.core.vision.AuraCameraManager
import com.aura.core.vision.AuraCameraManagerState
import java.util.ServiceLoader

/**
 * Desktop integration seam for native camera backends.
 *
 * Native providers are discovered with ServiceLoader so Windows Media Foundation,
 * Linux V4L2, and macOS AVFoundation implementations can be shipped independently.
 * No provider means camera support is reported as unavailable; we never probe/open
 * hardware just to decide whether a camera exists.
 */
interface DesktopCameraBackendProvider {
    fun createAdapter(): AuraCameraAdapter
    fun createPermissionGate(): AuraCameraPermissionGate
    fun displayName(): String
}

data class DesktopCameraAvailability(
    val available: Boolean,
    val backendName: String?,
    val message: String
)

class DesktopCameraService(
    providers: List<DesktopCameraBackendProvider> = ServiceLoader
        .load(DesktopCameraBackendProvider::class.java)
        .iterator()
        .asSequence()
        .toList()
) {
    private val provider = providers.firstOrNull()
    private val manager: AuraCameraManager? = provider?.let {
        AuraCameraManager(it.createAdapter(), it.createPermissionGate())
    }

    fun availability(): DesktopCameraAvailability =
        if (provider == null) {
            DesktopCameraAvailability(
                available = false,
                backendName = null,
                message = "Backend kamera native belum tersedia. AURA tidak akan mengaktifkan atau memindai kamera dengan membuka perangkat secara diam-diam."
            )
        } else {
            DesktopCameraAvailability(
                available = true,
                backendName = provider.displayName(),
                message = "Backend ${provider.displayName()} tersedia. Kamera tetap memerlukan tindakan mulai dan izin pengguna."
            )
        }

    fun state(): AuraCameraManagerState? = manager?.state()

    suspend fun refreshDevices(): AuraCameraManagerState? = manager?.refreshDevices()

    suspend fun start(
        deviceId: String,
        userInitiated: Boolean
    ): AuraCameraManagerState? =
        manager?.start(deviceId = deviceId, userInitiated = userInitiated)

    suspend fun pause(): AuraCameraManagerState? = manager?.pause()

    suspend fun stop(): AuraCameraManagerState? = manager?.stop()
}
