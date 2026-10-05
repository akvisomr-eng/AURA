package com.aura.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.aura.core.AuraRuntime
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var sceneText: TextView
    private lateinit var listenButton: TextView

    private val auraRuntime = AuraRuntime()

    private val cameraRequestCode = 1701
    private val microphoneRequestCode = 1702
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    @Volatile
    private var latestScene = "Belum ada objek terdeteksi."

    private val listening = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this, this)
        render()
        ensureCamera()
    }

    private fun render() {
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(0xFF05070A.toInt())
        }

        val root = FrameLayout(this)

        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val topPanel = TextView(this).apply {
            text = "AURA 1.8\nCOGNITIVE RUNTIME"
            textSize = 20f
            setTextColor(0xFF7FE7FF.toInt())
            setPadding(28, 24, 28, 18)
            setBackgroundColor(0xB805070A.toInt())
        }
        root.addView(
            topPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP }
        )

        sceneText = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFFE5EDF5.toInt())
            setPadding(24, 14, 24, 14)
            text = latestScene
            setBackgroundColor(0xB805070A.toInt())
        }
        root.addView(
            sceneText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 112
            }
        )

        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFB8C4D0.toInt())
            setPadding(24, 8, 24, 8)
            text = "Menyiapkan kamera..."
            setBackgroundColor(0xB805070A.toInt())
        }
        root.addView(
            statusText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 72
            }
        )

        listenButton = TextView(this).apply {
            text = "●  BICARA DENGAN AURA"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFF061016.toInt())
            setBackgroundColor(0xFF7FE7FF.toInt())
            setPadding(24, 18, 24, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleListening() }
        }
        root.addView(
            listenButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                64
            ).apply {
                gravity = Gravity.BOTTOM
                leftMargin = 24
                rightMargin = 24
                bottomMargin = 16
            }
        )

        setContentView(root)
    }

    private fun ensureCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraRequestCode
            )
        }
    }

    private fun startCamera() {
        statusText.text = "Kamera aktif • perception online"

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ::analyzeFrame) }

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalGetImage::class)
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(image)
            .addOnSuccessListener { detectedObjects ->
                if (detectedObjects.isEmpty()) {
                    latestScene = "AURA melihat: belum ada objek yang dikenali."
                } else {
                    val labels = detectedObjects.flatMap { obj ->
                        obj.labels.map { label -> label.text }
                    }.distinct().take(4)

                    latestScene = if (labels.isEmpty()) {
                        "AURA melihat " + detectedObjects.size + " objek."
                    } else {
                        "AURA melihat: " + labels.joinToString(", ")
                    }
                }
                runOnUiThread { sceneText.text = latestScene }
            }
            .addOnFailureListener {
                latestScene = "Vision aktif • menunggu hasil stabil."
                runOnUiThread { sceneText.text = latestScene }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun toggleListening() {
        if (listening.get()) {
            speechRecognizer?.stopListening()
            listening.set(false)
            listenButton.text = "●  BICARA DENGAN AURA"
            statusText.text = "Perception online"
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                microphoneRequestCode
            )
            return
        }

        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "Speech recognition tidak tersedia di perangkat ini."
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening.set(true)
                    listenButton.text = "■  AURA MENDENGARKAN"
                    statusText.text = "Mendengarkan..."
                }

                override fun onBeginningOfSpeech() {
                    statusText.text = "Mendengarkan suara Anda..."
                }

                override fun onEndOfSpeech() {
                    listening.set(false)
                    listenButton.text = "●  BICARA DENGAN AURA"
                }

                override fun onResults(results: Bundle?) {
                    val heard = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (heard.isNotEmpty()) handleCommand(heard)
                }

                override fun onError(error: Int) {
                    listening.set(false)
                    listenButton.text = "●  BICARA DENGAN AURA"
                    statusText.text = "Siap • tekan tombol untuk berbicara lagi"
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun handleCommand(command: String) {
        val turn = auraRuntime.respond(command, latestScene)
        statusText.text = "AURA • " + turn.intent.name
        sceneText.text = turn.responseText
        speak(turn.responseText)
    }

    private fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aura-response")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale("id", "ID")
            textToSpeech?.setSpeechRate(0.95f)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            cameraRequestCode -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                } else {
                    statusText.text = "Camera permission diperlukan untuk Cognitive Vision."
                }
            }
            microphoneRequestCode -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    startListening()
                } else {
                    statusText.text = "Microphone permission diperlukan untuk voice interface."
                }
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        detector.close()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
