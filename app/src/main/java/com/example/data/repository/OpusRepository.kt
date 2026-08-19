package com.example.data.repository

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.db.OpusDatabase
import com.example.data.model.AiProviderConfig
import com.example.data.model.AiUsageAggregate
import com.example.data.model.AiUsageEntity
import com.example.data.model.AiProviderType
import com.example.data.model.AiTemplateRecommendation
import com.example.data.model.AnimatedWord
import com.example.data.model.AutoPublishConfig
import com.example.data.model.AutoPublishResult
import com.example.data.model.Clip
import com.example.data.model.ClipGenerationData
import com.example.data.model.DedicatedCaptionResult
import com.example.data.model.DirectApiPublishLog
import com.example.data.model.DirectPlatformApiCredentials
import com.example.data.model.GoogleFlowCreditInfo
import com.example.data.model.GatewayConfig
import com.example.data.model.GatewaySnapshot
import com.example.data.model.Project
import com.example.data.model.PipelineCheckpointEntity
import com.example.data.model.ProcessingJobEntity
import com.example.data.model.RepurposingHistoryEntity
import com.example.data.model.SocialPostCopy
import com.example.data.model.UserCreditState
import com.example.data.model.VideoProcessingCacheEntity
import com.example.data.model.ViralScoreMetricEntity
import com.example.data.remote.GeminiClipService
import com.example.data.remote.ProcessingGatewayClient
import com.example.data.remote.SpeechToTextService
import com.example.data.remote.SocialGatewayClient
import com.example.data.video.CaptionSidecarWriter
import com.example.data.video.FaceTrackingAnalyzer
import com.example.data.video.LocalMediaAnalyzer
import com.example.data.video.Media3VideoProcessor
import com.example.data.video.MediaUriStabilizer
import com.example.data.worker.VideoProcessingWorker
import com.example.domain.analysis.AnalysisValidator
import com.example.domain.analysis.Transcript
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject

sealed class ProcessingStep(val stepNumber: Int, val title: String, val description: String) {
    object Idle : ProcessingStep(0, "Ready", "Waiting for video input...")
    object Transcribing : ProcessingStep(1, "AI Speech Transcription", "Analyzing audio waveforms & separating multi-speaker tracks...")
    object ScanningHooks : ProcessingStep(2, "Virality Curve Scanning", "Evaluating retention probability, hook tension & emotional peaks...")
    object CalculatingScores : ProcessingStep(3, "Virality Score™ Calculation", "Validating explainable factors from transcript, timing and media signals...")
    object StylingCaptions : ProcessingStep(4, "Dynamic Caption & B-Roll", "Synthesizing karaoke highlights, auto emojis & 9:16 reframe...")
    object Completed : ProcessingStep(5, "Clips Generated", "Your viral shorts are ready in ISM Studio!")
}

class OpusRepository(context: Context) {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val db = OpusDatabase.getDatabase(context)
    private val projectDao = db.projectDao()
    private val aiUsageDao = db.aiUsageDao()
    private val pipelineCheckpointDao = db.pipelineCheckpointDao()
    private val clipDao = db.clipDao()
    private val videoProcessingCacheDao = db.videoProcessingCacheDao()
    private val viralScoreMetricDao = db.viralScoreMetricDao()
    private val repurposingHistoryDao = db.repurposingHistoryDao()
    private val processingJobDao = db.processingJobDao()
    private val appContext = context.applicationContext
    private val secureKeyManager = com.example.domain.security.SecureKeyManager(appContext)
    val geminiService = GeminiClipService(appContext)
    private val videoProcessor = Media3VideoProcessor(appContext)
    private val localMediaAnalyzer = LocalMediaAnalyzer(appContext)
    private val speechToTextService = SpeechToTextService(appContext)
    private val faceTrackingAnalyzer = FaceTrackingAnalyzer(appContext)
    val aiRouter = com.example.domain.ai.IntelligentAiRouter(
        listOf(
            com.example.domain.ai.ProductionGeminiProvider(
                geminiService,
                AiProviderConfig(
                    id = "gemini-prod",
                    name = "Google Gemini 2.5 Flash",
                    providerType = AiProviderType.GEMINI.name,
                    apiKey = com.example.BuildConfig.GEMINI_API_KEY,
                    modelName = "gemini-2.5-flash",
                    priority = 1,
                    isEnabled = true
                )
            )
        )
    )

    private val apiPrefs = context.getSharedPreferences("opus_api_settings", Context.MODE_PRIVATE)

    init {
        migrateLegacyDemoMetrics()
    }

    private fun migrateLegacyDemoMetrics() {
        if (apiPrefs.getBoolean("real_metrics_migrated_v1", false)) return

        // Remove only the exact values shipped as demo data. Preserve any quota
        // values the user may have configured after the first installation.
        val hasLegacyDemoCredits = apiPrefs.getInt("total_credits_minutes", Int.MIN_VALUE) == 180 &&
            apiPrefs.getInt("used_credits_minutes", Int.MIN_VALUE) == 35 &&
            apiPrefs.getInt("total_requests_limit", Int.MIN_VALUE) == 1500 &&
            apiPrefs.getInt("used_requests_count", Int.MIN_VALUE) == 84

        val editor = apiPrefs.edit().putBoolean("real_metrics_migrated_v1", true)
        if (hasLegacyDemoCredits) {
            editor.remove("total_credits_minutes")
                .remove("used_credits_minutes")
                .remove("total_requests_limit")
                .remove("used_requests_count")
                .remove("plan_name")
                .remove("rpm_limit")
                .remove("active_provider_name")
                .remove("last_reset_timestamp")
        }
        editor.apply()
    }

    private val _customApiKey = MutableStateFlow(readSecret(apiPrefs, "custom_gemini_key"))
    val customApiKey = _customApiKey.asStateFlow()

    // Google Flow Credit Balance Tracking
    private val _googleFlowCredits = MutableStateFlow(loadGoogleFlowCredits())
    val googleFlowCredits = _googleFlowCredits.asStateFlow()

    // Multi-Provider Failover Pool
    private val _aiProviders = MutableStateFlow<List<AiProviderConfig>>(loadAiProviders())
    val aiProviders = _aiProviders.asStateFlow()

    suspend fun removeLegacyDemoDataIfPresent() = withContext(Dispatchers.IO) {
        val demo = projectDao.getProjectByIdSync(1L)
        val isLegacyDemo = demo?.title == "The Psychology of Peak Human Performance & Focus Protocol" &&
            demo.sourceUrl == "https://www.youtube.com/watch?v=huberman_focus_peak"

        if (isLegacyDemo) {
            clipDao.deleteClipsForProject(demo.id)
            viralScoreMetricDao.deleteScoresForProject(demo.id)
            repurposingHistoryDao.deleteHistoryForProject(demo.id)
            videoProcessingCacheDao.deleteCacheByUrl(demo.sourceUrl)
            projectDao.deleteProjectById(demo.id)
        }
    }

    private fun loadGoogleFlowCredits(): GoogleFlowCreditInfo {
        return GoogleFlowCreditInfo(
            totalCreditsMinutes = apiPrefs.getInt("total_credits_minutes", 0),
            usedCreditsMinutes = apiPrefs.getInt("used_credits_minutes", 0),
            totalRequestsLimit = apiPrefs.getInt("total_requests_limit", 0),
            usedRequestsCount = apiPrefs.getInt("used_requests_count", 0),
            planName = apiPrefs.getString("plan_name", "غير مُكوّن") ?: "غير مُكوّن",
            rpmLimit = apiPrefs.getInt("rpm_limit", 0),
            isAutoFailoverEnabled = apiPrefs.getBoolean("is_failover_enabled", false),
            activeProviderName = apiPrefs.getString("active_provider_name", "غير متاح") ?: "غير متاح",
            lastResetTimestamp = apiPrefs.getLong("last_reset_timestamp", System.currentTimeMillis())
        )
    }

    suspend fun deductGoogleFlowCredits(minutes: Int) = withContext(Dispatchers.IO) {
        val current = _googleFlowCredits.value
        val updated = current.copy(
            usedCreditsMinutes = (current.usedCreditsMinutes + minutes).coerceAtMost(current.totalCreditsMinutes + 500),
            usedRequestsCount = current.usedRequestsCount + 1
        )
        saveGoogleFlowCredits(updated)
    }

    suspend fun resetGoogleFlowCredits() = withContext(Dispatchers.IO) {
        val reset = GoogleFlowCreditInfo(
            totalCreditsMinutes = 0,
            usedCreditsMinutes = 0,
            totalRequestsLimit = 0,
            usedRequestsCount = 0,
            planName = "غير مُكوّن",
            rpmLimit = 0,
            isAutoFailoverEnabled = false,
            activeProviderName = "غير متاح",
            lastResetTimestamp = System.currentTimeMillis()
        )
        saveGoogleFlowCredits(reset)
    }

    suspend fun saveGoogleFlowCredits(info: GoogleFlowCreditInfo) = withContext(Dispatchers.IO) {
        apiPrefs.edit()
            .putInt("total_credits_minutes", info.totalCreditsMinutes)
            .putInt("used_credits_minutes", info.usedCreditsMinutes)
            .putInt("total_requests_limit", info.totalRequestsLimit)
            .putInt("used_requests_count", info.usedRequestsCount)
            .putString("plan_name", info.planName)
            .putInt("rpm_limit", info.rpmLimit)
            .putBoolean("is_failover_enabled", info.isAutoFailoverEnabled)
            .putString("active_provider_name", info.activeProviderName)
            .putLong("last_reset_timestamp", info.lastResetTimestamp)
            .apply()
        _googleFlowCredits.value = info
    }

    private fun readSecret(prefs: android.content.SharedPreferences, key: String): String {
        val encryptedKey = "${key}_encrypted"
        val encrypted = prefs.getString(encryptedKey, null).orEmpty()
        if (encrypted.isNotBlank()) {
            return runCatching { secureKeyManager.decrypt(encrypted) }.getOrDefault("")
        }

        // Migrate legacy plaintext values once, then remove the insecure copy.
        val legacy = prefs.getString(key, null).orEmpty()
        if (legacy.isNotBlank()) {
            runCatching {
                val protectedValue = secureKeyManager.encrypt(legacy)
                prefs.edit().putString(encryptedKey, protectedValue).remove(key).apply()
            }
        }
        return legacy
    }

    private fun putSecret(editor: android.content.SharedPreferences.Editor, key: String, value: String) {
        val encryptedKey = "${key}_encrypted"
        if (value.isBlank()) {
            editor.remove(key).remove(encryptedKey)
        } else {
            editor.putString(encryptedKey, secureKeyManager.encrypt(value)).remove(key)
        }
    }

    private fun loadAiProviders(): List<AiProviderConfig> {
        val json = readSecret(apiPrefs, "ai_providers_json").takeIf { it.isNotBlank() }
        if (!json.isNullOrBlank()) {
            try {
                val listType = Types.newParameterizedType(List::class.java, AiProviderConfig::class.java)
                val adapter: JsonAdapter<List<AiProviderConfig>> = moshi.adapter(listType)
                val list = adapter.fromJson(json)
                val configuredProviders = list.orEmpty().filter { it.apiKey.isNotBlank() }
                if (configuredProviders.isNotEmpty()) return configuredProviders
            } catch (e: Exception) {
                Log.e("OpusRepository", "Failed to parse saved ai providers", e)
            }
        }
        val currentGeminiKey = readSecret(apiPrefs, "custom_gemini_key")
        return if (currentGeminiKey.isBlank()) {
            emptyList()
        } else {
            listOf(
                AiProviderConfig(
                    id = "gemini_primary",
                    name = "Google Gemini (Gemini 2.5 Flash)",
                    providerType = AiProviderType.GEMINI.name,
                    apiKey = currentGeminiKey,
                    modelName = "gemini-2.5-flash",
                    priority = 1,
                    isEnabled = true,
                    creditUnit = "",
                    balanceStatus = "مفتاح مُضاف"
                )
            )
        }
    }

    suspend fun updateProviderKey(providerId: String, newKey: String, model: String? = null) = withContext(Dispatchers.IO) {
        val updated = _aiProviders.value.map { provider ->
            if (provider.id == providerId) {
                provider.copy(
                    apiKey = newKey.trim(),
                    modelName = model?.trim() ?: provider.modelName,
                    isEnabled = newKey.isNotBlank()
                )
            } else provider
        }
        saveAiProviders(updated)
    }

    suspend fun saveAiProviders(providers: List<AiProviderConfig>) = withContext(Dispatchers.IO) {
        val listType = Types.newParameterizedType(List::class.java, AiProviderConfig::class.java)
        val adapter: JsonAdapter<List<AiProviderConfig>> = moshi.adapter(listType)
        val json = adapter.toJson(providers)
        val providersEditor = apiPrefs.edit()
        putSecret(providersEditor, "ai_providers_json", json)
        providersEditor.apply()
        _aiProviders.value = providers

        // Sync primary gemini key
        val primaryGemini = providers.find { it.providerType == AiProviderType.GEMINI.name && it.isEnabled && it.apiKey.isNotBlank() }
        if (primaryGemini != null) {
            geminiService.customApiKey = primaryGemini.apiKey
            _customApiKey.value = primaryGemini.apiKey
            val geminiKeyEditor = apiPrefs.edit()
            putSecret(geminiKeyEditor, "custom_gemini_key", primaryGemini.apiKey)
            geminiKeyEditor.apply()
        }
    }

    suspend fun addOrUpdateAiProvider(provider: AiProviderConfig) = withContext(Dispatchers.IO) {
        val currentList = _aiProviders.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == provider.id }
        if (index != -1) {
            currentList[index] = provider
        } else {
            currentList.add(provider)
        }
        saveAiProviders(currentList)
    }

    suspend fun removeAiProvider(providerId: String) = withContext(Dispatchers.IO) {
        val currentList = _aiProviders.value.filter { it.id != providerId }
        saveAiProviders(currentList)
    }

    suspend fun toggleAiProvider(providerId: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val currentList = _aiProviders.value.map {
            if (it.id == providerId) it.copy(isEnabled = isEnabled) else it
        }
        saveAiProviders(currentList)
    }

    suspend fun testAiProvider(provider: AiProviderConfig): Pair<Boolean, String> {
        return geminiService.testProviderConnection(provider)
    }


    private val publishPrefs = context.getSharedPreferences("opus_publish_settings", Context.MODE_PRIVATE)
    private val _autoPublishConfig = MutableStateFlow(
        AutoPublishConfig(
            isEnabled = publishPrefs.getBoolean("auto_publish_enabled", false),
            targetPlatforms = publishPrefs.getStringSet("target_platforms", setOf("TikTok", "YouTube Shorts", "Instagram Reels")) ?: setOf("TikTok", "YouTube Shorts", "Instagram Reels"),
            autoOpenShareSheet = publishPrefs.getBoolean("auto_share_sheet", true),
            autoCopyCaption = publishPrefs.getBoolean("auto_copy_caption", true),
            webhookUrl = publishPrefs.getString("webhook_url", "") ?: "",
            scheduledSlot = publishPrefs.getString("scheduled_slot", "Instant (Immediately after AI generation)") ?: "Instant (Immediately after AI generation)"
        )
    )
    val autoPublishConfig = _autoPublishConfig.asStateFlow()

    private val directApiPrefs = context.getSharedPreferences("opus_direct_platform_apis", Context.MODE_PRIVATE)
    private val _directApiCredentials = MutableStateFlow(
        DirectPlatformApiCredentials(
            youtubeApiKey = readSecret(directApiPrefs, "yt_api_key"),
            youtubeBearerToken = readSecret(directApiPrefs, "yt_bearer_token"),
            tiktokAccessToken = readSecret(directApiPrefs, "tiktok_access_token"),
            instagramAccessToken = readSecret(directApiPrefs, "ig_access_token"),
            instagramAccountId = readSecret(directApiPrefs, "ig_account_id"),
            twitterBearerToken = readSecret(directApiPrefs, "x_bearer_token"),
            isDirectApiEnabled = directApiPrefs.getBoolean("direct_api_enabled", true)
        )
    )
    val directApiCredentials = _directApiCredentials.asStateFlow()

    private val gatewayPrefs = context.getSharedPreferences("ism_gateway_settings", Context.MODE_PRIVATE)
    private val gatewayClient = SocialGatewayClient()
    private val _gatewayConfig = MutableStateFlow(
        GatewayConfig(
            baseUrl = gatewayPrefs.getString("base_url", "") ?: "",
            token = readSecret(gatewayPrefs, "gateway_token")
        )
    )
    val gatewayConfig = _gatewayConfig.asStateFlow()
    private val _gatewaySnapshot = MutableStateFlow<GatewaySnapshot?>(null)
    val gatewaySnapshot = _gatewaySnapshot.asStateFlow()
    private val _gatewayError = MutableStateFlow("")
    val gatewayError = _gatewayError.asStateFlow()

    suspend fun saveGatewayConfig(config: GatewayConfig) = withContext(Dispatchers.IO) {
        val editor = gatewayPrefs.edit().putString("base_url", config.baseUrl.trim())
        putSecret(editor, "gateway_token", config.token.trim())
        editor.apply()
        _gatewayConfig.value = config.copy(baseUrl = config.baseUrl.trim(), token = config.token.trim())
        _gatewayError.value = ""
    }

    suspend fun testGatewayConnection(): Result<String> = withContext(Dispatchers.IO) {
        val result = gatewayClient.testConnection(_gatewayConfig.value)
        result.exceptionOrNull()?.let { _gatewayError.value = it.localizedMessage ?: "تعذر الاتصال بـ Gateway" }
        result
    }

    suspend fun refreshGatewayStatus(): Result<GatewaySnapshot> = withContext(Dispatchers.IO) {
        val result = gatewayClient.loadSnapshot(_gatewayConfig.value)
        result.onSuccess {
            _gatewaySnapshot.value = it
            _gatewayError.value = ""
        }.onFailure {
            _gatewayError.value = it.localizedMessage ?: "تعذر تحميل حالة Gateway"
        }
        result
    }

    private val _recentPublishLogs = MutableStateFlow<List<DirectApiPublishLog>>(emptyList())
    val recentPublishLogs = _recentPublishLogs.asStateFlow()

    suspend fun saveDirectApiCredentials(creds: DirectPlatformApiCredentials) = withContext(Dispatchers.IO) {
        val credentialsEditor = directApiPrefs.edit()
        putSecret(credentialsEditor, "yt_api_key", creds.youtubeApiKey.trim())
        putSecret(credentialsEditor, "yt_bearer_token", creds.youtubeBearerToken.trim())
        putSecret(credentialsEditor, "tiktok_access_token", creds.tiktokAccessToken.trim())
        putSecret(credentialsEditor, "ig_access_token", creds.instagramAccessToken.trim())
        putSecret(credentialsEditor, "ig_account_id", creds.instagramAccountId.trim())
        putSecret(credentialsEditor, "x_bearer_token", creds.twitterBearerToken.trim())
        credentialsEditor.putBoolean("direct_api_enabled", creds.isDirectApiEnabled).apply()
        _directApiCredentials.value = creds
    }

    suspend fun generateDedicatedCaption(
        videoTitle: String,
        transcript: String,
        tone: String,
        targetPlatform: String,
        language: String,
        includeEmojis: Boolean = true
    ): DedicatedCaptionResult {
        return geminiService.generateDedicatedVideoCaption(
            videoTitle = videoTitle,
            transcript = transcript,
            tone = tone,
            targetPlatform = targetPlatform,
            language = language,
            includeEmojis = includeEmojis,
            providers = _aiProviders.value
        )
    }

    private fun currentGeminiRouter(): com.example.domain.ai.IntelligentAiRouter {
        val configuredGeminiProviders = _aiProviders.value
            .filter { it.isEnabled && it.apiKey.isNotBlank() && it.providerType == AiProviderType.GEMINI.name }
            .sortedBy { it.priority }
            .map { config ->
                com.example.domain.ai.ProductionGeminiProvider(geminiService, config)
            }
        return com.example.domain.ai.IntelligentAiRouter(configuredGeminiProviders)
    }

    suspend fun executeAiEditingCommand(
        commandPrompt: String,
        clipTitle: String,
        currentTranscript: String,
        currentViralityScore: Int
    ): String = withContext(Dispatchers.IO) {
        val result = currentGeminiRouter().routeExecutionWithFailover("AI Editing Command") { provider ->
            provider.executeAiEditingCommand(
                commandPrompt = commandPrompt,
                clipTitle = clipTitle,
                currentTranscript = currentTranscript,
                currentViralityScore = currentViralityScore
            )
        }
        when (result) {
            is com.example.domain.ai.AiExecutionResult.Success -> {
                recordAiUsage(
                    AiUsageEntity(
                        provider = result.providerName,
                        model = _aiProviders.value.firstOrNull { it.name == result.providerName }?.modelName ?: "configured-model",
                        requestType = "editing_command",
                        inputUnits = null,
                        outputUnits = result.tokensUsed.takeIf { it > 0 },
                        latencyMs = result.latencyMs,
                        success = true,
                        estimatedCostUsd = result.estimatedCostUsd.takeIf { it > 0 },
                        isEstimate = result.tokensUsed <= 0
                    )
                )
                result.data
            }
            is com.example.domain.ai.AiExecutionResult.Failure -> {
                recordAiUsage(
                    AiUsageEntity(
                        provider = result.providerName,
                        model = _aiProviders.value.firstOrNull { it.name == result.providerName }?.modelName ?: "configured-model",
                        requestType = "editing_command",
                        latencyMs = 0,
                        success = false,
                        errorMessage = result.errorMessage
                    )
                )
                throw IllegalStateException("لم يُنفّذ أمر التحرير: ${result.errorMessage}")
            }
        }
    }

    suspend fun processVideoAndGenerateClips(
        projectId: Long,
        videoTitle: String,
        durationSec: Int,
        userNicheHint: String,
        targetPlatform: String,
        captionStyle: String,
        requestedClipCount: Int
    ): List<Clip> = withContext(Dispatchers.IO) {
        val clipsData = geminiService.analyzeAndGenerateClips(
            title = videoTitle,
            sourceUrl = "pipeline_process_$projectId",
            transcriptOrPrompt = userNicheHint,
            durationMinutes = (durationSec / 60).coerceAtLeast(1),
            providers = _aiProviders.value
        )
        val entities = clipsData.map { clipData ->
            createClipEntity(
                projectId = projectId,
                data = clipData,
                captionTheme = captionStyle
            )
        }
        clipDao.insertClips(entities)
        return@withContext entities
    }

    suspend fun determineOptimalTemplate(
        title: String,
        transcript: String,
        durationSec: Int = 300
    ): AiTemplateRecommendation {
        return geminiService.determineOptimalTemplateAndPreset(
            title = title,
            transcriptOrPrompt = transcript,
            videoDurationSec = durationSec,
            providers = _aiProviders.value
        )
    }

    suspend fun publishDirectlyToPlatform(
        clip: Clip,
        platform: String,
        customCaption: String? = null
    ): DirectApiPublishLog = withContext(Dispatchers.IO) {
        val captionToUse = customCaption ?: run {
            val listType = Types.newParameterizedType(List::class.java, SocialPostCopy::class.java)
            val socialAdapter: JsonAdapter<List<SocialPostCopy>> = moshi.adapter(listType)
            val socialList: List<SocialPostCopy> = try {
                socialAdapter.fromJson(clip.socialCopyJson) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val match = socialList.find { it.platform.equals(platform, ignoreCase = true) } ?: socialList.firstOrNull()
            "${match?.hook ?: clip.title}\n\n${match?.caption ?: clip.transcript.take(160)}\n\n${match?.hashtags?.joinToString(" ") ?: "#Viral"}"
        }

        val log = geminiService.publishDirectViaApi(
            platform = platform,
            clipTitle = clip.title,
            captionText = captionToUse,
            credentials = _directApiCredentials.value
        )

        _recentPublishLogs.value = listOf(log) + _recentPublishLogs.value.take(19)

        // Log into Room database user history
        val publishHistory = RepurposingHistoryEntity(
            projectId = clip.projectId,
            videoTitle = clip.title,
            sourceUrl = clip.exportPath.ifBlank { "Direct Platform Dispatch" },
            actionType = "DIRECT_API_PUBLISHED",
            clipsGeneratedCount = 1,
            highestViralScore = clip.viralityScore,
            estimatedTimeSavedMinutes = 0,
            status = if (log.isSuccess) "SUCCESS" else "FAILED",
            targetPlatform = platform,
            details = "Direct API dispatch to $platform: ${if (log.isSuccess) "Published successfully (HTTP ${log.httpCode})" else "Failed: ${log.responseSummary}"}",
            timestamp = System.currentTimeMillis()
        )
        repurposingHistoryDao.insertHistory(publishHistory)

        return@withContext log
    }

    suspend fun saveAutoPublishConfig(config: AutoPublishConfig) = withContext(Dispatchers.IO) {
        publishPrefs.edit()
            .putBoolean("auto_publish_enabled", config.isEnabled)
            .putStringSet("target_platforms", config.targetPlatforms)
            .putBoolean("auto_share_sheet", config.autoOpenShareSheet)
            .putBoolean("auto_copy_caption", config.autoCopyCaption)
            .putString("webhook_url", config.webhookUrl)
            .putString("scheduled_slot", config.scheduledSlot)
            .apply()
        _autoPublishConfig.value = config
    }

    init {
        geminiService.customApiKey = _customApiKey.value
    }

    suspend fun saveCustomApiKey(key: String) = withContext(Dispatchers.IO) {
        val trimmed = key.trim()
        val customKeyEditor = apiPrefs.edit()
        putSecret(customKeyEditor, "custom_gemini_key", trimmed)
        customKeyEditor.apply()
        geminiService.customApiKey = trimmed
        _customApiKey.value = trimmed
    }

    suspend fun clearCustomApiKey() = withContext(Dispatchers.IO) {
        apiPrefs.edit().remove("custom_gemini_key").remove("custom_gemini_key_encrypted").apply()
        geminiService.customApiKey = null
        _customApiKey.value = ""
    }

    suspend fun testApiKeyConnection(key: String): Pair<Boolean, String> {
        return geminiService.testApiKey(key)
    }

    private val _processingStep = MutableStateFlow<ProcessingStep>(ProcessingStep.Idle)
    val processingStep = _processingStep.asStateFlow()

    private val _userCreditState = MutableStateFlow(
        UserCreditState(
            creditsRemaining = _googleFlowCredits.value.remainingCreditsMinutes,
            totalProcessedMinutes = apiPrefs.getInt("real_total_processed_minutes", 0),
            currentPlan = apiPrefs.getString("plan_name", "غير مُكوّن") ?: "غير مُكوّن",
            renewalDate = apiPrefs.getString("renewal_date", "") ?: "",
            clipsCreatedCount = apiPrefs.getInt("real_clips_created_count", 0)
        )
    )
    val userCreditState = _userCreditState.asStateFlow()

    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()
    val allClips: Flow<List<Clip>> = clipDao.getAllClips()
    val favoriteClips: Flow<List<Clip>> = clipDao.getFavoriteClips()

    // Room Database Flows for Caching, Viral Scores & User History
    val repurposingHistory: Flow<List<RepurposingHistoryEntity>> = repurposingHistoryDao.getAllHistory()
    val recentRepurposingHistory: Flow<List<RepurposingHistoryEntity>> = repurposingHistoryDao.getRecentHistory(20)
    val cachedVideoMetadata: Flow<List<VideoProcessingCacheEntity>> = videoProcessingCacheDao.getAllCachedMetadata()
    val topViralScoreMetrics: Flow<List<ViralScoreMetricEntity>> = viralScoreMetricDao.getTopViralClips(15)
    val totalTimeSavedMinutes: Flow<Int?> = repurposingHistoryDao.getTotalEstimatedTimeSaved()
    val totalClipsFromHistory: Flow<Int?> = repurposingHistoryDao.getTotalClipsGenerated()
    val processingJobs: Flow<List<ProcessingJobEntity>> = processingJobDao.observeAll()

    suspend fun enqueueVideoProcessing(
        title: String,
        sourceUri: String,
        transcriptOrPrompt: String,
        durationMinutes: Int,
        targetPlatform: String,
        captionTheme: String,
        processingMode: String = "balanced"
    ): String = withContext(Dispatchers.IO) {
        require(title.isNotBlank()) { "عنوان الفيديو مطلوب." }
        require(sourceUri.isNotBlank()) { "مصدر الفيديو مطلوب." }
        require(durationMinutes > 0) { "مدة الفيديو غير صالحة." }

        val parsedSourceUri = Uri.parse(sourceUri)
        val stableSourceUri = if (parsedSourceUri.scheme == "content" || parsedSourceUri.scheme == "file") {
            runCatching {
                MediaUriStabilizer.copyForBackground(appContext, parsedSourceUri, title)
            }.getOrElse { error ->
                throw IllegalStateException("تعذر تثبيت ملف الفيديو قبل تشغيل المعالجة: ${error.localizedMessage ?: "مصدر غير قابل للقراءة"}", error)
            }.toString()
        } else {
            sourceUri
        }

        val jobId = UUID.randomUUID().toString()
        processingJobDao.upsert(
            ProcessingJobEntity(
                jobId = jobId,
                title = title,
                sourceUri = stableSourceUri,
                transcriptOrPrompt = transcriptOrPrompt,
                durationMinutes = durationMinutes,
                targetPlatform = targetPlatform,
                captionTheme = captionTheme
            )
        )

        val input = workDataOf(
            VideoProcessingWorker.KEY_JOB_ID to jobId,
            VideoProcessingWorker.KEY_TITLE to title,
            VideoProcessingWorker.KEY_SOURCE_URI to stableSourceUri,
            VideoProcessingWorker.KEY_TRANSCRIPT to transcriptOrPrompt,
            VideoProcessingWorker.KEY_DURATION_MINUTES to durationMinutes,
            VideoProcessingWorker.KEY_TARGET_PLATFORM to targetPlatform,
            VideoProcessingWorker.KEY_CAPTION_THEME to captionTheme,
            VideoProcessingWorker.KEY_PROCESSING_MODE to processingMode
        )
        val request = OneTimeWorkRequestBuilder<VideoProcessingWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("opus_video_processing")
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "opus_video_processing_$jobId",
            ExistingWorkPolicy.KEEP,
            request
        )
        jobId
    }

    suspend fun importRemoteProcessingResult(
        title: String,
        sourceUri: String,
        durationMinutes: Int,
        targetPlatform: String,
        captionTheme: String,
        clips: List<ProcessingGatewayClient.RemoteClip>,
        exportedPaths: Map<String, String>
    ): Long = withContext(Dispatchers.IO) {
        require(clips.isNotEmpty()) { "Gateway لم يُرجع مقاطع صالحة." }
        val projectId = projectDao.insertProject(
            Project(
                title = title,
                sourceUrl = sourceUri,
                sourceDurationSec = durationMinutes * 60,
                status = "COMPLETED",
                targetPlatform = targetPlatform,
                captionTheme = captionTheme,
                clipCount = clips.size,
                bestViralityScore = clips.maxOfOrNull { it.score } ?: 0
            )
        )
        val entities = clips.mapIndexed { index, clip ->
            Clip(
                projectId = projectId,
                title = clip.title.ifBlank { "Clip ${index + 1}" },
                startTimeSec = clip.startTimeSec,
                endTimeSec = clip.endTimeSec.coerceAtLeast(clip.startTimeSec),
                durationSec = clip.durationSec.coerceAtLeast(clip.endTimeSec - clip.startTimeSec),
                viralityScore = clip.score.coerceIn(0, 100),
                hookExplanation = "نتيجة تحليل Gateway/Python؛ الدرجة التفصيلية غير متاحة من المصدر.",
                transcript = clip.transcript,
                animatedCaptionsJson = "[]",
                bRollPromptsJson = "[]",
                socialCopyJson = "[]",
                exportPath = exportedPaths[clip.mediaUrl].orEmpty()
            )
        }
        clipDao.insertClips(entities)
        projectId
    }

    fun observeProcessingJob(jobId: String): Flow<ProcessingJobEntity?> = processingJobDao.observe(jobId)

    suspend fun cancelVideoProcessing(jobId: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(appContext).cancelUniqueWork("opus_video_processing_$jobId")
        processingJobDao.updateState(
            jobId = jobId,
            status = ProcessingJobEntity.STATUS_CANCELLED,
            progress = 0,
            stage = "CANCELLED",
            errorMessage = "تم إلغاء المهمة بواسطة المستخدم."
        )
    }

    fun getClipsForProject(projectId: Long): Flow<List<Clip>> = clipDao.getClipsForProject(projectId)
    fun getProjectById(projectId: Long): Flow<Project?> = projectDao.getProjectById(projectId)
    fun getClipById(clipId: Long): Flow<Clip?> = clipDao.getClipById(clipId)
    fun getViralScoresForProject(projectId: Long): Flow<List<ViralScoreMetricEntity>> = viralScoreMetricDao.getScoresForProject(projectId)
    fun getViralScoreForClip(clipId: Long): Flow<ViralScoreMetricEntity?> = viralScoreMetricDao.getScoreForClip(clipId)

    fun observePipelineCheckpoints(jobId: String): Flow<List<PipelineCheckpointEntity>> = pipelineCheckpointDao.observeJob(jobId)
    val recentAiUsage: Flow<List<AiUsageEntity>> = aiUsageDao.observeRecent()

    fun observeAiUsageAggregates(since: Long): Flow<List<AiUsageAggregate>> =
        aiUsageDao.observeAggregatesSince(since)

    fun observeRecentAiUsageAggregates(days: Int = 30): Flow<List<AiUsageAggregate>> =
        observeAiUsageAggregates(System.currentTimeMillis() - days.coerceAtLeast(1) * 86_400_000L)

    suspend fun recordAiUsage(record: AiUsageEntity) = withContext(Dispatchers.IO) {
        aiUsageDao.insert(record)
    }

    suspend fun savePipelineCheckpoint(
        jobId: String,
        projectId: Long,
        stage: String,
        status: String,
        progress: Float,
        message: String,
        errorMessage: String? = null,
        artifactPath: String? = null
    ) = withContext(Dispatchers.IO) {
        pipelineCheckpointDao.upsert(
            PipelineCheckpointEntity(
                jobId = jobId,
                projectId = projectId,
                stage = stage,
                status = status,
                progress = progress.coerceIn(0f, 1f),
                message = message,
                errorMessage = errorMessage,
                artifactPath = artifactPath
            )
        )
    }
    fun getCachedProcessingByUrl(sourceUrl: String): Flow<VideoProcessingCacheEntity?> = videoProcessingCacheDao.getCacheByUrl(sourceUrl)

    suspend fun clearVideoProcessingCache() = withContext(Dispatchers.IO) {
        videoProcessingCacheDao.clearAllCache()
    }

    suspend fun deleteHistoryEntry(id: Long) = withContext(Dispatchers.IO) {
        repurposingHistoryDao.deleteHistoryById(id)
    }

    suspend fun clearAllRepurposingHistory() = withContext(Dispatchers.IO) {
        repurposingHistoryDao.clearAllHistory()
    }

    suspend fun transcribeLocalMediaDetailed(sourceUrl: String, language: String? = null): Result<Transcript> = withContext(Dispatchers.IO) {
        val uri = sourceUrl.toMediaUriOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("مصدر transcription يجب أن يكون Uri محليًا."))
        val sttKey = _aiProviders.value.firstOrNull {
            it.isEnabled && it.apiKey.isNotBlank() &&
                (it.providerType == AiProviderType.OPENAI.name || it.providerType == AiProviderType.GROQ.name)
        }?.apiKey.orEmpty()
        speechToTextService.transcribe(uri, sttKey, language)
    }

    suspend fun transcribeLocalMedia(sourceUrl: String, language: String? = null): Result<String> =
        transcribeLocalMediaDetailed(sourceUrl, language).map { it.text }

    suspend fun processNewVideo(
        title: String,
        sourceUrl: String,
        transcriptOrPrompt: String,
        durationMinutes: Int,
        targetPlatform: String,
        captionTheme: String
    ): Long = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        _processingStep.value = ProcessingStep.Transcribing

        // Check local Room cache for metadata only; a cache hit never fabricates clips.
        val cachedEntry = if (sourceUrl.isNotBlank()) videoProcessingCacheDao.getCacheByUrlSync(sourceUrl) else null
        if (cachedEntry != null) videoProcessingCacheDao.recordCacheHit(cachedEntry.id)

        val inputMediaUri = sourceUrl.toMediaUriOrNull()
        val mediaAnalysis = inputMediaUri?.let { uri ->
            localMediaAnalyzer.analyze(uri).getOrElse { throw it }
        }
        val sourceMetadata = mediaAnalysis?.metadata
        val actualDurationSec = sourceMetadata?.durationSec ?: (durationMinutes * 60)
        val actualDurationMinutes = ((actualDurationSec + 59) / 60).coerceAtLeast(1)
        val effectiveTranscript = if (inputMediaUri != null && transcriptOrPrompt.isBlank()) {
            val sttKey = _aiProviders.value.firstOrNull {
                it.isEnabled && it.apiKey.isNotBlank() &&
                    (it.providerType == AiProviderType.OPENAI.name || it.providerType == AiProviderType.GROQ.name)
            }?.apiKey.orEmpty()
            if (sttKey.isNotBlank()) {
                speechToTextService.transcribe(inputMediaUri, sttKey).getOrElse { throw it }.text
            } else {
                // Gemini can receive the local video file directly; transcription is optional.
                ""
            }
        } else transcriptOrPrompt.trim()

        _processingStep.value = ProcessingStep.ScanningHooks
        val aiClips = geminiService.analyzeAndGenerateClips(
            title = title,
            sourceUrl = sourceUrl,
            transcriptOrPrompt = effectiveTranscript,
            durationMinutes = actualDurationMinutes,
            providers = _aiProviders.value,
            videoUri = inputMediaUri?.takeIf { it.scheme == "content" || it.scheme == "file" }
        ).map(AnalysisValidator::normalizeScores)
            .distinctBy { "${it.startTimeSec}:${it.endTimeSec}" }
            .take(10)
        val validation = AnalysisValidator.validateClips(aiClips, actualDurationSec)
        if (!validation.isValid) {
            throw IllegalStateException(
                "مخرجات تحليل المقاطع غير صالحة: ${validation.issues.take(5).joinToString { it.message }}"
            )
        }
        val clipsData = aiClips
        if (clipsData.isEmpty()) {
            _processingStep.value = ProcessingStep.Idle
            throw IllegalStateException(
                "لم يُرجع مزود الذكاء الاصطناعي مقاطع حقيقية. أضف مفتاحاً صالحاً ونصاً أو فيديو قابلاً للتحليل."
            )
        }

        recordAiUsage(
            AiUsageEntity(
                provider = _aiProviders.value.firstOrNull { it.isEnabled && it.apiKey.isNotBlank() }?.name ?: "configured-provider",
                model = _aiProviders.value.firstOrNull { it.isEnabled && it.apiKey.isNotBlank() }?.modelName ?: "unknown",
                requestType = "clip_analysis",
                inputUnits = null,
                outputUnits = null,
                audioDurationSec = mediaAnalysis?.metadata?.durationSec,
                videoDurationSec = actualDurationSec,
                latencyMs = System.currentTimeMillis() - startTime,
                success = true,
                estimatedCostUsd = null,
                isEstimate = false
            )
        )

        // Deduct Google Flow Credits only after validated AI output.
        deductGoogleFlowCredits(actualDurationMinutes)

        _processingStep.value = ProcessingStep.CalculatingScores

        val maxScore = clipsData.maxOfOrNull { it.viralityScore } ?: 90
        val actualTitle = title.ifBlank { "Viral Video Repurposing Project" }
        val actualUrl = sourceUrl.ifBlank { "Custom Video Upload / Prompt" }

        val project = Project(
            title = actualTitle,
            sourceUrl = actualUrl,
            sourceDurationSec = actualDurationSec,
            status = "PROCESSING",
            targetPlatform = targetPlatform,
            captionTheme = captionTheme,
            clipCount = clipsData.size,
            bestViralityScore = maxScore,
            createdAt = System.currentTimeMillis()
        )

        val newProjectId = projectDao.insertProject(project)

        _processingStep.value = ProcessingStep.StylingCaptions

        val clipEntities = clipsData.map { clipData ->
            createClipEntity(
                projectId = newProjectId,
                data = clipData,
                captionTheme = captionTheme
            )
        }

        clipDao.insertClips(clipEntities)

        // Real render step inspired by PublikClip's separate scoring/rendering
        // stages. Only local files, content Uris, and direct media URLs are
        // rendered here; a YouTube/Drive webpage URL must first be resolved to
        // an authorized media Uri by a downloader or Drive integration.
        var exportFailures = 0
        val mediaUri = inputMediaUri
        if (mediaUri != null) {
            _processingStep.value = ProcessingStep.StylingCaptions
            val exportRoot = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: appContext.cacheDir,
                "opus_clips/$newProjectId"
            )
            val storedClips = clipDao.getClipsForProjectSync(newProjectId)
            val faceTrackPoints = runCatching {
                faceTrackingAnalyzer.analyze(mediaUri, sampleIntervalMs = 600L, maxSamples = 180)
            }.getOrElse { error ->
                Log.w("OpusRepository", "Face tracking unavailable; using safe center crop", error)
                emptyList()
            }
            clipsData.forEachIndexed { index, data ->
                val storedClip = storedClips.firstOrNull {
                    it.title == data.title &&
                        it.startTimeSec == data.startTimeSec &&
                        it.endTimeSec == data.endTimeSec
                } ?: storedClips.getOrNull(index)
                try {
                    val output = File(exportRoot, "clip_${index + 1}.mp4")
                    videoProcessor.exportClip(
                        inputUri = mediaUri,
                        outputFile = output,
                        startTimeSec = data.startTimeSec,
                        endTimeSec = data.endTimeSec,
                        vertical = true,
                        cropCenterX = cropCenterForClip(faceTrackPoints, data.startTimeSec, data.endTimeSec),
                        captionCues = storedClip?.let(::decodeCaptionCues).orEmpty()
                    ) { progress ->
                        _processingStep.value = ProcessingStep.StylingCaptions
                    }
                    CaptionSidecarWriter.writeWebVtt(output, data.wordTimestamps, data.keywords)
                    storedClip?.let { clipDao.updateExportPath(it.id, output.absolutePath) }
                } catch (error: Exception) {
                    exportFailures++
                    Log.w("OpusRepository", "Real clip export failed for clip ${index + 1}", error)
                }
            }
        }

        val finalProjectStatus = if (exportFailures == 0) "COMPLETED" else "PARTIAL_FAILURE"
        projectDao.updateProject(project.copy(id = newProjectId, status = finalProjectStatus))

        val processingDurationMs = System.currentTimeMillis() - startTime

        // 1. Cache video processing metadata in Room DB
        val videoCache = VideoProcessingCacheEntity(
            sourceUrl = actualUrl,
            videoHash = "hash_${newProjectId}_${actualDurationSec}",
            videoTitle = actualTitle,
            sourceDurationSec = actualDurationSec,
            resolution = sourceMetadata?.let { "${it.width}x${it.height}" } ?: "غير متاح",
            detectedLanguage = detectLanguageFromText(transcriptOrPrompt),
            speakerCount = -1,
            audioSummary = "",
            fullTranscript = effectiveTranscript.ifBlank { clipsData.joinToString("\n") { it.transcript } },
            rawAnalysisJson = "{}",
            processingDurationMs = processingDurationMs,
            cacheHitCount = 1,
            cachedAt = System.currentTimeMillis()
        )
        videoProcessingCacheDao.insertOrUpdateCache(videoCache)

        // 2. Cache granular viral score breakdown for each generated clip
        val viralScoreEntities = clipsData.mapIndexed { index, clipData ->
            ViralScoreMetricEntity(
                clipId = newProjectId * 100 + (index + 1),
                projectId = newProjectId,
                clipTitle = clipData.title,
                overallViralityScore = clipData.viralityScore,
                hookScore = clipData.hookScore,
                retentionScore = clipData.retentionScore,
                emotionalScore = clipData.emotionalScore,
                shareabilityScore = clipData.shareabilityScore,
                punchlineScore = clipData.punchlineScore,
                tiktokFitScore = -1,
                reelsFitScore = -1,
                shortsFitScore = -1,
                viralityGrade = when {
                    clipData.viralityScore >= 95 -> "S+"
                    clipData.viralityScore >= 90 -> "S"
                    clipData.viralityScore >= 80 -> "A+"
                    clipData.viralityScore >= 70 -> "A"
                    else -> "B"
                },
                hookExplanation = clipData.hookExplanation,
                viralityFactorsJson = clipData.hookExplanation.takeIf { it.isNotBlank() }?.let { "[${JSONObject.quote(it)}]" } ?: "[]",
                suggestedTargetAudience = "غير مستخرج",
                peakRetentionSec = -1f,
                evaluatedAt = System.currentTimeMillis()
            )
        }
        viralScoreMetricDao.insertScores(viralScoreEntities)

        // 3. Log repurposing event in User History Room Table
        val historyEntry = RepurposingHistoryEntity(
            projectId = newProjectId,
            videoTitle = actualTitle,
            sourceUrl = actualUrl,
            actionType = "AI_REPURPOSE_PROCESSED",
            clipsGeneratedCount = clipsData.size,
            highestViralScore = maxScore,
            estimatedTimeSavedMinutes = 0,
            status = if (exportFailures == 0) "SUCCESS" else "PARTIAL_FAILURE",
            targetPlatform = targetPlatform,
            details = if (exportFailures == 0) {
                "Extracted ${clipsData.size} validated clips; highest returned score was ${maxScore}%."
            } else {
                "Extracted ${clipsData.size} clips, but $exportFailures MP4 export(s) failed; review the project before publishing."
            },
            timestamp = System.currentTimeMillis()
        )
        repurposingHistoryDao.insertHistory(historyEntry)

        // Deduct credits
        val updatedCreditState = _userCreditState.value.copy(
            creditsRemaining = _googleFlowCredits.value.remainingCreditsMinutes,
            totalProcessedMinutes = _userCreditState.value.totalProcessedMinutes + actualDurationMinutes,
            clipsCreatedCount = _userCreditState.value.clipsCreatedCount + clipsData.size
        )
        _userCreditState.value = updatedCreditState
        apiPrefs.edit()
            .putInt("real_total_processed_minutes", updatedCreditState.totalProcessedMinutes)
            .putInt("real_clips_created_count", updatedCreditState.clipsCreatedCount)
            .apply()

        _processingStep.value = ProcessingStep.Completed
        _processingStep.value = ProcessingStep.Idle

        return@withContext newProjectId
    }


    private fun String.toMediaUriOrNull(): Uri? {
        val uri = runCatching { Uri.parse(trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme == "content" || scheme == "file") return uri
        if (scheme != "http" && scheme != "https") return null
        val path = uri.path?.lowercase() ?: return null
        return if (listOf(".mp4", ".mov", ".m4v", ".webm", ".mkv", ".avi").any(path::endsWith)) {
            uri
        } else {
            null
        }
    }

    private fun detectLanguageFromText(text: String): String {
        if (text.isBlank()) return "غير مستخرج"
        val arabic = text.count { it in '\u0600'..'\u06FF' }
        val latin = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        return when {
            arabic > latin && arabic >= 3 -> "ar"
            latin >= 3 -> "en"
            else -> "غير مؤكد"
        }
    }

    private fun decodeCaptionCues(clip: Clip): List<com.example.data.video.CaptionCue> {
        return runCatching {
            val type = Types.newParameterizedType(List::class.java, AnimatedWord::class.java)
            val words: List<AnimatedWord> = moshi.adapter<List<AnimatedWord>>(type).fromJson(clip.animatedCaptionsJson).orEmpty()
            words.map {
                com.example.data.video.CaptionCue(
                    text = listOfNotNull(it.emoji.takeIf(String::isNotBlank), it.word.takeIf(String::isNotBlank)).joinToString(" "),
                    startSec = it.startSec,
                    endSec = it.endSec,
                    isHighlight = it.isHighlight
                )
            }.filter { it.text.isNotBlank() && it.endSec > it.startSec }
        }.getOrDefault(emptyList())
    }

    private fun createClipEntity(
        projectId: Long,
        data: ClipGenerationData,
        captionTheme: String
    ): Clip {
        val duration = maxOf(5, data.endTimeSec - data.startTimeSec)
        val animatedWords = data.wordTimestamps
            .filter { it.word.isNotBlank() && it.startSec >= 0f && it.endSec > it.startSec && it.endSec <= duration }
            .mapIndexed { index, timestamp ->
                val cleanWord = timestamp.word.replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF]"), "")
                val isHigh = data.keywords.any { it.equals(cleanWord, ignoreCase = true) }
                val emoji = if (isHigh && index < data.emojis.size) data.emojis[index] else ""
                val color = when (captionTheme) {
                    "Opus Neon" -> if (isHigh) "#38BDF8" else "#FFFFFF"
                    "MrBeast Yellow" -> if (isHigh) "#FACC15" else "#FFFFFF"
                    "Ali Abdaal" -> if (isHigh) "#F43F5E" else "#F1F5F9"
                    "Cyber Green" -> if (isHigh) "#10B981" else "#E2E8F0"
                    else -> if (isHigh) "#A855F7" else "#FFFFFF"
                }
                AnimatedWord(
                    word = timestamp.word,
                    startSec = timestamp.startSec,
                    endSec = timestamp.endSec,
                    isHighlight = isHigh,
                    emoji = emoji,
                    colorHex = color
                )
            }

        val animatedWordsAdapter: JsonAdapter<List<AnimatedWord>> = moshi.adapter(
            Types.newParameterizedType(List::class.java, AnimatedWord::class.java)
        )
        val bRollAdapter: JsonAdapter<List<com.example.data.model.BRollIdea>> = moshi.adapter(
            Types.newParameterizedType(List::class.java, com.example.data.model.BRollIdea::class.java)
        )
        val socialAdapter: JsonAdapter<List<com.example.data.model.SocialPostCopy>> = moshi.adapter(
            Types.newParameterizedType(List::class.java, com.example.data.model.SocialPostCopy::class.java)
        )

        return Clip(
            projectId = projectId,
            title = data.title,
            startTimeSec = data.startTimeSec,
            endTimeSec = data.endTimeSec,
            durationSec = duration,
            viralityScore = data.viralityScore,
            hookScore = data.hookScore,
            retentionScore = data.retentionScore,
            emotionalScore = data.emotionalScore,
            shareabilityScore = data.shareabilityScore,
            punchlineScore = data.punchlineScore,
            hookExplanation = data.hookExplanation,
            transcript = data.transcript,
            animatedCaptionsJson = animatedWordsAdapter.toJson(animatedWords),
            bRollPromptsJson = bRollAdapter.toJson(data.bRollIdeas),
            socialCopyJson = socialAdapter.toJson(data.socialCopies),
            layoutType = "9:16 Full Screen",
            isFavorite = data.viralityScore >= 95
        )
    }

    private fun cropCenterForClip(
        points: List<com.example.data.video.FaceTrackPoint>,
        startTimeSec: Int,
        endTimeSec: Int
    ): Float? {
        val inClip = points.filter { it.timeMs in (startTimeSec * 1000L)..(endTimeSec * 1000L) }
        if (inClip.isEmpty()) return null
        val dominantTrack = inClip.groupBy { it.trackingId }
            .values
            .maxByOrNull { track ->
                track.size * 10f + track.map { it.width * it.height }.average().toFloat()
            }
            ?: return null
        var smoothed = dominantTrack.first().centerX
        var weightTotal = 0f
        var weightedCenter = 0f
        dominantTrack.sortedBy { it.timeMs }.forEach { point ->
            val confidence = (point.width * point.height).coerceIn(0.05f, 1f)
            smoothed += (point.centerX - smoothed) * 0.35f
            weightedCenter += smoothed * confidence
            weightTotal += confidence
        }
        return if (weightTotal > 0f) (weightedCenter / weightTotal).coerceIn(-1f, 1f) else smoothed.coerceIn(-1f, 1f)
    }

    suspend fun exportClipToFile(
        clipId: Long,
        burnInSubtitles: Boolean,
        removeWatermark: Boolean,
        aspectRatioName: String = "9:16",
        smartReframe: Boolean = true,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val clip = clipDao.getClipByIdSync(clipId) ?: error("المقطع غير موجود.")
        val project = projectDao.getProjectByIdSync(clip.projectId) ?: error("المشروع غير موجود.")
        val inputUri = project.sourceUrl.toMediaUriOrNull()
            ?: error("مصدر الفيديو الأصلي غير متاح محلياً لإعادة التصدير.")
        val ratio = when {
            aspectRatioName.contains("1:1") -> com.example.data.video.ExportAspectRatio.SQUARE_1_1
            aspectRatioName.contains("4:5") -> com.example.data.video.ExportAspectRatio.PORTRAIT_4_5
            aspectRatioName.contains("16:9") -> com.example.data.video.ExportAspectRatio.LANDSCAPE_16_9
            else -> com.example.data.video.ExportAspectRatio.VERTICAL_9_16
        }
        val trackedCenterX = if (smartReframe && ratio == com.example.data.video.ExportAspectRatio.VERTICAL_9_16) {
            faceTrackingAnalyzer.analyze(inputUri, sampleIntervalMs = 750L, maxSamples = 120)
                .asSequence()
                .filter { it.timeMs in (clip.startTimeSec * 1000L)..(clip.endTimeSec * 1000L) }
                .groupBy { it.trackingId }
                .values
                .maxByOrNull { it.size }
                ?.map { it.centerX }
                ?.average()
                ?.toFloat()
        } else {
            null
        }

        val exportDirectory = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: appContext.cacheDir,
            "opus_clips/${clip.projectId}/manual_exports"
        )
        val output = File(exportDirectory, "clip_${clip.id}_${System.currentTimeMillis()}.mp4")
        videoProcessor.exportClip(
            inputUri = inputUri,
            outputFile = output,
            startTimeSec = clip.startTimeSec,
            endTimeSec = clip.endTimeSec,
            vertical = ratio == com.example.data.video.ExportAspectRatio.VERTICAL_9_16,
            aspectRatio = ratio,
            captionCues = if (burnInSubtitles) decodeCaptionCues(clip) else emptyList(),
            watermarkText = if (removeWatermark) "" else "ISM",
            cropCenterX = trackedCenterX,
            onProgress = onProgress
        )
        clipDao.updateExportPath(clip.id, output.absolutePath)
        output
    }

    suspend fun saveExportToMediaStore(file: File): Uri = withContext(Dispatchers.IO) {
        require(file.exists() && file.length() > 0L) { "ملف التصدير غير موجود أو فارغ." }
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ISM")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: error("تعذر إنشاء ملف الفيديو في المعرض.")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("تعذر فتح ملف الفيديو للكتابة.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            }
            uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    suspend fun toggleFavorite(clipId: Long, currentVal: Boolean) = withContext(Dispatchers.IO) {
        clipDao.setFavorite(clipId, !currentVal)
    }

    suspend fun updateLayoutType(clipId: Long, layout: String) = withContext(Dispatchers.IO) {
        clipDao.updateLayoutType(clipId, layout)
    }

    suspend fun reparseAndSyncSpeechToText(
        clipId: Long,
        transcriptOrAudio: String,
        durationSec: Float,
        language: String = "English",
        captionTheme: String = "Opus Neon"
    ): List<AnimatedWord> = withContext(Dispatchers.IO) {
        val timedWords = geminiService.generateSpeechToTextCaptions(
            spokenTextOrAudioPrompt = transcriptOrAudio,
            durationSec = durationSec,
            language = language,
            captionTheme = captionTheme
        )

        val animatedWordsAdapter: JsonAdapter<List<AnimatedWord>> = moshi.adapter(
            Types.newParameterizedType(List::class.java, AnimatedWord::class.java)
        )
        val json = animatedWordsAdapter.toJson(timedWords)
        val combinedTranscript = timedWords.joinToString(" ") { it.word }
        clipDao.updateCaptions(clipId, json, combinedTranscript)

        return@withContext timedWords
    }

    suspend fun updateClipWordList(
        clipId: Long,
        words: List<AnimatedWord>
    ) = withContext(Dispatchers.IO) {
        val animatedWordsAdapter: JsonAdapter<List<AnimatedWord>> = moshi.adapter(
            Types.newParameterizedType(List::class.java, AnimatedWord::class.java)
        )
        val json = animatedWordsAdapter.toJson(words)
        val combinedTranscript = words.joinToString(" ") { it.word }
        clipDao.updateCaptions(clipId, json, combinedTranscript)
    }

    fun getClipWords(clip: Clip): List<AnimatedWord> {
        val animatedWordsAdapter: JsonAdapter<List<AnimatedWord>> = moshi.adapter(
            Types.newParameterizedType(List::class.java, AnimatedWord::class.java)
        )
        return try {
            animatedWordsAdapter.fromJson(clip.animatedCaptionsJson) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun exportClipSrt(clip: Clip): String {
        val words = getClipWords(clip)
        return geminiService.exportToSrt(words)
    }

    fun exportClipVtt(clip: Clip): String {
        val words = getClipWords(clip)
        return geminiService.exportToVtt(words)
    }

    suspend fun deleteProject(projectId: Long) = withContext(Dispatchers.IO) {
        clipDao.deleteClipsForProject(projectId)
        projectDao.deleteProjectById(projectId)
    }

    suspend fun getBestClipForProject(projectId: Long): Clip? = withContext(Dispatchers.IO) {
        val clips = clipDao.getClipsForProject(projectId).firstOrNull() ?: emptyList()
        return@withContext clips.maxByOrNull { it.viralityScore } ?: clips.firstOrNull()
    }

    suspend fun dispatchAutoPublishForNewProject(projectId: Long, context: Context): AutoPublishResult? = withContext(Dispatchers.IO) {
        val config = _autoPublishConfig.value
        if (!config.isEnabled) return@withContext null

        val topClip = getBestClipForProject(projectId) ?: return@withContext null
        return@withContext executeAutoPublishForClip(topClip, preferredPlatform = null, context = context)
    }

    suspend fun executeAutoPublishForClip(
        clip: Clip,
        preferredPlatform: String? = null,
        context: Context
    ): AutoPublishResult = withContext(Dispatchers.IO) {
        val config = _autoPublishConfig.value
        val platformsToDispatch = if (preferredPlatform != null) listOf(preferredPlatform) else config.targetPlatforms.toList().ifEmpty { listOf("YouTube Shorts", "TikTok") }

        // Extract social post text
        val socialAdapter: JsonAdapter<List<SocialPostCopy>> = moshi.adapter(
            Types.newParameterizedType(List::class.java, SocialPostCopy::class.java)
        )
        val socialList: List<SocialPostCopy> = try {
            socialAdapter.fromJson(clip.socialCopyJson) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val targetPost = socialList.find { it.platform.equals(preferredPlatform, ignoreCase = true) }
            ?: socialList.firstOrNull()

        val hook = targetPost?.hook ?: clip.title
        val caption = targetPost?.caption ?: clip.transcript.take(160)
        val hashtags = targetPost?.hashtags?.joinToString(" ") ?: "#Viral #Shorts #ISM"
        val fullPostPayload = "$hook\n\n$caption\n\n$hashtags"

        // 1. Copy to clipboard if enabled
        if (config.autoCopyCaption) {
            withContext(Dispatchers.Main) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Viral Post Caption", fullPostPayload))
                } catch (e: Exception) {
                    Log.e("OpusRepository", "Clipboard error", e)
                }
            }
        }

        // 2. Direct API publishing is opt-in and must return a real platform result.
        val apiLogs = mutableListOf<DirectApiPublishLog>()
        if (_directApiCredentials.value.isDirectApiEnabled) {
            platformsToDispatch.forEach { platform ->
                try {
                    val log = geminiService.publishDirectViaApi(
                        platform = platform,
                        clipTitle = clip.title,
                        captionText = fullPostPayload,
                        credentials = _directApiCredentials.value
                    )
                    apiLogs.add(log)
                } catch (e: Exception) {
                    Log.e("OpusRepository", "Direct API publish failed for $platform", e)
                    apiLogs.add(
                        DirectApiPublishLog(
                            platform = platform,
                            isSuccess = false,
                            httpCode = 500,
                            endpointUrl = "",
                            responseSummary = "فشل النشر بسبب خطأ غير متوقع: ${e.localizedMessage ?: "Unknown error"}"
                        )
                    )
                }
            }
        }
        if (apiLogs.isNotEmpty()) {
            _recentPublishLogs.value = apiLogs + _recentPublishLogs.value.take(20)
        }

        // 3. Dispatch Webhook if URL configured (optional legacy fallback)
        var webhookSuccess = false
        if (config.webhookUrl.isNotBlank()) {
            webhookSuccess = sendWebhookPayload(clip, config.webhookUrl, fullPostPayload, platformsToDispatch)
        }

        val successfulPlatforms = apiLogs.filter { it.isSuccess }.map { it.platform }.distinct()
        val failedPlatforms = platformsToDispatch.filter { platform ->
            apiLogs.none { it.platform.equals(platform, ignoreCase = true) && it.isSuccess }
        }
        val successCount = successfulPlatforms.size
        val overallSuccess = successCount > 0 || webhookSuccess
        val message = when {
            successCount > 0 -> "تم النشر الفعلي عبر الـ API على ${successfulPlatforms.joinToString(" • ")}."
            webhookSuccess -> "تعذر النشر المباشر، لكن تم إرسال بيانات المقطع إلى Webhook بنجاح."
            else -> "فشل النشر: لم تُقبل أي منصة الطلب. راجع بيانات الاعتماد وسجل النشر."
        }

        return@withContext AutoPublishResult(
            isSuccess = overallSuccess,
            message = message,
            dispatchedPlatforms = platformsToDispatch,
            webhookDispatched = webhookSuccess,
            postText = fullPostPayload,
            successfulPlatforms = successfulPlatforms,
            failedPlatforms = failedPlatforms
        )
    }

    private fun sendWebhookPayload(
        clip: Clip,
        webhookUrl: String,
        fullCaption: String,
        platforms: List<String>
    ): Boolean {
        return try {
            val url = URL(webhookUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val json = JSONObject().apply {
                put("event", "clip.auto_publish")
                put("clipId", clip.id)
                put("title", clip.title)
                put("viralityScore", clip.viralityScore)
                put("durationSec", clip.durationSec)
                put("caption", fullCaption)
                put("targetPlatforms", JSONObject.wrap(platforms))
                put("timestamp", System.currentTimeMillis())
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(json.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e("OpusRepository", "Webhook dispatch failed: ${e.message}")
            false
        }
    }
}

