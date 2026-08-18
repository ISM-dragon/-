package com.example.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.min

/** Retries only requests explicitly marked as safe for provider failover. */
class RetryInterceptor(
    private val maxAttempts: Int = 2,
    private val baseDelayMs: Long = 500L
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(RETRYABLE_HEADER) != "true") {
            return chain.proceed(request)
        }

        var lastError: IOException? = null
        repeat(maxAttempts) { attempt ->
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful || response.code !in RETRYABLE_STATUS_CODES || attempt == maxAttempts - 1) {
                    return response
                }
                response.close()
            } catch (error: IOException) {
                lastError = error
                if (attempt == maxAttempts - 1) throw error
            }
            Thread.sleep(min(baseDelayMs * (attempt + 1), 2_000L))
        }
        throw lastError ?: IOException("Request failed after retries")
    }

    companion object {
        const val RETRYABLE_HEADER = "X-Opus-Retryable"
        private val RETRYABLE_STATUS_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
    }
}
