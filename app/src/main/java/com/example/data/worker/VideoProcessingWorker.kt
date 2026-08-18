package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.data.repository.OpusRepository
import kotlinx.coroutines.CancellationException
import java.io.IOException

class VideoProcessingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val requestId = inputData.getString(REQUEST_ID_KEY)
            ?: return failure("Missing processing request id")
        val repository = OpusRepository(applicationContext)
        val request = repository.getProcessingRequest(requestId)
            ?: return failure("Processing request was not found")

        return try {
            val projectId = repository.processNewVideoInternal(
                title = request.title,
                sourceUrl = request.sourceUrl,
                transcriptOrPrompt = request.transcriptOrPrompt,
                durationMinutes = request.durationMinutes,
                targetPlatform = request.targetPlatform,
                captionTheme = request.captionTheme,
                layoutType = request.layoutType
            )
            repository.deleteProcessingRequest(requestId)
            Result.success(Data.Builder().putLong(PROJECT_ID_KEY, projectId).build())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (temporary: IOException) {
            Log.w(TAG, "Temporary processing failure; retrying", temporary)
            Result.retry()
        } catch (error: Exception) {
            Log.e(TAG, "Video processing failed", error)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else failure(error.message ?: "Video processing failed")
        }
    }

    private fun failure(message: String): Result = Result.failure(
        Data.Builder().putString(ERROR_KEY, message).build()
    )

    companion object {
        const val REQUEST_ID_KEY = "processing_request_id"
        const val PROJECT_ID_KEY = "project_id"
        const val ERROR_KEY = "processing_error"
        private const val MAX_RETRIES = 2
        private const val TAG = "VideoProcessingWorker"
    }
}
