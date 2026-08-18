package com.example

import com.example.data.model.ClipGenerationData
import com.example.domain.analysis.AnalysisValidator
import com.example.domain.analysis.AudioSignal
import com.example.domain.analysis.AudioSignalType
import com.example.domain.analysis.CandidateClipDetector
import com.example.domain.analysis.Transcript
import com.example.domain.analysis.TranscriptSegment
import com.example.domain.analysis.WordTimestamp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisEngineTest {
    @Test
    fun validatorRejectsOutOfRangeAndOutOfBoundsClips() {
        val clip = ClipGenerationData(
            title = "Invalid",
            startTimeSec = -1,
            endTimeSec = 99,
            viralityScore = 120,
            hookScore = 50,
            retentionScore = 50,
            emotionalScore = 50,
            shareabilityScore = 50,
            punchlineScore = 50,
            hookExplanation = "hook",
            transcript = "text",
            keywords = emptyList(),
            emojis = emptyList(),
            bRollIdeas = emptyList(),
            socialCopies = emptyList()
        )
        assertFalse(AnalysisValidator.validateClips(listOf(clip), 60).isValid)
    }

    @Test
    fun detectorUsesSentenceBoundariesAndAudioSignals() {
        val transcript = Transcript(
            language = "en",
            provider = "test",
            isWordTimed = true,
            segments = listOf(
                TranscriptSegment(
                    text = "Why does this work? The answer changes everything.",
                    startSec = 0f,
                    endSec = 20f,
                    words = listOf(WordTimestamp("Why", 0f, 0.3f))
                )
            )
        )
        val signals = listOf(AudioSignal(0f, 2f, AudioSignalType.PEAK, 0.9f, 0.9f, "test"))
        val detector = CandidateClipDetector()
        val curve = detector.buildInterestCurve(transcript, signals)
        val candidates = detector.detect(transcript, curve)
        assertTrue(curve.points.isNotEmpty())
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.first().endSec > candidates.first().startSec)
    }
}

