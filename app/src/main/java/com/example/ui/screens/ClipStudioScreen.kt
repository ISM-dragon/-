package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import com.example.data.model.AutoPublishResult
import com.example.data.model.Clip
import com.example.data.repository.OpusRepository
import com.example.ui.components.AutoCaptionStudioCard
import com.example.ui.components.AutoPublishResultDialog
import com.example.ui.components.AutoPublishSettingsDialog
import com.example.ui.components.BRollSuggestionCard
import com.example.ui.components.CaptionStylePicker
import com.example.ui.components.DedicatedCaptionGeneratorCard
import com.example.ui.components.DirectPublisherDialog
import com.example.ui.components.SocialCopyCard
import com.example.ui.components.VideoSimPlayer
import com.example.ui.components.ViralityRadarCard
import com.example.ui.theme.OpusBorder
import com.example.ui.theme.OpusDarkCanvas
import com.example.ui.theme.OpusDarkSurface
import com.example.ui.theme.OpusDarkSurfaceHighlight
import com.example.ui.theme.OpusDarkSurfaceVariant
import com.example.ui.theme.OpusElectricCyan
import com.example.ui.theme.OpusHotPink
import com.example.ui.theme.OpusPrimaryViolet
import com.example.ui.theme.OpusTextPrimary
import com.example.ui.theme.OpusTextSecondary
import com.example.ui.theme.OpusViralEmerald
import com.example.ui.theme.OpusVioletGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ClipStudioScreen(
    repository: OpusRepository,
    initialProjectId: Long?,
    modifier: Modifier = Modifier,
    onOpenComparison: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allClips by repository.allClips.collectAsState(initial = emptyList())

    val displayedClips = remember(allClips, initialProjectId) {
        if (initialProjectId != null && initialProjectId > 0) {
            allClips.filter { it.projectId == initialProjectId }
        } else {
            emptyList()
        }
    }

    var selectedClipIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(initialProjectId, displayedClips.size) {
        if (selectedClipIndex !in displayedClips.indices) selectedClipIndex = 0
    }
    val activeClip = displayedClips.getOrNull(selectedClipIndex) ?: displayedClips.firstOrNull()

    var selectedCaptionTheme by remember { mutableStateOf("Opus Neon") }
    var selectedLayout by remember { mutableStateOf("9:16 Full Screen") }
    var activeTab by remember { mutableIntStateOf(1) } // Default to Auto-Captions tab for direct editing
    var captionPosition by remember { mutableStateOf("Bottom (Safe Zone)") }
    var fontSizeSp by remember { mutableIntStateOf(14) }
    var showAutoEmojis by remember { mutableStateOf(true) }
    var isUppercase by remember { mutableStateOf(false) }
    var externalSeekSec by remember { mutableStateOf<Float?>(null) }
    var burnInSubtitles by remember { mutableStateOf(true) }

    var showExportModal by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var exportResolution by remember { mutableStateOf("1080p (Full HD)") }
    var removeWatermark by remember { mutableStateOf(true) }

    var showAutoPublishSettingsDialog by remember { mutableStateOf(false) }
    var autoPublishDialogData by remember { mutableStateOf<Pair<Clip, AutoPublishResult>?>(null) }
    var isPublishingNow by remember { mutableStateOf(false) }

    var showDirectPublisherDialog by remember { mutableStateOf(false) }
    var directPubPlatform by remember { mutableStateOf("TikTok") }
    var directPubCaption by remember { mutableStateOf("") }

    if (showAutoPublishSettingsDialog) {
        AutoPublishSettingsDialog(
            repository = repository,
            onDismiss = { showAutoPublishSettingsDialog = false }
        )
    }

    if (showDirectPublisherDialog && activeClip != null) {
        DirectPublisherDialog(
            clip = activeClip,
            repository = repository,
            initialPlatform = directPubPlatform,
            initialCaption = directPubCaption,
            onDismiss = { showDirectPublisherDialog = false }
        )
    }

    autoPublishDialogData?.let { (clip, result) ->
        AutoPublishResultDialog(
            clip = clip,
            publishResult = result,
            onDismiss = { autoPublishDialogData = null },
            onOpenStudio = { autoPublishDialogData = null }
        )
    }

    if (displayedClips.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(OpusDarkCanvas),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "No Clips",
                    tint = OpusTextSecondary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (initialProjectId != null && initialProjectId > 0) "لا توجد مقاطع محفوظة لهذا المشروع" else "No Clips Generated Yet",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = OpusTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = if (initialProjectId != null && initialProjectId > 0) {
                        "لم يتم إنشاء ملفات مقاطع لهذا المشروع بعد. افحص حالة المعالجة أو أعد التصدير."
                    } else {
                        "ارفع فيديو محلياً أولاً لإنشاء مقاطعك الحقيقية."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = OpusTextSecondary),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpusDarkCanvas)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Generated Viral Shorts (${displayedClips.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary
                    )
                )
                Text(
                    text = "Ranked by Virality",
                    fontSize = 11.sp,
                    color = OpusViralEmerald,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedClips.indices.toList()) { index ->
                    val clip = displayedClips[index]
                    val isSelected = activeClip?.id == clip.id
                    val scoreColor = if (clip.viralityScore >= 90) OpusViralEmerald else OpusElectricCyan

                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) OpusDarkSurfaceHighlight else OpusDarkSurface)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) OpusElectricCyan else OpusBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedClipIndex = index
                                selectedLayout = clip.layoutType
                            }
                            .padding(10.dp)
                            .testTag("clip_item_card_${clip.id}")
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(scoreColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${clip.viralityScore} Score",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = scoreColor
                                    )
                                }

                                Text(
                                    text = "${clip.durationSec}s",
                                    fontSize = 10.sp,
                                    color = OpusTextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = clip.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) OpusTextPrimary else OpusTextSecondary
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (activeClip != null) {
            item {
                VideoSimPlayer(
                    clip = activeClip,
                    selectedCaptionTheme = selectedCaptionTheme,
                    layoutType = selectedLayout,
                    onLayoutChange = { newLayout ->
                        selectedLayout = newLayout
                        coroutineScope.launch {
                            repository.updateLayoutType(activeClip.id, newLayout)
                        }
                    },
                    captionPosition = captionPosition,
                    fontSizeSp = fontSizeSp,
                    showAutoEmojis = showAutoEmojis,
                    isUppercase = isUppercase,
                    externalSeekSec = externalSeekSec,
                    onSeekComplete = { externalSeekSec = null }
                )
            }

            item {
                var isExecutingAiCommand by remember { mutableStateOf(false) }
                var lastAiCommandFeedback by remember { mutableStateOf<String?>(null) }

                com.example.ui.components.AiEditingCommandBar(
                    isProcessing = isExecutingAiCommand,
                    lastAiFeedback = lastAiCommandFeedback,
                    onExecuteCommand = { cmd ->
                        isExecutingAiCommand = true
                        coroutineScope.launch {
                            try {
                                val feedback = repository.executeAiEditingCommand(
                                    commandPrompt = cmd,
                                    clipTitle = activeClip.title,
                                    currentTranscript = activeClip.transcript,
                                    currentViralityScore = activeClip.viralityScore
                                )
                                lastAiCommandFeedback = feedback
                                Toast.makeText(context, "تم تطبيق أمر التحرير بنجاح", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                val message = e.localizedMessage?.takeIf { it.isNotBlank() }
                                    ?: "تعذر تنفيذ أمر التحرير."
                                lastAiCommandFeedback = "فشل التحرير: $message"
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            } finally {
                                isExecutingAiCommand = false
                            }
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    repository.toggleFavorite(activeClip.id, activeClip.isFavorite)
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(OpusDarkSurfaceVariant)
                                .size(40.dp)
                                .testTag("favorite_clip_button")
                        ) {
                            Icon(
                                imageVector = if (activeClip.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (activeClip.isFavorite) OpusHotPink else OpusTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = if (activeClip.isFavorite) "Saved in Favorites" else "Save Clip",
                            fontSize = 12.sp,
                            color = OpusTextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Direct In-App API Publish Action Button
                        Button(
                            onClick = {
                                directPubPlatform = "TikTok"
                                directPubCaption = ""
                                showDirectPublisherDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OpusDarkSurfaceHighlight),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OpusElectricCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("direct_publish_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Publish",
                                tint = OpusElectricCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "نشر API مباشر",
                                fontWeight = FontWeight.Bold,
                                color = OpusElectricCyan,
                                fontSize = 12.sp
                            )
                        }

                        // Export Short Button
                        Button(
                            onClick = { showExportModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("open_export_modal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Export",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Export",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                ScrollableTabRow(
                    selectedTabIndex = activeTab,
                    containerColor = OpusDarkSurface,
                    contentColor = OpusElectricCyan,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = OpusElectricCyan
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                ) {
                    listOf("Virality Radar", "Auto-Captions & STT", "مولد الكابشن الذكي", "AI B-Roll", "Social Copy").forEachIndexed { index, title ->
                        Tab(
                            selected = activeTab == index,
                            onClick = { activeTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (activeTab == index) OpusElectricCyan else OpusTextSecondary
                                )
                            },
                            modifier = Modifier.testTag("studio_tab_$index")
                        )
                    }
                }
            }

            when (activeTab) {
                0 -> item {
                    ViralityRadarCard(
                        clip = activeClip,
                        onCompareClick = {
                            onOpenComparison?.invoke(activeClip.id)
                        }
                    )
                }
                1 -> item {
                    AutoCaptionStudioCard(
                        clip = activeClip,
                        selectedCaptionTheme = selectedCaptionTheme,
                        onThemeSelect = { selectedCaptionTheme = it },
                        captionPosition = captionPosition,
                        onPositionChange = { captionPosition = it },
                        fontSizeSp = fontSizeSp,
                        onFontSizeChange = { fontSizeSp = it },
                        showAutoEmojis = showAutoEmojis,
                        onShowAutoEmojisChange = { showAutoEmojis = it },
                        isUppercase = isUppercase,
                        onUppercaseChange = { isUppercase = it },
                        onSeekToSec = { externalSeekSec = it },
                        repository = repository
                    )
                }
                2 -> item {
                    DedicatedCaptionGeneratorCard(
                        clip = activeClip,
                        repository = repository,
                        onDirectPublishClick = { platform, caption ->
                            directPubPlatform = platform
                            directPubCaption = caption
                            showDirectPublisherDialog = true
                        }
                    )
                }
                3 -> item {
                    BRollSuggestionCard(bRollPromptsJson = activeClip.bRollPromptsJson)
                }
                4 -> item {
                    SocialCopyCard(socialCopyJson = activeClip.socialCopyJson)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showExportModal) {
        AlertDialog(
            onDismissRequest = { if (!isExporting) showExportModal = false },
            containerColor = OpusDarkSurface,
            title = {
                Text(
                    text = "Export High-Definition Short",
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Choose resolution & rendering parameters:",
                        fontSize = 12.sp,
                        color = OpusTextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    listOf("1080p (Full HD)", "4K Ultra HD (60fps)").forEach { res ->
                        val isSel = exportResolution == res
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) OpusDarkSurfaceHighlight else OpusDarkSurfaceVariant)
                                .border(1.dp, if (isSel) OpusElectricCyan else OpusBorder, RoundedCornerShape(8.dp))
                                .clickable { exportResolution = res }
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = res,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) OpusElectricCyan else OpusTextPrimary
                                )
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = OpusElectricCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Burn-In Subtitles Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(OpusDarkSurfaceVariant)
                            .clickable { burnInSubtitles = !burnInSubtitles }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Burn-In AI Dynamic Subtitles",
                                fontSize = 12.sp,
                                color = OpusTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Theme: $selectedCaptionTheme ($captionPosition)",
                                fontSize = 10.sp,
                                color = OpusElectricCyan
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (burnInSubtitles) OpusElectricCyan else OpusDarkSurfaceHighlight)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (burnInSubtitles) "Hardcoded" else "Soft Sub",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (burnInSubtitles) Color.Black else OpusTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(OpusDarkSurfaceVariant)
                            .clickable { removeWatermark = !removeWatermark }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Remove ISM Watermark",
                            fontSize = 12.sp,
                            color = OpusTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (removeWatermark) OpusViralEmerald else OpusDarkSurfaceHighlight)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (removeWatermark) "Pro Enabled" else "Free Tier",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (removeWatermark) Color.Black else OpusTextSecondary
                            )
                        }
                    }

                    if (isExporting) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Encoding video: $exportProgress%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusElectricCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { exportProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = OpusElectricCyan,
                            trackColor = OpusDarkSurfaceHighlight
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isExporting) return@Button
                        val clipToExport = activeClip ?: return@Button
                        isExporting = true
                        exportProgress = 0
                        coroutineScope.launch {
                            try {
                                val output = repository.exportClipToFile(
                                    clipId = clipToExport.id,
                                    burnInSubtitles = burnInSubtitles,
                                    removeWatermark = removeWatermark,
                                    aspectRatioName = selectedLayout,
                                    onProgress = { progress -> exportProgress = progress }
                                )
                                val galleryUri = repository.saveExportToMediaStore(output)
                                isExporting = false
                                showExportModal = false
                                Toast.makeText(
                                    context,
                                    "اكتمل التصدير وحُفظ في المعرض: $galleryUri",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (error: Exception) {
                                isExporting = false
                                Toast.makeText(
                                    context,
                                    "فشل التصدير: ${error.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isExporting,
                    modifier = Modifier.testTag("confirm_export_button")
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rendering...", color = Color.White, fontSize = 12.sp)
                    } else {
                        Text("Start Export (Instant)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                if (!isExporting) {
                    TextButton(onClick = { showExportModal = false }) {
                        Text("Cancel", color = OpusTextSecondary)
                    }
                }
            }
        )
    }
}
