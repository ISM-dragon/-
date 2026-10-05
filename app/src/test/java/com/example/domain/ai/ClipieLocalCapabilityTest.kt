package com.example.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipieLocalCapabilityTest {
    private val fullLocal = ClipieLocalCapability(
        localTranscription = true,
        localVideoRendering = true,
        timedCaptions = true
    )

    @Test
    fun `gateway is canonical when configured`() {
        assertEquals(
            ClipieProcessingRoute.REMOTE_CANONICAL,
            ClipieLocalCapabilityPolicy.chooseRoute(fullLocal, gatewayConfigured = true, offlineRequested = false)
        )
    }

    @Test
    fun `full local capability supports bounded offline fallback`() {
        assertEquals(
            ClipieProcessingRoute.LOCAL_OFFLINE_FALLBACK,
            ClipieLocalCapabilityPolicy.chooseRoute(fullLocal, gatewayConfigured = false, offlineRequested = true)
        )
    }

    @Test
    fun `missing local renderer does not claim offline parity`() {
        val capability = fullLocal.copy(localVideoRendering = false)
        assertEquals(
            ClipieProcessingRoute.UNAVAILABLE,
            ClipieLocalCapabilityPolicy.chooseRoute(capability, gatewayConfigured = false, offlineRequested = true)
        )
    }

    @Test
    fun `insecure model download configuration is rejected`() {
        val issues = ClipieLocalCapabilityPolicy.validate(
            fullLocal.copy(modelDownloadSupported = true, secureProviderKeyStorage = false)
        )
        assertTrue(issues.any { it.contains("secure key storage") })
    }
}
