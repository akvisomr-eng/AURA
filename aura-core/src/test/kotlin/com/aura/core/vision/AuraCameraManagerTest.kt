package com.aura.core.vision

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuraCameraManagerTest {
    private val format = AuraCameraFormat(1280, 720, 30)
    private val camera = AuraCameraDevice(
        id = "usb-1",
        displayName = "USB Webcam",
        transport = AuraCameraTransport.USB_UVC,
        supportedFormats = listOf(format),
        capabilities = setOf(AuraVisionCapability.PREVIEW)
    )

    @Test
    fun discoveryDoesNotOpenCameraAutomatically() {
        val adapter = FakeAdapter(listOf(camera))
        val manager = AuraCameraManager(adapter, FakePermissionGate(AuraCameraPermission.GRANTED))

        val state = runSuspend { manager.refreshDevices() }

        assertEquals(listOf(camera), state.devices)
        assertEquals(0, adapter.openCalls)
        assertEquals(AuraCameraManagerStatus.READY, state.status)
    }

    @Test
    fun startRequiresExplicitUserAction() {
        val adapter = FakeAdapter(listOf(camera))
        val manager = AuraCameraManager(adapter, FakePermissionGate(AuraCameraPermission.GRANTED))

        val state = runSuspend { manager.start(camera.id, userInitiated = false) }

        assertEquals(AuraCameraManagerStatus.ERROR, state.status)
        assertEquals(0, adapter.openCalls)
    }

    @Test
    fun deniedPermissionNeverOpensCamera() {
        val adapter = FakeAdapter(listOf(camera))
        val manager = AuraCameraManager(adapter, FakePermissionGate(AuraCameraPermission.DENIED))

        val state = runSuspend { manager.start(camera.id, userInitiated = true) }

        assertEquals(AuraCameraManagerStatus.PERMISSION_REQUIRED, state.status)
        assertEquals(AuraCameraPermission.DENIED, state.permission)
        assertEquals(0, adapter.openCalls)
    }

    @Test
    fun grantedPermissionStartsCaptureAndRequiresVisibleIndicator() {
        val adapter = FakeAdapter(listOf(camera))
        val manager = AuraCameraManager(adapter, FakePermissionGate(AuraCameraPermission.GRANTED))

        val state = runSuspend { manager.start(camera.id, userInitiated = true, format = format) }

        assertEquals(AuraCameraManagerStatus.RUNNING, state.status)
        assertEquals(camera.id, state.selectedDeviceId)
        assertTrue(state.visibleIndicatorRequired)
        assertEquals(1, adapter.openCalls)
        assertNotNull(runSuspend { manager.captureFrame() })
    }

    @Test
    fun unsupportedFormatIsRejectedBeforeOpening() {
        val adapter = FakeAdapter(listOf(camera))
        val manager = AuraCameraManager(adapter, FakePermissionGate(AuraCameraPermission.GRANTED))

        val state = runSuspend {
            manager.start(camera.id, userInitiated = true, format = AuraCameraFormat(1920, 1080, 60))
        }

        assertEquals(AuraCameraManagerStatus.ERROR, state.status)
        assertEquals(0, adapter.openCalls)
    }

    @Test
    fun pauseClosesCameraAndPreventsFrameCapture() {
        val adapter = FakeAdapter(listOf(camera))
        val manager = AuraCameraManager(adapter, FakePermissionGate(AuraCameraPermission.GRANTED))
        runSuspend { manager.start(camera.id, userInitiated = true) }

        val state = runSuspend { manager.pause() }

        assertEquals(AuraCameraManagerStatus.PAUSED, state.status)
        assertEquals(1, adapter.closeCalls)
        assertNull(runSuspend { manager.captureFrame() })
    }

    @Test
    fun unpluggedSelectedCameraIsClosedAndMarkedDisconnected() {
        val adapter = FakeAdapter(listOf(camera))
        val manager = AuraCameraManager(adapter, FakePermissionGate(AuraCameraPermission.GRANTED))
        runSuspend { manager.start(camera.id, userInitiated = true) }
        adapter.devices = emptyList()

        val state = runSuspend { manager.refreshDevices() }

        assertEquals(AuraCameraManagerStatus.DEVICE_DISCONNECTED, state.status)
        assertNull(state.selectedDeviceId)
        assertEquals(1, adapter.closeCalls)
    }

    @Test
    fun captureFailureMovesManagerToErrorAndClosesDevice() {
        val adapter = FakeAdapter(listOf(camera)).apply { failCapture = true }
        val manager = AuraCameraManager(adapter, FakePermissionGate(AuraCameraPermission.GRANTED))
        runSuspend { manager.start(camera.id, userInitiated = true) }

        val frame = runSuspend { manager.captureFrame() }

        assertNull(frame)
        assertEquals(AuraCameraManagerStatus.ERROR, manager.state().status)
        assertEquals(1, adapter.closeCalls)
    }

    private class FakeAdapter(var devices: List<AuraCameraDevice>) : AuraCameraAdapter {
        var openCalls = 0
        var closeCalls = 0
        var failCapture = false

        override suspend fun listDevices(): List<AuraCameraDevice> = devices
        override suspend fun open(deviceId: String, format: AuraCameraFormat?) {
            check(devices.any { it.id == deviceId })
            openCalls++
        }
        override suspend fun close() { closeCalls++ }
        override suspend fun captureFrame(): AuraVisionFrame? {
            if (failCapture) error("simulated capture failure")
            return AuraVisionFrame(2, 2, 1L, Any())
        }
    }

    private class FakePermissionGate(
        private val result: AuraCameraPermission
    ) : AuraCameraPermissionGate {
        override suspend fun requestPermission(device: AuraCameraDevice): AuraCameraPermission = result
    }

    /** Test-only runner for immediate fake suspend implementations; no coroutine dependency needed. */
    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return outcome!!.getOrThrow()
    }
}
