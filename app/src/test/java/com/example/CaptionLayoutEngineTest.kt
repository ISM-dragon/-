package com.example

import com.example.domain.analysis.CaptionLayoutEngine
import com.example.domain.analysis.CaptionStyleCatalog
import com.example.domain.analysis.WordTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionLayoutEngineTest {
    @Test
    fun arabicAndMixedTextAreDetectedWithoutChangingLogicalOrder() {
        assertTrue(CaptionLayoutEngine.isRtl("الذكاء الاصطناعي غيّر كل شيء"))
        assertFalse(CaptionLayoutEngine.isRtl("AI changes everything"))
        assertTrue(CaptionLayoutEngine.isRtl("AI في المغرب"))
    }

    @Test
    fun linesRespectWordAndCharacterBoundaries() {
        val words = listOf(
            WordTimestamp("هذا", 0f, 0.3f),
            WordTimestamp("اختبار", 0.3f, 0.7f),
            WordTimestamp("للكابتشن", 0.7f, 1.2f),
            WordTimestamp("العربي", 1.2f, 1.6f),
            WordTimestamp("المقروء", 2.7f, 3.2f)
        )
        val lines = CaptionLayoutEngine.buildLines(
            words = words,
            style = CaptionStyleCatalog.find("arabic_bold"),
            maxCharacters = 18,
            maxWords = 4
        )

        assertEquals("arabic_bold", lines.first().styleId)
        assertTrue(lines.size >= 2)
        assertTrue(lines.all { it.text.isNotBlank() && it.words.size <= 4 })
        assertTrue(lines.zipWithNext().all { (left, right) -> right.startSec >= left.endSec })
        assertFalse(lines.any { it.text.contains("  ") })
    }

    @Test
    fun keywordAndKaraokeModesProduceDifferentHighlights() {
        val line = CaptionLayoutEngine.buildLines(
            listOf(
                WordTimestamp("The", 0f, 0.2f),
                WordTimestamp("biggest", 0.2f, 0.5f, 0.95f),
                WordTimestamp("misconception", 0.5f, 1f, 0.9f)
            )
        ).single()

        val keyword = CaptionLayoutEngine.highlightedWordIndexes(line, "keyword", maxHighlights = 1)
        val karaoke = CaptionLayoutEngine.highlightedWordIndexes(line, "karaoke")
        assertEquals(1, keyword.size)
        assertEquals(line.words.size, karaoke.size)
    }
}
