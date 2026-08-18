package com.example

import com.example.data.model.AiProviderConfig
import com.example.data.model.AiProviderType
import com.example.data.model.AnimatedWord
import com.example.data.model.BRollIdea
import com.example.data.model.ClipGenerationData
import com.example.data.model.GoogleFlowCreditInfo
import com.example.data.model.SocialPostCopy
import com.example.data.model.ViralityAnalysisResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusArchitectureCoreTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun testGoogleFlowCreditsCalculation() {
        val credits = GoogleFlowCreditInfo(
            totalCreditsMinutes = 180,
            usedCreditsMinutes = 35,
            totalRequestsLimit = 1500,
            usedRequestsCount = 84
        )

        assertEquals(145, credits.remainingCreditsMinutes)
        assertEquals(1416, credits.remainingRequestsCount)
        assertFalse(credits.isExhausted)
        assertTrue(credits.creditPercentage in 0.80f..0.81f)
    }

    @Test
    fun testGoogleFlowCreditsExhaustion() {
        val exhaustedCredits = GoogleFlowCreditInfo(
            totalCreditsMinutes = 100,
            usedCreditsMinutes = 100,
            totalRequestsLimit = 500,
            usedRequestsCount = 500
        )

        assertEquals(0, exhaustedCredits.remainingCreditsMinutes)
        assertEquals(0, exhaustedCredits.remainingRequestsCount)
        assertTrue(exhaustedCredits.isExhausted)
        assertEquals(0f, exhaustedCredits.creditPercentage, 0.001f)
    }

    @Test
    fun testAiProviderConfigCalculations() {
        val provider = AiProviderConfig(
            name = "OpenAI GPT-4o Mini",
            providerType = AiProviderType.OPENAI.name,
            apiKey = "sk-test-12345678",
            totalCreditsAllocated = 20.0,
            usedCredits = 5.0,
            isEnabled = true
        )

        assertEquals(15.0, provider.remainingCredits, 0.001)
        assertEquals(0.75f, provider.creditPercentage, 0.001f)
        assertTrue(provider.isEnabled)
        assertFalse(provider.isExhausted)
    }

    @Test
    fun testViralityJsonParsingRobustness() {
        val sampleJson = """
        {
          "clips": [
            {
              "title": "5 Morning Habits of Billionaires",
              "startTimeSec": 15,
              "endTimeSec": 60,
              "viralityScore": 96,
              "hookScore": 98,
              "retentionScore": 94,
              "emotionalScore": 90,
              "shareabilityScore": 95,
              "punchlineScore": 92,
              "hookExplanation": "Direct psychological question hook immediately arrests scrolling.",
              "transcript": "Did you know that 80% of top CEOs follow this exact routine?",
              "keywords": ["habits", "success", "morning"],
              "emojis": ["🔥", "⚡", "🚀"],
              "bRollIdeas": [
                {
                  "title": "Alarm clock close-up",
                  "timestampSec": 16,
                  "visualPrompt": "Cinematic macro shot of a ringing alarm clock at 5 AM",
                  "soundEffect": "Swoosh"
                }
              ],
              "socialCopies": [
                {
                  "platform": "TikTok",
                  "caption": "Transform your morning in 3 steps #productivity #success",
                  "hook": "Do this every morning!",
                  "hashtags": ["#shorts", "#fyp", "#viral"]
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val adapter = moshi.adapter(ViralityAnalysisResult::class.java)
        val result = adapter.fromJson(sampleJson)

        assertNotNull(result)
        assertEquals(1, result?.clips?.size)
        val clip = result?.clips?.first()
        assertEquals("5 Morning Habits of Billionaires", clip?.title)
        assertEquals(96, clip?.viralityScore)
        assertEquals(15, clip?.startTimeSec)
        assertEquals(60, clip?.endTimeSec)
        assertEquals(1, clip?.bRollIdeas?.size)
        assertEquals("Alarm clock close-up", clip?.bRollIdeas?.first()?.title)
        assertEquals(1, clip?.socialCopies?.size)
    }

    @Test
    fun testAnimatedWordsSerializationDeserialization() {
        val words = listOf(
            AnimatedWord(word = "Never", startSec = 0.0f, endSec = 0.4f, isHighlight = true, emoji = "❌", colorHex = "#FF0055"),
            AnimatedWord(word = "give", startSec = 0.4f, endSec = 0.8f, isHighlight = false, colorHex = "#FFFFFF"),
            AnimatedWord(word = "up!", startSec = 0.8f, endSec = 1.3f, isHighlight = true, emoji = "🚀", colorHex = "#00FF66")
        )

        val listType = Types.newParameterizedType(List::class.java, AnimatedWord::class.java)
        val adapter = moshi.adapter<List<AnimatedWord>>(listType)
        val json = adapter.toJson(words)
        val deserialized = adapter.fromJson(json)

        assertNotNull(deserialized)
        assertEquals(3, deserialized?.size)
        assertEquals("Never", deserialized?.get(0)?.word)
        assertTrue(deserialized?.get(0)?.isHighlight == true)
        assertEquals("🚀", deserialized?.get(2)?.emoji)
    }

    @Test
    fun testProviderTypeDefaults() {
        val gemini = AiProviderType.GEMINI
        assertEquals("gemini-2.5-flash", gemini.defaultModel)
        assertTrue(gemini.docsUrl.isNotBlank())
        
        val anthropic = AiProviderType.ANTHROPIC
        assertEquals("claude-3-5-sonnet-20241022", anthropic.defaultModel)
        
        val groq = AiProviderType.GROQ
        assertEquals("llama-3.3-70b-versatile", groq.defaultModel)
    }
}
