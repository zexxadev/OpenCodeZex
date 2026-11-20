package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.CloudAppSettings
import com.example.data.firebase.CloudConfigSyncRepository
import com.example.data.firebase.NoOpCloudConfigSyncRepository
import com.example.data.local.LocalLlmClient
import com.example.data.local.LocalOpenCodeRuntime
import com.example.data.local.LocalWorkspaceManager
import com.example.data.model.AgentInfo
import com.example.data.model.ChatMessage
import com.example.data.model.CommandInfo
import com.example.data.model.FileContentResponse
import com.example.data.model.FileDiff
import com.example.data.model.FileNode
import com.example.data.model.GrepMatch
import com.example.data.model.LocalProviderConfig
import com.example.data.model.MessagePart
import com.example.data.model.ModelOption
import com.example.data.model.ModelReference
import com.example.data.model.OpenCodeEvent
import com.example.data.model.OpenCodeSession
import com.example.data.model.PermissionRequestItem
import com.example.data.model.ProviderApiFormat
import com.example.data.model.ProviderCatalog
import com.example.data.model.ProviderCatalogDefaults
import com.example.data.model.SavedProviderAccount
import com.example.data.model.ServerProfile
import com.example.data.model.SessionRunStatus
import com.example.data.model.SessionStatusInfo
import com.example.data.network.DiagnosticError
import com.example.data.network.OpenCodeClientManager
import com.example.data.network.OpenCodeSseClient
import com.example.data.preferences.ServerProfileStore
import com.example.data.repository.ApiOutcome
import com.example.data.repository.DataStoreProviderConfigRepository
import com.example.data.repository.OpenCodeRepository
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val version: String, val latencyMs: Long) : ConnectionState
    data class Error(val diagnostic: DiagnosticError) : ConnectionState
}

sealed interface CloudSyncStatus {
    data object Idle : CloudSyncStatus
    data object Syncing : CloudSyncStatus
    data class Synced(val lastSyncedAtMs: Long = System.currentTimeMillis()) : CloudSyncStatus
    data class Error(val message: String) : CloudSyncStatus
}

enum class ComposerMode(val label: String) {
    PROMPT("Prompt"),
    COMMAND("/Cmd"),
    SHELL("\$Shell")
}

enum class FileSearchMode {
    BROWSE, FIND_FILE, GREP
}

data class FailedPromptRequest(
    val sessionId: String,
    val input: String,
    val mode: ComposerMode,
    val agent: String,
    val model: ModelReference?,
    val errorTitle: String,
    val errorDetail: String
)

data class OpenCodeUiState(
    val profiles: List<ServerProfile> = ServerProfile.defaultPresets,
    val activeProfile: ServerProfile = ServerProfile.defaultPresets.first(),
    val connectionState: ConnectionState = ConnectionState.Idle,
    val sseConnected: Boolean = false,
    val sessions: List<OpenCodeSession> = emptyList(),
    val sessionStatuses: Map<String, SessionStatusInfo> = emptyMap(),
    val isLoadingSessions: Boolean = false,
    val sessionSearchQuery: String = "",

    val activeSessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val diffs: List<FileDiff> = emptyList(),
    val pendingPermissions: List<PermissionRequestItem> = emptyList(),
    val isLoadingMessages: Boolean = false,
    val isLoadingDiffs: Boolean = false,
    val isSendingPrompt: Boolean = false,
    val retryBannerMessage: String? = null,
    val failedPromptRequest: FailedPromptRequest? = null,

    val composerText: String = "",
    val composerMode: ComposerMode = ComposerMode.PROMPT,
    val fileMentions: List<String> = emptyList(),

    val agents: List<AgentInfo> = emptyList(),
    val selectedAgent: String = "build",
    val models: List<ModelOption> = emptyList(),
    val providerCatalog: ProviderCatalog? = null,
    val selectedModel: ModelReference? = null,
    val commands: List<CommandInfo> = emptyList(),

    val currentDirPath: String = "",
    val fileNodes: List<FileNode> = emptyList(),
    val fileSearchMode: FileSearchMode = FileSearchMode.BROWSE,
    val fileSearchQuery: String = "",
    val foundFilePaths: List<String> = emptyList(),
    val grepMatches: List<GrepMatch> = emptyList(),
    val isLoadingFiles: Boolean = false,
    val previewFilePath: String? = null,
    val previewFileContent: FileContentResponse? = null,
    val isLoadingFilePreview: Boolean = false,

    val themePreference: ThemePreference = ThemePreference.DARK,
    val hasCompletedSetup: Boolean? = null,
    val showSetupWizardManual: Boolean = false,
    val isTermuxInstalled: Boolean = false,
    val isSavingApiKey: Boolean = false,
    val isRefreshingModels: Boolean = false,
    val localProviderConfig: LocalProviderConfig = LocalProviderConfig(),
    val providerAccounts: List<SavedProviderAccount> = ProviderCatalogDefaults.defaultAccounts(),
    val localWorkspacePath: String = "",
    val authenticatedUserId: String? = null,
    val authenticatedUserEmail: String? = null,
    val authenticatedUserName: String? = null,
    val cloudSyncStatus: CloudSyncStatus = CloudSyncStatus.Idle
) {
    val shouldShowSetupWizard: Boolean
        get() = showSetupWizardManual || hasCompletedSetup == false
    val activeSession: OpenCodeSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }

    val activeSessionStatus: SessionStatusInfo
        get() = activeSessionId?.let { sessionStatuses[it] } ?: SessionStatusInfo()

    val isActiveSessionBusy: Boolean
        get() = activeSessionStatus.status != SessionRunStatus.IDLE

    val activeSessionPermissions: List<PermissionRequestItem>
        get() = pendingPermissions.filter { it.sessionId == activeSessionId }

    val filteredSessions: List<OpenCodeSession>
        get() {
            val q = sessionSearchQuery.trim()
            if (q.isEmpty()) return sessions
            return sessions.filter {
                it.displayTitle.contains(q, ignoreCase = true) ||
                    it.id.contains(q, ignoreCase = true)
            }
        }
}

class OpenCodeViewModel(
    application: Application,
    private val currentUserId: String? = null,
    private val authenticatedEmail: String? = null,
    private val authenticatedDisplayName: String? = null,
    private val cloudSyncRepository: CloudConfigSyncRepository = NoOpCloudConfigSyncRepository
) : AndroidViewModel(application) {

    companion object {
        fun provideFactory(
            application: Application,
            userId: String?,
            email: String?,
            displayName: String?,
            cloudSyncRepository: CloudConfigSyncRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OpenCodeViewModel(
                    application = application,
                    currentUserId = userId,
                    authenticatedEmail = email,
                    authenticatedDisplayName = displayName,
                    cloudSyncRepository = cloudSyncRepository
                ) as T
            }
        }
    }

    private val clientManager = OpenCodeClientManager()
    private val sseClient = OpenCodeSseClient(clientManager.json)
    private val profileStore = ServerProfileStore(application.applicationContext, clientManager.json)
    private val providerConfigRepository = DataStoreProviderConfigRepository(application.applicationContext, clientManager.json)
    private val workspaceManager = LocalWorkspaceManager(application.applicationContext)
    private val localLlmClient = LocalLlmClient(clientManager.json)
    private val localRuntime = LocalOpenCodeRuntime(workspaceManager, localLlmClient, clientManager.json)
    private val repository = OpenCodeRepository(
        clientManager = clientManager,
        sseClient = sseClient,
        profileStore = profileStore,
        providerConfigRepository = providerConfigRepository,
        localRuntime = localRuntime
    )

    private val _uiState = MutableStateFlow(
        OpenCodeUiState(
            authenticatedUserId = currentUserId,
            authenticatedUserEmail = authenticatedEmail,
            authenticatedUserName = authenticatedDisplayName
        )
    )
    val uiState: StateFlow<OpenCodeUiState> = _uiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private var sseJob: Job? = null
    private var mentionJob: Job? = null
    private var initializedConnection = false
    private var lastSubmittedPrompt: FailedPromptRequest? = null

    init {
        refreshTermuxStatus()
        val workspacePath = repository.initializeLocalEnvironment()
        _uiState.update { it.copy(localWorkspacePath = workspacePath) }

        viewModelScope.launch {
            providerConfigRepository.migrateFromLegacyIfNeeded(profileStore)

            val baseConfigFlow = combine(
                repository.profilesFlow,
                repository.activeProfileIdFlow,
                repository.themePreferenceFlow,
                repository.selectedAgentFlow,
                repository.selectedModelFlow
            ) { profiles, activeId, theme, agent, modelPair ->
                val active = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
                ConfigSnapshot(profiles, active, theme, agent, modelPair)
            }

            combine(
                baseConfigFlow,
                repository.hasCompletedSetupFlow,
                repository.localProviderConfigFlow,
                repository.providerAccountsFlow
            ) { snap, setupDone, localProv, accounts ->
                QuadSnapshot(snap, setupDone ?: false, localProv, accounts)
            }.collect { (snap, setupDone, localProv, accounts) ->
                val prevProfile = _uiState.value.activeProfile
                val modelRef = if (!snap.modelPair.first.isNullOrBlank() && !snap.modelPair.second.isNullOrBlank()) {
                    ModelReference(snap.modelPair.first!!, snap.modelPair.second!!)
                } else if (snap.active.isLocalAndroid) {
                    ModelReference(localProv.providerId, localProv.modelId)
                } else null

                _uiState.update {
                    it.copy(
                        profiles = snap.profiles,
                        activeProfile = snap.active,
                        themePreference = snap.theme,
                        selectedAgent = snap.agent,
                        selectedModel = modelRef,
                        hasCompletedSetup = setupDone,
                        localProviderConfig = localProv,
                        providerAccounts = accounts
                    )
                }
                if (snap.active.isLocalAndroid && _uiState.value.connectionState is ConnectionState.Connected) {
                    refreshServerMetadata(snap.active)
                }

                if (!initializedConnection || prevProfile.id != snap.active.id ||
                    prevProfile.normalizedUrl != snap.active.normalizedUrl ||
                    prevProfile.password != snap.active.password ||
                    prevProfile.directory != snap.active.directory ||
                    prevProfile.mode != snap.active.mode
                ) {
                    initializedConnection = true
                    connectToActiveServer(snap.active)
                }
            }
        }

        if (!currentUserId.isNullOrBlank()) {
            startCloudConfigSync(currentUserId)
        }
    }

    private fun startCloudConfigSync(uid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSyncStatus = CloudSyncStatus.Syncing) }
            val hasRemote = cloudSyncRepository.hasRemoteSettings(uid)
            if (hasRemote.isSuccess && hasRemote.getOrDefault(false) == false) {
                pushFullConfigToCloudInternal(silent = true)
            } else if (hasRemote.isFailure) {
                _uiState.update {
                    it.copy(
                        cloudSyncStatus = CloudSyncStatus.Error(
                            hasRemote.exceptionOrNull()?.message ?: "Gagal memeriksa konfigurasi cloud"
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            cloudSyncRepository.observeAppSettings(uid)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(cloudSyncStatus = CloudSyncStatus.Error(e.message ?: "Gagal membaca pengaturan cloud"))
                    }
                }
                .collect { remote ->
                    if (remote != null) {
                        val cur = _uiState.value
                        if (cur.themePreference != remote.themePreference) {
                            repository.profileStore.setThemePreference(remote.themePreference)
                        }
                        if (remote.activeProfileId.isNotBlank() && cur.activeProfile.id != remote.activeProfileId) {
                            repository.profileStore.setActiveProfileId(remote.activeProfileId)
                        }
                        if (remote.selectedAgent.isNotBlank() && cur.selectedAgent != remote.selectedAgent) {
                            repository.profileStore.setSelectedAgent(remote.selectedAgent)
                        }
                        if (remote.selectedProviderId.isNotBlank() && remote.selectedModelId.isNotBlank()) {
                            val currentModel = cur.selectedModel
                            if (currentModel?.providerID != remote.selectedProviderId ||
                                currentModel.modelID != remote.selectedModelId
                            ) {
                                repository.providerConfigRepository.setSelectedModel(
                                    remote.selectedProviderId,
                                    remote.selectedModelId
                                )
                            }
                        }
                        if (cur.hasCompletedSetup != remote.setupCompleted) {
                            repository.profileStore.setHasCompletedSetup(remote.setupCompleted)
                        }
                        _uiState.update { it.copy(cloudSyncStatus = CloudSyncStatus.Synced()) }
                    }
                }
        }

        viewModelScope.launch {
            cloudSyncRepository.observeServerProfiles(uid)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(cloudSyncStatus = CloudSyncStatus.Error(e.message ?: "Gagal sinkron profil server"))
                    }
                }
                .collect { remoteProfiles ->
                    if (remoteProfiles.isNotEmpty()) {
                        val localProfiles = repository.profilesFlow.first()
                        val merged = (remoteProfiles + localProfiles.filter { local ->
                            remoteProfiles.none { it.id == local.id }
                        })
                        if (merged != localProfiles) {
                            repository.profileStore.saveProfiles(merged)
                        }
                        _uiState.update { it.copy(cloudSyncStatus = CloudSyncStatus.Synced()) }
                    }
                }
        }

        viewModelScope.launch {
            cloudSyncRepository.observeProviderAccounts(uid)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(cloudSyncStatus = CloudSyncStatus.Error(e.message ?: "Gagal sinkron provider AI"))
                    }
                }
                .collect { remoteAccounts ->
                    if (remoteAccounts.isNotEmpty()) {
                        val currentLocal = repository.providerAccountsFlow.first()
                        val merged = ProviderCatalogDefaults.mergeWithBuiltIns(remoteAccounts)
                        val preservedMissingCustoms = currentLocal.filter { local ->
                            local.isCustom && merged.none { it.id.equals(local.id, ignoreCase = true) }
                        }
                        val finalList = merged + preservedMissingCustoms
                        if (finalList != currentLocal) {
                            repository.providerConfigRepository.saveProviderAccounts(finalList)
                        }
                        _uiState.update { it.copy(cloudSyncStatus = CloudSyncStatus.Synced()) }
                    }
                }
        }
    }

    fun syncNowToCloud() {
        if (currentUserId.isNullOrBlank()) return
        viewModelScope.launch {
            pushFullConfigToCloudInternal(silent = false)
        }
    }

    private suspend fun pushFullConfigToCloudInternal(silent: Boolean) {
        if (currentUserId.isNullOrBlank()) return
        _uiState.update { it.copy(cloudSyncStatus = CloudSyncStatus.Syncing) }
        val settingsResult = pushCurrentAppSettingsToCloud()
        val profiles = repository.profilesFlow.first()
        var firstError: Throwable? = settingsResult.exceptionOrNull()

        profiles.forEach { profile ->
            val res = cloudSyncRepository.saveServerProfile(profile)
            if (res.isFailure && firstError == null) firstError = res.exceptionOrNull()
        }

        val accounts = repository.providerAccountsFlow.first()
        accounts.filter { it.isCustom || it.apiKey.isNotBlank() || it.cachedModels.isNotEmpty() }.forEach { acc ->
            val res = cloudSyncRepository.saveProviderAccount(acc)
            if (res.isFailure && firstError == null) firstError = res.exceptionOrNull()
        }

        if (firstError == null) {
            _uiState.update { it.copy(cloudSyncStatus = CloudSyncStatus.Synced()) }
            if (!silent) {
                _snackbarEvents.tryEmit("Konfigurasi berhasil disinkronkan ke Firebase Cloud")
            }
        } else {
            val errMsg = firstError.message ?: "Sinkronisasi cloud gagal"
            _uiState.update { it.copy(cloudSyncStatus = CloudSyncStatus.Error(errMsg)) }
            if (!silent) {
                _snackbarEvents.tryEmit("Gagal sinkron ke Cloud: $errMsg")
            }
        }
    }

    private suspend fun pushCurrentAppSettingsToCloud(
        themeOverride: ThemePreference? = null,
        activeProfileIdOverride: String? = null,
        agentOverride: String? = null,
        providerIdOverride: String? = null,
        modelIdOverride: String? = null,
        setupCompletedOverride: Boolean? = null
    ): Result<Unit> {
        if (currentUserId.isNullOrBlank()) return Result.success(Unit)
        val state = _uiState.value
        val activeModel = state.selectedModel
        return cloudSyncRepository.saveAppSettings(
            CloudAppSettings(
                themePreference = themeOverride ?: state.themePreference,
                activeProfileId = activeProfileIdOverride ?: state.activeProfile.id,
                selectedAgent = agentOverride ?: state.selectedAgent,
                selectedProviderId = providerIdOverride ?: activeModel?.providerID ?: state.localProviderConfig.providerId,
                selectedModelId = modelIdOverride ?: activeModel?.modelID ?: state.localProviderConfig.modelId,
                setupCompleted = setupCompletedOverride ?: (state.hasCompletedSetup == true)
            )
        ).also { res ->
            res.onSuccess {
                _uiState.update { it.copy(cloudSyncStatus = CloudSyncStatus.Synced()) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(cloudSyncStatus = CloudSyncStatus.Error(err.message ?: "Gagal menyimpan ke Cloud"))
                }
            }
        }
    }

    private data class QuadSnapshot(
        val config: ConfigSnapshot,
        val setupDone: Boolean,
        val localProvider: LocalProviderConfig,
        val accounts: List<SavedProviderAccount>
    )

    private data class ConfigSnapshot(
        val profiles: List<ServerProfile>,
        val active: ServerProfile,
        val theme: ThemePreference,
        val agent: String,
        val modelPair: Pair<String?, String?>
    )

    fun connectToActiveServer(profile: ServerProfile = _uiState.value.activeProfile) {
        sseJob?.cancel()
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.Connecting,
                sseConnected = false
            )
        }
        viewModelScope.launch {
            when (val healthOutcome = repository.checkHealth(profile)) {
                is ApiOutcome.Success -> {
                    val info = healthOutcome.data
                    val version = info.health.version ?: "v1"
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.Connected(version, info.latencyMs)
                        )
                    }
                    startSseSubscription(profile)
                    refreshServerMetadata(profile)
                    refreshSessions(profile)
                    loadDirectory("")
                }
                is ApiOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.Error(healthOutcome.error),
                            sseConnected = false
                        )
                    }
                }
            }
        }
    }

    private fun startSseSubscription(profile: ServerProfile) {
        sseJob?.cancel()
        sseJob = viewModelScope.launch {
            repository.observeEvents(profile).collect { event ->
                handleSseEvent(event)
            }
        }
    }

    fun reconnectSse() {
        val profile = _uiState.value.activeProfile
        _uiState.update { it.copy(sseConnected = false) }
        startSseSubscription(profile)
        _snackbarEvents.tryEmit("Menghubungkan ulang stream SSE…")
    }

    private fun handleSseEvent(event: OpenCodeEvent) {
        when (event) {
            is OpenCodeEvent.Connected -> {
                _uiState.update { it.copy(sseConnected = true) }
            }

            is OpenCodeEvent.SessionStatusChanged -> {
                _uiState.update { state ->
                    val updated = state.sessionStatuses.toMutableMap()
                    updated[event.sessionId] = event.status
                    val clearRetry = event.sessionId == state.activeSessionId &&
                        (event.status.status == SessionRunStatus.IDLE || event.status.status == SessionRunStatus.BUSY)
                    state.copy(
                        sessionStatuses = updated,
                        sseConnected = true,
                        retryBannerMessage = if (clearRetry) null else state.retryBannerMessage
                    )
                }
                if (event.sessionId == _uiState.value.activeSessionId &&
                    event.status.status == SessionRunStatus.IDLE
                ) {
                    refreshSessionDiff(event.sessionId)
                    refreshMessages(event.sessionId, silent = true)
                }
            }

            is OpenCodeEvent.SessionUpserted -> {
                _uiState.update { state ->
                    val list = state.sessions.toMutableList()
                    val idx = list.indexOfFirst { it.id == event.session.id }
                    if (idx >= 0) list[idx] = event.session else list.add(0, event.session)
                    state.copy(
                        sessions = list.sortedByDescending { it.time?.updated ?: it.time?.created ?: 0L }
                    )
                }
            }

            is OpenCodeEvent.SessionRemoved -> {
                _uiState.update { state ->
                    val remaining = state.sessions.filterNot { it.id == event.sessionId }
                    val newActive = if (state.activeSessionId == event.sessionId) null else state.activeSessionId
                    state.copy(
                        sessions = remaining,
                        activeSessionId = newActive,
                        messages = if (newActive == null) emptyList() else state.messages
                    )
                }
            }

            is OpenCodeEvent.MessageUpserted -> {
                val activeId = _uiState.value.activeSessionId ?: return
                if (event.message.sessionId != activeId) return
                _uiState.update { state ->
                    val list = state.messages.toMutableList()
                    val idx = list.indexOfFirst { it.id == event.message.id }
                    if (idx >= 0) {
                        val existing = list[idx]
                        val mergedParts = if (event.message.parts.isEmpty()) existing.parts else event.message.parts
                        list[idx] = event.message.copy(parts = mergedParts)
                    } else {
                        list.add(event.message)
                    }
                    state.copy(messages = list.sortedBy { it.createdAt })
                }
            }

            is OpenCodeEvent.PartUpserted -> {
                val activeId = _uiState.value.activeSessionId ?: return
                if (event.sessionId != null && event.sessionId != activeId) return
                val initialPart = applyInitialDelta(event.part, event.delta)
                _uiState.update { state ->
                    val list = state.messages.toMutableList()
                    val msgIdx = list.indexOfFirst { it.id == initialPart.messageId }
                    if (msgIdx >= 0) {
                        val msg = list[msgIdx]
                        val parts = msg.parts.toMutableList()
                        val partIdx = parts.indexOfFirst { it.id == initialPart.id }
                        if (partIdx >= 0) {
                            parts[partIdx] = mergePart(parts[partIdx], event.part, event.delta)
                        } else {
                            parts.add(initialPart)
                        }
                        list[msgIdx] = msg.copy(parts = parts)
                    } else {
                        list.add(
                            ChatMessage(
                                id = initialPart.messageId,
                                sessionId = activeId,
                                role = "assistant",
                                createdAt = System.currentTimeMillis(),
                                completedAt = null,
                                agent = state.selectedAgent,
                                modelId = state.selectedModel?.modelID,
                                providerId = state.selectedModel?.providerID,
                                errorText = null,
                                parts = listOf(initialPart)
                            )
                        )
                    }
                    state.copy(
                        messages = list,
                        sseConnected = true,
                        retryBannerMessage = null,
                        failedPromptRequest = if (state.failedPromptRequest?.sessionId == activeId) null else state.failedPromptRequest
                    )
                }
            }

            is OpenCodeEvent.PermissionAsked -> {
                _uiState.update { state ->
                    val existing = state.pendingPermissions.filterNot { it.id == event.permission.id }
                    state.copy(pendingPermissions = existing + event.permission)
                }
            }

            is OpenCodeEvent.PermissionResolved -> {
                _uiState.update { state ->
                    state.copy(
                        pendingPermissions = state.pendingPermissions.filterNot {
                            it.id == event.permissionId
                        }
                    )
                }
            }

            is OpenCodeEvent.SessionError -> {
                val curState = _uiState.value
                if (event.sessionId == null || event.sessionId == curState.activeSessionId) {
                    _uiState.update { state ->
                        val sid = event.sessionId ?: state.activeSessionId
                        val shouldShowBanner = !state.activeProfile.isLocalAndroid ||
                            state.messages.none { !it.errorText.isNullOrBlank() }
                        val updatedFailed = if (shouldShowBanner && sid != null) {
                            lastSubmittedPrompt?.takeIf { it.sessionId == sid }?.copy(
                                errorTitle = "Respon AI Gagal",
                                errorDetail = event.message
                            )
                        } else {
                            state.failedPromptRequest
                        }
                        state.copy(
                            retryBannerMessage = null,
                            failedPromptRequest = updatedFailed
                        )
                    }
                    _snackbarEvents.tryEmit(event.message)
                }
            }

            is OpenCodeEvent.Other -> {
                if (event.type == "retry.attempt" && !event.detail.isNullOrBlank()) {
                    _uiState.update { it.copy(retryBannerMessage = event.detail) }
                }
            }
        }
    }

    private fun applyInitialDelta(incoming: MessagePart, delta: String?): MessagePart {
        if (delta.isNullOrEmpty()) return incoming
        return when {
            incoming is MessagePart.Text && incoming.text.isEmpty() -> incoming.copy(text = delta)
            incoming is MessagePart.Reasoning && incoming.text.isEmpty() -> incoming.copy(text = delta)
            else -> incoming
        }
    }

    private fun mergePart(existing: MessagePart, incoming: MessagePart, delta: String?): MessagePart {
        return if (existing is MessagePart.Text && incoming is MessagePart.Text &&
            incoming.text.isEmpty() && !delta.isNullOrEmpty()
        ) {
            incoming.copy(text = existing.text + delta)
        } else if (existing is MessagePart.Reasoning && incoming is MessagePart.Reasoning &&
            incoming.text.isEmpty() && !delta.isNullOrEmpty()
        ) {
            incoming.copy(text = existing.text + delta)
        } else {
            incoming
        }
    }

    private fun refreshServerMetadata(profile: ServerProfile) {
        viewModelScope.launch {
            when (val providers = repository.getProvidersAndModels(profile)) {
                is ApiOutcome.Success -> {
                    val (catalog, models) = providers.data
                    _uiState.update { state ->
                        val currentModel = state.selectedModel
                        val resolvedModel = currentModel ?: models.firstOrNull { it.isConnected }?.let {
                            ModelReference(it.providerId, it.modelId)
                        }
                        state.copy(
                            providerCatalog = catalog,
                            models = models,
                            selectedModel = resolvedModel
                        )
                    }
                }
                is ApiOutcome.Failure -> Unit
            }
        }
        viewModelScope.launch {
            when (val agents = repository.getAgents(profile)) {
                is ApiOutcome.Success -> {
                    _uiState.update { state ->
                        val primaryAgents = agents.data.filter { it.mode != "subagent" }
                        state.copy(agents = primaryAgents.ifEmpty { agents.data })
                    }
                }
                is ApiOutcome.Failure -> Unit
            }
        }
        viewModelScope.launch {
            when (val commands = repository.getCommands(profile)) {
                is ApiOutcome.Success -> {
                    _uiState.update { it.copy(commands = commands.data) }
                }
                is ApiOutcome.Failure -> Unit
            }
        }
    }

    fun refreshSessions(profile: ServerProfile = _uiState.value.activeProfile) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSessions = true) }
            when (val res = repository.listSessions(profile)) {
                is ApiOutcome.Success -> {
                    _uiState.update { it.copy(sessions = res.data, isLoadingSessions = false) }
                }
                is ApiOutcome.Failure -> {
                    _uiState.update { it.copy(isLoadingSessions = false) }
                    _snackbarEvents.tryEmit(res.error.title)
                }
            }
            when (val statusRes = repository.getSessionsStatus(profile)) {
                is ApiOutcome.Success -> {
                    _uiState.update { it.copy(sessionStatuses = statusRes.data) }
                }
                is ApiOutcome.Failure -> Unit
            }
        }
    }

    fun updateSessionSearch(query: String) {
        _uiState.update { it.copy(sessionSearchQuery = query) }
    }

    fun createSession(title: String?, onCreated: (OpenCodeSession) -> Unit = {}) {
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            when (val res = repository.createSession(profile, title)) {
                is ApiOutcome.Success -> {
                    val created = res.data
                    _uiState.update { state ->
                        val updated = listOf(created) + state.sessions.filterNot { it.id == created.id }
                        state.copy(sessions = updated)
                    }
                    openSession(created.id)
                    onCreated(created)
                }
                is ApiOutcome.Failure -> {
                    _snackbarEvents.tryEmit("${res.error.title}: ${res.error.detail}")
                }
            }
        }
    }

    fun openSession(sessionId: String) {
        _uiState.update {
            it.copy(
                activeSessionId = sessionId,
                messages = emptyList(),
                diffs = emptyList(),
                retryBannerMessage = null,
                failedPromptRequest = null
            )
        }
        refreshMessages(sessionId)
        refreshSessionDiff(sessionId)
    }

    fun refreshMessages(sessionId: String, silent: Boolean = false) {
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isLoadingMessages = true) }
            when (val res = repository.getMessages(profile, sessionId)) {
                is ApiOutcome.Success -> {
                    if (_uiState.value.activeSessionId == sessionId) {
                        _uiState.update { it.copy(messages = res.data, isLoadingMessages = false) }
                    }
                }
                is ApiOutcome.Failure -> {
                    _uiState.update { it.copy(isLoadingMessages = false) }
                    if (!silent) _snackbarEvents.tryEmit(res.error.title)
                }
            }
        }
    }

    fun refreshSessionDiff(sessionId: String) {
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDiffs = true) }
            when (val res = repository.getSessionDiff(profile, sessionId)) {
                is ApiOutcome.Success -> {
                    if (_uiState.value.activeSessionId == sessionId) {
                        _uiState.update { it.copy(diffs = res.data, isLoadingDiffs = false) }
                    }
                }
                is ApiOutcome.Failure -> {
                    _uiState.update { it.copy(isLoadingDiffs = false) }
                }
            }
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            when (val res = repository.renameSession(profile, sessionId, trimmed)) {
                is ApiOutcome.Success -> {
                    _uiState.update { state ->
                        state.copy(sessions = state.sessions.map { if (it.id == sessionId) res.data else it })
                    }
                    _snackbarEvents.tryEmit("Judul sesi diperbarui")
                }
                is ApiOutcome.Failure -> _snackbarEvents.tryEmit(res.error.title)
            }
        }
    }

    fun forkSession(sessionId: String, onForked: (OpenCodeSession) -> Unit = {}) {
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            when (val res = repository.forkSession(profile, sessionId)) {
                is ApiOutcome.Success -> {
                    val forked = res.data
                    _uiState.update { state ->
                        state.copy(sessions = listOf(forked) + state.sessions)
                    }
                    openSession(forked.id)
                    _snackbarEvents.tryEmit("Sesi berhasil di-fork")
                    onForked(forked)
                }
                is ApiOutcome.Failure -> _snackbarEvents.tryEmit(res.error.title)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            when (val res = repository.deleteSession(profile, sessionId)) {
                is ApiOutcome.Success -> {
                    _uiState.update { state ->
                        val nextActive = if (state.activeSessionId == sessionId) null else state.activeSessionId
                        state.copy(
                            sessions = state.sessions.filterNot { it.id == sessionId },
                            activeSessionId = nextActive,
                            messages = if (nextActive == null) emptyList() else state.messages
                        )
                    }
                    _snackbarEvents.tryEmit("Sesi dihapus")
                }
                is ApiOutcome.Failure -> _snackbarEvents.tryEmit(res.error.title)
            }
        }
    }

    fun updateComposerText(text: String) {
        _uiState.update { it.copy(composerText = text) }
        val atMatch = Regex("(?:^|\\s)@([^\\s@]*)$").find(text)
        if (atMatch != null && _uiState.value.connectionState is ConnectionState.Connected) {
            val query = atMatch.groupValues[1]
            mentionJob?.cancel()
            mentionJob = viewModelScope.launch {
                delay(180)
                when (val res = repository.findFiles(_uiState.value.activeProfile, query)) {
                    is ApiOutcome.Success -> {
                        _uiState.update { it.copy(fileMentions = res.data.take(8)) }
                    }
                    is ApiOutcome.Failure -> {
                        _uiState.update { it.copy(fileMentions = emptyList()) }
                    }
                }
            }
        } else {
            _uiState.update { it.copy(fileMentions = emptyList()) }
        }
    }

    fun insertFileMention(filePath: String) {
        val current = _uiState.value.composerText
        val replaced = current.replace(Regex("@[^\\s@]*$"), "@$filePath ")
        _uiState.update {
            it.copy(
                composerText = if (replaced == current) "$current @$filePath ".trimStart() else replaced,
                fileMentions = emptyList()
            )
        }
    }

    fun appendFileToPrompt(filePath: String) {
        _uiState.update { state ->
            val prefix = if (state.composerText.isBlank()) "" else "${state.composerText.trimEnd()} "
            state.copy(composerText = "$prefix@$filePath ")
        }
        _snackbarEvents.tryEmit("@$filePath disisipkan ke prompt")
    }

    fun setComposerMode(mode: ComposerMode) {
        _uiState.update { it.copy(composerMode = mode, fileMentions = emptyList()) }
    }

    fun sendCurrentInput() {
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: return
        val input = state.composerText.trim()
        if (input.isEmpty() || state.isSendingPrompt) return

        val profile = state.activeProfile
        val agent = state.selectedAgent
        val model = state.selectedModel
        val submitted = FailedPromptRequest(
            sessionId = sessionId,
            input = input,
            mode = state.composerMode,
            agent = agent,
            model = model,
            errorTitle = "",
            errorDetail = ""
        )
        lastSubmittedPrompt = submitted

        _uiState.update {
            it.copy(
                composerText = "",
                fileMentions = emptyList(),
                isSendingPrompt = true,
                retryBannerMessage = null,
                failedPromptRequest = null
            )
        }

        viewModelScope.launch {
            val outcome = executeComposerSubmission(
                profile = profile,
                sessionId = sessionId,
                input = input,
                mode = state.composerMode,
                agent = agent,
                model = model
            )

            _uiState.update { it.copy(isSendingPrompt = false) }
            when (outcome) {
                is ApiOutcome.Success -> {
                    _uiState.update { cur ->
                        cur.copy(
                            failedPromptRequest = if (cur.failedPromptRequest?.sessionId == sessionId) null else cur.failedPromptRequest
                        )
                    }
                    // For prompt/command streaming, SSE delivers live PartUpserted & MessageUpserted events;
                    // canonical refresh runs when SessionStatusChanged returns to IDLE.
                    if (state.composerMode == ComposerMode.SHELL) {
                        refreshMessages(sessionId, silent = true)
                    }
                }
                is ApiOutcome.Failure -> {
                    // Restore text so user doesn't lose prompt & expose Retry banner
                    _uiState.update { cur ->
                        cur.copy(
                            composerText = input,
                            retryBannerMessage = null,
                            failedPromptRequest = submitted.copy(
                                errorTitle = outcome.error.title,
                                errorDetail = outcome.error.detail
                            )
                        )
                    }
                    _snackbarEvents.tryEmit("${outcome.error.title}: ${outcome.error.detail}")
                }
            }
        }
    }

    fun retryFailedResponse(failedAssistantMessageId: String? = null) {
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: return
        if (state.isSendingPrompt) return

        val profile = state.activeProfile
        if (failedAssistantMessageId != null) {
            val msgIdx = state.messages.indexOfFirst { it.id == failedAssistantMessageId }
            val failedMsg = state.messages.getOrNull(msgIdx)
            val precedingPrompt = if (msgIdx > 0) {
                state.messages.subList(0, msgIdx)
                    .lastOrNull { !it.isAssistant }
                    ?.parts
                    ?.filterIsInstance<MessagePart.Text>()
                    ?.joinToString("\n") { it.text }
                    ?.trim()
                    .orEmpty()
            } else {
                ""
            }
            val fallbackPrompt = precedingPrompt.ifBlank {
                state.failedPromptRequest?.takeIf { it.sessionId == sessionId }?.input
                    ?: lastSubmittedPrompt?.takeIf { it.sessionId == sessionId }?.input
                    ?: ""
            }
            if (fallbackPrompt.isBlank()) {
                _snackbarEvents.tryEmit("Tidak ditemukan prompt sebelumnya untuk diulangi")
                return
            }

            val agent = failedMsg?.agent ?: state.selectedAgent
            val model = state.selectedModel
            val tracked = FailedPromptRequest(
                sessionId = sessionId,
                input = fallbackPrompt,
                mode = ComposerMode.PROMPT,
                agent = agent,
                model = model,
                errorTitle = "",
                errorDetail = ""
            )
            lastSubmittedPrompt = tracked

            _uiState.update {
                it.copy(
                    isSendingPrompt = true,
                    retryBannerMessage = null,
                    failedPromptRequest = null
                )
            }

            viewModelScope.launch {
                val outcome = repository.retryPrompt(
                    profile = profile,
                    sessionId = sessionId,
                    failedAssistantMessageId = failedAssistantMessageId,
                    promptText = fallbackPrompt,
                    agent = agent,
                    model = model
                )
                _uiState.update { it.copy(isSendingPrompt = false) }
                if (outcome is ApiOutcome.Failure) {
                    _uiState.update { cur ->
                        cur.copy(
                            retryBannerMessage = null,
                            failedPromptRequest = tracked.copy(
                                errorTitle = outcome.error.title,
                                errorDetail = outcome.error.detail
                            )
                        )
                    }
                    _snackbarEvents.tryEmit("${outcome.error.title}: ${outcome.error.detail}")
                }
            }
            return
        }

        val failed = state.failedPromptRequest?.takeIf { it.sessionId == sessionId }
            ?: lastSubmittedPrompt?.takeIf { it.sessionId == sessionId }
            ?: return

        _uiState.update { cur ->
            val clearComposer = cur.composerText.trim() == failed.input
            cur.copy(
                composerText = if (clearComposer) "" else cur.composerText,
                isSendingPrompt = true,
                retryBannerMessage = null,
                failedPromptRequest = null
            )
        }

        viewModelScope.launch {
            val outcome = executeComposerSubmission(
                profile = profile,
                sessionId = sessionId,
                input = failed.input,
                mode = failed.mode,
                agent = failed.agent,
                model = failed.model
            )
            _uiState.update { it.copy(isSendingPrompt = false) }
            when (outcome) {
                is ApiOutcome.Success -> {
                    if (failed.mode == ComposerMode.SHELL) {
                        refreshMessages(sessionId, silent = true)
                    }
                }
                is ApiOutcome.Failure -> {
                    _uiState.update { cur ->
                        cur.copy(
                            retryBannerMessage = null,
                            failedPromptRequest = failed.copy(
                                errorTitle = outcome.error.title,
                                errorDetail = outcome.error.detail
                            )
                        )
                    }
                    _snackbarEvents.tryEmit("${outcome.error.title}: ${outcome.error.detail}")
                }
            }
        }
    }

    fun dismissFailedPrompt() {
        _uiState.update { it.copy(failedPromptRequest = null) }
    }

    private suspend fun executeComposerSubmission(
        profile: ServerProfile,
        sessionId: String,
        input: String,
        mode: ComposerMode,
        agent: String,
        model: ModelReference?
    ): ApiOutcome<Unit> {
        return when (mode) {
            ComposerMode.PROMPT -> {
                if (input.startsWith("/")) {
                    val parts = input.split(" ", limit = 2)
                    val cmd = parts[0].removePrefix("/")
                    val args = parts.getOrElse(1) { "" }
                    repository.sendCommand(profile, sessionId, cmd, args, agent)
                } else {
                    repository.sendPromptAsync(profile, sessionId, input, agent, model)
                }
            }
            ComposerMode.COMMAND -> {
                val parts = input.split(" ", limit = 2)
                val cmd = parts[0].removePrefix("/")
                val args = parts.getOrElse(1) { "" }
                repository.sendCommand(profile, sessionId, cmd, args, agent)
            }
            ComposerMode.SHELL -> {
                repository.sendShell(profile, sessionId, input, agent, model)
            }
        }
    }

    fun abortActiveSession() {
        val sessionId = _uiState.value.activeSessionId ?: return
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            when (val res = repository.abortSession(profile, sessionId)) {
                is ApiOutcome.Success -> {
                    _snackbarEvents.tryEmit("Eksekusi agen dihentikan")
                    refreshMessages(sessionId, silent = true)
                }
                is ApiOutcome.Failure -> _snackbarEvents.tryEmit(res.error.title)
            }
        }
    }

    fun replyPermission(permission: PermissionRequestItem, decision: String) {
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            when (val res = repository.replyPermission(profile, permission.sessionId, permission.id, decision)) {
                is ApiOutcome.Success -> {
                    _uiState.update { state ->
                        state.copy(pendingPermissions = state.pendingPermissions.filterNot { it.id == permission.id })
                    }
                }
                is ApiOutcome.Failure -> _snackbarEvents.tryEmit(res.error.title)
            }
        }
    }

    fun selectAgent(agentName: String) {
        viewModelScope.launch {
            repository.profileStore.setSelectedAgent(agentName)
            pushCurrentAppSettingsToCloud(agentOverride = agentName)
        }
    }

    fun selectModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            repository.providerConfigRepository.setSelectedModel(providerId, modelId)
            pushCurrentAppSettingsToCloud(providerIdOverride = providerId, modelIdOverride = modelId)
        }
    }

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch {
            repository.profileStore.setThemePreference(preference)
            pushCurrentAppSettingsToCloud(themeOverride = preference)
        }
    }

    fun saveServerProfile(profile: ServerProfile, setActive: Boolean = true) {
        viewModelScope.launch {
            val current = _uiState.value.profiles.toMutableList()
            val idx = current.indexOfFirst { it.id == profile.id }
            if (idx >= 0) current[idx] = profile else current.add(0, profile)
            repository.profileStore.saveProfiles(current)
            if (setActive) {
                repository.profileStore.setActiveProfileId(profile.id)
            }
            cloudSyncRepository.saveServerProfile(profile)
            if (setActive) {
                pushCurrentAppSettingsToCloud(activeProfileIdOverride = profile.id)
            }
            _snackbarEvents.tryEmit("Profil server '${profile.name}' disimpan")
        }
    }

    fun activateServerProfile(profileId: String) {
        viewModelScope.launch {
            repository.profileStore.setActiveProfileId(profileId)
            pushCurrentAppSettingsToCloud(activeProfileIdOverride = profileId)
        }
    }

    fun deleteServerProfile(profileId: String) {
        viewModelScope.launch {
            val current = _uiState.value.profiles
            if (current.size <= 1) {
                _snackbarEvents.tryEmit("Minimal harus ada satu profil server")
                return@launch
            }
            val updated = current.filterNot { it.id == profileId }
            repository.profileStore.saveProfiles(updated)
            val wasActive = _uiState.value.activeProfile.id == profileId
            if (wasActive) {
                repository.profileStore.setActiveProfileId(updated.first().id)
            }
            cloudSyncRepository.deleteServerProfile(profileId)
            if (wasActive) {
                pushCurrentAppSettingsToCloud(activeProfileIdOverride = updated.first().id)
            }
            _snackbarEvents.tryEmit("Profil server dihapus")
        }
    }

    // Files Explorer
    fun loadDirectory(path: String) {
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentDirPath = path,
                    fileSearchMode = FileSearchMode.BROWSE,
                    isLoadingFiles = true
                )
            }
            when (val res = repository.listFiles(profile, path)) {
                is ApiOutcome.Success -> {
                    _uiState.update { it.copy(fileNodes = res.data, isLoadingFiles = false) }
                }
                is ApiOutcome.Failure -> {
                    _uiState.update { it.copy(isLoadingFiles = false) }
                }
            }
        }
    }

    fun navigateUpDirectory() {
        val current = _uiState.value.currentDirPath.trimEnd('/')
        if (current.isEmpty()) return
        val parent = current.substringBeforeLast('/', "")
        loadDirectory(parent)
    }

    fun updateFileSearchQuery(query: String) {
        _uiState.update { it.copy(fileSearchQuery = query) }
    }

    fun setFileSearchMode(mode: FileSearchMode) {
        _uiState.update { it.copy(fileSearchMode = mode) }
        if (mode == FileSearchMode.BROWSE) {
            loadDirectory(_uiState.value.currentDirPath)
        } else {
            executeFileSearch()
        }
    }

    fun executeFileSearch() {
        val state = _uiState.value
        val query = state.fileSearchQuery.trim()
        if (query.isEmpty()) return
        val profile = state.activeProfile
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFiles = true) }
            when (state.fileSearchMode) {
                FileSearchMode.FIND_FILE -> {
                    when (val res = repository.findFiles(profile, query)) {
                        is ApiOutcome.Success -> {
                            _uiState.update { it.copy(foundFilePaths = res.data, isLoadingFiles = false) }
                        }
                        is ApiOutcome.Failure -> {
                            _uiState.update { it.copy(isLoadingFiles = false) }
                            _snackbarEvents.tryEmit(res.error.title)
                        }
                    }
                }
                FileSearchMode.GREP -> {
                    when (val res = repository.findText(profile, query)) {
                        is ApiOutcome.Success -> {
                            _uiState.update { it.copy(grepMatches = res.data, isLoadingFiles = false) }
                        }
                        is ApiOutcome.Failure -> {
                            _uiState.update { it.copy(isLoadingFiles = false) }
                            _snackbarEvents.tryEmit(res.error.title)
                        }
                    }
                }
                FileSearchMode.BROWSE -> {
                    loadDirectory(state.currentDirPath)
                }
            }
        }
    }

    fun openFilePreview(path: String) {
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    previewFilePath = path,
                    previewFileContent = null,
                    isLoadingFilePreview = true
                )
            }
            when (val res = repository.readFileContent(profile, path)) {
                is ApiOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            previewFileContent = res.data,
                            isLoadingFilePreview = false
                        )
                    }
                }
                is ApiOutcome.Failure -> {
                    _uiState.update { it.copy(isLoadingFilePreview = false) }
                    _snackbarEvents.tryEmit(res.error.title)
                }
            }
        }
    }

    fun closeFilePreview() {
        _uiState.update { it.copy(previewFilePath = null, previewFileContent = null) }
    }

    fun refreshTermuxStatus() {
        val installed = try {
            getApplication<Application>().packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (_: Exception) {
            false
        }
        _uiState.update { it.copy(isTermuxInstalled = installed) }
    }

    fun openSetupWizard() {
        refreshTermuxStatus()
        _uiState.update { it.copy(showSetupWizardManual = true) }
    }

    fun completeSetupWizard() {
        viewModelScope.launch {
            repository.profileStore.setHasCompletedSetup(true)
            _uiState.update { it.copy(showSetupWizardManual = false, hasCompletedSetup = true) }
            pushCurrentAppSettingsToCloud(setupCompletedOverride = true)
        }
    }

    fun saveProviderApiKey(
        providerId: String,
        apiKey: String,
        modelId: String? = null,
        customBaseUrl: String? = null,
        apiFormat: ProviderApiFormat? = null
    ) {
        val cleanProvider = providerId.trim().lowercase()
        val cleanKey = apiKey.trim()
        val isCustomEndpoint = !customBaseUrl.isNullOrBlank() &&
            ProviderCatalogDefaults.builtInSpec(cleanProvider) == null
        if (cleanProvider.isEmpty() || (cleanKey.isEmpty() && !isCustomEndpoint)) {
            _snackbarEvents.tryEmit("Provider ID dan API Key tidak boleh kosong")
            return
        }
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingApiKey = true) }
            when (val res = repository.setProviderApiKey(profile, cleanProvider, cleanKey, modelId, customBaseUrl, apiFormat)) {
                is ApiOutcome.Success -> {
                    _uiState.update { it.copy(isSavingApiKey = false) }
                    val updatedAccount = repository.providerAccountsFlow.first()
                        .firstOrNull { it.id.equals(cleanProvider, ignoreCase = true) }
                    if (updatedAccount != null) {
                        cloudSyncRepository.saveProviderAccount(updatedAccount)
                        pushCurrentAppSettingsToCloud(
                            providerIdOverride = updatedAccount.id,
                            modelIdOverride = modelId?.takeIf { it.isNotBlank() } ?: updatedAccount.defaultModelId
                        )
                    }
                    refreshServerMetadata(profile)
                    val targetLabel = if (profile.isLocalAndroid) "Runtime Lokal Android" else "server OpenCode"
                    _snackbarEvents.tryEmit("API Key '$cleanProvider' berhasil disimpan ke $targetLabel")
                }
                is ApiOutcome.Failure -> {
                    _uiState.update { it.copy(isSavingApiKey = false) }
                    _snackbarEvents.tryEmit("${res.error.title}: ${res.error.detail}")
                }
            }
        }
    }

    fun saveProviderAccount(
        account: SavedProviderAccount,
        activate: Boolean = true,
        autoFetchModels: Boolean = true
    ) {
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingApiKey = true) }
            when (val res = repository.saveProviderAccount(account, activate, profile)) {
                is ApiOutcome.Success -> {
                    _uiState.update { it.copy(isSavingApiKey = false) }
                    val savedId = account.id.trim().lowercase()
                    val persisted = repository.providerAccountsFlow.first()
                        .firstOrNull { it.id.equals(savedId, ignoreCase = true) }
                        ?: account.copy(id = savedId)
                    cloudSyncRepository.saveProviderAccount(persisted)
                    if (activate) {
                        pushCurrentAppSettingsToCloud(
                            providerIdOverride = persisted.id,
                            modelIdOverride = persisted.defaultModelId
                        )
                    }
                    refreshServerMetadata(profile)
                    _snackbarEvents.tryEmit("Provider '${account.name.ifBlank { account.id }}' disimpan")
                    if (autoFetchModels) {
                        refreshProviderModels(savedId, silentSuccess = true)
                    }
                }
                is ApiOutcome.Failure -> {
                    _uiState.update { it.copy(isSavingApiKey = false) }
                    _snackbarEvents.tryEmit("${res.error.title}: ${res.error.detail}")
                }
            }
        }
    }

    fun deleteCustomProvider(providerId: String) {
        viewModelScope.launch {
            repository.deleteCustomProviderAccount(providerId)
            cloudSyncRepository.deleteProviderAccount(providerId)
            pushCurrentAppSettingsToCloud()
            refreshServerMetadata(_uiState.value.activeProfile)
            _snackbarEvents.tryEmit("Provider custom dihapus")
        }
    }

    fun refreshProviderModels(providerId: String? = null, silentSuccess: Boolean = false) {
        val profile = _uiState.value.activeProfile
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingModels = true) }
            when (val res = repository.refreshProviderModels(providerId)) {
                is ApiOutcome.Success -> {
                    _uiState.update { it.copy(isRefreshingModels = false) }
                    val cleanPid = providerId?.trim()?.lowercase()
                    if (cleanPid != null) {
                        repository.providerAccountsFlow.first()
                            .firstOrNull { it.id.equals(cleanPid, ignoreCase = true) }
                            ?.let { cloudSyncRepository.saveProviderAccount(it) }
                    }
                    refreshServerMetadata(profile)
                    if (!silentSuccess) {
                        if (res.data > 0) {
                            _snackbarEvents.tryEmit("${res.data} model dimuat dari endpoint provider")
                        } else {
                            _snackbarEvents.tryEmit("Katalog model diperbarui")
                        }
                    }
                }
                is ApiOutcome.Failure -> {
                    _uiState.update { it.copy(isRefreshingModels = false) }
                    if (!silentSuccess) {
                        _snackbarEvents.tryEmit("${res.error.title}: ${res.error.detail}")
                    }
                }
            }
        }
    }

    fun createLocalWorkspaceFile(path: String, content: String) {
        viewModelScope.launch {
            when (val res = repository.createLocalFile(path, content)) {
                is ApiOutcome.Success -> {
                    loadDirectory(_uiState.value.currentDirPath)
                    _snackbarEvents.tryEmit("File '$path' dibuat")
                }
                is ApiOutcome.Failure -> _snackbarEvents.tryEmit(res.error.detail)
            }
        }
    }

    fun importFileToLocalWorkspace(uri: Uri) {
        viewModelScope.launch {
            when (val res = repository.importLocalFile(uri)) {
                is ApiOutcome.Success -> {
                    loadDirectory(_uiState.value.currentDirPath)
                    _snackbarEvents.tryEmit("File '${res.data}' diimpor ke workspace")
                }
                is ApiOutcome.Failure -> _snackbarEvents.tryEmit(res.error.detail)
            }
        }
    }
}
