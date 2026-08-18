package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.data.model.ComparisonCriteriaItem
import com.example.data.model.ComparisonFaqItem
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
fun ComparisonDetailScreen(
    slug: String,
    onBack: () -> Unit,
    onStartClipper: () -> Unit,
    modifier: Modifier = Modifier
) {
    val competitor = ComparisonRepository.getCompetitorBySlug(slug)
        ?: ComparisonRepository.allCompetitors.first()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpusDarkCanvas)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Breadcrumb & Back Header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp).testTag("compare_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = OpusElectricCyan
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "Compare > ISM vs ${competitor.name}",
                    fontSize = 11.sp,
                    color = OpusTextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // H1 Title & SEO Introduction
        item {
            Column(modifier = Modifier.fillMaxWidth().testTag("compare_detail_header")) {
                Text(
                    text = competitor.h1,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = OpusTextPrimary,
                        lineHeight = 26.sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = competitor.overview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OpusTextSecondary,
                        lineHeight = 17.sp
                    )
                )
            }
        }

        // Winner Summary Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("winner_verdict_banner"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceHighlight),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpusVioletGlow)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(OpusViralEmerald.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Winner",
                            tint = OpusViralEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Executive Verdict:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusVioletGlow
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = competitor.winnerSummary,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = OpusTextPrimary
                            )
                        )
                    }
                }
            }
        }

        // 12-Criteria Specification Comparison Table
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("spec_table_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Head-to-Head Specification Matrix",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    competitor.criteriaList.forEachIndexed { index, item ->
                        CriteriaRow(item = item, isEven = index % 2 == 0)
                        if (index < competitor.criteriaList.size - 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        // Pros & Cons Comparison Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ISMs & Cons
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(OpusElectricCyan))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ISM Strengths & Advantages",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OpusElectricCyan
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        competitor.opusPros.forEach { pro ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Pro", tint = OpusViralEmerald, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = pro, fontSize = 12.sp, color = OpusTextPrimary, lineHeight = 16.sp)
                            }
                        }
                    }
                }

                // Competitor Pros & Cons
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(OpusTextSecondary))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${competitor.name} Strengths & Best Use Cases",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OpusTextSecondary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        competitor.competitorPros.forEach { pro ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Pro", tint = OpusGold, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = pro, fontSize = 12.sp, color = OpusTextPrimary, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        // Deep-Dive Scenario Guidance
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "When Should You Choose Which Tool?",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(OpusDarkSurfaceVariant)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Choose ISM if:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusElectricCyan
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = competitor.verdictOpus,
                                fontSize = 12.sp,
                                color = OpusTextPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(OpusDarkSurfaceVariant)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Choose ${competitor.name} if:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusTextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = competitor.verdictCompetitor,
                                fontSize = 12.sp,
                                color = OpusTextPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // FAQ Accordion
        item {
            Text(
                text = "Frequently Asked Questions",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )
            )
        }

        items(competitor.faqs) { faq ->
            FaqAccordionItem(faq = faq)
        }

        // Technical SEO & Schema Inspection Card
        item {
            SeoMetadataCard(
                url = "https://opus.pro/compare/${competitor.slug}",
                title = competitor.seoTitle,
                metaDescription = competitor.metaDescription,
                structuredDataJsonLd = competitor.structuredDataJsonLd
            )
        }

        // Sticky Conversion CTA Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("conversion_cta_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceHighlight),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpusVioletGlow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ready to Turn Long Videos into Viral Shorts?",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = OpusTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Join 3M+ creators using ISM AI to extract viral hooks in 1 click.",
                        fontSize = 12.sp,
                        color = OpusTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStartClipper,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = "Start", tint = OpusGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Try ISM Free (60 Mins)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun CriteriaRow(
    item: ComparisonCriteriaItem,
    isEven: Boolean
) {
    val winnerColor = when (item.winner) {
        "opus" -> OpusViralEmerald
        "competitor" -> OpusGold
        else -> OpusElectricCyan
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isEven) OpusDarkSurfaceVariant else OpusDarkSurfaceHighlight)
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.featureName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(winnerColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (item.winner == "opus") "ISM Lead" else if (item.winner == "tie") "Equal" else "Competitor Edge",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = winnerColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "ISM:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OpusElectricCyan)
                    Text(text = item.opusValue, fontSize = 11.sp, color = OpusTextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Competitor:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OpusTextSecondary)
                    Text(text = item.competitorValue, fontSize = 11.sp, color = OpusTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun FaqAccordionItem(faq: ComparisonFaqItem) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("faq_accordion_item"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = faq.question,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = OpusTextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle",
                    tint = OpusElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = faq.answer,
                        fontSize = 12.sp,
                        color = OpusTextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
