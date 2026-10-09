package com.aura.desktop

import com.aura.core.vision.AuraCameraAdapter
import com.aura.core.vision.AuraCameraPermissionGate
import com.aura.core.vision.AuraCameraManager
import com.aura.core.vision.AuraCameraManagerState
import java.util.ServiceLoader

/**
 * Desktop integration seam for native camera backends.
 *
 * Providers declare platform compatibility and are selected without enumerating or
 * opening hardware. Camera discovery happens only after the user requests a scan.
 */
interface DesktopCameraBackendProvider {
    fun isSupported(): Boolean = true
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
    private val provider = providers.firstOrNull { it.isSupported() }
    private val manager: AuraCameraManager? = provider?.let {
        AuraCameraManager(it.createAdapter(), it.createPermissionGate())
    }

    fun availability(): DesktopCameraAvailability =
        if (provider == null) {
            DesktopCameraAvailability(
                available = false,
                backendName = null,
                message = "Backend kamera native tidak tersedia untuk platform ini. AURA tidak akan membuka kamera secara diam-diam."
            )
        } else {
            DesktopCameraAvailability(
                available = true,
                backendName = provider.displayName(),
                message = "Backend ${provider.displayName()} tersedia. Pemindaian dan capture hanya dilakukan setelah tindakan pengguna."
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

    suspend fun captureFrame() = manager?.captureFrame()
}
