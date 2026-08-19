package com.example

import com.example.domain.analysis.AudioSignal
import com.example.domain.analysis.AudioSignalType
import com.example.domain.analysis.CandidateClipDetector
import com.example.domain.analysis.ClipQualityTier
import com.example.domain.analysis.ClipRatingDimensions
import com.example.domain.analysis.Transcript
import com.example.domain.analysis.TranscriptSegment
import com.example.domain.analysis.WordTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedAnalysisModelsTest {
    @Test
    fun qualityTierUsesEvidenceBackedThresholds() {
        assertEquals(ClipQualityTier.EXCEPTIONAL, ClipQualityTier.fromScore(90))
        assertEquals(ClipQualityTier.STRONG, ClipQualityTier.fromScore(89))
        assertEquals(ClipQualityTier.GOOD, ClipQualityTier.fromScore(70))
        assertEquals(ClipQualityTier.BORDERLINE, ClipQualityTier.fromScore(60))
        assertEquals(ClipQualityTier.WEAK, ClipQualityTier.fromScore(59))

        val rating = ClipRatingDimensions(
            contentScore = 92,
            viralPotential = 88,
            editQuality = 90,
            personalFit = null,
            confidence = 0.84f,
            finalScore = 90,
            evidence = listOf("word_timed_transcript", "face_track")
        )
        assertEquals(ClipQualityTier.EXCEPTIONAL, rating.tier)
    }

    @Test
    fun detectorDynamicallyLimitsAndSeparatesCandidates() {
        val segments = (0 until 12).map { index ->
            val start = index * 10f
            TranscriptSegment(
                text = if (index % 2 == 0) "Why does this important idea change everything?" else "This supporting explanation adds useful context.",
                startSec = start,
                endSec = start + 7f,
                words = listOf(WordTimestamp("idea", start + 1f, start + 1.4f)),
                confidence = 0.95f
            )
        }
        val transcript = Transcript("en", segments, "test", isWordTimed = true)
        val audio = (0 until 12).map { index ->
            AudioSignal(index * 10f, index * 10f + 3f, AudioSignalType.PEAK, 0.9f, 0.9f, "test")
        }
        val detector = CandidateClipDetector()
        val curve = detector.buildInterestCurve(transcript, audio, windowSec = 2f)
        val candidates = detector.detect(transcript, curve, maxCandidates = 0)

        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.size <= detector.recommendedCandidateCount(transcript.durationSec, null))
        assertTrue(candidates.all { it.durationSec >= 5f })
        assertTrue(candidates.zipWithNext().all { (left, right) ->
            kotlin.math.abs(left.startSec - right.startSec) >= 5f || left.topic != right.topic
        })
    }
}
