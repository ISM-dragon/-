package com.example.domain.ai

import com.example.data.model.AiProviderType

enum class AiCapability {
    TEXT,
    STRUCTURED_OUTPUT,
    VISION,
    VIDEO_INPUT,
    TRANSCRIPTION,
    WORD_TIMESTAMPS,
    TTS,
    STREAMING
}

enum class AiTask {
    CLIP_ANALYSIS,
    TRANSCRIPTION,
    CAPTION_GENERATION,
    HOOK_GENERATION,
    TEMPLATE_RECOMMENDATION,
    EDITING_COMMAND
}

data class ProviderCapability(
    val provider: AiProviderType,
    val model: String,
    val capabilities: Set<AiCapability>,
    val contextLimit: Int = 0,
    val latencyClass: String = "standard",
    val estimatedCostPer1kTokensUsd: Double? = null
)

data class RoutingPolicy(
    val task: AiTask,
    val requiredCapabilities: Set<AiCapability>,
    val preferLowLatency: Boolean = false,
    val preferLowCost: Boolean = false,
    val maxAttempts: Int = 2
)

data class ProviderUsageRecord(
    val provider: String,
    val model: String,
    val task: AiTask,
    val inputUnits: Long,
    val outputUnits: Long,
    val latencyMs: Long,
    val success: Boolean,
    val estimatedCostUsd: Double?
)

fun AiProviderType.defaultCapabilities(): Set<AiCapability> = when (this) {
    AiProviderType.GEMINI -> setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT, AiCapability.VISION, AiCapability.VIDEO_INPUT)
    AiProviderType.OPENAI -> setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT, AiCapability.VISION, AiCapability.TRANSCRIPTION, AiCapability.TTS)
    AiProviderType.ANTHROPIC -> setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT, AiCapability.VISION)
    AiProviderType.OPENROUTER -> setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT)
    AiProviderType.GROQ -> setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT, AiCapability.TRANSCRIPTION)
    AiProviderType.MISTRAL -> setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT, AiCapability.VISION)
    AiProviderType.CUSTOM -> setOf(AiCapability.TEXT)
}

fun routingPolicyForTask(task: AiTask): RoutingPolicy = when (task) {
    AiTask.TRANSCRIPTION -> RoutingPolicy(task, setOf(AiCapability.TRANSCRIPTION), preferLowLatency = true)
    AiTask.CLIP_ANALYSIS -> RoutingPolicy(task, setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT))
    AiTask.CAPTION_GENERATION, AiTask.HOOK_GENERATION -> RoutingPolicy(task, setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT), preferLowCost = true)
    AiTask.TEMPLATE_RECOMMENDATION -> RoutingPolicy(task, setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT), preferLowLatency = true)
    AiTask.EDITING_COMMAND -> RoutingPolicy(task, setOf(AiCapability.TEXT))
}
