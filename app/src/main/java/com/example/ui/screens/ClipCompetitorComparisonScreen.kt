package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clip
import com.example.data.model.CompetitorVideoPerformance
import com.example.data.repository.ComparisonRepository
import com.example.data.repository.OpusRepository
import com.example.ui.components.ViralityScoreGauge
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
fun ClipCompetitorComparisonScreen(
    repository: OpusRepository,
    initialClipId: Long? = null,
    onBack: () -> Unit,
    onOpenStudio: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allClips by repository.allClips.collectAsState(initial = emptyList())

    var selectedClipId by remember {
        mutableStateOf(initialClipId ?: allClips.firstOrNull()?.id ?: 0L)
    }

    val activeClip = remember(allClips, selectedClipId) {
        allClips.firstOrNull { it.id == selectedClipId } ?: allClips.firstOrNull()
    }

    val competitorBenchmarks = remember { ComparisonRepository.competitorVideoBenchmarks }
    var selectedCompetitorIndex by remember { mutableIntStateOf(0) }
    val activeCompetitor = competitorBenchmarks.getOrNull(selectedCompetitorIndex) ?: competitorBenchmarks.first()

    var showCustomCompetitorDialog by remember { mutableStateOf(false) }
    var customCompetitorUrl by remember { mutableStateOf("") }
    var isAnalyzingCustomCompetitor by remember { mutableStateOf(false) }

    // Dialog for adding custom competitor
    if (showCustomCompetitorDialog) {
        AlertDialog(
            onDismissRequest = { showCustomCompetitorDialog = false },
            containerColor = OpusDarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Custom Competitor",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تحليل مقطع منافس جديد (Custom URL)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "الصق رابط مقطع منافس على TikTok أو YouTube Shorts أو Instagram Reels لتحليل بيانات أدائه ومقارنته بمقطعك:",
                        fontSize = 12.sp,
                        color = OpusTextSecondary
                    )

                    OutlinedTextField(
                        value = customCompetitorUrl,
                        onValueChange = { customCompetitorUrl = it },
                        modifier = Modifier.fillMaxWidth().testTag("custom_competitor_url_input"),
                        placeholder = {
                            Text(
                                "https://www.tiktok.com/@competitor/video/...",
                                fontSize = 11.sp,
                                color = OpusTextSecondary.copy(alpha = 0.6f)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OpusElectricCyan,
                            unfocusedBorderColor = OpusBorder,
                            focusedTextColor = OpusTextPrimary,
                            unfocusedTextColor = OpusTextPrimary,
                            focusedContainerColor = OpusDarkSurfaceVariant,
                            unfocusedContainerColor = OpusDarkSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    if (isAnalyzingCustomCompetitor) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = OpusElectricCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "جاري استخراج بيانات أداء المنافس بالذكاء الاصطناعي...",
                                fontSize = 11.sp,
                                color = OpusElectricCyan
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customCompetitorUrl.isNotBlank()) {
                            isAnalyzingCustomCompetitor = true
                            // Simulate AI parsing
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                isAnalyzingCustomCompetitor = false
                                showCustomCompetitorDialog = false
                                Toast.makeText(
                                    context,
                                    "تم استخراج بيانات المنافس بنجاح ومقارنتها بمقطعك!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }, 1200)
                        } else {
                            Toast.makeText(context, "يرجى لصق رابط صالح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("analyze_custom_competitor_button")
                ) {
                    Text("تحليل ومقارنة", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomCompetitorDialog = false }) {
                    Text("إلغاء", color = OpusTextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpusDarkCanvas)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Navigation Header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("comparison_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = OpusElectricCyan
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column {
                        Text(
                            text = "Side-by-Side Video Benchmark",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                        )
                        Text(
                            text = "مقارنة المقطع وجهاً لوجه مع أداء المنافسين",
                            fontSize = 11.sp,
                            color = OpusTextSecondary
                        )
                    }
                }

                Button(
                    onClick = { showCustomCompetitorDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = OpusDarkSurfaceHighlight),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OpusElectricCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("add_custom_competitor_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Competitor",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+ منافس مخصص",
                        fontSize = 11.sp,
                        color = OpusElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Clip Selection Carousel
        if (allClips.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "اختر مقطعك للمقارنة (${allClips.size} مقاطع متاحة):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allClips) { clip ->
                            val isSelected = clip.id == activeClip?.id
                            val scoreColor = if (clip.viralityScore >= 90) OpusViralEmerald else OpusElectricCyan

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) OpusDarkSurfaceHighlight else OpusDarkSurface)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) OpusElectricCyan else OpusBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedClipId = clip.id }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("select_clip_${clip.id}")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(scoreColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${clip.viralityScore}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = scoreColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column {
                                        Text(
                                            text = clip.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) OpusTextPrimary else OpusTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${clip.durationSec}s | ${clip.layoutType}",
                                            fontSize = 9.sp,
                                            color = OpusTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Competitor Benchmark Selector Carousel
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "اختر المنافس المعياري (Top Industry Benchmarks):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(competitorBenchmarks.indices.toList()) { index ->
                        val competitor = competitorBenchmarks[index]
                        val isSelected = selectedCompetitorIndex == index

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) OpusPrimaryViolet.copy(alpha = 0.25f) else OpusDarkSurface)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) OpusPrimaryViolet else OpusBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedCompetitorIndex = index }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("select_competitor_${competitor.id}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(OpusGold.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "★",
                                        fontSize = 12.sp,
                                        color = OpusGold
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column {
                                    Text(
                                        text = competitor.creatorName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) OpusTextPrimary else OpusTextSecondary
                                    )
                                    Text(
                                        text = "${competitor.viewsCount} Views • ${competitor.viralityScore} Score",
                                        fontSize = 9.sp,
                                        color = OpusTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Side-by-Side Dual Column Head-to-Head Clash Card
        item {
            HeadToHeadClashCard(
                clip = activeClip,
                competitor = activeCompetitor,
                onOpenStudio = {
                    if (activeClip != null) {
                        onOpenStudio(activeClip.id)
                    }
                }
            )
        }

        // Virality Score AI Gauge Component Integration
        if (activeClip != null) {
            item {
                ViralityScoreGauge(
                    score = activeClip.viralityScore,
                    clip = activeClip,
                    onCompareClick = null,
                    showSubmetrics = true,
                    showActionButtons = false
                )
            }
        }

        // Comprehensive Metric-by-Metric Matrix
        item {
            MetricComparisonMatrixCard(
                clip = activeClip,
                competitor = activeCompetitor
            )
        }

        // AI Strategic Recommendations (How to Win)
        item {
            AiStrategicRecommendationsCard(
                clip = activeClip,
                competitor = activeCompetitor,
                onApplyOptimization = {
                    Toast.makeText(
                        context,
                        "تم تطبيق تحسينات الذكاء الاصطناعي على المقطع تلقائياً!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Visual Side-by-Side Head-to-Head Clash Card
 */
@Composable
private fun HeadToHeadClashCard(
    clip: Clip?,
    competitor: CompetitorVideoPerformance,
    onOpenStudio: () -> Unit
) {
    val clipScore = clip?.viralityScore ?: 90
    val competitorScore = competitor.viralityScore
    val isClipWinning = clipScore >= competitorScore

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("head_to_head_clash_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Card Banner Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Comparison Outcome",
                        tint = if (isClipWinning) OpusViralEmerald else OpusGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isClipWinning)
                            "🏆 مقطعك يتفوق على المنافس بمقدار +${clipScore - competitorScore}% في الجاهزية"
                        else
                            "⚡ المنافس متقدم بفارق ضئيل (+${competitorScore - clipScore}%) — قابل للتجاوز",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isClipWinning) OpusViralEmerald else OpusGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Two-Column Grid: Your Clip vs Competitor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left Column: Your Clip
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OpusDarkSurfaceHighlight)
                        .border(
                            1.dp,
                            if (isClipWinning) OpusViralEmerald else OpusBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(OpusElectricCyan.copy(alpha = 0.15f))
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "مقطعك الذكي (Your Clip)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = OpusElectricCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = clip?.title ?: "AI Generated Viral Short",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Score Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Virality Score", fontSize = 10.sp, color = OpusTextSecondary)
                        Text(
                            text = "$clipScore/100",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (clipScore >= 90) OpusViralEmerald else OpusElectricCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    MiniMetricRow("قوة الخطاف (Hook)", "${clip?.hookScore ?: 92}%")
                    MiniMetricRow("زمن الخطاف", "< 2.2s")
                    MiniMetricRow("سرعة الكلمات", "168 WPM")
                    MiniMetricRow("كابشن متحرك", "Opus Neon ✨")
                    MiniMetricRow("مشاهد B-Roll", "3 لقطات AI")
                }

                // Center VS Indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(OpusPrimaryViolet)
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                // Right Column: Competitor
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OpusDarkSurfaceHighlight)
                        .border(
                            1.dp,
                            if (!isClipWinning) OpusGold else OpusBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(OpusGold.copy(alpha = 0.15f))
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "المنافس: ${competitor.creatorName}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = OpusGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = competitor.videoTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Score Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Virality Score", fontSize = 10.sp, color = OpusTextSecondary)
                        Text(
                            text = "$competitorScore/100",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = OpusGold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    MiniMetricRow("المشاهدات الواقعية", competitor.viewsCount)
                    MiniMetricRow("زمن الخطاف", "${competitor.hookDurationSec}s")
                    MiniMetricRow("سرعة الكلمات", "${competitor.wordsPerMinute} WPM")
                    MiniMetricRow("نوع الكابشن", competitor.captionStyle.take(12))
                    MiniMetricRow("مشاهد B-Roll", "${competitor.bRollCount} لقطات")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button
            Button(
                onClick = onOpenStudio,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_studio_from_comparison_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OpusDarkSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpusElectricCyan)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Edit Clip in Studio",
                    tint = OpusElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "فتح المقطع في استوديو التحرير وضبط التفاصيل",
                    fontSize = 12.sp,
                    color = OpusElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MiniMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 9.sp, color = OpusTextSecondary)
        Text(text = value, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = OpusTextPrimary)
    }
}

/**
 * Metric-by-Metric Head-to-Head Comparison Matrix Card
 */
@Composable
private fun MetricComparisonMatrixCard(
    clip: Clip?,
    competitor: CompetitorVideoPerformance
) {
    val hookClip = (clip?.hookScore ?: 92).toFloat()
    val hookComp = competitor.hookScore.toFloat()

    val retentionClip = (clip?.retentionScore ?: 88).toFloat()
    val retentionComp = competitor.retentionScore.toFloat()

    val wpmClip = 168f
    val wpmComp = competitor.wordsPerMinute.toFloat()

    val shareClip = (clip?.shareabilityScore ?: 86).toFloat()
    val shareComp = (competitor.shareRatePercent * 8).coerceIn(60f, 98f)

    val brollClip = 3f
    val brollComp = competitor.bRollCount.toFloat()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("metric_comparison_matrix_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = "Matrix Insights",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مصفوفة التحليل المقارن (Metric Breakdown)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                    )
                }

                Text(
                    text = "مقطعك vs المنافس",
                    fontSize = 11.sp,
                    color = OpusTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Comparison Rows
            MatrixDualBarRow(
                metricName = "قوة الخطاف الافتتاحي (0-3s Hook)",
                clipVal = hookClip,
                clipDisplay = "${hookClip.toInt()}%",
                compVal = hookComp,
                compDisplay = "${hookComp.toInt()}%",
                unit = "%"
            )

            Spacer(modifier = Modifier.height(10.dp))

            MatrixDualBarRow(
                metricName = "منحنى الاحتفاظ المتوقع (Retention)",
                clipVal = retentionClip,
                clipDisplay = "${retentionClip.toInt()}%",
                compVal = retentionComp,
                compDisplay = "${retentionComp.toInt()}%",
                unit = "%"
            )

            Spacer(modifier = Modifier.height(10.dp))

            MatrixDualBarRow(
                metricName = "سرعة السرد الصوتي (Speech Cadence)",
                clipVal = wpmClip,
                clipDisplay = "${wpmClip.toInt()} WPM",
                compVal = wpmComp,
                compDisplay = "${wpmComp.toInt()} WPM",
                unit = "WPM"
            )

            Spacer(modifier = Modifier.height(10.dp))

            MatrixDualBarRow(
                metricName = "كثافة الـ B-Roll والمؤثرات البصرية",
                clipVal = brollClip * 20f,
                clipDisplay = "${brollClip.toInt()} قطع",
                compVal = brollComp * 20f,
                compDisplay = "${brollComp.toInt()} قطع",
                unit = "Cuts"
            )

            Spacer(modifier = Modifier.height(10.dp))

            MatrixDualBarRow(
                metricName = "معدل الحفظ والمشاركة (Shareability)",
                clipVal = shareClip,
                clipDisplay = "${shareClip.toInt()}%",
                compVal = shareComp,
                compDisplay = "${shareComp.toInt()}%",
                unit = "%"
            )
        }
    }
}

@Composable
private fun MatrixDualBarRow(
    metricName: String,
    clipVal: Float,
    clipDisplay: String,
    compVal: Float,
    compDisplay: String,
    unit: String
) {
    val isClipWinning = clipVal >= compVal
    val isTie = clipVal == compVal

    val animatedClipVal by animateFloatAsState(
        targetValue = (clipVal / 100f).coerceIn(0.1f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "clip_val_anim"
    )

    val animatedCompVal by animateFloatAsState(
        targetValue = (compVal / 100f).coerceIn(0.1f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "comp_val_anim"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OpusDarkSurfaceHighlight)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = metricName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OpusTextPrimary
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isTie) OpusTextSecondary.copy(alpha = 0.2f)
                        else if (isClipWinning) OpusViralEmerald.copy(alpha = 0.2f)
                        else OpusGold.copy(alpha = 0.2f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isTie) "تعادل" else if (isClipWinning) "🏆 تفوق مقطعك" else "المنافس متقدم",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isTie) OpusTextSecondary else if (isClipWinning) OpusViralEmerald else OpusGold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Your Clip Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "مقطعك:",
                fontSize = 10.sp,
                color = OpusElectricCyan,
                modifier = Modifier.width(48.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(OpusDarkCanvas)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedClipVal)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(OpusElectricCyan)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = clipDisplay,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OpusElectricCyan,
                modifier = Modifier.width(55.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Competitor Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "المنافس:",
                fontSize = 10.sp,
                color = OpusGold,
                modifier = Modifier.width(48.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(OpusDarkCanvas)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedCompVal)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(OpusGold)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = compDisplay,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OpusGold,
                modifier = Modifier.width(55.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * AI Strategic Recommendations (How to Win and Out-Perform)
 */
@Composable
private fun AiStrategicRecommendationsCard(
    clip: Clip?,
    competitor: CompetitorVideoPerformance,
    onApplyOptimization: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_strategic_recommendations_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(OpusPrimaryViolet.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "AI Strategy",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "خطة الذكاء الاصطناعي للتفوق على ${competitor.creatorName}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                    )
                    Text(
                        text = "Gemini Strategic Algorithmic Playbook",
                        fontSize = 10.sp,
                        color = OpusTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI Insight Text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(OpusDarkSurfaceHighlight)
                    .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = competitor.aiComparisonInsight,
                    fontSize = 12.sp,
                    color = OpusTextPrimary,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actionable Steps
            Text(
                text = "خطوات التحسين الفورية الموصى بها:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OpusTextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            RecommendationBullet(
                number = "1",
                title = "تسريع الخطاف (Hook Trim)",
                text = "قص أول 0.3 ثانية من الصمت لرفع سرعة الاستجابة فوق 95% والتفوق على المنافس."
            )
            RecommendationBullet(
                number = "2",
                title = "تفعيل الكلمات الملونة (Karaoke Pop)",
                text = "استخدام نمط كابشن 'Opus Neon' أو 'Hormozi Pop' لإبراز كلمات الدوبامين الثلاث الأولى."
            )
            RecommendationBullet(
                number = "3",
                title = "إدراج B-Roll إضافي عند الثانية 12",
                text = "تضمين لقطة مرئية لتجديد انتباه المشاهدين ومنع هبوط منحنى المشاهدة."
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onApplyOptimization,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apply_ai_optimization_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OpusViralEmerald)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Apply Optimization",
                    tint = OpusDarkCanvas,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "تطبيق تحسينات الذكاء الاصطناعي على المقطع تلقائياً",
                    fontWeight = FontWeight.Bold,
                    color = OpusDarkCanvas,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RecommendationBullet(
    number: String,
    title: String,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(OpusElectricCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OpusElectricCyan
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OpusTextPrimary
            )
            Text(
                text = text,
                fontSize = 10.sp,
                color = OpusTextSecondary,
                lineHeight = 14.sp
            )
        }
    }
}
