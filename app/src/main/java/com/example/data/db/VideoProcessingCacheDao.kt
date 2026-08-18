package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.VideoProcessingCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoProcessingCacheDao {

    @Query("SELECT * FROM video_processing_cache WHERE sourceUrl = :sourceUrl LIMIT 1")
    fun getCacheByUrl(sourceUrl: String): Flow<VideoProcessingCacheEntity?>

    @Query("SELECT * FROM video_processing_cache WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getCacheByUrlSync(sourceUrl: String): VideoProcessingCacheEntity?

    @Query("SELECT * FROM video_processing_cache WHERE videoHash = :videoHash LIMIT 1")
    suspend fun getCacheByHashSync(videoHash: String): VideoProcessingCacheEntity?

    @Query("SELECT * FROM video_processing_cache ORDER BY cachedAt DESC")
    fun getAllCachedMetadata(): Flow<List<VideoProcessingCacheEntity>>

    @Query("SELECT * FROM video_processing_cache ORDER BY cachedAt DESC LIMIT :limit")
    fun getRecentCacheEntries(limit: Int = 10): Flow<List<VideoProcessingCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCache(cache: VideoProcessingCacheEntity): Long

    @Update
    suspend fun updateCache(cache: VideoProcessingCacheEntity)

    @Query("UPDATE video_processing_cache SET cacheHitCount = cacheHitCount + 1 WHERE id = :cacheId")
    suspend fun recordCacheHit(cacheId: Long)

    @Delete
    suspend fun deleteCache(cache: VideoProcessingCacheEntity)

    @Query("DELETE FROM video_processing_cache WHERE id = :id")
    suspend fun deleteCacheById(id: Long)

    @Query("DELETE FROM video_processing_cache WHERE sourceUrl = :sourceUrl")
    suspend fun deleteCacheByUrl(sourceUrl: String)

    @Query("DELETE FROM video_processing_cache WHERE cachedAt < :thresholdTimestamp")
    suspend fun purgeOlderThan(thresholdTimestamp: Long)

    @Query("DELETE FROM video_processing_cache")
    suspend fun clearAllCache()

    @Query("SELECT COUNT(*) FROM video_processing_cache")
    suspend fun getCacheCount(): Int
}
