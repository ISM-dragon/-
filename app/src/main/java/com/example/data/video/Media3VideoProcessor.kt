package com.example.data.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Effects
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Real on-device video export for Opus Pro.
 *
 * Gemini supplies the semantic decision (start/end timestamps); Media3 performs
 * the deterministic trim/export. This mirrors PublikClip's separation between
 * scoring and rendering without importing its Python/desktop implementation.
 */
@OptIn(UnstableApi::class)
data class SourceVideoMetadata(
    val durationSec: Int,
    val width: Int,
    val height: Int,
    val mimeType: String
)

@OptIn(UnstableApi::class)
class Media3VideoProcessor(private val context: Context) {

    fun inspectSource(inputUri: Uri): SourceVideoMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (inputUri.scheme == "content" || inputUri.scheme == "file") {
                retriever.setDataSource(context, inputUri)
            } else {
                retriever.setDataSource(inputUri.toString(), emptyMap())
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return null
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: "video/mp4"
            SourceVideoMetadata(
                durationSec = (durationMs / 1_000L).toInt().coerceAtLeast(1),
                width = width,
                height = height,
                mimeType = mimeType
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun exportClip(
        inputUri: Uri,
        outputFile: File,
        startTimeSec: Int,
        endTimeSec: Int,
        vertical: Boolean = true,
        onProgress: (Int) -> Unit = {}
    ): File {
        require(startTimeSec >= 0) { "Clip start time cannot be negative." }
        require(endTimeSec > startTimeSec) { "Clip end time must be after its start time." }

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startTimeSec * 1_000L)
            .setEndPositionMs(endTimeSec * 1_000L)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(clipping)
            .build()

        val effects = if (vertical) {
            Effects(
                emptyList(),
                listOf(
                    Presentation.createForAspectRatio(
                        9f / 16f,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
            )
        } else {
            Effects(emptyList(), emptyList())
        }

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()

        return suspendCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            var progressRunnable: Runnable? = null

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        progressRunnable?.let(handler::removeCallbacks)
                        if (outputFile.exists() && outputFile.length() > 0L) {
                            onProgress(100)
                            continuation.resume(outputFile)
                        } else {
                            continuation.resumeWithException(
                                IllegalStateException("Media3 completed without creating an output file.")
                            )
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        progressRunnable?.let(handler::removeCallbacks)
                        continuation.resumeWithException(exportException)
                    }
                })
                .build()

            progressRunnable = object : Runnable {
                override fun run() {
                    if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress)
                    }
                    handler.postDelayed(this, 250L)
                }
            }
            handler.post(progressRunnable!!)
            transformer.start(editedMediaItem, outputFile.absolutePath)
        }
    }
}
