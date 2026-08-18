package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CompetitorComparison
import com.example.data.repository.ComparisonRepository
import com.example.ui.components.SeoMetadataCard
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
fun ComparisonHubScreen(
    onSelectCompetitor: (String) -> Unit,
    onStartClipper: () -> Unit,
    modifier: Modifier = Modifier
) {
    val competitors = ComparisonRepository.allCompetitors
    var monthlyVideosCount by remember { mutableIntStateOf(4) }
    val hoursSavedPerMonth = monthlyVideosCount * 5.5f
    val dollarsSavedPerMonth = (monthlyVideosCount * 120) - 19

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpusDarkCanvas)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            // SEO Header
            Column(modifier = Modifier.fillMaxWidth().testTag("compare_hub_header")) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(OpusPrimaryViolet.copy(alpha = 0.2f))
                        .border(1.dp, OpusVioletGlow.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "SEO Decision Hub (2026 Edition)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusElectricCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ISM vs Top 7 Video Clipping Alternatives",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        color = OpusTextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Objective feature breakdown, Virality Score™ comparison, caption quality benchmarks, and pricing ROI across Descript, Klap, Munch, Vizard, Vidyo.ai, CapCut & Submagic.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OpusTextSecondary,
                        lineHeight = 17.sp
                    )
                )
            }
        }

        // Creator ROI & Time-Saving Interactive Calculator
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("roi_calculator_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "ROI",
                                tint = OpusViralEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Creator Time & Cost Savings Estimator",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OpusTextPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Long-form videos produced monthly: $monthlyVideosCount",
                            fontSize = 12.sp,
                            color = OpusTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Slider(
                        value = monthlyVideosCount.toFloat(),
                        onValueChange = { monthlyVideosCount = it.toInt() },
                        valueRange = 1f..20f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = OpusViralEmerald,
                            activeTrackColor = OpusViralEmerald,
                            inactiveTrackColor = OpusDarkSurfaceHighlight
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OpusDarkSurfaceVariant)
                                .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "~${hoursSavedPerMonth.toInt()} Hours",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OpusElectricCyan
                                )
                                Text(
                                    text = "Saved in manual timeline cuts",
                                    fontSize = 10.sp,
                                    color = OpusTextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OpusDarkSurfaceVariant)
                                .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$${dollarsSavedPerMonth.toInt()}/mo",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OpusViralEmerald
                                )
                                Text(
                                    text = "Editor salary savings",
                                    fontSize = 10.sp,
                                    color = OpusTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Master 10-Feature Comparison Decision Matrix (Scrollable Table)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("decision_matrix_table"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "10-Point Core Feature Decision Matrix",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Swipe horizontally to see full head-to-head capability comparison across top competitors.",
                        fontSize = 11.sp,
                        color = OpusTextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                    ) {
                        Column {
                            // Table Header Row
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(OpusDarkSurfaceHighlight)
                                    .padding(8.dp)
                            ) {
                                TableCell("Core Capability", width = 160, isHeader = true)
                                TableCell("ISM", width = 100, isHeader = true, color = OpusElectricCyan)
                                TableCell("Descript", width = 90, isHeader = true)
                                TableCell("Klap", width = 90, isHeader = true)
                                TableCell("Munch", width = 90, isHeader = true)
                                TableCell("Vizard", width = 90, isHeader = true)
                                TableCell("CapCut", width = 90, isHeader = true)
                                TableCell("Submagic", width = 90, isHeader = true)
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Matrix Rows
                            MatrixRow("1-Click AI Clip Finding", "Autonomous", "Manual", "AI", "AI", "AI", "Manual", "Pre-cut only")
                            MatrixRow("Virality Score™ (0-100)", "Yes (5 Factors)", "No", "Basic %", "Trend keywords", "Basic", "No", "No")
                            MatrixRow("Auto Speaker Tracking (9:16)", "Yes (Split/Crop)", "Manual", "Yes", "Yes", "Yes", "Manual", "Manual")
                            MatrixRow("Dynamic Karaoke Subtitles", "12+ Themes", "Basic", "5 Styles", "Standard", "Basic", "Manual", "Yes")
                            MatrixRow("AI Auto-Emojis", "Contextual", "No", "Limited", "No", "No", "Manual", "Yes")
                            MatrixRow("AI B-Roll Prompts", "Full Cues", "No", "No", "No", "No", "Library", "Stock video")
                            MatrixRow("Social Copywriting", "TikTok, Reels, Shorts, LI", "No", "Basic", "Post generator", "No", "No", "No")
                            MatrixRow("Export Quality", "4K 60fps", "4K", "1080p", "1080p", "1080p", "4K 60fps", "1080p")
                            MatrixRow("Starting Monthly Price", "$9 - $19", "$19", "$29", "$49", "$16", "Free / $9.99", "$20")
                            MatrixRow("Free Monthly Allowance", "60 mins free", "60 mins", "1 trial", "Trial only", "300m watermark", "Unlimited", "3 trials")
                        }
                    }
                }
            }
        }

        // Competitor Cards List
        item {
            Text(
                text = "Detailed Competitor Breakdowns",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )
            )
        }

        items(competitors) { competitor ->
            CompetitorCard(
                competitor = competitor,
                onClick = { onSelectCompetitor(competitor.slug) }
            )
        }

        // Technical SEO & Schema preview
        item {
            SeoMetadataCard(
                url = "https://opus.pro/compare",
                title = "ISM vs Competitors & Alternatives (2026 Guide)",
                metaDescription = "Compare ISM vs Descript, Klap, Munch, Vizard, Vidyo.ai, CapCut & Submagic. Complete feature, virality score & pricing breakdown.",
                structuredDataJsonLd = """{"@context":"https://schema.org","@type":"CollectionPage","name":"ISM Video Clipper Competitor Comparison Hub","hasPart":[{"@type":"Product","name":"Descript"},{"@type":"Product","name":"Klap"},{"@type":"Product","name":"Munch"}]}"""
            )
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: Int,
    isHeader: Boolean = false,
    color: Color = OpusTextPrimary
) {
    Box(
        modifier = Modifier
            .width(width.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = if (isHeader) Alignment.CenterStart else Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = if (isHeader) 11.sp else 10.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun MatrixRow(
    feature: String,
    opus: String,
    descript: String,
    klap: String,
    munch: String,
    vizard: String,
    capcut: String,
    submagic: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(OpusDarkSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell(feature, width = 160, isHeader = true)
        TableCell(opus, width = 100, color = OpusViralEmerald)
        TableCell(descript, width = 90, color = OpusTextSecondary)
        TableCell(klap, width = 90, color = OpusTextSecondary)
        TableCell(munch, width = 90, color = OpusTextSecondary)
        TableCell(vizard, width = 90, color = OpusTextSecondary)
        TableCell(capcut, width = 90, color = OpusTextSecondary)
        TableCell(submagic, width = 90, color = OpusTextSecondary)
    }
}

@Composable
private fun CompetitorCard(
    competitor: CompetitorComparison,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("competitor_card_${competitor.slug}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(OpusPrimaryViolet.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = competitor.name.take(2).uppercase(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = OpusElectricCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ISM vs ${competitor.name}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                        )
                        Text(
                            text = competitor.category,
                            fontSize = 11.sp,
                            color = OpusTextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = OpusGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${competitor.rating}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = competitor.overview,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OpusTextSecondary,
                    lineHeight = 16.sp
                ),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Winner badge & link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Starting: ${competitor.startingPrice}",
                    fontSize = 11.sp,
                    color = OpusVioletGlow,
                    fontWeight = FontWeight.Medium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Read Full Review",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusElectricCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Go",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
