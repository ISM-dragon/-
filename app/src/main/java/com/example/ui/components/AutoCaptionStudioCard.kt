package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnimatedWord
import com.example.data.model.Clip
import com.example.data.repository.OpusRepository
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoCaptionStudioCard(
    clip: Clip,
    selectedCaptionTheme: String,
    onThemeSelect: (String) -> Unit,
    captionPosition: String,
    onPositionChange: (String) -> Unit,
    fontSizeSp: Int,
    onFontSizeChange: (Int) -> Unit,
    showAutoEmojis: Boolean,
    onShowAutoEmojisChange: (Boolean) -> Unit,
    isUppercase: Boolean,
    onUppercaseChange: (Boolean) -> Unit,
    onSeekToSec: (Float) -> Unit,
    repository: OpusRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessingSTT by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("English (US)") }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var activeSubTab by remember { mutableIntStateOf(0) } // 0: Templates, 1: STT & Word Timestamps, 2: Layout & Typography, 3: Export SRT/VTT

    val languages = listOf("English (US)", "العربية (Arabic)", "Español (Spanish)", "Français (French)", "Deutsch (German)", "日本語 (Japanese)", "Português (Portuguese)", "Hindi (हिन्दी)")

    val currentWords = remember(clip.animatedCaptionsJson) {
        repository.getClipWords(clip)
    }

    var editingWordIndex by remember { mutableStateOf<Int?>(null) }
    var editWordText by remember { mutableStateOf("") }
    var editWordEmoji by remember { mutableStateOf("") }
    var editWordIsHighlight by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Top Header with STT trigger
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
            border = BorderStroke(1.dp, OpusBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
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
                                .background(OpusPrimaryViolet.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "STT",
                                tint = OpusElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Auto-Caption & Speech-to-Text",
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "AI Audio Transcription & Millisecond Timestamp Sync",
                                color = OpusTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Language Selector
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(OpusDarkSurfaceVariant)
                                .border(1.dp, OpusBorder, RoundedCornerShape(8.dp))
                                .clickable { showLanguageMenu = true }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = selectedLanguage.substringBefore(" "),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusElectricCyan
                            )
                        }

                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                            modifier = Modifier.background(OpusDarkSurface)
                        ) {
                            languages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang, color = OpusTextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        selectedLanguage = lang
                                        showLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // One-click AI STT action button
                Button(
                    onClick = {
                        if (isProcessingSTT) return@Button
                        isProcessingSTT = true
                        coroutineScope.launch {
                            try {
                                repository.reparseAndSyncSpeechToText(
                                    clipId = clip.id,
                                    transcriptOrAudio = clip.transcript,
                                    durationSec = clip.durationSec.toFloat(),
                                    language = selectedLanguage,
                                    captionTheme = selectedCaptionTheme
                                )
                                Toast.makeText(context, "تمت إعادة مواءمة وتوليد التسميات التوضيحية بنجاح!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "حدث خطأ أثناء معالجة الصوت: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isProcessingSTT = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_reparse_stt_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet),
                    enabled = !isProcessingSTT
                ) {
                    if (isProcessingSTT) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Analyzing Speech Waveforms & Aligning Words...",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Auto-Sync",
                            tint = OpusElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "توليد وضبط التسميات التلقائية بالذكاء الاصطناعي (Auto-Sync STT)",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sub Studio Tabs (Templates, Word Timestamps, Layout & Typography, Export Subtitles)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(OpusDarkSurface)
                .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("🎨 Templates", "⏱️ Word Timestamps", "📐 Typography", "📄 SRT / VTT").forEachIndexed { idx, tabTitle ->
                val isSel = activeSubTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) OpusPrimaryViolet else Color.Transparent)
                        .clickable { activeSubTab = idx }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabTitle,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) Color.White else OpusTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tab Content
        when (activeSubTab) {
            0 -> {
                // Templates Picker
                CaptionStylePicker(
                    selectedTheme = selectedCaptionTheme,
                    onThemeSelect = onThemeSelect
                )
            }
            1 -> {
                // Word Timestamps & Interactive Timeline Editor
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                    border = BorderStroke(1.dp, OpusBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Interactive Timed Words (${currentWords.size} words)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                            Text(
                                text = "Tap word to seek video",
                                fontSize = 10.sp,
                                color = OpusViralEmerald
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (currentWords.isEmpty()) {
                            Text(
                                text = "No timed words available. Click 'Auto-Sync STT' above to generate.",
                                color = OpusTextSecondary,
                                fontSize = 12.sp
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                currentWords.forEachIndexed { index, wordItem ->
                                    val isHigh = wordItem.isHighlight
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isHigh) OpusPrimaryViolet.copy(alpha = 0.35f) else OpusDarkSurfaceVariant)
                                            .border(
                                                1.dp,
                                                if (isHigh) OpusElectricCyan else OpusBorder,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                onSeekToSec(wordItem.startSec)
                                            }
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                            .testTag("word_chip_$index")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = wordItem.word,
                                                fontSize = 11.sp,
                                                fontWeight = if (isHigh) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isHigh) OpusElectricCyan else OpusTextPrimary
                                            )
                                            if (wordItem.emoji.isNotBlank()) {
                                                Text(text = wordItem.emoji, fontSize = 10.sp)
                                            }
                                            Text(
                                                text = "${wordItem.startSec}s",
                                                fontSize = 9.sp,
                                                color = OpusTextSecondary
                                            )
                                            // Edit button
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Word",
                                                tint = OpusTextSecondary,
                                                modifier = Modifier
                                                    .size(11.dp)
                                                    .clickable {
                                                        editingWordIndex = index
                                                        editWordText = wordItem.word
                                                        editWordEmoji = wordItem.emoji
                                                        editWordIsHighlight = wordItem.isHighlight
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                // Layout, Placement & Typography Settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                    border = BorderStroke(1.dp, OpusBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Subtitle Overlay Layout & Typography",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Subtitle Position
                        Text(
                            text = "Screen Position:",
                            fontSize = 11.sp,
                            color = OpusTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Bottom (Safe Zone)", "Center", "Top").forEach { pos ->
                                val isSel = captionPosition == pos
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) OpusDarkSurfaceHighlight else OpusDarkSurfaceVariant)
                                        .border(1.dp, if (isSel) OpusElectricCyan else OpusBorder, RoundedCornerShape(8.dp))
                                        .clickable { onPositionChange(pos) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pos.substringBefore(" "),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) OpusElectricCyan else OpusTextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Font Size Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Font Scale: ${fontSizeSp}sp",
                                fontSize = 11.sp,
                                color = OpusTextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Row {
                                listOf(12, 14, 18, 22).forEach { sizeOption ->
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (fontSizeSp == sizeOption) OpusPrimaryViolet else OpusDarkSurfaceVariant)
                                            .clickable { onFontSizeChange(sizeOption) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${sizeOption}sp",
                                            fontSize = 9.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Slider(
                            value = fontSizeSp.toFloat(),
                            onValueChange = { onFontSizeChange(it.toInt()) },
                            valueRange = 11f..26f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = OpusElectricCyan,
                                activeTrackColor = OpusPrimaryViolet,
                                inactiveTrackColor = OpusDarkSurfaceHighlight
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Switch: Auto Emojis
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Dynamic Auto Emojis 🔥",
                                    fontSize = 12.sp,
                                    color = OpusTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Shows animated contextual emojis over trigger words",
                                    fontSize = 10.sp,
                                    color = OpusTextSecondary
                                )
                            }
                            Switch(
                                checked = showAutoEmojis,
                                onCheckedChange = onShowAutoEmojisChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = OpusViralEmerald
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Switch: UPPERCASE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "All UPPERCASE Style",
                                    fontSize = 12.sp,
                                    color = OpusTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "High-impact MrBeast / Hormozi capitalization",
                                    fontSize = 10.sp,
                                    color = OpusTextSecondary
                                )
                            }
                            Switch(
                                checked = isUppercase,
                                onCheckedChange = onUppercaseChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = OpusGold
                                )
                            )
                        }
                    }
                }
            }
            3 -> {
                // Export SRT / VTT Subtitle Files
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                    border = BorderStroke(1.dp, OpusBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Export Timed Subtitle Files (.SRT / .VTT)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                        Text(
                            text = "Compatible with Premiere Pro, CapCut, DaVinci Resolve, and YouTube",
                            fontSize = 11.sp,
                            color = OpusTextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Copy SRT
                            Button(
                                onClick = {
                                    val srtContent = repository.exportClipSrt(clip)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Subtitles.srt", srtContent))
                                    Toast.makeText(context, "تم نسخ ملف ترجمات SRT للحافظة!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OpusDarkSurfaceHighlight),
                                border = BorderStroke(1.dp, OpusElectricCyan)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = OpusElectricCyan, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy .SRT", color = OpusElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Copy VTT
                            Button(
                                onClick = {
                                    val vttContent = repository.exportClipVtt(clip)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Subtitles.vtt", vttContent))
                                    Toast.makeText(context, "تم نسخ ملف ترجمات VTT للحافظة!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OpusDarkSurfaceHighlight),
                                border = BorderStroke(1.dp, OpusVioletGlow)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = OpusVioletGlow, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy .VTT", color = OpusVioletGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Share Subtitle File
                        Button(
                            onClick = {
                                val srtContent = repository.exportClipSrt(clip)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "${clip.title} Subtitles")
                                    putExtra(Intent.EXTRA_TEXT, srtContent)
                                }
                                val chooser = Intent.createChooser(sendIntent, "مشاركة ملف الترجمة .SRT")
                                context.startActivity(chooser)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share Subtitle File (.SRT)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Word Edit Dialog
    if (editingWordIndex != null) {
        val targetIdx = editingWordIndex!!
        AlertDialog(
            onDismissRequest = { editingWordIndex = null },
            containerColor = OpusDarkSurface,
            title = {
                Text(
                    text = "Edit Timed Word",
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary,
                    fontSize = 15.sp
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editWordText,
                        onValueChange = { editWordText = it },
                        label = { Text("Word Text", color = OpusTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OpusTextPrimary,
                            unfocusedTextColor = OpusTextPrimary,
                            focusedBorderColor = OpusElectricCyan,
                            unfocusedBorderColor = OpusBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editWordEmoji,
                        onValueChange = { editWordEmoji = it },
                        label = { Text("Optional Context Emoji (e.g. 🔥, 🚀, 💡)", color = OpusTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OpusTextPrimary,
                            unfocusedTextColor = OpusTextPrimary,
                            focusedBorderColor = OpusElectricCyan,
                            unfocusedBorderColor = OpusBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Viral Highlight Keyword",
                            fontSize = 12.sp,
                            color = OpusTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = editWordIsHighlight,
                            onCheckedChange = { editWordIsHighlight = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = OpusElectricCyan
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedList = currentWords.toMutableList()
                        if (targetIdx in updatedList.indices) {
                            val old = updatedList[targetIdx]
                            updatedList[targetIdx] = old.copy(
                                word = editWordText,
                                emoji = editWordEmoji,
                                isHighlight = editWordIsHighlight
                            )
                            coroutineScope.launch {
                                repository.updateClipWordList(clip.id, updatedList)
                            }
                        }
                        editingWordIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWordIndex = null }) {
                    Text("Cancel", color = OpusTextSecondary)
                }
            }
        )
    }
}
