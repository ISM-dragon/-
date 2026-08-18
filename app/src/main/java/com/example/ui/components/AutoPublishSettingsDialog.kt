package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AutoPublishConfig
import com.example.data.repository.OpusRepository
import com.example.ui.theme.OpusBorder
import com.example.ui.theme.OpusDarkCanvas
import com.example.ui.theme.OpusDarkSurface
import com.example.ui.theme.OpusDarkSurfaceHighlight
import com.example.ui.theme.OpusDarkSurfaceVariant
import com.example.ui.theme.OpusElectricCyan
import com.example.ui.theme.OpusGold
import com.example.ui.theme.OpusPrimaryViolet
import com.example.ui.theme.OpusTextPrimary
import com.example.ui.theme.OpusTextSecondary
import com.example.ui.theme.OpusViralEmerald
import com.example.ui.theme.OpusVioletGlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoPublishSettingsDialog(
    repository: OpusRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentConfig by repository.autoPublishConfig.collectAsState()
    val directCreds by repository.directApiCredentials.collectAsState()

    var isEnabled by remember { mutableStateOf(currentConfig.isEnabled) }
    var selectedPlatforms by remember { mutableStateOf(currentConfig.targetPlatforms.toMutableSet()) }
    var autoOpenShareSheet by remember { mutableStateOf(currentConfig.autoOpenShareSheet) }
    var autoCopyCaption by remember { mutableStateOf(currentConfig.autoCopyCaption) }
    var webhookUrl by remember { mutableStateOf(currentConfig.webhookUrl) }
    var selectedSlot by remember { mutableStateOf(currentConfig.scheduledSlot) }

    // Direct Platform API credentials
    var ytKey by remember(directCreds) { mutableStateOf(directCreds.youtubeApiKey) }
    var ytToken by remember(directCreds) { mutableStateOf(directCreds.youtubeBearerToken) }
    var ttToken by remember(directCreds) { mutableStateOf(directCreds.tiktokAccessToken) }
    var igToken by remember(directCreds) { mutableStateOf(directCreds.instagramAccessToken) }
    var xToken by remember(directCreds) { mutableStateOf(directCreds.twitterBearerToken) }
    var isDirectApiActive by remember(directCreds) { mutableStateOf(directCreds.isDirectApiEnabled) }

    val availablePlatforms = listOf(
        "TikTok" to "🎵",
        "YouTube Shorts" to "🔴",
        "Instagram Reels" to "📸",
        "X (Twitter)" to "✖️",
        "LinkedIn" to "💼"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
                .testTag("auto_publish_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, OpusVioletGlow.copy(alpha = 0.8f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(OpusPrimaryViolet, OpusDarkSurfaceHighlight)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Auto Publish",
                                tint = OpusElectricCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "خاصية النشر التلقائي",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = OpusTextPrimary
                                )
                            )
                            Text(
                                text = "Auto-Publish Pipeline",
                                fontSize = 11.sp,
                                color = OpusElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("close_auto_publish_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = OpusTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Master Toggle Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isEnabled) OpusPrimaryViolet.copy(alpha = 0.25f) else OpusDarkSurfaceVariant)
                        .border(
                            1.dp,
                            if (isEnabled) OpusElectricCyan else OpusBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Active",
                                    tint = if (isEnabled) OpusGold else OpusTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تفعيل النشر التلقائي فور انتهاء الفيديو",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OpusTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "إرسال المقاطع الأكثر فيروسية ومشاركتها مباشرة بمجرد اكتمال التوليد",
                                fontSize = 11.sp,
                                color = OpusTextSecondary
                            )
                        }

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = OpusElectricCyan,
                                uncheckedThumbColor = OpusTextSecondary,
                                uncheckedTrackColor = OpusDarkSurfaceHighlight
                            ),
                            modifier = Modifier.testTag("auto_publish_toggle_switch")
                        )
                    }
                }

                AnimatedVisibility(visible = isEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Target Platforms Selector
                        Text(
                            text = "منصات النشر المستهدفة:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusVioletGlow
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            availablePlatforms.forEach { (platform, emoji) ->
                                val isSelected = selectedPlatforms.contains(platform)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) OpusDarkSurfaceHighlight else OpusDarkSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) OpusViralEmerald else OpusBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            if (isSelected) {
                                                if (selectedPlatforms.size > 1) {
                                                    selectedPlatforms = (selectedPlatforms - platform).toMutableSet()
                                                } else {
                                                    Toast.makeText(context, "يجب تحديد منصة واحدة على الأقل", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                selectedPlatforms = (selectedPlatforms + platform).toMutableSet()
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = emoji, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = platform,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) OpusTextPrimary else OpusTextSecondary
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = OpusViralEmerald,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Trigger Options
                        Text(
                            text = "إجراءات النشر التلقائي الذكية:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusVioletGlow
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 1: Auto open share sheet
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(OpusDarkSurfaceVariant)
                                .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                                .clickable { autoOpenShareSheet = !autoOpenShareSheet }
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share Sheet",
                                        tint = OpusElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "فتح نافذة المشاركة للنظام تلقائياً (Share Sheet)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OpusTextPrimary
                                        )
                                        Text(
                                            text = "إطلاق خيارات النشر الفوري للتطبيقات المثبتة بالجهاز",
                                            fontSize = 10.sp,
                                            color = OpusTextSecondary
                                        )
                                    }
                                }

                                Switch(
                                    checked = autoOpenShareSheet,
                                    onCheckedChange = { autoOpenShareSheet = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = OpusViralEmerald
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 2: Auto copy caption
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(OpusDarkSurfaceVariant)
                                .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                                .clickable { autoCopyCaption = !autoCopyCaption }
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Caption",
                                        tint = OpusVioletGlow,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "نسخ الكابشن والهوك والهاشتاغات للحافظة فوراً",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OpusTextPrimary
                                        )
                                        Text(
                                            text = "جاهز للصق بنقرة واحدة في TikTok و Shorts و Reels",
                                            fontSize = 10.sp,
                                            color = OpusTextSecondary
                                        )
                                    }
                                }

                                Switch(
                                    checked = autoCopyCaption,
                                    onCheckedChange = { autoCopyCaption = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = OpusViralEmerald
                                    )
                                )
                            }
                        }

                        // Direct In-App API Platform Keys Configuration
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(OpusDarkSurfaceHighlight)
                                .border(1.dp, OpusElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = "Direct API",
                                            tint = OpusElectricCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = "النشر التلقائي المباشر (Direct API Mode)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = OpusTextPrimary
                                            )
                                            Text(
                                                text = "نشر داخلي عبر الـ API بدون تطبيقات خارجية",
                                                fontSize = 10.sp,
                                                color = OpusElectricCyan
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = isDirectApiActive,
                                        onCheckedChange = { isDirectApiActive = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = OpusElectricCyan
                                        )
                                    )
                                }

                                if (isDirectApiActive) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(text = "🔴 YouTube Bearer Token / API Key:", fontSize = 11.sp, color = OpusTextPrimary, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    OutlinedTextField(
                                        value = ytToken,
                                        onValueChange = { ytToken = it },
                                        placeholder = { Text("ya29... أو AIzaSy...", fontSize = 10.sp, color = OpusTextSecondary.copy(alpha = 0.5f)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OpusElectricCyan, unfocusedBorderColor = OpusBorder, focusedContainerColor = OpusDarkCanvas, unfocusedContainerColor = OpusDarkCanvas),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "🎵 TikTok Open API Access Token:", fontSize = 11.sp, color = OpusTextPrimary, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    OutlinedTextField(
                                        value = ttToken,
                                        onValueChange = { ttToken = it },
                                        placeholder = { Text("act.xxxxxxxxxx...", fontSize = 10.sp, color = OpusTextSecondary.copy(alpha = 0.5f)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OpusElectricCyan, unfocusedBorderColor = OpusBorder, focusedContainerColor = OpusDarkCanvas, unfocusedContainerColor = OpusDarkCanvas),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "📸 Instagram Reels Access Token:", fontSize = 11.sp, color = OpusTextPrimary, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    OutlinedTextField(
                                        value = igToken,
                                        onValueChange = { igToken = it },
                                        placeholder = { Text("EAAGxxxxxxxxxx...", fontSize = 10.sp, color = OpusTextSecondary.copy(alpha = 0.5f)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OpusElectricCyan, unfocusedBorderColor = OpusBorder, focusedContainerColor = OpusDarkCanvas, unfocusedContainerColor = OpusDarkCanvas),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Webhook automation (Make / Zapier / Buffer / n8n)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Http,
                                contentDescription = "Webhook",
                                tint = OpusElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "رابط Webhook للأتمتة (Make / Zapier / Buffer / n8n):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = webhookUrl,
                            onValueChange = { webhookUrl = it },
                            placeholder = {
                                Text(
                                    text = "https://hook.make.com/your-endpoint-or-zapier",
                                    fontSize = 12.sp,
                                    color = OpusTextSecondary.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webhook_url_input"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = OpusDarkSurfaceVariant,
                                unfocusedContainerColor = OpusDarkSurfaceVariant,
                                focusedBorderColor = OpusElectricCyan,
                                unfocusedBorderColor = OpusBorder,
                                focusedTextColor = OpusTextPrimary,
                                unfocusedTextColor = OpusTextPrimary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "💡 اختياري: عند اكتمال الفيديو سيتم إرسال حمولة JSON تحتوي على عنوان الهوك والترجمة ورابط المقطع إلى خط الأتمتة الخاص بك.",
                            fontSize = 10.sp,
                            color = OpusTextSecondary,
                            lineHeight = 14.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Scheduling slots
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Schedule",
                                tint = OpusGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "توقيت النشر المفضل:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        listOf(
                            "Instant (فوري بعد انتهاء التوليد)",
                            "Peak Evening (وقت الذروة 6:00 PM)",
                            "Peak Night (وقت الذروة 9:00 PM)"
                        ).forEach { slot ->
                            val isSel = selectedSlot == slot
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) OpusDarkSurfaceHighlight else OpusDarkSurfaceVariant)
                                    .border(1.dp, if (isSel) OpusElectricCyan else OpusBorder, RoundedCornerShape(8.dp))
                                    .clickable { selectedSlot = slot }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = slot,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) OpusElectricCyan else OpusTextSecondary
                                    )
                                    if (isSel) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = OpusElectricCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Save & Apply
                Button(
                    onClick = {
                        val newConfig = AutoPublishConfig(
                            isEnabled = isEnabled,
                            targetPlatforms = selectedPlatforms,
                            autoOpenShareSheet = autoOpenShareSheet,
                            autoCopyCaption = autoCopyCaption,
                            webhookUrl = webhookUrl.trim(),
                            scheduledSlot = selectedSlot
                        )
                        val newCreds = com.example.data.model.DirectPlatformApiCredentials(
                            youtubeApiKey = ytKey.trim(),
                            youtubeBearerToken = ytToken.trim(),
                            tiktokAccessToken = ttToken.trim(),
                            instagramAccessToken = igToken.trim(),
                            instagramAccountId = directCreds.instagramAccountId,
                            twitterBearerToken = xToken.trim(),
                            isDirectApiEnabled = isDirectApiActive
                        )
                        coroutineScope.launch {
                            repository.saveAutoPublishConfig(newConfig)
                            repository.saveDirectApiCredentials(newCreds)
                            Toast.makeText(context, "تم حفظ وتفعيل إعدادات النشر المباشر بنجاح!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("save_auto_publish_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "حفظ وتفعيل إعدادات النشر التلقائي",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
