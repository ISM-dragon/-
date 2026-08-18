package com.example

import com.example.data.model.AiProviderConfig
import com.example.data.model.AiProviderType
import com.example.domain.ai.AiExecutionResult
import com.example.domain.ai.AiProvider
import com.example.domain.ai.IntelligentAiRouter
import com.example.domain.model.CreatorProfile
import com.example.domain.model.ExplainableViralityFactors
import com.example.domain.model.PipelineJob
import com.example.domain.model.PipelineStageProgress
import com.example.domain.model.PipelineStageStatus
import com.example.domain.model.PipelineStageType
import com.example.domain.security.SecureKeyManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductionArchitectureSuiteTest {

    @Test
    fun testExplainableViralityFactorsScoringAlgorithm() {
        val factors = ExplainableViralityFactors(
            hookStrength = 95,
            retentionPotential = 90,
            emotionalIntensity = 85,
            noveltyScore = 90,
            clarityScore = 95,
            pacingScore = 90,
            shareabilityScore = 95,
            curiosityGapScore = 90,
            audienceRelevance = 90
        )

        val score = factors.overallCalculatedScore
        assertTrue("Virality score should be high quality >= 90", score >= 90)
        assertEquals(91, score)
    }

    @Test
    fun testCreatorProfileDefaults() {
        val profile = CreatorProfile()
        assertEquals("ar", profile.primaryLanguage)
        assertEquals("Opus Neon", profile.captionTheme)
        assertTrue(profile.autoHighlightKeywords)
        assertTrue(profile.includeEmojisInCaptions)
        assertEquals(0.5f, profile.silenceRemovalAggressiveness, 0.01f)
    }

    @Test
    fun testSecureKeyManagerMasking() {
        val context = RuntimeEnvironment.getApplication()
        val secureKeyManager = SecureKeyManager(context)

        val sampleApiKey = "sk-proj-abc123456789xyz9999"
        val masked = secureKeyManager.maskKey(sampleApiKey)
        
        assertTrue("Key should be masked with dots", masked.contains("••••"))
        assertTrue("Key start should be preserved for recognition", masked.startsWith("sk-pro"))
        assertTrue("Key end should be preserved", masked.endsWith("9999"))
        assertFalse("Raw key should not be exposed", masked == sampleApiKey)
    }

    @Test
    fun testPipelineJobStageTransitions() {
        var job = PipelineJob(
            projectId = 101L,
            currentStage = PipelineStageType.IMPORT,
            overallStatus = PipelineStageStatus.PROCESSING
        )

        assertEquals(PipelineStageType.values().size, job.stages.size)
        assertEquals(PipelineStageStatus.PROCESSING, job.overallStatus)
        assertFalse(job.isCancelled)

        // Cancel job
        job = job.copy(isCancelled = true, overallStatus = PipelineStageStatus.CANCELLED)
        assertTrue(job.isCancelled)
        assertEquals(PipelineStageStatus.CANCELLED, job.overallStatus)
    }

    @Test
    fun testIntelligentRouterFailover() = runBlocking {
        val failingProvider = object : AiProvider {
            override val providerType = AiProviderType.OPENAI
            override val config = AiProviderConfig(
                name = "Failing OpenAI",
                apiKey = "invalid_key",
                priority = 1,
                isEnabled = true
            )
            override suspend fun testConnection() = Pair(false, "Quota exceeded 429")
            override suspend fun analyzeVideoAndDiscoverClips(
                videoTitle: String, durationSec: Int, userNicheHint: String,
                targetPlatform: String, captionStyle: String, requestedClipCount: Int,
                creatorProfile: CreatorProfile?
            ) = AiExecutionResult.Failure(
                providerName = "Failing OpenAI",
                httpCode = 429,
                errorMessage = "Rate limit reached",
                canFailover = true
            )
            override suspend fun executeAiEditingCommand(commandPrompt: String, clipTitle: String, currentTranscript: String, currentViralityScore: Int) = AiExecutionResult.Failure("Failing OpenAI", 429, "Rate limit")
            override suspend fun generateDedicatedCaptions(topic: String, targetPlatform: String, tone: String, detectedNiche: String) = AiExecutionResult.Failure("Failing OpenAI", 429, "Rate limit")
            override suspend fun recommendTemplateStyle(videoTitle: String, videoTopic: String) = AiExecutionResult.Failure("Failing OpenAI", 429, "Rate limit")
        }

        val healthyProvider = object : AiProvider {
            override val providerType = AiProviderType.GEMINI
            override val config = AiProviderConfig(
                name = "Backup Gemini",
                apiKey = "valid_key",
                priority = 2,
                isEnabled = true
            )
            override suspend fun testConnection() = Pair(true, "OK")
            override suspend fun analyzeVideoAndDiscoverClips(
                videoTitle: String, durationSec: Int, userNicheHint: String,
                targetPlatform: String, captionStyle: String, requestedClipCount: Int,
                creatorProfile: CreatorProfile?
            ): AiExecutionResult<List<com.example.data.model.ClipGenerationData>> = AiExecutionResult.Success(
                data = emptyList<com.example.data.model.ClipGenerationData>(),
                providerName = "Backup Gemini",
                latencyMs = 120L
            )
            override suspend fun executeAiEditingCommand(commandPrompt: String, clipTitle: String, currentTranscript: String, currentViralityScore: Int) = AiExecutionResult.Success("Applied", "Backup Gemini", 100L)
            override suspend fun generateDedicatedCaptions(topic: String, targetPlatform: String, tone: String, detectedNiche: String) = AiExecutionResult.Failure("Backup Gemini", 500, "Error")
            override suspend fun recommendTemplateStyle(videoTitle: String, videoTopic: String) = AiExecutionResult.Failure("Backup Gemini", 500, "Error")
        }

        val router = IntelligentAiRouter(listOf(failingProvider, healthyProvider))
        val result = router.routeExecutionWithFailover("Discover Clips") { provider ->
            provider.analyzeVideoAndDiscoverClips("Test", 60, "", "", "", 2)
        }

        assertTrue("Router should have fallen over to healthy secondary provider", result is AiExecutionResult.Success)
        val success = result as AiExecutionResult.Success
        assertEquals("Backup Gemini", success.providerName)
    }
}
