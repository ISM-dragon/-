package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Clip
import com.example.data.model.DirectApiPublishLog
import com.example.data.model.DirectPlatformApiCredentials
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
fun DirectPublisherDialog(
    clip: Clip,
    repository: OpusRepository,
    initialPlatform: String = "TikTok",
    initialCaption: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val creds by repository.directApiCredentials.collectAsState()
    val recentLogs by repository.recentPublishLogs.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Live Direct Publish, 1: Platform API Keys, 2: Realtime Logs
    var selectedPlatform by remember { mutableStateOf(initialPlatform) }
    var captionText by remember {
        mutableStateOf(if (initialCaption.isNotBlank()) initialCaption else "${clip.title}\n\n${clip.transcript.take(160)}...\n\n#Viral #Shorts #Reels")
    }

    var isPublishing by remember { mutableStateOf(false) }
    var currentResultLog by remember { mutableStateOf<DirectApiPublishLog?>(null) }

    // API Key inputs
    var ytKey by remember(creds) { mutableStateOf(creds.youtubeApiKey) }
    var ytToken by remember(creds) { mutableStateOf(creds.youtubeBearerToken) }
    var ttToken by remember(creds) { mutableStateOf(creds.tiktokAccessToken) }
    var igToken by remember(creds) { mutableStateOf(creds.instagramAccessToken) }
    var igAccount by remember(creds) { mutableStateOf(creds.instagramAccountId) }
    var xToken by remember(creds) { mutableStateOf(creds.twitterBearerToken) }

    fun executeDirectPublish() {
        coroutineScope.launch {
            isPublishing = true
            val log = repository.publishDirectlyToPlatform(
                clip = clip,
                platform = selectedPlatform,
                customCaption = captionText
            )
            currentResultLog = log
            isPublishing = false
            Toast.makeText(
                context,
                if (log.isSuccess) "تم النشر المباشر عبر الـ API بنجاح! 🚀" else "فشل النشر: ${log.responseSummary}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun saveApiKeys() {
        coroutineScope.launch {
            repository.saveDirectApiCredentials(
                DirectPlatformApiCredentials(
                    youtubeApiKey = ytKey,
                    youtubeBearerToken = ytToken,
                    tiktokAccessToken = ttToken,
                    instagramAccessToken = igToken,
                    instagramAccountId = igAccount,
                    twitterBearerToken = xToken,
                    isDirectApiEnabled = true
                )
            )
            Toast.makeText(context, "تم حفظ مفاتيح المنصات بنجاح! 🔑", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
                .testTag("direct_publisher_dialog"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, OpusElectricCyan.copy(alpha = 0.8f))
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
                                    Brush.linearGradient(
                                        listOf(OpusElectricCyan, OpusPrimaryViolet)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Direct In-App API",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "النشر التلقائي المباشر (Direct API)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = OpusTextPrimary
                            )
                            Text(
                                text = "نشر داخلي عبر الـ API بدون تطبيقات أو أتمتة خارجية",
                                fontSize = 11.sp,
                                color = OpusElectricCyan
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("close_direct_pub_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = OpusTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = OpusDarkSurfaceVariant,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = OpusElectricCyan
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                ) {
                    listOf("نشر فوري عبر API", "إعداد مفاتيح API", "سجل الطلبات الحية").forEachIndexed { index, title ->
                        Tab(
                            selected = activeTab == index,
                            onClick = { activeTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (activeTab == index) OpusElectricCyan else OpusTextSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (activeTab) {
                    0 -> {
                        // Tab 0: Live Direct Publish
                        Text(
                            text = "اختر منصة النشر المباشر:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusVioletGlow
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val platforms = listOf(
                            "TikTok" to "🎵",
                            "YouTube Shorts" to "🔴",
                            "Instagram Reels" to "📸",
                            "X (Twitter)" to "✖️"
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            platforms.forEach { (platform, icon) ->
                                val isSelected = selectedPlatform == platform
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) OpusPrimaryViolet else OpusDarkSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) OpusElectricCyan else OpusBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedPlatform = platform }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = icon, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = platform,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else OpusTextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Caption Preview
                        Text(
                            text = "نص المنشور والهوك والهاشتاغات:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = captionText,
                            onValueChange = { captionText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .testTag("direct_publish_caption_input"),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = OpusTextPrimary),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OpusElectricCyan,
                                unfocusedBorderColor = OpusBorder,
                                focusedContainerColor = OpusDarkCanvas,
                                unfocusedContainerColor = OpusDarkCanvas
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live Result Card if published
                        val result = currentResultLog
                        if (result != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (result.isSuccess) OpusViralEmerald.copy(alpha = 0.15f) else OpusHotPink.copy(alpha = 0.15f))
                                    .border(1.dp, if (result.isSuccess) OpusViralEmerald else OpusHotPink, RoundedCornerShape(12.dp))
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
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Status",
                                                tint = if (result.isSuccess) OpusViralEmerald else OpusHotPink,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (result.isSuccess) "استجابة API: HTTP ${result.httpCode} OK" else "استجابة API: HTTP ${result.httpCode}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = OpusTextPrimary
                                            )
                                        }

                                        Text(
                                            text = result.platform,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OpusElectricCyan
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = result.responseSummary,
                                        fontSize = 11.sp,
                                        color = OpusTextSecondary
                                    )

                                    if (result.postUrl.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(OpusDarkSurfaceHighlight)
                                                .clickable {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.postUrl))
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "الرابط: ${result.postUrl}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = "Open",
                                                tint = OpusElectricCyan,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "عرض الفيديو المنشور: ${result.postUrl}",
                                                fontSize = 10.sp,
                                                color = OpusElectricCyan
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Send Button
                        Button(
                            onClick = { executeDirectPublish() },
                            enabled = !isPublishing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("execute_direct_publish_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OpusElectricCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            if (isPublishing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "جاري الإرسال عبر $selectedPlatform API...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إرسال ونشر مباشر فوراً (Direct API Call)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    1 -> {
                        // Tab 1: Platform API Keys
                        Text(
                            text = "مفاتيح المنصات المباشرة (Direct Platform API Keys):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusVioletGlow
                        )
                        Text(
                            text = "أدخل مفتاح API أو Token المنصة لتفعيل النشر المباشر الحقيقي بدون أي وسيط:",
                            fontSize = 11.sp,
                            color = OpusTextSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // YouTube Data API
                        Text(text = "🔴 YouTube Shorts / Data API v3 (Bearer Token أو API Key):", fontSize = 11.sp, color = OpusTextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = ytToken,
                            onValueChange = { ytToken = it },
                            placeholder = { Text("ya29.a0Ac... (OAuth Bearer) أو AIzaSy...", fontSize = 11.sp, color = OpusTextSecondary.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth().testTag("yt_token_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OpusElectricCyan, unfocusedBorderColor = OpusBorder, focusedContainerColor = OpusDarkCanvas, unfocusedContainerColor = OpusDarkCanvas)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // TikTok API
                        Text(text = "🎵 TikTok Open API Access Token:", fontSize = 11.sp, color = OpusTextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = ttToken,
                            onValueChange = { ttToken = it },
                            placeholder = { Text("act.xxxxxxxxxxxxxxxx...", fontSize = 11.sp, color = OpusTextSecondary.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth().testTag("tt_token_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OpusElectricCyan, unfocusedBorderColor = OpusBorder, focusedContainerColor = OpusDarkCanvas, unfocusedContainerColor = OpusDarkCanvas)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Instagram Graph API
                        Text(text = "📸 Instagram Reels / Graph API Access Token:", fontSize = 11.sp, color = OpusTextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = igToken,
                            onValueChange = { igToken = it },
                            placeholder = { Text("EAAGxxxxxxxxxxxx...", fontSize = 11.sp, color = OpusTextSecondary.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth().testTag("ig_token_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OpusElectricCyan, unfocusedBorderColor = OpusBorder, focusedContainerColor = OpusDarkCanvas, unfocusedContainerColor = OpusDarkCanvas)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // X (Twitter) Bearer Token
                        Text(text = "✖️ X (Twitter) API v2 Bearer Token:", fontSize = 11.sp, color = OpusTextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = xToken,
                            onValueChange = { xToken = it },
                            placeholder = { Text("AAAAAAAAAAAAAAAAAAAAA...", fontSize = 11.sp, color = OpusTextSecondary.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth().testTag("x_token_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OpusElectricCyan, unfocusedBorderColor = OpusBorder, focusedContainerColor = OpusDarkCanvas, unfocusedContainerColor = OpusDarkCanvas)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { saveApiKeys() },
                            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("save_platform_keys_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet)
                        ) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "حفظ مفاتيح المنصات المباشرة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    2 -> {
                        // Tab 2: Realtime Logs
                        Text(
                            text = "سجل استجابات وطلبات الـ API المباشرة:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusElectricCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (recentLogs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OpusDarkSurfaceVariant)
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لا توجد طلبات سابقة بعد. قم بالنشر الآن لمشاهدة الاستجابة الحية!",
                                    fontSize = 11.sp,
                                    color = OpusTextSecondary
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                recentLogs.forEach { logItem ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(OpusDarkCanvas)
                                            .border(1.dp, if (logItem.isSuccess) OpusViralEmerald.copy(alpha = 0.5f) else OpusHotPink.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${logItem.platform} • HTTP ${logItem.httpCode}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (logItem.isSuccess) OpusViralEmerald else OpusHotPink
                                                )
                                                Text(
                                                    text = if (logItem.isSuccess) "نجاح 200 OK" else "فشل",
                                                    fontSize = 10.sp,
                                                    color = OpusTextSecondary
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "Endpoint: ${logItem.endpointUrl}",
                                                fontSize = 9.sp,
                                                color = OpusTextSecondary,
                                                fontFamily = FontFamily.Monospace
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = logItem.rawPayload,
                                                fontSize = 10.sp,
                                                color = OpusElectricCyan,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 3
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
