package com.example.domain.analysis

/** Built-in caption styles are data so the renderer can remain generic and user styles can be persisted later. */
object CaptionStyleCatalog {
    val builtIn: List<CaptionStylePreset> = listOf(
        CaptionStylePreset(
            id = "minimal",
            name = "Minimal",
            fontFamily = "sans-serif",
            sizeSp = 14,
            weight = 500,
            colorArgb = 0xFFFFFFFF,
            outlineArgb = 0xCC000000,
            shadowArgb = 0x99000000,
            position = "bottom_safe",
            animation = "none",
            highlightStyle = "none",
            rtlMode = "auto"
        ),
        CaptionStylePreset(
            id = "dynamic",
            name = "Dynamic",
            fontFamily = "sans-serif-condensed",
            sizeSp = 16,
            weight = 700,
            colorArgb = 0xFFFFFFFF,
            outlineArgb = 0xFF101018,
            shadowArgb = 0xCC000000,
            position = "bottom_safe",
            animation = "word_highlight",
            highlightStyle = "keyword",
            rtlMode = "auto"
        ),
        CaptionStylePreset(
            id = "arabic_bold",
            name = "Arabic Bold",
            fontFamily = "sans-serif",
            sizeSp = 17,
            weight = 800,
            colorArgb = 0xFFFFFFFF,
            outlineArgb = 0xFF101018,
            shadowArgb = 0xCC000000,
            position = "bottom_safe",
            animation = "word_highlight",
            highlightStyle = "keyword",
            rtlMode = "rtl"
        ),
        CaptionStylePreset(
            id = "podcast",
            name = "Podcast",
            fontFamily = "sans-serif",
            sizeSp = 15,
            weight = 650,
            colorArgb = 0xFFFFFFFF,
            outlineArgb = 0xFF29115E,
            shadowArgb = 0xCC000000,
            position = "center_safe",
            animation = "phrase_fade",
            highlightStyle = "none",
            rtlMode = "auto"
        )
    )

    fun find(id: String): CaptionStylePreset =
        builtIn.firstOrNull { it.id == id || it.name.equals(id, ignoreCase = true) }
            ?: builtIn.first()
}
