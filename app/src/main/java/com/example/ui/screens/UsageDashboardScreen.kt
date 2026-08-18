package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiUsageAggregate
import com.example.data.model.ProcessingJobEntity
import com.example.data.repository.OpusRepository
import com.example.ui.theme.OpusDarkCanvas
import com.example.ui.theme.OpusDarkSurface
import com.example.ui.theme.OpusElectricCyan
import com.example.ui.theme.OpusGold
import com.example.ui.theme.OpusHotPink
import com.example.ui.theme.OpusTextPrimary
import com.example.ui.theme.OpusTextSecondary
import com.example.ui.theme.OpusViralEmerald
import java.text.NumberFormat
import java.util.Locale

@Composable
fun UsageDashboardScreen(
    repository: OpusRepository,
    modifier: Modifier = Modifier
) {
    val aggregates by repository.observeRecentAiUsageAggregates(30).collectAsState(initial = emptyList())
    val jobs by repository.processingJobs.collectAsState(initial = emptyList())
    val activeJobs = remember(jobs) {
        jobs.filter { it.status == ProcessingJobEntity.STATUS_QUEUED || it.status == ProcessingJobEntity.STATUS_RUNNING }
    }
    val totalTokens = aggregates.sumOf { it.totalTokens }
    val totalRequests = aggregates.sumOf { it.requests }
    val totalFailures = aggregates.sumOf { it.failures }
    val estimatedCost = aggregates.sumOf { it.estimatedCostUsd }
    val averageLatency = if (aggregates.isEmpty()) 0.0 else {
        aggregates.sumOf { it.averageLatencyMs * it.requests } / totalRequests.coerceAtLeast(1)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpusDarkCanvas)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Usage Dashboard", color = OpusTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "مراقبة آخر 30 يومًا — جاهزة لإضافة مزودين ونماذج مستقبلًا",
                color = OpusTextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("التوكنز", formatInteger(totalTokens), OpusElectricCyan, Modifier.weight(1f))
                MetricCard("التكلفة", formatUsd(estimatedCost), OpusGold, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("الطلبات", totalRequests.toString(), OpusViralEmerald, Modifier.weight(1f))
                MetricCard("الفشل", totalFailures.toString(), OpusHotPink, Modifier.weight(1f))
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("المعالجة الحالية", color = OpusTextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (activeJobs.isEmpty()) {
                        Text("لا توجد مهام قيد التنفيذ حاليًا", color = OpusTextSecondary, fontSize = 13.sp)
                    } else {
                        activeJobs.take(5).forEach { job ->
                            JobProgressRow(job)
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("ملخص الأداء", color = OpusTextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SummaryLine("متوسط زمن الاستجابة", "${averageLatency.toInt()} ms")
                    SummaryLine("عدد المزودين المستخدمين", aggregates.map { it.provider }.distinct().size.toString())
                    SummaryLine("عدد النماذج المستخدمة", aggregates.map { it.model }.distinct().size.toString())
                }
            }
        }

        item {
            Text("الاستهلاك حسب المزود والنموذج", color = OpusTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }

        if (aggregates.isEmpty()) {
            item {
                EmptyUsageCard()
            }
        } else {
            items(aggregates, key = { "${it.provider}:${it.model}" }) { aggregate ->
                ProviderUsageCard(aggregate)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun MetricCard(title: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = OpusTextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun JobProgressRow(job: ProcessingJobEntity) {
    val progress = job.progress.coerceIn(0, 100) / 100f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(job.title, color = OpusTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            Text(job.currentStage.ifBlank { job.status }, color = OpusTextSecondary, fontSize = 11.sp)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                color = OpusElectricCyan,
                trackColor = OpusTextSecondary.copy(alpha = 0.2f)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("${job.progress}%", color = OpusElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProviderUsageCard(aggregate: AiUsageAggregate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(aggregate.provider, color = OpusElectricCyan, fontWeight = FontWeight.Bold)
                    Text(aggregate.model, color = OpusTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatUsd(aggregate.estimatedCostUsd), color = OpusGold, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            SummaryLine("التوكنز", formatInteger(aggregate.totalTokens))
            SummaryLine("الطلبات الناجحة / الفاشلة", "${aggregate.successes} / ${aggregate.failures}")
            SummaryLine("متوسط الكمون", "${aggregate.averageLatencyMs.toInt()} ms")
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = OpusTextSecondary, fontSize = 12.sp)
        Text(value, color = OpusTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyUsageCard() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("ستظهر بيانات الاستخدام بعد تنفيذ أول طلب AI فعلي", color = OpusTextSecondary, fontSize = 13.sp)
    }
}

private fun formatInteger(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)
private fun formatUsd(value: Double): String = String.format(Locale.US, "$%.4f", value)
