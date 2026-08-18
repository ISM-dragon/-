package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GatewayConfig
import com.example.data.repository.OpusRepository
import com.example.ui.theme.OpusBorder
import com.example.ui.theme.OpusDarkCanvas
import com.example.ui.theme.OpusDarkSurface
import com.example.ui.theme.OpusElectricCyan
import com.example.ui.theme.OpusHotPink
import com.example.ui.theme.OpusPrimaryViolet
import com.example.ui.theme.OpusTextPrimary
import com.example.ui.theme.OpusTextSecondary
import com.example.ui.theme.OpusViralEmerald
import kotlinx.coroutines.launch

@Composable
fun SocialGatewayScreen(
    repository: OpusRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by repository.gatewayConfig.collectAsState()
    val snapshot by repository.gatewaySnapshot.collectAsState()
    val gatewayError by repository.gatewayError.collectAsState()
    val scope = rememberCoroutineScope()
    var baseUrl by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var token by remember(config.token) { mutableStateOf(config.token) }
    var feedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(config.baseUrl) {
        if (config.baseUrl.isNotBlank()) repository.refreshGatewayStatus()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(OpusDarkCanvas).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = "Gateway", tint = OpusElectricCyan)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("ISM Social Gateway", color = OpusTextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("الحسابات والطابور والنشر التلقائي", color = OpusTextSecondary, fontSize = 12.sp)
                }
                Button(onClick = onBack) { Text("رجوع") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = OpusDarkSurface), border = androidx.compose.foundation.BorderStroke(1.dp, OpusBorder)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = "Security", tint = OpusViralEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text("إعداد Gateway الآمن", color = OpusTextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Text("استخدم HTTPS خارج الشبكة المحلية. لا تضع أسرار المنصات داخل APK.", color = OpusTextSecondary, fontSize = 11.sp)
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Gateway API URL") }, singleLine = true)
                    OutlinedTextField(value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Session token") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    repository.saveGatewayConfig(GatewayConfig(baseUrl, token))
                                    feedback = repository.testGatewayConnection().fold({ it }, { it.localizedMessage ?: "تعذر الاتصال" })
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                            Spacer(Modifier.width(6.dp))
                            Text("حفظ واختبار")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    repository.refreshGatewayStatus()
                                    busy = false
                                }
                            },
                            enabled = !busy && config.baseUrl.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            Spacer(Modifier.width(6.dp))
                            Text("تحديث")
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = OpusElectricCyan)
                    if (feedback.isNotBlank()) Text(feedback, color = OpusViralEmerald, fontSize = 12.sp)
                    if (gatewayError.isNotBlank()) Text(gatewayError, color = OpusHotPink, fontSize = 12.sp)
                }
            }
        }
        snapshot?.let { state ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GatewayMetric("الحسابات", state.connectedAccounts.toString(), Modifier.weight(1f))
                    GatewayMetric("مجدول", (state.statusCounts["scheduled"] ?: 0).toString(), Modifier.weight(1f))
                    GatewayMetric("منشور", (state.statusCounts["published"] ?: 0).toString(), Modifier.weight(1f))
                    GatewayMetric("فشل", (state.statusCounts["failed"] ?: 0).toString(), Modifier.weight(1f))
                }
            }
            item { Text("صحة الحسابات", color = OpusTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            items(state.accounts) { account ->
                Card(colors = CardDefaults.cardColors(containerColor = OpusDarkSurface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${account.platform} · ${account.accountName}", color = OpusTextPrimary, fontWeight = FontWeight.Bold)
                            Text(account.status, color = if (account.status == "connected") OpusViralEmerald else OpusHotPink, fontSize = 11.sp)
                        }
                        Text("${account.publishCount}/${account.dailyLimit} اليوم · فاصل ${account.minGapSeconds} ثانية", color = OpusTextSecondary, fontSize = 11.sp)
                        account.pauseReason?.let { Text(it, color = OpusHotPink, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            item { Text("آخر المنشورات", color = OpusTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            items(state.recentPosts) { post ->
                Card(colors = CardDefaults.cardColors(containerColor = OpusDarkSurface), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(post.title.ifBlank { post.platform }, color = OpusTextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${post.platform} · ${post.account} · ${post.scheduledAt ?: "بدون موعد"}", color = OpusTextSecondary, fontSize = 11.sp, maxLines = 2)
                            post.error?.let { Text(it, color = OpusHotPink, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                        Text(post.status, color = if (post.status == "published") OpusViralEmerald else OpusElectricCyan, fontSize = 11.sp)
                    }
                }
            }
        } ?: item {
            Text("احفظ رابط Gateway ثم اضغط تحديث لعرض حالة الحسابات والمنشورات.", color = OpusTextSecondary, fontSize = 12.sp)
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun GatewayMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = OpusPrimaryViolet.copy(alpha = 0.28f))) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = OpusElectricCyan, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(label, color = OpusTextSecondary, fontSize = 10.sp)
        }
    }
}
