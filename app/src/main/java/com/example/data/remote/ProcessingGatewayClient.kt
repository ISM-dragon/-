package com.example.data.remote

import android.content.ContentResolver
import android.net.Uri
import com.example.data.model.GatewayConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

/**
 * Remote processing bridge. Gemini credentials never cross this boundary;
 * the Gateway owns the Python pipeline and its server-side provider secrets.
 */
class ProcessingGatewayClient(
    private val contentResolver: ContentResolver
) {
    data class Progress(
        val percent: Int,
        val stage: String,
        val message: String
    )

    data class RemoteClip(
        val title: String,
        val startTimeSec: Int,
        val endTimeSec: Int,
        val durationSec: Int,
        val score: Int,
        val transcript: String,
        val mediaUrl: String
    )

    data class RemoteResult(
        val gatewayJobId: String,
        val clips: List<RemoteClip>
    )

    suspend fun process(
        config: GatewayConfig,
        sourceUri: String,
        captionTheme: String,
        mode: String,
        onProgress: suspend (Progress) -> Unit
    ): Result<RemoteResult> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = validateBaseUrl(config.baseUrl)
            val localUri = Uri.parse(sourceUri)
            val upload = upload(baseUrl, config.token, localUri) { percent ->
                onProgress(Progress(percent, "UPLOADING", "رفع الفيديو إلى Gateway: $percent%"))
            }
            onProgress(Progress(12, "UPLOADED", "تم رفع الفيديو إلى Gateway بشكل خاص"))
            val gatewayJobId = start(baseUrl, config.token, upload, captionTheme, mode)
            var lastStatus = "queued"
            var completedResult: RemoteResult? = null
            while (completedResult == null) {
                val statusPayload = status(baseUrl, config.token, gatewayJobId)
                val fraction = statusPayload.optDouble("fraction", 0.0).toFloat().coerceIn(0f, 1f)
                val percent = (15 + fraction * 80f).toInt().coerceIn(15, 95)
                val stage = statusPayload.optString("stage", statusPayload.optString("status", "processing"))
                val message = statusPayload.optString("message", "جاري تنفيذ المعالجة على Gateway")
                if (stage != lastStatus || percent >= 95) {
                    onProgress(Progress(percent, stage, message))
                    lastStatus = stage
                }
                when (statusPayload.optString("status")) {
                    "done", "completed", "succeeded" -> {
                        onProgress(Progress(100, "COMPLETED", "اكتملت المعالجة البعيدة"))
                        completedResult = RemoteResult(gatewayJobId, parseClips(statusPayload))
                    }
                    "failed", "error" -> error(statusPayload.optString("error", "فشلت معالجة Gateway"))
                }
                if (completedResult == null) delay(POLL_INTERVAL_MS)
            }
            Result.success(requireNotNull(completedResult))
        } catch (error: Exception) {
            Result.failure<RemoteResult>(error)
        }
    }

    private suspend fun upload(
        baseUrl: String,
        token: String,
        sourceUri: Uri,
        onProgress: suspend (Int) -> Unit
    ): String {
        val connection = openConnection("$baseUrl/v1/sources/upload", token, "POST").apply {
            setRequestProperty("Content-Type", "video/mp4")
            doOutput = true
        }
        return try {
            val input = contentResolver.openInputStream(sourceUri)
                ?: error("تعذر فتح ملف الفيديو للرفع")
            val totalBytes = contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use { it.length } ?: -1L
            var writtenBytes = 0L
            var lastReportedAt = 0L
            input.use { source ->
                connection.outputStream.use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        writtenBytes += count
                        val now = System.currentTimeMillis()
                        if (now - lastReportedAt >= 250L || (totalBytes > 0L && writtenBytes >= totalBytes)) {
                            val percent = if (totalBytes > 0L) {
                                (writtenBytes.toDouble() / totalBytes.toDouble() * 10.0).toInt().coerceIn(0, 10)
                            } else {
                                0
                            }
                            onProgress(percent)
                            lastReportedAt = now
                        }
                    }
                }
            }
            onProgress(10)
            val json = readJson(connection)
            json.optString("source").takeIf { it.isNotBlank() }
                ?: error("Gateway لم يُرجع رابط المصدر المرفوع")
        } finally {
            connection.disconnect()
        }
    }

    private fun start(baseUrl: String, token: String, sourceUrl: String, captionTheme: String, mode: String): String {
        val connection = openConnection("$baseUrl/v1/processing/jobs", token, "POST").apply {
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }
        return try {
            val body = JSONObject()
                .put("source", sourceUrl)
                .put("llm", "gemini")
                .put("captions", captionTheme.ifBlank { "classic" })
                .put("mode", mode.ifBlank { "balanced" })
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val json = readJson(connection)
            json.optString("id").takeIf { it.isNotBlank() }
                ?: error("Gateway لم يُرجع معرف المهمة")
        } finally {
            connection.disconnect()
        }
    }

    private fun status(baseUrl: String, token: String, jobId: String): JSONObject {
        val connection = openConnection("$baseUrl/v1/processing/jobs/${URI.create(jobId).toASCIIString()}", token, "GET")
        return try { readJson(connection) } finally { connection.disconnect() }
    }

    private fun parseClips(status: JSONObject): List<RemoteClip> {
        val results = status.optJSONObject("results") ?: return emptyList()
        val render = results.optJSONObject("render") ?: return emptyList()
        val outputs = render.optJSONArray("outputs") ?: JSONArray()
        return buildList {
            for (index in 0 until outputs.length()) {
                val item = outputs.optJSONObject(index) ?: continue
                val url = item.optString("path").takeIf { it.startsWith("http") } ?: continue
                val start = item.optInt("start", item.optInt("start_time", 0))
                val end = item.optInt("end", item.optInt("end_time", start + item.optInt("duration", 0)))
                add(
                    RemoteClip(
                        title = item.optString("title", "Clip ${index + 1}"),
                        startTimeSec = start,
                        endTimeSec = end.coerceAtLeast(start),
                        durationSec = item.optInt("duration", (end - start).coerceAtLeast(0)),
                        score = item.optInt("score", item.optInt("final_score", 0)),
                        transcript = item.optString("transcript"),
                        mediaUrl = url
                    )
                )
            }
        }
    }

    suspend fun download(config: GatewayConfig, mediaUrl: String, destination: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(mediaUrl, config.token, "GET")
            try {
                connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
                require(destination.isFile && destination.length() > 0) { "Gateway أعاد ملفاً فارغاً" }
                destination
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun openConnection(url: String, token: String, method: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer ${token.trim()}")
        }

    private fun readJson(connection: HttpURLConnection): JSONObject {
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IOException("Gateway HTTP $code: ${body.take(300)}")
        return JSONObject(body)
    }

    private fun validateBaseUrl(raw: String): String {
        val normalized = raw.trim().removeSuffix("/")
        require(normalized.isNotBlank()) { "Gateway URL غير مضبوط" }
        val uri = URI(normalized)
        val host = uri.host.orEmpty().lowercase()
        val local = host == "localhost" || host == "127.0.0.1" || host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.16.")
        require(uri.scheme?.lowercase() == "https" || local) { "يجب استخدام HTTPS خارج الشبكة المحلية" }
        return normalized
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
