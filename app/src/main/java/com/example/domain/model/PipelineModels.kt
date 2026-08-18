package com.example.domain.model

enum class PipelineStageStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class PipelineStageType(val titleAr: String, val titleEn: String, val weight: Float) {
    IMPORT("استيراد الفيديو والتحقق من الذاكرة", "Video Import & Memory Safety", 0.05f),
    AUDIO_EXTRACTION("استخراج مسار الصوت وتصفية الترددات", "Audio Extraction & Waveform", 0.10f),
    TRANSCRIPTION("تحويل الصوت إلى نص دقيق بكلمات موقوتة", "Word-Level Transcription", 0.15f),
    SILENCE_REMOVAL("كشف وحذف الصمت والكلمات الحشوية", "Silence & Filler Word Removal", 0.10f),
    SEMANTIC_ANALYSIS("التحليل الدلالي لمواضيع ونبرة المحتوى", "Semantic Theme & Sentiment Analysis", 0.15f),
    CLIP_DETECTION("اكتشاف المقاطع المرشحة ورسم حدودها", "Candidate Clip Boundary Detection", 0.15f),
    VIRALITY_SCORING("حساب مؤشر الانتشارية والتفسير المعياري", "Explainable Virality Multi-Factor Scoring", 0.10f),
    HOOK_GENERATION("توليد الخطافات والنسخ الجذابة", "Hook Crafting & Multi-Platform Copies", 0.05f),
    CAPTION_SYNTHESIS("توليد وتنسيق الكابشن الحركي الملون", "Dynamic Animated Caption Synthesis", 0.05f),
    SMART_REFRAMING("إعادة التأطير الذكي مع تتبع المتحدث (9:16)", "Smart Reframing & Speaker Tracking", 0.05f),
    RENDERING_EXPORT("تصيير الفيديو وحفظ الحزم الجاهزة", "Video Rendering & Preset Package Export", 0.05f)
}

data class PipelineStageProgress(
    val stage: PipelineStageType,
    val status: PipelineStageStatus = PipelineStageStatus.QUEUED,
    val progress: Float = 0f, // 0.0 to 1.0
    val message: String = "",
    val errorMessage: String? = null,
    val canRetry: Boolean = true
)

data class PipelineJob(
    val jobId: String = java.util.UUID.randomUUID().toString(),
    val projectId: Long,
    val currentStage: PipelineStageType = PipelineStageType.IMPORT,
    val overallStatus: PipelineStageStatus = PipelineStageStatus.QUEUED,
    val overallProgress: Float = 0f,
    val stages: Map<PipelineStageType, PipelineStageProgress> = PipelineStageType.values().associateWith { 
        PipelineStageProgress(stage = it) 
    },
    val errorDetails: String? = null,
    val isCancelled: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
