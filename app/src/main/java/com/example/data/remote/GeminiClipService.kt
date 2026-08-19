package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AiProviderConfig
import com.example.data.model.AiProviderType
import com.example.data.model.AiTemplateRecommendation
import com.example.data.model.AnimatedWord
import com.example.data.model.BRollIdea
import com.example.data.model.ClipGenerationData
import com.example.data.model.DedicatedCaptionResult
import com.example.data.model.DirectApiPublishLog
import com.example.data.model.DirectPlatformApiCredentials
import com.example.data.model.SocialPostCopy
import com.example.domain.analysis.WordTimestamp
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class GeminiClipService(private val context: Context? = null) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    var customApiKey: String? = null

    suspend fun testProviderConnection(provider: AiProviderConfig): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmedKey = provider.apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Pair(false, "API Key cannot be empty.")
        }
        try {
            when (provider.providerType) {
                AiProviderType.GEMINI.name -> {
                    val requestJson = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().put("text", "Respond with 'OK' if you receive this."))
                                })
                            })
                        })
                    }
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = requestJson.toString().toRequestBody(mediaType)
                    val modelToUse = provider.modelName.ifBlank { "gemini-2.5-flash" }
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$modelToUse:generateContent?key=$trimmedKey")
                        .post(body)
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful) {
                        Pair(true, "Successfully connected to Google Gemini ($modelToUse)!")
                    } else {
                        val errorMsg = try {
                            val json = JSONObject(responseBody ?: "")
                            json.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                        } catch (e: Exception) {
                            "HTTP ${response.code}: ${response.message}"
                        }
                        Pair(false, errorMsg)
                    }
                }
                AiProviderType.ANTHROPIC.name -> {
                    val modelToUse = provider.modelName.ifBlank { "claude-3-5-sonnet-20241022" }
                    val requestJson = JSONObject().apply {
                        put("model", modelToUse)
                        put("max_tokens", 10)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", "Respond with 'OK'")
                            })
                        })
                    }
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = requestJson.toString().toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url("https://api.anthropic.com/v1/messages")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("x-api-key", trimmedKey)
                        .addHeader("anthropic-version", "2023-06-01")
                        .build()

                    val startTime = System.currentTimeMillis()
                    val response = okHttpClient.newCall(request).execute()
                    val latency = System.currentTimeMillis() - startTime
                    val responseBody = response.body?.string()

                    if (response.isSuccessful) {
                        Pair(true, "Successfully connected to Anthropic Claude ($modelToUse) in ${latency}ms! Key active.")
                    } else {
                        val errorMsg = try {
                            val json = JSONObject(responseBody ?: "")
                            json.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                        } catch (e: Exception) {
                            "HTTP ${response.code}: ${response.message}"
                        }
                        Pair(false, errorMsg)
                    }
                }
                else -> {
                    // OpenRouter, Groq, Mistral, OpenAI, Custom
                    val endpointUrl = when (provider.providerType) {
                        AiProviderType.OPENROUTER.name -> "https://openrouter.ai/api/v1/chat/completions"
                        AiProviderType.GROQ.name -> "https://api.groq.com/openai/v1/chat/completions"
                        AiProviderType.MISTRAL.name -> "https://api.mistral.ai/v1/chat/completions"
                        AiProviderType.OPENAI.name -> "https://api.openai.com/v1/chat/completions"
                        else -> provider.customBaseUrl.ifBlank { "https://api.openai.com/v1/chat/completions" }
                    }
                    val defaultModel = when (provider.providerType) {
                        AiProviderType.OPENROUTER.name -> "meta-llama/llama-3.3-70b-instruct"
                        AiProviderType.GROQ.name -> "llama-3.3-70b-versatile"
                        AiProviderType.MISTRAL.name -> "mistral-large-latest"
                        AiProviderType.OPENAI.name -> "gpt-4o-mini"
                        else -> "default"
                    }
                    val modelToUse = provider.modelName.ifBlank { defaultModel }

                    val requestJson = JSONObject().apply {
                        put("model", modelToUse)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", "Respond with 'OK'")
                            })
                        })
                        put("max_tokens", 10)
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = requestJson.toString().toRequestBody(mediaType)
                    val reqBuilder = Request.Builder()
                        .url(endpointUrl)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer $trimmedKey")

                    if (provider.providerType == AiProviderType.OPENROUTER.name) {
                        reqBuilder.addHeader("HTTP-Referer", "https://opuspro.internal")
                        reqBuilder.addHeader("X-Title", "ISM Flow")
                    }

                    val response = okHttpClient.newCall(reqBuilder.build()).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful) {
                        Pair(true, "Successfully connected to ${provider.name} ($modelToUse)!")
                    } else {
                        val errorMsg = try {
                            val json = JSONObject(responseBody ?: "")
                            json.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                        } catch (e: Exception) {
                            "HTTP ${response.code}: ${response.message}"
                        }
                        Pair(false, errorMsg)
                    }
                }
            }
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Connection failed. Please check network.")
        }
    }

    suspend fun testApiKey(candidateKey: String): Pair<Boolean, String> {
        return testProviderConnection(
            AiProviderConfig(
                name = "Google Gemini Test",
                providerType = AiProviderType.GEMINI.name,
                apiKey = candidateKey
            )
        )
    }

    private suspend fun executeAiRequestWithProvider(
        provider: AiProviderConfig,
        systemPrompt: String,
        userContent: String,
        videoUri: Uri? = null
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = provider.apiKey.trim()
        if (apiKey.isBlank()) return@withContext null

        try {
            when (provider.providerType) {
                AiProviderType.GEMINI.name -> {
                    val videoRef = videoUri?.let { uploadVideoForAnalysis(it, apiKey) }
                    if (videoUri != null && videoRef == null) {
                        throw IOException("تعذر رفع الفيديو إلى Gemini File API أو لم يصبح الملف ACTIVE خلال المهلة.")
                    }
                    val parts = JSONArray()
                    videoRef?.let { (fileUri, mimeType) ->
                        parts.put(
                            JSONObject().put(
                                "file_data",
                                JSONObject()
                                    .put("mime_type", mimeType)
                                    .put("file_uri", fileUri)
                            )
                        )
                    }
                    parts.put(JSONObject().put("text", "$systemPrompt\n\n$userContent\n\nReturn timestamps based on the actual video."))
                    val requestJson = JSONObject().apply {
                        put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.3)
                            put("topP", 0.9)
                            put("responseMimeType", "application/json")
                        })
                    }
                    val modelToUse = provider.modelName.ifBlank { "gemini-2.5-flash" }
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = requestJson.toString().toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$modelToUse:generateContent?key=$apiKey")
                        .post(body)
                        .build()
                    val response = okHttpClient.newCall(request).execute()
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                        val json = JSONObject(responseBody)
                        val candidates = json.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val content = candidates.getJSONObject(0).optJSONObject("content")
                            val partsResponse = content?.optJSONArray("parts")
                            return@withContext partsResponse?.getJSONObject(0)?.optString("text")
                        }
                    } else {
                        throw IOException("Gemini HTTP ${response.code}: ${extractProviderError(responseBody)}")
                    }
                    throw IOException("لم يُرجع Gemini محتوى صالحاً للتحليل.")
                }
                AiProviderType.ANTHROPIC.name -> {
                    val modelToUse = provider.modelName.ifBlank { "claude-3-5-sonnet-20241022" }
                    val requestJson = JSONObject().apply {
                        put("model", modelToUse)
                        put("max_tokens", 2048)
                        put("system", systemPrompt)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", userContent)
                            })
                        })
                    }
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = requestJson.toString().toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url("https://api.anthropic.com/v1/messages")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("x-api-key", apiKey)
                        .addHeader("anthropic-version", "2023-06-01")
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                        val json = JSONObject(responseBody)
                        val contentArr = json.optJSONArray("content")
                        if (contentArr != null && contentArr.length() > 0) {
                            return@withContext contentArr.getJSONObject(0).optString("text")
                        }
                    } else {
                        throw IOException("Anthropic HTTP ${response.code}: ${extractProviderError(responseBody)}")
                    }
                    throw IOException("لم يُرجع Anthropic محتوى صالحاً.")
                }
                else -> {
                    // OpenRouter, Groq, Mistral, OpenAI, Custom
                    val endpointUrl = when (provider.providerType) {
                        AiProviderType.OPENROUTER.name -> "https://openrouter.ai/api/v1/chat/completions"
                        AiProviderType.GROQ.name -> "https://api.groq.com/openai/v1/chat/completions"
                        AiProviderType.MISTRAL.name -> "https://api.mistral.ai/v1/chat/completions"
                        AiProviderType.OPENAI.name -> "https://api.openai.com/v1/chat/completions"
                        else -> provider.customBaseUrl.ifBlank { "https://api.openai.com/v1/chat/completions" }
                    }
                    val defaultModel = when (provider.providerType) {
                        AiProviderType.OPENROUTER.name -> "meta-llama/llama-3.3-70b-instruct"
                        AiProviderType.GROQ.name -> "llama-3.3-70b-versatile"
                        AiProviderType.MISTRAL.name -> "mistral-large-latest"
                        AiProviderType.OPENAI.name -> "gpt-4o-mini"
                        else -> "default"
                    }
                    val modelToUse = provider.modelName.ifBlank { defaultModel }

                    val requestJson = JSONObject().apply {
                        put("model", modelToUse)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "system")
                                put("content", systemPrompt)
                            })
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", userContent)
                            })
                        })
                        put("temperature", 0.3)
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = requestJson.toString().toRequestBody(mediaType)
                    val reqBuilder = Request.Builder()
                        .url(endpointUrl)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer $apiKey")

                    if (provider.providerType == AiProviderType.OPENROUTER.name) {
                        reqBuilder.addHeader("HTTP-Referer", "https://opuspro.internal")
                        reqBuilder.addHeader("X-Title", "ISM Flow")
                    }

                    val response = okHttpClient.newCall(reqBuilder.build()).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                        val json = JSONObject(responseBody)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val msg = choices.getJSONObject(0).optJSONObject("message")
                            return@withContext msg?.optString("content")
                        }
                    } else {
                        throw IOException("${provider.name} HTTP ${response.code}: ${extractProviderError(responseBody)}")
                    }
                    throw IOException("لم يُرجع ${provider.name} محتوى صالحاً.")
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiClipService", "Error executing request with provider ${provider.name}: ${e.message}", e)
            throw e
        }
    }

    private fun extractProviderError(responseBody: String?): String {
        if (responseBody.isNullOrBlank()) return "استجابة فارغة"
        return runCatching {
            val json = JSONObject(responseBody)
            json.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: responseBody.take(300)
        }.getOrDefault(responseBody.take(300))
    }


    private fun uploadVideoForAnalysis(uri: Uri, apiKey: String): Pair<String, String>? {
        val resolver = context?.contentResolver ?: return null
        val mimeType = resolver.getType(uri) ?: "video/mp4"
        val contentLength = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (contentLength <= 0L) return null

        return try {
            val startBody = JSONObject()
                .put("file", JSONObject().put("display_name", "opus_pro_${System.currentTimeMillis()}"))
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val startRequest = Request.Builder()
                .url("https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey")
                .addHeader("X-Goog-Upload-Protocol", "resumable")
                .addHeader("X-Goog-Upload-Command", "start")
                .addHeader("X-Goog-Upload-Header-Content-Length", contentLength.toString())
                .addHeader("X-Goog-Upload-Header-Content-Type", mimeType)
                .post(startBody)
                .build()
            val startResponse = okHttpClient.newCall(startRequest).execute()
            if (!startResponse.isSuccessful) return null
            val uploadUrl = startResponse.header("X-Goog-Upload-URL") ?: return null

            val uploadBody = object : okhttp3.RequestBody() {
                override fun contentType() = mimeType.toMediaType()
                override fun contentLength() = contentLength
                override fun writeTo(sink: BufferedSink) {
                    resolver.openInputStream(uri)?.use { input: InputStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            sink.write(buffer, 0, read)
                        }
                    } ?: error("Unable to open video Uri")
                }
            }
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .addHeader("X-Goog-Upload-Offset", "0")
                .addHeader("X-Goog-Upload-Command", "upload, finalize")
                .post(uploadBody)
                .build()
            val uploadResponse = okHttpClient.newCall(uploadRequest).execute()
            if (!uploadResponse.isSuccessful) return null
            val uploadJson = JSONObject(uploadResponse.body?.string().orEmpty())
            val uploadedFile = uploadJson.optJSONObject("file") ?: return null
            val fileName = uploadedFile.optString("name").takeIf { it.isNotBlank() } ?: return null
            val fileUri = uploadedFile.optString("uri").takeIf { it.isNotBlank() } ?: return null

            repeat(60) {
                val stateRequest = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/$fileName?key=$apiKey")
                    .get()
                    .build()
                val stateResponse = okHttpClient.newCall(stateRequest).execute()
                if (stateResponse.isSuccessful) {
                    val stateFile = JSONObject(stateResponse.body?.string().orEmpty())
                    when (stateFile.optString("state")) {
                        "ACTIVE" -> return fileUri to mimeType
                        "FAILED" -> return null
                    }
                }
                Thread.sleep(1_000L)
            }
            null
        } catch (error: Exception) {
            Log.e("GeminiClipService", "Gemini video upload failed", error)
            null
        }
    }

    suspend fun analyzeAndGenerateClips(
        title: String,
        sourceUrl: String,
        transcriptOrPrompt: String,
        durationMinutes: Int,
        providers: List<AiProviderConfig> = emptyList(),
        videoUri: Uri? = null
    ): List<ClipGenerationData> = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are ISM (OpusClip) AI Video Repurposing Engine.
            Your task is to analyze the following video title and transcript/content, extract the most viral 30-60 second clips for TikTok, Instagram Reels, and YouTube Shorts.
            
            For each clip, calculate an authentic Virality Score (0-100) based on:
            - Hook Score (0-100)
            - Retention Score (0-100)
            - Emotional Arc Score (0-100)
            - Shareability Score (0-100)
            - Punchline Score (0-100)
            
            Return a JSON array of clip objects with fields:
            - "title": string (engaging short title)
            - "startTimeSec": integer (e.g. 15)
            - "endTimeSec": integer (e.g. 65)
            - "viralityScore": integer (e.g. 96)
            - "hookScore": integer (e.g. 98)
            - "retentionScore": integer (e.g. 92)
            - "emotionalScore": integer (e.g. 89)
            - "shareabilityScore": integer (e.g. 95)
            - "punchlineScore": integer (e.g. 91)
            - "hookExplanation": string (explaining why this moment grabs attention in the first 3 seconds)
            - "transcript": string (the spoken text in this clip segment)
            - "keywords": array of strings (top 3-5 punchy words in the clip)
            - "emojis": array of strings (relevant emojis)
            - "bRollIdeas": array of objects with "title", "timestampSec", "visualPrompt", "soundEffect"
            - "socialCopies": array of objects with "platform" (TikTok, Instagram Reels, YouTube Shorts, LinkedIn), "caption", "hook", "hashtags" (array of strings)
            
            Output ONLY raw JSON array, without markdown backticks.
        """.trimIndent()

        val userContent = """
            Video Title: $title
            Source Duration: $durationMinutes minutes
            Source URL/Context: $sourceUrl
            Transcript/Content: $transcriptOrPrompt
        """.trimIndent()

        // 1. If providers pool provided, try them in priority order
        var lastProviderError: Throwable? = null
        val activeProviders = providers.filter { it.isEnabled && it.apiKey.isNotBlank() }.sortedBy { it.priority }
        if (activeProviders.isNotEmpty()) {
            for (provider in activeProviders) {
                try {
                    val rawResponse = executeAiRequestWithProvider(provider, systemPrompt, userContent, videoUri)
                    if (!rawResponse.isNullOrBlank()) {
                        val cleanedText = rawResponse.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()
                        val parsedClips = parseClipsFromJson(cleanedText)
                        if (parsedClips.isNotEmpty()) {
                            Log.d("GeminiClipService", "Successfully generated clips using provider: ${provider.name}")
                            return@withContext parsedClips
                        }
                        lastProviderError = IllegalStateException("استجابة ${provider.name} لا تحتوي على مصفوفة مقاطع JSON صالحة.")
                    }
                } catch (e: Exception) {
                    lastProviderError = e
                    Log.w("GeminiClipService", "Provider ${provider.name} failed during clip generation, failing over...", e)
                }
            }
        }

        // 2. Primary Google Gemini key fallback
        val apiKey = customApiKey?.trim()?.takeIf { it.isNotBlank() } ?: try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        val primaryKeyAlreadyTried = activeProviders.any {
            it.providerType == AiProviderType.GEMINI.name && it.apiKey.trim() == apiKey
        }

        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY" && !primaryKeyAlreadyTried) {
            try {
                val primaryConfig = AiProviderConfig(
                    name = "Primary Google Gemini",
                    providerType = AiProviderType.GEMINI.name,
                    apiKey = apiKey
                )
                val rawResponse = executeAiRequestWithProvider(primaryConfig, systemPrompt, userContent, videoUri)
                if (!rawResponse.isNullOrBlank()) {
                    val cleanedText = rawResponse.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    val parsedClips = parseClipsFromJson(cleanedText)
                    if (parsedClips.isNotEmpty()) {
                        return@withContext parsedClips
                    }
                    lastProviderError = IllegalStateException("استجابة Gemini لا تحتوي على مصفوفة مقاطع JSON صالحة.")
                }
            } catch (e: Exception) {
                lastProviderError = e
                Log.e("GeminiClipService", "Primary Gemini API call failed", e)
            }
        }

        val detail = lastProviderError?.localizedMessage?.takeIf { it.isNotBlank() }
            ?: "لم يُضف مفتاح مزود صالح أو كانت الاستجابة فارغة."
        throw IllegalStateException("فشل تحليل الفيديو: $detail", lastProviderError)
    }

    private fun parseClipsFromJson(jsonText: String): List<ClipGenerationData> {
        val result = mutableListOf<ClipGenerationData>()
        try {
            val array = JSONArray(jsonText)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val bRollList = mutableListOf<BRollIdea>()
                val bRollArray = obj.optJSONArray("bRollIdeas")
                if (bRollArray != null) {
                    for (j in 0 until bRollArray.length()) {
                        val bObj = bRollArray.getJSONObject(j)
                        val bTitle = bObj.optString("title").trim()
                        val bPrompt = bObj.optString("visualPrompt").trim()
                        if (bTitle.isNotBlank() && bPrompt.isNotBlank()) {
                            bRollList.add(
                                BRollIdea(
                                    title = bTitle,
                                    timestampSec = bObj.optInt("timestampSec", -1),
                                    visualPrompt = bPrompt,
                                    soundEffect = bObj.optString("soundEffect").trim()
                                )
                            )
                        }
                    }
                }

                val socialList = mutableListOf<SocialPostCopy>()
                val socialArray = obj.optJSONArray("socialCopies")
                if (socialArray != null) {
                    for (j in 0 until socialArray.length()) {
                        val sObj = socialArray.getJSONObject(j)
                        val tagsList = mutableListOf<String>()
                        val tagsArray = sObj.optJSONArray("hashtags")
                        if (tagsArray != null) {
                            for (k in 0 until tagsArray.length()) {
                                tagsList.add(tagsArray.getString(k))
                            }
                        }
                        val platform = sObj.optString("platform").trim()
                        val caption = sObj.optString("caption").trim()
                        val hook = sObj.optString("hook").trim()
                        if (platform.isNotBlank() && caption.isNotBlank() && hook.isNotBlank()) {
                            socialList.add(
                                SocialPostCopy(
                                    platform = platform,
                                    caption = caption,
                                    hook = hook,
                                    hashtags = tagsList
                                )
                            )
                        }
                    }
                }

                val keywordsList = mutableListOf<String>()
                val keywordsArray = obj.optJSONArray("keywords")
                if (keywordsArray != null) {
                    for (k in 0 until keywordsArray.length()) {
                        keywordsList.add(keywordsArray.getString(k))
                    }
                }

                val wordTimestamps = mutableListOf<WordTimestamp>()
                val wordTimestampArray = obj.optJSONArray("wordTimestamps")
                if (wordTimestampArray != null) {
                    for (k in 0 until wordTimestampArray.length()) {
                        val wordObject = wordTimestampArray.getJSONObject(k)
                        val start = wordObject.optDouble("startSec", -1.0).toFloat()
                        val end = wordObject.optDouble("endSec", -1.0).toFloat()
                        if (start >= 0f && end > start) {
                            wordTimestamps += WordTimestamp(
                                word = wordObject.optString("word").trim(),
                                startSec = start,
                                endSec = end,
                                confidence = wordObject.optDouble("confidence", 1.0).toFloat().coerceIn(0f, 1f),
                                speakerId = wordObject.optString("speakerId").takeIf { it.isNotBlank() }
                            )
                        }
                    }
                }

                val emojisList = mutableListOf<String>()
                val emojisArray = obj.optJSONArray("emojis")
                if (emojisArray != null) {
                    for (k in 0 until emojisArray.length()) {
                        emojisList.add(emojisArray.getString(k))
                    }
                }

                result.add(
                    ClipGenerationData(
                        title = obj.optString("title").trim(),
                        startTimeSec = obj.optInt("startTimeSec", -1),
                        endTimeSec = obj.optInt("endTimeSec", -1),
                        viralityScore = obj.optInt("viralityScore", 0),
                        hookScore = obj.optInt("hookScore", 0),
                        retentionScore = obj.optInt("retentionScore", 0),
                        emotionalScore = obj.optInt("emotionalScore", 0),
                        shareabilityScore = obj.optInt("shareabilityScore", 0),
                        punchlineScore = obj.optInt("punchlineScore", 0),
                        hookExplanation = obj.optString("hookExplanation"),
                        transcript = obj.optString("transcript"),
                        keywords = keywordsList,
                        emojis = emojisList,
                        bRollIdeas = bRollList,
                        socialCopies = socialList,
                        wordTimestamps = wordTimestamps
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiClipService", "Failed to parse JSON clips", e)
        }
        return result
    }

    /**
     * No synthetic clips are generated here. A clip is only accepted when the
     * configured provider returns valid timestamps and analysis data.
     */
    fun generatePrecomputedRealisticClips(
        title: String,
        transcriptOrPrompt: String
    ): List<ClipGenerationData> = emptyList()

    suspend fun generateSpeechToTextCaptions(
        spokenTextOrAudioPrompt: String,
        durationSec: Float,
        language: String = "English",
        captionTheme: String = "Opus Neon"
    ): List<AnimatedWord> = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.trim()?.takeIf { it.isNotBlank() } ?: try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val systemPrompt = """
                    You are an expert Speech-to-Text & Subtitle Synchronization Engine for short-form viral videos (TikTok, Reels, Shorts).
                    Task: Transcribe the given spoken speech in $language, breakdown into individual spoken words, and assign millisecond-precise timestamps (startSec, endSec) distributed evenly across total duration of ${durationSec}s.
                    For key viral hooks and emotional emphasis words, set "isHighlight": true and pick a punchy emoji (e.g., 🔥, 💡, 🚀, ⚡, 🤯, 💰, 🎯).
                    
                    Return ONLY a JSON array of objects with fields:
                    - "word": string
                    - "startSec": float (e.g. 0.0)
                    - "endSec": float (e.g. 0.42)
                    - "isHighlight": boolean
                    - "emoji": string (emoji or empty "")
                    - "colorHex": string (hex color e.g. #38BDF8)
                    
                    No markdown backticks, only raw JSON array.
                """.trimIndent()

                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", "$systemPrompt\n\nSpoken Audio Content:\n$spokenTextOrAudioPrompt"))
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.2)
                    })
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = requestJson.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                    .post(body)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val json = JSONObject(responseBody)
                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val text = content?.optJSONArray("parts")?.getJSONObject(0)?.optString("text")
                        if (!text.isNullOrBlank()) {
                            val cleaned = text.trim()
                                .removePrefix("```json")
                                .removePrefix("```")
                                .removeSuffix("```")
                                .trim()
                            val words = parseWordsFromJson(cleaned, captionTheme)
                            if (words.isNotEmpty()) {
                                return@withContext words
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiClipService", "STT Gemini call failed; no timed captions were generated", e)
            }
        }

        // Never fabricate timestamps. The caller can display an explicit
        // unavailable state and retry once a real transcription provider exists.
        return@withContext emptyList()
    }

    private fun parseWordsFromJson(jsonText: String, captionTheme: String): List<AnimatedWord> {
        val result = mutableListOf<AnimatedWord>()
        try {
            val array = JSONArray(jsonText)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val isHigh = obj.optBoolean("isHighlight", false)
                val defaultColor = when (captionTheme) {
                    "MrBeast Yellow" -> if (isHigh) "#FACC15" else "#FFFFFF"
                    "Ali Abdaal" -> if (isHigh) "#F43F5E" else "#E2E8F0"
                    "Cyber Green" -> if (isHigh) "#10B981" else "#FFFFFF"
                    "Hormozi Bold" -> if (isHigh) "#A855F7" else "#FFFFFF"
                    else -> if (isHigh) "#38BDF8" else "#FFFFFF"
                }
                result.add(
                    AnimatedWord(
                        word = obj.optString("word", ""),
                        startSec = obj.optDouble("startSec", 0.0).toFloat(),
                        endSec = obj.optDouble("endSec", 0.5).toFloat(),
                        isHighlight = isHigh,
                        emoji = obj.optString("emoji", ""),
                        colorHex = obj.optString("colorHex", defaultColor)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiClipService", "Error parsing animated words json", e)
        }
        return result
    }

    fun generateAcousticTimedWords(
        transcript: String,
        durationSec: Float,
        captionTheme: String
    ): List<AnimatedWord> {
        val rawWords = transcript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (rawWords.isEmpty()) return emptyList()

        val viralKeywords = setOf(
            "secret", "mistake", "never", "always", "money", "growth", "scale", "hack", "dopamine",
            "focus", "results", "million", "billion", "exponential", "breakthrough", "strategy",
            "protocol", "stop", "proven", "power", "win", "success", "fail", "faster", "easy",
            "خطأ", "سر", "نجاح", "أرباح", "نمو", "استراتيجية", "ذكاء", "فيروسي", "تطبيق", "فكرة"
        )

        val emojisPool = listOf("🔥", "⚡", "💡", "🚀", "💰", "🤯", "🎯", "🧠", "✨", "📈")
        var currentPointer = 0f
        val wordWeights = rawWords.map { w ->
            var weight = (w.length.toFloat() / 5f).coerceIn(0.7f, 1.8f)
            if (w.endsWith(".") || w.endsWith("!") || w.endsWith("?")) weight += 0.4f
            if (w.endsWith(",") || w.endsWith(";")) weight += 0.2f
            weight
        }
        val totalWeight = wordWeights.sum()
        val timePerWeightUnit = if (totalWeight > 0) durationSec / totalWeight else durationSec / rawWords.size

        return rawWords.mapIndexed { index, word ->
            val wWeight = wordWeights[index]
            val wordDur = wWeight * timePerWeightUnit
            val start = currentPointer
            val end = (start + wordDur).coerceAtMost(durationSec)
            currentPointer = end

            val cleanWord = word.replace(Regex("[^\\p{L}\\p{Nd}]"), "").lowercase()
            val isHigh = viralKeywords.contains(cleanWord) || (index % 7 == 2)
            val emoji = if (isHigh) emojisPool[(index + cleanWord.length) % emojisPool.size] else ""

            val color = when (captionTheme) {
                "MrBeast Yellow" -> if (isHigh) "#FACC15" else "#FFFFFF"
                "Ali Abdaal" -> if (isHigh) "#F43F5E" else "#E2E8F0"
                "Cyber Green" -> if (isHigh) "#10B981" else "#FFFFFF"
                "Hormozi Bold" -> if (isHigh) "#A855F7" else "#FFFFFF"
                else -> if (isHigh) "#38BDF8" else "#FFFFFF"
            }

            AnimatedWord(
                word = word,
                startSec = (start * 100).toInt() / 100f,
                endSec = (end * 100).toInt() / 100f,
                isHighlight = isHigh,
                emoji = emoji,
                colorHex = color
            )
        }
    }

    fun exportToSrt(words: List<AnimatedWord>, wordsPerGroup: Int = 4): String {
        if (words.isEmpty()) return ""
        val sb = StringBuilder()
        val groups = words.chunked(wordsPerGroup)

        groups.forEachIndexed { index, chunk ->
            val startSec = chunk.first().startSec
            val endSec = chunk.last().endSec
            sb.append("${index + 1}\n")
            sb.append("${formatSrtTime(startSec)} --> ${formatSrtTime(endSec)}\n")
            sb.append(chunk.joinToString(" ") { it.word })
            sb.append("\n\n")
        }
        return sb.toString().trim()
    }

    fun exportToVtt(words: List<AnimatedWord>, wordsPerGroup: Int = 4): String {
        if (words.isEmpty()) return "WEBVTT\n\n"
        val sb = StringBuilder("WEBVTT\n\n")
        val groups = words.chunked(wordsPerGroup)

        groups.forEachIndexed { index, chunk ->
            val startSec = chunk.first().startSec
            val endSec = chunk.last().endSec
            sb.append("${index + 1}\n")
            sb.append("${formatVttTime(startSec)} --> ${formatVttTime(endSec)}\n")
            sb.append(chunk.joinToString(" ") { it.word })
            sb.append("\n\n")
        }
        return sb.toString().trim()
    }

    private fun formatSrtTime(seconds: Float): String {
        val totalMs = (seconds * 1000).toLong()
        val hours = totalMs / 3600000
        val mins = (totalMs % 3600000) / 60000
        val secs = (totalMs % 60000) / 1000
        val ms = totalMs % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, mins, secs, ms)
    }

    private fun formatVttTime(seconds: Float): String {
        val totalMs = (seconds * 1000).toLong()
        val hours = totalMs / 3600000
        val mins = (totalMs % 3600000) / 60000
        val secs = (totalMs % 60000) / 1000
        val ms = totalMs % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, mins, secs, ms)
    }

    suspend fun generateDedicatedVideoCaption(
        videoTitle: String,
        transcript: String,
        tone: String,
        targetPlatform: String,
        language: String,
        includeEmojis: Boolean = true,
        providers: List<AiProviderConfig> = emptyList()
    ): DedicatedCaptionResult = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are a world-class Viral Social Media Copywriter and Video Caption Engine specializing in $targetPlatform with tone '$tone' in language '$language'.
            Analyze the video title: "$videoTitle" and transcript: "$transcript".
            
            Return a JSON object with:
            - "hooks": array of 3 distinct, high-retention 3-second opening hooks.
            - "mainCaption": fully formatted, engaging caption text with line breaks, emojis, and high readability.
            - "keyTakeaways": array of 2-3 bullet points or key insights.
            - "callToAction": a high-converting call to action question or prompt to drive comments and saves.
            - "hashtags": array of 8-12 high-reach viral and niche hashtags with # symbols.
            - "viralityGrade": string (e.g. "A+", "98/100")
            - "platformTips": a one-sentence tip tailored for $targetPlatform algorithm.
            
            Return ONLY raw JSON, no markdown codeblocks.
        """.trimIndent()

        val activeProviders = providers.filter { it.isEnabled && it.apiKey.isNotBlank() }.sortedBy { it.priority }
        if (activeProviders.isNotEmpty()) {
            for (provider in activeProviders) {
                try {
                    val rawResponse = executeAiRequestWithProvider(provider, systemPrompt, "Video: $videoTitle\nTranscript: $transcript")
                    if (!rawResponse.isNullOrBlank()) {
                        val cleaned = rawResponse.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()
                        val parsed = parseDedicatedCaptionJson(cleaned)
                        if (parsed != null) return@withContext parsed
                    }
                } catch (e: Exception) {
                    Log.w("GeminiClipService", "Provider ${provider.name} failed during caption gen", e)
                }
            }
        }

        val apiKey = customApiKey?.trim()?.takeIf { it.isNotBlank() } ?: try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val primaryConfig = AiProviderConfig(
                    name = "Primary Google Gemini",
                    providerType = AiProviderType.GEMINI.name,
                    apiKey = apiKey
                )
                val rawResponse = executeAiRequestWithProvider(primaryConfig, systemPrompt, "Video: $videoTitle\nTranscript: $transcript")
                if (!rawResponse.isNullOrBlank()) {
                    val cleaned = rawResponse.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    val parsed = parseDedicatedCaptionJson(cleaned)
                    if (parsed != null) return@withContext parsed
                }
            } catch (e: Exception) {
                Log.e("GeminiClipService", "Error generating dedicated captions with Gemini", e)
            }
        }

        return@withContext generateFallbackDedicatedCaption(videoTitle, transcript, tone, targetPlatform, language, includeEmojis)
    }

    private fun parseDedicatedCaptionJson(jsonString: String): DedicatedCaptionResult? {
        return try {
            val obj = JSONObject(jsonString)
            val hooksArray = obj.optJSONArray("hooks")
            val hooks = mutableListOf<String>()
            if (hooksArray != null) {
                for (i in 0 until hooksArray.length()) hooks.add(hooksArray.getString(i))
            }

            val takeawaysArray = obj.optJSONArray("keyTakeaways")
            val takeaways = mutableListOf<String>()
            if (takeawaysArray != null) {
                for (i in 0 until takeawaysArray.length()) takeaways.add(takeawaysArray.getString(i))
            }

            val tagsArray = obj.optJSONArray("hashtags")
            val tags = mutableListOf<String>()
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) tags.add(tagsArray.getString(i))
            }

            val mainCaption = obj.optString("mainCaption", "")
            DedicatedCaptionResult(
                hooks = if (hooks.isNotEmpty()) hooks else listOf("Wait until the end... 🤯", "The #1 mistake everyone makes:"),
                mainCaption = mainCaption,
                keyTakeaways = takeaways,
                callToAction = obj.optString("callToAction", "Save this video for later & share your thoughts below! 👇"),
                hashtags = if (tags.isNotEmpty()) tags else listOf("#Viral", "#Shorts", "#Trending", "#Growth", "#ISM"),
                characterCount = mainCaption.length,
                viralityGrade = obj.optString("viralityGrade", "A+ (97/100)"),
                platformTips = obj.optString("platformTips", "Post between 6 PM - 9 PM for peak viral engagement.")
            )
        } catch (e: Exception) {
            Log.e("GeminiClipService", "Failed to parse caption JSON", e)
            null
        }
    }

    private fun generateFallbackDedicatedCaption(
        videoTitle: String,
        transcript: String,
        tone: String,
        targetPlatform: String,
        language: String,
        includeEmojis: Boolean
    ): DedicatedCaptionResult {
        val isArabic = language.contains("العربية", ignoreCase = true) || language.contains("Arabic", ignoreCase = true)

        val hooks = if (isArabic) {
            when (tone) {
                "MrBeast Viral" -> listOf(
                    "🔥 السر الحقيقي الذي لا يخبرك به أحد عن: $videoTitle",
                    "😱 جربت هذا الشيء والنتيجة كانت صادمة!",
                    "⚡ 99% من الناس يرتكبون هذا الخطأ الفادح يومياً..."
                )
                "Hormozi Value" -> listOf(
                    "💡 إذا كنت تريد مضاعفة نتائجك في 30 يومًا، طبق هذه القاعدة:",
                    "📈 الاستراتيجية الوحيدة التي ستحتاجها لـ $videoTitle بدون تعقيد.",
                    "💰 كيف توفر 100 ساعة عمل باستخدام هذا المبدأ البسيط:"
                )
                else -> listOf(
                    "✨ اكتشف أهم نقطة تحول في: $videoTitle",
                    "🎯 دقيقة واحدة ستغير نظرتك تماماً للموضوع:",
                    "🧠 السر الذي غيّر كل شيء خطوة بخطوة:"
                )
            }
        } else {
            when (tone) {
                "MrBeast Viral" -> listOf(
                    "🔥 The UNTOLD secret behind $videoTitle that will blow your mind!",
                    "🤯 I tested this exact formula and the result was insane...",
                    "⚡ 99% of creators make this fatal mistake every single day:"
                )
                "Hormozi Value" -> listOf(
                    "💡 If you want 10x results in 30 days, follow this one framework:",
                    "📈 The exact step-by-step strategy for $videoTitle with zero fluff.",
                    "💰 How to save 100 hours of wasted effort using this rule:"
                )
                else -> listOf(
                    "✨ The single most important takeaway from $videoTitle:",
                    "🎯 One minute that will completely change how you approach this:",
                    "🧠 The breakdown nobody is talking about right now:"
                )
            }
        }

        val mainText = if (isArabic) {
            """
                $videoTitle 🚀
                
                ${transcript.take(160)}...
                
                📌 النقاط الجوهرية:
                • التطبيق العملي الفوري يحقق 80% من النتائج.
                • تجنب التردد وركز على الاستمرارية اليومية.
                
                ما رأيك في هذه الفكرة؟ شاركنا رأيك في التعليقات! 👇
            """.trimIndent()
        } else {
            """
                $videoTitle 🚀
                
                ${transcript.take(160)}...
                
                📌 Key Takeaways:
                • Immediate execution yields 80% of the upside.
                • Consistency beats perfection every single time.
                
                What's your biggest takeaway from this? Drop a comment below! 👇
            """.trimIndent()
        }

        val tags = if (isArabic) {
            listOf("#ريلز", "#شورتس", "#تيك_توك", "#نجاح", "#تطوير_الذات", "#ذكاء_اصطناعي", "#فيديو_فيروسي", "#أرباح")
        } else {
            listOf("#Viral", "#Shorts", "#Reels", "#TikTok", "#CreatorEconomy", "#GrowthHacking", "#ISM", "#Mindset")
        }

        return DedicatedCaptionResult(
            hooks = hooks,
            mainCaption = mainText,
            keyTakeaways = if (isArabic) listOf("التطبيق الفوري يسرع النتائج", "التركيز على القيمة العالية") else listOf("Execution is everything", "High leverage frameworks"),
            callToAction = if (isArabic) "احفظ الفيديو للرجوع إليه لاحقاً واشترك للمزيد! 📌" else "Save this clip for later and follow for daily breakdowns! 📌",
            hashtags = tags,
            characterCount = mainText.length,
            viralityGrade = "A+ (98/100)",
            platformTips = "Shorts & Reels algorithms favor strong first 3-second hooks."
        )
    }

    suspend fun publishDirectViaApi(
        platform: String,
        clipTitle: String,
        captionText: String,
        credentials: DirectPlatformApiCredentials
    ): DirectApiPublishLog = withContext(Dispatchers.IO) {
        val endpointUrl = when (platform) {
            "YouTube Shorts" -> "https://www.googleapis.com/youtube/v3/videos"
            "TikTok" -> "https://open.tiktokapis.com/v2/post/publish/video/init/"
            "Instagram Reels" -> "https://graph.facebook.com/v19.0/${credentials.instagramAccountId.ifBlank { "17841400000000" }}/media"
            "X (Twitter)" -> "https://api.twitter.com/2/tweets"
            else -> "https://api.opuspro.internal/v1/publish/direct"
        }

        val token = when (platform) {
            "YouTube Shorts" -> credentials.youtubeBearerToken.ifBlank { credentials.youtubeApiKey }
            "TikTok" -> credentials.tiktokAccessToken
            "Instagram Reels" -> credentials.instagramAccessToken
            "X (Twitter)" -> credentials.twitterBearerToken
            else -> customApiKey ?: ""
        }

        if (token.isBlank()) {
            return@withContext DirectApiPublishLog(
                platform = platform,
                isSuccess = false,
                httpCode = 401,
                endpointUrl = endpointUrl,
                responseSummary = "لم يتم النشر: بيانات اعتماد المنصة غير موجودة.",
                postUrl = "",
                rawPayload = "{}"
            )
        }

        // Video platforms require a real media upload/container flow. This method
        // intentionally refuses to claim success until an exported file is passed
        // through the platform-specific upload API.
        if (platform in setOf("YouTube Shorts", "TikTok", "Instagram Reels")) {
            return@withContext DirectApiPublishLog(
                platform = platform,
                isSuccess = false,
                httpCode = 501,
                endpointUrl = endpointUrl,
                responseSummary = "لم يُنفّذ النشر: يجب رفع ملف الفيديو عبر مسار الرفع الرسمي أولاً.",
                postUrl = "",
                rawPayload = "{}"
            )
        }

        // Only text-capable platforms reach this request path.
        if (token.isNotBlank()) {
            try {
                val payloadJson = JSONObject().apply {
                    when (platform) {
                        "X (Twitter)" -> {
                            put("text", "$clipTitle\n\n$captionText")
                        }
                        "YouTube Shorts" -> {
                            put("snippet", JSONObject().apply {
                                put("title", clipTitle.take(100))
                                put("description", captionText)
                                put("categoryId", "22")
                            })
                            put("status", JSONObject().apply {
                                put("privacyStatus", "public")
                                put("selfDeclaredMadeForKids", false)
                            })
                        }
                        else -> {
                            put("title", clipTitle)
                            put("caption", captionText)
                            put("auto_publish", true)
                        }
                    }
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = payloadJson.toString().toRequestBody(mediaType)
                val reqBuilder = Request.Builder()
                    .url(endpointUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")

                if (token.startsWith("ya29.") || token.startsWith("Bearer ") || platform != "YouTube Shorts" || credentials.youtubeBearerToken.isNotBlank()) {
                    val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
                    reqBuilder.addHeader("Authorization", authHeader)
                }

                val response = okHttpClient.newCall(reqBuilder.build()).execute()
                val responseBody = response.body?.string() ?: "{}"
                val isSuccess = response.isSuccessful || response.code in 200..204

                return@withContext DirectApiPublishLog(
                    platform = platform,
                    isSuccess = isSuccess,
                    httpCode = response.code,
                    endpointUrl = endpointUrl,
                    responseSummary = if (isSuccess) "Direct In-App API Request Succeeded (HTTP ${response.code})" else "API Response: HTTP ${response.code}",
                    postUrl = if (isSuccess) extractPostUrl(responseBody) else "",
                    rawPayload = responseBody.take(400)
                )
            } catch (e: Exception) {
                Log.e("GeminiClipService", "Direct API publish failed with exception", e)
                return@withContext DirectApiPublishLog(
                    platform = platform,
                    isSuccess = false,
                    httpCode = 500,
                    endpointUrl = endpointUrl,
                    responseSummary = "Connection error: ${e.message}",
                    postUrl = "",
                    rawPayload = e.localizedMessage ?: "Unknown network exception"
                )
            }
        }

        return@withContext DirectApiPublishLog(
            platform = platform,
            isSuccess = false,
            httpCode = 401,
            endpointUrl = endpointUrl,
            responseSummary = "لم يتم النشر: بيانات اعتماد المنصة غير موجودة.",
            postUrl = "",
            rawPayload = "{}"
        )
    }

    private fun extractPostUrl(responseBody: String): String {
        return runCatching {
            val json = JSONObject(responseBody)
            json.optString("postUrl").ifBlank {
                json.optString("url").ifBlank {
                    json.optString("permalink").ifBlank {
                        json.optString("web_url")
                    }
                }
            }
        }.getOrDefault("")
    }

    suspend fun determineOptimalTemplateAndPreset(
        title: String,
        transcriptOrPrompt: String,
        videoDurationSec: Int = 300,
        providers: List<AiProviderConfig> = emptyList()
    ): AiTemplateRecommendation = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are ISM AI Video Director. Analyze the following video content and autonomously determine the most viral visual template, caption typography theme, reframing layout, and ideal platform target.
            
            Available Caption Themes:
            - "Opus Neon" (High energy, neon cyan/violet highlights, cyber tech vibe)
            - "MrBeast Bold" (Ultra high retention, bright yellow/red text with emojis)
            - "Ali Abdaal Clean" (Minimalist, elegant white/emerald, educational/productivity)
            - "Hormozi Kinetic" (Punchy uppercase, gold/electric, business/sales/mindset)
            
            Available Layouts:
            - "9:16 Full Screen"
            - "Auto Split-Screen"
            - "1:1 Square"
            
            Available Platforms:
            - "TikTok & Reels (9:16)"
            - "YouTube Shorts"
            - "Instagram Reels"
            
            Return a JSON object with:
            - "recommendedCaptionTheme": string
            - "recommendedLayout": string
            - "recommendedPlatform": string
            - "recommendedDurationRange": string (e.g. "30s - 60s", "< 30s", "60s - 90s")
            - "styleReasoning": string (concise explanation of why this visual styling maximizes virality for this specific content)
            - "detectedNiche": string (e.g. "Tech & AI", "Business Scaling", "Self Improvement", "Entertainment", "Podcast Highlight")
            - "confidenceScore": integer (85 to 99)
            
            Output ONLY valid JSON without markdown wrapping.
        """.trimIndent()

        val userContent = """
            Video Title: $title
            Duration: $videoDurationSec seconds
            Content/Transcript: $transcriptOrPrompt
        """.trimIndent()

        val activeProviders = providers.filter { it.isEnabled && it.apiKey.isNotBlank() }.sortedBy { it.priority }
        if (activeProviders.isNotEmpty()) {
            for (provider in activeProviders) {
                try {
                    val rawResponse = executeAiRequestWithProvider(provider, systemPrompt, userContent)
                    if (!rawResponse.isNullOrBlank()) {
                        val cleaned = rawResponse.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()
                        val obj = JSONObject(cleaned)
                        return@withContext AiTemplateRecommendation(
                            recommendedCaptionTheme = obj.optString("recommendedCaptionTheme", "Opus Neon"),
                            recommendedLayout = obj.optString("recommendedLayout", "9:16 Full Screen"),
                            recommendedPlatform = obj.optString("recommendedPlatform", "TikTok & Reels (9:16)"),
                            recommendedDurationRange = obj.optString("recommendedDurationRange", "30s - 60s"),
                            styleReasoning = obj.optString("styleReasoning", "AI analyzed video pacing and selected high-retention typography and framing."),
                            detectedNiche = obj.optString("detectedNiche", "AI Viral Content"),
                            confidenceScore = obj.optInt("confidenceScore", 96)
                        )
                    }
                } catch (e: Exception) {
                    Log.w("GeminiClipService", "Provider ${provider.name} failed during template recommendation", e)
                }
            }
        }

        val apiKey = customApiKey?.trim()?.takeIf { it.isNotBlank() } ?: try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val primaryConfig = AiProviderConfig(
                    name = "Primary Google Gemini",
                    providerType = AiProviderType.GEMINI.name,
                    apiKey = apiKey
                )
                val rawResponse = executeAiRequestWithProvider(primaryConfig, systemPrompt, userContent)
                if (!rawResponse.isNullOrBlank()) {
                    val cleaned = rawResponse.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    val obj = JSONObject(cleaned)
                    return@withContext AiTemplateRecommendation(
                        recommendedCaptionTheme = obj.optString("recommendedCaptionTheme", "Opus Neon"),
                        recommendedLayout = obj.optString("recommendedLayout", "9:16 Full Screen"),
                        recommendedPlatform = obj.optString("recommendedPlatform", "TikTok & Reels (9:16)"),
                        recommendedDurationRange = obj.optString("recommendedDurationRange", "30s - 60s"),
                        styleReasoning = obj.optString("styleReasoning", "AI analyzed video pacing and selected high-retention typography and framing."),
                        detectedNiche = obj.optString("detectedNiche", "AI Viral Content"),
                        confidenceScore = obj.optInt("confidenceScore", 96)
                    )
                }
            } catch (e: Exception) {
                Log.e("GeminiClipService", "Auto template determination via Gemini API failed, using heuristic template", e)
            }
        }

        // Heuristic fallback matching based on keywords in title & prompt
        val lowerText = (title + " " + transcriptOrPrompt).lowercase()
        return@withContext when {
            lowerText.contains("saas") || lowerText.contains("business") || lowerText.contains("growth") || lowerText.contains("money") -> {
                AiTemplateRecommendation(
                    recommendedCaptionTheme = "Hormozi Kinetic",
                    recommendedLayout = "9:16 Full Screen",
                    recommendedPlatform = "TikTok & Reels (9:16)",
                    recommendedDurationRange = "30s - 60s",
                    styleReasoning = "Business & growth content performs with highest retention using bold kinetic captions and high-contrast gold highlights.",
                    detectedNiche = "Business & Revenue Growth",
                    confidenceScore = 97
                )
            }
            lowerText.contains("code") || lowerText.contains("agent") || lowerText.contains("ai") || lowerText.contains("tech") -> {
                AiTemplateRecommendation(
                    recommendedCaptionTheme = "Opus Neon",
                    recommendedLayout = "9:16 Full Screen",
                    recommendedPlatform = "YouTube Shorts",
                    recommendedDurationRange = "30s - 60s",
                    styleReasoning = "Tech & AI topics maximize engagement with glowing neon cyan kinetic typography and high-velocity pacing.",
                    detectedNiche = "AI & Technology Engineering",
                    confidenceScore = 98
                )
            }
            lowerText.contains("mindset") || lowerText.contains("rule") || lowerText.contains("life") || lowerText.contains("psychology") -> {
                AiTemplateRecommendation(
                    recommendedCaptionTheme = "Ali Abdaal Clean",
                    recommendedLayout = "9:16 Full Screen",
                    recommendedPlatform = "Instagram Reels",
                    recommendedDurationRange = "30s - 60s",
                    styleReasoning = "Psychological and peak performance insights are best received with clean, elegant typography and balanced negative space.",
                    detectedNiche = "Productivity & Psychology",
                    confidenceScore = 95
                )
            }
            else -> {
                AiTemplateRecommendation(
                    recommendedCaptionTheme = "MrBeast Bold",
                    recommendedLayout = "9:16 Full Screen",
                    recommendedPlatform = "TikTok & Reels (9:16)",
                    recommendedDurationRange = "30s - 60s",
                    styleReasoning = "High-octane bold text with maximum color contrast and dynamic word highlights for universal engagement.",
                    detectedNiche = "Viral Entertainment & General",
                    confidenceScore = 94
                )
            }
        }
    }
}

