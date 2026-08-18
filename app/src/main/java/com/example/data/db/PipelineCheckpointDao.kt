package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.PipelineCheckpointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineCheckpointDao {
    @Query("SELECT * FROM pipeline_checkpoints WHERE jobId = :jobId ORDER BY updatedAt ASC")
    fun observeJob(jobId: String): Flow<List<PipelineCheckpointEntity>>

    @Query("SELECT * FROM pipeline_checkpoints WHERE jobId = :jobId AND stage = :stage LIMIT 1")
    suspend fun getStage(jobId: String, stage: String): PipelineCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: PipelineCheckpointEntity)

    @Query("DELETE FROM pipeline_checkpoints WHERE jobId = :jobId")
    suspend fun deleteJob(jobId: String)
}
