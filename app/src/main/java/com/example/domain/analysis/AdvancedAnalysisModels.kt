package com.example.domain.analysis

/** Evidence-backed speaker statistics. A nullable field means that no reliable evidence was available. */
data class SpeakerRecord(
    val speakerId: String,
    val startSec: Float,
    val endSec: Float,
    val speechRatio: Float,
    val energy: Float?,
    val turnCount: Int,
    val wordCount: Int,
    val dominance: Float,
    val faceTrackIds: Set<Int> = emptySet(),
    val visualPresence: Float?
)

data class ActiveSpeakerFrame(
    val timestampSec: Float,
    val activeSpeakerId: String?,
    val secondarySpeakerIds: List<String> = emptyList(),
    val targetFaceTrackId: Int?,
    val confidence: Float,
    val cameraPriority: Float
)

enum class ReframingMode {
    AUTO,
    SPEAKER,
    GROUP,
    CENTER
}

data class SafeZoneConfig(
    val topFraction: Float = 0.10f,
    val bottomFraction: Float = 0.18f,
    val sideFraction: Float = 0.06f
) {
    init {
        require(topFraction in 0f..0.45f)
        require(bottomFraction in 0f..0.45f)
        require(sideFraction in 0f..0.45f)
    }
}

data class CaptionStylePreset(
    val id: String,
    val name: String,
    val fontFamily: String,
    val sizeSp: Int,
    val weight: Int,
    val colorArgb: Long,
    val outlineArgb: Long,
    val shadowArgb: Long,
    val position: String,
    val animation: String,
    val highlightStyle: String,
    val rtlMode: String
)

data class CaptionLine(
    val words: List<WordTimestamp>,
    val startSec: Float,
    val endSec: Float,
    val position: String,
    val styleId: String
) {
    val text: String get() = words.joinToString(" ") { it.word }.trim()
}

data class ClipRatingDimensions(
    val contentScore: Int?,
    val viralPotential: Int?,
    val editQuality: Int?,
    val personalFit: Int?,
    val confidence: Float,
    val finalScore: Int?,
    val evidence: List<String> = emptyList()
) {
    init {
        listOfNotNull(contentScore, viralPotential, editQuality, personalFit, finalScore)
            .forEach { require(it in 0..100) }
        require(confidence in 0f..1f)
    }

    val tier: ClipQualityTier?
        get() = finalScore?.let(ClipQualityTier::fromScore)
}

enum class ClipQualityTier {
    EXCEPTIONAL,
    STRONG,
    GOOD,
    BORDERLINE,
    WEAK;

    companion object {
        fun fromScore(score: Int): ClipQualityTier = when {
            score >= 90 -> EXCEPTIONAL
            score >= 80 -> STRONG
            score >= 70 -> GOOD
            score >= 60 -> BORDERLINE
            else -> WEAK
        }
    }
}

data class QualityCheckResult(
    val passed: Boolean,
    val checks: Map<String, Boolean>,
    val failures: List<String> = emptyList(),
    val confidence: Float = 1f
) {
    init {
        require(confidence in 0f..1f)
    }
}

data class AutonomousDecision(
    val decisionType: String,
    val selectedValue: String,
    val reason: String,
    val confidence: Float,
    val evidence: List<String> = emptyList()
) {
    init {
        require(confidence in 0f..1f)
    }
}
