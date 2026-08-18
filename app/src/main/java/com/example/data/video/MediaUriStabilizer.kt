package com.example.data.video

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object MediaUriStabilizer {
    /**
     * Copies a picker Uri into app-private storage so background workers do not
     * depend on a temporary picker permission or a provider process staying alive.
     */
    fun copyForBackground(context: Context, uri: Uri, displayName: String): Uri {
        if (uri.scheme == "file") return uri
        val source = context.contentResolver.openInputStream(uri)
            ?: error("تعذر فتح ملف الفيديو المختار")
        val directory = File(context.filesDir, "source_media").apply { mkdirs() }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "video.mp4" }
        val destination = File(directory, "${UUID.randomUUID()}_$safeName")
        source.use { input ->
            destination.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        require(destination.length() > 0L) { "ملف الفيديو المنسوخ فارغ" }
        return Uri.fromFile(destination)
    }
}
