package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_usage")
data class AiUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val model: String,
    val requestType: String,
    val inputUnits: Long? = null,
    val outputUnits: Long? = null,
    val audioDurationSec: Int? = null,
    val videoDurationSec: Int? = null,
    val latencyMs: Long,
    val success: Boolean,
    val estimatedCostUsd: Double? = null,
    val isEstimate: Boolean = estimatedCostUsd != null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
