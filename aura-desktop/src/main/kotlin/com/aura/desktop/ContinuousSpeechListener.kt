package com.aura.desktop

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Desktop voice pipeline.
 *
 * Wake phrase handling currently uses Indonesian cloud transcription after local
 * voice-activity segmentation; it is not a fully offline acoustic wake-word model.
 * Audio segments are uploaded only when AURA_SPEECH_API_KEY is configured.
 */
class ContinuousSpeechListener(
    private val apiKeyProvider: () -> String?,
    private val onText: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val client: CloudSpeechRecognitionClient = CloudSpeechRecognitionClient()
) : AutoCloseable {
    @Volatile private var format = AudioFormat(16_000f, 16, 1, true, false)
    @Volatile private var running = false
    @Volatile private var line: TargetDataLine? = null
    @Volatile private var conversationUntilMs = 0L
    private var worker: Thread? = null

    @Synchronized fun start() {
        if (running) return
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() }
        if (key == null) {
            onStatus("Siaga suara belum aktif: konfigurasi AURA_SPEECH_API_KEY. Mikrofon tidak mengirim audio tanpa kunci API.")
            return
        }
        running = true
        worker = thread(name = "aura-microphone-listener", isDaemon = true) { captureLoop(key) }
        onStatus("AURA siaga. Ucapkan “AURA” untuk memulai percakapan. Deteksi kata pemicu memakai transkripsi cloud.")
    }

    /**
     * Starts capture if needed and temporarily accepts the next utterance without
     * requiring the user to say the wake word after clicking the desktop avatar.
     */
    @Synchronized fun beginConversation(): Boolean {
        if (!running) start()
        if (!running) return false
        conversationUntilMs = System.currentTimeMillis() + CONVERSATION_WINDOW_MS
        onStatus("Mode bicara aktif selama 60 detik. Silakan bicara.")
        return true
    }

    @Synchronized fun stop() {
        running = false
        conversationUntilMs = 0L
        runCatching { line?.stop(); line?.close() }
        line = null
        worker?.interrupt()
        worker = null
        onStatus("Mikrofon AURA dimatikan.")
    }

    override fun close() = stop()

    private fun captureLoop(apiKey: String) {
        var noiseFloor = 180.0
        var speech = ByteArrayOutputStream()
        var speechFrames = 0
        var quietFrames = 0
        var active = false
        try {
            // Windows microphones commonly expose 44.1/48 kHz rather than 16 kHz.
            // Try several PCM rates and package the selected rate in the WAV header.
            var openedLine: TargetDataLine? = null
            var selectedFormat: AudioFormat? = null
            for (rate in CAPTURE_SAMPLE_RATES) {
                val candidate = AudioFormat(rate, 16, 1, true, false)
                val candidateLine = runCatching {
                    AudioSystem.getTargetDataLine(candidate).also {
                        val chunkBytes = maxOf(candidate.frameSize, (candidate.sampleRate * 0.02f).toInt() * candidate.frameSize)
                        it.open(candidate, chunkBytes * 8)
                    }
                }.getOrNull()
                if (candidateLine != null) {
                    openedLine = candidateLine
                    selectedFormat = candidate
                    break
                }
            }
            val input = openedLine ?: throw IllegalStateException(
                "Tidak dapat membuka mikrofon dengan format PCM 16/44,1/48/22,05 kHz. Periksa izin mikrofon dan perangkat input Windows."
            )
            format = selectedFormat!!
            line = input
            val frameBytes = maxOf(format.frameSize, (format.sampleRate * 0.02f).toInt() * format.frameSize)
            val frame = ByteArray(frameBytes)
            input.start()
            onStatus("Mikrofon aktif (${format.sampleRate.toInt()} Hz).")
            while (running && !Thread.currentThread().isInterrupted) {
                val read = input.read(frame, 0, frame.size)
                if (read <= 0) continue
                val rms = rms16(frame, read)
                val threshold = maxOf(500.0, noiseFloor * 3.2)
                val voiced = rms > threshold
                if (!active && !voiced) noiseFloor = (noiseFloor * 0.96 + rms * 0.04).coerceIn(40.0, 1800.0)
                if (voiced) {
                    if (!active) {
                        active = true
                        speech = ByteArrayOutputStream()
                        speechFrames = 0
                        quietFrames = 0
                    }
                    speech.write(frame, 0, read)
                    speechFrames++
                    quietFrames = 0
                } else if (active) {
                    speech.write(frame, 0, read)
                    speechFrames++
                    quietFrames++
                }
                val finished = active && (quietFrames >= 45 || speechFrames >= 1000)
                if (finished) {
                    val audioBytes = speech.toByteArray()
                    val sampleCount = audioBytes.size / 2
                    active = false
                    val minimumSamples = (MIN_SPEECH_SAMPLES * format.sampleRate / 16_000f).toInt()
                    if (sampleCount >= minimumSamples) transcribeAsync(audioBytes, apiKey)
                    speech = ByteArrayOutputStream()
                    speechFrames = 0
                    quietFrames = 0
                }
            }
        } catch (e: Exception) {
            if (running) onStatus("Listener mikrofon berhenti: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { line?.stop(); line?.close() }
            line = null
            running = false
        }
    }

    private fun transcribeAsync(pcm: ByteArray, key: String) {
        thread(name = "aura-cloud-transcription", isDaemon = true) {
            val wav = wavBytes(pcm)
            val file = runCatching {
                Files.createTempFile("aura-speech-", ".wav").also { Files.write(it, wav) }
            }.getOrElse {
                onStatus("Audio sementara gagal disiapkan: ${it.message}")
                return@thread
            }
            try {
                // Force Indonesian by default; allow an explicit override for multilingual use.
                val languageCode = System.getenv("AURA_SPEECH_LANGUAGE")
                    ?.takeIf { it.isNotBlank() } ?: "id"
                val language = SpeechLanguage.common.firstOrNull { it.code == languageCode }
                val recognized = client.transcribe(file, key, language).trim()
                if (recognized.isNotBlank()) routeRecognizedText(recognized)
            } catch (e: Exception) {
                onStatus("Transkripsi ucapan gagal: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                runCatching { Files.deleteIfExists(file) }
            }
        }
    }

    private fun routeRecognizedText(text: String) {
        val now = System.currentTimeMillis()
        val normalized = text.trim().replace(Regex("""^[,.:;!?\s]+"""), "")
        val wakeMatch = Regex("""(?i)^aura\b[,.!?;:]?\s*""").find(normalized)
        if (wakeMatch != null) {
            conversationUntilMs = now + CONVERSATION_WINDOW_MS
            val command = normalized.substring(wakeMatch.range.last + 1).trim()
            onStatus(if (command.isBlank()) "AURA terbangun. Silakan lanjutkan perintah Anda." else "Kata pemicu terdeteksi; AURA memproses perintah.")
            if (command.isNotBlank()) onText(command)
            return
        }
        if (now <= conversationUntilMs) {
            conversationUntilMs = now + CONVERSATION_WINDOW_MS
            onText(normalized)
        } else {
            // Do not act on ordinary background conversation unless the wake phrase was heard.
            onStatus("AURA tetap siaga. Ucapkan “AURA” untuk memulai percakapan.")
        }
    }

    private fun wavBytes(pcm: ByteArray): ByteArray {
        val stream = javax.sound.sampled.AudioInputStream(
            ByteArrayInputStream(pcm), format, (pcm.size / format.frameSize).toLong()
        )
        return ByteArrayOutputStream().use { out ->
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out)
            out.toByteArray()
        }
    }

    private fun rms16(bytes: ByteArray, length: Int): Double {
        if (length < 2) return 0.0
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toInt()
            sum += sample.toDouble() * sample
            count++
            i += 2
        }
        return if (count == 0) 0.0 else sqrt(sum / count)
    }

    companion object {
        // A short utterance such as the wake word “AURA” is commonly under one second.
        // The previous 25,000-sample gate silently discarded these utterances.
        internal const val MIN_SPEECH_SAMPLES = 6_400 // reference threshold: 400 ms at 16 kHz
        internal val CAPTURE_SAMPLE_RATES = listOf(16_000f, 44_100f, 48_000f, 22_050f)
        private const val CONVERSATION_WINDOW_MS = 60_000L
    }

    init {
        // The desktop assistant should enter hands-free standby without a button press.
        start()
    }
}
