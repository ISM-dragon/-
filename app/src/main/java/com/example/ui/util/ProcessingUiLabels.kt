package com.example.ui.util

/**
 * Shared Arabic labels for processing stages so every surface (Projects,
 * Usage Dashboard, Upload, Home, notifications-adjacent cards) shows the same
 * human-readable text instead of raw internal stage names such as
 * VALIDATING or SOURCE_VALIDATION.
 */
object ProcessingUiLabels {

    private val EXACT_LABELS = mapOf(
        "QUEUED" to "في الطابور",
        "PENDING" to "في الطابور",
        "VALIDATING" to "فحص الملف",
        "UPLOADING" to "رفع الفيديو إلى Gateway",
        "UPLOADED" to "اكتمل الرفع",
        "RUNNING" to "قيد التنفيذ",
        "PROCESSING" to "قيد التنفيذ",
        "COMPLETED" to "اكتملت",
        "SUCCEEDED" to "اكتملت",
        "SUCCESS" to "اكتملت",
        "FAILED" to "فشلت",
        "ERROR" to "فشلت",
        "CANCELLED" to "ملغاة",
        "CANCELED" to "ملغاة",
        "RETRY_WAIT" to "إعادة محاولة…",
        "IDLE" to "جاهز",
        "READY" to "جاهز",
        "SOURCE_VALIDATION" to "فحص مصدر الفيديو",
        "AUDIO_EXTRACTION" to "استخراج الصوت",
        "HOOK_SCANNING" to "كشف الهوك",
        "VIRALITY_SCORING" to "حساب درجات الانتشار",
        "CAPTION_RENDERING" to "تنسيق الترجمة"
    )

    /** Substring rules cover the remaining pipeline stage enum names safely. */
    private val SUBSTRING_RULES = listOf(
        "TRANSCRI" to "نسخ الكلام",
        "AUDIO" to "معالجة الصوت",
        "DIARIZ" to "فصل المتحدثين",
        "CAMERA" to "تحليل الكاميرا",
        "TRACK" to "تتبّع الوجوه",
        "DETECT" to "كشف العناصر",
        "EVENT" to "تحليل الأحداث",
        "PANNS" to "تحليل الأحداث",
        "CANDIDATE" to "اكتشاف المرشحين",
        "WINDOW" to "اكتشاف المرشحين",
        "CURVE" to "منحنى الانتشار",
        "HOOK" to "كشف الهوك",
        "SCORE" to "حساب الدرجات",
        "CAPTION" to "توليد الترجمة",
        "STYLE" to "تنسيق الترجمة",
        "RENDER" to "ترميز وتصدير",
        "EXPORT" to "ترميز وتصدير",
        "DOWNLOAD" to "تنزيل النتائج",
        "MERGE" to "دمج المقاطع"
    )

    fun stage(stage: String, fallback: String = ""): String {
        val normalized = stage.trim()
        if (normalized.isBlank()) {
            return fallback.takeIf { it.isNotBlank() } ?: "…"
        }
        val upper = normalized.uppercase()
        EXACT_LABELS[upper]?.let { return it }
        for ((key, label) in SUBSTRING_RULES) {
            if (upper.contains(key)) return label
        }
        return fallback.takeIf { it.isNotBlank() } ?: normalized.replace('_', ' ')
    }
}
