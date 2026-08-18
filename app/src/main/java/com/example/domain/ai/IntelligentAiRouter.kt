package com.example.domain.ai

import com.example.data.model.AiTemplateRecommendation
import com.example.data.model.ClipGenerationData
import com.example.data.model.DedicatedCaptionResult
import com.example.domain.model.CreatorProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Chooses only providers that advertise the required capability and applies a bounded fallback.
 * A failure is returned to the caller; the router never fabricates a successful response.
 */
class IntelligentAiRouter(
    private val providers: List<AiProvider>
) {
    private val usageMutex = Mutex()
    private val _usage = mutableListOf<ProviderUsageRecord>()
    val usage: List<ProviderUsageRecord>
        get() = synchronized(_usage) { _usage.toList() }

    suspend fun <T> routeExecutionWithFailover(
        operationName: String,
        executable: suspend (AiProvider) -> AiExecutionResult<T>
    ): AiExecutionResult<T> = route(
        policy = RoutingPolicy(
            task = AiTask.EDITING_COMMAND,
            requiredCapabilities = setOf(AiCapability.TEXT),
            maxAttempts = 2
        ),
        executable = executable
    )

    suspend fun <T> route(
        policy: RoutingPolicy,
        executable: suspend (AiProvider) -> AiExecutionResult<T>
    ): AiExecutionResult<T> {
        val candidates = providers
            .filter { provider ->
                provider.config.isEnabled &&
                    !provider.config.isExhausted &&
                    policy.requiredCapabilities.all(provider.capabilities::contains)
            }
            .sortedWith(
                compareBy<AiProvider> {
                    when {
                        policy.preferLowLatency && it.config.lastLatencyMs > 0 -> it.config.lastLatencyMs
                        policy.preferLowCost -> it.config.totalCreditsAllocated
                        else -> it.config.priority.toLong()
                    }
                }.thenBy { it.config.priority }
            )
            .take(policy.maxAttempts.coerceIn(1, 4))

        if (candidates.isEmpty()) {
            return AiExecutionResult.Failure(
                providerName = "Router",
                httpCode = 400,
                errorMessage = "لا يوجد مزود مفعّل يعلن دعم القدرات المطلوبة: ${policy.requiredCapabilities.joinToString()}",
                canFailover = false
            )
        }

        var lastFailure: AiExecutionResult.Failure? = null
        candidates.forEachIndexed { index, provider ->
            if (index > 0) delay((250L * (1L shl (index - 1))).coerceAtMost(2_000L))
            val started = System.currentTimeMillis()
            val result = try {
                executable(provider)
            } catch (error: Exception) {
                AiExecutionResult.Failure(
                    providerName = provider.config.name,
                    httpCode = 500,
                    errorMessage = error.localizedMessage ?: error.javaClass.simpleName,
                    canFailover = true
                )
            }
            val latency = System.currentTimeMillis() - started
            recordUsage(provider, policy.task, result, latency)
            when (result) {
                is AiExecutionResult.Success -> return result
                is AiExecutionResult.Failure -> {
                    lastFailure = result
                    if (!result.canFailover || result.httpCode !in setOf(null, 408, 429, 500, 502, 503, 504)) return result
                }
            }
        }
        return lastFailure ?: AiExecutionResult.Failure(
            providerName = "Router",
            httpCode = 503,
            errorMessage = "فشل جميع المزودين المناسبين للمهمة.",
            canFailover = false
        )
    }

    private suspend fun <T> recordUsage(
        provider: AiProvider,
        task: AiTask,
        result: AiExecutionResult<T>,
        latencyMs: Long
    ) {
        val record = when (result) {
            is AiExecutionResult.Success -> ProviderUsageRecord(
                provider = provider.config.name,
                model = provider.config.modelName,
                task = task,
                inputUnits = 0,
                outputUnits = result.tokensUsed,
                latencyMs = latencyMs,
                success = true,
                estimatedCostUsd = result.estimatedCostUsd
            )
            is AiExecutionResult.Failure -> ProviderUsageRecord(
                provider = provider.config.name,
                model = provider.config.modelName,
                task = task,
                inputUnits = 0,
                outputUnits = 0,
                latencyMs = latencyMs,
                success = false,
                estimatedCostUsd = null
            )
        }
        usageMutex.withLock { _usage += record }
    }
}
