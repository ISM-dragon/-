package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun AiEditingCommandBar(
    onExecuteCommand: (String) -> Unit,
    isProcessing: Boolean = false,
    lastAiFeedback: String? = null
) {
    var commandText by remember { mutableStateOf("") }

    val quickCommands = listOf(
        "حذف الصمت والوقفات" to "Remove silence & dead air",
        "تسريع الإيقاع 1.2x" to "Make this faster and punchier",
        "توليد خطاف صادم" to "Create a high-curiosity hook",
        "تحويل إلى 9:16 تيك توك" to "Turn this into TikTok 9:16 layout",
        "إضافة كابشن حركي ملون" to "Add dynamic animated karaoke captions",
        "إبراز أهم كلمة" to "Highlight key takeaways with emojis"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, OpusPrimaryViolet.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(OpusPrimaryViolet.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "AI Editor",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "مساعد التحرير بالذكاء الاصطناعي (AI Editing Commands)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Prompt Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickCommands) { (chipAr, chipEn) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(OpusDarkSurfaceHighlight)
                            .border(1.dp, OpusBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                commandText = chipAr
                                onExecuteCommand(chipAr)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = chipAr,
                            fontSize = 10.sp,
                            color = OpusTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Custom Text Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    placeholder = {
                        Text("اكتب أمراً مثل: احذف الصمت، غير الخطاف، ركز على المتحدث...", fontSize = 11.sp, color = OpusTextSecondary)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OpusElectricCyan,
                        unfocusedBorderColor = OpusBorder,
                        focusedContainerColor = OpusDarkSurfaceVariant,
                        unfocusedContainerColor = OpusDarkSurfaceVariant,
                        focusedTextColor = OpusTextPrimary,
                        unfocusedTextColor = OpusTextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (commandText.isNotBlank() && !isProcessing) {
                            onExecuteCommand(commandText)
                        }
                    },
                    enabled = commandText.isNotBlank() && !isProcessing,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Execute", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // AI Feedback Result Box
            if (!lastAiFeedback.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(OpusViralEmerald.copy(alpha = 0.1f))
                        .border(1.dp, OpusViralEmerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "✨ تم تطبيق الأمر: $lastAiFeedback",
                        fontSize = 11.sp,
                        color = OpusViralEmerald,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
