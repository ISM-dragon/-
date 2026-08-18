package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-craft Virality Score Component displaying AI analysis output
 * using an animated radial gauge, multi-level progress meters, and benchmark insights.
 */
@Composable
fun ViralityScoreGauge(
    score: Int,
    modifier: Modifier = Modifier,
    clip: Clip? = null,
    onCompareClick: (() -> Unit)? = null,
    showSubmetrics: Boolean = true,
    showActionButtons: Boolean = true
) {
    var animatedScoreTarget by remember { mutableFloatStateOf(0f) }
    var isExpandedBreakdown by remember { mutableStateOf(true) }

    LaunchedEffect(score) {
        animatedScoreTarget = score.toFloat()
    }

    val animatedScore by animateFloatAsState(
        targetValue = animatedScoreTarget,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "virality_gauge_anim"
    )

    val viralityColor by animateColorAsState(
        targetValue = when {
            score >= 90 -> OpusViralEmerald
            score >= 80 -> OpusElectricCyan
            score >= 70 -> OpusGold
            else -> OpusHotPink
        },
        label = "virality_color_anim"
    )

    val tierLabel = when {
        score >= 95 -> "🔥 TOP 1% ALGORITHM TIER"
        score >= 90 -> "⚡ ULTRA VIRAL POTENTIAL"
        score >= 80 -> "🚀 HIGH ENGAGEMENT SWEETSPOT"
        score >= 70 -> "📈 ABOVE AVERAGE MOMENTUM"
        else -> "💡 OPTIMIZATION NEEDED"
    }

    val viralitySummary = when {
        score >= 90 -> "الذكاء الاصطناعي يتوقع وصول هذا المقطع إلى قائمة المقاطع الرائجة (Trending) بفضل قوة الخطاف الافتتاحي ومعدل الاحتفاظ السريع."
        score >= 80 -> "المقطع يمتلك تماسكاً ممتازاً وسرعة سردية ملائمة جداً لخوارزميات TikTok و Shorts مع إمكانية تحسين الـ B-Roll."
        else -> "الخطاف جيد لكن يتطلب تسريع الثواني الثلاث الأولى وإضافة مؤثرات صوتية لتعزيز الاحتفاظ."
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("virality_score_gauge_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Title & Algorithm Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(viralityColor.copy(alpha = 0.15f))
                            .border(1.dp, viralityColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Virality Gauge",
                            tint = viralityColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Opus Virality Score™",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                        )
                        Text(
                            text = "AI Algorithm Performance Engine",
                            fontSize = 11.sp,
                            color = OpusTextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(viralityColor.copy(alpha = 0.15f))
                        .border(1.dp, viralityColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tierLabel,
                        color = viralityColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Visual: Radial Arc Gauge + Score Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Radial Arc Gauge Canvas
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .testTag("virality_radial_gauge"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(136.dp)) {
                        val strokeWidth = 12.dp.toPx()
                        val arcPadding = strokeWidth / 2
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val startAngle = 140f
                        val totalSweep = 260f

                        // Background Track Arc
                        drawArc(
                            color = Color(0xFF1E293B),
                            startAngle = startAngle,
                            sweepAngle = totalSweep,
                            useCenter = false,
                            topLeft = Offset(arcPadding, arcPadding),
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Active Animated Progress Arc
                        val currentSweep = (animatedScore.coerceIn(0f, 100f) / 100f) * totalSweep
                        val gradientBrush = Brush.sweepGradient(
                            listOf(
                                OpusElectricCyan,
                                OpusViralEmerald,
                                OpusGold,
                                OpusHotPink,
                                OpusElectricCyan
                            )
                        )

                        drawArc(
                            brush = gradientBrush,
                            startAngle = startAngle,
                            sweepAngle = currentSweep,
                            useCenter = false,
                            topLeft = Offset(arcPadding, arcPadding),
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Needle / Tip Marker Indicator
                        val angleRad = Math.toRadians((startAngle + currentSweep).toDouble())
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val radius = (size.width - strokeWidth) / 2
                        val markerX = centerX + (radius * cos(angleRad)).toFloat()
                        val markerY = centerY + (radius * sin(angleRad)).toFloat()

                        drawCircle(
                            color = Color.White,
                            radius = 6.dp.toPx(),
                            center = Offset(markerX, markerY)
                        )
                        drawCircle(
                            color = viralityColor,
                            radius = 3.dp.toPx(),
                            center = Offset(markerX, markerY)
                        )
                    }

                    // Central Readout
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${animatedScore.toInt()}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = OpusTextPrimary,
                                fontSize = 34.sp
                            )
                        )
                        Text(
                            text = "out of 100",
                            fontSize = 10.sp,
                            color = OpusTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Mini Key Takeaways
                Column(
                    modifier = Modifier.width(160.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScoreHighlightBadge(
                        icon = Icons.Default.Timer,
                        label = "Hook Speed",
                        value = "< 2.3s",
                        accentColor = OpusViralEmerald
                    )
                    ScoreHighlightBadge(
                        icon = Icons.Default.TrendingUp,
                        label = "Retention Prob.",
                        value = "${(score * 0.94).toInt()}%",
                        accentColor = OpusElectricCyan
                    )
                    ScoreHighlightBadge(
                        icon = Icons.Default.EmojiEvents,
                        label = "Viral Rank",
                        value = if (score >= 90) "Top #1 Pick" else "Tier A+",
                        accentColor = OpusGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AI Assessment Summary Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OpusDarkSurfaceHighlight)
                    .border(1.dp, OpusBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Analysis",
                        tint = OpusElectricCyan,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = clip?.hookExplanation?.ifBlank { viralitySummary } ?: viralitySummary,
                        fontSize = 12.sp,
                        color = OpusTextPrimary,
                        lineHeight = 17.sp
                    )
                }
            }

            if (showSubmetrics) {
                Spacer(modifier = Modifier.height(16.dp))

                // Section Title with toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpandedBreakdown = !isExpandedBreakdown },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Submetrics",
                            tint = OpusPrimaryViolet,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Psychological Sub-Metrics Breakdown",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                        )
                    }

                    Text(
                        text = if (isExpandedBreakdown) "إخفاء التفاصيل" else "عرض التفاصيل",
                        fontSize = 11.sp,
                        color = OpusElectricCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = isExpandedBreakdown) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val hookVal = clip?.hookScore ?: (score + 2).coerceIn(60, 99)
                        val retentionVal = clip?.retentionScore ?: (score - 4).coerceIn(60, 99)
                        val emotionalVal = clip?.emotionalScore ?: (score - 2).coerceIn(60, 99)
                        val shareVal = clip?.shareabilityScore ?: (score + 1).coerceIn(60, 99)
                        val punchlineVal = clip?.punchlineScore ?: (score - 3).coerceIn(60, 99)

                        SubmetricProgressBar(
                            label = "قوة الخطاف (0-3s Hook Power)",
                            score = hookVal,
                            color = OpusViralEmerald,
                            description = "جاذبية المشهد الأول وحبس انتباه المشاهد الفوري"
                        )

                        SubmetricProgressBar(
                            label = "منحنى الاحتفاظ (Retention Velocity)",
                            score = retentionVal,
                            color = OpusElectricCyan,
                            description = "الحفاظ على المشاهدة دون تخطي حتى نهاية المقطع"
                        )

                        SubmetricProgressBar(
                            label = "الأثر العاطفي والفضول (Curiosity Gap)",
                            score = emotionalVal,
                            color = OpusHotPink,
                            description = "تحفيز الدوبامين والشغف لمعرفة النهاية"
                        )

                        SubmetricProgressBar(
                            label = "قابلية المشاركة والحفظ (Share & Save Rate)",
                            score = shareVal,
                            color = OpusGold,
                            description = "احتمالية إرسال المقطع للأصدقاء وحفظه في المفضلة"
                        )

                        SubmetricProgressBar(
                            label = "قوة القفلة والخاتمة (Punchline Climax)",
                            score = punchlineVal,
                            color = OpusVioletGlow,
                            description = "وضوح الفائدة النهائية أو الإثارة في ختام المقطع"
                        )
                    }
                }
            }

            if (showActionButtons && onCompareClick != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onCompareClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_side_by_side_comparison_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OpusPrimaryViolet
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = "Compare Side-by-Side",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مقارنة المقطع وجهاً لوجه مع المنافسين (Side-by-Side)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreHighlightBadge(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OpusDarkSurfaceHighlight)
            .border(1.dp, OpusBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = OpusTextSecondary
            )
        }
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}

@Composable
private fun SubmetricProgressBar(
    label: String,
    score: Int,
    color: Color,
    description: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "submetric_progress"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = OpusTextPrimary
            )
            Text(
                text = "$score%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(OpusDarkCanvas)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                color.copy(alpha = 0.6f),
                                color
                            )
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = description,
            fontSize = 10.sp,
            color = OpusTextSecondary,
            lineHeight = 13.sp
        )
    }
}
