package com.example.domain.ai

/**
 * Describes the local capabilities observed in the Clipie APK without coupling
 * the product to its compiled Flutter implementation or native binaries.
 *
 * The policy keeps the Gateway as the canonical production route while allowing
 * a bounded on-device fallback for offline preview/transcription.
 */
data class ClipieLocalCapability(
    val localTranscription: Boolean = false,
    val localVideoRendering: Boolean = false,
    val timedCaptions: Boolean = false,
    val modelDownloadSupported: Boolean = false,
    val secureProviderKeyStorage: Boolean = true
)

enum class ClipieProcessingRoute {
    REMOTE_CANONICAL,
    LOCAL_OFFLINE_FALLBACK,
    UNAVAILABLE
}

object ClipieLocalCapabilityPolicy {
    fun chooseRoute(
        capability: ClipieLocalCapability,
        gatewayConfigured: Boolean,
        offlineRequested: Boolean
    ): ClipieProcessingRoute = when {
        gatewayConfigured && !offlineRequested -> ClipieProcessingRoute.REMOTE_CANONICAL
        offlineRequested && capability.localTranscription && capability.localVideoRendering ->
            ClipieProcessingRoute.LOCAL_OFFLINE_FALLBACK
        gatewayConfigured -> ClipieProcessingRoute.REMOTE_CANONICAL
        else -> ClipieProcessingRoute.UNAVAILABLE
    }

    fun validate(capability: ClipieLocalCapability): List<String> = buildList {
        if (capability.modelDownloadSupported && !capability.secureProviderKeyStorage) {
            add("Model/provider downloads require secure key storage.")
        }
        if (capability.timedCaptions && !capability.localVideoRendering) {
            add("Timed captions require a local rendering implementation.")
        }
    }
}
