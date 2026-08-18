package com.example.data.model

import androidx.room.Entity

@Entity(
    tableName = "pipeline_checkpoints",
    primaryKeys = ["jobId", "stage"]
)
data class PipelineCheckpointEntity(
    val jobId: String,
    val projectId: Long,
    val stage: String,
    val status: String,
    val progress: Float,
    val message: String,
    val errorMessage: String? = null,
    val artifactPath: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
