package com.example.domain.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CandidateClipDetector {
    fun buildInterestCurve(
        transcript: Transcript,
        audioSignals: List<AudioSignal>,
        windowSec: Float = 2f
    ): InterestCurve {
        val duration = transcript.durationSec.coerceAtLeast(1f)
        val safeWindow = windowSec.coerceIn(0.5f, 10f)
        val points = generateSequence(0f) { current ->
            val next = current + safeWindow
            if (next < duration) next else null
        }.map { timestamp ->
            val segment = transcript.segments.minByOrNull { abs(it.startSec - timestamp) }
            val nearbyAudio = audioSignals.filter { it.startSec <= timestamp + safeWindow && it.endSec >= timestamp }
            val lexical = segment?.text.orEmpty().split(Regex("\\s+")).count { it.length >= 6 }
            val question = if (segment?.text?.contains("?") == true || segment?.text?.contains("؟") == true) 0.2f else 0f
            val energy = nearbyAudio.map { it.intensity }.average().toFloat().coerceIn(0f, 1f)
            val peak = nearbyAudio.count { it.type == AudioSignalType.PEAK }.coerceAtMost(3) / 3f
            val confidence = segment?.confidence?.coerceIn(0f, 1f) ?: 0f
            val score = (
                lexical.coerceAtMost(12) / 12f * 0.35f +
                    question + energy * 0.3f + peak * 0.15f + confidence * 0.2f
                ).coerceIn(0f, 1f)
            InterestPoint(
                timestampSec = timestamp,
                score = score,
                signals = buildList {
                    if (lexical >= 5) add("lexical_novelty")
                    if (question > 0f) add("question")
                    if (energy >= 0.55f) add("audio_energy")
                    if (peak > 0f) add("audio_peak")
                    if (confidence >= 0.8f) add("transcript_confidence")
                },
                confidence = (confidence * 0.7f + 0.3f).coerceIn(0f, 1f)
            )
        }.toList()
        return InterestCurve(points, safeWindow, "sentence+lexical+audio+confidence")
    }

    /**
     * Returns a practical limit without fabricating clips to meet a quota.
     * The caller's explicit limit remains authoritative when it is positive.
     */
    fun recommendedCandidateCount(durationSec: Float, explicitLimit: Int? = null): Int {
        if (explicitLimit != null && explicitLimit > 0) return explicitLimit.coerceIn(1, 30)
        val minutes = (durationSec / 60f).roundToInt().coerceAtLeast(1)
        return (minutes + 2).coerceIn(3, 30)
    }

    fun detect(
        transcript: Transcript,
        curve: InterestCurve,
        maxCandidates: Int = 30,
        qualityFloor: Float = 0.28f,
        minSeparationSec: Float = 5f
    ): List<CandidateClip> {
        val targetCount = recommendedCandidateCount(transcript.durationSec, maxCandidates)
        val topPoints = curve.points
            .asSequence()
            .filter { it.score >= qualityFloor }
            .sortedByDescending { it.score }
            .take(targetCount * 4)
            .toList()

        val candidates = topPoints.mapNotNull { point ->
            val anchorIndex = transcript.segments.indexOfMinByOrNull { abs(it.startSec - point.timestampSec) }
            val anchor = anchorIndex?.let { transcript.segments.getOrNull(it) } ?: return@mapNotNull null
            val start = optimizeStart(transcript.segments, anchorIndex, anchor.startSec, point.timestampSec)
            val end = optimizeEnd(transcript.segments, anchorIndex, anchor.endSec, start)
            if (end - start < 5f) return@mapNotNull null
            val hook = anchor.text.trim().take(160)
            CandidateClip(
                startSec = start,
                endSec = end,
                topic = anchor.text.split(Regex("[.!؟?]"), limit = 2).firstOrNull().orEmpty().trim(),
                hook = hook,
                reason = "مرشح بحدود hook محسنة ونقطة اهتمام ${"%.2f".format(point.score)}.",
                signals = point.signals,
                confidence = (point.confidence * (0.6f + point.score * 0.4f)).coerceIn(0f, 1f)
            )
        }.sortedByDescending { it.confidence * exp(-it.startSec / 100000f) }

        return selectDiverse(candidates, targetCount, minSeparationSec)
    }

    private fun optimizeStart(
        segments: List<TranscriptSegment>,
        anchorIndex: Int?,
        anchorStart: Float,
        pointSec: Float
    ): Float {
        if (anchorIndex == null) return max(0f, pointSec - 2f)
        val previous = segments.getOrNull(anchorIndex - 1)
        val canIncludePrevious = previous != null && anchorStart - previous.endSec <= 1.5f
        return when {
            canIncludePrevious && previous!!.text.length < 90 -> previous.startSec
            abs(anchorStart - pointSec) <= 1.5f -> anchorStart
            else -> max(0f, pointSec - 2f)
        }
    }

    private fun optimizeEnd(
        segments: List<TranscriptSegment>,
        anchorIndex: Int?,
        anchorEnd: Float,
        start: Float
    ): Float {
        val next = anchorIndex?.let { segments.getOrNull(it + 1) }
        val contextEnd = if (next != null && next.startSec - anchorEnd <= 1.5f) next.endSec else anchorEnd + 8f
        return min(segments.lastOrNull()?.endSec ?: (start + 60f), max(start + 15f, contextEnd)).coerceAtMost(start + 60f)
    }

    private fun selectDiverse(
        candidates: List<CandidateClip>,
        targetCount: Int,
        minSeparationSec: Float
    ): List<CandidateClip> {
        val selected = mutableListOf<CandidateClip>()
        for (candidate in candidates) {
            val tooClose = selected.any { existing ->
                abs(existing.startSec - candidate.startSec) < minSeparationSec || overlap(existing, candidate) > 0.65f
            }
            val sameTopic = selected.count { topicSimilarity(it.topic, candidate.topic) >= 0.7f }
            if (!tooClose && sameTopic < 2) selected += candidate
            if (selected.size >= targetCount) break
        }
        return selected
    }

    private fun overlap(a: CandidateClip, b: CandidateClip): Float {
        val intersection = max(0f, min(a.endSec, b.endSec) - max(a.startSec, b.startSec))
        val union = max(a.endSec, b.endSec) - min(a.startSec, b.startSec)
        return if (union <= 0f) 0f else intersection / union
    }

    private fun topicSimilarity(a: String, b: String): Float {
        val left = a.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        val right = b.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0f
        return left.intersect(right).size.toFloat() / left.union(right).size.toFloat()
    }

    private fun <T> List<T>.indexOfMinByOrNull(selector: (T) -> Float): Int? {
        if (isEmpty()) return null
        var bestIndex = 0
        var bestValue = selector(first())
        for (index in 1 until size) {
            val value = selector(this[index])
            if (value < bestValue) {
                bestValue = value
                bestIndex = index
            }
        }
        return bestIndex
    }
}
