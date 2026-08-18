package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.model.AiUsageAggregate
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

    @Query("""
        SELECT
            provider,
            model,
            COUNT(*) AS requests,
            SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successes,
            SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failures,
            COALESCE(SUM(inputUnits), 0) AS inputUnits,
            COALESCE(SUM(outputUnits), 0) AS outputUnits,
            COALESCE(SUM(inputUnits), 0) + COALESCE(SUM(outputUnits), 0) AS totalTokens,
            COALESCE(SUM(estimatedCostUsd), 0.0) AS estimatedCostUsd,
            COALESCE(AVG(latencyMs), 0.0) AS averageLatencyMs,
            MAX(createdAt) AS lastUsedAt
        FROM ai_usage
        WHERE createdAt >= :since
        GROUP BY provider, model
        ORDER BY estimatedCostUsd DESC, totalTokens DESC
    """)
    fun observeAggregatesSince(since: Long): Flow<List<AiUsageAggregate>>
}
