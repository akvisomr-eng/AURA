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
 * Opt-in continuous microphone listener. A lightweight adaptive energy gate
 * discards quiet/noise-only frames; only speech-like segments are sent to cloud.
 * The caller must explicitly start it and provide the speech API key.
 */
class ContinuousSpeechListener(
    private val apiKeyProvider: () -> String?,
    private val onText: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val client: CloudSpeechRecognitionClient = CloudSpeechRecognitionClient()
) : AutoCloseable {
    private val format = AudioFormat(16_000f, 16, 1, true, false)
    @Volatile private var running = false
    @Volatile private var line: TargetDataLine? = null
    private var worker: Thread? = null

    @Synchronized fun start() {
        if (running) return
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() }
        if (key == null) {
            onStatus("Pengenalan cloud belum aktif: atur AURA_SPEECH_API_KEY. Tidak ada biaya yang dibuat otomatis.")
            return
        }
        val info = DataLine.Info(TargetDataLine::class.java, format)
        if (!AudioSystem.isLineSupported(info)) {
            onStatus("Mikrofon tidak mendukung format audio 16 kHz. Listener belum dimulai.")
            return
        }
        running = true
        worker = thread(name = "aura-microphone-listener", isDaemon = true) { captureLoop(key) }
        onStatus("Mikrofon aktif. AURA menyaring suara pelan; segmen ucapan akan dikirim ke cloud.")
    }

    @Synchronized fun stop() {
        running = false
        runCatching { line?.stop(); line?.close() }
        line = null
        worker?.interrupt()
        worker = null
        onStatus("Mikrofon AURA dihentikan.")
    }

    override fun close() = stop()

    private fun captureLoop(apiKey: String) {
        val frameBytes = 640 // 20 ms at 16 kHz, mono, 16-bit PCM
        val frame = ByteArray(frameBytes)
        var noiseFloor = 180.0
        var speech = ByteArrayOutputStream()
        var speechFrames = 0
        var quietFrames = 0
        var active = false
        try {
            val input = AudioSystem.getTargetDataLine(format)
            line = input
            input.open(format, frameBytes * 8)
            input.start()
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
                    val frameCount = audioBytes.size / 2
                    active = false
                    if (frameCount >= 25_000) {
                        transcribeAsync(audioBytes, apiKey)
                    }
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
                val language = System.getenv("AURA_SPEECH_LANGUAGE")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { code -> SpeechLanguage.common.firstOrNull { it.code == code } }
                val text = client.transcribe(file, key, language)
                if (text.isNotBlank()) onText(text)
            } catch (e: Exception) {
                onStatus("Transkripsi cloud gagal: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                runCatching { Files.deleteIfExists(file) }
            }
        }
    }

    private fun wavBytes(pcm: ByteArray): ByteArray {
        val pcmFormat = format
        val stream = javax.sound.sampled.AudioInputStream(
            ByteArrayInputStream(pcm), pcmFormat, (pcm.size / pcmFormat.frameSize).toLong()
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
}
