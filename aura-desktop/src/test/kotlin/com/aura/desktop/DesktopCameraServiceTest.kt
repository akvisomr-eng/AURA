package com.aura.desktop

import com.aura.core.vision.AuraCameraAdapter
import com.aura.core.vision.AuraCameraDevice
import com.aura.core.vision.AuraCameraPermission
import com.aura.core.vision.AuraCameraPermissionGate
import com.aura.core.vision.AuraCameraTransport
import com.aura.core.vision.AuraVisionFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopCameraServiceTest {
    @Test
    fun missingNativeProviderIsReportedWithoutOpeningHardware() {
        val service = DesktopCameraService(emptyList())

        val availability = service.availability()

        assertFalse(availability.available)
        assertNull(availability.backendName)
        assertTrue(availability.message.contains("belum tersedia"))
        assertNull(service.state())
    }

    @Test
    fun registeredProviderExposesManagerWithoutStartingCapture() {
        val adapter = FakeDesktopCameraAdapter()
        val provider = object : DesktopCameraBackendProvider {
            override fun createAdapter(): AuraCameraAdapter = adapter
            override fun createPermissionGate() = AuraCameraPermissionGate { AuraCameraPermission.GRANTED }
            override fun displayName() = "Fake test backend"
        }
        val service = DesktopCameraService(listOf(provider))

        assertTrue(service.availability().available)
        assertEquals("Fake test backend", service.availability().backendName)
        assertNotNull(service.state())
        assertEquals(0, adapter.openCount)
        assertEquals(0, adapter.listCount)
    }

    private class FakeDesktopCameraAdapter : AuraCameraAdapter {
        var openCount = 0
        var listCount = 0

        override suspend fun listDevices(): List<AuraCameraDevice> {
            listCount++
            return listOf(AuraCameraDevice("fake-0", "Fake camera", AuraCameraTransport.UNKNOWN))
        }

        override suspend fun open(deviceId: String, format: com.aura.core.vision.AuraCameraFormat?) {
            openCount++
        }

        override suspend fun close() = Unit

        override suspend fun captureFrame(): AuraVisionFrame? = null
    }
}
