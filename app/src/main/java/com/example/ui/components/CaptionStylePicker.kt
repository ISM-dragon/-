package com.example.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OpusBorder
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

data class CaptionPreset(
    val name: String,
    val sampleText: String,
    val previewGradient: List<Color>,
    val textColor: Color,
    val highlightColor: Color
)

val CaptionPresetsList = listOf(
    CaptionPreset(
        name = "Opus Neon",
        sampleText = "AI VIRAL 🚀",
        previewGradient = listOf(OpusPrimaryViolet, OpusElectricCyan),
        textColor = Color.White,
        highlightColor = OpusElectricCyan
    ),
    CaptionPreset(
        name = "MrBeast Yellow",
        sampleText = "DON'T MISS 🔥",
        previewGradient = listOf(Color(0xFF854D0E), OpusGold),
        textColor = Color.White,
        highlightColor = OpusGold
    ),
    CaptionPreset(
        name = "Ali Abdaal",
        sampleText = "FOCUS PROTOCOL 💡",
        previewGradient = listOf(Color(0xFF831843), OpusHotPink),
        textColor = Color(0xFFE2E8F0),
        highlightColor = OpusHotPink
    ),
    CaptionPreset(
        name = "Cyber Green",
        sampleText = "10X OUTPUT ⚡",
        previewGradient = listOf(Color(0xFF064E3B), OpusViralEmerald),
        textColor = Color.White,
        highlightColor = OpusViralEmerald
    ),
    CaptionPreset(
        name = "Hormozi Bold",
        sampleText = "SCALE FAST 💰",
        previewGradient = listOf(Color(0xFF312E81), OpusVioletGlow),
        textColor = Color.White,
        highlightColor = OpusVioletGlow
    )
)

@Composable
fun CaptionStylePicker(
    selectedTheme: String,
    onThemeSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().testTag("caption_style_picker")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dynamic Caption Templates",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )
            )
            Text(
                text = "Auto Emojis On",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = OpusViralEmerald
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(CaptionPresetsList) { preset ->
                val isSelected = selectedTheme == preset.name
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) OpusDarkSurfaceHighlight else OpusDarkSurface)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) OpusElectricCyan else OpusBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onThemeSelect(preset.name) }
                        .padding(10.dp)
                        .testTag("caption_preset_${preset.name.replace(" ", "_")}")
                ) {
                    Column {
                        // Mini preview banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(preset.previewGradient)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.sampleText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = preset.name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) OpusElectricCyan else OpusTextPrimary
                        )
                    }
                }
            }
        }
    }
}
