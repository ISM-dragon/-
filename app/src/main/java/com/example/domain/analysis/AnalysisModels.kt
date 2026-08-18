package com.example.domain.analysis

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WordTimestamp(
    val word: String,
    val startSec: Float,
    val endSec: Float,
    val confidence: Float = 1f,
    val speakerId: String? = null
)

@JsonClass(generateAdapter = true)
data class TranscriptSegment(
    val text: String,
    val startSec: Float,
    val endSec: Float,
    val words: List<WordTimestamp> = emptyList(),
    val confidence: Float = 1f,
    val speakerId: String? = null
)

@JsonClass(generateAdapter = true)
data class Transcript(
    val language: String,
    val segments: List<TranscriptSegment>,
    val provider: String,
    val isWordTimed: Boolean
) {
    val durationSec: Float
        get() = segments.maxOfOrNull { it.endSec } ?: 0f

    val text: String
        get() = segments.joinToString(" ") { it.text }.trim()
}

enum class AudioSignalType {
    SPEECH_ENERGY,
    SILENCE,
    PAUSE,
    PEAK,
    LAUGHTER,
    APPLAUSE,
    UNKNOWN
}

@JsonClass(generateAdapter = true)
data class AudioSignal(
    val startSec: Float,
    val endSec: Float,
    val type: AudioSignalType,
    val intensity: Float,
    val confidence: Float,
    val source: String
)

@JsonClass(generateAdapter = true)
data class InterestPoint(
    val timestampSec: Float,
    val score: Float,
    val signals: List<String>,
    val confidence: Float
)

@JsonClass(generateAdapter = true)
data class InterestCurve(
    val points: List<InterestPoint>,
    val windowSec: Float,
    val method: String
)

@JsonClass(generateAdapter = true)
data class CandidateClip(
    val startSec: Float,
    val endSec: Float,
    val topic: String,
    val hook: String,
    val reason: String,
    val signals: List<String>,
    val confidence: Float
) {
    val durationSec: Float
        get() = endSec - startSec
}

@JsonClass(generateAdapter = true)
data class ExplainableScore(
    val overall: Int,
    val confidence: Float,
    val factors: Map<String, Int>,
    val positiveSignals: List<String>,
    val negativeSignals: List<String>,
    val reasoningSummary: String
)

@JsonClass(generateAdapter = true)
data class AnalysisArtifacts(
    val transcript: Transcript? = null,
    val audioSignals: List<AudioSignal> = emptyList(),
    val interestCurve: InterestCurve? = null,
    val candidates: List<CandidateClip> = emptyList(),
    val scores: Map<String, ExplainableScore> = emptyMap()
)

data class ValidationIssue(
    val field: String,
    val message: String
)

data class ValidationReport(
    val isValid: Boolean,
    val issues: List<ValidationIssue> = emptyList()
)
