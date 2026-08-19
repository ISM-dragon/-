package com.example.domain.analysis

import kotlin.math.max

/**
 * Converts word-level evidence into readable caption lines without changing the source transcript.
 * Rendering layers can use the resulting lines for Media3 overlays or Compose previews.
 */
object CaptionLayoutEngine {
    fun buildLines(
        words: List<WordTimestamp>,
        style: CaptionStylePreset = CaptionStyleCatalog.find("dynamic"),
        maxCharacters: Int = 34,
        maxWords: Int = 7
    ): List<CaptionLine> {
        require(maxCharacters >= 8)
        require(maxWords >= 1)
        if (words.isEmpty()) return emptyList()

        val sorted = words
            .filter { it.word.isNotBlank() && it.endSec >= it.startSec }
            .sortedBy { it.startSec }
        if (sorted.isEmpty()) return emptyList()

        val lines = mutableListOf<CaptionLine>()
        var current = mutableListOf<WordTimestamp>()
        var currentChars = 0

        fun flush() {
            if (current.isEmpty()) return
            lines += CaptionLine(
                words = current.toList(),
                startSec = current.first().startSec,
                endSec = current.last().endSec,
                position = style.position,
                styleId = style.id
            )
            current = mutableListOf()
            currentChars = 0
        }

        sorted.forEachIndexed { index, word ->
            val separator = if (current.isEmpty()) 0 else 1
            val wouldOverflow = current.isNotEmpty() &&
                (currentChars + separator + word.word.length > maxCharacters || current.size >= maxWords)
            val previous = sorted.getOrNull(index - 1)
            val semanticBoundary = previous != null &&
                (previous.word.endsWithAny(".", "!", "?", "؟", "。") || word.startSec - previous.endSec > 0.85f)

            if (wouldOverflow || (semanticBoundary && current.isNotEmpty())) flush()
            current += word
            currentChars += word.word.length + if (current.size > 1) 1 else 0
        }
        flush()
        return lines
    }

    fun isRtl(text: String): Boolean {
        val rtl = text.count { it in '\u0590'..'\u08FF' }
        val ltr = text.count { it.isLetter() && it !in '\u0590'..'\u08FF' }
        return rtl > ltr
    }

    fun highlightedWordIndexes(
        line: CaptionLine,
        mode: String,
        maxHighlights: Int = 2
    ): Set<Int> {
        if (mode.equals("none", ignoreCase = true)) return emptySet()
        val candidates = line.words.mapIndexedNotNull { index, word ->
            val normalized = word.word.trim { !it.isLetterOrDigit() }
            if (normalized.length < 4) null else index to (normalized.length * word.confidence)
        }
        return when {
            mode.equals("karaoke", ignoreCase = true) -> line.words.indices.toSet()
            else -> candidates.sortedByDescending { it.second }
                .take(max(1, maxHighlights))
                .map { it.first }
                .toSet()
        }
    }

    fun safeTextWidth(
        viewportFraction: Float,
        safeZones: SafeZoneConfig = SafeZoneConfig()
    ): Float {
        val horizontal = (1f - safeZones.sideFraction * 2f).coerceIn(0.1f, 1f)
        return (viewportFraction * horizontal).coerceIn(0.1f, 1f)
    }

    private fun String.endsWithAny(vararg suffixes: String): Boolean = suffixes.any { endsWith(it) }
}
