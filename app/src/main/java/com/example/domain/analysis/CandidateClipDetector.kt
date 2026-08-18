package com.example.domain.analysis

import kotlin.math.abs
import kotlin.math.exp

class CandidateClipDetector {
    fun buildInterestCurve(
        transcript: Transcript,
        audioSignals: List<AudioSignal>,
        windowSec: Float = 2f
    ): InterestCurve {
        val duration = transcript.durationSec.coerceAtLeast(1f)
        val points = generateSequence(0f) { current ->
            val next = current + windowSec
            if (next < duration) next else null
        }.map { timestamp ->
            val segment = transcript.segments.minByOrNull { abs(it.startSec - timestamp) }
            val nearbyAudio = audioSignals.filter { it.startSec <= timestamp + windowSec && it.endSec >= timestamp }
            val lexical = segment?.text.orEmpty().split(Regex("\\s+")).count { it.length >= 6 }
            val question = if (segment?.text?.contains("?") == true || segment?.text?.contains("؟") == true) 0.2f else 0f
            val energy = nearbyAudio.map { it.intensity }.average().toFloat().coerceIn(0f, 1f)
            val peak = nearbyAudio.count { it.type == AudioSignalType.PEAK }.coerceAtMost(3) / 3f
            val score = (lexical.coerceAtMost(12) / 12f * 0.35f + question + energy * 0.3f + peak * 0.15f).coerceIn(0f, 1f)
            InterestPoint(timestamp, score, buildList {
                if (lexical >= 5) add("lexical_novelty")
                if (question > 0f) add("question")
                if (energy >= 0.55f) add("audio_energy")
                if (peak > 0f) add("audio_peak")
            }, 0.7f)
        }.toList()
        return InterestCurve(points, windowSec, "sentence+lexical+MediaCodec-energy")
    }

    fun detect(
        transcript: Transcript,
        curve: InterestCurve,
        maxCandidates: Int = 30
    ): List<CandidateClip> {
        val topPoints = curve.points.sortedByDescending { it.score }.take(maxCandidates * 2)
        val candidates = topPoints.mapNotNull { point ->
            val segment = transcript.segments.minByOrNull { abs(it.startSec - point.timestampSec) } ?: return@mapNotNull null
            val start = maxOf(0f, segment.startSec - 3f)
            val end = minOf(transcript.durationSec, maxOf(segment.endSec + 8f, start + 15f))
            if (end - start < 5f) return@mapNotNull null
            val hook = segment.text.trim().take(160)
            CandidateClip(
                startSec = start,
                endSec = end,
                topic = segment.text.split(Regex("[.!؟?]"), limit = 2).firstOrNull().orEmpty().trim(),
                hook = hook,
                reason = "مرشح مبني على نقطة اهتمام ${point.score.toString().take(4)} مع حدود جملة.",
                signals = point.signals,
                confidence = (point.confidence * (0.6f + point.score * 0.4f)).coerceIn(0f, 1f)
            )
        }
        return candidates
            .distinctBy { "${it.startSec.toInt()}:${it.endSec.toInt()}" }
            .sortedByDescending { it.confidence * exp(-it.startSec / 100000f) }
            .take(maxCandidates)
    }
}
