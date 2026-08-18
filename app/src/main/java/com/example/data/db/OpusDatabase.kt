package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AiUsageEntity
import com.example.data.model.Clip
import com.example.data.model.Project
import com.example.data.model.PipelineCheckpointEntity
import com.example.data.model.ProcessingJobEntity
import com.example.data.model.RepurposingHistoryEntity
import com.example.data.model.VideoProcessingCacheEntity
import com.example.data.model.ViralScoreMetricEntity

@Database(
    entities = [
        Project::class,
        Clip::class,
        AiUsageEntity::class,
        PipelineCheckpointEntity::class,
        VideoProcessingCacheEntity::class,
        ViralScoreMetricEntity::class,
        RepurposingHistoryEntity::class,
        ProcessingJobEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class OpusDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun aiUsageDao(): AiUsageDao
    abstract fun clipDao(): ClipDao
    abstract fun pipelineCheckpointDao(): PipelineCheckpointDao
    abstract fun videoProcessingCacheDao(): VideoProcessingCacheDao
    abstract fun viralScoreMetricDao(): ViralScoreMetricDao
    abstract fun repurposingHistoryDao(): RepurposingHistoryDao
    abstract fun processingJobDao(): ProcessingJobDao

    companion object {
        @Volatile
        private var INSTANCE: OpusDatabase? = null

        fun getDatabase(context: Context): OpusDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OpusDatabase::class.java,
                    "opus_pro_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
