package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.data.model.AiProviderConfig
import com.example.data.model.AiProviderType
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeySettingsDialog(
    repository: OpusRepository,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val googleFlowCredits by repository.googleFlowCredits.collectAsState()
    val aiProviders by repository.aiProviders.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Credits & Flow, 1 = Multi-Provider Keys

    // Add New Provider State
    var isAddingProvider by remember { mutableStateOf(false) }
    var selectedProviderType by remember { mutableStateOf(AiProviderType.GEMINI) }
    var newProviderName by remember { mutableStateOf(selectedProviderType.displayName) }
    var newProviderKey by remember { mutableStateOf("") }
    var newProviderModel by remember { mutableStateOf(selectedProviderType.defaultModel) }
    var newProviderCustomUrl by remember { mutableStateOf("") }
    var isTypeDropdownExpanded by remember { mutableStateOf(false) }
    var isTestingNewProvider by remember { mutableStateOf(false) }
    var newProviderTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isKeyVisible by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, OpusVioletGlow.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .testTag("api_key_settings_dialog"),
            colors = CardDefaults.cardColors(containerColor = OpusDarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(OpusPrimaryViolet, OpusElectricCyan)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Google Flow",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Google Flow & AI Credits",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = OpusTextPrimary
                                )
                            )
                            Text(
                                text = "إدارة رصيد الحساب والمزودين الاحتياطيين",
                                fontSize = 11.sp,
                                color = OpusTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("close_api_key_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = OpusTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = OpusDarkSurfaceVariant,
                    contentColor = OpusElectricCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = OpusElectricCyan,
                            height = 3.dp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (selectedTab == 0) OpusElectricCyan else OpusTextSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "رصيد Google Flow",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) OpusElectricCyan else OpusTextSecondary
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (selectedTab == 1) OpusElectricCyan else OpusTextSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "المزودون والمفاتيح (${aiProviders.count { it.isEnabled }})",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) OpusElectricCyan else OpusTextSecondary
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // TAB 1: GOOGLE FLOW CREDITS & BALANCE
                    GoogleFlowCreditsSection(
                        credits = googleFlowCredits,
                        onResetCredits = {
                            coroutineScope.launch {
                                repository.resetGoogleFlowCredits()
                                Toast.makeText(context, "تمت إعادة شحن رصيد Google Flow بنجاح!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onToggleFailover = { enabled ->
                            coroutineScope.launch {
                                repository.saveGoogleFlowCredits(googleFlowCredits.copy(isAutoFailoverEnabled = enabled))
                            }
                        }
                    )
                } else {
                    // TAB 2: MULTI-PROVIDER FAILOVER POOL
                    MultiProviderManagementSection(
                        providers = aiProviders,
                        isAddingProvider = isAddingProvider,
                        onSetAddingProvider = { isAddingProvider = it },
                        selectedProviderType = selectedProviderType,
                        onSelectProviderType = { type ->
                            selectedProviderType = type
                            newProviderName = type.displayName
                            newProviderModel = type.defaultModel
                            newProviderKey = ""
                            newProviderTestResult = null
                        },
                        newProviderName = newProviderName,
                        onNameChange = { newProviderName = it },
                        newProviderKey = newProviderKey,
                        onKeyChange = {
                            newProviderKey = it
                            newProviderTestResult = null
                        },
                        newProviderModel = newProviderModel,
                        onModelChange = { newProviderModel = it },
                        newProviderCustomUrl = newProviderCustomUrl,
                        onCustomUrlChange = { newProviderCustomUrl = it },
                        isTypeDropdownExpanded = isTypeDropdownExpanded,
                        onExpandDropdown = { isTypeDropdownExpanded = it },
                        isTesting = isTestingNewProvider,
                        testResult = newProviderTestResult,
                        isKeyVisible = isKeyVisible,
                        onToggleKeyVisible = { isKeyVisible = !isKeyVisible },
                        onTestConnection = {
                            if (newProviderKey.isBlank()) {
                                Toast.makeText(context, "يرجى كتابة المفتاح أولاً", Toast.LENGTH_SHORT).show()
                                return@MultiProviderManagementSection
                            }
                            isTestingNewProvider = true
                            newProviderTestResult = null
                            val testConfig = AiProviderConfig(
                                name = newProviderName,
                                providerType = selectedProviderType.name,
                                apiKey = newProviderKey,
                                modelName = newProviderModel,
                                customBaseUrl = newProviderCustomUrl
                            )
                            coroutineScope.launch {
                                val res = repository.testAiProvider(testConfig)
                                newProviderTestResult = res
                                isTestingNewProvider = false
                            }
                        },
                        onSaveNewProvider = {
                            if (newProviderKey.isBlank()) {
                                Toast.makeText(context, "الرجاء إدخال مفتاح الـ API", Toast.LENGTH_SHORT).show()
                                return@MultiProviderManagementSection
                            }
                            coroutineScope.launch {
                                val newConfig = AiProviderConfig(
                                    name = newProviderName.ifBlank { selectedProviderType.displayName },
                                    providerType = selectedProviderType.name,
                                    apiKey = newProviderKey.trim(),
                                    modelName = newProviderModel.ifBlank { selectedProviderType.defaultModel },
                                    customBaseUrl = newProviderCustomUrl.trim(),
                                    priority = aiProviders.size + 1,
                                    isEnabled = true
                                )
                                repository.addOrUpdateAiProvider(newConfig)
                                Toast.makeText(context, "تمت إضافة المزود بنجاح لنظام التبديل التلقائي!", Toast.LENGTH_SHORT).show()
                                isAddingProvider = false
                                newProviderKey = ""
                                newProviderTestResult = null
                            }
                        },
                        onToggleProvider = { id, enabled ->
                            coroutineScope.launch {
                                repository.toggleAiProvider(id, enabled)
                            }
                        },
                        onDeleteProvider = { id ->
                            coroutineScope.launch {
                                repository.removeAiProvider(id)
                                Toast.makeText(context, "تم حذف المزود", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onTestExistingProvider = { provider ->
                            coroutineScope.launch {
                                Toast.makeText(context, "جاري فحص اتصال ${provider.name}...", Toast.LENGTH_SHORT).show()
                                val res = repository.testAiProvider(provider)
                                Toast.makeText(context, "${provider.name}: ${res.second}", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleFlowCreditsSection(
    credits: com.example.data.model.GoogleFlowCreditInfo,
    onResetCredits: () -> Unit,
    onToggleFailover: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Main Credit Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(OpusElectricCyan, OpusPrimaryViolet)),
                    RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "رصيد المعالجة المتبقي",
                            fontSize = 12.sp,
                            color = OpusTextSecondary
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${credits.remainingCreditsMinutes}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = if (credits.remainingCreditsMinutes > 30) OpusViralEmerald else if (credits.remainingCreditsMinutes > 0) OpusGold else OpusHotPink
                            )
                            Text(
                                text = " / ${credits.totalCreditsMinutes} دقيقة",
                                fontSize = 14.sp,
                                color = OpusTextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (credits.remainingCreditsMinutes > 30) OpusViralEmerald.copy(alpha = 0.15f)
                                else OpusHotPink.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (credits.remainingCreditsMinutes > 30) OpusViralEmerald else OpusHotPink,
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (credits.isExhausted) "منتهي (Exhausted)" else "نشط (Active)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (credits.remainingCreditsMinutes > 30) OpusViralEmerald else OpusHotPink
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { credits.creditPercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (credits.creditPercentage > 0.3f) OpusElectricCyan else OpusHotPink,
                    trackColor = OpusDarkSurfaceHighlight,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Statistics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(OpusDarkSurfaceHighlight)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("الطلبات المتبقية", fontSize = 10.sp, color = OpusTextSecondary)
                            Text(
                                "${credits.remainingRequestsCount} / ${credits.totalRequestsLimit}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(OpusDarkSurfaceHighlight)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("معدل السرعة (RPM)", fontSize = 10.sp, color = OpusTextSecondary)
                            Text(
                                "${credits.rpmLimit} req/min",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusElectricCyan
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Failover Setting Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, OpusBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Failover",
                        tint = OpusElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "التبديل التلقائي عند انتهاء الرصيد",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                        Text(
                            text = "محاولة مزود بديل عند الفشل إذا كان مفتاحه صالحاً ومفعّلاً",
                            fontSize = 10.sp,
                            color = OpusTextSecondary
                        )
                    }
                }

                Switch(
                    checked = credits.isAutoFailoverEnabled,
                    onCheckedChange = onToggleFailover,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = OpusElectricCyan,
                        uncheckedTrackColor = OpusDarkSurfaceHighlight
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onResetCredits,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpusGold)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset usage data",
                    tint = OpusGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "مسح بيانات الحصة",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusGold
                )
            }

            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Upgrade",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Google AI Studio",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiProviderManagementSection(
    providers: List<AiProviderConfig>,
    isAddingProvider: Boolean,
    onSetAddingProvider: (Boolean) -> Unit,
    selectedProviderType: AiProviderType,
    onSelectProviderType: (AiProviderType) -> Unit,
    newProviderName: String,
    onNameChange: (String) -> Unit,
    newProviderKey: String,
    onKeyChange: (String) -> Unit,
    newProviderModel: String,
    onModelChange: (String) -> Unit,
    newProviderCustomUrl: String,
    onCustomUrlChange: (String) -> Unit,
    isTypeDropdownExpanded: Boolean,
    onExpandDropdown: (Boolean) -> Unit,
    isTesting: Boolean,
    testResult: Pair<Boolean, String>?,
    isKeyVisible: Boolean,
    onToggleKeyVisible: () -> Unit,
    onTestConnection: () -> Unit,
    onSaveNewProvider: () -> Unit,
    onToggleProvider: (String, Boolean) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onTestExistingProvider: (AiProviderConfig) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Top Banner / Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "سلسلة المزودين البديلة (Failover Chain)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpusTextPrimary
                )
                Text(
                    text = "في حال نفاذ رصيد Google يتم استخدام المفاتيح بالترتيب",
                    fontSize = 10.sp,
                    color = OpusTextSecondary
                )
            }

            if (!isAddingProvider) {
                Button(
                    onClick = { onSetAddingProvider(true) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OpusElectricCyan),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Provider",
                        tint = OpusDarkCanvas,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة مزود", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OpusDarkCanvas)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Form to Add New Provider
        AnimatedVisibility(visible = isAddingProvider) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, OpusElectricCyan, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceHighlight)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "إضافة مفتاح API جديد",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusElectricCyan
                        )
                        IconButton(
                            onClick = { onSetAddingProvider(false) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = OpusTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Provider Dropdown
                    Text("مزود الخدمة (Provider):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OpusTextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))

                    ExposedDropdownMenuBox(
                        expanded = isTypeDropdownExpanded,
                        onExpandedChange = onExpandDropdown
                    ) {
                        OutlinedTextField(
                            value = selectedProviderType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTypeDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = OpusTextPrimary,
                                unfocusedTextColor = OpusTextPrimary,
                                focusedBorderColor = OpusElectricCyan,
                                unfocusedBorderColor = OpusBorder,
                                focusedContainerColor = OpusDarkSurfaceVariant,
                                unfocusedContainerColor = OpusDarkSurfaceVariant
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = isTypeDropdownExpanded,
                            onDismissRequest = { onExpandDropdown(false) },
                            modifier = Modifier.background(OpusDarkSurface)
                        ) {
                            AiProviderType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(type.displayName, color = OpusTextPrimary, fontWeight = FontWeight.Bold)
                                            Text("Default Model: ${type.defaultModel}", color = OpusTextSecondary, fontSize = 10.sp)
                                        }
                                    },
                                    onClick = {
                                        onSelectProviderType(type)
                                        onExpandDropdown(false)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // API Key Field
                    Text("مفتاح الـ API Key:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OpusTextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = newProviderKey,
                        onValueChange = onKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(selectedProviderType.placeholderKey, fontSize = 11.sp, color = OpusTextSecondary.copy(alpha = 0.5f)) },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OpusTextPrimary,
                            unfocusedTextColor = OpusTextPrimary,
                            focusedBorderColor = OpusElectricCyan,
                            unfocusedBorderColor = OpusBorder,
                            focusedContainerColor = OpusDarkSurfaceVariant,
                            unfocusedContainerColor = OpusDarkSurfaceVariant
                        ),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onToggleKeyVisible) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = OpusTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = clipboard.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                            if (pasted.isNotBlank()) onKeyChange(pasted.trim())
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = OpusElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Model Name Field
                    Text("اسم النموذج (Model Name):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OpusTextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newProviderModel,
                        onValueChange = onModelChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OpusTextPrimary,
                            unfocusedTextColor = OpusTextPrimary,
                            focusedBorderColor = OpusElectricCyan,
                            unfocusedBorderColor = OpusBorder,
                            focusedContainerColor = OpusDarkSurfaceVariant,
                            unfocusedContainerColor = OpusDarkSurfaceVariant
                        )
                    )

                    if (selectedProviderType == AiProviderType.CUSTOM) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("رابط الـ API المخصص (Base URL):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OpusTextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newProviderCustomUrl,
                            onValueChange = onCustomUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://api.your-host.com/v1/chat/completions", fontSize = 10.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = OpusTextPrimary,
                                unfocusedTextColor = OpusTextPrimary,
                                focusedBorderColor = OpusElectricCyan,
                                unfocusedBorderColor = OpusBorder,
                                focusedContainerColor = OpusDarkSurfaceVariant,
                                unfocusedContainerColor = OpusDarkSurfaceVariant
                            )
                        )
                    }

                    if (selectedProviderType.docsUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "احصل على المفتاح مجاناً من: ${selectedProviderType.docsUrl}",
                            fontSize = 10.sp,
                            color = OpusElectricCyan,
                            modifier = Modifier.clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedProviderType.docsUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        )
                    }

                    // Test feedback
                    if (testResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val (success, msg) = testResult
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (success) OpusViralEmerald.copy(alpha = 0.15f) else OpusHotPink.copy(alpha = 0.15f))
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (success) OpusViralEmerald else OpusHotPink,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = msg,
                                    fontSize = 11.sp,
                                    color = if (success) OpusViralEmerald else OpusHotPink
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTestConnection,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isTesting && newProviderKey.isNotBlank(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OpusElectricCyan)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = OpusElectricCyan, strokeWidth = 2.dp)
                            } else {
                                Text("فحص المفتاح", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OpusElectricCyan)
                            }
                        }

                        Button(
                            onClick = onSaveNewProvider,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OpusPrimaryViolet),
                            enabled = newProviderKey.isNotBlank()
                        ) {
                            Text("حفظ وتفعيل", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // List of Configured Providers
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            providers.forEachIndexed { index, provider ->
                val hasKey = provider.apiKey.isNotBlank()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (provider.isEnabled && hasKey) OpusVioletGlow.copy(alpha = 0.5f) else OpusBorder,
                            RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (provider.isEnabled && hasKey) OpusDarkSurfaceVariant else OpusDarkSurface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == 0) OpusPrimaryViolet.copy(alpha = 0.2f)
                                        else OpusElectricCyan.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (index == 0) OpusGold else OpusElectricCyan
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = provider.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OpusTextPrimary
                                    )
                                    if (index == 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(OpusGold.copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text("أساسي (Primary)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = OpusGold)
                                        }
                                    }
                                }

                                Text(
                                    text = if (hasKey) "المفتاح: ${provider.apiKey.take(7)}... (Model: ${provider.modelName})" else "لم يتم إدخال مفتاح API بعد",
                                    fontSize = 10.sp,
                                    color = if (hasKey) OpusTextSecondary else OpusHotPink.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasKey) {
                                IconButton(
                                    onClick = { onTestExistingProvider(provider) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Test",
                                        tint = OpusElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Switch(
                                checked = provider.isEnabled && hasKey,
                                onCheckedChange = { onToggleProvider(provider.id, it) },
                                enabled = hasKey,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = OpusElectricCyan,
                                    uncheckedTrackColor = OpusDarkSurfaceHighlight
                                )
                            )

                            if (index > 0) {
                                IconButton(
                                    onClick = { onDeleteProvider(provider.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = OpusHotPink.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
