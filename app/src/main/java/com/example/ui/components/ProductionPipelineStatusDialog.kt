package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.PipelineJob
import com.example.domain.model.PipelineStageProgress
import com.example.domain.model.PipelineStageStatus
import com.example.domain.model.PipelineStageType
import com.example.ui.theme.OpusBorder
import com.example.ui.theme.OpusDarkSurface
import com.example.ui.theme.OpusDarkSurfaceHighlight
import com.example.ui.theme.OpusDarkSurfaceVariant
import com.example.ui.theme.OpusElectricCyan
import com.example.ui.theme.OpusPrimaryViolet
import com.example.ui.theme.OpusTextPrimary
import com.example.ui.theme.OpusTextSecondary
import com.example.ui.theme.OpusViralEmerald

@Composable
fun ProductionPipelineStatusDialog(
    job: PipelineJob,
    videoTitle: String,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (job.overallStatus == PipelineStageStatus.COMPLETED || job.overallStatus == PipelineStageStatus.FAILED) {
                onDismiss()
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, OpusBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = OpusDarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "خط أنابيب المعالجة الذكية (11 مرحلة)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                        Text(
                            text = videoTitle,
                            fontSize = 12.sp,
                            color = OpusElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (job.overallStatus) {
                                    PipelineStageStatus.COMPLETED -> OpusViralEmerald.copy(alpha = 0.2f)
                                    PipelineStageStatus.FAILED -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                    PipelineStageStatus.CANCELLED -> OpusTextSecondary.copy(alpha = 0.2f)
                                    else -> OpusPrimaryViolet.copy(alpha = 0.2f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when (job.overallStatus) {
                                PipelineStageStatus.COMPLETED -> "اكتمل بنجاح"
                                PipelineStageStatus.FAILED -> "فشل"
                                PipelineStageStatus.CANCELLED -> "ملغي"
                                else -> "${(job.overallProgress * 100).toInt()}%"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (job.overallStatus) {
                                PipelineStageStatus.COMPLETED -> OpusViralEmerald
                                PipelineStageStatus.FAILED -> Color(0xFFEF4444)
                                PipelineStageStatus.CANCELLED -> OpusTextSecondary
                                else -> OpusElectricCyan
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { job.overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = OpusElectricCyan,
                    trackColor = OpusDarkSurfaceHighlight,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 11-Stage List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(PipelineStageType.values()) { stageType ->
                        val stageProgress = job.stages[stageType] ?: PipelineStageProgress(stage = stageType)
                        PipelineStageRowItem(stageProgress = stageProgress)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error Message if failed
                if (job.overallStatus == PipelineStageStatus.FAILED && job.errorDetails != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = job.errorDetails, fontSize = 11.sp, color = Color(0xFFFCA5A5), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (job.overallStatus == PipelineStageStatus.PROCESSING) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إلغاء المعالجة", fontSize = 12.sp)
                        }
                    } else if (job.overallStatus == PipelineStageStatus.FAILED) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إعادة المحاولة", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إغلاق", fontSize = 12.sp, color = OpusTextSecondary)
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = OpusViralEmerald)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Done", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("فتح المقاطع الجاهزة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PipelineStageRowItem(stageProgress: PipelineStageProgress) {
    val isDone = stageProgress.status == PipelineStageStatus.COMPLETED
    val isCurrent = stageProgress.status == PipelineStageStatus.PROCESSING
    val isFailed = stageProgress.status == PipelineStageStatus.FAILED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isCurrent -> OpusDarkSurfaceHighlight
                    isDone -> OpusViralEmerald.copy(alpha = 0.05f)
                    isFailed -> Color(0xFFEF4444).copy(alpha = 0.08f)
                    else -> OpusDarkSurfaceVariant.copy(alpha = 0.5f)
                }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isDone -> OpusViralEmerald.copy(alpha = 0.2f)
                        isCurrent -> OpusElectricCyan.copy(alpha = 0.2f)
                        isFailed -> Color(0xFFEF4444).copy(alpha = 0.2f)
                        else -> OpusDarkSurfaceHighlight
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isDone -> Icon(Icons.Default.Check, contentDescription = "Done", tint = OpusViralEmerald, modifier = Modifier.size(12.dp))
                isCurrent -> CircularProgressIndicator(modifier = Modifier.size(12.dp), color = OpusElectricCyan, strokeWidth = 1.5.dp)
                isFailed -> Icon(Icons.Default.Warning, contentDescription = "Failed", tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                else -> Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(OpusTextSecondary))
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stageProgress.stage.titleAr,
                fontSize = 11.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isDone -> OpusViralEmerald
                    isCurrent -> OpusTextPrimary
                    isFailed -> Color(0xFFEF4444)
                    else -> OpusTextSecondary
                }
            )
            if (stageProgress.message.isNotBlank()) {
                Text(
                    text = stageProgress.message,
                    fontSize = 9.sp,
                    color = OpusTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isCurrent) {
            Text(
                text = "${(stageProgress.progress * 100).toInt()}%",
                fontSize = 10.sp,
                color = OpusElectricCyan,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
