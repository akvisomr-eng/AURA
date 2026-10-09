package com.aura.app.vision

import android.content.Context
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.aura.core.vision.AuraCameraAdapter
import com.aura.core.vision.AuraCameraDevice
import com.aura.core.vision.AuraCameraFormat
import com.aura.core.vision.AuraCameraTransport
import com.aura.core.vision.AuraVisionCapability
import com.aura.core.vision.AuraVisionFrame
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * CameraX-backed Android adapter. It only sees cameras exposed by the Android
 * camera provider; USB webcam support therefore depends on device/OEM UVC
 * integration. Runtime CAMERA permission must be granted by the host first.
 *
 * Frames are copied from the luma plane before ImageProxy is closed, so vision
 * consumers never receive a recycled CameraX buffer.
 */
class AuraAndroidCameraAdapter(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) : AuraCameraAdapter {
    private val appContext = context.applicationContext
    private val providerFuture = ProcessCameraProvider.getInstance(appContext)
    @Volatile private var provider: ProcessCameraProvider? = null
    @Volatile private var latestFrame: AuraVisionFrame? = null
    @Volatile private var active = false
    private val analysisExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private suspend fun cameraProvider(): ProcessCameraProvider {
        provider?.let { return it }
        return suspendCoroutine { continuation ->
            providerFuture.addListener({
                try {
                    val resolved = providerFuture.get()
                    provider = resolved
                    continuation.resume(resolved)
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(appContext))
        }
    }

    override suspend fun listDevices(): List<AuraCameraDevice> {
        val cameraProvider = cameraProvider()
        return cameraProvider.availableCameraInfos.map { info ->
            val cameraId = runCatching { Camera2CameraInfo.from(info).cameraId }
                .getOrDefault("camera-" + info.hashCode())
            val isExternal = info.lensFacing == CameraSelector.LENS_FACING_EXTERNAL
            AuraCameraDevice(
                id = cameraId,
                displayName = (if (isExternal) "External camera (" else "Camera (") + cameraId + ")",
                transport = if (isExternal) AuraCameraTransport.USB_UVC else AuraCameraTransport.BUILT_IN,
                isConnected = true,
                capabilities = setOf(AuraVisionCapability.PREVIEW)
            )
        }.distinctBy { it.id }
    }

    override suspend fun open(deviceId: String, format: AuraCameraFormat?) {
        check(!active) { "Camera adapter is already open" }
        val cameraProvider = cameraProvider()
        val cameraInfo = cameraProvider.availableCameraInfos.firstOrNull {
            runCatching { Camera2CameraInfo.from(it).cameraId == deviceId }.getOrDefault(false)
        } ?: error("Camera is not available: " + deviceId)

        val selector = CameraSelector.Builder()
            .addCameraFilter { infos ->
                infos.filter {
                    runCatching { Camera2CameraInfo.from(it).cameraId == deviceId }.getOrDefault(false)
                }
            }
            .build()
        check(runCatching { selector.filter(listOf(cameraInfo)).isNotEmpty() }.getOrDefault(false)) {
            "Camera selector could not resolve device: " + deviceId
        }

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor, ::onFrame)

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        latestFrame = null
        active = true
    }

    private fun onFrame(proxy: ImageProxy) {
        try {
            val plane = proxy.planes.firstOrNull() ?: return
            val source: ByteBuffer = plane.buffer.duplicate()
            val bytes = ByteArray(source.remaining())
            source.get(bytes)
            latestFrame = AuraVisionFrame(
                width = proxy.width,
                height = proxy.height,
                capturedAtEpochMillis = System.currentTimeMillis(),
                platformImage = bytes
            )
        } finally {
            proxy.close()
        }
    }

    override suspend fun captureFrame(): AuraVisionFrame? {
        check(active) { "Camera adapter is not open" }
        return latestFrame
    }

    override suspend fun close() {
        provider?.unbindAll()
        active = false
        latestFrame = null
    }

    fun release() {
        runCatching { provider?.unbindAll() }
        active = false
        latestFrame = null
        analysisExecutor.shutdownNow()
    }
}
