package com.example.data.video

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Effects
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Real on-device video export for ISM.
 *
 * Gemini supplies the semantic decision (start/end timestamps); Media3 performs
 * the deterministic trim/export. This mirrors PublikClip's separation between
 * scoring and rendering without importing its Python/desktop implementation.
 */
data class CaptionCue(
    val text: String,
    val startSec: Float,
    val endSec: Float,
    val isHighlight: Boolean = false
)

enum class ExportAspectRatio(val value: Float) {
    VERTICAL_9_16(9f / 16f),
    SQUARE_1_1(1f),
    PORTRAIT_4_5(4f / 5f),
    LANDSCAPE_16_9(16f / 9f)
}

data class SourceVideoMetadata(
    val durationSec: Int,
    val width: Int,
    val height: Int,
    val mimeType: String
)

@OptIn(markerClass = [UnstableApi::class])
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
        aspectRatio: ExportAspectRatio = if (vertical) ExportAspectRatio.VERTICAL_9_16 else ExportAspectRatio.LANDSCAPE_16_9,
        captionCues: List<CaptionCue> = emptyList(),
        watermarkText: String = "",
        cropCenterX: Float? = null,
        onProgress: (Int) -> Unit = {}
    ): File {
        require(startTimeSec >= 0) { "Clip start time cannot be negative." }
        require(endTimeSec > startTimeSec) { "Clip end time must be after its start time." }

        require(outputFile.parentFile?.exists() == true || outputFile.parentFile?.mkdirs() == true) {
            "تعذر إنشاء مجلد التصدير."
        }
        if (outputFile.exists()) {
            require(outputFile.delete()) { "تعذر حذف ملف التصدير القديم." }
        }

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startTimeSec * 1_000L)
            .setEndPositionMs(endTimeSec * 1_000L)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(clipping)
            .build()

        val videoEffects = mutableListOf<androidx.media3.common.Effect>()
        if (cropCenterX != null) {
            val source = inspectSource(inputUri)
            val sourceAspect = source
                ?.takeIf { it.width > 0 && it.height > 0 }
                ?.let { it.width.toFloat() / it.height.toFloat() }
                ?.takeIf { it.isFinite() && it > 0f }
                ?: (16f / 9f)
            val cropFraction = (aspectRatio.value / sourceAspect).coerceIn(0.01f, 1f)
            if (cropFraction < 0.999f) {
                val halfWidth = cropFraction.coerceIn(0.01f, 1f)
                val boundedCenter = cropCenterX.coerceIn(-1f, 1f)
                val left = (boundedCenter - halfWidth).coerceIn(-1f, 1f - (2f * halfWidth))
                val right = left + (2f * halfWidth)
                videoEffects += Crop(left, right, -1f, 1f)
            }
        }
        if (aspectRatio != ExportAspectRatio.LANDSCAPE_16_9 || vertical) {
            videoEffects += Presentation.createForAspectRatio(
                aspectRatio.value,
                Presentation.LAYOUT_SCALE_TO_FIT
            )
        }
        val overlays = buildList {
            if (captionCues.isNotEmpty()) add(TimedCaptionOverlay(captionCues))
            if (watermarkText.isNotBlank()) add(TextOverlay.createStaticTextOverlay(SpannableString(watermarkText)))
        }
        if (overlays.isNotEmpty()) {
            videoEffects += OverlayEffect(overlays)
        }
        val effects = Effects(emptyList(), videoEffects)

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()

        return suspendCancellableCoroutine { continuation ->
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
                        if (!continuation.isCompleted) {
                            if (outputFile.exists() && outputFile.length() > 0L) {
                                onProgress(100)
                                continuation.resume(outputFile)
                            } else {
                                continuation.resumeWithException(
                                    IllegalStateException("Media3 completed without creating an output file.")
                                )
                            }
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        progressRunnable?.let(handler::removeCallbacks)
                        if (!continuation.isCompleted) continuation.resumeWithException(exportException)
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
            continuation.invokeOnCancellation {
                progressRunnable?.let(handler::removeCallbacks)
                runCatching { transformer.cancel() }
            }
            transformer.start(editedMediaItem, outputFile.absolutePath)
        }
    }

    private class TimedCaptionOverlay(
        private val cues: List<CaptionCue>
    ) : TextOverlay() {
        override fun getText(presentationTimeUs: Long): SpannableString {
            val timeSec = presentationTimeUs / 1_000_000f
            val cue = cues.lastOrNull { timeSec >= it.startSec && timeSec <= it.endSec }
                ?: return SpannableString("")
            return SpannableString(cue.text).apply {
                setSpan(
                    AbsoluteSizeSpan(TEXT_SIZE_PIXELS),
                    0,
                    length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    ForegroundColorSpan(Color.YELLOW.takeIf { cue.isHighlight } ?: Color.WHITE),
                    0,
                    length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (cue.isHighlight) {
                    setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        length,
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
    }
}
