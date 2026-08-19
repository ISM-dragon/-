package com.example.data.model

data class GatewayProviderModel(
    val id: String,
    val name: String,
    val type: String,
    val baseUrl: String = "",
    val defaultModel: String = "",
    val fallbackModel: String = "",
    val enabled: Boolean = true,
    val credentialConfigured: Boolean = false,
    val capabilities: Map<String, Boolean> = emptyMap(),
    val inputCostPerMillionUsd: Double = 0.0,
    val outputCostPerMillionUsd: Double = 0.0
)

data class GatewayUsageAggregate(
    val provider: String,
    val model: String,
    val requests: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val estimatedRequests: Int,
    val averageLatencyMs: Double,
    val costUsd: Double
)

data class GatewayUsageSummary(
    val days: Int,
    val events: Int,
    val aggregates: List<GatewayUsageAggregate>
)

data class GatewayProcessingCapabilities(
    val pipeline: Boolean,
    val python: Boolean,
    val ffmpeg: Boolean,
    val storage: Boolean,
    val geminiConfigured: Boolean,
    val androidRemoteProcessing: Boolean,
    val youtubeUrls: Boolean,
    val httpsUrls: Boolean
)

data class GatewayProject(
    val id: String,
    val name: String,
    val source: String? = null,
    val status: String,
    val activeJobId: String? = null
)

data class GatewayJob(
    val id: String,
    val status: String,
    val stage: String? = null,
    val fraction: Double? = null,
    val message: String? = null,
    val error: String? = null,
    val projectId: String? = null
)
