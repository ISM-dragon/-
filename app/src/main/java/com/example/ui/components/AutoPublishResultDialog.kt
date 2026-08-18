package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AutoPublishResult
import com.example.data.model.Clip
import com.example.ui.theme.OpusBorder
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

@Composable
fun AutoPublishResultDialog(
    clip: Clip,
    publishResult: AutoPublishResult,
    onDismiss: () -> Unit,
    onOpenStudio: () -> Unit
) {
    val context = LocalContext.current
    val resultColor = if (publishResult.isSuccess) OpusViralEmerald else Color(0xFFF87171)
    val resultTitle = if (publishResult.isSuccess) "اكتمل تنفيذ النشر" else "تعذر تنفيذ النشر"

    val shareToApp = { packageName: String?, fallbackUrl: String? ->
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, clip.title)
            putExtra(Intent.EXTRA_TEXT, publishResult.postText.ifBlank { "${clip.title}\n\n${clip.transcript}\n\n#Viral #Shorts #ISM" })
            if (!packageName.isNullOrBlank()) {
                setPackage(packageName)
            }
        }
        try {
            context.startActivity(sendIntent)
        } catch (e: Exception) {
            val generalIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, clip.title)
                putExtra(Intent.EXTRA_TEXT, publishResult.postText.ifBlank { "${clip.title}\n\n${clip.transcript}\n\n#Viral #Shorts #ISM" })
            }
            if (!fallbackUrl.isNullOrBlank()) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))
                    context.startActivity(browserIntent)
                } catch (_: Exception) {
                    val chooser = Intent.createChooser(generalIntent, "نشر المقطع الفيروسي عبر:")
                    context.startActivity(chooser)
                }
            } else {
                val chooser = Intent.createChooser(generalIntent, "نشر المقطع الفيروسي عبر:")
                context.startActivity(chooser)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
                .testTag("auto_publish_result_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, resultColor.copy(alpha = 0.8f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header Badge
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
                                        listOf(resultColor.copy(alpha = 0.5f), OpusDarkSurfaceHighlight)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Auto Publish Result",
                                tint = resultColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = resultTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = OpusTextPrimary
                                )
                            )
                            Text(
                                text = publishResult.message,
                                fontSize = 11.sp,
                                color = resultColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("close_publish_result_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = OpusTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Clip Info Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(OpusDarkSurfaceHighlight)
                        .border(1.dp, OpusBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
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
                                    .background(OpusViralEmerald.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🔥 Virality Score ${clip.viralityScore}/100",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OpusViralEmerald
                                )
                            }
                            Text(
                                text = "${clip.durationSec} ثانية",
                                fontSize = 11.sp,
                                color = OpusTextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = clip.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Copy Status Notification
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(OpusPrimaryViolet.copy(alpha = 0.18f))
                        .border(1.dp, OpusVioletGlow.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Ready",
                            tint = OpusElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "النص والهاشتاغات جاهزة للمشاركة. تحقق من حالة كل منصة قبل اعتبار النشر مكتملًا.",
                            fontSize = 11.sp,
                            color = OpusTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (publishResult.webhookDispatched) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(resultColor.copy(alpha = 0.15f))
                            .border(1.dp, resultColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Webhook",
                                tint = resultColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تم إرسال حمولة البيانات إلى Webhook بنجاح.",
                                fontSize = 11.sp,
                                color = resultColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Direct 1-Tap Platform Launchers
                Text(
                    text = "إطلاق النشر الفوري بنقرة واحدة (1-Tap Launch):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusVioletGlow
                )
                Spacer(modifier = Modifier.height(8.dp))

                // TikTok
                LaunchPlatformButton(
                    title = "فتح ونشر على TikTok",
                    emoji = "🎵",
                    color = Color(0xFFFE2C55),
                    onClick = {
                        shareToApp("com.zhiliaoapp.musically", "https://www.tiktok.com/upload")
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // YouTube Shorts
                LaunchPlatformButton(
                    title = "فتح ونشر على YouTube Shorts",
                    emoji = "🔴",
                    color = Color(0xFFFF0000),
                    onClick = {
                        shareToApp("com.google.android.youtube", "https://studio.youtube.com")
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Instagram Reels
                LaunchPlatformButton(
                    title = "فتح ونشر على Instagram Reels",
                    emoji = "📸",
                    color = Color(0xFFE1306C),
                    onClick = {
                        shareToApp("com.instagram.android", "https://www.instagram.com")
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // X (Twitter)
                LaunchPlatformButton(
                    title = "فتح ونشر على X (Twitter)",
                    emoji = "✖️",
                    color = Color(0xFF1DA1F2),
                    onClick = {
                        shareToApp("com.twitter.android", "https://x.com/compose/tweet")
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Native Share Chooser
                LaunchPlatformButton(
                    title = "مشاركة لجميع التطبيقات (System Share)",
                    emoji = "🚀",
                    color = OpusElectricCyan,
                    onClick = {
                        shareToApp(null, null)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Viral Copy", publishResult.postText))
                            Toast.makeText(context, "تم نسخ النص والهاشتاغات مجدداً!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OpusElectricCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ النص", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenStudio()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Studio",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح الاستوديو", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchPlatformButton(
    title: String,
    emoji: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OpusDarkSurfaceVariant)
            .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open",
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
