package com.example.domain.ai

import com.example.data.model.AiProviderConfig
import com.example.data.model.AiProviderType
import com.example.data.model.AiTemplateRecommendation
import com.example.data.model.ClipGenerationData
import com.example.data.model.DedicatedCaptionResult
import com.example.domain.model.CreatorProfile

/**
 * Result wrapper representing success, error, quota exhaustion, or rate limit with details.
 */
sealed class AiExecutionResult<out T> {
    data class Success<out T>(
        val data: T,
        val providerName: String,
        val latencyMs: Long,
        val tokensUsed: Long = 0L,
        val estimatedCostUsd: Double = 0.0
    ) : AiExecutionResult<T>()

    data class Failure(
        val providerName: String,
        val httpCode: Int? = null,
        val errorMessage: String,
        val isRateLimitOrQuota: Boolean = false,
        val canFailover: Boolean = true
    ) : AiExecutionResult<Nothing>()
}

/**
 * Universal AI Provider Interface for Gemini, OpenAI, Anthropic, OpenRouter, Groq, Mistral & Local Models
 */
interface AiProvider {
    val providerType: AiProviderType
    val config: AiProviderConfig
    val capabilities: Set<AiCapability>
        get() = providerType.defaultCapabilities()

    suspend fun testConnection(): Pair<Boolean, String>

    suspend fun analyzeVideoAndDiscoverClips(
        videoTitle: String,
        durationSec: Int,
        userNicheHint: String,
        targetPlatform: String,
        captionStyle: String,
        requestedClipCount: Int = 0,
        creatorProfile: CreatorProfile? = null
    ): AiExecutionResult<List<ClipGenerationData>>

    suspend fun executeAiEditingCommand(
        commandPrompt: String,
        clipTitle: String,
        currentTranscript: String,
        currentViralityScore: Int
    ): AiExecutionResult<String>

    suspend fun generateDedicatedCaptions(
        topic: String,
        targetPlatform: String,
        tone: String,
        detectedNiche: String = "Viral Media"
    ): AiExecutionResult<DedicatedCaptionResult>

    suspend fun recommendTemplateStyle(
        videoTitle: String,
        videoTopic: String
    ): AiExecutionResult<AiTemplateRecommendation>
}
