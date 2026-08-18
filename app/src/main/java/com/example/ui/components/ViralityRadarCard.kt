package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clip
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

@Composable
fun ViralityRadarCard(
    clip: Clip,
    modifier: Modifier = Modifier
) {
    val viralityColor = when {
        clip.viralityScore >= 90 -> OpusViralEmerald
        clip.viralityScore >= 80 -> OpusElectricCyan
        clip.viralityScore >= 70 -> OpusGold
        else -> OpusVioletGlow
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("virality_radar_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Virality Analysis",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Opus Virality Score™",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(viralityColor.copy(alpha = 0.15f))
                        .border(1.dp, viralityColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (clip.viralityScore >= 90) "🔥 High Viral Potential" else "📈 Solid Performer",
                        color = viralityColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Score Circle & Submetrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Score Display
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    viralityColor.copy(alpha = 0.25f),
                                    OpusDarkSurfaceVariant
                                )
                            )
                        )
                        .border(2.5.dp, viralityColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${clip.viralityScore}",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = OpusTextPrimary,
                                fontSize = 30.sp
                            )
                        )
                        Text(
                            text = "/ 100",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = OpusTextSecondary,
                                fontSize = 9.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Breakdown Bars
                Column(modifier = Modifier.weight(1f)) {
                    ScoreBarItem(label = "Hook Strength (0-3s)", score = clip.hookScore, color = OpusElectricCyan)
                    Spacer(modifier = Modifier.height(6.dp))
                    ScoreBarItem(label = "Retention Probability", score = clip.retentionScore, color = OpusVioletGlow)
                    Spacer(modifier = Modifier.height(6.dp))
                    ScoreBarItem(label = "Emotional Arc", score = clip.emotionalScore, color = OpusHotPink)
                    Spacer(modifier = Modifier.height(6.dp))
                    ScoreBarItem(label = "Shareability Index", score = clip.shareabilityScore, color = OpusViralEmerald)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AI Insight box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(OpusDarkSurfaceVariant)
                    .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "AI Psychology",
                        tint = OpusVioletGlow,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Why AI Selected This Moment:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OpusVioletGlow
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = clip.hookExplanation,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OpusTextPrimary.copy(alpha = 0.9f),
                                lineHeight = 16.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBarItem(
    label: String,
    score: Int,
    color: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 800),
        label = "score_bar"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = OpusTextSecondary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$score%",
                fontSize = 11.sp,
                color = OpusTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = OpusDarkSurfaceHighlight,
            strokeCap = StrokeCap.Round
        )
    }
}
