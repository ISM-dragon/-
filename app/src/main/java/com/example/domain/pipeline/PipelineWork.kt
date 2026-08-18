package com.example.domain.pipeline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.model.Project
import com.example.data.repository.OpusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit

class VideoPipelineWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val sourceUrl = inputData.getString(KEY_SOURCE_URL).orEmpty()
        val transcript = inputData.getString(KEY_TRANSCRIPT).orEmpty()
        val durationMinutes = inputData.getInt(KEY_DURATION_MINUTES, 1)
        val targetPlatform = inputData.getString(KEY_TARGET_PLATFORM).orEmpty()
        val captionTheme = inputData.getString(KEY_CAPTION_THEME).orEmpty()
        if (sourceUrl.isBlank()) return Result.failure(errorData("sourceUrl", "مصدر الفيديو فارغ."))

        val repository = OpusRepository(applicationContext)
        val pipeline = ProductionVideoPipeline(repository, applicationContext)
        val project = Project(
            title = title,
            sourceUrl = sourceUrl,
            sourceDurationSec = durationMinutes * 60,
            targetPlatform = targetPlatform,
            captionTheme = captionTheme,
            status = "QUEUED"
        )
        return try {
            coroutineScope {
                val progressJob: Job = launch {
                    pipeline.activeJob.filterNotNull().collectLatest { job ->
                        setProgress(
                            Data.Builder()
                                .putInt("stage", job.currentStage.ordinal)
                                .putFloat("progress", job.overallProgress)
                                .putString("message", job.stages[job.currentStage]?.message.orEmpty())
                                .build()
                        )
                    }
                }
                try {
                    val result = pipeline.executePipeline(
                        project = project,
                        userNicheHint = "",
                        targetPlatform = targetPlatform,
                        captionStyle = captionTheme
                    )
                    if (result.isFailure) throw result.exceptionOrNull() ?: IllegalStateException("Pipeline failed")
                } finally {
                    progressJob.cancel()
                }
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = error.localizedMessage ?: error.javaClass.simpleName
            if (runAttemptCount < 2 && isRetryable(error)) Result.retry()
            else Result.failure(errorData("error", message))
        }
    }

    private fun isRetryable(error: Exception): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("timeout") || message.contains("network") || message.contains("http 5")
    }

    private fun errorData(key: String, value: String): Data = Data.Builder().putString(key, value).build()

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_TRANSCRIPT = "transcript"
        const val KEY_DURATION_MINUTES = "duration_minutes"
        const val KEY_TARGET_PLATFORM = "target_platform"
        const val KEY_CAPTION_THEME = "caption_theme"
    }
}

object PipelineWorkScheduler {
    fun enqueue(
        context: Context,
        uniqueName: String,
        title: String,
        sourceUrl: String,
        transcript: String,
        durationMinutes: Int,
        targetPlatform: String,
        captionTheme: String
    ): androidx.work.Operation {
        val input = Data.Builder()
            .putString(VideoPipelineWorker.KEY_TITLE, title)
            .putString(VideoPipelineWorker.KEY_SOURCE_URL, sourceUrl)
            .putString(VideoPipelineWorker.KEY_TRANSCRIPT, transcript)
            .putInt(VideoPipelineWorker.KEY_DURATION_MINUTES, durationMinutes)
            .putString(VideoPipelineWorker.KEY_TARGET_PLATFORM, targetPlatform)
            .putString(VideoPipelineWorker.KEY_CAPTION_THEME, captionTheme)
            .build()
        val request = OneTimeWorkRequestBuilder<VideoPipelineWorker>()
            .setInputData(input)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("opus-video-pipeline")
            .build()
        return WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context, uniqueName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName)
    }
}
