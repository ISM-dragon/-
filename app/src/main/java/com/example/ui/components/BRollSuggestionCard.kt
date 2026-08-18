package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BRollIdea
import com.example.ui.theme.OpusBorder
import com.example.ui.theme.OpusDarkSurface
import com.example.ui.theme.OpusDarkSurfaceVariant
import com.example.ui.theme.OpusElectricCyan
import com.example.ui.theme.OpusGold
import com.example.ui.theme.OpusHotPink
import com.example.ui.theme.OpusPrimaryViolet
import com.example.ui.theme.OpusTextPrimary
import com.example.ui.theme.OpusTextSecondary
import com.example.ui.theme.OpusVioletGlow
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Composable
fun BRollSuggestionCard(
    bRollPromptsJson: String,
    modifier: Modifier = Modifier
) {
    val moshi = remember { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    val bRollList = remember(bRollPromptsJson) {
        try {
            val adapter = moshi.adapter<List<BRollIdea>>(
                Types.newParameterizedType(List::class.java, BRollIdea::class.java)
            )
            adapter.fromJson(bRollPromptsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("broll_suggestions_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "AI B-Roll",
                        tint = OpusHotPink,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI B-Roll Cutaway Ideas",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OpusHotPink.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${bRollList.size} Prompts",
                        color = OpusHotPink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (bRollList.isEmpty()) {
                Text(
                    text = "No B-roll suggestions available for this segment.",
                    color = OpusTextSecondary,
                    fontSize = 12.sp
                )
            } else {
                bRollList.forEachIndexed { index, broll ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(OpusDarkSurfaceVariant)
                            .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. ${broll.title}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = OpusTextPrimary
                                    )
                                )
                                Text(
                                    text = "@ 0:${broll.timestampSec.toString().padStart(2, '0')}s",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OpusElectricCyan
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Visual: \"${broll.visualPrompt}\"",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OpusTextSecondary,
                                    lineHeight = 16.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "SFX",
                                    tint = OpusGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SFX: ${broll.soundEffect}",
                                    fontSize = 11.sp,
                                    color = OpusGold,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
