package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.example.data.model.Project
import com.example.data.repository.OpusRepository
import com.example.data.repository.ProcessingStep
import com.example.ui.components.AutoPublishResultDialog
import com.example.ui.components.AutoPublishSettingsDialog
import com.example.ui.components.DeviceGalleryVideoPicker
import com.example.ui.components.SelectedVideoData
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
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    repository: OpusRepository,
    onProjectCreated: (Long) -> Unit,
    onOpenProject: (Long) -> Unit,
    onUploadLocalVideo: () -> Unit = {},
    onOpenApiKeySettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val processingStep by repository.processingStep.collectAsState()
    val customApiKey by repository.customApiKey.collectAsState()
    val googleFlowCredits by repository.googleFlowCredits.collectAsState()
    val aiProviders by repository.aiProviders.collectAsState()
    val allProjects by repository.allProjects.collectAsState(initial = emptyList())

    var inputSourceMode by remember { mutableIntStateOf(0) } // 0: URL / Prompt, 1: Device Media Picker
    var videoUrl by remember { mutableStateOf("") }
    var videoTitle by remember { mutableStateOf("") }
    var transcriptPrompt by remember { mutableStateOf("") }
    var durationMinutes by remember { mutableIntStateOf(15) }
    var selectedCaptionTheme by remember { mutableStateOf("Opus Neon") }
    var selectedPlatform by remember { mutableStateOf("TikTok & Reels (9:16)") }
    var autoDetectAiTemplate by remember { mutableStateOf(true) }
    var detectedRecommendation by remember { mutableStateOf<AiTemplateRecommendation?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    var showAutoPublishSettingsDialog by remember { mutableStateOf(false) }
    var showCreatorProfileDialog by remember { mutableStateOf(false) }
    var creatorProfile by remember { mutableStateOf(com.example.domain.model.CreatorProfile()) }
    val autoPublishConfig by repository.autoPublishConfig.collectAsState()
    var autoPublishDialogData by remember { mutableStateOf<Pair<Clip, AutoPublishResult>?>(null) }

    if (showCreatorProfileDialog) {
        com.example.ui.components.CreatorProfileDialog(
            initialProfile = creatorProfile,
            onSaveProfile = { creatorProfile = it },
            onDismiss = { showCreatorProfileDialog = false }
        )
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
            onDismiss = { autoPublishDialogData = null },
            onOpenStudio = {
                val pId = clip.projectId
                autoPublishDialogData = null
                onOpenProject(pId)
            }
        )
    }

    if (isProcessing || processingStep !is ProcessingStep.Idle) {
        VideoProcessingLoadingDialog(
            processingStep = processingStep,
            videoTitle = videoTitle.ifBlank { "Viral Video Analysis" }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpusDarkCanvas)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            // Hero Title & Value Proposition
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .testTag("hero_section"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(OpusPrimaryViolet.copy(alpha = 0.2f))
                        .border(1.dp, OpusVioletGlow.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = OpusElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Google Flow GenAI Repurposing Engine",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusElectricCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                            text = "حوّل فيديوك إلى مقاطع قصيرة جاهزة للنشر",

                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = OpusTextPrimary,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center
                    )
                )

                Text(
                    text = "توليد تلقائي للمقاطع الفيروسية، واستخراج الهوك القوي، والترجمة الحركية المتحركة عبر Google Flow AI ومفتاح API الخاص بك.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = OpusTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // Live Processing Progress Card
        if (processingStep !is ProcessingStep.Idle) {
            item {
                ProcessingPipelineCard(step = processingStep)
            }
        }

        // API Key Settings Quick Card (shown only when real provider/quota data exists)
        if (googleFlowCredits.totalCreditsMinutes > 0 || aiProviders.any { it.apiKey.isNotBlank() }) {
            item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenApiKeySettings() }
                    .testTag("api_key_settings_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OpusDarkSurfaceVariant
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (googleFlowCredits.remainingCreditsMinutes > 30) OpusVioletGlow.copy(alpha = 0.5f) else OpusHotPink.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (googleFlowCredits.remainingCreditsMinutes > 30) OpusViralEmerald.copy(alpha = 0.2f) else OpusHotPink.copy(alpha = 0.2f)
                            )
                            .border(
                                1.dp,
                                if (googleFlowCredits.remainingCreditsMinutes > 30) OpusViralEmerald else OpusHotPink,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Google Flow Credits",
                            tint = if (googleFlowCredits.remainingCreditsMinutes > 30) OpusViralEmerald else OpusHotPink,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Google Flow & AI Engine",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OpusTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (googleFlowCredits.remainingCreditsMinutes > 30) OpusViralEmerald.copy(alpha = 0.2f) else OpusHotPink.copy(alpha = 0.2f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${googleFlowCredits.remainingCreditsMinutes}m Credit",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (googleFlowCredits.remainingCreditsMinutes > 30) OpusViralEmerald else OpusHotPink
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (aiProviders.any { it.isEnabled && it.apiKey.isNotBlank() })
                                "نظام التبديل التلقائي نشط (${aiProviders.count { it.isEnabled && it.apiKey.isNotBlank() }} مزودين) — تبديل ذكي فوري عند نفاذ الرصيد"
                            else
                                "رصيد Google Flow: ${googleFlowCredits.remainingCreditsMinutes} دقيقة — اضغط لإدارة المفاتيح والمزودين الاحتياطيين",
                            fontSize = 11.sp,
                            color = OpusTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open API Settings",
                        tint = if (googleFlowCredits.remainingCreditsMinutes > 30) OpusViralEmerald else OpusElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        }

        // Auto-Publish Setup Quick Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showCreatorProfileDialog = true }
                    .testTag("creator_profile_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OpusDarkSurfaceHighlight
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    OpusPrimaryViolet.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(OpusPrimaryViolet.copy(alpha = 0.3f))
                            .border(1.dp, OpusElectricCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Creator Profile",
                            tint = OpusElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎨 ملف صانع المحتوى (Creator Profile)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OpusTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(OpusViralEmerald.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (creatorProfile.primaryLanguage == "ar") "العربية (RTL)" else "English",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OpusViralEmerald
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "تصنيف: ${creatorProfile.contentCategory} • نيش: ${creatorProfile.targetAudience} • حذف الصمت: ${(creatorProfile.silenceRemovalAggressiveness * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = OpusTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open Creator Profile",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Auto-Publish Setup Quick Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showAutoPublishSettingsDialog = true }
                    .testTag("auto_publish_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (autoPublishConfig.isEnabled) OpusDarkSurfaceHighlight else OpusDarkSurfaceVariant
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (autoPublishConfig.isEnabled) OpusElectricCyan.copy(alpha = 0.6f) else OpusBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (autoPublishConfig.isEnabled) OpusPrimaryViolet.copy(alpha = 0.3f) else OpusDarkSurfaceVariant
                            )
                            .border(
                                1.dp,
                                if (autoPublishConfig.isEnabled) OpusElectricCyan else OpusBorder,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Auto Publish",
                            tint = if (autoPublishConfig.isEnabled) OpusElectricCyan else OpusTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚡ خاصية النشر التلقائي",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OpusTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (autoPublishConfig.isEnabled) OpusViralEmerald.copy(alpha = 0.2f) else OpusDarkSurfaceHighlight
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (autoPublishConfig.isEnabled) "مفعل (Active)" else "غير مفعّل",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (autoPublishConfig.isEnabled) OpusViralEmerald else OpusTextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (autoPublishConfig.isEnabled)
                                "نشر ومشاركة تلقائية فورية على: ${autoPublishConfig.targetPlatforms.joinToString(" • ")}"
                            else
                                "تفعيل النشر والمشاركة التلقائية للمقاطع على TikTok, Shorts, Reels فور اكتمال التوليد",
                            fontSize = 11.sp,
                            color = OpusTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open Auto Publish Settings",
                        tint = if (autoPublishConfig.isEnabled) OpusElectricCyan else OpusTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Upload Local File Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onUploadLocalVideo() }
                    .testTag("upload_local_video_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceHighlight),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpusVioletGlow.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(OpusPrimaryViolet.copy(alpha = 0.3f))
                            .border(1.dp, OpusElectricCyan.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload Local Video",
                            tint = OpusElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "رفع فيديو من جهازك (Local Video)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OpusTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(OpusViralEmerald.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("MP4 / MOV", fontSize = 9.sp, fontWeight = FontWeight.Black, color = OpusViralEmerald)
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "استعراض فيديو من الهاتف مع استخراج الإطارات ومعاينة المقاطع فوراً",
                            fontSize = 11.sp,
                            color = OpusTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open Upload",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Main 1-Click Repurposing Input Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clipper_input_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Source Input Mode Selector Segment
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OpusDarkSurfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (inputSourceMode == 0) OpusPrimaryViolet else Color.Transparent)
                                .clickable { inputSourceMode = 0 }
                                .padding(vertical = 8.dp)
                                .testTag("source_mode_url_tab"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "URL",
                                    tint = if (inputSourceMode == 0) OpusElectricCyan else OpusTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "رابط يوتيوب / ويب",
                                    fontSize = 11.sp,
                                    fontWeight = if (inputSourceMode == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (inputSourceMode == 0) Color.White else OpusTextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (inputSourceMode == 1) OpusPrimaryViolet else Color.Transparent)
                                .clickable { inputSourceMode = 1 }
                                .padding(vertical = 8.dp)
                                .testTag("source_mode_gallery_tab"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Device Video",
                                    tint = if (inputSourceMode == 1) OpusElectricCyan else OpusTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "من الهاتف (Media Picker)",
                                    fontSize = 11.sp,
                                    fontWeight = if (inputSourceMode == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (inputSourceMode == 1) Color.White else OpusTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (inputSourceMode == 1) {
                        // Embedded Device Gallery Video Picker Component
                        DeviceGalleryVideoPicker(
                            onVideoSelected = { videoData ->
                                videoTitle = videoData.fileName
                                val durMin = (videoData.durationMs / 60000).toInt().coerceAtLeast(1)
                                durationMinutes = durMin
                                transcriptPrompt = "تحليل فيديو محلي: ${videoData.fileName} بدقة ${videoData.width}x${videoData.height}"
                                Toast.makeText(context, "تم اختيار الفيديو: ${videoData.fileName}", Toast.LENGTH_SHORT).show()
                            },
                            onStartProcessing = { videoData ->
                                isProcessing = true
                                coroutineScope.launch {
                                    try {
                                        val tTitle = videoData.fileName
                                        val tPrompt = "فيديو محلي من المعرض: ${videoData.fileName} بدقة ${videoData.width}x${videoData.height}"
                                        val tDurMin = (videoData.durationMs / 60000).toInt().coerceAtLeast(1)

                                        var cTheme = selectedCaptionTheme
                                        var pPlatform = selectedPlatform

                                        if (autoDetectAiTemplate) {
                                            try {
                                                val aiRec = repository.determineOptimalTemplate(
                                                    title = tTitle,
                                                    transcript = tPrompt,
                                                    durationSec = (videoData.durationMs / 1000).toInt().coerceAtLeast(30)
                                                )
                                                cTheme = aiRec.recommendedCaptionTheme
                                                pPlatform = aiRec.recommendedPlatform
                                            } catch (_: Exception) {}
                                        }

                                        val newProjectId = repository.processNewVideo(
                                            title = tTitle,
                                            sourceUrl = videoData.uri.toString(),
                                            transcriptOrPrompt = tPrompt,
                                            durationMinutes = tDurMin,
                                            targetPlatform = pPlatform,
                                            captionTheme = cTheme
                                        )
                                        isProcessing = false
                                        Toast.makeText(context, "تم توليد المقاطع بنجاح عبر Gemini AI!", Toast.LENGTH_SHORT).show()

                                        if (autoPublishConfig.isEnabled) {
                                            val publishRes = repository.dispatchAutoPublishForNewProject(newProjectId, context)
                                            val bestClip = repository.getBestClipForProject(newProjectId)
                                            if (publishRes != null && bestClip != null) {
                                                autoPublishDialogData = Pair(bestClip, publishRes)
                                            } else {
                                                onProjectCreated(newProjectId)
                                            }
                                        } else {
                                            onProjectCreated(newProjectId)
                                        }
                                    } catch (e: Exception) {
                                        isProcessing = false
                                        Toast.makeText(context, "خطأ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            isProcessing = isProcessing
                        )
                    } else {
                        // URL & Prompt Mode Content
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "رابط الفيديو أو نص المحتوى",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OpusTextPrimary
                                )
                            )
                            Text(
                                text = "YouTube, MP4, Podcast",
                                fontSize = 11.sp,
                                color = OpusTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // URL Input Field
                        OutlinedTextField(
                            value = videoUrl,
                            onValueChange = { videoUrl = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_url_input"),
                            placeholder = {
                                Text("الصق رابط الفيديو (مثلاً: https://youtu.be/...)", color = OpusTextSecondary, fontSize = 12.sp)
                            },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Link, contentDescription = "Link", tint = OpusElectricCyan)
                            },
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(OpusDarkSurfaceVariant)
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val item = clipboard.primaryClip?.getItemAt(0)
                                            if (item != null) {
                                                videoUrl = item.text?.toString() ?: ""
                                                Toast.makeText(context, "تم اللصق من الحافظة", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = OpusElectricCyan, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("لصق", fontSize = 10.sp, color = OpusTextPrimary)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OpusElectricCyan,
                                unfocusedBorderColor = OpusBorder,
                                focusedTextColor = OpusTextPrimary,
                                unfocusedTextColor = OpusTextPrimary,
                                focusedContainerColor = OpusDarkSurfaceHighlight,
                                unfocusedContainerColor = OpusDarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Video Title (Optional)
                        OutlinedTextField(
                            value = videoTitle,
                            onValueChange = { videoTitle = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_title_input"),
                            placeholder = {
                                Text("عنوان الفيديو أو موضوع النقاش (اختياري)", color = OpusTextSecondary, fontSize = 12.sp)
                            },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Videocam, contentDescription = "Title", tint = OpusVioletGlow)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OpusVioletGlow,
                                unfocusedBorderColor = OpusBorder,
                                focusedTextColor = OpusTextPrimary,
                                unfocusedTextColor = OpusTextPrimary,
                                focusedContainerColor = OpusDarkSurfaceHighlight,
                                unfocusedContainerColor = OpusDarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Transcript / Prompt Context Field
                        OutlinedTextField(
                            value = transcriptPrompt,
                            onValueChange = { transcriptPrompt = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transcript_prompt_input"),
                            placeholder = {
                                Text("ملخص أو نص تفريغ أو موضوعات تركيز الذكاء الاصطناعي...", color = OpusTextSecondary, fontSize = 12.sp)
                            },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.TextFields, contentDescription = "Transcript", tint = OpusGold)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OpusGold,
                                unfocusedBorderColor = OpusBorder,
                                focusedTextColor = OpusTextPrimary,
                                unfocusedTextColor = OpusTextPrimary,
                                focusedContainerColor = OpusDarkSurfaceHighlight,
                                unfocusedContainerColor = OpusDarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Source Video Duration Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مدة الفيديو التقريبية: $durationMinutes دقيقة",
                                style = MaterialTheme.typography.labelMedium.copy(color = OpusTextPrimary, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "سيظهر العدد الفعلي بعد اكتمال المعالجة",
                                fontSize = 11.sp,
                                color = OpusElectricCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Slider(
                            value = durationMinutes.toFloat(),
                            onValueChange = { durationMinutes = it.toInt() },
                            valueRange = 5f..90f,
                            steps = 16,
                            colors = SliderDefaults.colors(
                                thumbColor = OpusElectricCyan,
                                activeTrackColor = OpusElectricCyan,
                                inactiveTrackColor = OpusDarkSurfaceHighlight
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("duration_slider")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // AI Template & Styling Auto-Detection Switch Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (autoDetectAiTemplate) OpusPrimaryViolet.copy(alpha = 0.25f) else OpusDarkSurfaceVariant)
                                .border(1.dp, if (autoDetectAiTemplate) OpusElectricCyan else OpusBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI Auto Template",
                                        tint = if (autoDetectAiTemplate) OpusElectricCyan else OpusTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "🤖 تحديد القالب والنمط تلقائياً عبر AI",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OpusTextPrimary
                                        )
                                        Text(
                                            text = if (autoDetectAiTemplate)
                                                "محرك Gemini API يحلل الفيديو ويحدد القالب ونمط الترجمة الأكثر انتشاراً"
                                            else
                                                "التحكم اليدوي في قالب الترجمة والأبعاد",
                                            fontSize = 10.sp,
                                            color = if (autoDetectAiTemplate) OpusElectricCyan else OpusTextSecondary
                                        )
                                    }
                                }

                                Switch(
                                    checked = autoDetectAiTemplate,
                                    onCheckedChange = { autoDetectAiTemplate = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = OpusElectricCyan
                                    ),
                                    modifier = Modifier.testTag("home_auto_ai_template_switch")
                                )
                            }
                        }

                        if (!autoDetectAiTemplate) {
                            Spacer(modifier = Modifier.height(10.dp))
                            // Manual Caption Style Selector (Shown only if manual override selected)
                            Text(
                                text = "نمط الترجمة الحركية اليدوي (Manual Caption Theme):",
                                style = MaterialTheme.typography.labelMedium.copy(color = OpusTextPrimary, fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val captionPresets = listOf("Opus Neon", "MrBeast Bold", "Ali Abdaal Clean", "Hormozi Kinetic")
                                items(captionPresets) { theme ->
                                    val isSelected = selectedCaptionTheme == theme
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) OpusPrimaryViolet else OpusDarkSurfaceVariant)
                                            .border(1.dp, if (isSelected) OpusElectricCyan else OpusBorder, RoundedCornerShape(8.dp))
                                            .clickable { selectedCaptionTheme = theme }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected) {
                                                Icon(imageVector = Icons.Default.Check, contentDescription = "Selected", tint = OpusElectricCyan, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = theme,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) OpusTextPrimary else OpusTextSecondary
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
                                    modifier = Modifier.testTag("home_auto_publish_switch")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Button: 1-Click Generate with Google Flow
                        Button(
                            onClick = {
                                if (videoUrl.isBlank() && transcriptPrompt.isBlank() && videoTitle.isBlank()) {
                                    Toast.makeText(context, "الرجاء إدخال رابط أو نص لبدء التوليد", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isProcessing = true
                                coroutineScope.launch {
                                    val targetTitle = videoTitle.trim().ifBlank {
                                        videoUrl.trim().takeLast(12).takeIf { it.isNotBlank() }
                                            ?: transcriptPrompt.trim().lineSequence().firstOrNull()?.take(80)
                                            ?: "فيديو جديد"
                                    }
                                    val targetUrl = videoUrl.trim()
                                    val targetPrompt = transcriptPrompt.trim()

                                    try {
                                        var effectiveCaptionTheme = selectedCaptionTheme
                                        var effectivePlatform = selectedPlatform

                                        if (autoDetectAiTemplate) {
                                            try {
                                                val aiRec = repository.determineOptimalTemplate(
                                                    title = targetTitle,
                                                    transcript = targetPrompt,
                                                    durationSec = durationMinutes * 60
                                                )
                                                detectedRecommendation = aiRec
                                                effectiveCaptionTheme = aiRec.recommendedCaptionTheme
                                                effectivePlatform = aiRec.recommendedPlatform
                                            } catch (_: Exception) {}
                                        }

                                        val newProjectId = repository.processNewVideo(
                                            title = targetTitle,
                                            sourceUrl = targetUrl,
                                            transcriptOrPrompt = targetPrompt,
                                            durationMinutes = durationMinutes,
                                            targetPlatform = effectivePlatform,
                                            captionTheme = effectiveCaptionTheme
                                        )
                                        isProcessing = false
                                        Toast.makeText(context, "تم توليد المقاطع بنجاح عبر Google Flow!", Toast.LENGTH_SHORT).show()

                                        // Check if Auto-Publish is active
                                        if (autoPublishConfig.isEnabled) {
                                            val publishRes = repository.dispatchAutoPublishForNewProject(newProjectId, context)
                                            val bestClip = repository.getBestClipForProject(newProjectId)
                                            if (publishRes != null && bestClip != null) {
                                                autoPublishDialogData = Pair(bestClip, publishRes)
                                            } else {
                                                onProjectCreated(newProjectId)
                                            }
                                        } else {
                                            onProjectCreated(newProjectId)
                                        }
                                    } catch (e: Exception) {
                                        isProcessing = false
                                        Toast.makeText(context, "خطأ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .testTag("generate_clips_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OpusPrimaryViolet
                            ),
                            enabled = !isProcessing
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "جاري المعالجة وتحديد القالب بالذكاء الاصطناعي...",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Generate",
                                        tint = OpusElectricCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "استخراج وتوليد المقاطع (Google Flow AI)",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Device Gallery Video Picker Section (Replacing Static Presets UI)
        item {
            DeviceGalleryVideoPicker(
                onVideoSelected = { videoData ->
                    videoTitle = videoData.fileName
                    val durMin = (videoData.durationMs / 60000).toInt().coerceAtLeast(1)
                    durationMinutes = durMin
                    transcriptPrompt = "تحليل فيديو محلي: ${videoData.fileName} بدقة ${videoData.width}x${videoData.height} وحجم ${videoData.fileSizeBytes / (1024 * 1024)}MB"
                    Toast.makeText(context, "تم اختيار الفيديو: ${videoData.fileName}", Toast.LENGTH_SHORT).show()
                },
                onStartProcessing = { videoData ->
                    isProcessing = true
                    coroutineScope.launch {
                        try {
                            val tTitle = videoData.fileName
                            val tPrompt = "فيديو محلي من المعرض: ${videoData.fileName} بدقة ${videoData.width}x${videoData.height}"
                            val tDurMin = (videoData.durationMs / 60000).toInt().coerceAtLeast(1)

                            var cTheme = selectedCaptionTheme
                            var pPlatform = selectedPlatform

                            if (autoDetectAiTemplate) {
                                try {
                                    val aiRec = repository.determineOptimalTemplate(
                                        title = tTitle,
                                        transcript = tPrompt,
                                        durationSec = (videoData.durationMs / 1000).toInt().coerceAtLeast(30)
                                    )
                                    cTheme = aiRec.recommendedCaptionTheme
                                    pPlatform = aiRec.recommendedPlatform
                                } catch (_: Exception) {}
                            }

                            val newProjectId = repository.processNewVideo(
                                title = tTitle,
                                sourceUrl = videoData.uri.toString(),
                                transcriptOrPrompt = tPrompt,
                                durationMinutes = tDurMin,
                                targetPlatform = pPlatform,
                                captionTheme = cTheme
                            )
                            isProcessing = false
                            Toast.makeText(context, "تم توليد المقاطع بنجاح عبر Gemini AI!", Toast.LENGTH_SHORT).show()

                            if (autoPublishConfig.isEnabled) {
                                val publishRes = repository.dispatchAutoPublishForNewProject(newProjectId, context)
                                val bestClip = repository.getBestClipForProject(newProjectId)
                                if (publishRes != null && bestClip != null) {
                                    autoPublishDialogData = Pair(bestClip, publishRes)
                                } else {
                                    onProjectCreated(newProjectId)
                                }
                            } else {
                                onProjectCreated(newProjectId)
                            }
                        } catch (e: Exception) {
                            isProcessing = false
                            Toast.makeText(context, "خطأ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                isProcessing = isProcessing
            )
        }

        // Recent Projects Section: render only when real Room data exists.
        if (allProjects.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المشاريع والمقاطع السابقة",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                    )
                    Text(
                        text = "${allProjects.size} مشروع",
                        fontSize = 12.sp,
                        color = OpusTextSecondary
                    )
                }
            }

            items(allProjects) { project ->
                ProjectRowCard(
                    project = project,
                    onClick = { onOpenProject(project.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun ProcessingPipelineCard(step: ProcessingStep) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("pipeline_progress_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusVioletGlow)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = OpusElectricCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "الخطوة ${step.stepNumber}/4: ${step.title}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                    )
                }
                Text(
                    text = "${step.stepNumber * 25}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusElectricCyan
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = step.description,
                fontSize = 11.sp,
                color = OpusTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { step.stepNumber / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = OpusElectricCyan,
                trackColor = OpusDarkSurfaceHighlight,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ProjectRowCard(
    project: Project,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("project_item_${project.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OpusPrimaryViolet.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Project",
                    tint = OpusElectricCyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${project.clipCount} مقطع مولّد",
                        fontSize = 11.sp,
                        color = OpusTextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(OpusTextSecondary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${project.sourceDurationSec / 60} دقيقة",
                        fontSize = 11.sp,
                        color = OpusTextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(OpusViralEmerald.copy(alpha = 0.15f))
                    .border(1.dp, OpusViralEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Score ${project.bestViralityScore}",
                    color = OpusViralEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
