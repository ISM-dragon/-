package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.OpusRepository
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

data class PricingPlan(
    val name: String,
    val price: String,
    val period: String,
    val minutes: String,
    val isPopular: Boolean,
    val features: List<String>,
    val buttonText: String
)

val PricingPlansList = listOf(
    PricingPlan(
        name = "Free Tier",
        price = "$0",
        period = "/month",
        minutes = "60 Free Mins",
        isPopular = false,
        features = listOf(
            "60 processing minutes monthly",
            "Auto Virality Score™ & Hook Curation",
            "720p HD exports with watermark",
            "3 standard caption themes"
        ),
        buttonText = "Current Base Plan"
    ),
    PricingPlan(
        name = "Starter",
        price = "$9",
        period = "/month",
        minutes = "150 Mins / mo",
        isPopular = false,
        features = listOf(
            "150 processing minutes monthly",
            "No watermark on exports",
            "1080p Full HD video exports",
            "Auto-emojis and keyword highlights",
            "AI B-roll prompt suggestions"
        ),
        buttonText = "Upgrade to Starter"
    ),
    PricingPlan(
        name = "Pro",
        price = "$19",
        period = "/month",
        minutes = "300 Mins / mo",
        isPopular = true,
        features = listOf(
            "300 processing minutes monthly",
            "4K Ultra HD @ 60fps exports",
            "Multi-speaker auto reframing & split",
            "Full 12+ dynamic animated caption themes",
            "AI social post copywriter for all channels",
            "Fastest Gemini 3.5 AI priority queue"
        ),
        buttonText = "Upgrade to Pro (Most Popular)"
    ),
    PricingPlan(
        name = "Business & Agency",
        price = "$99",
        period = "/month",
        minutes = "1,800 Mins / mo",
        isPopular = false,
        features = listOf(
            "1,800 processing minutes monthly",
            "Multi-user team workspaces",
            "Custom brand font & color uploads",
            "Dedicated API integration for auto-posting",
            "Priority VIP 24/7 support"
        ),
        buttonText = "Upgrade to Business"
    )
)

@Composable
fun PricingScreen(
    repository: OpusRepository,
    onPlanSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userCreditState by repository.userCreditState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpusDarkCanvas)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            // Header
            Column(modifier = Modifier.fillMaxWidth().testTag("pricing_header")) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(OpusPrimaryViolet.copy(alpha = 0.2f))
                        .border(1.dp, OpusVioletGlow.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Simple, Predictable Creator Pricing",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusElectricCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Flexible Plans for Every Creator",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        color = OpusTextPrimary
                    )
                )

                Text(
                    text = "Start free with 60 minutes. Upgrade whenever you need more monthly processing capacity and 4K 60fps exports.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OpusTextSecondary,
                        lineHeight = 17.sp
                    )
                )
            }
        }

        // Active Account Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("account_balance_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceHighlight),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Current Account Balance:",
                            fontSize = 11.sp,
                            color = OpusTextSecondary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${userCreditState.creditsRemaining} Minutes",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = OpusElectricCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${userCreditState.currentPlan})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusVioletGlow
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(OpusViralEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${userCreditState.clipsCreatedCount} Clips Made",
                            color = OpusViralEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Pricing Cards List
        items(PricingPlansList) { plan ->
            PricingPlanCard(
                plan = plan,
                isCurrent = userCreditState.currentPlan.contains(plan.name, ignoreCase = true),
                onSelect = {
                    Toast.makeText(context, "Selected ${plan.name}! Plan activated.", Toast.LENGTH_SHORT).show()
                    onPlanSelected()
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun PricingPlanCard(
    plan: PricingPlan,
    isCurrent: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (plan.isPopular) OpusVioletGlow else OpusBorder
    val containerColor = if (plan.isPopular) OpusDarkSurfaceHighlight else OpusDarkSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pricing_card_${plan.name.replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(if (plan.isPopular) 2.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary
                    )
                )

                if (plan.isPopular) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OpusPrimaryViolet)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "★ MOST POPULAR",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = plan.price,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = if (plan.isPopular) OpusElectricCyan else OpusTextPrimary
                    )
                )
                Text(
                    text = plan.period,
                    fontSize = 12.sp,
                    color = OpusTextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(OpusDarkSurfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = plan.minutes,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            plan.features.forEach { feat ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Included",
                        tint = OpusViralEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = feat,
                        fontSize = 12.sp,
                        color = OpusTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (plan.isPopular) OpusPrimaryViolet else OpusDarkSurfaceVariant
                )
            ) {
                Text(
                    text = if (isCurrent) "Current Active Plan" else plan.buttonText,
                    color = if (plan.isPopular) Color.White else OpusTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
