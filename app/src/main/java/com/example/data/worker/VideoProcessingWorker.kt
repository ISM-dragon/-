package com.example.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.db.OpusDatabase
import com.example.data.model.ProcessingJobEntity
import com.example.data.repository.OpusRepository
import kotlinx.coroutines.CancellationException
import java.util.Locale

class VideoProcessingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val database = OpusDatabase.getDatabase(appContext)
    private val jobs = database.processingJobDao()

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val sourceUri = inputData.getString(KEY_SOURCE_URI).orEmpty()
        val transcriptOrPrompt = inputData.getString(KEY_TRANSCRIPT).orEmpty()
        val durationMinutes = inputData.getInt(KEY_DURATION_MINUTES, 0)
        val targetPlatform = inputData.getString(KEY_TARGET_PLATFORM).orEmpty()
        val captionTheme = inputData.getString(KEY_CAPTION_THEME).orEmpty()

        if (jobId.isBlank() || sourceUri.isBlank() || title.isBlank() || durationMinutes <= 0) {
            return Result.failure(workDataOf(KEY_ERROR to "بيانات مهمة المعالجة غير مكتملة."))
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
            jobs.updateState(jobId, ProcessingJobEntity.STATUS_RUNNING, 15, "ANALYZING")
            setProgress(workDataOf(KEY_JOB_ID to jobId, KEY_PROGRESS to 15, KEY_STAGE to "ANALYZING"))

            val projectId = repository.processNewVideo(
                title = title,
                sourceUrl = sourceUri,
                transcriptOrPrompt = transcriptOrPrompt,
                durationMinutes = durationMinutes,
                targetPlatform = targetPlatform,
                captionTheme = captionTheme
            )

            jobs.updateState(
                jobId = jobId,
                status = ProcessingJobEntity.STATUS_SUCCEEDED,
                progress = 100,
                stage = "COMPLETED",
                outputProjectId = projectId
            )
            setProgress(workDataOf(KEY_JOB_ID to jobId, KEY_PROGRESS to 100, KEY_STAGE to "COMPLETED", KEY_PROJECT_ID to projectId))
            Result.success(workDataOf(KEY_JOB_ID to jobId, KEY_PROJECT_ID to projectId))
        } catch (cancelled: CancellationException) {
            jobs.updateState(
                jobId = jobId,
                status = ProcessingJobEntity.STATUS_CANCELLED,
                progress = 0,
                stage = "CANCELLED",
                errorMessage = "تم إلغاء المعالجة."
            )
            throw cancelled
        } catch (error: Exception) {
            val message = error.localizedMessage?.takeIf { it.isNotBlank() } ?: "فشلت معالجة الفيديو."
            jobs.updateState(
                jobId = jobId,
                status = ProcessingJobEntity.STATUS_FAILED,
                progress = 0,
                stage = "FAILED",
                errorMessage = String.format(Locale.ROOT, "المحاولة %d: %s", attempt, message)
            )
            Result.failure(workDataOf(KEY_JOB_ID to jobId, KEY_ERROR to message))
        }
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
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_ERROR = "error"
    }
}
