package com.aura.core.vision

/**
 * Coordinates a platform camera adapter without depending on Android, AWT, or
 * a particular capture library. Callers should serialize commands on one
 * dispatcher; UI layers own permission prompts and visible camera indicators.
 */
class AuraVisionController(
    private val adapter: AuraCameraAdapter
) {
    private var permission: AuraCameraPermission = AuraCameraPermission.NOT_REQUESTED
    private var selectedDeviceId: String? = null
    private var running = false
    private var paused = false

    fun state(): AuraVisionSessionState = AuraVisionSessionState(
        selectedDeviceId = selectedDeviceId,
        permission = permission,
        isRunning = running,
        isPaused = paused,
        visibleIndicatorRequired = true
    )

    suspend fun discoverDevices(): List<AuraCameraDevice> =
        adapter.listDevices().filter { it.isConnected }.distinctBy { it.id }

    fun updatePermission(value: AuraCameraPermission) {
        permission = value
        if (value != AuraCameraPermission.GRANTED && running) {
            adapterCloseBestEffort()
        }
    }

    suspend fun selectDevice(deviceId: String) {
        require(!running) { "Close the active camera before changing devices" }
        require(discoverDevices().any { it.id == deviceId }) {
            "Camera is not currently connected: $deviceId"
        }
        selectedDeviceId = deviceId
    }

    suspend fun start(format: AuraCameraFormat? = null) {
        check(permission == AuraCameraPermission.GRANTED) {
            "Camera permission must be granted before starting"
        }
        val deviceId = selectedDeviceId
            ?: error("Select a connected camera before starting")
        check(!running) { "Camera session is already running" }
        adapter.open(deviceId, format)
        running = true
        paused = false
    }

    suspend fun captureFrame(): AuraVisionFrame? {
        check(running) { "Camera session is not running" }
        if (paused) return null
        val frame = adapter.captureFrame() ?: return null
        require(frame.width > 0 && frame.height > 0) { "Camera returned invalid frame dimensions" }
        return frame
    }

    suspend fun pause() {
        check(running) { "Camera session is not running" }
        paused = true
    }

    suspend fun resume() {
        check(running) { "Camera session is not running" }
        paused = false
    }

    suspend fun stop() {
        if (running) adapter.close()
        running = false
        paused = false
    }

    private fun adapterCloseBestEffort() {
        // Permission revocation is synchronous in the shared state model.
        // Platform integrations should also close their native session in the
        // permission callback before publishing the new permission state.
        running = false
        paused = false
    }
}
