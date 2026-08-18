package com.example.data.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A real on-device face sampling service used to compute smart-reframe anchors. */
data class FaceTrackPoint(
    val timeMs: Long,
    val trackingId: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float
)

class FaceTrackingAnalyzer(private val context: Context) {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .enableTracking()
        .build()

    suspend fun analyze(
        inputUri: Uri,
        sampleIntervalMs: Long = 500L,
        maxSamples: Int = 240
    ): List<FaceTrackPoint> = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        val detector = FaceDetection.getClient(options)
        try {
            if (inputUri.scheme == "content" || inputUri.scheme == "file") {
                retriever.setDataSource(context, inputUri)
            } else {
                retriever.setDataSource(inputUri.toString(), emptyMap())
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(1L) ?: return@withContext emptyList()
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toFloatOrNull()?.coerceAtLeast(1f) ?: return@withContext emptyList()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toFloatOrNull()?.coerceAtLeast(1f) ?: return@withContext emptyList()

            val points = mutableListOf<FaceTrackPoint>()
            var sampleTime = 0L
            var samples = 0
            while (sampleTime <= durationMs && samples < maxSamples) {
                val frame = retriever.getFrameAtTime(
                    sampleTime * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (frame != null) {
                    try {
                        val faces = detector.process(InputImage.fromBitmap(frame, 0)).awaitResult()
                        faces.forEach { face ->
                            val box = face.boundingBox
                            points += FaceTrackPoint(
                                timeMs = sampleTime,
                                trackingId = face.trackingId ?: -1,
                                centerX = ((box.centerX() / width) * 2f - 1f).coerceIn(-1f, 1f),
                                centerY = ((box.centerY() / height) * 2f - 1f).coerceIn(-1f, 1f),
                                width = (box.width() / width).coerceIn(0f, 1f),
                                height = (box.height() / height).coerceIn(0f, 1f)
                            )
                        }
                    } finally {
                        frame.recycle()
                    }
                }
                sampleTime += sampleIntervalMs.coerceAtLeast(100L)
                samples++
            }
            points
        } finally {
            retriever.release()
            detector.close()
        }
    }

    private suspend fun com.google.android.gms.tasks.Task<List<Face>>.awaitResult(): List<Face> =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { faces ->
                if (continuation.isActive) continuation.resume(faces)
            }
            addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
}
