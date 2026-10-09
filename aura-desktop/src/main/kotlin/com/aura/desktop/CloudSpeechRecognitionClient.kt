package com.aura.desktop

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Cloud transcription client for AURA's voice pipeline.
 *
 * The caller must obtain explicit microphone consent, segment speech locally,
 * and pass only the resulting audio segment here. API keys are read from the
 * environment by the application and must never be committed or logged.
 */
class CloudSpeechRecognitionClient(
    private val endpoint: String = System.getenv("AURA_SPEECH_API_URL")
        ?.takeIf { it.isNotBlank() } ?: "https://api.openai.com/v1/audio/transcriptions",
    private val model: String = System.getenv("AURA_SPEECH_MODEL")
        ?.takeIf { it.isNotBlank() } ?: "gpt-4o-mini-transcribe",
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()
) {
    fun transcribe(audioFile: Path, apiKey: String, language: SpeechLanguage? = null): String {
        require(apiKey.isNotBlank()) { "Kunci API pengenalan ucapan belum dikonfigurasi." }
        require(Files.isRegularFile(audioFile)) { "Berkas audio tidak ditemukan." }
        val size = Files.size(audioFile)
        require(size in 1..25_000_000) { "Ukuran audio harus 1 byte hingga 25 MB." }

        val boundary = "AuraSpeechBoundary" + System.nanoTime().toString(16)
        val bytes = Files.readAllBytes(audioFile)
        val filename = audioFile.fileName.toString().replace("\"", "")
        val mime = when (audioFile.fileName.toString().substringAfterLast('.', "").lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> "audio/webm"
            else -> "application/octet-stream"
        }
        val body = MultipartBody(boundary)
            .field("model", model)
            .apply { language?.let { field("language", it.code) } }
            .file("file", filename, mime, bytes)
            .build()
        val request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(90))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            val safeMessage = response.body().take(500)
            throw IllegalStateException("Layanan pengenalan ucapan mengembalikan HTTP ${response.statusCode()}: $safeMessage")
        }
        return Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            .find(response.body())?.groupValues?.get(1)?.let(::decodeJsonString)
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("Respons transkripsi cloud tidak berisi teks yang dapat dibaca.")
    }

    private fun decodeJsonString(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val ch = value[i++]
            if (ch != '\\' || i >= value.length) {
                append(ch)
                continue
            }
            when (val escaped = value[i++]) {
                '"', '\\', '/' -> append(escaped)
                'b' -> append('\b')
                'f' -> append('\u000C')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                'u' -> {
                    if (i + 4 <= value.length) {
                        append(value.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: '\uFFFD')
                        i += 4
                    }
                }
                else -> append(escaped)
            }
        }
    }
}

data class SpeechLanguage(val code: String, val displayName: String) {
    companion object {
        /** ISO-639-1 language codes supported by the transcription API. */
        val common: List<SpeechLanguage> = listOf(
            SpeechLanguage("id", "Bahasa Indonesia"),
            SpeechLanguage("en", "English"),
            SpeechLanguage("ms", "Bahasa Melayu"),
            SpeechLanguage("jv", "Basa Jawa"),
            SpeechLanguage("su", "Basa Sunda"),
            SpeechLanguage("zh", "中文 / Mandarin"),
            SpeechLanguage("ja", "日本語"),
            SpeechLanguage("ko", "한국어"),
            SpeechLanguage("th", "ไทย"),
            SpeechLanguage("vi", "Tiếng Việt"),
            SpeechLanguage("tl", "Filipino"),
            SpeechLanguage("hi", "हिन्दी"),
            SpeechLanguage("ar", "العربية"),
            SpeechLanguage("bn", "বাংলা"),
            SpeechLanguage("ur", "اردو"),
            SpeechLanguage("ta", "தமிழ்"),
            SpeechLanguage("te", "తెలుగు"),
            SpeechLanguage("fr", "Français"),
            SpeechLanguage("de", "Deutsch"),
            SpeechLanguage("es", "Español"),
            SpeechLanguage("pt", "Português"),
            SpeechLanguage("it", "Italiano"),
            SpeechLanguage("nl", "Nederlands"),
            SpeechLanguage("ru", "Русский"),
            SpeechLanguage("tr", "Türkçe"),
            SpeechLanguage("pl", "Polski"),
            SpeechLanguage("uk", "Українська"),
            SpeechLanguage("sv", "Svenska"),
            SpeechLanguage("fi", "Suomi"),
            SpeechLanguage("el", "Ελληνικά"),
            SpeechLanguage("he", "עברית"),
            SpeechLanguage("fa", "فارسی"),
            SpeechLanguage("sw", "Kiswahili"),
            SpeechLanguage("ca", "Català"),
            SpeechLanguage("ro", "Română"),
            SpeechLanguage("hu", "Magyar"),
            SpeechLanguage("cs", "Čeština"),
            SpeechLanguage("da", "Dansk"),
            SpeechLanguage("no", "Norsk"),
            SpeechLanguage("hr", "Hrvatski"),
            SpeechLanguage("sr", "Српски"),
            SpeechLanguage("sk", "Slovenčina"),
            SpeechLanguage("bg", "Български"),
            SpeechLanguage("et", "Eesti"),
            SpeechLanguage("lv", "Latviešu"),
            SpeechLanguage("lt", "Lietuvių"),
            SpeechLanguage("is", "Íslenska"),
            SpeechLanguage("az", "Azərbaycan dili"),
            SpeechLanguage("kk", "Қазақ тілі"),
            SpeechLanguage("uz", "Oʻzbekcha"),
            SpeechLanguage("mn", "Монгол"),
            SpeechLanguage("ne", "नेपाली"),
            SpeechLanguage("mr", "मराठी"),
            SpeechLanguage("gu", "ગુજરાતી"),
            SpeechLanguage("pa", "ਪੰਜਾਬੀ"),
            SpeechLanguage("kn", "ಕನ್ನಡ"),
            SpeechLanguage("ml", "മലയാളം"),
            SpeechLanguage("my", "မြန်မာ"),
            SpeechLanguage("km", "ខ្មែរ"),
            SpeechLanguage("lo", "ລາວ"),
            SpeechLanguage("si", "සිංහල"),
            SpeechLanguage("af", "Afrikaans"),
            SpeechLanguage("am", "አማርኛ"),
            SpeechLanguage("yo", "Yorùbá"),
            SpeechLanguage("ha", "Hausa"),
            SpeechLanguage("ig", "Igbo")
        )
    }
}

private class MultipartBody(private val boundary: String) {
    private val parts = mutableListOf<ByteArray>()
    private val crlf = "\r\n"

    fun field(name: String, value: String): MultipartBody = apply {
        val text = "--$boundary$crlf" +
            "Content-Disposition: form-data; name=\"$name\"$crlf$crlf" +
            "$value$crlf"
        parts += text.toByteArray(StandardCharsets.UTF_8)
    }

    fun file(name: String, filename: String, mime: String, bytes: ByteArray): MultipartBody = apply {
        val header = "--$boundary$crlf" +
            "Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"$crlf" +
            "Content-Type: $mime$crlf$crlf"
        parts += header.toByteArray(StandardCharsets.UTF_8)
        parts += bytes
        parts += crlf.toByteArray(StandardCharsets.UTF_8)
    }

    fun build(): ByteArray {
        val end = "--$boundary--$crlf".toByteArray(StandardCharsets.UTF_8)
        val size = parts.sumOf { it.size } + end.size
        return java.io.ByteArrayOutputStream(size).use { out ->
            parts.forEach(out::write)
            out.write(end)
            out.toByteArray()
        }
    }
}
