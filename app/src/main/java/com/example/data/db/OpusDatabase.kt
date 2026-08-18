package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Clip
import com.example.data.model.Project
import com.example.data.model.RepurposingHistoryEntity
import com.example.data.model.VideoProcessingCacheEntity
import com.example.data.model.ViralScoreMetricEntity

@Database(
    entities = [
        Project::class,
        Clip::class,
        VideoProcessingCacheEntity::class,
        ViralScoreMetricEntity::class,
        RepurposingHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OpusDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun clipDao(): ClipDao
    abstract fun videoProcessingCacheDao(): VideoProcessingCacheDao
    abstract fun viralScoreMetricDao(): ViralScoreMetricDao
    abstract fun repurposingHistoryDao(): RepurposingHistoryDao

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
