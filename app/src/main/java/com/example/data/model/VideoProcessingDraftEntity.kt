package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * Room entity representing an in-progress or interrupted video processing job.
 * Allows users to resume unfinished edits seamlessly even after closing the app.
 */
@Entity(tableName = "video_processing_drafts")
@JsonClass(generateAdapter = true)
data class VideoProcessingDraftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val transcriptPrompt: String = "",
    val durationMinutes: Int = 15,
    val targetPlatform: String = "TikTok & Reels (9:16)",
    val captionTheme: String = "Opus Neon",
    val lastProcessingStep: String = "Idle", // Idle, Transcribing, ScanningHooks, CalculatingScores, StylingCaptions, RenderingExport
    val progressPercent: Float = 0f,
    val isUnfinished: Boolean = true,
    val detectedNiche: String = "General Content",
    val partialClipsJson: String = "[]",
    val lastUpdated: Long = System.currentTimeMillis(),
    /**
     * Links this draft to the background [com.example.data.worker.VideoProcessingWorker]
     * job that owns it, so interrupted work can be resumed or cleaned up after the
     * worker reaches a terminal state while the app is closed.
     */
    val jobId: String = ""
)
