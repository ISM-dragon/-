package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ProcessingJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessingJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: ProcessingJobEntity)

    @Query("SELECT * FROM processing_jobs WHERE jobId = :jobId LIMIT 1")
    fun observe(jobId: String): Flow<ProcessingJobEntity?>

    @Query("SELECT * FROM processing_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun get(jobId: String): ProcessingJobEntity?

    @Query("SELECT * FROM processing_jobs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProcessingJobEntity>>

    @Query("UPDATE processing_jobs SET status = :status, progress = :progress, currentStage = :stage, errorMessage = :errorMessage, outputProjectId = :outputProjectId, updatedAt = :updatedAt WHERE jobId = :jobId")
    suspend fun updateState(
        jobId: String,
        status: String,
        progress: Int,
        stage: String,
        errorMessage: String = "",
        outputProjectId: Long = 0L,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM processing_jobs WHERE updatedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int
}
