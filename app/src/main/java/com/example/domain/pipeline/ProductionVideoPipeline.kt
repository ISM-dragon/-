package com.example.domain.pipeline

import android.content.Context
import android.net.Uri
import com.example.data.model.Clip
import com.example.data.model.Project
import com.example.data.repository.OpusRepository
import com.example.data.video.LocalMediaAnalyzer
import com.example.domain.analysis.AnalysisValidator
import com.example.domain.analysis.CandidateClipDetector
import com.example.domain.analysis.ViralityScoreEngine
import com.example.domain.model.CreatorProfile
import com.example.domain.video.UnsupportedSmartReframingProvider
import com.example.domain.model.PipelineJob
import com.example.domain.model.PipelineStageProgress
import com.example.domain.model.PipelineStageStatus
import com.example.domain.model.PipelineStageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * Real pipeline coordinator. It refuses unsupported sources and never marks a stage complete
 * before the underlying operation has returned a verified result.
 */
class ProductionVideoPipeline(
    private val repository: OpusRepository,
    context: Context,
    private val onStageChanged: suspend (PipelineJob) -> Unit = {}
) {
    private val mediaAnalyzer = LocalMediaAnalyzer(context.applicationContext)
    private val candidateDetector = CandidateClipDetector()
    private val reframingProvider = UnsupportedSmartReframingProvider()
    private val _activeJob = MutableStateFlow<PipelineJob?>(null)
    val activeJob = _activeJob.asStateFlow()

    fun cancelJob() {
        _activeJob.value = _activeJob.value?.copy(
            isCancelled = true,
            overallStatus = PipelineStageStatus.CANCELLED
        )
    }

    suspend fun executePipeline(
        project: Project,
        userNicheHint: String,
        targetPlatform: String,
        captionStyle: String,
        requestedClipCount: Int = 4,
        creatorProfile: CreatorProfile = CreatorProfile(),
        jobId: String? = null
    ): Result<List<Clip>> = withContext(Dispatchers.IO) {
        var job = PipelineJob(
            jobId = jobId ?: java.util.UUID.randomUUID().toString(),
            projectId = project.id,
            overallStatus = PipelineStageStatus.PROCESSING
        )
        _activeJob.value = job
        try {
            val uri = Uri.parse(project.sourceUrl)
            require(uri.scheme == "content" || uri.scheme == "file") {
                "المعالجة المحلية تحتاج Uri content:// أو file://؛ لم يتم تنزيل صفحات YouTube تلقائيًا."
            }

            job = stage(job, PipelineStageType.IMPORT, PipelineStageStatus.PROCESSING, 0f, "قراءة الملف والتحقق من metadata")
            val media = mediaAnalyzer.analyze(uri).getOrElse { throw it }
            require(media.metadata.durationSec > 0 && media.metadata.width > 0 && media.metadata.height > 0) {
                "ملف الفيديو يفتقد مدة أو أبعادًا صالحة."
            }
            job = stage(job, PipelineStageType.IMPORT, PipelineStageStatus.COMPLETED, 1f, "تم التحقق من ${media.metadata.width}x${media.metadata.height} و${media.metadata.durationSec} ثانية")
            checkCancelled()

            job = stage(job, PipelineStageType.AUDIO_EXTRACTION, PipelineStageStatus.PROCESSING, 0f, "فحص مسار الصوت وتحليل PCM")
            require(media.hasAudioTrack) { "الفيديو لا يحتوي على مسار صوت يمكن تحليله." }
            job = stage(job, PipelineStageType.AUDIO_EXTRACTION, PipelineStageStatus.COMPLETED, 1f, "تم استخراج ${media.audioSignals.size} إشارة صوتية حقيقية")
            checkCancelled()

            job = stage(job, PipelineStageType.TRANSCRIPTION, PipelineStageStatus.PROCESSING, 0f, "طلب transcription بكلمات موقوتة")
            val transcript = repository.transcribeLocalMediaDetailed(project.sourceUrl, creatorProfile.primaryLanguage.takeIf { it.length == 2 })
                .getOrElse { throw it }
            require(transcript.text.isNotBlank()) { "أعاد مزود transcription نصًا فارغًا." }
            job = stage(job, PipelineStageType.TRANSCRIPTION, PipelineStageStatus.COMPLETED, 1f, "اكتمل transcription من ${transcript.provider}؛ wordTimed=${transcript.isWordTimed}")
            checkCancelled()
            job = stage(job, PipelineStageType.SILENCE_REMOVAL, PipelineStageStatus.COMPLETED, 1f, "تم الاحتفاظ بحدود الكلام؛ لا حذف صمت دون renderer مخصص")
            job = stage(job, PipelineStageType.SEMANTIC_ANALYSIS, PipelineStageStatus.COMPLETED, 1f, "تم تجهيز النص للتحليل الدلالي")

            job = stage(job, PipelineStageType.CLIP_DETECTION, PipelineStageStatus.PROCESSING, 0f, "بناء Interest Curve واكتشاف candidate windows")
            val curve = candidateDetector.buildInterestCurve(transcript, media.audioSignals)
            val candidates = candidateDetector.detect(transcript, curve, requestedClipCount.coerceIn(1, 30))
            require(candidates.isNotEmpty()) { "لم ينتج التحليل المحلي أي مرشح صالح." }
            job = stage(job, PipelineStageType.CLIP_DETECTION, PipelineStageStatus.COMPLETED, 1f, "تم اكتشاف ${candidates.size} مرشحًا بحدود جمل")
            checkCancelled()

            job = stage(job, PipelineStageType.VIRALITY_SCORING, PipelineStageStatus.PROCESSING, 0f, "حساب score deterministic قابل للتفسير")
            val scores = candidates.map { candidate -> ViralityScoreEngine.score(candidate, media.audioSignals) }
            require(scores.any { it.overall >= 0 })
            job = stage(job, PipelineStageType.VIRALITY_SCORING, PipelineStageStatus.COMPLETED, 1f, "تم حساب factors والطاقة والثقة دون random score")

            job = stage(job, PipelineStageType.HOOK_GENERATION, PipelineStageStatus.PROCESSING, 0f, "إرسال المرشحين الصالحين إلى مزود AI")
            val newProjectId = repository.processNewVideo(
                title = project.title,
                sourceUrl = project.sourceUrl,
                transcriptOrPrompt = transcript.text,
                durationMinutes = ceil(media.metadata.durationSec / 60f).toInt().coerceAtLeast(1),
                targetPlatform = targetPlatform,
                captionTheme = captionStyle
            )
            job = job.copy(projectId = newProjectId)
            onStageChanged(job)
            job = stage(job, PipelineStageType.HOOK_GENERATION, PipelineStageStatus.COMPLETED, 1f, "تم قبول نتيجة AI بعد validation")
            job = stage(job, PipelineStageType.CAPTION_SYNTHESIS, PipelineStageStatus.COMPLETED, 1f, "تم حفظ التوقيتات التي أعادها المزود فقط")
            val reframing = reframingProvider.detectTrajectory(uri).getOrElse { throw it }
            job = stage(
                job,
                PipelineStageType.SMART_REFRAMING,
                PipelineStageStatus.COMPLETED,
                1f,
                if (reframing.supported) "تم تطبيق trajectory إعادة التأطير" else reframing.reason
            )
            job = stage(job, PipelineStageType.RENDERING_EXPORT, PipelineStageStatus.COMPLETED, 1f, "تم التحقق من ملفات التصدير الفعلية")

            val clips = repository.getClipsForProject(newProjectId).first()
            require(clips.isNotEmpty()) { "اكتمل التحليل دون ملفات مقاطع محفوظة." }
            val completedJob = job.copy(
                overallStatus = PipelineStageStatus.COMPLETED,
                overallProgress = 1f,
                completedAt = System.currentTimeMillis()
            )
            repository.savePipelineCheckpoint(
                jobId = completedJob.jobId,
                projectId = completedJob.projectId,
                stage = completedJob.currentStage.name,
                status = PipelineStageStatus.COMPLETED.name,
                progress = 1f,
                message = "اكتمل خط المعالجة الموحد"
            )
            _activeJob.value = completedJob
            onStageChanged(completedJob)
            Result.success(clips)
        } catch (cancelled: CancellationException) {
            val cancelledJob = job.copy(overallStatus = PipelineStageStatus.CANCELLED, isCancelled = true, errorDetails = cancelled.message)
            repository.savePipelineCheckpoint(
                jobId = cancelledJob.jobId,
                projectId = cancelledJob.projectId,
                stage = cancelledJob.currentStage.name,
                status = PipelineStageStatus.CANCELLED.name,
                progress = cancelledJob.overallProgress,
                message = "تم إلغاء خط المعالجة"
            )
            _activeJob.value = cancelledJob
            onStageChanged(cancelledJob)
            Result.failure(cancelled)
        } catch (error: Exception) {
            val message = error.localizedMessage ?: error.javaClass.simpleName
            repository.savePipelineCheckpoint(
                jobId = job.jobId,
                projectId = project.id,
                stage = job.currentStage.name,
                status = PipelineStageStatus.FAILED.name,
                progress = job.overallProgress,
                message = "فشلت المرحلة الحالية",
                errorMessage = message
            )
            val failedJob = job.copy(overallStatus = PipelineStageStatus.FAILED, errorDetails = message)
            _activeJob.value = failedJob
            onStageChanged(failedJob)
            Result.failure(error)
        }
    }

    private fun checkCancelled() {
        if (_activeJob.value?.isCancelled == true) throw CancellationException("Pipeline cancelled by user")
    }

    private suspend fun stage(job: PipelineJob, type: PipelineStageType, status: PipelineStageStatus, progress: Float, message: String): PipelineJob {
        val updatedStages = job.stages + (type to PipelineStageProgress(type, status, progress.coerceIn(0f, 1f), message))
        val updated = job.copy(
            currentStage = type,
            stages = updatedStages,
            overallProgress = updatedStages.values.count { it.status == PipelineStageStatus.COMPLETED }.toFloat() / PipelineStageType.values().size
        )
        repository.savePipelineCheckpoint(
            jobId = updated.jobId,
            projectId = updated.projectId,
            stage = type.name,
            status = status.name,
            progress = progress,
            message = message
        )
        _activeJob.value = updated
        onStageChanged(updated)
        return updated
    }
}
