package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.repository.OpusRepository
import com.example.ui.components.ApiKeySettingsDialog
import com.example.ui.components.OpusBottomNav
import com.example.ui.components.OpusHeader
import com.example.ui.components.OpusNavTab
import com.example.ui.screens.ClipStudioScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.VideoUploadScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OpusDarkCanvas

class MainActivity : ComponentActivity() {

    private lateinit var repository: OpusRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = OpusRepository(this)

        setContent {
            MyApplicationTheme {
                OpusProApp(repository = repository)
            }
        }
    }
}

@Composable
fun OpusProApp(repository: OpusRepository) {
    var currentTab by remember { mutableStateOf(OpusNavTab.HOME) }
    var selectedProjectId by remember { mutableLongStateOf(1L) }
    var showUploadScreen by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    val customApiKey by repository.customApiKey.collectAsState()
    val googleFlowCredits by repository.googleFlowCredits.collectAsState()
    val aiProviders by repository.aiProviders.collectAsState()

    // Preload sample project on first launch
    LaunchedEffect(Unit) {
        repository.initializePreloadedProjectsIfEmpty()
    }

    if (showApiKeyDialog) {
        ApiKeySettingsDialog(
            repository = repository,
            onDismiss = { showApiKeyDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = OpusDarkCanvas,
        topBar = {
            if (!showUploadScreen) {
                OpusHeader(
                    onApiKeyClick = {
                        showApiKeyDialog = true
                    },
                    hasCustomApiKey = customApiKey.isNotBlank(),
                    remainingCreditsMinutes = googleFlowCredits.remainingCreditsMinutes,
                    activeProvidersCount = aiProviders.count { it.isEnabled && it.apiKey.isNotBlank() }
                )
            }
        },
        bottomBar = {
            if (!showUploadScreen) {
                OpusBottomNav(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        currentTab = tab
                        showUploadScreen = false
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OpusDarkCanvas)
                .padding(innerPadding)
        ) {
            if (showUploadScreen) {
                VideoUploadScreen(
                    repository = repository,
                    onBack = { showUploadScreen = false },
                    onProjectCreated = { newProjectId ->
                        selectedProjectId = newProjectId
                        showUploadScreen = false
                        currentTab = OpusNavTab.STUDIO
                    }
                )
            } else {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                    },
                    label = "tab_transition"
                ) { targetTab ->
                    when (targetTab) {
                        OpusNavTab.HOME -> {
                            HomeScreen(
                                repository = repository,
                                onProjectCreated = { newProjectId ->
                                    selectedProjectId = newProjectId
                                    currentTab = OpusNavTab.STUDIO
                                },
                                onOpenProject = { projectId ->
                                    selectedProjectId = projectId
                                    currentTab = OpusNavTab.STUDIO
                                },
                                onUploadLocalVideo = {
                                    showUploadScreen = true
                                },
                                onOpenApiKeySettings = {
                                    showApiKeyDialog = true
                                }
                            )
                        }
                        OpusNavTab.STUDIO -> {
                            ClipStudioScreen(
                                repository = repository,
                                initialProjectId = selectedProjectId
                            )
                        }
                        OpusNavTab.PROJECTS -> {
                            ProjectsScreen(
                                repository = repository,
                                onOpenProjectClips = { projectId ->
                                    selectedProjectId = projectId
                                    currentTab = OpusNavTab.STUDIO
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
