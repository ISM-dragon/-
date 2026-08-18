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
