package com.example.domain.ai

import com.example.data.model.AiProviderConfig
import com.example.data.model.AiProviderType
import com.example.data.model.AiTemplateRecommendation
import com.example.data.model.ClipGenerationData
import com.example.data.model.DedicatedCaptionResult
import com.example.data.remote.GeminiClipService
import com.example.domain.model.CreatorProfile

class ProductionGeminiProvider(
    private val geminiService: GeminiClipService,
    override val config: AiProviderConfig
) : AiProvider {

    override val providerType: AiProviderType = AiProviderType.GEMINI

    override suspend fun testConnection(): Pair<Boolean, String> {
        return geminiService.testApiKey(config.apiKey)
    }

    override suspend fun analyzeVideoAndDiscoverClips(
        videoTitle: String,
        durationSec: Int,
        userNicheHint: String,
        targetPlatform: String,
        captionStyle: String,
        requestedClipCount: Int,
        creatorProfile: CreatorProfile?
    ): AiExecutionResult<List<ClipGenerationData>> {
        val start = System.currentTimeMillis()
        return try {
            val analysisContext = buildString {
                append(userNicheHint)
                append("\nTarget platform: ").append(targetPlatform)
                append("\nCaption style: ").append(captionStyle)
                append("\nRequested clip count: ").append(requestedClipCount.coerceIn(1, 20))
                creatorProfile?.let { profile ->
                    append("\nCreator language: ").append(profile.primaryLanguage)
                    append("; category: ").append(profile.contentCategory)
                    append("; audience: ").append(profile.targetAudience)
                }
            }
            val clips = geminiService.analyzeAndGenerateClips(
                title = videoTitle,
                sourceUrl = "",
                transcriptOrPrompt = analysisContext,
                durationMinutes = ((durationSec + 59) / 60).coerceAtLeast(1),
                providers = listOf(config)
            )
            val latency = System.currentTimeMillis() - start
            AiExecutionResult.Success(
                data = clips,
                providerName = config.name.ifBlank { "Google Gemini" },
                latencyMs = latency,
                // Gemini usage metadata is not returned by the current REST path;
                // keep this explicitly unavailable instead of inventing a token count.
                tokensUsed = 0L
            )
        } catch (e: Exception) {
            AiExecutionResult.Failure(
                providerName = config.name.ifBlank { "Google Gemini" },
                errorMessage = e.localizedMessage ?: "Gemini analysis error",
                canFailover = true
            )
        }
    }

    override suspend fun executeAiEditingCommand(
        commandPrompt: String,
        clipTitle: String,
        currentTranscript: String,
        currentViralityScore: Int
    ): AiExecutionResult<String> {
        val start = System.currentTimeMillis()
        return try {
            AiExecutionResult.Failure(
                providerName = config.name.ifBlank { "Google Gemini" },
                httpCode = 501,
                errorMessage = "لم يُنفّذ أمر التحرير: لا يوجد مسار فعلي لتعديل ملف الفيديو حاليًا.",
                canFailover = false
            )
        } catch (e: Exception) {
            AiExecutionResult.Failure(
                providerName = config.name.ifBlank { "Google Gemini" },
                errorMessage = e.localizedMessage ?: "Editing command failed"
            )
        }
    }

    override suspend fun generateDedicatedCaptions(
        topic: String,
        targetPlatform: String,
        tone: String,
        detectedNiche: String
    ): AiExecutionResult<DedicatedCaptionResult> {
        val start = System.currentTimeMillis()
        return try {
            val res = geminiService.generateDedicatedVideoCaption(
                videoTitle = topic,
                transcript = detectedNiche,
                tone = tone,
                targetPlatform = targetPlatform,
                language = "ar",
                includeEmojis = true,
                providers = listOf(config)
            )
            AiExecutionResult.Success(
                data = res,
                providerName = config.name.ifBlank { "Google Gemini" },
                latencyMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            AiExecutionResult.Failure(
                providerName = config.name.ifBlank { "Google Gemini" },
                errorMessage = e.localizedMessage ?: "Caption generation failed"
            )
        }
    }

    override suspend fun recommendTemplateStyle(
        videoTitle: String,
        videoTopic: String
    ): AiExecutionResult<AiTemplateRecommendation> {
        val start = System.currentTimeMillis()
        return try {
            val rec = geminiService.determineOptimalTemplateAndPreset(
                title = videoTitle,
                transcriptOrPrompt = videoTopic,
                videoDurationSec = 300,
                providers = listOf(config)
            )
            AiExecutionResult.Success(
                data = rec,
                providerName = config.name.ifBlank { "Google Gemini" },
                latencyMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            AiExecutionResult.Failure(
                providerName = config.name.ifBlank { "Google Gemini" },
                errorMessage = e.localizedMessage ?: "Template recommendation failed"
            )
        }
    }
}
