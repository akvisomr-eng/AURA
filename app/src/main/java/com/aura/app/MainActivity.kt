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
import android.view.View
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private var latestScene = "Saya sedang melihat lingkungan di depan Anda."

    private val listening = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this, this)
        render()
        ensureCamera()
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun adaptiveScale(widthPx: Int): Float {
        val widthDp = widthPx / resources.displayMetrics.density
        return min(1.0f, max(0.82f, widthDp / 400f))
    }

    private fun roundedBackground(color: Int, radiusDp: Float) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun render() {
        root = FrameLayout(this).apply {
            setBackgroundColor(0xFF05070A.toInt())
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(0xFF05070A.toInt())
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        topPanel = TextView(this).apply {
            text = "AURA 1.8  •  AKTIF"
            textSize = 18f
            maxLines = 2
            setTextColor(0xFF8BEAFF.toInt())
            setPadding(dp(18f), dp(12f), dp(18f), dp(12f))
            background = roundedBackground(0xC905070A.toInt(), 18f)
            gravity = Gravity.CENTER_VERTICAL
        }

        sceneText = TextView(this).apply {
            textSize = 16f
            maxLines = 4
            setTextColor(0xFFF2F7FA.toInt())
            setPadding(dp(18f), dp(14f), dp(18f), dp(14f))
            background = roundedBackground(0xD905070A.toInt(), 18f)
            text = latestScene
        }

        statusText = TextView(this).apply {
            textSize = 13f
            maxLines = 2
            setTextColor(0xFFB9C6D0.toInt())
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            background = roundedBackground(0xB505070A.toInt(), 14f)
            text = "Menyiapkan penglihatan AURA…"
        }

        listenButton = TextView(this).apply {
            text = "●  BICARA DENGAN AURA"
            textSize = 15f
            maxLines = 1
            gravity = Gravity.CENTER
            setTextColor(0xFF061016.toInt())
            background = roundedBackground(0xFF8BEAFF.toInt(), 22f)
            setPadding(dp(18f), dp(14f), dp(18f), dp(14f))
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleListening() }
        }

        root.addView(topPanel)
        root.addView(sceneText)
        root.addView(statusText)
        root.addView(listenButton)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            updateResponsiveLayout(root.width, insets.getInsets(WindowInsetsCompat.Type.systemBars()))
            insets
        }

        root.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            updateResponsiveLayout(right - left, null)
        }

        setContentView(root)
    }

    private fun updateResponsiveLayout(widthPx: Int, systemInsets: android.graphics.Insets?) {
        if (widthPx <= 0) return
        val scale = adaptiveScale(widthPx)
        val widthDp = widthPx / resources.displayMetrics.density
        val compact = widthDp < 360f
        val horizontal = dp(if (compact) 12f else 18f)
        val top = systemInsets?.top ?: 0
        val bottom = systemInsets?.bottom ?: 0

        topPanel.textSize = 17f * scale
        sceneText.textSize = 16f * scale
        statusText.textSize = 13f * scale
        listenButton.textSize = 15f * scale

        (topPanel.layoutParams as FrameLayout.LayoutParams).apply {
            width = FrameLayout.LayoutParams.WRAP_CONTENT
            height = FrameLayout.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            leftMargin = horizontal
            topMargin = top + dp(10f)
            topPanel.layoutParams = this
        }

        (sceneText.layoutParams as FrameLayout.LayoutParams).apply {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = FrameLayout.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM
            leftMargin = horizontal
            rightMargin = horizontal
            bottomMargin = bottom + dp(if (compact) 92f else 104f)
            sceneText.layoutParams = this
        }

        (statusText.layoutParams as FrameLayout.LayoutParams).apply {
            width = FrameLayout.LayoutParams.WRAP_CONTENT
            height = FrameLayout.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = horizontal
            bottomMargin = bottom + dp(if (compact) 68f else 78f)
            statusText.layoutParams = this
        }

        (listenButton.layoutParams as FrameLayout.LayoutParams).apply {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = dp(if (compact) 52f else 58f)
            gravity = Gravity.BOTTOM
            leftMargin = horizontal
            rightMargin = horizontal
            bottomMargin = bottom + dp(12f)
            listenButton.layoutParams = this
        }
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
        statusText.text = "Penglihatan aktif • AURA sedang mengamati"

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
                    latestScene = "Saya belum mengenali objek yang cukup jelas."
                } else {
                    val labels = detectedObjects.flatMap { obj ->
                        obj.labels.map { label -> label.text }
                    }.distinct().take(4)

                    latestScene = if (labels.isEmpty()) {
                        "Saya melihat " + detectedObjects.size + " objek di depan Anda."
                    } else {
                        "Saya melihat " + labels.joinToString(", ") + "."
                    }
                }
                runOnUiThread { sceneText.text = latestScene }
            }
            .addOnFailureListener {
                latestScene = "Penglihatan aktif. Saya sedang menunggu hasil yang lebih stabil."
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
            statusText.text = "Penglihatan aktif • siap mendengarkan"
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
                    statusText.text = "Silakan bicara dalam bahasa Indonesia…"
                }

                override fun onBeginningOfSpeech() {
                    statusText.text = "Saya mendengarkan…"
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
                    statusText.text = "Saya belum menangkapnya. Silakan coba lagi."
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
        val naturalText = text
            .replace("AURA 1.8 aktif.", "AURA satu titik delapan aktif.")
            .replace("•", ", ")
            .trim()

        textToSpeech?.speak(
            naturalText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "aura-id-response"
        )
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            statusText.text = "Mesin suara belum siap."
            return
        }

        val indonesia = Locale("id", "ID")
        val result = textToSpeech?.setLanguage(indonesia)

        val indonesianVoices = textToSpeech?.voices
            ?.filter { it.locale.language == "id" }
            .orEmpty()

        val preferredVoice: Voice? =
            indonesianVoices.firstOrNull {
                it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS) == true
            } ?: indonesianVoices.firstOrNull()

        if (preferredVoice != null) {
            textToSpeech?.voice = preferredVoice
        }

        // Sedikit lebih lambat dan sedikit lebih tinggi agar percakapan terdengar
        // lebih natural dalam bahasa Indonesia. Kualitas akhir tetap mengikuti
        // mesin TTS/voice Indonesia yang terpasang di perangkat.
        textToSpeech?.setSpeechRate(0.91f)
        textToSpeech?.setPitch(1.02f)

        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            statusText.text = "Suara Indonesia belum tersedia di mesin TTS perangkat."
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
