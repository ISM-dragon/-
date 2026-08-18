package com.example.domain.pipeline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.worker.VideoProcessingWorker
import java.util.concurrent.TimeUnit

/**
 * Compatibility alias for callers that used the original PipelineWork worker.
 * There is now one production worker: [VideoProcessingWorker].
 */
typealias VideoPipelineWorker = VideoProcessingWorker

/**
 * Legacy scheduler facade. New code should prefer OpusRepository.enqueueVideoProcessing,
 * which persists the ProcessingJobEntity before enqueueing the same worker.
 */
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
    ): Operation {
        require(uniqueName.isNotBlank()) { "اسم المهمة مطلوب." }
        require(title.isNotBlank()) { "عنوان الفيديو مطلوب." }
        require(sourceUrl.isNotBlank()) { "مصدر الفيديو مطلوب." }
        require(durationMinutes > 0) { "مدة الفيديو غير صالحة." }

        val input = workDataOf(
            VideoProcessingWorker.KEY_TITLE to title,
            VideoProcessingWorker.KEY_SOURCE_URI to sourceUrl,
            VideoProcessingWorker.KEY_TRANSCRIPT to transcript,
            VideoProcessingWorker.KEY_DURATION_MINUTES to durationMinutes,
            VideoProcessingWorker.KEY_TARGET_PLATFORM to targetPlatform,
            VideoProcessingWorker.KEY_CAPTION_THEME to captionTheme
        )
        val request = OneTimeWorkRequestBuilder<VideoProcessingWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("opus_video_processing")
            .build()
        return WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context, uniqueName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName)
    }
}
