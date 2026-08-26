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
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clip
import com.example.domain.analysis.HookRetentionAnalysis
import com.example.domain.analysis.KeywordDensityAnalysis
import com.example.domain.analysis.PacingAnalysis
import com.example.domain.analysis.ViralityAnalysisResult
import com.example.domain.analysis.ViralityHeuristicEngine
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
 * High-craft, Machine Learning & Heuristic Virality Analysis Component.
 * Displays interactive deep-dives for Pacing, 0-3s Hook Retention, Keyword Density, and Viral Booster actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ViralityAnalysisCard(
    clip: Clip,
    modifier: Modifier = Modifier,
    onCompareClick: (() -> Unit)? = null
) {
    var analysisResult by remember(clip.id, clip.transcript) {
        mutableStateOf(ViralityHeuristicEngine.analyzeClip(clip))
    }

    var selectedAnalysisTab by remember { mutableIntStateOf(0) }
    var isAnalyzingNow by remember { mutableStateOf(false) }

    val score = analysisResult.overallScore
    var animatedScoreTarget by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(score) {
        animatedScoreTarget = score.toFloat()
    }

    val animatedScore by animateFloatAsState(
        targetValue = animatedScoreTarget,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "virality_score_anim"
    )

    val tierColor by animateColorAsState(
        targetValue = when {
            score >= 90 -> OpusViralEmerald
            score >= 80 -> OpusElectricCyan
            score >= 70 -> OpusGold
            else -> OpusHotPink
        },
        label = "virality_tier_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("virality_analysis_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Section: Engine Title & Live Re-calculate
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(tierColor.copy(alpha = 0.15f))
                            .border(1.dp, tierColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = "ML Engine",
                            tint = tierColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Opus Virality ML Engine™",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = OpusTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(tierColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Grade ${analysisResult.tierGrade}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = tierColor
                                )
                            }
                        }
                        Text(
                            text = "تحليل خوارزمي متقدم: السرعة • الخطاف • كثافة السيو",
                            fontSize = 11.sp,
                            color = OpusTextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = {
                        analysisResult = ViralityHeuristicEngine.analyzeClip(clip)
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(OpusDarkSurfaceHighlight)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Re-analyze",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Score Radar & Mini Takeaways Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Radial Arc Gauge Canvas
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .testTag("virality_radial_gauge_ml"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(136.dp)) {
                        val strokeWidth = 12.dp.toPx()
                        val arcPadding = strokeWidth / 2
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val startAngle = 140f
                        val totalSweep = 260f

                        // Background Track
                        drawArc(
                            color = Color(0xFF1E293B),
                            startAngle = startAngle,
                            sweepAngle = totalSweep,
                            useCenter = false,
                            topLeft = Offset(arcPadding, arcPadding),
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Active Animated Arc
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

                        // Marker Indicator
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
                            color = tierColor,
                            radius = 3.dp.toPx(),
                            center = Offset(markerX, markerY)
                        )
                    }

                    // Score Readout
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${animatedScore.toInt()}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = OpusTextPrimary,
                                fontSize = 36.sp
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

                // Mini Key Pillars
                Column(
                    modifier = Modifier.width(160.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricHighlightPill(
                        icon = Icons.Default.Speed,
                        label = "إيقاع السرعة",
                        value = "${analysisResult.pacing.wordsPerMinute.toInt()} WPM",
                        accentColor = OpusViralEmerald
                    )
                    MetricHighlightPill(
                        icon = Icons.Default.Timer,
                        label = "قوة الخطاف (0-3s)",
                        value = "${analysisResult.hookRetention.score}%",
                        accentColor = OpusElectricCyan
                    )
                    MetricHighlightPill(
                        icon = Icons.Default.Key,
                        label = "كثافة السيو",
                        value = "${analysisResult.keywordDensity.densityPercentage}%",
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
                        contentDescription = "AI Summary",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = analysisResult.summaryInsight,
                        fontSize = 12.sp,
                        color = OpusTextPrimary,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Selector for Detailed Heuristic Dimensions
            ScrollableTabRow(
                selectedTabIndex = selectedAnalysisTab,
                containerColor = OpusDarkSurfaceVariant,
                contentColor = OpusElectricCyan,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedAnalysisTab]),
                        color = OpusElectricCyan
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
            ) {
                val tabs = listOf(
                    "⚡ السرعة والإيقاع",
                    "🎣 منحنى الاحتفاظ",
                    "🔑 كلمات السيو",
                    "💡 نصائح التحسين"
                )
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedAnalysisTab == index,
                        onClick = { selectedAnalysisTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (selectedAnalysisTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedAnalysisTab == index) OpusElectricCyan else OpusTextSecondary
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tab Content
            when (selectedAnalysisTab) {
                0 -> PacingAnalysisView(analysisResult.pacing)
                1 -> HookRetentionCurveView(analysisResult.hookRetention)
                2 -> KeywordDensityView(analysisResult.keywordDensity)
                3 -> RecommendationsView(analysisResult.recommendations)
            }

            if (onCompareClick != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCompareClick,
                    modifier = Modifier.fillMaxWidth().testTag("open_comparison_from_analysis"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet)
                ) {
                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = "Compare",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مقارنة المقطع مع المنافسين ومقاطع المنصات",
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
private fun MetricHighlightPill(
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
private fun PacingAnalysisView(pacing: PacingAnalysis) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "معدل السرعة وسلاسة السرد (Pacing Cadence)",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )
            )
            Text(
                text = "${pacing.score}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = OpusViralEmerald
            )
        }

        LinearProgressIndicator(
            progress = { pacing.score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = OpusViralEmerald,
            trackColor = OpusDarkCanvas
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoStatBox(
                title = "سرعة الكلمات",
                value = "${pacing.wordsPerMinute.toInt()} WPM",
                subtitle = "المثالي: 160-195 WPM",
                modifier = Modifier.weight(1f)
            )
            InfoStatBox(
                title = "عدد الكلمات",
                value = "${pacing.totalWordCount} كلمة",
                subtitle = "في ${pacing.durationSec} ثانية",
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(OpusDarkSurfaceHighlight)
                .padding(10.dp)
        ) {
            Text(
                text = pacing.diagnosis,
                fontSize = 11.sp,
                color = OpusTextPrimary,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun HookRetentionCurveView(hook: HookRetentionAnalysis) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "منحنى الاحتفاظ المتوقع (Audience Retention Curve)",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )
            )
            Text(
                text = "${hook.score}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = OpusElectricCyan
            )
        }

        // Custom Visual Retention Curve Graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(OpusDarkCanvas)
                .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(94.dp)) {
                val points = hook.retentionCurve
                if (points.size >= 2) {
                    val w = size.width
                    val h = size.height
                    val maxTimestamp = points.last().timestampSec.coerceAtLeast(1f)

                    val path = Path()
                    val fillPath = Path()

                    points.forEachIndexed { i, pt ->
                        val x = (pt.timestampSec / maxTimestamp) * w
                        val y = h - ((pt.retentionPercent / 100f) * h)
                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    // Fill Gradient Area
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(
                                OpusElectricCyan.copy(alpha = 0.35f),
                                OpusElectricCyan.copy(alpha = 0.02f)
                            )
                        )
                    )

                    // Line Stroke
                    drawPath(
                        path = path,
                        color = OpusElectricCyan,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Data Points
                    points.forEach { pt ->
                        val x = (pt.timestampSec / maxTimestamp) * w
                        val y = h - ((pt.retentionPercent / 100f) * h)
                        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(x, y))
                        drawCircle(color = OpusElectricCyan, radius = 2.dp.toPx(), center = Offset(x, y))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            hook.retentionCurve.forEach { pt ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${pt.timestampSec.toInt()}s",
                        fontSize = 10.sp,
                        color = OpusTextSecondary
                    )
                    Text(
                        text = "${pt.retentionPercent}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusElectricCyan
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(OpusDarkSurfaceHighlight)
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = "نوع الخطاف المكتشف: ${hook.hookType}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusViralEmerald
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = hook.explanation,
                    fontSize = 11.sp,
                    color = OpusTextPrimary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordDensityView(keywords: KeywordDensityAnalysis) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "كثافة الكلمات المفتاحية ومطابقة السيو",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )
            )
            Text(
                text = "${keywords.score}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = OpusGold
            )
        }

        LinearProgressIndicator(
            progress = { keywords.score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = OpusGold,
            trackColor = OpusDarkCanvas
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoStatBox(
                title = "نسبة الكثافة",
                value = "${keywords.densityPercentage}%",
                subtitle = "النطاق الأمثل: 4-8%",
                modifier = Modifier.weight(1f)
            )
            InfoStatBox(
                title = "الكلمات الفيروسية",
                value = "${keywords.totalKeywordsCount} كلمة",
                subtitle = "تم استخراجها في النص",
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "الكلمات المحفزة للانتشار المكتشفة في المقطع:",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = OpusTextSecondary
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            keywords.detectedViralKeywords.forEach { kw ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OpusDarkSurfaceHighlight)
                        .border(1.dp, OpusGold.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${kw.word} (${kw.occurrences}x)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = OpusGold
                    )
                }
            }
        }

        Text(
            text = "الهاشتاغات المقترحة تلقائياً:",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = OpusTextSecondary
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            keywords.recommendedTags.forEach { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OpusElectricCyan.copy(alpha = 0.15f))
                        .border(1.dp, OpusElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = tag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OpusElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationsView(recommendations: List<com.example.domain.analysis.ViralityRecommendation>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "توصيات الذكاء الاصطناعي لرفع معدل الانتشار:",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = OpusTextPrimary
            )
        )

        recommendations.forEach { rec ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(OpusDarkSurfaceHighlight)
                    .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(OpusViralEmerald.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${rec.impactPoints}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = OpusViralEmerald
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = rec.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = rec.actionableStep,
                            fontSize = 11.sp,
                            color = OpusTextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoStatBox(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(OpusDarkSurfaceHighlight)
            .border(1.dp, OpusBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(text = title, fontSize = 10.sp, color = OpusTextSecondary)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OpusTextPrimary)
            Text(text = subtitle, fontSize = 9.sp, color = OpusElectricCyan)
        }
    }
}
