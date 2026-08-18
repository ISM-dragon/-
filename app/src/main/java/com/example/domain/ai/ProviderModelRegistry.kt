package com.example.domain.ai

import com.example.data.model.AiProviderType

data class ModelCatalogEntry(
    val providerType: AiProviderType,
    val modelId: String,
    val displayName: String,
    val capabilities: Set<AiCapability>,
    val inputCostPerMillionUsd: Double? = null,
    val outputCostPerMillionUsd: Double? = null,
    val enabledByDefault: Boolean = false
)

/**
 * Future-facing catalog only. It does not contain API keys and does not imply
 * that a model is configured or available in the current installation.
 */
object ProviderModelRegistry {
    val builtInCatalog: List<ModelCatalogEntry> = listOf(
        ModelCatalogEntry(
            AiProviderType.GEMINI,
            "gemini-2.5-flash",
            "Gemini 2.5 Flash",
            setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT, AiCapability.VIDEO_INPUT),
            inputCostPerMillionUsd = 0.30,
            outputCostPerMillionUsd = 2.50,
            enabledByDefault = true
        ),
        ModelCatalogEntry(
            AiProviderType.OPENAI,
            "gpt-4o-mini",
            "GPT-4o mini",
            setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT, AiCapability.VISION),
            inputCostPerMillionUsd = 0.15,
            outputCostPerMillionUsd = 0.60
        ),
        ModelCatalogEntry(
            AiProviderType.GROQ,
            "openai/whisper-large-v3-turbo",
            "Whisper Large V3 Turbo",
            setOf(AiCapability.TRANSCRIPTION, AiCapability.WORD_TIMESTAMPS)
        ),
        ModelCatalogEntry(
            AiProviderType.ANTHROPIC,
            "claude-sonnet-placeholder",
            "Anthropic model slot",
            setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT, AiCapability.VISION)
        ),
        ModelCatalogEntry(
            AiProviderType.OPENROUTER,
            "openrouter-model-slot",
            "OpenRouter model slot",
            setOf(AiCapability.TEXT, AiCapability.STRUCTURED_OUTPUT)
        )
    )

    fun find(provider: AiProviderType, modelId: String): ModelCatalogEntry? =
        builtInCatalog.firstOrNull { it.providerType == provider && it.modelId == modelId }
}
