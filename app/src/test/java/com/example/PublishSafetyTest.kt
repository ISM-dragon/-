package com.example

import com.example.data.model.DirectPlatformApiCredentials
import com.example.data.remote.GeminiClipService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishSafetyTest {
    @Test
    fun missingCredentialsNeverReportsSuccessfulPublish() = runBlocking {
        val result = GeminiClipService().publishDirectViaApi(
            platform = "TikTok",
            clipTitle = "Test clip",
            captionText = "Test caption",
            credentials = DirectPlatformApiCredentials()
        )

        assertFalse(result.isSuccess)
        assertTrue(result.httpCode == 401 || result.httpCode == 412)
        assertTrue(result.postUrl.isBlank())
    }

    @Test
    fun missingCredentialsNeverGeneratesSampleUrlsForVideoPlatforms() = runBlocking {
        val service = GeminiClipService()
        val credentials = DirectPlatformApiCredentials()

        listOf("TikTok", "Instagram Reels", "YouTube Shorts").forEach { platform ->
            val result = service.publishDirectViaApi(
                platform = platform,
                clipTitle = "Test clip",
                captionText = "Test caption",
                credentials = credentials
            )
            assertFalse(result.isSuccess)
            assertTrue(result.postUrl.isBlank())
        }
    }
}
