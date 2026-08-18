package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiTemplateRecommendation
import com.example.data.repository.OpusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoProcessingInput(
    val title: String,
    val sourceUrl: String,
    val transcriptOrPrompt: String,
    val durationMinutes: Int,
    val targetPlatform: String,
    val captionTheme: String,
    val layoutType: String = "9:16 Full Screen"
)

data class HomeUiState(
    val isProcessing: Boolean = false,
    val recommendation: AiTemplateRecommendation? = null,
    val completedProjectId: Long? = null,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: OpusRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun startProcessing(input: VideoProcessingInput, autoDetectAiTemplate: Boolean) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                completedProjectId = null,
                errorMessage = null
            )
            try {
                val recommendation = if (autoDetectAiTemplate) {
                    repository.determineOptimalTemplate(
                        title = input.title,
                        transcript = input.transcriptOrPrompt,
                        durationSec = (input.durationMinutes * 60).coerceAtLeast(30)
                    )
                } else {
                    null
                }
                val projectId = repository.processNewVideo(
                    title = input.title,
                    sourceUrl = input.sourceUrl,
                    transcriptOrPrompt = input.transcriptOrPrompt,
                    durationMinutes = input.durationMinutes,
                    targetPlatform = recommendation?.recommendedPlatform ?: input.targetPlatform,
                    captionTheme = recommendation?.recommendedCaptionTheme ?: input.captionTheme,
                    layoutType = recommendation?.recommendedLayout ?: input.layoutType
                )
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    recommendation = recommendation,
                    completedProjectId = projectId
                )
            } catch (cancelled: CancellationException) {
                _uiState.value = _uiState.value.copy(isProcessing = false)
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = error.message ?: "Video processing failed"
                )
            }
        }
    }

    fun setAutoPublishEnabled(config: com.example.data.model.AutoPublishConfig, enabled: Boolean) {
        viewModelScope.launch {
            repository.saveAutoPublishConfig(config.copy(isEnabled = enabled))
        }
    }

    fun consumeCompletedProject() {
        _uiState.value = _uiState.value.copy(completedProjectId = null)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    class Factory(private val repository: OpusRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            return HomeViewModel(repository) as T
        }
    }
}
