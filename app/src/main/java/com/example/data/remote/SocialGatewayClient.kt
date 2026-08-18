package com.example.data.remote

import com.example.data.model.GatewayAccountStatus
import com.example.data.model.GatewayConfig
import com.example.data.model.GatewayPostStatus
import com.example.data.model.GatewaySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

class SocialGatewayClient {

    suspend fun loadSnapshot(config: GatewayConfig): Result<GatewaySnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = validateBaseUrl(config.baseUrl)
            val summary = JSONObject(getBody("$baseUrl/v1/dashboard/summary", config.token))
            val accountsJson = JSONArray(getBody("$baseUrl/v1/accounts", config.token))
            val postsJson = JSONArray(getBody("$baseUrl/v1/social/schedule", config.token))

            val statusCounts = mutableMapOf<String, Int>()
            val counts = summary.optJSONObject("posts")
            if (counts != null) {
                val keys = counts.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    statusCounts[key] = counts.optInt(key, 0)
                }
            }

            GatewaySnapshot(
                connectedAccounts = summary.optInt("accounts", accountsJson.length()),
                statusCounts = statusCounts,
                accounts = parseAccounts(accountsJson),
                recentPosts = parsePosts(postsJson)
            )
        }
    }

    suspend fun testConnection(config: GatewayConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = validateBaseUrl(config.baseUrl)
            val response = JSONObject(getBody("$baseUrl/health", ""))
            if (!response.optBoolean("ok", false)) error("Gateway returned an unhealthy response")
            "Gateway متصل · وضع ${response.optString("provider_mode", "unknown")}"
        }
    }

    private fun parseAccounts(array: JSONArray): List<GatewayAccountStatus> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                GatewayAccountStatus(
                    id = item.optString("id"),
                    platform = item.optString("platform"),
                    accountName = item.optString("account_name"),
                    status = item.optString("status"),
                    dailyLimit = item.optInt("daily_limit", 0),
                    publishCount = item.optInt("publish_count", 0),
                    minGapSeconds = item.optInt("min_gap_seconds", 0),
                    pauseReason = item.optString("pause_reason").takeIf { it.isNotBlank() },
                    cooldownUntil = item.optString("cooldown_until").takeIf { it.isNotBlank() }
                )
            )
        }
    }

    private fun parsePosts(array: JSONArray): List<GatewayPostStatus> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                GatewayPostStatus(
                    id = item.optString("id"),
                    platform = item.optString("platform"),
                    title = item.optString("title"),
                    status = item.optString("status"),
                    scheduledAt = item.optString("scheduledAt").takeIf { it.isNotBlank() },
                    account = item.optString("account"),
                    error = item.optString("error").takeIf { it.isNotBlank() }
                )
            )
        }
    }

    private fun getBody(url: String, token: String): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer ${token.trim()}")
        }
        return try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            if (code !in 200..299) error("Gateway HTTP $code: ${body.take(240)}")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun validateBaseUrl(raw: String): String {
        val normalized = raw.trim().removeSuffix("/")
        require(normalized.isNotBlank()) { "Gateway URL غير مضبوط" }
        val uri = URI(normalized)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase().orEmpty()
        val localHost = host == "localhost" || host == "127.0.0.1" || host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.16.")
        require(scheme == "https" || localHost) { "يجب استخدام HTTPS خارج الشبكة المحلية" }
        return normalized
    }
}
