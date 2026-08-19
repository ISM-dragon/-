package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.mutableStateMapOf
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
fun ApiManagementSettingsScreen(
    repository: OpusRepository,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val aiProviders by repository.aiProviders.collectAsState()
    val googleFlowCredits by repository.googleFlowCredits.collectAsState()

    var selectedFilterTab by remember { mutableIntStateOf(0) } // 0: All Providers, 1: Major LLMs (OpenAI/Anthropic/Gemini), 2: High-Speed/Routing
    var isAddingCustomProvider by remember { mutableStateOf(false) }

    // State tracking for per-provider editing & testing
    val draftKeys = remember { mutableStateMapOf<String, String>() }
    val draftModels = remember { mutableStateMapOf<String, String>() }
    val keyVisibility = remember { mutableStateMapOf<String, Boolean>() }
    val testingState = remember { mutableStateMapOf<String, Boolean>() }
    val testResults = remember { mutableStateMapOf<String, Pair<Boolean, String>>() }

    // New custom provider fields
    var newType by remember { mutableStateOf(AiProviderType.OPENAI) }
    var newName by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    var newModel by remember { mutableStateOf("") }
    var newCustomUrl by remember { mutableStateOf("") }
    var newTypeExpanded by remember { mutableStateOf(false) }

    val configuredCount = aiProviders.count { it.apiKey.isNotBlank() && it.isEnabled }
    val totalEstimatedAvailableUsd = aiProviders
        .filter { it.creditUnit == "$" && it.isEnabled && it.apiKey.isNotBlank() }
        .sumOf { it.remainingCredits }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpusDarkCanvas)
            .testTag("api_management_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // TOP HERO BANNER & HEADER
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(OpusPrimaryViolet, OpusElectricCyan)),
                        RoundedCornerShape(20.dp)
                    )
                    .testTag("api_hero_card"),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onBack != null) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("api_screen_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = OpusTextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(OpusPrimaryViolet, OpusElectricCyan)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "API & Provider Management",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OpusTextPrimary
                                )
                                Text(
                                    text = "Real-Time Balance & Multi-Model Engine",
                                    fontSize = 11.sp,
                                    color = OpusElectricCyan,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (configuredCount > 0) OpusViralEmerald.copy(alpha = 0.15f)
                                    else OpusGold.copy(alpha = 0.15f)
                                )
                                .border(
                                    1.dp,
                                    if (configuredCount > 0) OpusViralEmerald else OpusGold,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (configuredCount > 0) "$configuredCount Active Keys" else "لا توجد مفاتيح",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (configuredCount > 0) OpusViralEmerald else OpusGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "أضف مفاتيحك الخاصة للمزودات المدعومة. تظهر بيانات الاستخدام والحصة فقط عندما يعيدها المزود فعلياً، ويمكن تجربة مزود بديل عند فشل الطلب.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = OpusTextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Aggregate Summary Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Card 1: Google Flow Minutes
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(OpusDarkSurfaceHighlight)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Google Flow Quota", fontSize = 10.sp, color = OpusTextSecondary)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "${googleFlowCredits.remainingCreditsMinutes}",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = OpusViralEmerald
                                    )
                                    Text(" / ${googleFlowCredits.totalCreditsMinutes} min", fontSize = 10.sp, color = OpusTextSecondary, modifier = Modifier.padding(bottom = 2.dp, start = 2.dp))
                                }
                            }
                        }

                        // Card 2: Combined USD Pool
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(OpusDarkSurfaceHighlight)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Combined API Pool", fontSize = 10.sp, color = OpusTextSecondary)
                                Text(
                                    if (totalEstimatedAvailableUsd > 0.0) "$${String.format(Locale.ROOT, "%.2f", totalEstimatedAvailableUsd)}" else "غير متاح",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OpusElectricCyan
                                )
                            }
                        }

                        // Card 3: Auto-Failover Switch
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(OpusDarkSurfaceHighlight)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text("Auto-Failover", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OpusTextPrimary)
                                    Text(if (googleFlowCredits.isAutoFailoverEnabled) "Protected" else "Off", fontSize = 9.sp, color = OpusTextSecondary)
                                }
                                Switch(
                                    checked = googleFlowCredits.isAutoFailoverEnabled,
                                    onCheckedChange = { enabled ->
                                        coroutineScope.launch {
                                            repository.saveGoogleFlowCredits(googleFlowCredits.copy(isAutoFailoverEnabled = enabled))
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = OpusElectricCyan,
                                        uncheckedTrackColor = OpusDarkSurfaceVariant
                                    ),
                                    modifier = Modifier.size(width = 38.dp, height = 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // PROVIDER FILTER TABS & QUICK ACTIONS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabRow(
                    selectedTabIndex = selectedFilterTab,
                    containerColor = OpusDarkSurfaceVariant,
                    contentColor = OpusElectricCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedFilterTab]),
                            color = OpusElectricCyan,
                            height = 2.5.dp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedFilterTab == 0,
                        onClick = { selectedFilterTab = 0 },
                        text = { Text("All (${aiProviders.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedFilterTab == 1,
                        onClick = { selectedFilterTab = 1 },
                        text = { Text("Major (OpenAI/Claude/Gemini)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedFilterTab == 2,
                        onClick = { selectedFilterTab = 2 },
                        text = { Text("High-Speed Routers", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { isAddingCustomProvider = !isAddingCustomProvider },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(OpusElectricCyan)
                        .size(38.dp)
                        .testTag("add_custom_provider_button")
                ) {
                    Icon(
                        imageVector = if (isAddingCustomProvider) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Add Provider",
                        tint = OpusDarkCanvas,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ADD NEW CUSTOM PROVIDER DRAWER (ANIMATED)
        item {
            AnimatedVisibility(visible = isAddingCustomProvider) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, OpusElectricCyan, RoundedCornerShape(16.dp))
                        .testTag("add_provider_form"),
                    colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceHighlight)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = OpusElectricCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add New AI Provider / Custom Key", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OpusTextPrimary)
                            }
                            IconButton(onClick = { isAddingCustomProvider = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = OpusTextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Type Dropdown
                        Text("Provider Family:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OpusTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = newTypeExpanded,
                            onExpandedChange = { newTypeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = newType.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = newTypeExpanded) },
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
                                expanded = newTypeExpanded,
                                onDismissRequest = { newTypeExpanded = false },
                                modifier = Modifier.background(OpusDarkSurface)
                            ) {
                                AiProviderType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.displayName, color = OpusTextPrimary, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            newType = type
                                            newName = type.displayName
                                            newModel = type.defaultModel
                                            newTypeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // API Key Field
                        Text("API Key:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OpusTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newKey,
                            onValueChange = { newKey = it },
                            placeholder = { Text(newType.placeholderKey, fontSize = 11.sp, color = OpusTextSecondary.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                        if (pasted.isNotBlank()) newKey = pasted.trim()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = OpusElectricCyan, modifier = Modifier.size(16.dp))
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = OpusTextPrimary,
                                unfocusedTextColor = OpusTextPrimary,
                                focusedBorderColor = OpusElectricCyan,
                                unfocusedBorderColor = OpusBorder,
                                focusedContainerColor = OpusDarkSurfaceVariant,
                                unfocusedContainerColor = OpusDarkSurfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Model Field
                        Text("Model Name:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OpusTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newModel.ifBlank { newType.defaultModel },
                            onValueChange = { newModel = it },
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

                        if (newType == AiProviderType.CUSTOM) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Base URL (e.g. Ollama/vLLM):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OpusTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = newCustomUrl,
                                onValueChange = { newCustomUrl = it },
                                placeholder = { Text("http://192.168.1.100:11434/v1/chat/completions", fontSize = 10.sp) },
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
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (newKey.isBlank()) {
                                    Toast.makeText(context, "Please enter an API key", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                coroutineScope.launch {
                                    val config = AiProviderConfig(
                                        name = newName.ifBlank { newType.displayName },
                                        providerType = newType.name,
                                        apiKey = newKey.trim(),
                                        modelName = newModel.ifBlank { newType.defaultModel },
                                        customBaseUrl = newCustomUrl.trim(),
                                        priority = aiProviders.size + 1,
                                        isEnabled = true,
                                        totalCreditsAllocated = 0.0,
                                        usedCredits = 0.0,
                                        creditUnit = newType.unitCurrency,
                                        balanceStatus = "Configured"
                                    )
                                    repository.addOrUpdateAiProvider(config)
                                    Toast.makeText(context, "Added ${config.name} successfully!", Toast.LENGTH_SHORT).show()
                                    isAddingCustomProvider = false
                                    newKey = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OpusElectricCyan)
                        ) {
                            Text("Save & Add to Failover Pool", fontWeight = FontWeight.Bold, color = OpusDarkCanvas)
                        }
                    }
                }
            }
        }

        // FILTERED LIST OF PROVIDER CARDS
        val displayedProviders = when (selectedFilterTab) {
            1 -> aiProviders.filter {
                it.providerType in listOf(AiProviderType.OPENAI.name, AiProviderType.ANTHROPIC.name, AiProviderType.GEMINI.name)
            }
            2 -> aiProviders.filter {
                it.providerType in listOf(AiProviderType.OPENROUTER.name, AiProviderType.GROQ.name, AiProviderType.MISTRAL.name, AiProviderType.CUSTOM.name)
            }
            else -> aiProviders
        }

        itemsIndexed(displayedProviders, key = { _, item -> item.id }) { index, provider ->
            val providerType = try {
                AiProviderType.valueOf(provider.providerType)
            } catch (_: Exception) {
                AiProviderType.CUSTOM
            }

            val currentDraftKey = draftKeys[provider.id] ?: provider.apiKey
            val currentDraftModel = draftModels[provider.id] ?: provider.modelName
            val isVisible = keyVisibility[provider.id] ?: false
            val isTesting = testingState[provider.id] ?: false
            val testResult = testResults[provider.id]

            ProviderCreditManagementCard(
                provider = provider,
                providerType = providerType,
                draftKey = currentDraftKey,
                onDraftKeyChange = { draftKeys[provider.id] = it },
                draftModel = currentDraftModel,
                onDraftModelChange = { draftModels[provider.id] = it },
                isKeyVisible = isVisible,
                onToggleKeyVisible = { keyVisibility[provider.id] = !isVisible },
                isTesting = isTesting,
                testResult = testResult,
                onSave = {
                    coroutineScope.launch {
                        repository.updateProviderKey(
                            providerId = provider.id,
                            newKey = currentDraftKey,
                            model = currentDraftModel
                        )
                        Toast.makeText(context, "Saved changes for ${provider.name}", Toast.LENGTH_SHORT).show()
                    }
                },
                onTestConnection = {
                    testingState[provider.id] = true
                    testResults.remove(provider.id)
                    coroutineScope.launch {
                        val testConfig = provider.copy(
                            apiKey = currentDraftKey,
                            modelName = currentDraftModel
                        )
                        val result = repository.testAiProvider(testConfig)
                        testResults[provider.id] = result
                        testingState[provider.id] = false
                    }
                },
                onToggleEnabled = { enabled ->
                    coroutineScope.launch {
                        repository.toggleAiProvider(provider.id, enabled)
                    }
                },
                onDelete = if (provider.id != "gemini_primary") {
                    {
                        coroutineScope.launch {
                            repository.removeAiProvider(provider.id)
                            Toast.makeText(context, "Removed provider", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else null
            )
        }

        // FOOTER / REASSURANCE CARD
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, OpusBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security",
                        tint = OpusViralEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Client-Side Zero-Log Security",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusTextPrimary
                        )
                        Text(
                            text = "Your API keys never touch our servers. They are stored securely in Android on-device keystore & SharedPreferences.",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = OpusTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderCreditManagementCard(
    provider: AiProviderConfig,
    providerType: AiProviderType,
    draftKey: String,
    onDraftKeyChange: (String) -> Unit,
    draftModel: String,
    onDraftModelChange: (String) -> Unit,
    isKeyVisible: Boolean,
    onToggleKeyVisible: () -> Unit,
    isTesting: Boolean,
    testResult: Pair<Boolean, String>?,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val brandColor = try {
        Color(android.graphics.Color.parseColor(providerType.brandColorHex))
    } catch (_: Exception) {
        OpusPrimaryViolet
    }

    val isConnected = provider.isEnabled && provider.apiKey.isNotBlank()
    val isDirty = draftKey != provider.apiKey || draftModel != provider.modelName
    val hasCreditData = provider.totalCreditsAllocated > 0.0

    val animatedProgress by animateFloatAsState(
        targetValue = provider.creditPercentage,
        label = "credit_progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.dp,
                if (isConnected) brandColor.copy(alpha = 0.5f) else OpusBorder,
                RoundedCornerShape(18.dp)
            )
            .testTag("provider_card_${provider.id}"),
        colors = CardDefaults.cardColors(containerColor = OpusDarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // TOP ROW: BRAND ICON, TITLE, PRIORITY & SWITCH
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brandColor.copy(alpha = 0.2f))
                            .border(1.dp, brandColor, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = providerType.name.take(2),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = brandColor
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusTextPrimary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(OpusDarkSurfaceHighlight)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Priority #${provider.priority}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OpusTextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "•  ${provider.modelName}",
                                fontSize = 10.sp,
                                color = OpusElectricCyan
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = provider.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = brandColor,
                            uncheckedTrackColor = OpusDarkSurfaceVariant
                        ),
                        modifier = Modifier.size(width = 38.dp, height = 24.dp)
                    )

                    if (onDelete != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Provider",
                                tint = OpusTextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // REAL-TIME CREDIT USAGE / BALANCE BOX
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, OpusBorder.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = OpusDarkSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Provider Credit Data",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OpusTextSecondary
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                val remainingText = if (provider.creditUnit == "$") {
                                    "$${String.format(Locale.ROOT, "%.2f", provider.remainingCredits)}"
                                } else {
                                    "${provider.remainingCredits.toInt()} ${provider.creditUnit}"
                                }

                                val totalText = if (provider.creditUnit == "$") {
                                    "$${String.format(Locale.ROOT, "%.2f", provider.totalCreditsAllocated)}"
                                } else {
                                    "${provider.totalCreditsAllocated.toInt()} ${provider.creditUnit}"
                                }

                                Text(
                                    text = remainingText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (provider.creditPercentage > 0.3f) OpusViralEmerald else OpusHotPink
                                )
                                Text(
                                    text = " / $totalText",
                                    fontSize = 11.sp,
                                    color = OpusTextSecondary,
                                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                                )
                            }
                        }

                        // Status Chip
                        val statusColor = when {
                            provider.apiKey.isBlank() || !hasCreditData -> OpusTextSecondary
                            provider.creditPercentage > 0.3f -> OpusViralEmerald
                            else -> OpusHotPink
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(1.dp, statusColor, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = when {
                                    provider.apiKey.isBlank() -> "No Key Added"
                                    !hasCreditData -> "Usage unavailable"
                                    provider.creditPercentage > 0.3f -> "Healthy Balance"
                                    else -> "Low Balance"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (animatedProgress > 0.3f) brandColor else OpusHotPink,
                        trackColor = OpusDarkSurfaceHighlight
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Sub-metrics Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = OpusElectricCyan, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${provider.totalTokensProcessed / 1000}k Tokens Consumed",
                                fontSize = 10.sp,
                                color = OpusTextSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = OpusGold, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${provider.lastLatencyMs}ms Latency • ${provider.rateLimitRpm} RPM",
                                fontSize = 10.sp,
                                color = OpusTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // API KEY INPUT & PASTE ROW
            Text(
                text = "Configured API Key:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = OpusTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = draftKey,
                onValueChange = onDraftKeyChange,
                placeholder = { Text(providerType.placeholderKey, fontSize = 11.sp, color = OpusTextSecondary.copy(alpha = 0.4f)) },
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("key_input_${provider.id}"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OpusTextPrimary,
                    unfocusedTextColor = OpusTextPrimary,
                    focusedBorderColor = brandColor,
                    unfocusedBorderColor = OpusBorder,
                    focusedContainerColor = OpusDarkSurfaceHighlight,
                    unfocusedContainerColor = OpusDarkSurfaceHighlight
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleKeyVisible, modifier = Modifier.size(30.dp)) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Visibility",
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
                                    if (pasted.isNotBlank()) onDraftKeyChange(pasted.trim())
                                }
                            },
                            modifier = Modifier.size(30.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

            // POPULAR MODEL PRESET CHIPS
            val modelPresets = when (providerType) {
                AiProviderType.OPENAI -> listOf("gpt-4o-mini", "gpt-4o", "o3-mini")
                AiProviderType.ANTHROPIC -> listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022")
                AiProviderType.GEMINI -> listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-flash")
                AiProviderType.GROQ -> listOf("llama-3.3-70b-versatile", "mixtral-8x7b-32768")
                AiProviderType.OPENROUTER -> listOf("meta-llama/llama-3.3-70b-instruct", "deepseek/deepseek-r1")
                AiProviderType.MISTRAL -> listOf("mistral-large-latest", "codestral-latest")
                else -> emptyList()
            }

            if (modelPresets.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    modelPresets.forEach { modelOption ->
                        val isSelected = draftModel == modelOption
                        FilterChip(
                            selected = isSelected,
                            onClick = { onDraftModelChange(modelOption) },
                            label = { Text(modelOption, fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = brandColor.copy(alpha = 0.2f),
                                selectedLabelColor = brandColor,
                                containerColor = OpusDarkSurfaceHighlight,
                                labelColor = OpusTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = brandColor,
                                borderColor = OpusBorder
                            ),
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // DOCS LINK & TEST FEEDBACK
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (providerType.docsUrl.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(providerType.docsUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    ) {
                        Text(
                            text = "Get API Key",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OpusElectricCyan
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "External Link",
                            tint = OpusElectricCyan,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Text(
                    text = "لا تُعرض الحصة إلا عند توفر بيانات فعلية",
                    fontSize = 9.sp,
                    color = OpusTextSecondary
                )
            }

            // TEST FEEDBACK BANNER
            if (testResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val (success, message) = testResult
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (success) OpusViralEmerald.copy(alpha = 0.15f) else OpusHotPink.copy(alpha = 0.15f))
                        .border(1.dp, if (success) OpusViralEmerald else OpusHotPink, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (success) OpusViralEmerald else OpusHotPink,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = message,
                            fontSize = 10.sp,
                            color = OpusTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ACTION BUTTONS (TEST & SAVE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = !isTesting && draftKey.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("test_btn_${provider.id}"),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (draftKey.isNotBlank()) OpusElectricCyan else OpusBorder)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = OpusElectricCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pinging...", fontSize = 11.sp, color = OpusElectricCyan)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Test", tint = OpusElectricCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Key", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OpusElectricCyan)
                    }
                }

                Button(
                    onClick = onSave,
                    enabled = isDirty,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("save_btn_${provider.id}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandColor,
                        disabledContainerColor = OpusDarkSurfaceHighlight
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isDirty) "Save Key" else "Saved",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
