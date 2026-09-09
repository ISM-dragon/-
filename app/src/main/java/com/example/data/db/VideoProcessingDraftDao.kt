package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.VideoProcessingDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoProcessingDraftDao {
    @Query("SELECT * FROM video_processing_drafts WHERE isUnfinished = 1 ORDER BY lastUpdated DESC")
    fun getAllUnfinishedDrafts(): Flow<List<VideoProcessingDraftEntity>>

    @Query("SELECT * FROM video_processing_drafts WHERE isUnfinished = 1 ORDER BY lastUpdated DESC LIMIT 1")
    fun getLatestUnfinishedDraft(): Flow<VideoProcessingDraftEntity?>

    @Query("SELECT * FROM video_processing_drafts WHERE isUnfinished = 1 ORDER BY lastUpdated DESC LIMIT 1")
    suspend fun getLatestUnfinishedDraftSync(): VideoProcessingDraftEntity?

    @Query("SELECT * FROM video_processing_drafts WHERE id = :id")
    suspend fun getDraftById(id: Long): VideoProcessingDraftEntity?

    @Query("SELECT * FROM video_processing_drafts WHERE jobId = :jobId LIMIT 1")
    suspend fun getDraftByJobIdSync(jobId: String): VideoProcessingDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDraft(draft: VideoProcessingDraftEntity): Long

    @Update
    suspend fun updateDraft(draft: VideoProcessingDraftEntity)

    @Query("UPDATE video_processing_drafts SET isUnfinished = 0 WHERE id = :id")
    suspend fun markDraftAsFinished(id: Long)

    @Query("UPDATE video_processing_drafts SET isUnfinished = 0, lastUpdated = :updatedAt WHERE jobId = :jobId")
    suspend fun markDraftAsFinishedByJobId(jobId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM video_processing_drafts WHERE id = :id")
    suspend fun deleteDraftById(id: Long)

    @Query("DELETE FROM video_processing_drafts WHERE isUnfinished = 0")
    suspend fun clearFinishedDrafts()

    @Query("DELETE FROM video_processing_drafts")
    suspend fun clearAllDrafts()
}
