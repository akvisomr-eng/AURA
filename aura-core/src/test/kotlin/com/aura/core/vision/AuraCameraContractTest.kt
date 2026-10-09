package com.aura.core.vision

import kotlin.test.Test
import kotlin.test.assertFailsWith

class AuraCameraContractTest {
    @Test
    fun runningCameraRequiresGrantedPermission() {
        assertFailsWith<IllegalArgumentException> {
            AuraVisionSessionState(
                selectedDeviceId = "usb-camera-1",
                permission = AuraCameraPermission.DENIED,
                isRunning = true
            )
        }
    }

    @Test
    fun runningCameraRequiresSelectedDevice() {
        assertFailsWith<IllegalArgumentException> {
            AuraVisionSessionState(
                permission = AuraCameraPermission.GRANTED,
                isRunning = true
            )
        }
    }

    @Test
    fun cameraFormatMustHavePositiveDimensionsAndFrameRate() {
        assertFailsWith<IllegalArgumentException> { AuraCameraFormat(0, 720, 30) }
        assertFailsWith<IllegalArgumentException> { AuraCameraFormat(1280, 720, 0) }
    }
}
