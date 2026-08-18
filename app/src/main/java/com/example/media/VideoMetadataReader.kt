package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoMetadata(
    val fileName: String = "video.mp4",
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnail: Bitmap? = null
)

object VideoMetadataReader {
    suspend fun read(context: Context, uri: Uri, maxThumbnailDimension: Int = 720): VideoMetadata =
        withContext(Dispatchers.IO) {
            val resolver = context.applicationContext.contentResolver
            var fileName = "video.mp4"
            var fileSize = 0L
            runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            var retriever: MediaMetadataRetriever? = null
            var thumbnail: Bitmap? = null
            try {
                retriever = MediaMetadataRetriever().apply { setDataSource(context, uri) }
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                thumbnail = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                thumbnail = thumbnail?.let { downsample(it, maxThumbnailDimension) }
                VideoMetadata(fileName, fileSize, duration, width, height, thumbnail)
            } finally {
                retriever?.release()
            }
        }

    private fun downsample(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largestDimension = maxOf(bitmap.width, bitmap.height)
        if (largestDimension <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largestDimension.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
