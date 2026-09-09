package com.example.domain.analysis

import com.example.data.model.BRollIdea
import com.example.data.model.ClipGenerationData
import com.example.data.model.SocialPostCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the clip validation rules that run right after Gemini returns
 * its clip candidates (no Android dependencies).
 */
class AnalysisValidatorTest {

    private fun clip(
        title: String = "Hook title",
        start: Int = 10,
        end: Int = 45,
        scores: Int = 85,
        transcript: String = "Spoken words here",
        hookExplanation: String = "explanation"
    ) = ClipGenerationData(
        title = title,
        startTimeSec = start,
        endTimeSec = end,
        viralityScore = scores,
        hookScore = scores,
        retentionScore = scores,
        emotionalScore = scores,
        shareabilityScore = scores,
        punchlineScore = scores,
        hookExplanation = hookExplanation,
        transcript = transcript,
        keywords = listOf("focus", "growth"),
        emojis = listOf("🔥"),
        bRollIdeas = listOf(BRollIdea(title = "Visual", timestampSec = 12, visualPrompt = "a visual")),
        socialCopies = listOf(SocialPostCopy(platform = "TikTok", caption = "cap", hook = "hook", hashtags = listOf("#x"))),
        wordTimestamps = emptyList()
    )

    @Test
    fun validClipPasses() {
        val report = AnalysisValidator.validateClips(listOf(clip()), sourceDurationSec = 300)
        assertTrue(report.isValid)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun emptyListIsInvalid() {
        val report = AnalysisValidator.validateClips(emptyList(), sourceDurationSec = 300)
        assertFalse(report.isValid)
        assertTrue(report.issues.any { it.field == "clips" })
    }

    @Test
    fun negativeStartAndReversedEndAreFlagged() {
        val report = AnalysisValidator.validateClips(listOf(clip(start = -1, end = 30)), sourceDurationSec = 300)
        assertFalse(report.isValid)
        assertTrue(report.issues.any { it.field == "clips[0].startTimeSec" })

        val reversed = AnalysisValidator.validateClips(listOf(clip(start = 40, end = 40)), sourceDurationSec = 300)
        assertTrue(reversed.issues.any { it.field == "clips[0].endTimeSec" })
    }

    @Test
    fun endBeyondSourceDurationIsFlagged() {
        val report = AnalysisValidator.validateClips(listOf(clip(start = 10, end = 500)), sourceDurationSec = 300)
        assertFalse(report.isValid)
        assertTrue(report.issues.any { it.field == "clips[0].endTimeSec" })
    }

    @Test
    fun clipOutsideMinMaxWindowIsFlagged() {
        val tooShort = AnalysisValidator.validateClips(
            listOf(clip(start = 0, end = 2)),
            sourceDurationSec = 300,
            minDurationSec = 5,
            maxDurationSec = 180
        )
        assertTrue(tooShort.issues.any { it.field == "clips[0].duration" })

        val tooLong = AnalysisValidator.validateClips(
            listOf(clip(start = 0, end = 400)),
            sourceDurationSec = 300,
            maxDurationSec = 180
        )
        assertTrue(tooLong.issues.any { it.field == "clips[0].duration" })
    }

    @Test
    fun outOfRangeScoresAreFlaggedPerField() {
        val bad = clip(scores = 101)
        val report = AnalysisValidator.validateClips(listOf(bad.copy(viralityScore = -5)), sourceDurationSec = 300)
        assertTrue(report.issues.any { it.field == "clips[0].viralityScore" })
    }

    @Test
    fun blankTitleAndTranscriptAreFlagged() {
        val report = AnalysisValidator.validateClips(listOf(clip(title = "  ", transcript = "")), sourceDurationSec = 300)
        assertTrue(report.issues.any { it.field == "clips[0].title" })
        assertTrue(report.issues.any { it.field == "clips[0].transcript" })
    }

    @Test
    fun normalizeScoresClampsIntoZeroToHundred() {
        val normalized = AnalysisValidator.normalizeScores(clip(scores = 150).copy(viralityScore = -12, hookScore = 250))
        assertEquals(0, normalized.viralityScore)
        assertEquals(100, normalized.hookScore)
    }
}
