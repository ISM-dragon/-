package com.example

import com.example.data.model.DirectPlatformApiCredentials
import com.example.data.remote.GeminiClipService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        assertEquals(401, result.httpCode)
        assertTrue(result.postUrl.isBlank())
    }

    @Test
    fun videoPlatformDoesNotClaimPublishWithoutMediaUploadFlow() = runBlocking {
        val result = GeminiClipService().publishDirectViaApi(
            platform = "TikTok",
            clipTitle = "Test clip",
            captionText = "Test caption",
            credentials = DirectPlatformApiCredentials(tiktokAccessToken = "test-token")
        )

        assertFalse(result.isSuccess)
        assertEquals(501, result.httpCode)
        assertTrue(result.responseSummary.contains("رفع ملف الفيديو"))
    }
}

