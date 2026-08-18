package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ViralScoreMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ViralScoreMetricDao {

    @Query("SELECT * FROM viral_score_metrics WHERE projectId = :projectId ORDER BY overallViralityScore DESC")
    fun getScoresForProject(projectId: Long): Flow<List<ViralScoreMetricEntity>>

    @Query("SELECT * FROM viral_score_metrics WHERE projectId = :projectId ORDER BY overallViralityScore DESC")
    suspend fun getScoresForProjectSync(projectId: Long): List<ViralScoreMetricEntity>

    @Query("SELECT * FROM viral_score_metrics WHERE clipId = :clipId LIMIT 1")
    fun getScoreForClip(clipId: Long): Flow<ViralScoreMetricEntity?>

    @Query("SELECT * FROM viral_score_metrics WHERE clipId = :clipId LIMIT 1")
    suspend fun getScoreForClipSync(clipId: Long): ViralScoreMetricEntity?

    @Query("SELECT * FROM viral_score_metrics ORDER BY overallViralityScore DESC LIMIT :limit")
    fun getTopViralClips(limit: Int = 10): Flow<List<ViralScoreMetricEntity>>

    @Query("SELECT AVG(overallViralityScore) FROM viral_score_metrics")
    suspend fun getAverageViralityScore(): Float?

    @Query("SELECT AVG(hookScore) FROM viral_score_metrics")
    suspend fun getAverageHookScore(): Float?

    @Query("SELECT AVG(retentionScore) FROM viral_score_metrics")
    suspend fun getAverageRetentionScore(): Float?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: ViralScoreMetricEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScores(scores: List<ViralScoreMetricEntity>)

    @Update
    suspend fun updateScore(score: ViralScoreMetricEntity)

    @Delete
    suspend fun deleteScore(score: ViralScoreMetricEntity)

    @Query("DELETE FROM viral_score_metrics WHERE clipId = :clipId")
    suspend fun deleteScoreForClip(clipId: Long)

    @Query("DELETE FROM viral_score_metrics WHERE projectId = :projectId")
    suspend fun deleteScoresForProject(projectId: Long)

    @Query("DELETE FROM viral_score_metrics")
    suspend fun clearAllScores()
}
