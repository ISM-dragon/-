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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clip
import com.example.data.model.Project
import com.example.data.model.ProcessingJobEntity
import com.example.data.model.RepurposingHistoryEntity
import com.example.data.model.VideoProcessingCacheEntity
import com.example.data.model.ViralScoreMetricEntity
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    repository: OpusRepository,
    onOpenProjectClips: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allProjects by repository.allProjects.collectAsState(initial = emptyList())
    val favoriteClips by repository.favoriteClips.collectAsState(initial = emptyList())
    val historyList by repository.repurposingHistory.collectAsState(initial = emptyList())
    val cachedMetadataList by repository.cachedVideoMetadata.collectAsState(initial = emptyList())
    val viralScoreMetricsList by repository.topViralScoreMetrics.collectAsState(initial = emptyList())
    val totalTimeSaved by repository.totalTimeSavedMinutes.collectAsState(initial = 0)
    val processingJobs by repository.processingJobs.collectAsState(initial = emptyList())
    val activeProcessingJobs = remember(processingJobs) {
        processingJobs.filter {
            it.status == ProcessingJobEntity.STATUS_QUEUED || it.status == ProcessingJobEntity.STATUS_RUNNING
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredProjects = remember(allProjects, searchQuery) {
        if (searchQuery.isBlank()) allProjects
        else allProjects.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val filteredFavorites = remember(favoriteClips, searchQuery) {
        if (searchQuery.isBlank()) favoriteClips
        else favoriteClips.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val filteredHistory = remember(historyList, searchQuery) {
        if (searchQuery.isBlank()) historyList
        else historyList.filter { it.videoTitle.contains(searchQuery, ignoreCase = true) || it.actionType.contains(searchQuery, ignoreCase = true) }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpusDarkCanvas)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Projects & Repurposing Cache",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    color = OpusTextPrimary
                )
            )
            Text(
                text = "Room-cached video analysis metadata, viral score curves, and user activity history.",
                style = MaterialTheme.typography.bodySmall.copy(color = OpusTextSecondary)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("projects_search_input"),
                placeholder = {
                    Text("Search projects, clips, or cache history...", color = OpusTextSecondary, fontSize = 12.sp)
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = OpusElectricCyan)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OpusVioletGlow,
                    unfocusedBorderColor = OpusBorder,
                    focusedTextColor = OpusTextPrimary,
                    unfocusedTextColor = OpusTextPrimary,
                    focusedContainerColor = OpusDarkSurface,
                    unfocusedContainerColor = OpusDarkSurface
                ),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tabs: All Projects vs Favorite Clips vs History & Cache
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = OpusDarkSurface,
                contentColor = OpusElectricCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = OpusElectricCyan
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, OpusBorder, RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Projects (${filteredProjects.size})",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 0) OpusElectricCyan else OpusTextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_all_projects")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Favorites (${filteredFavorites.size})",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 1) OpusHotPink else OpusTextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_favorites")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "History & Cache (${historyList.size})",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 2) OpusViralEmerald else OpusTextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_history_cache")
                )
            }
        }

        if (activeProcessingJobs.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceHighlight),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OpusElectricCyan.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Background processing (${activeProcessingJobs.size})",
                            color = OpusTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        activeProcessingJobs.take(3).forEach { job ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(job.title, color = OpusTextPrimary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${job.currentStage} • ${job.progress}%", color = OpusElectricCyan, fontSize = 10.sp)
                                }
                                IconButton(
                                    onClick = { coroutineScope.launch { repository.cancelVideoProcessing(job.jobId) } },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Cancel processing", tint = OpusHotPink)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedTab == 0) {
            if (filteredProjects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No projects found.", color = OpusTextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                items(filteredProjects) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { onOpenProjectClips(project.id) },
                        onDelete = {
                            coroutineScope.launch {
                                repository.deleteProject(project.id)
                                Toast.makeText(context, "Project deleted.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        } else if (selectedTab == 1) {
            if (filteredFavorites.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No favorite clips saved yet. Star clips in Studio to bookmark them here!", color = OpusTextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                items(filteredFavorites) { clip ->
                    FavoriteClipCard(
                        clip = clip,
                        onOpen = { onOpenProjectClips(clip.projectId) }
                    )
                }
            }
        } else {
            // TAB 2: Room Database Cache, Viral Scores & Repurposing History
            item {
                // Room Stats Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceHighlight),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OpusViralEmerald.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = "Room DB",
                                    tint = OpusViralEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Room Local Database Cache",
                                    fontWeight = FontWeight.Bold,
                                    color = OpusTextPrimary,
                                    fontSize = 13.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(OpusViralEmerald.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "SQLite v3 Active",
                                    fontSize = 10.sp,
                                    color = OpusViralEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Estimated Time Saved", fontSize = 10.sp, color = OpusTextSecondary)
                                Text(
                                    text = "${totalTimeSaved ?: 0} mins",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OpusViralEmerald
                                )
                            }
                            Column {
                                Text(text = "Cached Processing", fontSize = 10.sp, color = OpusTextSecondary)
                                Text(
                                    text = "${cachedMetadataList.size} items",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OpusElectricCyan
                                )
                            }
                            Column {
                                Text(text = "Viral Scores Tracked", fontSize = 10.sp, color = OpusTextSecondary)
                                Text(
                                    text = "${viralScoreMetricsList.size} clips",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OpusHotPink
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.clearVideoProcessingCache()
                                        Toast.makeText(context, "Cleared processing cache.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(34.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OpusDarkSurface),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
                            ) {
                                Text("Clear Cache", fontSize = 11.sp, color = OpusTextSecondary)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.clearAllRepurposingHistory()
                                        Toast.makeText(context, "Cleared activity history.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(34.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OpusDarkSurface),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
                            ) {
                                Text("Clear History", fontSize = 11.sp, color = OpusTextSecondary)
                            }
                        }
                    }
                }
            }

            // Section 1: User Repurposing Activity History
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.History, contentDescription = "History", tint = OpusElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "User Repurposing Activity (${filteredHistory.size})",
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary,
                        fontSize = 13.sp
                    )
                }
            }

            if (filteredHistory.isEmpty()) {
                item {
                    Text(text = "No history records found.", color = OpusTextSecondary, fontSize = 12.sp)
                }
            } else {
                items(filteredHistory) { history ->
                    HistoryItemCard(
                        history = history,
                        dateString = dateFormatter.format(Date(history.timestamp)),
                        onDelete = {
                            coroutineScope.launch {
                                repository.deleteHistoryEntry(history.id)
                                Toast.makeText(context, "History entry deleted.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // Section 2: Cached Video Processing Metadata
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Memory, contentDescription = "Cache", tint = OpusVioletGlow, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cached Video Processing Metadata (${cachedMetadataList.size})",
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary,
                        fontSize = 13.sp
                    )
                }
            }

            if (cachedMetadataList.isEmpty()) {
                item {
                    Text(text = "No cached video metadata yet.", color = OpusTextSecondary, fontSize = 12.sp)
                }
            } else {
                items(cachedMetadataList) { cacheEntry ->
                    CachedMetadataCard(cacheEntry = cacheEntry)
                }
            }

            // Section 3: Granular Viral Score Metrics Breakdown
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.TrendingUp, contentDescription = "Viral", tint = OpusHotPink, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Granular Viral Scores & Platform Fit (${viralScoreMetricsList.size})",
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary,
                        fontSize = 13.sp
                    )
                }
            }

            if (viralScoreMetricsList.isEmpty()) {
                item {
                    Text(text = "No viral score metrics evaluated yet.", color = OpusTextSecondary, fontSize = 12.sp)
                }
            } else {
                items(viralScoreMetricsList) { metric ->
                    ViralMetricCard(metric = metric)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("project_row_${project.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(OpusPrimaryViolet.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Folder",
                            tint = OpusElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = project.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${project.clipCount} Clips • ${project.sourceDurationSec / 60}m duration",
                            fontSize = 11.sp,
                            color = OpusTextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp).testTag("delete_project_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = OpusTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OpusDarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = project.captionTheme,
                        fontSize = 10.sp,
                        color = OpusVioletGlow,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OpusViralEmerald.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Top Virality: ${project.bestViralityScore}/100",
                        fontSize = 10.sp,
                        color = OpusViralEmerald,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteClipCard(
    clip: Clip,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("fav_clip_row_${clip.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(OpusHotPink.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Fav",
                    tint = OpusHotPink,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clip.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = OpusTextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${clip.durationSec}s • Score ${clip.viralityScore} • ${clip.layoutType}",
                    fontSize = 11.sp,
                    color = OpusTextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(OpusPrimaryViolet)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Open",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    history: RepurposingHistoryEntity,
    dateString: String,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (history.actionType.contains("PUBLISH")) OpusElectricCyan.copy(alpha = 0.2f)
                                else OpusPrimaryViolet.copy(alpha = 0.25f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (history.actionType) {
                                "DIRECT_API_PUBLISHED" -> "DIRECT API"
                                else -> "AI REPURPOSE"
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (history.actionType.contains("PUBLISH")) OpusElectricCyan else OpusVioletGlow
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = dateString,
                        fontSize = 10.sp,
                        color = OpusTextSecondary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = OpusTextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = history.videoTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = OpusTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (history.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = history.details,
                    fontSize = 10.sp,
                    color = OpusTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (history.estimatedTimeSavedMinutes > 0) {
                                "Saved: ~${history.estimatedTimeSavedMinutes}m"
                            } else {
                                "Time saved: unavailable"
                            },
                    fontSize = 10.sp,
                    color = OpusViralEmerald,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Virality: ${history.highestViralScore}%",
                    fontSize = 10.sp,
                    color = OpusHotPink,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CachedMetadataCard(cacheEntry: VideoProcessingCacheEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = cacheEntry.videoTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = OpusTextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OpusElectricCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Hits: ${cacheEntry.cacheHitCount}",
                        fontSize = 9.sp,
                        color = OpusElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${cacheEntry.resolution} • Lang: ${cacheEntry.detectedLanguage.uppercase()} • Duration: ${cacheEntry.sourceDurationSec / 60}m",
                fontSize = 10.sp,
                color = OpusTextSecondary
            )

            if (cacheEntry.audioSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cacheEntry.audioSummary,
                    fontSize = 10.sp,
                    color = OpusTextSecondary.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ViralMetricCard(metric: ViralScoreMetricEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = metric.clipTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = OpusTextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (metric.viralityGrade.startsWith("S")) OpusViralEmerald.copy(alpha = 0.2f)
                            else OpusVioletGlow.copy(alpha = 0.2f)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Grade ${metric.viralityGrade} (${metric.overallViralityScore}%)",
                        fontSize = 10.sp,
                        color = if (metric.viralityGrade.startsWith("S")) OpusViralEmerald else OpusVioletGlow,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Hook: ${metric.hookScore}%", fontSize = 10.sp, color = OpusTextSecondary)
                Text(text = "Retention: ${metric.retentionScore}%", fontSize = 10.sp, color = OpusTextSecondary)
                Text(text = "Shareability: ${metric.shareabilityScore}%", fontSize = 10.sp, color = OpusTextSecondary)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "TikTok: ${metric.tiktokFitScore.takeIf { it >= 0 }?.let { "$it%" } ?: "غير متاح"}", fontSize = 10.sp, color = OpusTextSecondary)
                Text(text = "Reels: ${metric.reelsFitScore.takeIf { it >= 0 }?.let { "$it%" } ?: "غير متاح"}", fontSize = 10.sp, color = OpusTextSecondary)
                Text(text = "Shorts: ${metric.shortsFitScore.takeIf { it >= 0 }?.let { "$it%" } ?: "غير متاح"}", fontSize = 10.sp, color = OpusTextSecondary)
            }
        }
    }
}
