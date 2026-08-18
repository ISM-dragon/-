package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.RepurposingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepurposingHistoryDao {

    @Query("SELECT * FROM repurposing_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<RepurposingHistoryEntity>>

    @Query("SELECT * FROM repurposing_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 25): Flow<List<RepurposingHistoryEntity>>

    @Query("SELECT * FROM repurposing_history WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getHistoryForProject(projectId: Long): Flow<List<RepurposingHistoryEntity>>

    @Query("SELECT * FROM repurposing_history WHERE actionType = :actionType ORDER BY timestamp DESC")
    fun getHistoryByAction(actionType: String): Flow<List<RepurposingHistoryEntity>>

    @Query("SELECT SUM(estimatedTimeSavedMinutes) FROM repurposing_history WHERE status = 'SUCCESS'")
    fun getTotalEstimatedTimeSaved(): Flow<Int?>

    @Query("SELECT SUM(clipsGeneratedCount) FROM repurposing_history WHERE status = 'SUCCESS'")
    fun getTotalClipsGenerated(): Flow<Int?>

    @Query("SELECT COUNT(*) FROM repurposing_history")
    suspend fun getHistoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: RepurposingHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryList(histories: List<RepurposingHistoryEntity>)

    @Update
    suspend fun updateHistory(history: RepurposingHistoryEntity)

    @Delete
    suspend fun deleteHistory(history: RepurposingHistoryEntity)

    @Query("DELETE FROM repurposing_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM repurposing_history WHERE projectId = :projectId")
    suspend fun deleteHistoryForProject(projectId: Long)

    @Query("DELETE FROM repurposing_history")
    suspend fun clearAllHistory()
}
