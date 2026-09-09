package com.example.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the caption line layout engine (no Android dependencies).
 * Guards the grouping/overflow/boundary rules used by karaoke caption overlays.
 */
class CaptionLayoutEngineTest {

    private fun word(text: String, start: Float, end: Float, confidence: Float = 1f) =
        WordTimestamp(word = text, startSec = start, endSec = end, confidence = confidence)

    @Test
    fun emptyInputProducesNoLines() {
        assertTrue(CaptionLayoutEngine.buildLines(emptyList()).isEmpty())
    }

    @Test
    fun onlyInvalidWordsProduceNoLines() {
        val words = listOf(word("  ", 0f, 1f), word("late", 2f, 1f)) // blank + end < start
        assertTrue(CaptionLayoutEngine.buildLines(words).isEmpty())
    }

    @Test
    fun shortSentenceStaysOnOneLineInOrder() {
        val words = listOf(
            word("Hello", 0f, 0.5f),
            word("viral", 0.6f, 1.1f),
            word("world", 1.2f, 1.7f)
        )
        val lines = CaptionLayoutEngine.buildLines(words)
        assertEquals(1, lines.size)
        assertEquals("Hello viral world", lines.single().text)
        assertEquals(0f, lines.single().startSec, 0.001f)
        assertEquals(1.7f, lines.single().endSec, 0.001f)
    }

    @Test
    fun wordsAreSortedByStartTimeBeforeGrouping() {
        val words = listOf(
            word("second", 0.4f, 0.8f),
            word("first", 0f, 0.4f),
            word("third", 1.2f, 1.6f)
        )
        val lines = CaptionLayoutEngine.buildLines(words, maxCharacters = 60)
        assertEquals("first second third", lines.single().text)
        assertEquals(0f, lines.single().startSec, 0.001f)
        assertEquals(1.6f, lines.single().endSec, 0.001f)
    }

    @Test
    fun wordCountLimitSplitsLine() {
        val words = (1..10).map { word("w$it", it.toFloat(), it + 1f) }
        val lines = CaptionLayoutEngine.buildLines(words, maxCharacters = 200, maxWords = 3)
        assertTrue(lines.size >= 4)
        assertTrue(lines.all { it.words.size <= 3 })
        // No word is lost or reordered across lines.
        val flattened = lines.flatMap { it.words }.map { it.word }
        assertEquals((1..10).map { "w$it" }, flattened)
    }

    @Test
    fun characterLimitSplitsLongWordsAcrossLines() {
        val words = (1..6).map { word("word$it", it.toFloat(), it + 1f) }
        val lines = CaptionLayoutEngine.buildLines(words, maxCharacters = 12, maxWords = 7)
        assertTrue(lines.size > 1)
        assertTrue(lines.all { it.text.length <= 12 })
    }

    @Test
    fun sentenceEndingRespectsSemanticBoundary() {
        val words = listOf(
            word("This", 0f, 0.3f),
            word("works.", 0.4f, 0.8f),
            word("Next", 2.0f, 2.3f), // > 0.85s pause => boundary
            word("idea!", 2.4f, 2.8f)
        )
        val lines = CaptionLayoutEngine.buildLines(words, maxCharacters = 60)
        assertEquals(2, lines.size)
        assertEquals("This works.", lines[0].text)
        assertEquals("Next idea!", lines[1].text)
    }

    @Test
    fun arabicTextIsDetectedAsRtl() {
        assertTrue(CaptionLayoutEngine.isRtl("مرحباً بكم في قناة النجاح"))
        assertFalse(CaptionLayoutEngine.isRtl("Welcome to the channel"))
    }

    @Test
    fun karaokeModeHighlightsEveryWord() {
        val words = listOf(word("a", 0f, 1f), word("short", 1f, 2f), word("ok", 2f, 3f))
        val line = CaptionLayoutEngine.buildLines(words).single()
        assertEquals(setOf(0, 1, 2), CaptionLayoutEngine.highlightedWordIndexes(line, "karaoke"))
    }

    @Test
    fun noneModeNeverHighlights() {
        val words = listOf(word("focus", 0f, 1f), word("growth", 1f, 2f))
        val line = CaptionLayoutEngine.buildLines(words).single()
        assertTrue(CaptionLayoutEngine.highlightedWordIndexes(line, "none").isEmpty())
    }

    @Test
    fun keywordModePicksHighestConfidenceLongWords() {
        val words = listOf(
            word("focus", 0f, 1f, confidence = 0.4f),
            word("growth", 1f, 2f, confidence = 0.9f),
            word("win", 2f, 3f, confidence = 1f) // too short (< 4 chars) to qualify
        )
        val line = CaptionLayoutEngine.buildLines(words).single()
        val highlighted = CaptionLayoutEngine.highlightedWordIndexes(line, "keyword", maxHighlights = 1)
        assertEquals(setOf(1), highlighted)
    }

    @Test
    fun safeTextWidthHonorsSideZonesAndClamps() {
        val normal = CaptionLayoutEngine.safeTextWidth(0.9f)
        assertTrue(normal in 0.1f..1f)
        val withZones = CaptionLayoutEngine.safeTextWidth(1f, SafeZoneConfig(sideFraction = 0.2f))
        assertTrue(withZones < 1f)
        // Extremes stay inside the usable band.
        assertTrue(CaptionLayoutEngine.safeTextWidth(0.001f) >= 0.1f)
        assertTrue(CaptionLayoutEngine.safeTextWidth(10f) <= 1f)
    }
}
