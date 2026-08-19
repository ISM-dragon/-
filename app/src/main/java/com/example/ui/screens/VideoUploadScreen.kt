package com.example.ui.screens

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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.data.model.AiTemplateRecommendation
import com.example.data.model.AutoPublishResult
import com.example.data.model.Clip
import com.example.data.model.ProcessingJobEntity
import com.example.data.repository.OpusRepository
import com.example.data.repository.ProcessingStep
import com.example.ui.components.AutoPublishResultDialog
import com.example.ui.components.AutoPublishSettingsDialog
import com.example.ui.components.CaptionPresetsList
import com.example.ui.components.VideoProcessingLoadingDialog
import com.example.ui.theme.OpusBorder
import com.example.ui.theme.OpusDarkCanvas
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
import java.util.Locale

private const val MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024 // 500 MB limit

@Composable
fun VideoUploadScreen(
    repository: OpusRepository,
    onBack: () -> Unit,
    onProjectCreated: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val processingStep by repository.processingStep.collectAsState()

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSizeBytes by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingMetadata by remember { mutableStateOf(false) }
    var metadataError by remember { mutableStateOf<String?>(null) }

    // Repurposing Configuration Options
    var selectedCaptionTheme by remember { mutableStateOf("Opus Neon") }
    var selectedTargetLength by remember { mutableStateOf("30s - 60s") }
    var selectedLayout by remember { mutableStateOf("9:16 Full Screen") }
    var autoDetectAiTemplate by remember { mutableStateOf(true) }
    var detectedAiRecommendation by remember { mutableStateOf<AiTemplateRecommendation?>(null) }

    var showAutoPublishSettingsDialog by remember { mutableStateOf(false) }
    val autoPublishConfig by repository.autoPublishConfig.collectAsState()
    var autoPublishDialogData by remember { mutableStateOf<Pair<Clip, AutoPublishResult>?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var activeProcessingJobId by remember { mutableStateOf<String?>(null) }
    val processingJobFlow = remember(activeProcessingJobId) {
        activeProcessingJobId?.let(repository::observeProcessingJob)
    }
    val processingJob by processingJobFlow?.collectAsState(initial = null)
        ?: remember { mutableStateOf<ProcessingJobEntity?>(null) }

    LaunchedEffect(processingJob?.status, processingJob?.outputProjectId) {
        val completedJob = processingJob
        if (completedJob?.status == ProcessingJobEntity.STATUS_SUCCEEDED && completedJob.outputProjectId > 0L) {
            isProcessing = false
            activeProcessingJobId = null
            Toast.makeText(context, "اكتملت المعالجة وحُفظ المشروع الحقيقي.", Toast.LENGTH_SHORT).show()
            onProjectCreated(completedJob.outputProjectId)
        } else if (completedJob?.status == ProcessingJobEntity.STATUS_FAILED) {
            isProcessing = false
            Toast.makeText(context, "فشلت المعالجة: ${completedJob.errorMessage}", Toast.LENGTH_LONG).show()
        }
    }

    if (showAutoPublishSettingsDialog) {
        AutoPublishSettingsDialog(
            repository = repository,
            onDismiss = { showAutoPublishSettingsDialog = false }
        )
    }

    autoPublishDialogData?.let { (clip, result) ->
        AutoPublishResultDialog(
            clip = clip,
            publishResult = result,
            onDismiss = {
                val pId = clip.projectId
                autoPublishDialogData = null
                onProjectCreated(pId)
            },
            onOpenStudio = {
                val pId = clip.projectId
                autoPublishDialogData = null
                onProjectCreated(pId)
            }
        )
    }

    if (isProcessing || processingStep !is ProcessingStep.Idle) {
        VideoProcessingLoadingDialog(
            processingStep = processingStep,
            videoTitle = fileName ?: "Local Video",
            actualProgressPercent = processingJob?.progress,
            actualStage = processingJob?.currentStage
        )
    }

    // Validation flags
    val isOverSizeLimit = fileSizeBytes > MAX_FILE_SIZE_BYTES
    val isValidDuration = durationMs in 5_000..7_200_000 // 5s to 2 hours
    val isReadyForProcessing = selectedVideoUri != null &&
        !isOverSizeLimit &&
        fileSizeBytes > 0 &&
        isValidDuration &&
        metadataError == null &&
        !isLoadingMetadata

    // Media Picker Launcher (using modern PickVisualMedia)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedVideoUri = uri
            isLoadingMetadata = true
            coroutineScope.launch {
                extractVideoMetadata(context, uri) { name, size, duration, width, height, bitmap ->
                    fileName = name
                    fileSizeBytes = size
                    durationMs = duration
                    videoWidth = width
                    videoHeight = height
                    thumbnailBitmap = bitmap
                    metadataError = validateVideoMetadata(size, duration, width, height)
                    metadataError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    isLoadingMetadata = false
                }
            }
        }
    }

    val multiMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            selectedVideoUris = uris
            selectedVideoUri = uris.first()
            isLoadingMetadata = true
            coroutineScope.launch {
                extractVideoMetadata(context, uris.first()) { name, size, duration, width, height, bitmap ->
                    fileName = if (uris.size > 1) "$name (+${uris.size - 1} فيديوهات)" else name
                    fileSizeBytes = size
                    durationMs = duration
                    videoWidth = width
                    videoHeight = height
                    thumbnailBitmap = bitmap
                    metadataError = validateVideoMetadata(size, duration, width, height)
                    metadataError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    isLoadingMetadata = false
                }
            }
        }
    }

    // Fallback GetContent Launcher (if PickVisualMedia is unavailable on older APIs)
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedVideoUri = uri
            isLoadingMetadata = true
            coroutineScope.launch {
                extractVideoMetadata(context, uri) { name, size, duration, width, height, bitmap ->
                    fileName = name
                    fileSizeBytes = size
                    durationMs = duration
                    videoWidth = width
                    videoHeight = height
                    thumbnailBitmap = bitmap
                    metadataError = validateVideoMetadata(size, duration, width, height)
                    metadataError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    isLoadingMetadata = false
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpusDarkCanvas)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Navigation Header
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp).testTag("video_upload_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = OpusElectricCyan
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column {
                    Text(
                        text = "Upload Video File",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = OpusTextPrimary
                        )
                    )
                    Text(
                        text = "Extract viral shorts directly from your on-device video storage",
                        fontSize = 11.sp,
                        color = OpusTextSecondary
                    )
                }
            }
        }

        // Active Processing Card (if generating)
        if (processingStep !is ProcessingStep.Idle) {
            item {
                ActiveUploadProcessingCard(
                    processingStep = processingStep,
                    processingJob = processingJob
                )
            }
        }

        // Main Upload Dropzone / Selector Box
        if (selectedVideoUri == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            try {
                                multiMediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            } catch (e: Exception) {
                                getContentLauncher.launch("video/*")
                            }
                        }
                        .testTag("video_file_dropzone"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, OpusVioletGlow.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(OpusPrimaryViolet.copy(alpha = 0.4f), OpusDarkSurfaceHighlight)
                                    )
                                )
                                .border(1.dp, OpusElectricCyan.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload Video",
                                tint = OpusElectricCyan,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Tap to Browse & Select Video",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Supports MP4, MOV, WEBM, MKV up to 500 MB",
                            fontSize = 12.sp,
                            color = OpusTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                try {
                                    mediaPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                } catch (e: Exception) {
                                    getContentLauncher.launch("video/*")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("choose_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Browse",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Choose Local File",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Supported Specs Info Card
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpecBadge(title = "Max Size", value = "500 MB", color = OpusViralEmerald, modifier = Modifier.weight(1f))
                    SpecBadge(title = "Resolution", value = "Up to 4K", color = OpusElectricCyan, modifier = Modifier.weight(1f))
                    SpecBadge(title = "Duration", value = "Up to 2 Hrs", color = OpusVioletGlow, modifier = Modifier.weight(1f))
                }
            }
        } else {
            // Selected Video Details & Thumbnail Preview Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_preview_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Selected Video Source",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OpusTextPrimary
                                )
                            )

                            TextButton(
                                onClick = {
                                    try {
                                        mediaPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    } catch (e: Exception) {
                                        getContentLauncher.launch("video/*")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Replace",
                                    tint = OpusElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Replace",
                                    fontSize = 11.sp,
                                    color = OpusElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Video Thumbnail Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(OpusDarkSurfaceHighlight)
                                .border(1.dp, OpusBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMetadata) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = OpusElectricCyan,
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Analyzing video stream...",
                                        fontSize = 12.sp,
                                        color = OpusTextSecondary
                                    )
                                }
                            } else if (thumbnailBitmap != null) {
                                Image(
                                    bitmap = thumbnailBitmap!!.asImageBitmap(),
                                    contentDescription = "Video Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Overlay Play Badge
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
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

                                // Bottom Badges Bar on Thumbnail
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
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatDuration(durationMs),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (videoWidth > 0 && videoHeight > 0) "${videoWidth}x${videoHeight}" else "HD Video",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OpusElectricCyan
                                    )
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Video",
                                        tint = OpusVioletGlow,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Video Ready",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OpusTextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Video Title and Metadata Details
                        Text(
                            text = fileName ?: "Selected Local Video",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetadataTag(
                                label = "Size",
                                value = formatFileSize(fileSizeBytes),
                                isError = isOverSizeLimit
                            )
                            MetadataTag(
                                label = "Duration",
                                value = formatDuration(durationMs),
                                isError = !isValidDuration && durationMs > 0
                            )
                            MetadataTag(
                                label = "Format",
                                value = getFileExtension(fileName),
                                isError = false
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Size Validation Status Banner
                        if (isOverSizeLimit) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OpusHotPink.copy(alpha = 0.15f))
                                    .border(1.dp, OpusHotPink, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Size Exceeded",
                                        tint = OpusHotPink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "File size (${formatFileSize(fileSizeBytes)}) exceeds the 500 MB upload limit. Please compress or trim the video.",
                                        fontSize = 11.sp,
                                        color = OpusHotPink,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OpusViralEmerald.copy(alpha = 0.15f))
                                    .border(1.dp, OpusViralEmerald.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Valid",
                                        tint = OpusViralEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "File validated & ready for ISM AI virality parsing",
                                        fontSize = 11.sp,
                                        color = OpusViralEmerald,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Repurposing Configuration Options (AI Autonomous by default)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Powered",
                                    tint = OpusElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تحديد القالب ونمط الترجمة بالذكاء الاصطناعي",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = OpusTextPrimary
                                    )
                                )
                            }

                            Switch(
                                checked = autoDetectAiTemplate,
                                onCheckedChange = { autoDetectAiTemplate = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = OpusElectricCyan
                                ),
                                modifier = Modifier.testTag("upload_ai_auto_template_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (autoDetectAiTemplate) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OpusPrimaryViolet.copy(alpha = 0.2f))
                                    .border(1.dp, OpusVioletGlow.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "⚡ محرك Gemini API يحدد القالب ديناميكياً",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OpusElectricCyan
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "يتم فحص طبيعة الفيديو ومحتواه الصوتي عبر مفتاح API لتطبيق نمط الترجمة الحركية المناسب (Neon, Kinetic, Bold) وأبعاد 9:16 الذكية تلقائياً.",
                                        fontSize = 11.sp,
                                        color = OpusTextSecondary
                                    )
                                }
                            }
                        } else {
                            // Manual Repurposing Parameters (Shown only when manual switch is off)
                            Spacer(modifier = Modifier.height(6.dp))

                            // Target Length Selector
                            Text(
                                text = "Target Clip Duration:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusVioletGlow
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("< 30s", "30s - 60s", "60s - 90s").forEach { length ->
                                    val isSelected = selectedTargetLength == length
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) OpusPrimaryViolet else OpusDarkSurfaceVariant)
                                            .border(1.dp, if (isSelected) OpusElectricCyan else OpusBorder, RoundedCornerShape(8.dp))
                                            .clickable { selectedTargetLength = length }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = length,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else OpusTextSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Auto-Reframing Format Selector
                            Text(
                                text = "Reframing & Aspect Ratio:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusVioletGlow
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("9:16 Full Screen", "Auto Split-Screen", "1:1 Square").forEach { layout ->
                                    val isSelected = selectedLayout == layout
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) OpusDarkSurfaceHighlight else OpusDarkSurfaceVariant)
                                            .border(1.dp, if (isSelected) OpusElectricCyan else OpusBorder, RoundedCornerShape(8.dp))
                                            .clickable { selectedLayout = layout }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = layout,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) OpusElectricCyan else OpusTextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Caption Style Theme
                            Text(
                                text = "Dynamic Caption Preset:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusVioletGlow
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(CaptionPresetsList) { preset ->
                                    val isSelected = selectedCaptionTheme == preset.name
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) OpusDarkSurfaceHighlight else OpusDarkSurfaceVariant)
                                            .border(1.dp, if (isSelected) OpusElectricCyan else OpusBorder, RoundedCornerShape(8.dp))
                                            .clickable { selectedCaptionTheme = preset.name }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(preset.highlightColor)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = preset.name,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) OpusElectricCyan else OpusTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Auto-Publish Option Switch Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (autoPublishConfig.isEnabled) OpusPrimaryViolet.copy(alpha = 0.25f) else OpusDarkSurfaceVariant)
                                .border(1.dp, if (autoPublishConfig.isEnabled) OpusElectricCyan else OpusBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Auto Publish",
                                        tint = if (autoPublishConfig.isEnabled) OpusElectricCyan else OpusTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "نشر تلقائي بعد انتهاء التوليد",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = OpusTextPrimary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Config",
                                                tint = OpusElectricCyan,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { showAutoPublishSettingsDialog = true }
                                            )
                                        }
                                        Text(
                                            text = if (autoPublishConfig.isEnabled) "مفعل (${autoPublishConfig.targetPlatforms.joinToString(", ")})" else "معطل (انقر للضبط)",
                                            fontSize = 10.sp,
                                            color = if (autoPublishConfig.isEnabled) OpusViralEmerald else OpusTextSecondary
                                        )
                                    }
                                }

                                Switch(
                                    checked = autoPublishConfig.isEnabled,
                                    onCheckedChange = { isChecked ->
                                        coroutineScope.launch {
                                            repository.saveAutoPublishConfig(autoPublishConfig.copy(isEnabled = isChecked))
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = OpusElectricCyan
                                    ),
                                    modifier = Modifier.testTag("upload_auto_publish_switch")
                                )
                            }
                        }
                    }
                }
            }

            // Primary Extract Action Button
            item {
                Button(
                    onClick = {
                        if (!isReadyForProcessing) return@Button
                        val videoTitle = fileName?.substringBeforeLast(".") ?: "Local Video Repurposed"
                        val calcDurationMin = maxOf(1, (durationMs / 60000).toInt())
                        isProcessing = true
                        coroutineScope.launch {
                            try {
                                var appliedCaptionTheme = selectedCaptionTheme
                                var appliedLayout = selectedLayout

                                if (autoDetectAiTemplate) {
                                    try {
                                        val aiRec = repository.determineOptimalTemplate(
                                            title = videoTitle,
                                            transcript = "Local uploaded video: $videoTitle",
                                            durationSec = maxOf(30, (durationMs / 1000).toInt())
                                        )
                                        detectedAiRecommendation = aiRec
                                        appliedCaptionTheme = aiRec.recommendedCaptionTheme
                                        appliedLayout = aiRec.recommendedPlatform
                                    } catch (_: Exception) {}
                                }

                                val batchUris = selectedVideoUris.ifEmpty { listOfNotNull(selectedVideoUri) }
                                val jobIds = batchUris.mapIndexed { index, uri ->
                                    repository.enqueueVideoProcessing(
                                        title = if (index == 0) videoTitle else "$videoTitle #${index + 1}",
                                        sourceUri = uri.toString(),
                                        transcriptOrPrompt = "Local uploaded video: $videoTitle",
                                        durationMinutes = calcDurationMin,
                                        targetPlatform = appliedLayout,
                                        captionTheme = appliedCaptionTheme
                                    )
                                }
                                activeProcessingJobId = jobIds.lastOrNull()
                                isProcessing = true
                                Toast.makeText(context, "أضيفت المعالجة إلى الطابور وستستمر في الخلفية.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                isProcessing = false
                                Toast.makeText(context, "خطأ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("extract_viral_clips_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReadyForProcessing && !isProcessing) OpusPrimaryViolet else OpusDarkSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isReadyForProcessing && !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "جاري المعالجة بالذكاء الاصطناعي...",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Extract",
                            tint = if (isReadyForProcessing) OpusGold else OpusTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Extract Viral Clips in 1 Click",
                            fontWeight = FontWeight.Black,
                            color = if (isReadyForProcessing) Color.White else OpusTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ActiveUploadProcessingCard(
    processingStep: ProcessingStep,
    processingJob: ProcessingJobEntity?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "upload_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_upload_processing_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceHighlight),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, OpusVioletGlow.copy(alpha = glowAlpha))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Processing Engine Active",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                    )
                }

                if (processingStep is ProcessingStep.Completed) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OpusViralEmerald)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Ready!", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = OpusElectricCyan,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val fallback = when (processingStep) {
                is ProcessingStep.Transcribing -> 0.25f to processingStep.description
                is ProcessingStep.ScanningHooks -> 0.50f to processingStep.description
                is ProcessingStep.CalculatingScores -> 0.75f to processingStep.description
                is ProcessingStep.StylingCaptions -> 0.90f to processingStep.description
                is ProcessingStep.Completed -> 1.0f to processingStep.description
                is ProcessingStep.Idle -> 0.0f to "Idle"
            }
            val progress = processingJob?.progress?.coerceIn(0, 100)?.div(100f) ?: fallback.first
            val message = processingJob?.currentStage?.takeIf { it.isNotBlank() }
                ?.replace('_', ' ')
                ?: fallback.second

            Text(
                text = message,
                fontSize = 12.sp,
                color = OpusElectricCyan,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = OpusElectricCyan,
                trackColor = OpusDarkSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpecBadge(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(OpusDarkSurface)
            .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontSize = 10.sp, color = OpusTextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun MetadataTag(
    label: String,
    value: String,
    isError: Boolean
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isError) OpusHotPink.copy(alpha = 0.2f) else OpusDarkSurfaceVariant)
            .border(1.dp, if (isError) OpusHotPink else OpusBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label: $value",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isError) OpusHotPink else OpusTextSecondary
        )
    }
}

private suspend fun extractVideoMetadata(
    context: Context,
    uri: Uri,
    onResult: (name: String, size: Long, duration: Long, width: Int, height: Int, thumbnail: Bitmap?) -> Unit
) = withContext(Dispatchers.IO) {
    var fileName = "video_${System.currentTimeMillis()}.mp4"
    var fileSize = 0L

    // Query ContentResolver for display name and file size
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex != -1) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) fileName = name
                }
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }
    } catch (_: Exception) {}

    var duration = 0L
    var width = 0
    var height = 0
    var thumbnail: Bitmap? = null

    // Extract frame & dimensions using MediaMetadataRetriever
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        duration = durationStr?.toLongOrNull() ?: 0L

        val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        width = widthStr?.toIntOrNull() ?: 0

        val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        height = heightStr?.toIntOrNull() ?: 0

        // Extract frame at 1-second mark (or 0)
        thumbnail = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
    } catch (_: Exception) {
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {}
    }

    withContext(Dispatchers.Main) {
        onResult(fileName, fileSize, duration, width, height, thumbnail)
    }
}

private fun validateVideoMetadata(size: Long, duration: Long, width: Int, height: Int): String? = when {
    size <= 0L -> "تعذر قراءة حجم ملف الفيديو. اختر الملف مرة أخرى."
    duration !in 5_000L..7_200_000L -> "مدة الفيديو غير صالحة؛ يجب أن تكون بين 5 ثوانٍ وساعتين."
    width <= 0 || height <= 0 -> "تعذر قراءة أبعاد الفيديو أو أن الملف غير صالح."
    else -> null
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1000) {
        String.format(Locale.US, "%.2f GB", mb / 1024)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun getFileExtension(name: String?): String {
    if (name.isNullOrBlank()) return "MP4"
    return name.substringAfterLast(".", "MP4").uppercase()
}
