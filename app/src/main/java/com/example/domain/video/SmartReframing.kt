package com.example.domain.video

import android.net.Uri

 data class CropTrajectoryPoint(
    val timestampSec: Float,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val confidence: Float
)

data class ReframingResult(
    val supported: Boolean,
    val points: List<CropTrajectoryPoint>,
    val reason: String
)

interface SmartReframingProvider {
    suspend fun detectTrajectory(uri: Uri): Result<ReframingResult>
}

/** Explicit fallback: Media3 can render a static aspect ratio, but no face/speaker model is bundled. */
class UnsupportedSmartReframingProvider : SmartReframingProvider {
    override suspend fun detectTrajectory(uri: Uri): Result<ReframingResult> =
        Result.success(
            ReframingResult(
                supported = false,
                points = emptyList(),
                reason = "لا يوجد نموذج face/active-speaker محلي مضمّن؛ سيستخدم التصدير static aspect ratio فقط."
            )
        )
}
