package com.example.data.remote

import android.content.Context
import android.net.Uri
import com.example.domain.analysis.Transcript
import com.example.domain.analysis.TranscriptSegment
import com.example.domain.analysis.WordTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit

class SpeechToTextService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(
        uri: Uri,
        apiKey: String,
        language: String? = null
    ): Result<Transcript> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("لا يوجد مفتاح Speech-to-Text صالح."))
        val resolver = context.contentResolver
        val descriptor = resolver.openAssetFileDescriptor(uri, "r")
            ?: return@withContext Result.failure(IllegalArgumentException("تعذر فتح ملف الصوت/الفيديو."))
        val length = descriptor.length
        val mime = resolver.getType(uri) ?: "audio/mp4"
        val filename = "audio_input.${mime.substringAfter('/').substringBefore(';').ifBlank { "mp4" }}"
        val streamBody = object : RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(uri)?.use { input: InputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                    }
                } ?: error("Unable to open media input")
            }
        }
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, streamBody)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities[]", "word")
            .apply { language?.takeIf { it.length == 2 }?.let { addFormDataPart("language", it) } }
            .build()
        val response = client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey")
                .post(form)
                .build()
        ).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            return@withContext Result.failure(IllegalStateException("Speech-to-Text فشل HTTP ${response.code}: ${body.take(240)}"))
        }
        runCatching { parseTranscript(body) }
    }

    private fun parseTranscript(body: String): Transcript {
        val json = JSONObject(body)
        val language = json.optString("language", "und")
        val segmentsJson = json.optJSONArray("segments")
        val segments = buildList {
            if (segmentsJson != null) {
                for (i in 0 until segmentsJson.length()) {
                    val segment = segmentsJson.getJSONObject(i)
                    val wordsJson = segment.optJSONArray("words")
                    val words = buildList {
                        if (wordsJson != null) {
                            for (j in 0 until wordsJson.length()) {
                                val word = wordsJson.getJSONObject(j)
                                add(
                                    WordTimestamp(
                                        word = word.optString("word").trim(),
                                        startSec = word.optDouble("start", 0.0).toFloat(),
                                        endSec = word.optDouble("end", 0.0).toFloat(),
                                        confidence = word.optDouble("probability", 1.0).toFloat().coerceIn(0f, 1f)
                                    )
                                )
                            }
                        }
                    }
                    add(
                        TranscriptSegment(
                            text = segment.optString("text").trim(),
                            startSec = segment.optDouble("start", 0.0).toFloat(),
                            endSec = segment.optDouble("end", 0.0).toFloat(),
                            words = words,
                            confidence = words.map { it.confidence }.average().toFloat().coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }.filter { it.text.isNotBlank() && it.endSec > it.startSec }
        require(segments.isNotEmpty()) { "Speech-to-Text أعاد نصًا فارغًا." }
        return Transcript(language, segments, "openai-whisper", segments.any { it.words.isNotEmpty() })
    }
}
