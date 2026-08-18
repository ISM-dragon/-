package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.model.AiUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiUsageDao {
    @Insert
    suspend fun insert(record: AiUsageEntity)

    @Query("SELECT * FROM ai_usage ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AiUsageEntity>>

    @Query("SELECT * FROM ai_usage WHERE success = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun observeFailures(limit: Int = 100): Flow<List<AiUsageEntity>>
}
