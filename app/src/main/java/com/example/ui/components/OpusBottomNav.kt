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
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.SlowMotionVideo
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
import com.example.ui.theme.OpusPrimaryViolet
import com.example.ui.theme.OpusTextPrimary
import com.example.ui.theme.OpusTextSecondary
import com.example.ui.theme.OpusVioletGlow

enum class OpusNavTab(val label: String, val subtitle: String, val testTag: String) {
    HOME("Google Flow", "AI Clipper", "nav_tab_home"),
    STUDIO("Studio", "Editor & Hooks", "nav_tab_studio"),
    PROJECTS("Library", "Saved Clips", "nav_tab_projects")
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
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = OpusNavTab.HOME.label,
                    fontSize = 11.sp,
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
            selected = currentTab == OpusNavTab.STUDIO,
            onClick = { onTabSelected(OpusNavTab.STUDIO) },
            icon = {
                Icon(
                    imageVector = Icons.Default.SlowMotionVideo,
                    contentDescription = "Clip Studio",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = OpusNavTab.STUDIO.label,
                    fontSize = 11.sp,
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
            selected = currentTab == OpusNavTab.PROJECTS,
            onClick = { onTabSelected(OpusNavTab.PROJECTS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = "Saved Projects",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = OpusNavTab.PROJECTS.label,
                    fontSize = 11.sp,
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
    }
}
