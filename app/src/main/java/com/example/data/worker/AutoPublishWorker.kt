package com.example.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.data.repository.OpusRepository
import kotlinx.coroutines.CancellationException

class AutoPublishWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(PROJECT_ID_KEY, -1L)
        if (projectId <= 0L) return Result.failure(error("Missing project id"))

        return try {
            val result = OpusRepository(applicationContext)
                .dispatchAutoPublishForNewProject(projectId, applicationContext)
            when {
                result == null -> Result.success()
                result.isSuccess -> Result.success()
                runAttemptCount < MAX_RETRIES -> Result.retry()
                else -> Result.failure(error(result.message))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(error(error.message ?: "Auto publish failed"))
        }
    }

    private fun error(message: String): Data = Data.Builder()
        .putString(ERROR_KEY, message)
        .build()

    companion object {
        const val PROJECT_ID_KEY = "project_id"
        const val ERROR_KEY = "publish_error"
        private const val MAX_RETRIES = 2
    }
}
