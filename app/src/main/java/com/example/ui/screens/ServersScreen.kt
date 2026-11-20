package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ProviderCatalogDefaults
import com.example.data.model.SavedProviderAccount
import com.example.data.model.ServerProfile
import com.example.ui.components.AddOrEditProviderDialog
import com.example.ui.components.ProviderAccountsSection
import com.example.ui.components.ProviderApiKeyLinkBanner
import com.example.ui.components.ThemeSelectorCard
import com.example.ui.components.ThemeToggleButton
import com.example.ui.theme.LocalOpenCodeColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.ConnectionState
import com.example.ui.viewmodel.OpenCodeUiState

private enum class ServerSectionFilter(val label: String) {
    ALL("Semua"),
    PROVIDERS("Provider & Model"),
    SERVERS("Profil Server"),
    THEME("Tema")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServersScreen(
    state: OpenCodeUiState,
    onTestConnection: () -> Unit,
    onActivateProfile: (String) -> Unit,
    onSaveProfile: (ServerProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onOpenSetupWizard: () -> Unit,
    onSaveProviderApiKey: (String, String, String?, String?) -> Unit,
    onSaveProviderAccount: (SavedProviderAccount) -> Unit = {},
    onDeleteCustomProvider: (String) -> Unit = {},
    onRefreshModels: (String?) -> Unit = {},
    onSelectModel: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var editingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var editingProviderAccount by remember { mutableStateOf<SavedProviderAccount?>(null) }
    var isAddingCustomProvider by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf(ServerSectionFilter.ALL) }
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .testTag("servers_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Server & Pengaturan",
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Koneksi runtime, provider AI, & tema",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeToggleButton(
                            currentTheme = state.themePreference,
                            onSelectTheme = onThemeChange
                        )
                        Button(
                            onClick = {
                                isAddingNew = true
                                editingProfile = ServerProfile(
                                    name = "Server Baru",
                                    baseUrl = "http://192.168.1.100:4096"
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("add_server_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Server")
                        }
                    }
                }
            }

            // Tidy section filter chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServerSectionFilter.entries.forEach { section ->
                        FilterChip(
                            selected = activeSection == section,
                            onClick = { activeSection = section },
                            label = { Text(section.label, maxLines = 1) },
                            modifier = Modifier.testTag("server_tab_filter_${section.name.lowercase()}")
                        )
                    }
                }
            }

            // Live Diagnostic + Quick Actions Card
            item {
                ActiveConnectionCard(
                    state = state,
                    onTestConnection = onTestConnection,
                    onOpenApiKeyDialog = { showApiKeyDialog = true },
                    onOpenSetupWizard = onOpenSetupWizard
                )
            }

            // Theme selector card (Gelap, Terang, Material Glass, Miuix, Sistem)
            if (activeSection == ServerSectionFilter.ALL || activeSection == ServerSectionFilter.THEME) {
                item {
                    ThemeSelectorCard(
                        currentTheme = state.themePreference,
                        onSelectTheme = onThemeChange
                    )
                }
            }

            // All Providers & All Available Models (Sorted Ready-First)
            if (activeSection == ServerSectionFilter.ALL || activeSection == ServerSectionFilter.PROVIDERS) {
                item {
                    ProviderAccountsSection(
                        accounts = state.providerAccounts,
                        activeProviderId = state.selectedModel?.providerID ?: state.localProviderConfig.providerId,
                        activeModelId = state.selectedModel?.modelID ?: state.localProviderConfig.modelId,
                        isRefreshingModels = state.isRefreshingModels,
                        onOpenAddCustomProvider = {
                            isAddingCustomProvider = true
                            editingProviderAccount = SavedProviderAccount(
                                id = "",
                                name = "",
                                baseUrl = "https://",
                                apiKey = "",
                                defaultModelId = "gpt-4o-mini",
                                isCustom = true
                            )
                        },
                        onEditProviderAccount = { account ->
                            isAddingCustomProvider = false
                            editingProviderAccount = account
                        },
                        onDeleteCustomProvider = onDeleteCustomProvider,
                        onSelectProviderModel = onSelectModel,
                        onRefreshModels = onRefreshModels
                    )
                }

                // Remote Server Connected Providers (when connected to external server)
                state.providerCatalog?.let { catalog ->
                    if (!state.activeProfile.isLocalAndroid && catalog.all.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Provider Server Eksternal (${catalog.connected.size} terhubung)",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val sortedCatalogProviders = catalog.all.sortedByDescending { it.id in catalog.connected }
                                        sortedCatalogProviders.forEach { provider ->
                                            val isConn = provider.id in catalog.connected
                                            AssistChip(
                                                onClick = {},
                                                label = { Text("${provider.displayName} (${provider.models.size})") },
                                                leadingIcon = if (isConn) {
                                                    {
                                                        Icon(
                                                            Icons.Rounded.CheckCircle,
                                                            contentDescription = null,
                                                            tint = LocalOpenCodeColors.current.success,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Saved Profiles & CLI Guide Section
            if (activeSection == ServerSectionFilter.ALL || activeSection == ServerSectionFilter.SERVERS) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Profil Server Tersimpan (${state.profiles.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                items(state.profiles, key = { it.id }) { profile ->
                    val isActive = profile.id == state.activeProfile.id
                    ServerProfileCard(
                        profile = profile,
                        isActive = isActive,
                        onSelect = { onActivateProfile(profile.id) },
                        onEdit = {
                            isAddingNew = false
                            editingProfile = profile
                        },
                        onDelete = { onDeleteProfile(profile.id) },
                        canDelete = state.profiles.size > 1
                    )
                }

                // Quick Setup CLI Guide
                item {
                    val cliCommand = "opencode serve --hostname 0.0.0.0 --port 4096"
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Cara Menjalankan OpenCode Server",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "Jalankan perintah berikut di direktori proyek pada komputer Anda agar dapat diakses dari Android melalui LAN, Tailscale, atau Emulator (10.0.2.2):",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LocalOpenCodeColors.current.codeBackground,
                                border = BorderStroke(1.dp, LocalOpenCodeColors.current.codeBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cliCommand,
                                        style = MonoCodeStyle,
                                        color = LocalOpenCodeColors.current.success,
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(rememberScrollState())
                                    )
                                    IconButton(
                                        onClick = { clipboard.setText(AnnotatedString(cliCommand)) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = "Salin perintah",
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

    editingProfile?.let { target ->
        EditServerProfileDialog(
            initial = target,
            isNew = isAddingNew,
            onDismiss = { editingProfile = null },
            onSave = { saved ->
                editingProfile = null
                onSaveProfile(saved)
            }
        )
    }

    editingProviderAccount?.let { targetAccount ->
        AddOrEditProviderDialog(
            initialAccount = targetAccount,
            isNewCustom = isAddingCustomProvider,
            isSaving = state.isSavingApiKey,
            onDismiss = { editingProviderAccount = null },
            onSave = { saved ->
                editingProviderAccount = null
                onSaveProviderAccount(saved)
            }
        )
    }

    if (showApiKeyDialog) {
        ProviderApiKeyDialog(
            isLocalRuntime = state.activeProfile.isLocalAndroid,
            accounts = state.providerAccounts,
            initialProviderId = state.localProviderConfig.providerId,
            initialModelId = state.localProviderConfig.modelId,
            initialApiKey = if (state.activeProfile.isLocalAndroid) state.localProviderConfig.apiKey else "",
            initialBaseUrl = state.localProviderConfig.customBaseUrl,
            isSaving = state.isSavingApiKey,
            onDismiss = { showApiKeyDialog = false },
            onSave = { providerId, key, modelId, customBaseUrl ->
                showApiKeyDialog = false
                onSaveProviderApiKey(providerId, key, modelId, customBaseUrl)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderApiKeyDialog(
    isLocalRuntime: Boolean,
    accounts: List<SavedProviderAccount>,
    initialProviderId: String,
    initialModelId: String,
    initialApiKey: String,
    initialBaseUrl: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String?) -> Unit
) {
    var providerId by remember { mutableStateOf(initialProviderId.ifBlank { "opencode" }) }
    var modelId by remember {
        mutableStateOf(
            initialModelId.ifBlank { ProviderCatalogDefaults.defaultModelFor(providerId) }
        )
    }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var customBaseUrl by remember {
        mutableStateOf(
            initialBaseUrl.ifBlank { ProviderCatalogDefaults.defaultBaseUrlFor(providerId) }
        )
    }
    var showKey by remember { mutableStateOf(false) }

    val matchedAccount = accounts.firstOrNull { it.id.equals(providerId, ignoreCase = true) }
    val modelsForProvider = remember(providerId, matchedAccount) {
        if (matchedAccount != null) ProviderCatalogDefaults.modelsForAccount(matchedAccount)
        else ProviderCatalogDefaults.builtInSpec(providerId)?.models.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isLocalRuntime) "API Key Runtime Lokal Android" else "Simpan API Key ke Server")
        },
        text = {
            ProviderApiKeyDialogContent(
                isLocalRuntime = isLocalRuntime,
                accounts = accounts,
                providerId = providerId,
                modelId = modelId,
                apiKey = apiKey,
                customBaseUrl = customBaseUrl,
                onProviderIdChange = { providerId = it },
                onSelectPreset = { preset ->
                    providerId = preset.id
                    val acc = accounts.firstOrNull { it.id == preset.id }
                    modelId = acc?.defaultModelId?.ifBlank { preset.defaultModel } ?: preset.defaultModel
                    apiKey = acc?.apiKey.orEmpty()
                    customBaseUrl = acc?.baseUrl?.ifBlank { preset.defaultBaseUrl } ?: preset.defaultBaseUrl
                },
                onModelIdChange = { modelId = it },
                onApiKeyChange = { apiKey = it },
                onCustomBaseUrlChange = { customBaseUrl = it }
            )
        },
        confirmButton = {
            Button(
                enabled = !isSaving && providerId.isNotBlank() && (apiKey.isNotBlank() || (providerId == "custom" && customBaseUrl.isNotBlank())),
                onClick = {
                    onSave(
                        providerId,
                        apiKey,
                        modelId.takeIf { isLocalRuntime },
                        customBaseUrl.takeIf { isLocalRuntime }
                    )
                },
                modifier = Modifier.testTag("confirm_save_provider_api_key")
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
internal fun ProviderApiKeyDialogContent(
    isLocalRuntime: Boolean,
    accounts: List<SavedProviderAccount>,
    providerId: String,
    modelId: String,
    apiKey: String,
    customBaseUrl: String,
    onProviderIdChange: (String) -> Unit,
    onSelectPreset: (ProviderPreset) -> Unit,
    onModelIdChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onCustomBaseUrlChange: (String) -> Unit
) {
    var showKey by remember { mutableStateOf(false) }
    val matchedAccount = accounts.firstOrNull { it.id.equals(providerId, ignoreCase = true) }
    val modelsForProvider = remember(providerId, matchedAccount) {
        if (matchedAccount != null) ProviderCatalogDefaults.modelsForAccount(matchedAccount)
        else ProviderCatalogDefaults.builtInSpec(providerId)?.models.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 440.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (isLocalRuntime) {
                "API Key disimpan di perangkat untuk memanggil API OpenCode / LLM secara langsung dari Runtime Lokal Android."
            } else {
                "API Key akan dikirim ke endpoint PUT /auth/{provider} pada server OpenCode yang sedang terhubung."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PopularOpenCodeProviders.forEach { preset ->
                FilterChip(
                    selected = providerId == preset.id,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.id, maxLines = 1) },
                    modifier = Modifier.testTag("dialog_preset_${preset.id}")
                )
            }
        }
        OutlinedTextField(
            value = providerId,
            onValueChange = onProviderIdChange,
            label = { Text("Provider ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (isLocalRuntime) {
            OutlinedTextField(
                value = customBaseUrl,
                onValueChange = onCustomBaseUrlChange,
                label = { Text("URL Endpoint Base") },
                placeholder = { Text("https://opencode.ai/zen/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (modelsForProvider.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modelsForProvider.forEach { m ->
                        FilterChip(
                            selected = modelId == m,
                            onClick = { onModelIdChange(m) },
                            label = { Text(m, maxLines = 1) }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = modelId,
                onValueChange = onModelIdChange,
                label = { Text("Model ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val keyPortalUrl = ProviderCatalogDefaults.apiKeyUrlFor(providerId, customBaseUrl)
        if (keyPortalUrl.isNotBlank()) {
            ProviderApiKeyLinkBanner(
                providerName = matchedAccount?.name ?: providerId,
                apiKeyUrl = keyPortalUrl,
                modifier = Modifier.testTag("dialog_get_api_key_link")
            )
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "Toggle key"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dialog_provider_api_key_input")
        )
    }
}

@Composable
private fun ActiveConnectionCard(
    state: OpenCodeUiState,
    onTestConnection: () -> Unit,
    onOpenApiKeyDialog: () -> Unit,
    onOpenSetupWizard: () -> Unit
) {
    val semantic = LocalOpenCodeColors.current
    val (borderColor, badgeBg, badgeText) = when (state.connectionState) {
        is ConnectionState.Connected -> Triple(semantic.success, semantic.successContainer, "Terhubung")
        is ConnectionState.Connecting -> Triple(semantic.warning, semantic.warningContainer, "Menghubungkan…")
        is ConnectionState.Error -> Triple(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer, "Gagal")
        is ConnectionState.Idle -> Triple(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.surfaceVariant, "Idle")
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (state.connectionState is ConnectionState.Connected) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = borderColor
                    )
                    Column {
                        Text(
                            text = state.activeProfile.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = state.activeProfile.normalizedUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(50), color = badgeBg) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = borderColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            when (val conn = state.connectionState) {
                is ConnectionState.Connected -> {
                    Text(
                        text = "Versi Server: ${conn.version} · Latensi: ${conn.latencyMs} ms · SSE: ${if (state.sseConnected) "Aktif" else "Menyambung"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ConnectionState.Connecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Memeriksa /global/health…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is ConnectionState.Error -> {
                    Text(
                        text = "${conn.diagnostic.title}: ${conn.diagnostic.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = conn.diagnostic.hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ConnectionState.Idle -> Unit
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenApiKeyDialog,
                    enabled = state.activeProfile.isLocalAndroid || state.connectionState is ConnectionState.Connected,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("open_provider_apikey_dialog")
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Atur Key", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onTestConnection,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("test_connection_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Uji Koneksi", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Uji", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onOpenSetupWizard,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reopen_setup_wizard_button")
                ) {
                    Icon(Icons.Rounded.RocketLaunch, contentDescription = "Setup Awal", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Wizard", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ServerProfileCard(
    profile: ServerProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("server_profile_${profile.id}"),
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Dns,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (profile.hasAuth) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = "Basic Auth aktif",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = profile.normalizedUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit profil")
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Hapus profil",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditServerProfileDialog(
    initial: ServerProfile,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ServerProfile) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var directory by remember { mutableStateOf(initial.directory) }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Tambah Server OpenCode" else "Edit Profil Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Profil") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_name_input")
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("URL Server") },
                    placeholder = { Text("http://192.168.1.10:4096") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = baseUrl.contains("10.0.2.2"),
                        onClick = { baseUrl = "http://10.0.2.2:4096" },
                        label = { Text("Emulator 10.0.2.2") }
                    )
                    FilterChip(
                        selected = baseUrl.contains("localhost"),
                        onClick = { baseUrl = "http://localhost:4096" },
                        label = { Text("Localhost") }
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (Basic Auth)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("OPENCODE_SERVER_PASSWORD (opsional)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_password_input")
                )
                OutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it },
                    label = { Text("Workspace Directory (opsional)") },
                    placeholder = { Text("/home/user/projects/myapp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            baseUrl = ServerProfile.normalizeServerUrl(baseUrl),
                            username = username.trim().ifBlank { "opencode" },
                            password = password,
                            directory = directory.trim()
                        )
                    )
                },
                modifier = Modifier.testTag("save_server_profile_button")
            ) {
                Text("Simpan & Hubungkan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
