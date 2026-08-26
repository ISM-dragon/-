package com.example.domain.analysis

import com.squareup.moshi.JsonClass

/**
 * High-dimensional analytical metrics representing virality factors
 * computed by the Virality Heuristic ML Engine.
 */
@JsonClass(generateAdapter = true)
data class ViralityAnalysisResult(
    val overallScore: Int, // 0 - 100
    val tierGrade: String, // S+, S, A+, A, B
    val algorithmTier: String,
    val summaryInsight: String,
    val pacing: PacingAnalysis,
    val hookRetention: HookRetentionAnalysis,
    val keywordDensity: KeywordDensityAnalysis,
    val emotionalResonance: EmotionalResonanceAnalysis,
    val platformFit: PlatformFitScores,
    val recommendations: List<ViralityRecommendation>
)

@JsonClass(generateAdapter = true)
data class PacingAnalysis(
    val score: Int, // 0 - 100
    val wordsPerMinute: Double,
    val totalWordCount: Int,
    val durationSec: Int,
    val pacingCategory: String, // "Optimal Viral Cadence", "High-Energy Punchy", "Narrative Storytelling", "Sluggish / Slow"
    val tempoConsistencyScore: Int, // 0 - 100
    val averageWordLength: Float,
    val pausesPerMinute: Float,
    val diagnosis: String
)

@JsonClass(generateAdapter = true)
data class HookRetentionAnalysis(
    val score: Int, // 0 - 100
    val firstThreeSecondsWordCount: Int,
    val hookType: String, // "Pattern Interrupt", "Curiosity Gap", "Contrarian Truth", "Actionable Number/Stat", "Direct Challenge"
    val hookStrengthScore: Int, // 0 - 100
    val dropOffRiskSec: Float,
    val retentionCurve: List<RetentionPoint>,
    val explanation: String
)

@JsonClass(generateAdapter = true)
data class RetentionPoint(
    val timestampSec: Float,
    val retentionPercent: Int, // 0 - 100
    val stageLabel: String
)

@JsonClass(generateAdapter = true)
data class KeywordDensityAnalysis(
    val score: Int, // 0 - 100
    val densityPercentage: Float, // e.g. 5.6%
    val totalKeywordsCount: Int,
    val searchabilityIndex: Int, // 0 - 100
    val detectedViralKeywords: List<ViralKeywordInfo>,
    val recommendedTags: List<String>,
    val diagnosis: String
)

@JsonClass(generateAdapter = true)
data class ViralKeywordInfo(
    val word: String,
    val occurrences: Int,
    val category: String, // "Power Trigger", "High Search Volume", "Emotional Seed", "Contrarian Marker"
    val weight: Float // 0.0 - 1.0
)

@JsonClass(generateAdapter = true)
data class EmotionalResonanceAnalysis(
    val emotionalScore: Int, // 0 - 100
    val curiosityGapScore: Int, // 0 - 100
    val shareabilityScore: Int, // 0 - 100
    val punchlineClimaxScore: Int, // 0 - 100
    val punchlineTimeSec: Float,
    val targetAudience: String
)

@JsonClass(generateAdapter = true)
data class PlatformFitScores(
    val tiktokScore: Int, // 0 - 100
    val reelsScore: Int, // 0 - 100
    val shortsScore: Int // 0 - 100
)

@JsonClass(generateAdapter = true)
data class ViralityRecommendation(
    val title: String,
    val impactPoints: Int, // e.g. +6 pts
    val category: String, // "Hook", "Pacing", "SEO", "Visual", "Audio"
    val actionableStep: String
)
