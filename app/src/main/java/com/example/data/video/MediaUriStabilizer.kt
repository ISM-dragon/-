package com.example.data.video

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object MediaUriStabilizer {
    private const val SOURCE_DIRECTORY = "source_media"
    /**
     * Copies a picker Uri into app-private storage so background workers do not
     * depend on a temporary picker permission or a provider process staying alive.
     */
    fun copyForBackground(context: Context, uri: Uri, displayName: String): Uri {
        if (uri.scheme == "file") return uri
        val source = context.contentResolver.openInputStream(uri)
            ?: error("تعذر فتح ملف الفيديو المختار")
        val directory = File(context.filesDir, SOURCE_DIRECTORY).apply {
            require(exists() || mkdirs()) { "تعذر إنشاء مساحة مؤقتة للفيديو" }
        }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "video.mp4" }
        val destination = File(directory, "${UUID.randomUUID()}_$safeName")
        try {
            source.use { input ->
                destination.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
            require(destination.length() > 0L) { "ملف الفيديو المنسوخ فارغ" }
            return Uri.fromFile(destination)
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    fun deleteManagedCopy(context: Context, uriString: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        if (uri.scheme != "file") return false
        val file = runCatching { File(requireNotNull(uri.path)).canonicalFile }.getOrNull() ?: return false
        val root = runCatching { File(context.filesDir, SOURCE_DIRECTORY).canonicalFile }.getOrNull() ?: return false
        if (!file.path.startsWith(root.path + File.separator)) return false
        return !file.exists() || file.delete()
    }
}
