package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OpusBorder
import com.example.ui.theme.OpusDarkSurface
import com.example.ui.theme.OpusDarkSurfaceHighlight
import com.example.ui.theme.OpusDarkSurfaceVariant
import com.example.ui.theme.OpusElectricCyan
import com.example.ui.theme.OpusGold
import com.example.ui.theme.OpusHotPink
import com.example.ui.theme.OpusPrimaryViolet
import com.example.ui.theme.OpusTextPrimary
import com.example.ui.theme.OpusTextSecondary
import com.example.ui.theme.OpusViralEmerald
import com.example.ui.theme.OpusVioletGlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_FILE_SIZE_BYTES = 500L * 1024L * 1024L // 500 MB limit

data class SelectedVideoData(
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val thumbnailBitmap: Bitmap?
)

@Composable
fun DeviceGalleryVideoPicker(
    onVideoSelected: (SelectedVideoData) -> Unit,
    onStartProcessing: (SelectedVideoData) -> Unit,
    isProcessing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSizeBytes by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingMetadata by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "gallery_picker_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val isOverSizeLimit = fileSizeBytes > MAX_FILE_SIZE_BYTES
    val isDurationValid = durationMs in 4_000..7_200_000 // 4s to 2 hours

    // Visual media picker contract
    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            isLoadingMetadata = true
            coroutineScope.launch {
                extractVideoMetadataInternal(context, uri) { name, size, duration, w, h, bmp ->
                    fileName = name
                    fileSizeBytes = size
                    durationMs = duration
                    videoWidth = w
                    videoHeight = h
                    thumbnailBitmap = bmp
                    isLoadingMetadata = false

                    val stableUri = runCatching {
                        com.example.data.video.MediaUriStabilizer.copyForBackground(context, uri, name)
                    }.getOrElse {
                        Toast.makeText(context, "تعذر تجهيز الفيديو للمعالجة الخلفية: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                        uri
                    }
                    val data = SelectedVideoData(
                        uri = stableUri,
                        fileName = name,
                        fileSizeBytes = size,
                        durationMs = duration,
                        width = w,
                        height = h,
                        thumbnailBitmap = bmp
                    )
                    onVideoSelected(data)
                }
            }
        }
    }

    // Fallback file picker contract
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            isLoadingMetadata = true
            coroutineScope.launch {
                extractVideoMetadataInternal(context, uri) { name, size, duration, w, h, bmp ->
                    fileName = name
                    fileSizeBytes = size
                    durationMs = duration
                    videoWidth = w
                    videoHeight = h
                    thumbnailBitmap = bmp
                    isLoadingMetadata = false

                    val stableUri = runCatching {
                        com.example.data.video.MediaUriStabilizer.copyForBackground(context, uri, name)
                    }.getOrElse {
                        Toast.makeText(context, "تعذر تجهيز الفيديو للمعالجة الخلفية: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                        uri
                    }
                    val data = SelectedVideoData(
                        uri = stableUri,
                        fileName = name,
                        fileSizeBytes = size,
                        durationMs = duration,
                        width = w,
                        height = h,
                        thumbnailBitmap = bmp
                    )
                    onVideoSelected(data)
                }
            }
        }
    }

    fun launchPicker() {
        try {
            visualMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        } catch (e: Exception) {
            getContentLauncher.launch("video/*")
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_gallery_video_picker_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(OpusPrimaryViolet.copy(alpha = 0.3f))
                            .border(1.dp, OpusElectricCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = "Device Video",
                            tint = OpusElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "اختيار فيديو من المعرض (Gallery Video)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                        )
                        Text(
                            text = "اختر أي فيديو مسجل أو تم تحميله لتحليله وتوليد مقاطع فيروسية",
                            fontSize = 10.sp,
                            color = OpusTextSecondary
                        )
                    }
                }

                if (selectedVideoUri != null) {
                    TextButton(
                        onClick = { launchPicker() },
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Replace Video",
                            tint = OpusElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "استبدال",
                            fontSize = 11.sp,
                            color = OpusElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedVideoUri == null) {
                // Empty State / Browse Dropzone
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(OpusDarkSurfaceHighlight)
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(OpusElectricCyan.copy(alpha = 0.6f), OpusVioletGlow.copy(alpha = 0.6f))),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = !isProcessing) { launchPicker() }
                        .padding(24.dp)
                        .testTag("gallery_picker_dropzone"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(OpusPrimaryViolet.copy(alpha = 0.35f))
                                .border(1.5.dp, OpusElectricCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload from gallery",
                                tint = OpusElectricCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "انقر لفتح استوديو الصور واختيار فيديو",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "يدعم MP4, MOV, WEBM, MKV بحد أقصى 500 ميجابايت",
                            fontSize = 11.sp,
                            color = OpusTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { launchPicker() },
                            colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isProcessing,
                            modifier = Modifier.testTag("browse_gallery_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Browse",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تصفح ملفات الفيديو",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Specs Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpecBadgeSmall(title = "أقصى حجم", value = "500 MB", color = OpusViralEmerald, modifier = Modifier.weight(1f))
                    SpecBadgeSmall(title = "الدقة", value = "4K / 1080p", color = OpusElectricCyan, modifier = Modifier.weight(1f))
                    SpecBadgeSmall(title = "المدة", value = "حتى ساعتين", color = OpusVioletGlow, modifier = Modifier.weight(1f))
                }
            } else {
                // Video Selected Preview
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Video Thumbnail with Metadata Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(OpusDarkSurfaceHighlight)
                            .border(1.dp, OpusBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingMetadata) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = OpusElectricCyan,
                                    modifier = Modifier.size(30.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "جاري قراءة بيانات وإطارات الفيديو...",
                                    fontSize = 11.sp,
                                    color = OpusTextSecondary
                                )
                            }
                        } else if (thumbnailBitmap != null) {
                            Image(
                                bitmap = thumbnailBitmap!!.asImageBitmap(),
                                contentDescription = "Thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Play Button Indicator
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .border(1.dp, OpusElectricCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Bottom Gradient Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                        )
                                    )
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = fileName ?: "video.mp4",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (videoWidth > 0 && videoHeight > 0) {
                                    Text(
                                        text = "${videoWidth}x${videoHeight}",
                                        color = OpusElectricCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.VideoFile,
                                    contentDescription = "Video",
                                    tint = OpusElectricCyan,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = fileName ?: "تم اختيار ملف الفيديو",
                                    color = OpusTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Metadata Metrics Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PickerMetadataChip(
                            label = "الحجم",
                            value = formatBytes(fileSizeBytes),
                            isError = isOverSizeLimit,
                            modifier = Modifier.weight(1f)
                        )
                        PickerMetadataChip(
                            label = "المدة",
                            value = formatDurationMs(durationMs),
                            isError = !isDurationValid && durationMs > 0,
                            modifier = Modifier.weight(1f)
                        )
                        PickerMetadataChip(
                            label = "الصيغة",
                            value = getExtension(fileName),
                            isError = false,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // AI Autonomous Template Determination Notice
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(OpusPrimaryViolet.copy(alpha = 0.2f))
                            .border(1.dp, OpusVioletGlow.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Powered",
                                tint = OpusElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "تحديد النمط والقالب تلقائياً عبر الذكاء الاصطناعي",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OpusElectricCyan
                                )
                                Text(
                                    text = "يقوم محرك Gemini API بتحليل نبرة وسرعة الفيديو لتحديد القالب والترجمة الأكثر انتشاراً",
                                    fontSize = 10.sp,
                                    color = OpusTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Process Button
                    Button(
                        onClick = {
                            if (selectedVideoUri != null && !isOverSizeLimit) {
                                val data = SelectedVideoData(
                                    uri = selectedVideoUri!!,
                                    fileName = fileName ?: "local_video.mp4",
                                    fileSizeBytes = fileSizeBytes,
                                    durationMs = durationMs,
                                    width = videoWidth,
                                    height = videoHeight,
                                    thumbnailBitmap = thumbnailBitmap
                                )
                                onStartProcessing(data)
                            } else if (isOverSizeLimit) {
                                Toast.makeText(context, "حجم الملف يتجاوز 500 ميجابايت", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .testTag("gallery_video_process_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OpusPrimaryViolet
                        ),
                        enabled = !isProcessing && selectedVideoUri != null && !isOverSizeLimit && !isLoadingMetadata
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري استخراج المقاطع بالذكاء الاصطناعي...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Process",
                                tint = OpusElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تحليل الفيديو واستخراج المقاطع (Gemini AI)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecBadgeSmall(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontSize = 9.sp, color = OpusTextSecondary)
            Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
private fun PickerMetadataChip(
    label: String,
    value: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isError) OpusHotPink.copy(alpha = 0.15f) else OpusDarkSurfaceVariant)
            .border(1.dp, if (isError) OpusHotPink else OpusBorder, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 9.sp,
                color = if (isError) OpusHotPink else OpusTextSecondary
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isError) OpusHotPink else OpusTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private suspend fun extractVideoMetadataInternal(
    context: Context,
    uri: Uri,
    onResult: (name: String, size: Long, duration: Long, width: Int, height: Int, thumbnail: Bitmap?) -> Unit
) = withContext(Dispatchers.IO) {
    var fileName = "video.mp4"
    var fileSize = 0L

    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: "video.mp4"
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
        }
    } catch (_: Exception) {}

    var durationMs = 0L
    var width = 0
    var height = 0
    var bitmap: Bitmap? = null

    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        durationMs = durStr?.toLongOrNull() ?: 0L

        val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        width = wStr?.toIntOrNull() ?: 0
        height = hStr?.toIntOrNull() ?: 0

        bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
        retriever.release()
    } catch (_: Exception) {}

    withContext(Dispatchers.Main) {
        onResult(fileName, fileSize, durationMs, width, height, bitmap)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1.0) {
        val kb = bytes / 1024.0
        "%.1f KB".format(kb)
    } else {
        "%.1f MB".format(mb)
    }
}

private fun formatDurationMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun getExtension(name: String?): String {
    if (name.isNullOrBlank()) return "MP4"
    val ext = name.substringAfterLast('.', "MP4").uppercase()
    return ext.take(4)
}
