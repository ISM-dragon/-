package com.example.domain.analysis

import com.example.data.model.ClipGenerationData
import kotlin.math.roundToInt

object AnalysisValidator {
    fun validateClips(
        clips: List<ClipGenerationData>,
        sourceDurationSec: Int,
        minDurationSec: Int = 5,
        maxDurationSec: Int = 180
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        if (clips.isEmpty()) {
            issues += ValidationIssue("clips", "لم يُرجع المزود أي مقطع قابل للتحقق.")
        }
        clips.forEachIndexed { index, clip ->
            val prefix = "clips[$index]"
            if (clip.title.isBlank()) issues += ValidationIssue("$prefix.title", "العنوان فارغ.")
            if (clip.startTimeSec < 0) issues += ValidationIssue("$prefix.startTimeSec", "البداية لا يمكن أن تكون سالبة.")
            if (clip.endTimeSec <= clip.startTimeSec) issues += ValidationIssue("$prefix.endTimeSec", "النهاية يجب أن تكون بعد البداية.")
            if (clip.endTimeSec > sourceDurationSec) issues += ValidationIssue("$prefix.endTimeSec", "النهاية تتجاوز مدة الفيديو.")
            val duration = clip.endTimeSec - clip.startTimeSec
            if (duration !in minDurationSec..maxDurationSec) {
                issues += ValidationIssue("$prefix.duration", "مدة المقطع يجب أن تكون بين $minDurationSec و$maxDurationSec ثانية.")
            }
            listOf(
                "viralityScore" to clip.viralityScore,
                "hookScore" to clip.hookScore,
                "retentionScore" to clip.retentionScore,
                "emotionalScore" to clip.emotionalScore,
                "shareabilityScore" to clip.shareabilityScore,
                "punchlineScore" to clip.punchlineScore
            ).forEach { (field, value) ->
                if (value !in 0..100) issues += ValidationIssue("$prefix.$field", "القيمة يجب أن تكون بين 0 و100.")
            }
            if (clip.transcript.isBlank()) issues += ValidationIssue("$prefix.transcript", "النص المنطوق فارغ.")
        }
        return ValidationReport(issues.isEmpty(), issues)
    }

    fun normalizeScores(clip: ClipGenerationData): ClipGenerationData = clip.copy(
        viralityScore = clip.viralityScore.coerceIn(0, 100),
        hookScore = clip.hookScore.coerceIn(0, 100),
        retentionScore = clip.retentionScore.coerceIn(0, 100),
        emotionalScore = clip.emotionalScore.coerceIn(0, 100),
        shareabilityScore = clip.shareabilityScore.coerceIn(0, 100),
        punchlineScore = clip.punchlineScore.coerceIn(0, 100)
    )
}

object ViralityScoreEngine {
    fun score(
        candidate: CandidateClip,
        audioSignals: List<AudioSignal>,
        aiScores: Map<String, Int> = emptyMap()
    ): ExplainableScore {
        val duration = candidate.durationSec.coerceAtLeast(1f)
        val energy = audioSignals.filter { it.startSec < candidate.endSec && it.endSec > candidate.startSec }
            .map { it.intensity }.average().toFloat().coerceIn(0f, 1f)
        val audioEnergy = (energy * 100).roundToInt()
        val hook = (candidate.confidence * 100).roundToInt().coerceIn(0, 100)
        val pacing = (60f / duration).coerceIn(0f, 1f).times(100).roundToInt()
        val ai = aiScores.values.average().takeIf { !it.isNaN() }?.roundToInt()?.coerceIn(0, 100) ?: 50
        val factors = mapOf(
            "hookStrength" to hook,
            "audioEnergy" to audioEnergy,
            "pacing" to pacing,
            "aiSignal" to ai
        )
        val overall = (hook * 0.30 + audioEnergy * 0.20 + pacing * 0.15 + ai * 0.35).roundToInt().coerceIn(0, 100)
        val positives = buildList {
            if (hook >= 70) add("بداية ذات إشارة hook واضحة")
            if (audioEnergy >= 65) add("طاقة صوتية مرتفعة")
            if (pacing >= 65) add("مدة وإيقاع مناسبين للمحتوى القصير")
        }
        val negatives = buildList {
            if (audioEnergy < 35) add("طاقة صوتية منخفضة")
            if (pacing < 35) add("المقطع طويل أو يحتاج حدودًا أفضل")
            if (candidate.confidence < 0.5f) add("ثقة منخفضة في حدود المرشح")
        }
        return ExplainableScore(
            overall = overall,
            confidence = ((candidate.confidence + energy) / 2f).coerceIn(0f, 1f),
            factors = factors,
            positiveSignals = positives,
            negativeSignals = negatives,
            reasoningSummary = "تم دمج hook والطاقة الصوتية والإيقاع وإشارة AI دون استخدام درجة عشوائية."
        )
    }
}
