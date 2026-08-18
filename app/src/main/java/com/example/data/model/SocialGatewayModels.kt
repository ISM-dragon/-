package com.example.data.model

data class GatewayConfig(
    val baseUrl: String = "",
    val token: String = ""
)

data class GatewayAccountStatus(
    val id: String,
    val platform: String,
    val accountName: String,
    val status: String,
    val dailyLimit: Int = 0,
    val publishCount: Int = 0,
    val minGapSeconds: Int = 0,
    val pauseReason: String? = null,
    val cooldownUntil: String? = null
)

data class GatewayPostStatus(
    val id: String,
    val platform: String,
    val title: String,
    val status: String,
    val scheduledAt: String? = null,
    val account: String = "",
    val error: String? = null
)

data class GatewaySnapshot(
    val connectedAccounts: Int = 0,
    val statusCounts: Map<String, Int> = emptyMap(),
    val accounts: List<GatewayAccountStatus> = emptyList(),
    val recentPosts: List<GatewayPostStatus> = emptyList()
)
