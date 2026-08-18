package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Clip
import com.example.data.model.Project
import com.example.data.model.ProcessingRequestEntity
import com.example.data.model.RepurposingHistoryEntity
import com.example.data.model.VideoProcessingCacheEntity
import com.example.data.model.ViralScoreMetricEntity

@Database(
    entities = [
        Project::class,
        Clip::class,
        VideoProcessingCacheEntity::class,
        ViralScoreMetricEntity::class,
        RepurposingHistoryEntity::class,
        ProcessingRequestEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class OpusDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun clipDao(): ClipDao
    abstract fun videoProcessingCacheDao(): VideoProcessingCacheDao
    abstract fun viralScoreMetricDao(): ViralScoreMetricDao
    abstract fun repurposingHistoryDao(): RepurposingHistoryDao
    abstract fun processingRequestDao(): ProcessingRequestDao

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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS processing_requests (
                        requestId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        sourceUrl TEXT NOT NULL,
                        transcriptOrPrompt TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        targetPlatform TEXT NOT NULL,
                        captionTheme TEXT NOT NULL,
                        layoutType TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_projects_createdAt ON projects(createdAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_projects_status ON projects(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clips_projectId ON clips(projectId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clips_isFavorite ON clips(isFavorite)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clips_viralityScore ON clips(viralityScore)")
            }
        }
    }
}
