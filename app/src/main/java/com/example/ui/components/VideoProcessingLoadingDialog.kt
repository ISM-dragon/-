package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.ProcessingStep
import com.example.ui.theme.OpusBorder
import com.example.ui.theme.OpusDarkSurface
import com.example.ui.theme.OpusDarkSurfaceHighlight
import com.example.ui.theme.OpusDarkSurfaceVariant
import com.example.ui.theme.OpusElectricCyan
import com.example.ui.theme.OpusGold
import com.example.ui.theme.OpusPrimaryViolet
import com.example.ui.theme.OpusTextPrimary
import com.example.ui.theme.OpusTextSecondary
import com.example.ui.theme.OpusViralEmerald
import com.example.ui.theme.OpusVioletGlow
import kotlinx.coroutines.delay

/**
 * Visual loading modal dialog displaying a prominent circular progress indicator
 * that appears during the video processing phase and persists until the API returns a response.
 */
@Composable
fun VideoProcessingLoadingDialog(
    processingStep: ProcessingStep,
    videoTitle: String = "الفيديو الجاري معالجته",
    actualProgressPercent: Int? = null,
    actualStage: String? = null,
    onDismissRequest: () -> Unit = {}
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "loading_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    val stepProgress = actualProgressPercent?.coerceIn(0, 100)?.div(100f) ?: when (processingStep) {
        is ProcessingStep.Idle -> 0f
        is ProcessingStep.Transcribing -> 0.28f
        is ProcessingStep.ScanningHooks -> 0.55f
        is ProcessingStep.CalculatingScores -> 0.78f
        is ProcessingStep.StylingCaptions -> 0.92f
        is ProcessingStep.Completed -> 1.0f
    }
    val stageLabel = actualStage?.takeIf { it.isNotBlank() }?.let(::localizedStageName)
        ?: processingStep.title
    val timelineStep = actualStage?.let(::timelineStepForStage) ?: processingStep.stepNumber

    val animatedProgress by animateFloatAsState(
        targetValue = stepProgress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "animated_step_progress"
    )

    Dialog(
        onDismissRequest = { /* Persistent during video processing - non-cancellable */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("video_processing_dialog"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                OpusPrimaryViolet,
                                OpusElectricCyan,
                                OpusVioletGlow
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(OpusPrimaryViolet.copy(alpha = 0.25f))
                            .border(1.dp, OpusVioletGlow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Processing",
                                tint = OpusElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "محرك الذكاء الاصطناعي Gemini API قيد التشغيل",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusElectricCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Prominent Circular Progress Bar Visual Centerpiece
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(pulseScale)
                            .testTag("video_processing_circular_indicator"),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background track circle
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.size(136.dp),
                            color = OpusDarkSurfaceVariant,
                            strokeWidth = 10.dp,
                            strokeCap = StrokeCap.Round
                        )

                        // Outer animated primary circular progress indicator
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(136.dp),
                            color = OpusElectricCyan,
                            strokeWidth = 10.dp,
                            strokeCap = StrokeCap.Round
                        )

                        // Secondary pulsing spinning halo indicator
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(118.dp)
                                .rotate(ringRotation),
                            color = OpusPrimaryViolet.copy(alpha = 0.65f),
                            strokeWidth = 3.dp,
                            strokeCap = StrokeCap.Round
                        )

                        // Inner Center Content with Percentage and Live Icon
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val percentText = (animatedProgress * 100).toInt()
                            Text(
                                text = "$percentText%",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = OpusTextPrimary
                            )
                            Text(
                                text = actualStage?.takeIf { it.isNotBlank() }?.let(::localizedStageName) ?: when (processingStep) {
                                    is ProcessingStep.Transcribing -> "تفريغ الصوت"
                                    is ProcessingStep.ScanningHooks -> "كشف الخطاف"
                                    is ProcessingStep.CalculatingScores -> "حساب الانتشار"
                                    is ProcessingStep.StylingCaptions -> "توليد الترجمة"
                                    is ProcessingStep.Completed -> "مكتمل!"
                                    else -> "تحليل AI"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusVioletGlow
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Current Stage Title and Description
                    Text(
                        text = stageLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = OpusTextPrimary
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = processingStep.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = OpusTextSecondary
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Processing Steps Timeline Breakdown Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(OpusDarkSurfaceHighlight)
                            .border(1.dp, OpusBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProcessingStageRow(
                                stepIndex = 1,
                                title = "تفريغ وتحليل الصوت (AI Speech)",
                                icon = Icons.Default.GraphicEq,
                                currentStepNum = timelineStep
                            )
                            ProcessingStageRow(
                                stepIndex = 2,
                                title = "فحص منحنى الاحتفاظ والخطاف (Hooks)",
                                icon = Icons.Default.TrendingUp,
                                currentStepNum = timelineStep
                            )
                            ProcessingStageRow(
                                stepIndex = 3,
                                title = "احتساب درجات الفيروسية ومطابقة النماذج",
                                icon = Icons.Default.Psychology,
                                currentStepNum = timelineStep
                            )
                            ProcessingStageRow(
                                stepIndex = 4,
                                title = "توليد الترجمة الحركية وأبعاد 9:16",
                                icon = Icons.Default.Subtitles,
                                currentStepNum = timelineStep
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bottom info bar (elapsed timer & persistence note)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(OpusViralEmerald)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "المعالجة جارية حتى استجابة API...",
                                fontSize = 10.sp,
                                color = OpusTextSecondary
                            )
                        }

                        Text(
                            text = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusElectricCyan
                        )
                    }
                }
            }
        }
    }
}

private fun timelineStepForStage(stage: String): Int = when (stage.uppercase()) {
    "VALIDATING", "IMPORT", "AUDIO_EXTRACTION", "TRANSCRIPTION" -> 1
    "SILENCE_REMOVAL", "SEMANTIC_ANALYSIS", "CLIP_DETECTION" -> 2
    "VIRALITY_SCORING", "HOOK_GENERATION" -> 3
    "CAPTION_SYNTHESIS", "SMART_REFRAMING", "RENDERING_EXPORT" -> 4
    "COMPLETED" -> 5
    else -> 1
}

private fun localizedStageName(stage: String): String = when (stage.uppercase()) {
    "VALIDATING" -> "التحقق من ملف الفيديو"
    "IMPORT" -> "استيراد الفيديو"
    "AUDIO_EXTRACTION" -> "تحليل المسار الصوتي"
    "TRANSCRIPTION" -> "تفريغ الكلام"
    "SILENCE_REMOVAL" -> "تحليل الصمت"
    "SEMANTIC_ANALYSIS" -> "التحليل الدلالي"
    "CLIP_DETECTION" -> "اختيار المقاطع"
    "VIRALITY_SCORING" -> "تقييم المقاطع"
    "HOOK_GENERATION" -> "تحليل Gemini"
    "CAPTION_SYNTHESIS" -> "إنشاء الكابتشن"
    "SMART_REFRAMING" -> "إعادة التأطير"
    "RENDERING_EXPORT" -> "تصدير MP4"
    "RETRY_WAIT" -> "انتظار إعادة المحاولة"
    "COMPLETED" -> "اكتملت المعالجة"
    "FAILED" -> "فشلت المعالجة"
    else -> stage.replace('_', ' ').lowercase()
}

@Composable
private fun ProcessingStageRow(
    stepIndex: Int,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    currentStepNum: Int
) {
    val isDone = currentStepNum > stepIndex
    val isCurrent = currentStepNum == stepIndex
    val isPending = currentStepNum < stepIndex

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isDone -> OpusViralEmerald.copy(alpha = 0.2f)
                        isCurrent -> OpusPrimaryViolet.copy(alpha = 0.35f)
                        else -> OpusDarkSurfaceVariant
                    }
                )
                .border(
                    1.dp,
                    when {
                        isDone -> OpusViralEmerald
                        isCurrent -> OpusElectricCyan
                        else -> OpusBorder
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = OpusViralEmerald,
                    modifier = Modifier.size(12.dp)
                )
            } else if (isCurrent) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = OpusElectricCyan,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stepIndex.toString(),
                    fontSize = 10.sp,
                    color = OpusTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isDone -> OpusViralEmerald
                isCurrent -> OpusTextPrimary
                else -> OpusTextSecondary
            },
            modifier = Modifier.weight(1f)
        )

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(OpusElectricCyan.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "جاري التنفيذ",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusElectricCyan
                )
            }
        }
    }
}
