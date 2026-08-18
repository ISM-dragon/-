package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * Room Entity caching video processing metadata, transcription data, and raw AI analysis
 * to prevent redundant API calls and enable instant local reloading.
 */
@Entity(
    tableName = "video_processing_cache",
    indices = [
        Index(value = ["sourceUrl"]),
        Index(value = ["videoHash"])
    ]
)
@JsonClass(generateAdapter = true)
data class VideoProcessingCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceUrl: String,
    val videoHash: String = "",
    val videoTitle: String,
    val sourceDurationSec: Int,
    val resolution: String = "غير متاح",
    val detectedLanguage: String = "غير متاح",
    val speakerCount: Int = -1,
    val audioSummary: String = "",
    val fullTranscript: String = "",
    val rawAnalysisJson: String = "", // Serialized cached JSON response
    val processingDurationMs: Long = 0L,
    val cacheHitCount: Int = 0,
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * Room Entity storing granular virality metrics, audience retention curves,
 * and platform compatibility scores for each generated short.
 */
@Entity(
    tableName = "viral_score_metrics",
    indices = [
        Index(value = ["clipId"]),
        Index(value = ["projectId"]),
        Index(value = ["overallViralityScore"])
    ]
)
@JsonClass(generateAdapter = true)
data class ViralScoreMetricEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val clipId: Long,
    val projectId: Long,
    val clipTitle: String,
    val overallViralityScore: Int, // 0 - 100
    val hookScore: Int = 0,
    val retentionScore: Int = 0,
    val emotionalScore: Int = 0,
    val shareabilityScore: Int = 0,
    val punchlineScore: Int = 0,
    val tiktokFitScore: Int = -1,
    val reelsFitScore: Int = -1,
    val shortsFitScore: Int = -1,
    val viralityGrade: String = "غير متاح", // S+, S, A+, A, B
    val hookExplanation: String = "",
    val viralityFactorsJson: String = "[]", // Serialized list of bullet points
    val suggestedTargetAudience: String = "غير متاح",
    val peakRetentionSec: Float = -1f,
    val evaluatedAt: Long = System.currentTimeMillis()
)

/**
 * Room Entity tracking user repurposing session history, time saved,
 * dispatch logs, and productivity analytics.
 */
@Entity(
    tableName = "repurposing_history",
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["timestamp"]),
        Index(value = ["actionType"])
    ]
)
@JsonClass(generateAdapter = true)
data class RepurposingHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val videoTitle: String,
    val sourceUrl: String,
    val actionType: String = "AI_REPURPOSE_PROCESSED", // AI_REPURPOSE_PROCESSED, DIRECT_API_PUBLISHED, EXPORTED_CLIP, CAPTION_GENERATED
    val clipsGeneratedCount: Int = 0,
    val highestViralScore: Int = 0,
    val estimatedTimeSavedMinutes: Int = 0,
    val status: String = "SUCCESS", // SUCCESS, PENDING, FAILED
    val targetPlatform: String = "TikTok & Shorts",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
