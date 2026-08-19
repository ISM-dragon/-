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
            val upload = upload(baseUrl, config.token, localUri)
            onProgress(Progress(12, "UPLOADED", "تم رفع الفيديو إلى Gateway بشكل خاص"))
            val project = createProject(config, "Android session ${System.currentTimeMillis()}", upload).getOrThrow()
            val gatewayJobId = processProject(config, project.id, upload, captionTheme, mode).getOrThrow().id
            var lastStatus = "queued"
            var completedPayload: JSONObject? = null
            while (completedPayload == null) {
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
                        completedPayload = statusPayload
                    }
                    "failed", "error" -> error(statusPayload.optString("error", "فشلت معالجة Gateway"))
                }
                if (completedPayload == null) delay(POLL_INTERVAL_MS)
            }
            Result.success(RemoteResult(gatewayJobId, parseClips(checkNotNull(completedPayload))))
        } catch (error: Exception) {
            Result.failure<RemoteResult>(error)
        }
    }

    private fun upload(baseUrl: String, token: String, sourceUri: Uri): String {
        val connection = openConnection("$baseUrl/v1/sources/upload", token, "POST").apply {
            setRequestProperty("Content-Type", "video/mp4")
            doOutput = true
        }
        return try {
            val input = contentResolver.openInputStream(sourceUri)
                ?: error("تعذر فتح ملف الفيديو للرفع")
            input.use { source ->
                connection.outputStream.use { output -> source.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
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

    suspend fun listProviders(config: GatewayConfig): Result<List<com.example.data.model.GatewayProviderModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(gatewayUrl(config, "/v1/ai/providers"), config.token, "GET")
            try { parseProviders(readJson(connection)) } finally { connection.disconnect() }
        }
    }

    suspend fun usageSummary(config: GatewayConfig, days: Int = 30): Result<com.example.data.model.GatewayUsageSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(gatewayUrl(config, "/v1/ai/usage?days=${days.coerceIn(1, 3650)}"), config.token, "GET")
            try { parseUsage(readJson(connection)) } finally { connection.disconnect() }
        }
    }

    suspend fun capabilities(config: GatewayConfig): Result<com.example.data.model.GatewayProcessingCapabilities> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(gatewayUrl(config, "/v1/processing/capabilities"), config.token, "GET")
            try {
                val json = readJson(connection)
                com.example.data.model.GatewayProcessingCapabilities(
                    pipeline = json.optBoolean("pipeline"),
                    python = json.optBoolean("python"),
                    ffmpeg = json.optBoolean("ffmpeg"),
                    storage = json.optBoolean("storage"),
                    geminiConfigured = json.optBoolean("gemini_configured"),
                    androidRemoteProcessing = json.optBoolean("android_remote_processing"),
                    youtubeUrls = json.optBoolean("youtube_urls"),
                    httpsUrls = json.optBoolean("https_urls")
                )
            } finally { connection.disconnect() }
        }
    }

    suspend fun createProject(config: GatewayConfig, name: String, source: String?): Result<com.example.data.model.GatewayProject> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(gatewayUrl(config, "/api/v1/projects"), config.token, "POST").apply {
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
            }
            try {
                val body = JSONObject().put("name", name).apply { if (!source.isNullOrBlank()) put("source", source) }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                parseProject(readJson(connection))
            } finally { connection.disconnect() }
        }
    }

    suspend fun processProject(config: GatewayConfig, projectId: String, source: String?, captionTheme: String, mode: String): Result<com.example.data.model.GatewayJob> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(gatewayUrl(config, "/api/v1/projects/${encodePath(projectId)}/process"), config.token, "POST").apply {
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
            }
            try {
                val body = JSONObject()
                    .put("llm", "gemini")
                    .put("captions", captionTheme.ifBlank { "classic" })
                    .apply { if (!source.isNullOrBlank()) put("source", source) }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                readJson(connection).optJSONObject("job")?.let { parseJob(it) }
                    ?: error("Gateway لم يُرجع job للمشروع")
            } finally { connection.disconnect() }
        }
    }

    suspend fun cancelJob(config: GatewayConfig, jobId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(gatewayUrl(config, "/api/v1/jobs/${encodePath(jobId)}/cancel"), config.token, "POST")
            try { readJson(connection).optString("status") == "cancelled" } finally { connection.disconnect() }
        }
    }

    private fun gatewayUrl(config: GatewayConfig, path: String): String = "${validateBaseUrl(config.baseUrl)}$path"

    private fun encodePath(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun parseProviders(json: JSONObject): List<com.example.data.model.GatewayProviderModel> {
        val array = json.optJSONArray("providers") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val capabilities = mutableMapOf<String, Boolean>()
                item.optJSONObject("capabilities")?.let { obj ->
                    obj.keys().forEach { key -> capabilities[key] = obj.optBoolean(key) }
                }
                add(com.example.data.model.GatewayProviderModel(
                    id = item.optString("id"), name = item.optString("name"), type = item.optString("type"),
                    baseUrl = item.optString("base_url"), defaultModel = item.optString("default_model"),
                    fallbackModel = item.optString("fallback_model"), enabled = item.optBoolean("enabled", true),
                    credentialConfigured = item.optBoolean("credential_configured"), capabilities = capabilities,
                    inputCostPerMillionUsd = item.optDouble("input_cost_per_million", 0.0),
                    outputCostPerMillionUsd = item.optDouble("output_cost_per_million", 0.0)
                ))
            }
        }
    }

    private fun parseUsage(json: JSONObject): com.example.data.model.GatewayUsageSummary {
        val aggregates = buildList {
            val array = json.optJSONArray("aggregates") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(com.example.data.model.GatewayUsageAggregate(
                    provider = item.optString("provider"), model = item.optString("model"),
                    requests = item.optInt("requests"), inputTokens = item.optLong("input_tokens"),
                    outputTokens = item.optLong("output_tokens"), totalTokens = item.optLong("total_tokens"),
                    estimatedRequests = item.optInt("estimated_requests"), averageLatencyMs = item.optDouble("average_latency_ms"),
                    costUsd = item.optDouble("cost_usd")
                ))
            }
        }
        return com.example.data.model.GatewayUsageSummary(json.optInt("days"), json.optInt("events"), aggregates)
    }

    private fun parseProject(json: JSONObject): com.example.data.model.GatewayProject = com.example.data.model.GatewayProject(
        id = json.optString("id"), name = json.optString("name"), source = json.optString("source").ifBlank { null },
        status = json.optString("status"), activeJobId = json.optString("active_job_id").ifBlank { null }
    )

    private fun parseJob(json: JSONObject): com.example.data.model.GatewayJob = com.example.data.model.GatewayJob(
        id = json.optString("id"), status = json.optString("status"), stage = json.optString("stage").ifBlank { null },
        fraction = if (json.has("fraction") && !json.isNull("fraction")) json.optDouble("fraction") else null,
        message = json.optString("message").ifBlank { null }, error = json.optString("error").ifBlank { null },
        projectId = json.optString("project_id").ifBlank { null }
    )

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
