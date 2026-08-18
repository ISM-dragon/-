package com.example.data.model

data class AiUsageAggregate(
    val provider: String,
    val model: String,
    val requests: Int,
    val successes: Int,
    val failures: Int,
    val inputUnits: Long,
    val outputUnits: Long,
    val totalTokens: Long,
    val estimatedCostUsd: Double,
    val averageLatencyMs: Double,
    val lastUsedAt: Long
)
