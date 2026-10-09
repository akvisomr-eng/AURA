package com.aura.core.vision

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuraVisionControllerTest {
    private class FakeAdapter : AuraCameraAdapter {
        var opened = false
        var closed = false
        override suspend fun listDevices() = listOf(
            AuraCameraDevice("usb-1", "USB Camera", AuraCameraTransport.USB_UVC),
            AuraCameraDevice("gone", "Disconnected", AuraCameraTransport.BUILT_IN, isConnected = false)
        )
        override suspend fun open(deviceId: String, format: AuraCameraFormat?) { opened = true }
        override suspend fun close() { closed = true; opened = false }
        override suspend fun captureFrame() =
            if (opened) AuraVisionFrame(640, 480, 1L, Any()) else null
    }

    @Test
    fun onlyConnectedDevicesAreDiscoverableAndPermissionIsRequired() {
        val adapter = FakeAdapter()
        val controller = AuraVisionController(adapter)
        assertEquals(listOf("usb-1"), runSuspend { controller.discoverDevices() }.map { it.id })
        runSuspend { controller.selectDevice("usb-1") }
        assertFailsWith<IllegalStateException> { runSuspend { controller.start() } }
        assertTrue(!adapter.opened)
    }

    @Test
    fun pauseStopsFrameDeliveryAndStopClosesAdapter() {
        val adapter = FakeAdapter()
        val controller = AuraVisionController(adapter)
        controller.updatePermission(AuraCameraPermission.GRANTED)
        runSuspend { controller.selectDevice("usb-1") }
        runSuspend { controller.start(AuraCameraFormat(640, 480, 30)) }
        assertEquals(640, runSuspend { controller.captureFrame() }?.width)
        runSuspend { controller.pause() }
        assertNull(runSuspend { controller.captureFrame() })
        runSuspend { controller.resume() }
        assertEquals(480, runSuspend { controller.captureFrame() }?.height)
        runSuspend { controller.stop() }
        assertTrue(adapter.closed)
        assertEquals(false, controller.state().isRunning)
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var value: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { value = result }
        })
        return value!!.getOrThrow()
    }
}
