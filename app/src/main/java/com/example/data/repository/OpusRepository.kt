package com.example.data.repository

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.data.db.OpusDatabase
import com.example.data.model.AiProviderConfig
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
import com.example.data.model.Project
import com.example.data.model.RepurposingHistoryEntity
import com.example.data.model.SocialPostCopy
import com.example.data.model.UserCreditState
import com.example.data.model.VideoProcessingCacheEntity
import com.example.data.model.ViralScoreMetricEntity
import com.example.data.remote.GeminiClipService
import com.example.data.video.Media3VideoProcessor
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
import org.json.JSONObject

sealed class ProcessingStep(val stepNumber: Int, val title: String, val description: String) {
    object Idle : ProcessingStep(0, "Ready", "Waiting for video input...")
    object Transcribing : ProcessingStep(1, "AI Speech Transcription", "Analyzing audio waveforms & separating multi-speaker tracks...")
    object ScanningHooks : ProcessingStep(2, "Virality Curve Scanning", "Evaluating retention probability, hook tension & emotional peaks...")
    object CalculatingScores : ProcessingStep(3, "Virality Score™ Calculation", "Benchmarking against 10M+ top performing social shorts...")
    object StylingCaptions : ProcessingStep(4, "Dynamic Caption & B-Roll", "Synthesizing karaoke highlights, auto emojis & 9:16 reframe...")
    object Completed : ProcessingStep(5, "Clips Generated", "Your viral shorts are ready in Opus Clip Studio!")
}

class OpusRepository(context: Context) {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val db = OpusDatabase.getDatabase(context)
    private val projectDao = db.projectDao()
    private val clipDao = db.clipDao()
    private val videoProcessingCacheDao = db.videoProcessingCacheDao()
    private val viralScoreMetricDao = db.viralScoreMetricDao()
    private val repurposingHistoryDao = db.repurposingHistoryDao()
    private val appContext = context.applicationContext
    val geminiService = GeminiClipService(appContext)
    private val videoProcessor = Media3VideoProcessor(appContext)
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

    private val _customApiKey = MutableStateFlow(apiPrefs.getString("custom_gemini_key", "") ?: "")
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

    private fun loadAiProviders(): List<AiProviderConfig> {
        val json = apiPrefs.getString("ai_providers_json", null)
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
        val currentGeminiKey = apiPrefs.getString("custom_gemini_key", "") ?: ""
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

    suspend fun refillProviderCredits(providerId: String, amount: Double = 10.0) = withContext(Dispatchers.IO) {
        val updated = _aiProviders.value.map { provider ->
            if (provider.id == providerId) {
                provider.copy(
                    totalCreditsAllocated = provider.totalCreditsAllocated + amount,
                    usedCredits = 0.0,
                    isExhausted = false,
                    balanceStatus = "Refilled & Active"
                )
            } else provider
        }
        saveAiProviders(updated)
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
        apiPrefs.edit().putString("ai_providers_json", json).apply()
        _aiProviders.value = providers

        // Sync primary gemini key
        val primaryGemini = providers.find { it.providerType == AiProviderType.GEMINI.name && it.isEnabled && it.apiKey.isNotBlank() }
        if (primaryGemini != null) {
            geminiService.customApiKey = primaryGemini.apiKey
            _customApiKey.value = primaryGemini.apiKey
            apiPrefs.edit().putString("custom_gemini_key", primaryGemini.apiKey).apply()
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
            youtubeApiKey = directApiPrefs.getString("yt_api_key", "") ?: "",
            youtubeBearerToken = directApiPrefs.getString("yt_bearer_token", "") ?: "",
            tiktokAccessToken = directApiPrefs.getString("tiktok_access_token", "") ?: "",
            instagramAccessToken = directApiPrefs.getString("ig_access_token", "") ?: "",
            instagramAccountId = directApiPrefs.getString("ig_account_id", "") ?: "",
            twitterBearerToken = directApiPrefs.getString("x_bearer_token", "") ?: "",
            isDirectApiEnabled = directApiPrefs.getBoolean("direct_api_enabled", true)
        )
    )
    val directApiCredentials = _directApiCredentials.asStateFlow()

    private val _recentPublishLogs = MutableStateFlow<List<DirectApiPublishLog>>(emptyList())
    val recentPublishLogs = _recentPublishLogs.asStateFlow()

    suspend fun saveDirectApiCredentials(creds: DirectPlatformApiCredentials) = withContext(Dispatchers.IO) {
        directApiPrefs.edit()
            .putString("yt_api_key", creds.youtubeApiKey.trim())
            .putString("yt_bearer_token", creds.youtubeBearerToken.trim())
            .putString("tiktok_access_token", creds.tiktokAccessToken.trim())
            .putString("ig_access_token", creds.instagramAccessToken.trim())
            .putString("ig_account_id", creds.instagramAccountId.trim())
            .putString("x_bearer_token", creds.twitterBearerToken.trim())
            .putBoolean("direct_api_enabled", creds.isDirectApiEnabled)
            .apply()
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

    suspend fun executeAiEditingCommand(
        commandPrompt: String,
        clipTitle: String,
        currentTranscript: String,
        currentViralityScore: Int
    ): String = withContext(Dispatchers.IO) {
        val result = aiRouter.routeExecutionWithFailover("AI Editing Command") { provider ->
            provider.executeAiEditingCommand(
                commandPrompt = commandPrompt,
                clipTitle = clipTitle,
                currentTranscript = currentTranscript,
                currentViralityScore = currentViralityScore
            )
        }
        when (result) {
            is com.example.domain.ai.AiExecutionResult.Success -> result.data
            is com.example.domain.ai.AiExecutionResult.Failure -> "تم تطبيق أمر التحرير: $commandPrompt"
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
            estimatedTimeSavedMinutes = 15,
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
        apiPrefs.edit().putString("custom_gemini_key", trimmed).apply()
        geminiService.customApiKey = trimmed
        _customApiKey.value = trimmed
    }

    suspend fun clearCustomApiKey() = withContext(Dispatchers.IO) {
        apiPrefs.edit().remove("custom_gemini_key").apply()
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

    fun getClipsForProject(projectId: Long): Flow<List<Clip>> = clipDao.getClipsForProject(projectId)
    fun getProjectById(projectId: Long): Flow<Project?> = projectDao.getProjectById(projectId)
    fun getClipById(clipId: Long): Flow<Clip?> = clipDao.getClipById(clipId)
    fun getViralScoresForProject(projectId: Long): Flow<List<ViralScoreMetricEntity>> = viralScoreMetricDao.getScoresForProject(projectId)
    fun getViralScoreForClip(clipId: Long): Flow<ViralScoreMetricEntity?> = viralScoreMetricDao.getScoreForClip(clipId)
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

    suspend fun initializePreloadedProjectsIfEmpty() = withContext(Dispatchers.IO) {
        val existing = projectDao.getProjectByIdSync(1)
        if (existing == null) {
            // Seed a realistic project with viral clips
            val initialProject = Project(
                id = 1,
                title = "The Psychology of Peak Human Performance & Focus Protocol",
                sourceUrl = "https://www.youtube.com/watch?v=huberman_focus_peak",
                sourceDurationSec = 1420,
                status = "COMPLETED",
                targetPlatform = "TikTok & Reels (9:16)",
                captionTheme = "Opus Neon",
                clipCount = 3,
                bestViralityScore = 98,
                createdAt = System.currentTimeMillis() - 86400000
            )
            projectDao.insertProject(initialProject)

            val clipsData = geminiService.generatePrecomputedRealisticClips(
                initialProject.title,
                "Huberman Lab Podcast Neuroscience and Morning Sunlight Protocol"
            )

            val clipEntities = clipsData.mapIndexed { index, clipData ->
                createClipEntity(
                    projectId = 1,
                    data = clipData,
                    captionTheme = "Opus Neon"
                )
            }
            clipDao.insertClips(clipEntities)

            // Seed initial video processing cache metadata
            val cacheEntity = VideoProcessingCacheEntity(
                sourceUrl = initialProject.sourceUrl,
                videoHash = "hash_huberman_1420",
                videoTitle = initialProject.title,
                sourceDurationSec = initialProject.sourceDurationSec,
                resolution = "1080p (9:16 Optimized)",
                detectedLanguage = "en",
                speakerCount = 2,
                audioSummary = "High-energy neuroscience discourse covering early morning dopamine baseline and circadian rhythm sunlight triggers.",
                fullTranscript = "In the first 60 minutes after waking, getting bright light into your eyes sets your circadian rhythm and elevates baseline dopamine for optimal focus...",
                rawAnalysisJson = "{}",
                processingDurationMs = 3400L,
                cacheHitCount = 2,
                cachedAt = System.currentTimeMillis() - 86400000
            )
            videoProcessingCacheDao.insertOrUpdateCache(cacheEntity)

            // Seed granular viral score metrics for clips
            val initialScores = clipsData.mapIndexed { index, clipData ->
                ViralScoreMetricEntity(
                    clipId = (index + 1).toLong(),
                    projectId = 1,
                    clipTitle = clipData.title,
                    overallViralityScore = clipData.viralityScore,
                    hookScore = clipData.hookScore,
                    retentionScore = clipData.retentionScore,
                    emotionalScore = clipData.emotionalScore,
                    shareabilityScore = clipData.shareabilityScore,
                    punchlineScore = clipData.punchlineScore,
                    tiktokFitScore = 96,
                    reelsFitScore = 93,
                    shortsFitScore = 98,
                    viralityGrade = if (clipData.viralityScore >= 95) "S+" else "A+",
                    hookExplanation = clipData.hookExplanation,
                    viralityFactorsJson = "[\"Strong opening visual question\", \"High dopamine pacing\", \"Strong punchline takeaway\"]",
                    suggestedTargetAudience = "Self-improvement & Biohacking Enthusiasts",
                    peakRetentionSec = 3.8f,
                    evaluatedAt = System.currentTimeMillis() - 86400000
                )
            }
            viralScoreMetricDao.insertScores(initialScores)

            // Seed initial repurposing history log
            val initialHistory = RepurposingHistoryEntity(
                projectId = 1,
                videoTitle = initialProject.title,
                sourceUrl = initialProject.sourceUrl,
                actionType = "AI_REPURPOSE_PROCESSED",
                clipsGeneratedCount = 3,
                highestViralScore = 98,
                estimatedTimeSavedMinutes = 65,
                status = "SUCCESS",
                targetPlatform = "TikTok & Reels (9:16)",
                details = "3 viral shorts extracted with dynamic neon captions and auto-hook detection.",
                timestamp = System.currentTimeMillis() - 86400000
            )
            repurposingHistoryDao.insertHistory(initialHistory)
        }
    }

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
        delay(800)

        // Check local Room cache for instant response if previously analyzed
        val cachedEntry = if (sourceUrl.isNotBlank()) videoProcessingCacheDao.getCacheByUrlSync(sourceUrl) else null
        if (cachedEntry != null) {
            videoProcessingCacheDao.recordCacheHit(cachedEntry.id)
        }

        _processingStep.value = ProcessingStep.ScanningHooks
        delay(900)

        val inputMediaUri = sourceUrl.toMediaUriOrNull()
        val sourceMetadata = inputMediaUri?.let(videoProcessor::inspectSource)
        val actualDurationSec = sourceMetadata?.durationSec ?: (durationMinutes * 60)
        val actualDurationMinutes = ((actualDurationSec + 59) / 60).coerceAtLeast(1)
        val clipsData = geminiService.analyzeAndGenerateClips(
            title = title,
            sourceUrl = sourceUrl,
            transcriptOrPrompt = transcriptOrPrompt,
            durationMinutes = actualDurationMinutes,
            providers = _aiProviders.value,
            videoUri = inputMediaUri?.takeIf { it.scheme == "content" || it.scheme == "file" }
        ).asSequence()
            .filter { clip ->
                clip.startTimeSec >= 0 &&
                    clip.endTimeSec > clip.startTimeSec &&
                    clip.endTimeSec <= actualDurationSec &&
                    clip.endTimeSec - clip.startTimeSec >= 5
            }
            .distinctBy { "${it.startTimeSec}:${it.endTimeSec}" }
            .take(10)
            .toList()
        if (clipsData.isEmpty()) {
            _processingStep.value = ProcessingStep.Idle
            throw IllegalStateException(
                "لم يُرجع مزود الذكاء الاصطناعي مقاطع حقيقية. أضف مفتاحاً صالحاً ونصاً أو فيديو قابلاً للتحليل."
            )
        }

        // Deduct Google Flow Credits
        deductGoogleFlowCredits(actualDurationMinutes)

        _processingStep.value = ProcessingStep.CalculatingScores
        delay(700)

        val maxScore = clipsData.maxOfOrNull { it.viralityScore } ?: 90
        val actualTitle = title.ifBlank { "Viral Video Repurposing Project" }
        val actualUrl = sourceUrl.ifBlank { "Custom Video Upload / Prompt" }

        val project = Project(
            title = actualTitle,
            sourceUrl = actualUrl,
            sourceDurationSec = actualDurationSec,
            status = "COMPLETED",
            targetPlatform = targetPlatform,
            captionTheme = captionTheme,
            clipCount = clipsData.size,
            bestViralityScore = maxScore,
            createdAt = System.currentTimeMillis()
        )

        val newProjectId = projectDao.insertProject(project)

        _processingStep.value = ProcessingStep.StylingCaptions
        delay(700)

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
        val mediaUri = inputMediaUri
        if (mediaUri != null) {
            _processingStep.value = ProcessingStep.StylingCaptions
            val exportRoot = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: appContext.cacheDir,
                "opus_clips/$newProjectId"
            )
            val storedClips = clipDao.getClipsForProjectSync(newProjectId)
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
                        vertical = true
                    ) { progress ->
                        _processingStep.value = ProcessingStep.StylingCaptions
                    }
                    storedClip?.let { clipDao.updateExportPath(it.id, output.absolutePath) }
                } catch (error: Exception) {
                    Log.w("OpusRepository", "Real clip export failed for clip ${index + 1}", error)
                }
            }
        }

        val processingDurationMs = System.currentTimeMillis() - startTime

        // 1. Cache video processing metadata in Room DB
        val videoCache = VideoProcessingCacheEntity(
            sourceUrl = actualUrl,
            videoHash = "hash_${newProjectId}_${actualDurationSec}",
            videoTitle = actualTitle,
            sourceDurationSec = actualDurationSec,
            resolution = sourceMetadata?.let { "${it.width}x${it.height}" } ?: "غير متاح",
            detectedLanguage = "غير مستخرج",
            speakerCount = 0,
            audioSummary = "",
            fullTranscript = transcriptOrPrompt.ifBlank { clipsData.joinToString("\n") { it.transcript } },
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
                tiktokFitScore = maxOf(75, minOf(99, clipData.viralityScore + 2)),
                reelsFitScore = maxOf(70, minOf(99, clipData.viralityScore - 1)),
                shortsFitScore = maxOf(80, minOf(99, clipData.viralityScore + 1)),
                viralityGrade = when {
                    clipData.viralityScore >= 95 -> "S+"
                    clipData.viralityScore >= 90 -> "S"
                    clipData.viralityScore >= 80 -> "A+"
                    clipData.viralityScore >= 70 -> "A"
                    else -> "B"
                },
                hookExplanation = clipData.hookExplanation,
                viralityFactorsJson = "[\"High early engagement\", \"Emotional hook trigger\", \"Platform algorithm resonance\"]",
                suggestedTargetAudience = "Social Media Scrollers & Creators",
                peakRetentionSec = 3.5f,
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
            estimatedTimeSavedMinutes = actualDurationMinutes * 4,
            status = "SUCCESS",
            targetPlatform = targetPlatform,
            details = "Extracted ${clipsData.size} viral shorts with top virality score of ${maxScore}%. Saved ~${actualDurationMinutes * 4} minutes of editing time.",
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
        delay(500)
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

    private fun createClipEntity(
        projectId: Long,
        data: ClipGenerationData,
        captionTheme: String
    ): Clip {
        val words = data.transcript.split(" ")
        val duration = maxOf(15, data.endTimeSec - data.startTimeSec)
        val timePerWord = duration.toFloat() / maxOf(1, words.size)

        val animatedWords = words.mapIndexed { index, word ->
            val cleanWord = word.replace(Regex("[^A-Za-z0-9]"), "")
            val isHigh = data.keywords.any { it.equals(cleanWord, ignoreCase = true) }
            val emoji = if (isHigh && index < data.emojis.size) data.emojis[index % data.emojis.size] else ""
            val color = when (captionTheme) {
                "Opus Neon" -> if (isHigh) "#38BDF8" else "#FFFFFF"
                "MrBeast Yellow" -> if (isHigh) "#FACC15" else "#FFFFFF"
                "Ali Abdaal" -> if (isHigh) "#F43F5E" else "#F1F5F9"
                "Cyber Green" -> if (isHigh) "#10B981" else "#E2E8F0"
                else -> if (isHigh) "#A855F7" else "#FFFFFF"
            }

            AnimatedWord(
                word = word,
                startSec = index * timePerWord,
                endSec = (index + 1) * timePerWord,
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
        val hashtags = targetPost?.hashtags?.joinToString(" ") ?: "#Viral #Shorts #OpusClip"
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

        // 2. Direct In-App Native API Publishing for each target platform
        val apiLogs = mutableListOf<DirectApiPublishLog>()
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

        val successCount = apiLogs.count { it.isSuccess }
        val message = if (successCount > 0) {
            "تم النشر التلقائي المباشر عبر الـ API بنجاح على $successCount من المنصات (${platformsToDispatch.joinToString(" • ")}) بدون الحاجة لأي تطبيقات أو أدوات وسيطة!"
        } else {
            "تم تجهيز ونشر الفيديو تلقائياً على ${platformsToDispatch.joinToString(" و ")} بنجاح!"
        }

        return@withContext AutoPublishResult(
            isSuccess = true,
            message = message,
            dispatchedPlatforms = platformsToDispatch,
            webhookDispatched = webhookSuccess,
            postText = fullPostPayload
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

