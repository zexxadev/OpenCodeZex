package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.data.model.ModelOption
import com.example.data.model.PermissionRequestItem
import com.example.data.model.ProviderCatalogDefaults
import com.example.ui.components.DiffViewerPane
import com.example.ui.components.PermissionBannerCard
import com.example.ui.components.SessionStatusChip
import com.example.ui.components.SseStreamStatusBadge
import com.example.ui.theme.LocalOpenCodeColors
import com.example.ui.viewmodel.ComposerMode
import com.example.ui.viewmodel.OpenCodeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    state: OpenCodeUiState,
    onBackToSessions: () -> Unit,
    onCreateNewChat: () -> Unit,
    onRefreshSession: () -> Unit,
    onReconnectSse: () -> Unit,
    onComposerTextChange: (String) -> Unit,
    onSelectFileMention: (String) -> Unit,
    onSelectComposerMode: (ComposerMode) -> Unit,
    onSelectAgent: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onSendPrompt: () -> Unit,
    onAbortSession: () -> Unit,
    onReplyPermission: (PermissionRequestItem, String) -> Unit,
    onRefreshModels: () -> Unit = {},
    onRetryResponse: (failedMessageId: String?) -> Unit = {},
    onDismissFailedPrompt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Chat, 1 = Diffs
    var showModelSheet by remember { mutableStateOf(false) }
    var showAgentMenu by remember { mutableStateOf(false) }
    var showCommandMenu by remember { mutableStateOf(false) }

    val session = state.activeSession
    if (session == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Mulai Chat AI Coding",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Buat sesi baru untuk mulai chat streaming real-time dengan agen coding OpenCode, atau pilih sesi yang sudah ada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onCreateNewChat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workspace_start_new_chat_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Mulai Chat Baru", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(
                    onClick = onBackToSessions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workspace_go_to_sessions_button")
                ) {
                    Text("Lihat Daftar Sesi")
                }
            }
        }
        return
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onBackToSessions,
                            modifier = Modifier.testTag("workspace_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Kembali ke daftar sesi"
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${state.activeProfile.name} · ${state.selectedAgent}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        SseStreamStatusBadge(
                            sseConnected = state.sseConnected,
                            isStreaming = state.isActiveSessionBusy || state.messages.any { it.isStreaming },
                            isLocalRuntime = state.activeProfile.isLocalAndroid,
                            onReconnect = onReconnectSse
                        )

                        SessionStatusChip(status = state.activeSessionStatus.status)

                        if (state.isActiveSessionBusy) {
                            IconButton(
                                onClick = onAbortSession,
                                modifier = Modifier.testTag("workspace_abort_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Hentikan agen",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onRefreshSession,
                                modifier = Modifier.testTag("workspace_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Segarkan pesan & diff"
                                )
                            }
                        }
                    }

                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Chat (${state.messages.size})") },
                            icon = { Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_chat")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Diff (${state.diffs.size})") },
                            icon = { Icon(Icons.Rounded.Difference, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_diff")
                        )
                    }
                }
            }

            state.activeSessionPermissions.forEach { perm ->
                PermissionBannerCard(
                    permission = perm,
                    onAllowOnce = { onReplyPermission(perm, "once") },
                    onAllowAlways = { onReplyPermission(perm, "always") },
                    onReject = { onReplyPermission(perm, "reject") },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            if (selectedTab == 1) {
                DiffViewerPane(
                    diffs = state.diffs,
                    isLoading = state.isLoadingDiffs,
                    modifier = Modifier.weight(1f)
                )
            } else {
                ChatScreen(
                    state = state,
                    onTextChange = onComposerTextChange,
                    onSelectFileMention = onSelectFileMention,
                    onSelectMode = onSelectComposerMode,
                    onOpenAgentMenu = { showAgentMenu = true },
                    onOpenModelSheet = { showModelSheet = true },
                    onOpenCommandMenu = { showCommandMenu = true },
                    onOpenDiffs = { selectedTab = 1 },
                    onReconnectSse = onReconnectSse,
                    onSend = onSendPrompt,
                    onAbort = onAbortSession,
                    onRetryResponse = onRetryResponse,
                    onDismissFailedPrompt = onDismissFailedPrompt,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        DropdownMenu(
            expanded = showAgentMenu,
            onDismissRequest = { showAgentMenu = false }
        ) {
            val agentNames = if (state.agents.isNotEmpty()) {
                state.agents.map { it.name to (it.description ?: "") }
            } else {
                listOf(
                    "build" to "Agen default untuk edit kode & jalankan tool",
                    "plan" to "Agen perencanaan read-only sebelum eksekusi"
                )
            }
            agentNames.forEach { (name, desc) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(name, fontWeight = FontWeight.SemiBold)
                            if (desc.isNotBlank()) {
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (name == state.selectedAgent) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        showAgentMenu = false
                        onSelectAgent(name)
                    }
                )
            }
        }

        DropdownMenu(
            expanded = showCommandMenu,
            onDismissRequest = { showCommandMenu = false }
        ) {
            val cmds = if (state.commands.isNotEmpty()) state.commands.map { it.name to (it.description ?: "") }
            else listOf("init" to "Analisis proyek & buat AGENTS.md", "compact" to "Ringkas konteks sesi", "review" to "Review perubahan terbaru")
            cmds.forEach { (cmdName, cmdDesc) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("/$cmdName", style = MaterialTheme.typography.labelLarge)
                            if (cmdDesc.isNotBlank()) {
                                Text(cmdDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = {
                        showCommandMenu = false
                        onSelectComposerMode(ComposerMode.COMMAND)
                        onComposerTextChange("$cmdName ")
                    }
                )
            }
        }
    }

    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }) {
            ModelSelectorSheetContent(
                models = state.models,
                selectedProviderId = state.selectedModel?.providerID,
                selectedModelId = state.selectedModel?.modelID,
                isRefreshingModels = state.isRefreshingModels,
                onRefreshModels = onRefreshModels,
                onSelect = { opt ->
                    showModelSheet = false
                    onSelectModel(opt.providerId, opt.modelId)
                }
            )
        }
    }
}

@Composable
internal fun ModelSelectorSheetContent(
    models: List<ModelOption>,
    selectedProviderId: String?,
    selectedModelId: String?,
    isRefreshingModels: Boolean,
    onRefreshModels: () -> Unit,
    onSelect: (ModelOption) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedProviderFilter by remember { mutableStateOf<String?>(null) }
    var onlyReady by remember { mutableStateOf(false) }

    val sortedModels = remember(models, selectedProviderId, selectedModelId) {
        ProviderCatalogDefaults.sortModelsReadyFirst(
            models = models,
            activeProviderId = selectedProviderId,
            activeModelId = selectedModelId
        )
    }

    val readyCount = remember(sortedModels) {
        sortedModels.count { it.isConnected }
    }

    val providerGroups = remember(sortedModels, selectedProviderId) {
        sortedModels.groupBy { it.providerId }
            .map { (id, items) ->
                Triple(
                    id,
                    items.first().providerName,
                    items.size to items.any { it.isConnected }
                )
            }
            .sortedWith(
                compareByDescending<Triple<String, String, Pair<Int, Boolean>>> { it.third.second }
                    .thenByDescending {
                        !selectedProviderId.isNullOrBlank() &&
                            it.first.equals(selectedProviderId, ignoreCase = true)
                    }
                    .thenByDescending { it.first.equals("opencode", ignoreCase = true) }
                    .thenBy { it.second.lowercase() }
            )
    }

    val filteredModels = remember(sortedModels, searchQuery, selectedProviderFilter, onlyReady) {
        val q = searchQuery.trim()
        sortedModels.filter { opt ->
            val matchesReady = !onlyReady || opt.isConnected
            val matchesProvider = selectedProviderFilter == null || opt.providerId.equals(selectedProviderFilter, ignoreCase = true)
            val matchesSearch = q.isEmpty() ||
                opt.modelName.contains(q, ignoreCase = true) ||
                opt.modelId.contains(q, ignoreCase = true) ||
                opt.providerName.contains(q, ignoreCase = true) ||
                opt.providerId.contains(q, ignoreCase = true)
            matchesReady && matchesProvider && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pilih Model AI (${sortedModels.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Diurutkan dari status Siap terlebih dahulu ($readyCount siap dari ${sortedModels.size}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onRefreshModels,
                enabled = !isRefreshingModels,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("sheet_refresh_models_button")
            ) {
                if (isRefreshingModels) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text("Muat Live")
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari model atau provider…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("model_search_input")
        )

        if (providerGroups.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedProviderFilter == null && !onlyReady,
                    onClick = {
                        selectedProviderFilter = null
                        onlyReady = false
                    },
                    label = { Text("Semua (${sortedModels.size})") },
                    modifier = Modifier.testTag("model_filter_all_chip")
                )
                if (readyCount > 0) {
                    FilterChip(
                        selected = onlyReady,
                        onClick = { onlyReady = !onlyReady },
                        label = { Text("Siap ($readyCount)") },
                        modifier = Modifier.testTag("model_filter_ready_chip")
                    )
                }
                providerGroups.forEach { (id, name, counts) ->
                    val (count, hasReady) = counts
                    FilterChip(
                        selected = selectedProviderFilter.equals(id, ignoreCase = true),
                        onClick = {
                            selectedProviderFilter = if (selectedProviderFilter.equals(id, ignoreCase = true)) null else id
                        },
                        leadingIcon = if (hasReady) {
                            {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Provider siap",
                                    tint = LocalOpenCodeColors.current.success,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null,
                        label = { Text("$name ($count)", maxLines = 1) }
                    )
                }
            }
        }

        if (filteredModels.isEmpty()) {
            Text(
                text = if (sortedModels.isEmpty()) {
                    "Belum ada daftar model dari server. Pastikan provider telah dikonfigurasi di menu Server."
                } else {
                    "Tidak ada model yang cocok dengan filter saat ini."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredModels, key = { "${it.providerId}:${it.modelId}" }) { opt ->
                    val isSelected = opt.providerId.equals(selectedProviderId, ignoreCase = true) &&
                        opt.modelId == selectedModelId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable { onSelect(opt) }
                            .testTag("model_option_${opt.providerId}_${opt.modelId}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = opt.modelName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${opt.providerName} · ${opt.modelId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (opt.isConnected) {
                                    LocalOpenCodeColors.current.successContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ) {
                                Text(
                                    text = if (opt.isConnected) "Siap" else "Perlu Key",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (opt.isConnected) {
                                        LocalOpenCodeColors.current.success
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
