package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import com.example.domain.analysis.WordTimestamp

@Entity(tableName = "projects")
@JsonClass(generateAdapter = true)
data class Project(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val sourceDurationSec: Int = 0,
    val status: String = "COMPLETED", // PROCESSING, COMPLETED, FAILED
    val targetPlatform: String = "TikTok & Reels (9:16)",
    val captionTheme: String = "Opus Neon",
    val clipCount: Int = 0,
    val bestViralityScore: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "clips")
@JsonClass(generateAdapter = true)
data class Clip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val title: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val durationSec: Int,
    val viralityScore: Int, // 0 - 100
    val hookScore: Int = 0,
    val retentionScore: Int = 0,
    val emotionalScore: Int = 0,
    val shareabilityScore: Int = 0,
    val punchlineScore: Int = 0,
    val hookExplanation: String,
    val transcript: String,
    val animatedCaptionsJson: String, // serialized List<AnimatedWord>
    val bRollPromptsJson: String,     // serialized List<BRollIdea>
    val socialCopyJson: String,       // serialized List<SocialPostCopy>
    val layoutType: String = "9:16 Full Screen",
    val isFavorite: Boolean = false,
    val exportPath: String = ""
)

@JsonClass(generateAdapter = true)
data class AnimatedWord(
    val word: String,
    val startSec: Float,
    val endSec: Float,
    val isHighlight: Boolean = false,
    val emoji: String = "",
    val colorHex: String = "#38BDF8"
)

@JsonClass(generateAdapter = true)
data class BRollIdea(
    val title: String,
    val timestampSec: Int,
    val visualPrompt: String,
    val soundEffect: String = "Swoosh transition"
)

@JsonClass(generateAdapter = true)
data class SocialPostCopy(
    val platform: String, // TikTok, Instagram Reels, YouTube Shorts, LinkedIn, X
    val caption: String,
    val hook: String,
    val hashtags: List<String>
)

data class ViralityAnalysisResult(
    val clips: List<ClipGenerationData>
)

@JsonClass(generateAdapter = true)
data class ClipGenerationData(
    val title: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val viralityScore: Int,
    val hookScore: Int,
    val retentionScore: Int,
    val emotionalScore: Int,
    val shareabilityScore: Int,
    val punchlineScore: Int,
    val hookExplanation: String,
    val transcript: String,
    val keywords: List<String>,
    val emojis: List<String>,
    val bRollIdeas: List<BRollIdea>,
    val socialCopies: List<SocialPostCopy>,
    val wordTimestamps: List<WordTimestamp> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AutoPublishConfig(
    val isEnabled: Boolean = false,
    val targetPlatforms: Set<String> = setOf("TikTok", "YouTube Shorts", "Instagram Reels"),
    val autoOpenShareSheet: Boolean = true,
    val autoCopyCaption: Boolean = true,
    val webhookUrl: String = "",
    val scheduledSlot: String = "Instant (Immediately after AI generation)"
)

data class AutoPublishResult(
    val isSuccess: Boolean,
    val message: String,
    val dispatchedPlatforms: List<String>,
    val webhookDispatched: Boolean = false,
    val postText: String = "",
    val successfulPlatforms: List<String> = emptyList(),
    val failedPlatforms: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DedicatedCaptionResult(
    val hooks: List<String>,
    val mainCaption: String,
    val keyTakeaways: List<String>,
    val callToAction: String,
    val hashtags: List<String>,
    val characterCount: Int,
    val viralityGrade: String = "A+",
    val platformTips: String = ""
)

@JsonClass(generateAdapter = true)
data class DirectPlatformApiCredentials(
    val youtubeApiKey: String = "",
    val youtubeBearerToken: String = "",
    val tiktokAccessToken: String = "",
    val instagramAccessToken: String = "",
    val instagramAccountId: String = "",
    val twitterBearerToken: String = "",
    val isDirectApiEnabled: Boolean = true
)

@JsonClass(generateAdapter = true)
data class DirectApiPublishLog(
    val id: String = System.currentTimeMillis().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val platform: String,
    val isSuccess: Boolean,
    val httpCode: Int,
    val endpointUrl: String,
    val responseSummary: String,
    val postUrl: String = "",
    val rawPayload: String = ""
)

@JsonClass(generateAdapter = true)
data class AiTemplateRecommendation(
    val recommendedCaptionTheme: String = "Opus Neon",
    val recommendedLayout: String = "9:16 Full Screen",
    val recommendedPlatform: String = "TikTok & Reels (9:16)",
    val recommendedDurationRange: String = "30s - 60s",
    val styleReasoning: String = "High dynamic energy with punchy neon highlights for maximum social retention.",
    val detectedNiche: String = "General Viral Content",
    val confidenceScore: Int = 98
)

@JsonClass(generateAdapter = true)
data class GoogleFlowCreditInfo(
    val totalCreditsMinutes: Int = 0,
    val usedCreditsMinutes: Int = 0,
    val totalRequestsLimit: Int = 0,
    val usedRequestsCount: Int = 0,
    val planName: String = "غير مُكوّن",
    val rpmLimit: Int = 0,
    val isAutoFailoverEnabled: Boolean = false,
    val activeProviderName: String = "غير متاح",
    val lastResetTimestamp: Long = System.currentTimeMillis()
) {
    val remainingCreditsMinutes: Int
        get() = (totalCreditsMinutes - usedCreditsMinutes).coerceAtLeast(0)

    val remainingRequestsCount: Int
        get() = (totalRequestsLimit - usedRequestsCount).coerceAtLeast(0)

    val creditPercentage: Float
        get() = if (totalCreditsMinutes > 0) {
            (remainingCreditsMinutes.toFloat() / totalCreditsMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val isExhausted: Boolean
        get() = remainingCreditsMinutes <= 0 || remainingRequestsCount <= 0
}

enum class AiProviderType(
    val displayName: String,
    val defaultModel: String,
    val placeholderKey: String,
    val docsUrl: String,
    val brandColorHex: String = "#8B5CF6",
    val unitCurrency: String = "$"
) {
    GEMINI("Google Gemini AI", "gemini-2.5-flash", "AIzaSy...", "https://aistudio.google.com/app/apikey", "#38BDF8", "Quota"),
    OPENAI("OpenAI (GPT-4o)", "gpt-4o-mini", "sk-proj-...", "https://platform.openai.com/api-keys", "#10B981", "$"),
    ANTHROPIC("Anthropic (Claude)", "claude-3-5-sonnet-20241022", "sk-ant-...", "https://console.anthropic.com/settings/keys", "#D97706", "$"),
    OPENROUTER("OpenRouter (Multi-LLM)", "meta-llama/llama-3.3-70b-instruct", "sk-or-v1-...", "https://openrouter.ai/keys", "#6366F1", "$"),
    GROQ("Groq (Ultra-Fast)", "llama-3.3-70b-versatile", "gsk_...", "https://console.groq.com/keys", "#F59E0B", "Reqs"),
    MISTRAL("Mistral AI", "mistral-large-latest", "...", "https://console.mistral.ai/api-keys/", "#EF4444", "$"),
    CUSTOM("Custom Endpoint", "default", "key_...", "", "#94A3B8", "Units")
}

@JsonClass(generateAdapter = true)
data class AiProviderConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val providerType: String = AiProviderType.GEMINI.name, // Gemini, OpenAI, Anthropic, OpenRouter, Groq, Mistral, Custom
    val apiKey: String,
    val customBaseUrl: String = "",
    val modelName: String = "gemini-2.5-flash",
    val priority: Int = 1, // 1 = Primary, 2 = Secondary, 3 = Tertiary...
    val isEnabled: Boolean = false,
    val isExhausted: Boolean = false,
    val lastTestedSuccess: Boolean? = null,
    val lastTestedMessage: String = "",
    val totalCreditsAllocated: Double = 0.0,
    val usedCredits: Double = 0.0,
    val creditUnit: String = "",
    val totalTokensProcessed: Long = 0L,
    val requestsCount: Int = 0,
    val maxRequestsLimit: Int = 0,
    val rateLimitRpm: Int = 0,
    val lastLatencyMs: Long = 0L,
    val balanceStatus: String = "غير مُكوّن"
) {
    val remainingCredits: Double
        get() = (totalCreditsAllocated - usedCredits).coerceAtLeast(0.0)

    val creditPercentage: Float
        get() = if (totalCreditsAllocated > 0.0) {
            ((totalCreditsAllocated - usedCredits) / totalCreditsAllocated).toFloat().coerceIn(0f, 1f)
        } else 0f
}



