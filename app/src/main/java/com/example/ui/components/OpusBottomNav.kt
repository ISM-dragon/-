package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OpusBorder
import com.example.ui.theme.OpusDarkSurface
import com.example.ui.theme.OpusElectricCyan
import com.example.ui.theme.OpusGold
import com.example.ui.theme.OpusHotPink
import com.example.ui.theme.OpusPrimaryViolet
import com.example.ui.theme.OpusTextPrimary
import com.example.ui.theme.OpusTextSecondary
import com.example.ui.theme.OpusViralEmerald
import com.example.ui.theme.OpusVioletGlow

enum class OpusNavTab(val label: String, val subtitle: String, val testTag: String) {
    HOME("Google Flow", "AI Clipper", "nav_tab_home"),
    DASHBOARD("Usage", "Dashboard", "nav_tab_dashboard"),
    STUDIO("Studio", "Editor & Hooks", "nav_tab_studio"),
    BENCHMARK("VS Benchmark", "Competitors", "nav_tab_benchmark"),
    PROJECTS("Library", "Saved Clips", "nav_tab_projects"),
    GATEWAY("Social", "Gateway", "nav_tab_gateway"),
    SETTINGS("API Hub", "Keys & Usage", "nav_tab_settings")
}

@Composable
fun OpusBottomNav(
    currentTab: OpusNavTab,
    onTabSelected: (OpusNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = OpusBorder.copy(alpha = 0.5f))
            .windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = OpusDarkSurface,
        contentColor = OpusTextPrimary
    ) {
        NavigationBarItem(
            selected = currentTab == OpusNavTab.HOME,
            onClick = { onTabSelected(OpusNavTab.HOME) },
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Google Flow AI",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = {
                Text(
                    text = OpusNavTab.HOME.label,
                    fontSize = 9.5.sp,
                    fontWeight = if (currentTab == OpusNavTab.HOME) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = OpusElectricCyan,
                selectedTextColor = OpusElectricCyan,
                indicatorColor = OpusPrimaryViolet.copy(alpha = 0.35f),
                unselectedIconColor = OpusTextSecondary,
                unselectedTextColor = OpusTextSecondary
            ),
            modifier = Modifier.testTag(OpusNavTab.HOME.testTag)
        )

        NavigationBarItem(
            selected = currentTab == OpusNavTab.DASHBOARD,
            onClick = { onTabSelected(OpusNavTab.DASHBOARD) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Usage Dashboard",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = {
                Text(
                    text = OpusNavTab.DASHBOARD.label,
                    fontSize = 9.5.sp,
                    fontWeight = if (currentTab == OpusNavTab.DASHBOARD) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = OpusViralEmerald,
                selectedTextColor = OpusViralEmerald,
                indicatorColor = OpusPrimaryViolet.copy(alpha = 0.35f),
                unselectedIconColor = OpusTextSecondary,
                unselectedTextColor = OpusTextSecondary
            ),
            modifier = Modifier.testTag(OpusNavTab.DASHBOARD.testTag)
        )

        NavigationBarItem(
            selected = currentTab == OpusNavTab.STUDIO,
            onClick = { onTabSelected(OpusNavTab.STUDIO) },
            icon = {
                Icon(
                    imageVector = Icons.Default.SlowMotionVideo,
                    contentDescription = "Clip Studio",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = {
                Text(
                    text = OpusNavTab.STUDIO.label,
                    fontSize = 9.5.sp,
                    fontWeight = if (currentTab == OpusNavTab.STUDIO) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = OpusVioletGlow,
                selectedTextColor = OpusVioletGlow,
                indicatorColor = OpusPrimaryViolet.copy(alpha = 0.35f),
                unselectedIconColor = OpusTextSecondary,
                unselectedTextColor = OpusTextSecondary
            ),
            modifier = Modifier.testTag(OpusNavTab.STUDIO.testTag)
        )

        NavigationBarItem(
            selected = currentTab == OpusNavTab.BENCHMARK,
            onClick = { onTabSelected(OpusNavTab.BENCHMARK) },
            icon = {
                Icon(
                    imageVector = Icons.Default.CompareArrows,
                    contentDescription = "Side-by-Side Benchmark",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = {
                Text(
                    text = OpusNavTab.BENCHMARK.label,
                    fontSize = 9.5.sp,
                    fontWeight = if (currentTab == OpusNavTab.BENCHMARK) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = OpusGold,
                selectedTextColor = OpusGold,
                indicatorColor = OpusPrimaryViolet.copy(alpha = 0.35f),
                unselectedIconColor = OpusTextSecondary,
                unselectedTextColor = OpusTextSecondary
            ),
            modifier = Modifier.testTag(OpusNavTab.BENCHMARK.testTag)
        )

        NavigationBarItem(
            selected = currentTab == OpusNavTab.PROJECTS,
            onClick = { onTabSelected(OpusNavTab.PROJECTS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = "Saved Projects",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = {
                Text(
                    text = OpusNavTab.PROJECTS.label,
                    fontSize = 9.5.sp,
                    fontWeight = if (currentTab == OpusNavTab.PROJECTS) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = OpusElectricCyan,
                selectedTextColor = OpusElectricCyan,
                indicatorColor = OpusPrimaryViolet.copy(alpha = 0.35f),
                unselectedIconColor = OpusTextSecondary,
                unselectedTextColor = OpusTextSecondary
            ),
            modifier = Modifier.testTag(OpusNavTab.PROJECTS.testTag)
        )

        NavigationBarItem(
            selected = currentTab == OpusNavTab.GATEWAY,
            onClick = { onTabSelected(OpusNavTab.GATEWAY) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Social Gateway",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = {
                Text(
                    text = OpusNavTab.GATEWAY.label,
                    fontSize = 9.5.sp,
                    fontWeight = if (currentTab == OpusNavTab.GATEWAY) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = OpusElectricCyan,
                selectedTextColor = OpusElectricCyan,
                indicatorColor = OpusPrimaryViolet.copy(alpha = 0.35f),
                unselectedIconColor = OpusTextSecondary,
                unselectedTextColor = OpusTextSecondary
            ),
            modifier = Modifier.testTag(OpusNavTab.GATEWAY.testTag)
        )

        NavigationBarItem(
            selected = currentTab == OpusNavTab.SETTINGS,
            onClick = { onTabSelected(OpusNavTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "API Keys & Usage",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = {
                Text(
                    text = OpusNavTab.SETTINGS.label,
                    fontSize = 9.5.sp,
                    fontWeight = if (currentTab == OpusNavTab.SETTINGS) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = OpusViralEmerald,
                selectedTextColor = OpusViralEmerald,
                indicatorColor = OpusPrimaryViolet.copy(alpha = 0.35f),
                unselectedIconColor = OpusTextSecondary,
                unselectedTextColor = OpusTextSecondary
            ),
            modifier = Modifier.testTag(OpusNavTab.SETTINGS.testTag)
        )
    }
}
