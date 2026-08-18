package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CreatorProfile
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatorProfileDialog(
    initialProfile: CreatorProfile,
    onSaveProfile: (CreatorProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var language by remember { mutableStateOf(initialProfile.primaryLanguage) }
    var category by remember { mutableStateOf(initialProfile.contentCategory) }
    var audience by remember { mutableStateOf(initialProfile.targetAudience) }
    var captionTheme by remember { mutableStateOf(initialProfile.captionTheme) }
    var exportPreset by remember { mutableStateOf(initialProfile.exportPreset) }
    var silenceAggressiveness by remember { mutableFloatStateOf(initialProfile.silenceRemovalAggressiveness) }
    var autoEmojis by remember { mutableStateOf(initialProfile.includeEmojisInCaptions) }
    var autoHighlight by remember { mutableStateOf(initialProfile.autoHighlightKeywords) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, OpusBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = OpusDarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(OpusPrimaryViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Profile", tint = OpusElectricCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "ملف منشئ المحتوى (Creator Profile)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OpusTextPrimary)
                        Text(text = "يساعد الذكاء الاصطناعي على تخصيص المقاطع والخطافات لجمهورك", fontSize = 10.sp, color = OpusTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Language Choice
                Text(text = "اللغة الأساسية للمحتوى (RTL/LTR)", fontSize = 11.sp, color = OpusTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ar" to "العربية (RTL)", "en" to "English", "fr" to "Français").forEach { (code, label) ->
                        val isSelected = language == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) OpusElectricCyan.copy(alpha = 0.2f) else OpusDarkSurfaceVariant)
                                .border(1.dp, if (isSelected) OpusElectricCyan else OpusBorder, RoundedCornerShape(10.dp))
                                .clickable { language = code }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) OpusElectricCyan else OpusTextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category & Niche
                Text(text = "تصنيف المحتوى والنيش (Category)", fontSize = 11.sp, color = OpusTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("تعليم وتقنية", "بودكاست ومقابلات", "أعمال واستثمار", "ألعاب وجيمينج", "كوميديا وترفيه", "تحفيز وتطوير ذات").forEach { cat ->
                        val isSelected = category == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) OpusPrimaryViolet.copy(alpha = 0.3f) else OpusDarkSurfaceHighlight)
                                .border(1.dp, if (isSelected) OpusPrimaryViolet else OpusBorder, RoundedCornerShape(8.dp))
                                .clickable { category = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = cat, fontSize = 10.sp, color = if (isSelected) OpusTextPrimary else OpusTextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Silence Removal Aggressiveness
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "حساسية حذف الصمت والحشو", fontSize = 11.sp, color = OpusTextSecondary)
                    Text(text = "${(silenceAggressiveness * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OpusElectricCyan)
                }
                Slider(
                    value = silenceAggressiveness,
                    onValueChange = { silenceAggressiveness = it },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = OpusElectricCyan,
                        activeTrackColor = OpusElectricCyan,
                        inactiveTrackColor = OpusDarkSurfaceHighlight
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Toggles
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "إبراز الكلمات المفتاحية بألوان حركية", fontSize = 11.sp, color = OpusTextPrimary)
                    Switch(
                        checked = autoHighlight,
                        onCheckedChange = { autoHighlight = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = OpusElectricCyan, checkedTrackColor = OpusPrimaryViolet)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Action
                Button(
                    onClick = {
                        val updated = initialProfile.copy(
                            primaryLanguage = language,
                            contentCategory = category,
                            targetAudience = audience,
                            captionTheme = captionTheme,
                            exportPreset = exportPreset,
                            silenceRemovalAggressiveness = silenceAggressiveness,
                            includeEmojisInCaptions = autoEmojis,
                            autoHighlightKeywords = autoHighlight
                        )
                        onSaveProfile(updated)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = OpusElectricCyan)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "حفظ إعدادات الملف", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}
