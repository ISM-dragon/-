package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CompetitorComparison(
    val slug: String,
    val name: String,
    val tagline: String,
    val seoTitle: String,
    val metaDescription: String,
    val h1: String,
    val category: String,
    val rating: Float,
    val startingPrice: String,
    val freePlanDetails: String,
    val overview: String,
    val coreAudience: String,
    val whyCompare: String,
    val winnerSummary: String,
    val criteriaList: List<ComparisonCriteriaItem>,
    val opusPros: List<String>,
    val competitorPros: List<String>,
    val opusCons: List<String>,
    val competitorCons: List<String>,
    val verdictOpus: String,
    val verdictCompetitor: String,
    val faqs: List<ComparisonFaqItem>,
    val structuredDataJsonLd: String
)

@JsonClass(generateAdapter = true)
data class ComparisonCriteriaItem(
    val featureName: String,
    val opusValue: String,
    val competitorValue: String,
    val winner: String, // "opus", "competitor", "tie"
    val note: String
)

@JsonClass(generateAdapter = true)
data class ComparisonFaqItem(
    val question: String,
    val answer: String
)

@JsonClass(generateAdapter = true)
data class UserCreditState(
    val creditsRemaining: Int = 60, // in minutes
    val totalProcessedMinutes: Int = 145,
    val currentPlan: String = "Pro Plan", // Free, Starter, Pro, Business
    val renewalDate: String = "September 1, 2026",
    val clipsCreatedCount: Int = 38
)

@JsonClass(generateAdapter = true)
data class CompetitorVideoPerformance(
    val id: String,
    val creatorName: String,
    val handle: String,
    val videoTitle: String,
    val platform: String, // "TikTok", "YouTube Shorts", "Instagram Reels"
    val viewsCount: String, // e.g. "3.8M"
    val likeCount: String, // e.g. "412K"
    val commentCount: String, // e.g. "8.2K"
    val shareRatePercent: Float, // e.g. 9.4%
    val viralityScore: Int, // e.g. 94
    val hookScore: Int, // e.g. 96
    val hookDurationSec: Float, // e.g. 2.1s
    val retentionScore: Int, // e.g. 91
    val averageWatchTimePercent: Int, // e.g. 84%
    val pacingCpm: Int, // Cuts Per Minute e.g. 18
    val wordsPerMinute: Int, // e.g. 172
    val captionStyle: String, // e.g. "Bold Yellow & Green Pop"
    val hasEmojis: Boolean = true,
    val hasBRoll: Boolean = true,
    val bRollCount: Int = 4,
    val topKeywords: List<String> = emptyList(),
    val hashtags: List<String> = emptyList(),
    val audienceCategory: String = "General Viral",
    val keyStrengths: List<String> = emptyList(),
    val keyVulnerabilities: List<String> = emptyList(),
    val aiComparisonInsight: String = ""
)

data class MetricComparisonItem(
    val metricName: String,
    val clipValue: Float,
    val clipDisplay: String,
    val competitorValue: Float,
    val competitorDisplay: String,
    val isHigherBetter: Boolean = true,
    val unit: String = "%",
    val explanation: String = ""
)
