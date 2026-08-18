package com.example.data.video

import com.example.domain.analysis.WordTimestamp
import java.io.File
import java.util.Locale

object CaptionSidecarWriter {
    fun writeWebVtt(
        outputVideo: File,
        words: List<WordTimestamp>,
        keywords: List<String>
    ): File? {
        val valid = words.filter { it.word.isNotBlank() && it.startSec >= 0f && it.endSec > it.startSec }
        if (valid.isEmpty()) return null
        val output = File(outputVideo.parentFile ?: outputVideo.absoluteFile.parentFile!!, outputVideo.nameWithoutExtension + ".vtt")
        output.writeText(buildString {
            appendLine("WEBVTT")
            appendLine()
            valid.forEachIndexed { index, word ->
                val highlight = keywords.any { it.equals(word.word.trim(), ignoreCase = true) }
                appendLine((index + 1).toString())
                appendLine("${format(word.startSec)} --> ${format(word.endSec)}")
                appendLine(if (highlight) "<b>${escape(word.word)}</b>" else escape(word.word))
                appendLine()
            }
        }, Charsets.UTF_8)
        return output
    }

    private fun format(seconds: Float): String {
        val totalMs = (seconds.coerceAtLeast(0f) * 1_000f).toLong()
        val hours = totalMs / 3_600_000
        val minutes = (totalMs % 3_600_000) / 60_000
        val secs = (totalMs % 60_000) / 1_000
        val millis = totalMs % 1_000
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, secs, millis)
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
