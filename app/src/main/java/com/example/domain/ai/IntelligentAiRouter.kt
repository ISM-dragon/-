package com.example.domain.ai

import com.example.data.model.AiProviderConfig
import com.example.data.model.AiTemplateRecommendation
import com.example.data.model.ClipGenerationData
import com.example.data.model.DedicatedCaptionResult
import com.example.domain.model.CreatorProfile

/**
 * Intelligent AI Router with Dynamic Fallback, Latency Ranking, Quota & Retry Mechanisms.
 */
class IntelligentAiRouter(
    private val providers: List<AiProvider>
) {

    /**
     * Executes AI operation through highest-priority active provider, automatically falling over
     * to secondary/tertiary providers in case of 429, 5xx, or network timeouts.
     */
    suspend fun <T> routeExecutionWithFailover(
        operationName: String,
        executable: suspend (AiProvider) -> AiExecutionResult<T>
    ): AiExecutionResult<T> {
        val sortedCandidates = providers
            .filter { it.config.isEnabled && !it.config.isExhausted }
            .sortedBy { it.config.priority }

        if (sortedCandidates.isEmpty()) {
            return AiExecutionResult.Failure(
                providerName = "None",
                httpCode = 400,
                errorMessage = "No active AI providers configured. Please add an API key in AI Provider Settings.",
                canFailover = false
            )
        }

        var lastFailure: AiExecutionResult.Failure? = null

        for (provider in sortedCandidates) {
            val result = try {
                executable(provider)
            } catch (e: Exception) {
                AiExecutionResult.Failure(
                    providerName = provider.config.name,
                    httpCode = 500,
                    errorMessage = e.localizedMessage ?: "Unexpected error: ${e.javaClass.simpleName}",
                    canFailover = true
                )
            }

            when (result) {
                is AiExecutionResult.Success -> return result
                is AiExecutionResult.Failure -> {
                    lastFailure = result
                    if (!result.canFailover) {
                        return result
                    }
                    // Continue to next provider in failover pool
                }
            }
        }

        return lastFailure ?: AiExecutionResult.Failure(
            providerName = "Router",
            errorMessage = "All AI providers in failover pool failed. Please check network and quotas."
        )
    }
}
