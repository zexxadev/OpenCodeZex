package com.example.data.repository

import android.net.Uri
import com.example.data.local.LocalOpenCodeRuntime
import com.example.data.model.AgentInfo
import com.example.data.model.AuthCredentialRequest
import com.example.data.model.ChatMessage
import com.example.data.model.CommandInfo
import com.example.data.model.CreateSessionRequest
import com.example.data.model.FileContentResponse
import com.example.data.model.FileDiff
import com.example.data.model.FileNode
import com.example.data.model.ForkSessionRequest
import com.example.data.model.GrepMatch
import com.example.data.model.HealthResponse
import com.example.data.model.LocalProviderConfig
import com.example.data.model.ModelOption
import com.example.data.model.ModelReference
import com.example.data.model.OpenCodeEvent
import com.example.data.model.OpenCodeJsonParser
import com.example.data.model.OpenCodeSession
import com.example.data.model.PermissionReplyRequest
import com.example.data.model.PromptPartInput
import com.example.data.model.ProviderApiFormat
import com.example.data.model.ProviderCatalog
import com.example.data.model.ProviderCatalogDefaults
import com.example.data.model.SavedProviderAccount
import com.example.data.model.SendCommandRequest
import com.example.data.model.SendPromptRequest
import com.example.data.model.SendShellRequest
import com.example.data.model.ServerProfile
import com.example.data.model.SessionStatusInfo
import com.example.data.model.UpdateSessionRequest
import com.example.data.network.AiRetryPolicy
import com.example.data.network.ConnectionDiagnostics
import com.example.data.network.DiagnosticError
import com.example.data.network.OpenCodeClientManager
import com.example.data.network.OpenCodeSseClient
import com.example.data.preferences.ServerProfileStore
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.JsonObject
import retrofit2.Response

sealed class ApiOutcome<out T> {
    data class Success<T>(val data: T) : ApiOutcome<T>()
    data class Failure(val error: DiagnosticError) : ApiOutcome<Nothing>()
}

data class HealthCheckResult(
    val health: HealthResponse,
    val latencyMs: Long
)

class OpenCodeRepository(
    private val clientManager: OpenCodeClientManager,
    private val sseClient: OpenCodeSseClient,
    val profileStore: ServerProfileStore,
    val providerConfigRepository: ProviderConfigRepository,
    val localRuntime: LocalOpenCodeRuntime
) {
    val profilesFlow: Flow<List<ServerProfile>> = profileStore.profilesFlow
    val activeProfileIdFlow: Flow<String?> = profileStore.activeProfileIdFlow
    val themePreferenceFlow: Flow<ThemePreference> = profileStore.themePreferenceFlow
    val selectedAgentFlow: Flow<String> = profileStore.selectedAgentFlow
    val selectedModelFlow: Flow<Pair<String?, String?>> = providerConfigRepository.selectedModelFlow
    val hasCompletedSetupFlow: Flow<Boolean?> = profileStore.hasCompletedSetupFlow
    val localProviderConfigFlow: Flow<LocalProviderConfig> = providerConfigRepository.providerConfigFlow
    val providerAccountsFlow: Flow<List<SavedProviderAccount>> = providerConfigRepository.providerAccountsFlow

    fun observeEvents(profile: ServerProfile): Flow<OpenCodeEvent> {
        return if (profile.isLocalAndroid) {
            localRuntime.observeEvents().onStart { emit(OpenCodeEvent.Connected) }
        } else {
            val sseApi = clientManager.getSseApi(profile)
            sseClient.observeEvents(sseApi, profile)
        }
    }

    fun initializeLocalEnvironment(): String = localRuntime.initializeEnvironment()

    suspend fun checkHealth(profile: ServerProfile): ApiOutcome<HealthCheckResult> {
        if (profile.isLocalAndroid) {
            localRuntime.initializeEnvironment()
            return ApiOutcome.Success(
                HealthCheckResult(
                    health = HealthResponse(healthy = true, version = "android-local-1.0"),
                    latencyMs = 1L
                )
            )
        }
        val start = System.currentTimeMillis()
        return executeCall(profile) {
            val response = clientManager.getApi(profile).getHealth()
            val latency = System.currentTimeMillis() - start
            if (response.isSuccessful && response.body() != null) {
                ApiOutcome.Success(HealthCheckResult(response.body()!!, latency))
            } else {
                ApiOutcome.Failure(
                    ConnectionDiagnostics.classifyHttpCode(response.code(), response.message())
                )
            }
        }
    }

    suspend fun listSessions(profile: ServerProfile): ApiOutcome<List<OpenCodeSession>> {
        if (profile.isLocalAndroid) {
            return ApiOutcome.Success(localRuntime.listSessions())
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).getSessions()
        }.map { sessions ->
            sessions.sortedByDescending { it.time?.updated ?: it.time?.created ?: 0L }
        }
    }

    suspend fun getSessionsStatus(profile: ServerProfile): ApiOutcome<Map<String, SessionStatusInfo>> {
        if (profile.isLocalAndroid) {
            return ApiOutcome.Success(localRuntime.getStatuses())
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).getSessionsStatus()
        }.map { obj ->
            obj.entries.associate { (sessionId, value) ->
                sessionId to OpenCodeJsonParser.parseSessionStatus(value)
            }
        }
    }

    suspend fun createSession(profile: ServerProfile, title: String?): ApiOutcome<OpenCodeSession> {
        if (profile.isLocalAndroid) {
            return ApiOutcome.Success(localRuntime.createSession(title))
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).createSession(
                CreateSessionRequest(title = title?.takeIf { it.isNotBlank() })
            )
        }
    }

    suspend fun renameSession(
        profile: ServerProfile,
        sessionId: String,
        newTitle: String
    ): ApiOutcome<OpenCodeSession> {
        if (profile.isLocalAndroid) {
            return runLocalCatching { localRuntime.renameSession(sessionId, newTitle) }
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).updateSession(
                id = sessionId,
                request = UpdateSessionRequest(title = newTitle)
            )
        }
    }

    suspend fun forkSession(
        profile: ServerProfile,
        sessionId: String,
        messageId: String? = null
    ): ApiOutcome<OpenCodeSession> {
        if (profile.isLocalAndroid) {
            return runLocalCatching { localRuntime.forkSession(sessionId) }
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).forkSession(
                id = sessionId,
                request = ForkSessionRequest(messageID = messageId)
            )
        }
    }

    suspend fun deleteSession(profile: ServerProfile, sessionId: String): ApiOutcome<Unit> {
        if (profile.isLocalAndroid) {
            localRuntime.deleteSession(sessionId)
            return ApiOutcome.Success(Unit)
        }
        return executeUnitResponse(profile) {
            clientManager.getApi(profile).deleteSession(sessionId)
        }
    }

    suspend fun abortSession(profile: ServerProfile, sessionId: String): ApiOutcome<Unit> {
        if (profile.isLocalAndroid) {
            localRuntime.abortSession(sessionId)
            return ApiOutcome.Success(Unit)
        }
        return executeUnitResponse(profile) {
            clientManager.getApi(profile).abortSession(sessionId)
        }
    }

    suspend fun getMessages(profile: ServerProfile, sessionId: String): ApiOutcome<List<ChatMessage>> {
        if (profile.isLocalAndroid) {
            return ApiOutcome.Success(localRuntime.getMessages(sessionId))
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).getMessages(sessionId)
        }.map { arr ->
            arr.mapNotNull { el ->
                (el as? JsonObject)?.let { OpenCodeJsonParser.parseMessageWithParts(it) }
            }.sortedBy { it.createdAt }
        }
    }

    suspend fun getSessionDiff(profile: ServerProfile, sessionId: String): ApiOutcome<List<FileDiff>> {
        if (profile.isLocalAndroid) {
            return ApiOutcome.Success(localRuntime.getSessionDiff(sessionId))
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).getSessionDiff(sessionId)
        }
    }

    suspend fun sendPromptAsync(
        profile: ServerProfile,
        sessionId: String,
        text: String,
        agent: String?,
        model: ModelReference?
    ): ApiOutcome<Unit> {
        if (profile.isLocalAndroid) {
            val effectiveConfig = providerConfigRepository.getEffectiveConfigFor(
                providerId = model?.providerID,
                modelId = model?.modelID
            )
            val isCustomEndpoint = effectiveConfig.customBaseUrl.isNotBlank() &&
                ProviderCatalogDefaults.builtInSpec(effectiveConfig.providerId) == null
            if (effectiveConfig.apiKey.isBlank() && !isCustomEndpoint) {
                return ApiOutcome.Failure(
                    DiagnosticError(
                        title = "API Key '${effectiveConfig.providerId}' Belum Diatur",
                        detail = "Runtime Lokal Android memerlukan API Key untuk provider ${effectiveConfig.providerId} (OpenCode / Gemini / OpenAI / Anthropic / OpenRouter / dll).",
                        hint = "Buka tab Server → Provider AI & API Key untuk mengisi API Key Anda."
                    )
                )
            }
            localRuntime.sendPrompt(
                sessionId = sessionId,
                text = text,
                agent = agent ?: "build",
                providerConfig = effectiveConfig,
                modelRef = model
            )
            return ApiOutcome.Success(Unit)
        }
        val req = SendPromptRequest(
            agent = agent?.takeIf { it.isNotBlank() },
            model = model,
            parts = listOf(PromptPartInput(type = "text", text = text))
        )
        return executeCall(profile) {
            AiRetryPolicy.executeWithTimeoutAndRetry(
                timeoutMs = AiRetryPolicy.ENDPOINT_TIMEOUT_MS,
                maxRetries = AiRetryPolicy.MAX_RETRIES
            ) {
                val api = clientManager.getApi(profile)
                val asyncResp = api.sendPromptAsync(sessionId, req)
                if (asyncResp.isSuccessful) {
                    ApiOutcome.Success(Unit)
                } else if (asyncResp.code() == 404) {
                    val syncResp = api.sendMessageSync(sessionId, req)
                    if (syncResp.isSuccessful) ApiOutcome.Success(Unit)
                    else if (syncResp.code() >= 500 || syncResp.code() == 429 || syncResp.code() == 408) {
                        throw IllegalStateException("HTTP ${syncResp.code()}: ${syncResp.message()}")
                    } else {
                        ApiOutcome.Failure(ConnectionDiagnostics.classifyHttpCode(syncResp.code(), syncResp.message()))
                    }
                } else if (asyncResp.code() >= 500 || asyncResp.code() == 429 || asyncResp.code() == 408) {
                    throw IllegalStateException("HTTP ${asyncResp.code()}: ${asyncResp.message()}")
                } else {
                    ApiOutcome.Failure(ConnectionDiagnostics.classifyHttpCode(asyncResp.code(), asyncResp.message()))
                }
            }
        }
    }

    suspend fun retryPrompt(
        profile: ServerProfile,
        sessionId: String,
        failedAssistantMessageId: String?,
        promptText: String,
        agent: String?,
        model: ModelReference?
    ): ApiOutcome<Unit> {
        if (profile.isLocalAndroid) {
            val effectiveConfig = providerConfigRepository.getEffectiveConfigFor(
                providerId = model?.providerID,
                modelId = model?.modelID
            )
            val isCustomEndpoint = effectiveConfig.customBaseUrl.isNotBlank() &&
                ProviderCatalogDefaults.builtInSpec(effectiveConfig.providerId) == null
            if (effectiveConfig.apiKey.isBlank() && !isCustomEndpoint) {
                return ApiOutcome.Failure(
                    DiagnosticError(
                        title = "API Key '${effectiveConfig.providerId}' Belum Diatur",
                        detail = "Runtime Lokal Android memerlukan API Key untuk provider ${effectiveConfig.providerId}.",
                        hint = "Buka tab Server → Provider AI & API Key untuk mengisi API Key Anda."
                    )
                )
            }
            localRuntime.retryPrompt(
                sessionId = sessionId,
                failedAssistantMessageId = failedAssistantMessageId,
                fallbackPromptText = promptText,
                agent = agent ?: "build",
                providerConfig = effectiveConfig,
                modelRef = model
            )
            return ApiOutcome.Success(Unit)
        }
        return sendPromptAsync(profile, sessionId, promptText, agent, model)
    }

    suspend fun sendCommand(
        profile: ServerProfile,
        sessionId: String,
        command: String,
        arguments: String,
        agent: String?
    ): ApiOutcome<Unit> {
        if (profile.isLocalAndroid) {
            val localConfig = providerConfigRepository.getEffectiveConfigFor()
            localRuntime.sendCommand(
                sessionId = sessionId,
                command = command.removePrefix("/"),
                arguments = arguments,
                agent = agent ?: "build",
                providerConfig = localConfig,
                modelRef = null
            )
            return ApiOutcome.Success(Unit)
        }
        return executeCall(profile) {
            AiRetryPolicy.executeWithTimeoutAndRetry(
                timeoutMs = AiRetryPolicy.ENDPOINT_TIMEOUT_MS,
                maxRetries = AiRetryPolicy.MAX_RETRIES
            ) {
                val resp = clientManager.getApi(profile).sendCommand(
                    id = sessionId,
                    request = SendCommandRequest(
                        command = command.removePrefix("/"),
                        arguments = arguments,
                        agent = agent
                    )
                )
                if (resp.isSuccessful) {
                    ApiOutcome.Success(Unit)
                } else if (resp.code() >= 500 || resp.code() == 429 || resp.code() == 408) {
                    throw IllegalStateException("HTTP ${resp.code()}: ${resp.message()}")
                } else {
                    ApiOutcome.Failure(ConnectionDiagnostics.classifyHttpCode(resp.code(), resp.message()))
                }
            }
        }
    }

    suspend fun sendShell(
        profile: ServerProfile,
        sessionId: String,
        command: String,
        agent: String,
        model: ModelReference?
    ): ApiOutcome<Unit> {
        if (profile.isLocalAndroid) {
            localRuntime.sendShell(sessionId, command)
            return ApiOutcome.Success(Unit)
        }
        return executeUnitResponse(profile) {
            clientManager.getApi(profile).sendShell(
                id = sessionId,
                request = SendShellRequest(
                    agent = agent.ifBlank { "build" },
                    command = command,
                    model = model
                )
            )
        }
    }

    suspend fun replyPermission(
        profile: ServerProfile,
        sessionId: String,
        permissionId: String,
        decision: String
    ): ApiOutcome<Unit> {
        if (profile.isLocalAndroid) {
            localRuntime.replyPermission(permissionId, decision)
            return ApiOutcome.Success(Unit)
        }
        return executeUnitResponse(profile) {
            clientManager.getApi(profile).replyPermission(
                sessionId = sessionId,
                permissionId = permissionId,
                request = PermissionReplyRequest(response = decision)
            )
        }
    }

    suspend fun getProvidersAndModels(profile: ServerProfile): ApiOutcome<Pair<ProviderCatalog, List<ModelOption>>> {
        if (profile.isLocalAndroid) {
            val config = providerConfigRepository.getProviderConfig()
            val accounts = providerConfigRepository.providerAccountsFlow.first()
            return ApiOutcome.Success(localRuntime.getProvidersAndModels(config, accounts))
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).getProviders()
        }.map { catalog ->
            val connectedSet = catalog.connected.toSet()
            val rawOptions = catalog.all.flatMap { provider ->
                val isConn = provider.id in connectedSet
                provider.models.entries.map { (key, model) ->
                    val actualModelId = model.id.ifBlank { key }
                    ModelOption(
                        providerId = provider.id,
                        providerName = provider.displayName,
                        modelId = actualModelId,
                        modelName = model.displayName.ifBlank { actualModelId },
                        isConnected = isConn,
                        supportsReasoning = model.reasoning
                    )
                }
            }
            val options = ProviderCatalogDefaults.sortModelsReadyFirst(rawOptions)
            catalog to options
        }
    }

    suspend fun setProviderApiKey(
        profile: ServerProfile,
        providerId: String,
        apiKey: String,
        modelId: String? = null,
        customBaseUrl: String? = null,
        apiFormat: ProviderApiFormat? = null
    ): ApiOutcome<Unit> {
        val cleanId = providerId.trim().lowercase()
        val cleanKey = apiKey.trim()
        providerConfigRepository.saveApiKey(
            providerId = cleanId,
            apiKey = cleanKey,
            modelId = modelId,
            customBaseUrl = customBaseUrl,
            apiFormat = apiFormat,
            activate = profile.isLocalAndroid
        )

        if (profile.isLocalAndroid) {
            return ApiOutcome.Success(Unit)
        }
        return executeUnitResponse(profile) {
            clientManager.getApi(profile).setProviderAuth(
                providerId = cleanId,
                request = AuthCredentialRequest(type = "api", key = cleanKey)
            )
        }
    }

    suspend fun saveProviderAccount(
        account: SavedProviderAccount,
        activate: Boolean,
        profile: ServerProfile
    ): ApiOutcome<Unit> {
        providerConfigRepository.saveProviderAccount(account, activate)
        val cleanId = account.id.trim().lowercase()
        val cleanKey = account.apiKey.trim()

        if (!profile.isLocalAndroid && cleanKey.isNotBlank() && cleanId.isNotBlank()) {
            // Best-effort sync to connected remote OpenCode server if reachable
            runCatching {
                clientManager.getApi(profile).setProviderAuth(
                    providerId = cleanId,
                    request = AuthCredentialRequest(type = "api", key = cleanKey)
                )
            }
        }
        return ApiOutcome.Success(Unit)
    }

    suspend fun deleteCustomProviderAccount(providerId: String): ApiOutcome<Unit> {
        providerConfigRepository.deleteCustomProviderAccount(providerId)
        return ApiOutcome.Success(Unit)
    }

    suspend fun refreshProviderModels(targetProviderId: String? = null): ApiOutcome<Int> {
        val accounts = providerConfigRepository.providerAccountsFlow.first().toMutableList()
        var totalFetched = 0
        var lastError: Throwable? = null

        for (idx in accounts.indices) {
            val acc = accounts[idx]
            if (targetProviderId != null && !acc.id.equals(targetProviderId, ignoreCase = true)) continue
            // OpenCode Zen (/zen/v1/models) and custom local endpoints can respond without requiring key first;
            // cloud providers with keys configured also refresh cleanly.
            val canQuery = targetProviderId != null ||
                acc.apiKey.isNotBlank() ||
                acc.id == "opencode" ||
                (acc.isCustom && acc.baseUrl.isNotBlank())
            if (!canQuery) continue

            val res = localRuntime.fetchAvailableModels(acc)
            res.onSuccess { fetched ->
                if (fetched.isNotEmpty()) {
                    totalFetched += fetched.size
                    val mergedModels = (fetched + acc.cachedModels + ProviderCatalogDefaults.builtInSpec(acc.id)?.models.orEmpty())
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    accounts[idx] = acc.copy(
                        cachedModels = mergedModels,
                        defaultModelId = acc.defaultModelId.ifBlank { mergedModels.firstOrNull().orEmpty() }
                    )
                }
            }.onFailure { err ->
                lastError = err
            }
        }

        providerConfigRepository.saveProviderAccounts(accounts)
        return if (totalFetched == 0 && targetProviderId != null && lastError != null) {
            ApiOutcome.Failure(
                DiagnosticError(
                    title = "Gagal Memuat Daftar Model",
                    detail = lastError?.localizedMessage ?: "Endpoint /models tidak merespons.",
                    hint = "Pastikan URL endpoint dan API Key sudah benar."
                )
            )
        } else {
            ApiOutcome.Success(totalFetched)
        }
    }

    suspend fun getAgents(profile: ServerProfile): ApiOutcome<List<AgentInfo>> {
        if (profile.isLocalAndroid) {
            return ApiOutcome.Success(localRuntime.getAgents())
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).getAgents()
        }
    }

    suspend fun getCommands(profile: ServerProfile): ApiOutcome<List<CommandInfo>> {
        if (profile.isLocalAndroid) {
            return ApiOutcome.Success(localRuntime.getCommands())
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).getCommands()
        }
    }

    suspend fun listFiles(profile: ServerProfile, path: String = ""): ApiOutcome<List<FileNode>> {
        if (profile.isLocalAndroid) {
            return runLocalCatching { localRuntime.workspace.listFiles(path) }
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).listFiles(path)
        }.map { nodes ->
            nodes.sortedWith(
                compareByDescending<FileNode> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    suspend fun readFileContent(profile: ServerProfile, path: String): ApiOutcome<FileContentResponse> {
        if (profile.isLocalAndroid) {
            return runLocalCatching { localRuntime.workspace.readFile(path) }
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).readFileContent(path)
        }
    }

    suspend fun findFiles(profile: ServerProfile, query: String): ApiOutcome<List<String>> {
        if (profile.isLocalAndroid) {
            return runLocalCatching { localRuntime.workspace.findFiles(query) }
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).findFiles(query)
        }
    }

    suspend fun findText(profile: ServerProfile, pattern: String): ApiOutcome<List<GrepMatch>> {
        if (profile.isLocalAndroid) {
            return runLocalCatching { localRuntime.workspace.grepText(pattern) }
        }
        return executeResponse(profile) {
            clientManager.getApi(profile).findText(pattern)
        }.map { arr ->
            OpenCodeJsonParser.parseGrepMatches(arr)
        }
    }

    suspend fun createLocalFile(path: String, content: String): ApiOutcome<Unit> {
        return runLocalCatching {
            localRuntime.workspace.writeFile(path, content)
            Unit
        }
    }

    suspend fun importLocalFile(uri: Uri): ApiOutcome<String> {
        return runLocalCatching {
            localRuntime.workspace.importFromUri(uri)
        }
    }

    private inline fun <T> runLocalCatching(block: () -> T): ApiOutcome<T> {
        return try {
            ApiOutcome.Success(block())
        } catch (t: Throwable) {
            ApiOutcome.Failure(
                DiagnosticError(
                    title = "Operasi Lokal Gagal",
                    detail = t.localizedMessage ?: "Terjadi kesalahan pada runtime lokal.",
                    hint = "Periksa path file atau izin akses."
                )
            )
        }
    }

    private suspend inline fun <T> executeResponse(
        profile: ServerProfile,
        crossinline block: suspend () -> Response<T>
    ): ApiOutcome<T> {
        return executeCall(profile) {
            val response = block()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiOutcome.Success(body)
            } else {
                ApiOutcome.Failure(
                    ConnectionDiagnostics.classifyHttpCode(response.code(), response.message())
                )
            }
        }
    }

    private suspend inline fun executeUnitResponse(
        profile: ServerProfile,
        crossinline block: suspend () -> Response<*>
    ): ApiOutcome<Unit> {
        return executeCall(profile) {
            val response = block()
            if (response.isSuccessful) {
                ApiOutcome.Success(Unit)
            } else {
                ApiOutcome.Failure(
                    ConnectionDiagnostics.classifyHttpCode(response.code(), response.message())
                )
            }
        }
    }

    private suspend inline fun <T> executeCall(
        profile: ServerProfile,
        crossinline block: suspend () -> ApiOutcome<T>
    ): ApiOutcome<T> {
        if (profile.normalizedUrl.isBlank()) {
            return ApiOutcome.Failure(
                DiagnosticError(
                    title = "URL Server Kosong",
                    detail = "Profil server belum memiliki alamat URL.",
                    hint = "Tambahkan URL server OpenCode (contoh: http://10.0.2.2:4096)."
                )
            )
        }
        return try {
            block()
        } catch (t: Throwable) {
            ApiOutcome.Failure(ConnectionDiagnostics.classifyException(t, profile.normalizedUrl))
        }
    }

    private inline fun <T, R> ApiOutcome<T>.map(transform: (T) -> R): ApiOutcome<R> {
        return when (this) {
            is ApiOutcome.Success -> ApiOutcome.Success(transform(data))
            is ApiOutcome.Failure -> this
        }
    }
}
