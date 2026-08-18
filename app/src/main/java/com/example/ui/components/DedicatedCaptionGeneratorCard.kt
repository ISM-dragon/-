package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clip
import com.example.data.model.DedicatedCaptionResult
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
fun DedicatedCaptionGeneratorCard(
    clip: Clip,
    repository: OpusRepository,
    modifier: Modifier = Modifier,
    onDirectPublishClick: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTone by remember { mutableStateOf("MrBeast Viral") }
    var selectedPlatform by remember { mutableStateOf("TikTok") }
    var selectedLanguage by remember { mutableStateOf("العربية") }
    var isGenerating by remember { mutableStateOf(false) }

    var captionResult by remember {
        mutableStateOf<DedicatedCaptionResult?>(null)
    }

    var editableCaption by remember { mutableStateOf("") }
    var selectedHook by remember { mutableStateOf("") }
    var customCTA by remember { mutableStateOf("") }

    // Initial load
    LaunchedEffect(clip.id, selectedTone, selectedPlatform, selectedLanguage) {
        if (captionResult == null) {
            isGenerating = true
            val res = repository.generateDedicatedCaption(
                videoTitle = clip.title,
                transcript = clip.transcript,
                tone = selectedTone,
                targetPlatform = selectedPlatform,
                language = selectedLanguage
            )
            captionResult = res
            selectedHook = res.hooks.firstOrNull() ?: clip.title
            editableCaption = res.mainCaption
            customCTA = res.callToAction
            isGenerating = false
        }
    }

    fun triggerRegeneration() {
        coroutineScope.launch {
            isGenerating = true
            val res = repository.generateDedicatedCaption(
                videoTitle = clip.title,
                transcript = clip.transcript,
                tone = selectedTone,
                targetPlatform = selectedPlatform,
                language = selectedLanguage
            )
            captionResult = res
            selectedHook = res.hooks.firstOrNull() ?: clip.title
            editableCaption = res.mainCaption
            customCTA = res.callToAction
            isGenerating = false
            Toast.makeText(context, "تم توليد كابشن جديد بنجاح! ✨", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyFullPost() {
        val hashtags = captionResult?.hashtags?.joinToString(" ") ?: "#Viral #Shorts"
        val full = "$selectedHook\n\n$editableCaption\n\n$customCTA\n\n$hashtags"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Viral Post", full))
        Toast.makeText(context, "تم نسخ الكابشن الكامل للحافظة! 📋", Toast.LENGTH_SHORT).show()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dedicated_caption_generator_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, OpusVioletGlow.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(OpusPrimaryViolet, OpusHotPink)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Caption Generator",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "مولد الكابشن والتسميات التوضيحية الذكي",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                        Text(
                            text = "توليد نصوص وهوكات فيروسية مخصصة للفيديوهات القصيرة",
                            fontSize = 10.sp,
                            color = OpusTextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = { triggerRegeneration() },
                    modifier = Modifier.size(34.dp).testTag("refresh_caption_btn")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = OpusElectricCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate",
                            tint = OpusElectricCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Language & Platform Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Language
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OpusDarkSurfaceVariant)
                        .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Language",
                                tint = OpusGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "اللغة: $selectedLanguage",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                        }

                        Text(
                            text = if (selectedLanguage == "العربية") "EN" else "عربي",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusElectricCyan,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(OpusDarkSurfaceHighlight)
                                .clickable {
                                    selectedLanguage = if (selectedLanguage == "العربية") "English" else "العربية"
                                    triggerRegeneration()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Target Platform
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OpusDarkSurfaceVariant)
                        .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "المنصة: $selectedPlatform",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )

                        Text(
                            text = "تغيير",
                            fontSize = 10.sp,
                            color = OpusVioletGlow,
                            modifier = Modifier
                                .clickable {
                                    val platforms = listOf("TikTok", "YouTube Shorts", "Instagram Reels", "X (Twitter)")
                                    val nextIdx = (platforms.indexOf(selectedPlatform) + 1) % platforms.size
                                    selectedPlatform = platforms[nextIdx]
                                    triggerRegeneration()
                                }
                                .padding(2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Tone Switcher Chips
            Text(
                text = "نبرة الكابشن والهوك الفيروسي:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OpusVioletGlow
            )
            Spacer(modifier = Modifier.height(6.dp))

            val tones = listOf(
                "MrBeast Viral" to "🚀 فيروسي حماسي",
                "Hormozi Value" to "💡 قيمة مكثفة",
                "Storytelling" to "📖 سرد قصصي",
                "Controversial" to "⚡ إثارة فضول"
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tones.forEach { (toneKey, label) ->
                    val isSelected = selectedTone == toneKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) OpusPrimaryViolet else OpusDarkSurfaceVariant)
                            .border(
                                1.dp,
                                if (isSelected) OpusElectricCyan else OpusBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedTone = toneKey
                                triggerRegeneration()
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else OpusTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Opening Hooks (3-Second Retention)
            val hooks = captionResult?.hooks ?: emptyList()
            if (hooks.isNotEmpty()) {
                Text(
                    text = "اختر خطاف البداية (Hook في أول 3 ثوانٍ):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusGold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    hooks.forEachIndexed { index, hook ->
                        val isHookSelected = selectedHook == hook
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isHookSelected) OpusDarkSurfaceHighlight else OpusDarkSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (isHookSelected) OpusGold else OpusBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedHook = hook }
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (isHookSelected) OpusGold else OpusDarkCanvas),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isHookSelected) Color.Black else OpusTextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = hook,
                                    fontSize = 11.sp,
                                    fontWeight = if (isHookSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isHookSelected) OpusTextPrimary else OpusTextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // 4. Main Body Caption Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "نص الكابشن المكتوب (Caption Body):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )

                Text(
                    text = "${editableCaption.length} حرف",
                    fontSize = 10.sp,
                    color = if (editableCaption.length > 2000) OpusHotPink else OpusViralEmerald
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = editableCaption,
                onValueChange = { editableCaption = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .testTag("caption_body_input"),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = OpusTextPrimary),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OpusElectricCyan,
                    unfocusedBorderColor = OpusBorder,
                    focusedContainerColor = OpusDarkCanvas,
                    unfocusedContainerColor = OpusDarkCanvas
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Hashtags
            val tags = captionResult?.hashtags ?: emptyList()
            if (tags.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = "Tags",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "الهاشتاغات الفيروسية المقترحة:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusElectricCyan
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(OpusDarkSurfaceHighlight)
                                .border(0.8.dp, OpusBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 10.sp,
                                color = OpusTextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // 6. Action Buttons: Copy & Native Direct Publish
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { copyFullPost() },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("copy_caption_full_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OpusDarkSurfaceHighlight
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = OpusTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "نسخ الكابشن",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary
                    )
                }

                Button(
                    onClick = {
                        val hashtags = captionResult?.hashtags?.joinToString(" ") ?: ""
                        val full = "$selectedHook\n\n$editableCaption\n\n$customCTA\n\n$hashtags"
                        onDirectPublishClick(selectedPlatform, full)
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(42.dp)
                        .testTag("direct_publish_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OpusPrimaryViolet
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Publish",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "نشر تلقائي مباشر",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
