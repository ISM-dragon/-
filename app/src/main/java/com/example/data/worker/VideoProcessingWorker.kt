package com.example.data.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.db.OpusDatabase
import com.example.data.model.ProcessingJobEntity
import com.example.data.model.Project
import com.example.data.repository.OpusRepository
import com.example.data.video.MediaUriStabilizer
import com.example.domain.model.PipelineJob
import com.example.domain.model.PipelineStageStatus
import com.example.domain.pipeline.ProductionVideoPipeline
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import kotlin.math.roundToInt
import java.util.UUID

class VideoProcessingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val database = OpusDatabase.getDatabase(appContext)
    private val jobs = database.processingJobDao()

    override suspend fun doWork(): Result {
        var jobId = inputData.getString(KEY_JOB_ID).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val sourceUri = inputData.getString(KEY_SOURCE_URI).orEmpty()
        val transcriptOrPrompt = inputData.getString(KEY_TRANSCRIPT).orEmpty()
        val durationMinutes = inputData.getInt(KEY_DURATION_MINUTES, 0)
        val targetPlatform = inputData.getString(KEY_TARGET_PLATFORM).orEmpty()
        val captionTheme = inputData.getString(KEY_CAPTION_THEME).orEmpty()

        if (jobId.isBlank() && sourceUri.isNotBlank() && title.isNotBlank() && durationMinutes > 0) {
            jobId = UUID.randomUUID().toString()
            jobs.upsert(
                ProcessingJobEntity(
                    jobId = jobId,
                    title = title,
                    sourceUri = sourceUri,
                    transcriptOrPrompt = transcriptOrPrompt,
                    durationMinutes = durationMinutes,
                    targetPlatform = targetPlatform,
                    captionTheme = captionTheme
                )
            )
        }
        if (jobId.isBlank() || sourceUri.isBlank() || title.isBlank() || durationMinutes <= 0) {
            return Result.failure(workDataOf(KEY_ERROR to "بيانات مهمة المعالجة غير مكتملة."))
        }
        val parsedSource = runCatching { Uri.parse(sourceUri) }.getOrNull()
        if (parsedSource?.scheme !in setOf("content", "file")) {
            return Result.failure(workDataOf(KEY_JOB_ID to jobId, KEY_ERROR to "مصدر الفيديو غير صالح أو غير محلي."))
        }
        val existingJob = jobs.get(jobId)
        if (existingJob?.status == ProcessingJobEntity.STATUS_SUCCEEDED) {
            return Result.success(workDataOf(KEY_JOB_ID to jobId, KEY_PROJECT_ID to existingJob.outputProjectId))
        }

        val attempt = runAttemptCount + 1
        jobs.updateState(
            jobId = jobId,
            status = ProcessingJobEntity.STATUS_RUNNING,
            progress = 5,
            stage = "VALIDATING",
            errorMessage = ""
        )
        setProgress(workDataOf(KEY_JOB_ID to jobId, KEY_PROGRESS to 5, KEY_STAGE to "VALIDATING"))

        return try {
            val repository = OpusRepository(applicationContext)
            val project = Project(
                title = title,
                sourceUrl = sourceUri,
                sourceDurationSec = durationMinutes * 60,
                targetPlatform = targetPlatform,
                captionTheme = captionTheme,
                status = "QUEUED"
            )
            val pipeline = ProductionVideoPipeline(
                repository = repository,
                context = applicationContext,
                onStageChanged = { pipelineJob -> syncPipelineState(jobId, pipelineJob) }
            )
            val result = pipeline.executePipeline(
                project = project,
                userNicheHint = "",
                targetPlatform = targetPlatform,
                captionStyle = captionTheme,
                jobId = jobId,
                transcriptOrPrompt = transcriptOrPrompt
            )
            if (result.isSuccess) {
                val projectId = result.getOrNull()?.firstOrNull()?.projectId ?: 0L
                require(projectId > 0L) { "اكتملت المعالجة دون مشروع محفوظ صالح." }
                jobs.updateState(
                    jobId = jobId,
                    status = ProcessingJobEntity.STATUS_SUCCEEDED,
                    progress = 100,
                    stage = "COMPLETED",
                    outputProjectId = projectId
                )
                ProcessingNotification.show(
                    applicationContext,
                    jobId,
                    "اكتملت معالجة ISM",
                    "تم إنشاء المقاطع وحفظ المشروع رقم $projectId.",
                    success = true
                )
                setProgress(workDataOf(KEY_JOB_ID to jobId, KEY_PROGRESS to 100, KEY_STAGE to "COMPLETED", KEY_PROJECT_ID to projectId))
                MediaUriStabilizer.deleteManagedCopy(applicationContext, sourceUri)
                Result.success(workDataOf(KEY_JOB_ID to jobId, KEY_PROJECT_ID to projectId))
            } else {
                throw result.exceptionOrNull() ?: IllegalStateException("فشل خط المعالجة الموحد")
            }
        } catch (cancelled: CancellationException) {
            jobs.updateState(
                jobId = jobId,
                status = ProcessingJobEntity.STATUS_CANCELLED,
                progress = 0,
                stage = "CANCELLED",
                errorMessage = "تم إلغاء المعالجة."
            )
            ProcessingNotification.show(
                applicationContext,
                jobId,
                "تم إلغاء معالجة ISM",
                "ألغى المستخدم مهمة معالجة الفيديو.",
                success = false
            )
            MediaUriStabilizer.deleteManagedCopy(applicationContext, sourceUri)
            throw cancelled
        } catch (error: Exception) {
            val message = error.localizedMessage?.takeIf { it.isNotBlank() } ?: "فشلت معالجة الفيديو."
            if (runAttemptCount < 2 && isRetryable(error)) {
                jobs.updateState(
                    jobId = jobId,
                    status = ProcessingJobEntity.STATUS_QUEUED,
                    progress = 0,
                    stage = "RETRY_WAIT",
                    errorMessage = String.format(Locale.ROOT, "إعادة المحاولة %d: %s", attempt, message)
                )
                Result.retry()
            } else {
                jobs.updateState(
                    jobId = jobId,
                    status = ProcessingJobEntity.STATUS_FAILED,
                    progress = 0,
                    stage = "FAILED",
                    errorMessage = String.format(Locale.ROOT, "المحاولة %d: %s", attempt, message)
                )
                ProcessingNotification.show(
                    applicationContext,
                    jobId,
                    "فشلت معالجة ISM",
                    message,
                    success = false
                )
                MediaUriStabilizer.deleteManagedCopy(applicationContext, sourceUri)
                Result.failure(workDataOf(KEY_JOB_ID to jobId, KEY_ERROR to message))
            }
        }
    }

    private suspend fun syncPipelineState(jobId: String, pipelineJob: PipelineJob) {
        val stage = pipelineJob.currentStage
        val stageProgress = pipelineJob.stages[stage]
        val status = when (pipelineJob.overallStatus) {
            PipelineStageStatus.COMPLETED -> ProcessingJobEntity.STATUS_SUCCEEDED
            PipelineStageStatus.FAILED -> ProcessingJobEntity.STATUS_FAILED
            PipelineStageStatus.CANCELLED -> ProcessingJobEntity.STATUS_CANCELLED
            else -> ProcessingJobEntity.STATUS_RUNNING
        }
        val safeOverallProgress = pipelineJob.overallProgress.takeIf { it.isFinite() } ?: 0f
        val progress = (safeOverallProgress.coerceIn(0f, 1f) * 100f).roundToInt()
        val message = stageProgress?.message.orEmpty().ifBlank { stage.titleEn }
        jobs.updateState(
            jobId = jobId,
            status = status,
            progress = progress,
            stage = stage.name,
            errorMessage = pipelineJob.errorDetails ?: stageProgress?.errorMessage.orEmpty(),
            outputProjectId = pipelineJob.projectId
        )
        setProgress(
            workDataOf(
                KEY_JOB_ID to jobId,
                KEY_PROGRESS to progress,
                KEY_STAGE to stage.name,
                KEY_MESSAGE to message,
                KEY_PROJECT_ID to pipelineJob.projectId
            )
        )
    }

    private fun isRetryable(error: Exception): Boolean {
        if (error is SocketTimeoutException || error is IOException) return true
        val message = error.message.orEmpty().lowercase(Locale.ROOT)
        return message.contains("timeout") ||
            message.contains("network") ||
            message.contains("http 5") ||
            message.contains("http 429") ||
            message.contains("temporarily") ||
            message.contains("اتصال")
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_TITLE = "title"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_TRANSCRIPT = "transcript_or_prompt"
        const val KEY_DURATION_MINUTES = "duration_minutes"
        const val KEY_TARGET_PLATFORM = "target_platform"
        const val KEY_CAPTION_THEME = "caption_theme"
        const val KEY_PROGRESS = "progress"
        const val KEY_STAGE = "stage"
        const val KEY_MESSAGE = "message"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_ERROR = "error"
    }
}
