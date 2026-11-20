package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LocalLlmClient
import com.example.data.model.ProviderApiFormat
import com.example.data.model.ProviderCatalogDefaults
import com.example.data.model.RuntimeMode
import com.example.data.model.SavedProviderAccount
import com.example.data.model.ServerProfile
import com.example.ui.components.ProviderApiKeyLinkBanner
import com.example.ui.theme.LocalOpenCodeColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.viewmodel.ConnectionState
import com.example.ui.viewmodel.OpenCodeUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

enum class SetupBackendTarget {
    ANDROID_BUILTIN,
    EXTERNAL_SERVER
}

data class ProviderPreset(
    val id: String,
    val label: String,
    val envVar: String,
    val defaultModel: String,
    val defaultBaseUrl: String = ""
)

val PopularOpenCodeProviders = ProviderCatalogDefaults.builtInProviders.map {
    ProviderPreset(
        id = it.id,
        label = it.name,
        envVar = it.envVarName,
        defaultModel = it.defaultModelId,
        defaultBaseUrl = it.baseUrl
    )
} + ProviderPreset(
    id = "custom",
    label = "Custom Endpoint",
    envVar = "CUSTOM_API_KEY",
    defaultModel = "gpt-4o-mini",
    defaultBaseUrl = ""
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupWizardScreen(
    state: OpenCodeUiState,
    canDismissWithBack: Boolean,
    snackbarEvents: Flow<String> = emptyFlow(),
    onRefreshTermux: () -> Unit,
    onSaveProfile: (ServerProfile) -> Unit,
    onTestConnection: () -> Unit,
    onSaveProviderApiKey: (providerId: String, apiKey: String, modelId: String?, customBaseUrl: String?) -> Unit,
    onSaveProviderAccount: (SavedProviderAccount) -> Unit = {},
    onRefreshModels: (String?) -> Unit = {},
    onFinishSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var target by remember {
        mutableStateOf(
            if (state.activeProfile.isLocalAndroid) SetupBackendTarget.ANDROID_BUILTIN
            else SetupBackendTarget.EXTERNAL_SERVER
        )
    }
    var serverUrl by remember {
        mutableStateOf(
            if (state.activeProfile.isLocalAndroid) "http://10.0.2.2:4096"
            else state.activeProfile.normalizedUrl.ifBlank { "http://10.0.2.2:4096" }
        )
    }
    var serverName by remember { mutableStateOf("Server OpenCode") }
    var serverPassword by remember { mutableStateOf("") }

    var selectedProviderId by remember { mutableStateOf(state.localProviderConfig.providerId.ifBlank { "opencode" }) }
    var modelId by remember {
        mutableStateOf(
            state.localProviderConfig.modelId.ifBlank {
                ProviderCatalogDefaults.defaultModelFor(selectedProviderId)
            }
        )
    }
    var apiKey by remember { mutableStateOf(state.localProviderConfig.apiKey) }
    var customBaseUrl by remember {
        mutableStateOf(
            state.localProviderConfig.customBaseUrl.ifBlank {
                ProviderCatalogDefaults.defaultBaseUrlFor(selectedProviderId)
            }
        )
    }
    var customProviderName by remember { mutableStateOf("Custom OpenAI API") }
    var selectedApiFormat by remember { mutableStateOf(state.localProviderConfig.apiFormat) }
    var showApiKey by remember { mutableStateOf(false) }
    var showServerPass by remember { mutableStateOf(false) }
    var copiedScript by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val semantic = LocalOpenCodeColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarEvents) {
        snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (canDismissWithBack) {
        BackHandler {
            if (currentStep > 0) currentStep-- else onFinishSetup()
        }
    }

    LaunchedEffect(copiedScript) {
        if (copiedScript) {
            delay(2000)
            copiedScript = false
        }
    }

    val matchedAccount = state.providerAccounts.firstOrNull { it.id.equals(selectedProviderId, ignoreCase = true) }
    val selectedPreset = PopularOpenCodeProviders.firstOrNull { it.id == selectedProviderId }
    val availableModelsForSelected = remember(selectedProviderId, matchedAccount, state.providerAccounts) {
        if (matchedAccount != null) {
            ProviderCatalogDefaults.modelsForAccount(matchedAccount)
        } else {
            ProviderCatalogDefaults.builtInSpec(selectedProviderId)?.models.orEmpty()
        }
    }
    val remoteScript = remember(selectedProviderId, apiKey, serverPassword) {
        buildRemoteInstallScript(
            envVarName = selectedPreset?.envVar ?: matchedAccount?.envVarName?.ifBlank { null } ?: "OPENCODE_API_KEY",
            apiKey = apiKey.trim(),
            serverPassword = serverPassword.trim()
        )
    }

    fun applyTargetAndProvider() {
        val profile = if (target == SetupBackendTarget.ANDROID_BUILTIN) {
            ServerProfile(
                id = "local-android",
                name = "Runtime Lokal Android (Built-in)",
                baseUrl = "local://android",
                mode = RuntimeMode.LOCAL_ANDROID
            )
        } else {
            ServerProfile(
                id = "custom-setup-server",
                name = serverName.ifBlank { "Server OpenCode" },
                baseUrl = ServerProfile.normalizeServerUrl(serverUrl),
                username = "opencode",
                password = serverPassword,
                mode = RuntimeMode.REMOTE_SERVER
            )
        }
        onSaveProfile(profile)
        if (selectedProviderId == "custom") {
            if (customBaseUrl.isNotBlank()) {
                val customId = customProviderName.trim().lowercase()
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                    .ifBlank { "custom_${System.currentTimeMillis()}" }
                onSaveProviderAccount(
                    SavedProviderAccount(
                        id = customId,
                        name = customProviderName.trim().ifBlank { "Custom Provider" },
                        baseUrl = customBaseUrl.trim(),
                        apiKey = apiKey.trim(),
                        defaultModelId = modelId.trim().ifBlank { "gpt-4o-mini" },
                        apiFormat = selectedApiFormat,
                        isCustom = true,
                        cachedModels = listOfNotNull(modelId.trim().takeIf { it.isNotBlank() })
                    )
                )
            }
        } else if (apiKey.isNotBlank() || customBaseUrl.isNotBlank()) {
            onSaveProviderApiKey(selectedProviderId, apiKey, modelId, customBaseUrl)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = if (LocalOpenCodeColors.current.isGlass) Color.Transparent else MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Setup Environment OpenCode",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Langkah ${currentStep + 1} dari 3",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = onFinishSetup,
                        modifier = Modifier.testTag("skip_setup_button")
                    ) {
                        Text(if (canDismissWithBack) "Tutup" else "Lewati")
                    }
                }

                // Stepper pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("1. Runtime", "2. API Key", "3. Selesai").forEachIndexed { idx, label ->
                        val active = idx == currentStep
                        val done = idx < currentStep
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { currentStep = idx },
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                active -> MaterialTheme.colorScheme.primaryContainer
                                done -> semantic.successContainer.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (currentStep) {
                        0 -> {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(116.dp)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.img_opencode_hero),
                                                contentDescription = "Ilustrasi OpenCode",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                                                        )
                                                    )
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Pilih Mode Backend OpenCode",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "Anda dapat menjalankan agen OpenCode langsung di dalam backend Android (tanpa Termux & tanpa PC) atau terhubung ke server eksternal.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                BackendChoiceCard(
                                    selected = target == SetupBackendTarget.ANDROID_BUILTIN,
                                    icon = Icons.Rounded.Bolt,
                                    title = "Runtime Lokal Android (Built-in)",
                                    subtitle = "Berjalan langsung di backend Kotlin APK ini tanpa Termux & tanpa PC. Mendukung tool read, write, edit, grep, shell lokal, diff viewer, dan pemanggilan API LLM langsung.",
                                    badge = "Tanpa Termux · Langsung Jalan",
                                    badgePositive = true,
                                    onClick = { target = SetupBackendTarget.ANDROID_BUILTIN }
                                )
                            }

                            item {
                                BackendChoiceCard(
                                    selected = target == SetupBackendTarget.EXTERNAL_SERVER,
                                    icon = Icons.Rounded.Computer,
                                    title = "Server Eksternal (`opencode serve`)",
                                    subtitle = "Hubungkan ke instance `opencode serve` di Laptop/PC, VPS, Emulator (10.0.2.2), atau Termux jika butuh toolchain desktop penuh.",
                                    badge = "Opsional (PC / LAN / VPS)",
                                    badgePositive = false,
                                    onClick = { target = SetupBackendTarget.EXTERNAL_SERVER }
                                )
                            }

                            if (target == SetupBackendTarget.ANDROID_BUILTIN) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = semantic.successContainer.copy(alpha = 0.45f),
                                        border = BorderStroke(1.dp, semantic.success.copy(alpha = 0.45f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Folder,
                                                contentDescription = null,
                                                tint = semantic.success
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Workspace Lokal Otomatis Siap",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = semantic.onSuccessContainer
                                                )
                                                Text(
                                                    text = state.localWorkspacePath.ifBlank { "files/opencode_workspace" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = semantic.onSuccessContainer.copy(alpha = 0.85f)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text("Alamat Server Eksternal", style = MaterialTheme.typography.titleSmall)
                                            OutlinedTextField(
                                                value = serverUrl,
                                                onValueChange = { serverUrl = it },
                                                label = { Text("URL Server") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("setup_server_url_input")
                                            )
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(
                                                    selected = serverUrl.contains("10.0.2.2"),
                                                    onClick = { serverUrl = "http://10.0.2.2:4096" },
                                                    label = { Text("Emulator (10.0.2.2)") }
                                                )
                                                FilterChip(
                                                    selected = serverUrl.contains("192.168."),
                                                    onClick = { serverUrl = "http://192.168.1.100:4096" },
                                                    label = { Text("IP LAN Wi-Fi") }
                                                )
                                                FilterChip(
                                                    selected = serverUrl.contains("127.0.0.1"),
                                                    onClick = { serverUrl = "http://127.0.0.1:4096" },
                                                    label = { Text("Localhost") }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Text("API Key Provider AI", style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text(
                                            text = if (target == SetupBackendTarget.ANDROID_BUILTIN) {
                                                "Runtime Lokal Android memanggil API LLM langsung menggunakan API Key Anda (disimpan aman di perangkat via DataStore)."
                                            } else {
                                                "API Key akan dikirim ke server OpenCode (PUT /auth/{provider}) atau disisipkan ke skrip setup."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Text("Pilih Provider API", style = MaterialTheme.typography.labelLarge)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            PopularOpenCodeProviders.forEach { preset ->
                                                val savedAcc = state.providerAccounts.firstOrNull { it.id == preset.id }
                                                val hasSavedKey = savedAcc?.apiKey?.isNotBlank() == true
                                                FilterChip(
                                                    selected = selectedProviderId == preset.id,
                                                    onClick = {
                                                        selectedProviderId = preset.id
                                                        val acc = state.providerAccounts.firstOrNull { it.id == preset.id }
                                                        modelId = acc?.defaultModelId?.ifBlank { preset.defaultModel } ?: preset.defaultModel
                                                        apiKey = acc?.apiKey.orEmpty()
                                                        customBaseUrl = acc?.baseUrl?.ifBlank { preset.defaultBaseUrl } ?: preset.defaultBaseUrl
                                                        selectedApiFormat = acc?.apiFormat
                                                            ?: ProviderCatalogDefaults.builtInSpec(preset.id)?.apiFormat
                                                            ?: ProviderApiFormat.OPENAI_COMPATIBLE
                                                    },
                                                    leadingIcon = if (hasSavedKey) {
                                                        {
                                                            Icon(
                                                                Icons.Rounded.CheckCircle,
                                                                contentDescription = null,
                                                                tint = semantic.success,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                    } else null,
                                                    label = { Text(preset.label) },
                                                    modifier = Modifier.testTag("setup_provider_${preset.id}")
                                                )
                                            }
                                            state.providerAccounts.filter { it.isCustom }.forEach { customAcc ->
                                                FilterChip(
                                                    selected = selectedProviderId == customAcc.id,
                                                    onClick = {
                                                        selectedProviderId = customAcc.id
                                                        modelId = customAcc.defaultModelId.ifBlank { customAcc.cachedModels.firstOrNull().orEmpty() }
                                                        apiKey = customAcc.apiKey
                                                        customBaseUrl = customAcc.baseUrl
                                                        selectedApiFormat = customAcc.apiFormat
                                                    },
                                                    label = { Text(customAcc.name) },
                                                    modifier = Modifier.testTag("setup_provider_${customAcc.id}")
                                                )
                                            }
                                        }

                                        if (selectedProviderId == "opencode") {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    text = "OpenCode Zen Gateway (${customBaseUrl.ifBlank { "https://opencode.ai/zen/v1" }}) mendukung satu API Key untuk akses model coding OpenCode (big-pickle, grok-code, kimi-k2, qwen3-coder, Claude, GPT, Gemini).",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }

                                        if (selectedProviderId == "custom") {
                                            OutlinedTextField(
                                                value = customProviderName,
                                                onValueChange = { customProviderName = it },
                                                label = { Text("Nama Provider Baru") },
                                                placeholder = { Text("Misal: Together AI / Ollama / LiteLLM") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("setup_custom_provider_name")
                                            )
                                        }

                                        OutlinedTextField(
                                            value = customBaseUrl,
                                            onValueChange = { customBaseUrl = it },
                                            label = { Text("URL Endpoint Base") },
                                            placeholder = { Text("https://opencode.ai/zen/v1") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("setup_provider_base_url")
                                        )

                                        val keyPortalUrl = ProviderCatalogDefaults.apiKeyUrlFor(selectedProviderId, customBaseUrl)
                                        if (keyPortalUrl.isNotBlank()) {
                                            ProviderApiKeyLinkBanner(
                                                providerName = selectedPreset?.label ?: matchedAccount?.name ?: selectedProviderId,
                                                apiKeyUrl = keyPortalUrl,
                                                modifier = Modifier.testTag("setup_get_api_key_link")
                                            )
                                        }

                                        OutlinedTextField(
                                            value = apiKey,
                                            onValueChange = { apiKey = it },
                                            label = { Text("API Key ${selectedPreset?.label ?: matchedAccount?.name ?: selectedProviderId}") },
                                            placeholder = {
                                                Text(
                                                    if (selectedProviderId == "opencode") "Masukkan OPENCODE_API_KEY Anda…"
                                                    else "AIza... atau sk-..."
                                                )
                                            },
                                            singleLine = true,
                                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                                    Icon(
                                                        if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                        contentDescription = "Toggle API Key"
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("setup_api_key_input")
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Model Tersedia (${availableModelsForSelected.size})",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            TextButton(
                                                onClick = {
                                                    if (apiKey.isNotBlank() && selectedProviderId != "custom") {
                                                        onSaveProviderApiKey(selectedProviderId, apiKey, modelId, customBaseUrl)
                                                    }
                                                    onRefreshModels(if (selectedProviderId == "custom") null else selectedProviderId)
                                                },
                                                enabled = !state.isRefreshingModels,
                                                modifier = Modifier.testTag("setup_refresh_models_button")
                                            ) {
                                                if (state.isRefreshingModels) {
                                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                    Spacer(Modifier.width(6.dp))
                                                }
                                                Text("Muat Model Live")
                                            }
                                        }

                                        if (availableModelsForSelected.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                availableModelsForSelected.forEach { m ->
                                                    FilterChip(
                                                        selected = modelId == m,
                                                        onClick = { modelId = m },
                                                        label = { Text(m, maxLines = 1) }
                                                    )
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = modelId,
                                            onValueChange = { modelId = it },
                                            label = { Text("Model ID Aktif") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("setup_model_id_input")
                                        )

                                        if (target == SetupBackendTarget.EXTERNAL_SERVER) {
                                            OutlinedTextField(
                                                value = serverPassword,
                                                onValueChange = { serverPassword = it },
                                                label = { Text("OPENCODE_SERVER_PASSWORD (opsional)") },
                                                singleLine = true,
                                                visualTransformation = if (showServerPass) VisualTransformation.None else PasswordVisualTransformation(),
                                                trailingIcon = {
                                                    IconButton(onClick = { showServerPass = !showServerPass }) {
                                                        Icon(
                                                            if (showServerPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                            contentDescription = "Toggle password"
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            if (target == SetupBackendTarget.ANDROID_BUILTIN) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, semantic.success.copy(alpha = 0.6f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = null,
                                                    tint = semantic.success,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = "Runtime Lokal Android Siap Digunakan",
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    Text(
                                                        text = "Tanpa Termux · Tanpa Server Eksternal",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = semantic.success
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "• Workspace: ${state.localWorkspacePath}\n• Provider: $selectedProviderId ($modelId)\n• Status API Key: ${if (apiKey.isNotBlank()) "Terisi (siap dipakai)" else "Belum diisi (dapat diisi di menu Server)"}\n• Tool aktif: read, write, edit, list, grep, bash (dengan izin)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text("1. Jalankan di Terminal Komputer / Server", style = MaterialTheme.typography.titleMedium)
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = semantic.codeBackground,
                                                border = BorderStroke(1.dp, semantic.codeBorder)
                                            ) {
                                                Text(
                                                    text = remoteScript,
                                                    style = MonoCodeStyle,
                                                    color = semantic.success,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(12.dp)
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    clipboard.setText(AnnotatedString(remoteScript))
                                                    copiedScript = true
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    if (copiedScript) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(if (copiedScript) "Tersalin!" else "Salin Perintah Server")
                                            }
                                        }
                                    }
                                }

                                item {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text("2. Uji Koneksi Server", style = MaterialTheme.typography.titleMedium)
                                            when (val conn = state.connectionState) {
                                                is ConnectionState.Connected -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = semantic.success)
                                                        Text(
                                                            text = "Terhubung! ${conn.version} (${conn.latencyMs} ms)",
                                                            color = semantic.success,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                                is ConnectionState.Connecting -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                        Text("Menguji koneksi…")
                                                    }
                                                }
                                                is ConnectionState.Error -> {
                                                    Text(
                                                        text = "${conn.diagnostic.title}: ${conn.diagnostic.detail}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                is ConnectionState.Idle -> Unit
                                            }
                                            Button(
                                                onClick = {
                                                    applyTargetAndProvider()
                                                    onTestConnection()
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("wizard_test_connection_button")
                                            ) {
                                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Simpan & Uji Koneksi")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStep > 0) {
                            OutlinedButton(onClick = { currentStep-- }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sebelumnya")
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        if (currentStep < 2) {
                            Button(
                                onClick = { currentStep++ },
                                modifier = Modifier.testTag("wizard_next_step_button")
                            ) {
                                Text("Lanjut")
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Button(
                                onClick = {
                                    applyTargetAndProvider()
                                    onFinishSetup()
                                },
                                modifier = Modifier.testTag("wizard_finish_button")
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mulai Gunakan OpenCode")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackendChoiceCard(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    badgePositive: Boolean,
    onClick: () -> Unit
) {
    val semantic = LocalOpenCodeColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (badgePositive) semantic.successContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (badgePositive) semantic.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun buildRemoteInstallScript(
    envVarName: String,
    apiKey: String,
    serverPassword: String
): String {
    val lines = mutableListOf(
        "curl -fsSL https://opencode.ai/install | bash",
        "# atau: npm install -g opencode-ai"
    )
    if (apiKey.isNotEmpty()) lines.add("export $envVarName=\"$apiKey\"")
    if (serverPassword.isNotEmpty()) lines.add("export OPENCODE_SERVER_PASSWORD=\"$serverPassword\"")
    lines.add("opencode serve --hostname 0.0.0.0 --port 4096")
    return lines.joinToString("\n")
}
