package com.example.domain.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreatorProfile(
    val primaryLanguage: String = "ar", // "ar" (Arabic RTL), "en", "fr"
    val targetPlatforms: List<String> = listOf("TikTok", "Instagram Reels", "YouTube Shorts"),
    val contentCategory: String = "Education & Tech", // Podcast, Gaming, Business, Vlog, Comedy, etc.
    val preferredDurationRangeSec: Pair<Int, Int> = Pair(30, 60),
    val captionTheme: String = "Opus Neon",
    val targetAudience: String = "Gen-Z & Mobile Scrollers",
    val silenceRemovalAggressiveness: Float = 0.5f, // 0.0 (off) to 1.0 (strict)
    val autoHighlightKeywords: Boolean = true,
    val includeEmojisInCaptions: Boolean = true,
    val exportPreset: String = "TikTok (9:16 Ultra HD)",
    val brandWatermark: String = ""
)

@JsonClass(generateAdapter = true)
data class ExplainableViralityFactors(
    val hookStrength: Int = 90,
    val hookReason: String = "Immediate curiosity gap created in the first 3 seconds.",
    val retentionPotential: Int = 85,
    val retentionReason: String = "Pacing keeps audience engaged with rapid topic shifts.",
    val emotionalIntensity: Int = 82,
    val emotionalReason: String = "High emotional polarity driving strong reactions.",
    val noveltyScore: Int = 88,
    val noveltyReason: String = "Unique take or uncommon angle on the discussed topic.",
    val clarityScore: Int = 92,
    val clarityReason: String = "Clear speech delivery, minimal filler words, high articulation.",
    val pacingScore: Int = 89,
    val pacingReason: String = "Dynamic rhythm without dead air or long pauses.",
    val shareabilityScore: Int = 94,
    val shareabilityReason: String = "High relatable factor suitable for direct DMs and group shares.",
    val curiosityGapScore: Int = 91,
    val curiosityGapReason: String = "Strategic information reveal that keeps viewers to the end.",
    val audienceRelevance: Int = 87,
    val audienceRelevanceReason: String = "Strong demographic alignment with mobile short-form consumers."
) {
    val overallCalculatedScore: Int
        get() = ((hookStrength * 0.20) + 
                 (retentionPotential * 0.15) + 
                 (emotionalIntensity * 0.10) + 
                 (noveltyScore * 0.10) + 
                 (clarityScore * 0.10) + 
                 (pacingScore * 0.10) + 
                 (shareabilityScore * 0.15) + 
                 (curiosityGapScore * 0.05) + 
                 (audienceRelevance * 0.05)).toInt().coerceIn(0, 100)
}

enum class AspectRatioPreset(val ratioName: String, val widthRatio: Int, val heightRatio: Int, val description: String) {
    VERTICAL_9_16("9:16 Vertical", 9, 16, "TikTok, Reels, Shorts"),
    SQUARE_1_1("1:1 Square", 1, 1, "Instagram Feed & LinkedIn"),
    PORTRAIT_4_5("4:5 Portrait", 4, 5, "Instagram & Facebook Feed"),
    LANDSCAPE_16_9("16:9 Landscape", 16, 9, "YouTube & Web Videos")
}

@JsonClass(generateAdapter = true)
data class SmartReframingConfig(
    val targetRatio: String = AspectRatioPreset.VERTICAL_9_16.ratioName,
    val faceDetectionEnabled: Boolean = true,
    val activeSpeakerTracking: Boolean = true,
    val dynamicCropSmoothing: Float = 0.8f,
    val splitScreenForMultiSpeaker: Boolean = true
)

@JsonClass(generateAdapter = true)
data class SilenceRemovalConfig(
    val isEnabled: Boolean = true,
    val silenceThresholdDb: Float = -35.0f,
    val minSilenceDurationMs: Int = 500,
    val removeFillerWords: Boolean = true, // "umm", "uh", "يعني", "أصلاً"
    val aggressiveLevel: Float = 0.6f // 0.0 to 1.0
)

@JsonClass(generateAdapter = true)
data class CostUsageAnalytics(
    val totalClipsGenerated: Long = 0L,
    val totalProcessingTimeMs: Long = 0L,
    val totalExports: Long = 0L,
    val failedJobsCount: Long = 0L,
    val averageViralityScore: Float = 0f,
    val estimatedAiCostUsd: Double = 0.0,
    val tokensConsumed: Long = 0L
)
