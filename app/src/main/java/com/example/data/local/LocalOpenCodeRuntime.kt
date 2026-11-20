package com.example.data.local

import com.example.data.model.AgentInfo
import com.example.data.model.ChatMessage
import com.example.data.model.CommandInfo
import com.example.data.model.DiffComputation
import com.example.data.model.DiffLineType
import com.example.data.model.FileDiff
import com.example.data.model.LocalProviderConfig
import com.example.data.model.MessagePart
import com.example.data.model.ModelOption
import com.example.data.model.ModelReference
import com.example.data.model.OpenCodeEvent
import com.example.data.model.OpenCodeSession
import com.example.data.model.PermissionRequestItem
import com.example.data.model.ProviderCatalog
import com.example.data.model.ProviderCatalogDefaults
import com.example.data.model.ProviderItem
import com.example.data.model.ProviderModelItem
import com.example.data.model.SavedProviderAccount
import com.example.data.model.SessionRunStatus
import com.example.data.model.SessionStatusInfo
import com.example.data.model.SessionSummary
import com.example.data.model.TimeRange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class StoredPart(
    val id: String,
    val messageId: String,
    val type: String,
    val text: String = "",
    val callId: String = "",
    val toolName: String = "",
    val status: String = "",
    val title: String? = null,
    val inputSummary: String = "",
    val output: String? = null,
    val error: String? = null,
    val hash: String = "",
    val files: List<String> = emptyList()
)

@Serializable
private data class StoredMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val createdAt: Long,
    val completedAt: Long? = null,
    val agent: String? = null,
    val modelId: String? = null,
    val providerId: String? = null,
    val errorText: String? = null,
    val parts: List<StoredPart> = emptyList()
)

@Serializable
private data class StoredSessionBundle(
    val messages: List<StoredMessage> = emptyList(),
    val diffs: List<FileDiff> = emptyList()
)

class LocalOpenCodeRuntime(
    val workspace: LocalWorkspaceManager,
    private val llmClient: LocalLlmClient,
    private val json: Json
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventsFlow = MutableSharedFlow<OpenCodeEvent>(extraBufferCapacity = 512)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val sessionStatuses = ConcurrentHashMap<String, SessionStatusInfo>()
    private val permissionWaiters = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private var alwaysAllowShell = false

    fun observeEvents(): Flow<OpenCodeEvent> = eventsFlow.asSharedFlow()

    fun initializeEnvironment(): String = workspace.ensureEnvironmentInitialized()

    fun listSessions(): List<OpenCodeSession> {
        workspace.ensureEnvironmentInitialized()
        return loadSessionsList().sortedByDescending { it.time?.updated ?: it.time?.created ?: 0L }
    }

    fun getStatuses(): Map<String, SessionStatusInfo> = sessionStatuses.toMap()

    fun createSession(title: String?): OpenCodeSession {
        workspace.ensureEnvironmentInitialized()
        val now = System.currentTimeMillis()
        val session = OpenCodeSession(
            id = "local_${UUID.randomUUID().toString().take(8)}",
            title = title?.trim().orEmpty(),
            directory = workspace.workspaceDir.absolutePath,
            time = TimeRange(created = now, updated = now),
            summary = SessionSummary()
        )
        val all = loadSessionsList().toMutableList()
        all.add(0, session)
        saveSessionsList(all)
        saveBundle(session.id, StoredSessionBundle())
        eventsFlow.tryEmit(OpenCodeEvent.SessionUpserted(session))
        return session
    }

    fun renameSession(sessionId: String, newTitle: String): OpenCodeSession {
        val all = loadSessionsList().toMutableList()
        val idx = all.indexOfFirst { it.id == sessionId }
        if (idx < 0) throw IllegalArgumentException("Sesi tidak ditemukan")
        val updated = all[idx].copy(
            title = newTitle,
            time = (all[idx].time ?: TimeRange()).copy(updated = System.currentTimeMillis())
        )
        all[idx] = updated
        saveSessionsList(all)
        eventsFlow.tryEmit(OpenCodeEvent.SessionUpserted(updated))
        return updated
    }

    fun forkSession(sessionId: String): OpenCodeSession {
        val origin = loadSessionsList().firstOrNull { it.id == sessionId }
            ?: throw IllegalArgumentException("Sesi asal tidak ditemukan")
        val bundle = loadBundle(sessionId)
        val now = System.currentTimeMillis()
        val forkedId = "local_${UUID.randomUUID().toString().take(8)}"
        val forked = origin.copy(
            id = forkedId,
            title = "${origin.displayTitle} (Fork)",
            parentID = sessionId,
            time = TimeRange(created = now, updated = now)
        )
        val clonedMsgs = bundle.messages.map { it.copy(sessionId = forkedId) }
        saveBundle(forkedId, bundle.copy(messages = clonedMsgs))
        val all = loadSessionsList().toMutableList()
        all.add(0, forked)
        saveSessionsList(all)
        eventsFlow.tryEmit(OpenCodeEvent.SessionUpserted(forked))
        return forked
    }

    fun deleteSession(sessionId: String) {
        abortSession(sessionId)
        val remaining = loadSessionsList().filterNot { it.id == sessionId }
        saveSessionsList(remaining)
        bundleFile(sessionId).delete()
        eventsFlow.tryEmit(OpenCodeEvent.SessionRemoved(sessionId))
    }

    fun getMessages(sessionId: String): List<ChatMessage> {
        return loadBundle(sessionId).messages.map { it.toDomain() }
    }

    fun getSessionDiff(sessionId: String): List<FileDiff> {
        return loadBundle(sessionId).diffs
    }

    fun getProvidersAndModels(
        config: LocalProviderConfig,
        accounts: List<SavedProviderAccount> = ProviderCatalogDefaults.defaultAccounts()
    ): Pair<ProviderCatalog, List<ModelOption>> {
        val providerItems = accounts.map { account ->
            val modelsList = ProviderCatalogDefaults.modelsForAccount(account)
            val modelMap = modelsList.associateWith { mId ->
                ProviderModelItem(
                    id = mId,
                    name = mId,
                    reasoning = true
                )
            }
            ProviderItem(
                id = account.id,
                name = account.name,
                models = modelMap
            )
        }

        val connected = accounts.filter { account ->
            account.apiKey.isNotBlank() ||
                (account.isCustom && account.baseUrl.isNotBlank()) ||
                (account.id == config.providerId && config.apiKey.isNotBlank())
        }.map { it.id }.distinct()

        val catalog = ProviderCatalog(all = providerItems, connected = connected)

        val rawOptions = accounts.flatMap { account ->
            val isConn = account.id in connected
            val modelsList = ProviderCatalogDefaults.modelsForAccount(account).let { list ->
                if (account.id == config.providerId && config.modelId.isNotBlank() && config.modelId !in list) {
                    listOf(config.modelId) + list
                } else {
                    list
                }
            }
            modelsList.map { modId ->
                ModelOption(
                    providerId = account.id,
                    providerName = account.name,
                    modelId = modId,
                    modelName = modId,
                    isConnected = isConn,
                    supportsReasoning = true
                )
            }
        }
        val options = ProviderCatalogDefaults.sortModelsReadyFirst(
            models = rawOptions,
            activeProviderId = config.providerId,
            activeModelId = config.modelId
        )

        return catalog to options
    }

    suspend fun fetchAvailableModels(account: SavedProviderAccount): Result<List<String>> =
        llmClient.fetchAvailableModels(account)

    fun getAgents(): List<AgentInfo> = listOf(
        AgentInfo("build", "Agen coding penuh (baca, tulis, edit file, dan shell lokal)", "primary", true),
        AgentInfo("plan", "Agen perencanaan read-only (hanya baca & cari kode)", "primary", true)
    )

    fun getCommands(): List<CommandInfo> = listOf(
        CommandInfo("init", "Pindai file workspace & perbarui ringkasan AGENTS.md"),
        CommandInfo("review", "Ringkas perubahan diff pada sesi ini"),
        CommandInfo("clear", "Mulai konteks percakapan bersih")
    )

    fun abortSession(sessionId: String) {
        activeJobs.remove(sessionId)?.cancel()
        setStatus(sessionId, SessionRunStatus.IDLE)
    }

    fun replyPermission(permissionId: String, decision: String) {
        if (decision == "always") {
            alwaysAllowShell = true
        }
        permissionWaiters.remove(permissionId)?.complete(decision)
    }

    fun sendShell(sessionId: String, command: String) {
        activeJobs[sessionId]?.cancel()
        activeJobs[sessionId] = scope.launch {
            val now = System.currentTimeMillis()
            val userMsg = ChatMessage(
                id = "msg_${UUID.randomUUID().toString().take(8)}",
                sessionId = sessionId,
                role = "user",
                createdAt = now,
                completedAt = now,
                agent = "shell",
                modelId = null,
                providerId = null,
                errorText = null,
                parts = listOf(MessagePart.Text("p_${now}_u", "", "$ $command"))
            )
            appendMessage(sessionId, userMsg)
            setStatus(sessionId, SessionRunStatus.BUSY)

            val assistantMsgId = "msg_${UUID.randomUUID().toString().take(8)}"
            val toolPartId = "p_${UUID.randomUUID().toString().take(8)}"
            val runningTool = MessagePart.ToolCall(
                id = toolPartId,
                messageId = assistantMsgId,
                callId = toolPartId,
                toolName = "bash",
                status = "running",
                title = "Shell Lokal Android",
                inputSummary = command,
                output = null,
                error = null
            )
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                sessionId = sessionId,
                role = "assistant",
                createdAt = now + 1,
                completedAt = null,
                agent = "shell",
                modelId = "android-sh",
                providerId = "local",
                errorText = null,
                parts = listOf(runningTool)
            )
            appendMessage(sessionId, assistantMsg)

            val result = runCatching { workspace.runLocalShell(command) }
            val completedTool = result.fold(
                onSuccess = { res ->
                    runningTool.copy(
                        status = if (res.exitCode == 0) "completed" else "error",
                        output = if (res.exitCode == 0) res.output else null,
                        error = if (res.exitCode != 0) res.output else null
                    )
                },
                onFailure = { err ->
                    runningTool.copy(status = "error", error = err.localizedMessage)
                }
            )
            updateMessage(
                sessionId,
                assistantMsg.copy(
                    completedAt = System.currentTimeMillis(),
                    parts = listOf(completedTool)
                )
            )
            setStatus(sessionId, SessionRunStatus.IDLE)
        }
    }

    fun sendCommand(
        sessionId: String,
        command: String,
        arguments: String,
        agent: String,
        providerConfig: LocalProviderConfig,
        modelRef: ModelReference?
    ) {
        when (command.lowercase()) {
            "init" -> {
                scope.launch {
                    setStatus(sessionId, SessionRunStatus.BUSY)
                    val files = workspace.findFiles("")
                    val summary = buildString {
                        appendLine("# OpenCode Local Android Workspace")
                        appendLine()
                        appendLine("Diperbarui otomatis oleh `/init`. Daftar file workspace saat ini:")
                        files.take(40).forEach { appendLine("- `$it`") }
                    }
                    workspace.writeFile("AGENTS.md", summary)
                    val msgId = "msg_${UUID.randomUUID().toString().take(8)}"
                    val msg = ChatMessage(
                        id = msgId,
                        sessionId = sessionId,
                        role = "assistant",
                        createdAt = System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis(),
                        agent = agent,
                        modelId = "local-runtime",
                        providerId = "local",
                        errorText = null,
                        parts = listOf(
                            MessagePart.Text(
                                id = "p_${UUID.randomUUID().toString().take(8)}",
                                messageId = msgId,
                                text = "Workspace dipindai (${files.size} file ditemukan) dan `AGENTS.md` telah diperbarui."
                            )
                        )
                    )
                    appendMessage(sessionId, msg)
                    setStatus(sessionId, SessionRunStatus.IDLE)
                }
            }
            "review" -> {
                val diffs = getSessionDiff(sessionId)
                val promptText = if (diffs.isEmpty()) {
                    "Periksa struktur workspace saat ini dan berikan saran peningkatan."
                } else {
                    "Review perubahan file berikut pada sesi ini:\n" + diffs.joinToString("\n") {
                        "- ${it.file} (+${it.additions} / -${it.deletions})"
                    }
                }
                sendPrompt(sessionId, promptText, agent, providerConfig, modelRef)
            }
            else -> {
                sendPrompt(sessionId, "/$command $arguments".trim(), agent, providerConfig, modelRef)
            }
        }
    }

    fun sendPrompt(
        sessionId: String,
        text: String,
        agent: String,
        providerConfig: LocalProviderConfig,
        modelRef: ModelReference?
    ) {
        activeJobs[sessionId]?.cancel()
        val effectiveConfig = resolveEffectiveConfig(providerConfig, modelRef)

        activeJobs[sessionId] = scope.launch {
            val now = System.currentTimeMillis()
            val userMsgId = "msg_${UUID.randomUUID().toString().take(8)}"
            val userMsg = ChatMessage(
                id = userMsgId,
                sessionId = sessionId,
                role = "user",
                createdAt = now,
                completedAt = now,
                agent = agent,
                modelId = effectiveConfig.modelId,
                providerId = effectiveConfig.providerId,
                errorText = null,
                parts = listOf(MessagePart.Text("p_${now}_u", userMsgId, text))
            )
            appendMessage(sessionId, userMsg)
            autoTitleIfEmpty(sessionId, text)

            val assistantMsgId = "msg_${UUID.randomUUID().toString().take(8)}"
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                sessionId = sessionId,
                role = "assistant",
                createdAt = now + 1,
                completedAt = null,
                agent = agent,
                modelId = effectiveConfig.modelId,
                providerId = effectiveConfig.providerId,
                errorText = null,
                parts = emptyList()
            )
            appendMessage(sessionId, assistantMsg)
            runAssistantTurn(sessionId, agent, effectiveConfig, assistantMsg)
        }
    }

    fun retryPrompt(
        sessionId: String,
        failedAssistantMessageId: String?,
        fallbackPromptText: String,
        agent: String,
        providerConfig: LocalProviderConfig,
        modelRef: ModelReference?
    ) {
        activeJobs[sessionId]?.cancel()
        val effectiveConfig = resolveEffectiveConfig(providerConfig, modelRef)

        activeJobs[sessionId] = scope.launch {
            val existingMessages = getMessages(sessionId)
            val existingFailed = failedAssistantMessageId?.let { id ->
                existingMessages.firstOrNull { it.id == id }
            }
            if (existingMessages.none { it.role == "user" } && fallbackPromptText.isNotBlank()) {
                val now = System.currentTimeMillis()
                val userMsgId = "msg_${UUID.randomUUID().toString().take(8)}"
                appendMessage(
                    sessionId,
                    ChatMessage(
                        id = userMsgId,
                        sessionId = sessionId,
                        role = "user",
                        createdAt = now,
                        completedAt = now,
                        agent = agent,
                        modelId = effectiveConfig.modelId,
                        providerId = effectiveConfig.providerId,
                        errorText = null,
                        parts = listOf(MessagePart.Text("p_${now}_u", userMsgId, fallbackPromptText))
                    )
                )
            }

            val targetAssistant = (existingFailed ?: ChatMessage(
                id = "msg_${UUID.randomUUID().toString().take(8)}",
                sessionId = sessionId,
                role = "assistant",
                createdAt = System.currentTimeMillis(),
                completedAt = null,
                agent = agent,
                modelId = effectiveConfig.modelId,
                providerId = effectiveConfig.providerId,
                errorText = null,
                parts = emptyList()
            )).copy(
                createdAt = existingFailed?.createdAt ?: System.currentTimeMillis(),
                completedAt = null,
                agent = agent,
                modelId = effectiveConfig.modelId,
                providerId = effectiveConfig.providerId,
                errorText = null,
                parts = emptyList()
            )
            updateMessage(sessionId, targetAssistant)
            runAssistantTurn(sessionId, agent, effectiveConfig, targetAssistant)
        }
    }

    private fun resolveEffectiveConfig(
        providerConfig: LocalProviderConfig,
        modelRef: ModelReference?
    ): LocalProviderConfig {
        return if (modelRef != null && modelRef.providerID == providerConfig.providerId) {
            providerConfig.copy(modelId = modelRef.modelID)
        } else {
            providerConfig
        }
    }

    private suspend fun runAssistantTurn(
        sessionId: String,
        agent: String,
        effectiveConfig: LocalProviderConfig,
        initialAssistantMsg: ChatMessage
    ) {
        val assistantMsgId = initialAssistantMsg.id
        var assistantMsg = initialAssistantMsg
        val parts = mutableListOf<MessagePart>()
        setStatus(sessionId, SessionRunStatus.BUSY)

        try {
            val conversation = buildConversationHistory(sessionId, agent, excludeMessageId = assistantMsgId)
            val allowMutating = agent != "plan"
            val touchedFiles = linkedSetOf<String>()

            for (step in 0 until 8) {
                var reasoningPartId: String? = null
                val reasoningAccum = StringBuilder()
                var textPartId: String? = null
                val textAccum = StringBuilder()

                val turn = llmClient.streamTurn(
                    config = effectiveConfig,
                    messages = conversation,
                    allowMutatingTools = allowMutating,
                    onReasoningDelta = { delta ->
                        if (delta.isEmpty()) return@streamTurn
                        if (sessionStatuses[sessionId]?.status == SessionRunStatus.RETRY) {
                            setStatus(sessionId, SessionRunStatus.BUSY)
                        }
                        reasoningAccum.append(delta)
                        val id = reasoningPartId ?: "p_${UUID.randomUUID().toString().take(8)}".also {
                            reasoningPartId = it
                        }
                        val rPart = MessagePart.Reasoning(
                            id = id,
                            messageId = assistantMsgId,
                            text = reasoningAccum.toString()
                        )
                        val idx = parts.indexOfFirst { it.id == id }
                        if (idx >= 0) parts[idx] = rPart else parts.add(rPart)
                        eventsFlow.emit(OpenCodeEvent.PartUpserted(sessionId, rPart))
                    },
                    onTextDelta = { delta ->
                        if (delta.isEmpty()) return@streamTurn
                        if (sessionStatuses[sessionId]?.status == SessionRunStatus.RETRY) {
                            setStatus(sessionId, SessionRunStatus.BUSY)
                        }
                        textAccum.append(delta)
                        val id = textPartId ?: "p_${UUID.randomUUID().toString().take(8)}".also {
                            textPartId = it
                        }
                        val tPart = MessagePart.Text(
                            id = id,
                            messageId = assistantMsgId,
                            text = textAccum.toString()
                        )
                        val idx = parts.indexOfFirst { it.id == id }
                        if (idx >= 0) parts[idx] = tPart else parts.add(tPart)
                        eventsFlow.emit(OpenCodeEvent.PartUpserted(sessionId, tPart))
                    },
                    onRetryAttempt = { retryAttempt, maxRetries, _ ->
                        setStatus(sessionId, SessionRunStatus.RETRY)
                        reasoningPartId?.let { id -> parts.removeAll { it.id == id } }
                        textPartId?.let { id -> parts.removeAll { it.id == id } }
                        reasoningPartId = null
                        textPartId = null
                        reasoningAccum.clear()
                        textAccum.clear()
                        assistantMsg = assistantMsg.copy(parts = parts.toList())
                        updateMessage(sessionId, assistantMsg)
                        eventsFlow.tryEmit(
                            OpenCodeEvent.Other(
                                type = "retry.attempt",
                                detail = "Gagal terhubung ke endpoint, mencoba ulang ($retryAttempt/$maxRetries)…"
                            )
                        )
                    }
                )

                setStatus(sessionId, SessionRunStatus.BUSY)

                if (!turn.reasoning.isNullOrBlank()) {
                    val id = reasoningPartId ?: "p_${UUID.randomUUID().toString().take(8)}".also {
                        reasoningPartId = it
                    }
                    val rPart = MessagePart.Reasoning(
                        id = id,
                        messageId = assistantMsgId,
                        text = turn.reasoning
                    )
                    val idx = parts.indexOfFirst { it.id == id }
                    if (idx >= 0) parts[idx] = rPart else parts.add(rPart)
                }

                if (turn.text.isNotBlank()) {
                    val id = textPartId ?: "p_${UUID.randomUUID().toString().take(8)}".also {
                        textPartId = it
                    }
                    val tPart = MessagePart.Text(
                        id = id,
                        messageId = assistantMsgId,
                        text = turn.text
                    )
                    val idx = parts.indexOfFirst { it.id == id }
                    if (idx >= 0) parts[idx] = tPart else parts.add(tPart)
                }

                if (parts.isNotEmpty()) {
                    assistantMsg = assistantMsg.copy(parts = parts.toList())
                    updateMessage(sessionId, assistantMsg)
                }

                if (turn.toolCalls.isEmpty()) break

                conversation.add(LlmConversationMessage.Assistant(turn.text, turn.toolCalls))

                for (tc in turn.toolCalls) {
                    val toolPartId = "p_${UUID.randomUUID().toString().take(8)}"
                    val summary = summarizeArgs(tc.arguments)
                    var toolPart = MessagePart.ToolCall(
                        id = toolPartId,
                        messageId = assistantMsgId,
                        callId = tc.id,
                        toolName = tc.name,
                        status = "running",
                        title = null,
                        inputSummary = summary,
                        output = null,
                        error = null
                    )
                    parts.add(toolPart)
                    assistantMsg = assistantMsg.copy(parts = parts.toList())
                    updateMessage(sessionId, assistantMsg)

                    val toolOutcome = executeLocalTool(
                        sessionId = sessionId,
                        toolCall = tc,
                        allowMutating = allowMutating,
                        touchedFiles = touchedFiles
                    )

                    toolPart = toolOutcome.fold(
                        onSuccess = { out ->
                            toolPart.copy(status = "completed", output = out)
                        },
                        onFailure = { err ->
                            toolPart.copy(status = "error", error = err.localizedMessage ?: "Tool gagal")
                        }
                    )
                    val idx = parts.indexOfFirst { it.id == toolPartId }
                    if (idx >= 0) parts[idx] = toolPart
                    assistantMsg = assistantMsg.copy(parts = parts.toList())
                    updateMessage(sessionId, assistantMsg)

                    conversation.add(
                        LlmConversationMessage.ToolResult(
                            toolCallId = tc.id,
                            toolName = tc.name,
                            output = toolOutcome.getOrElse { "Error: ${it.localizedMessage}" }
                        )
                    )
                }
            }

            if (touchedFiles.isNotEmpty()) {
                parts.add(
                    MessagePart.Patch(
                        id = "p_${UUID.randomUUID().toString().take(8)}",
                        messageId = assistantMsgId,
                        hash = UUID.randomUUID().toString().take(8),
                        files = touchedFiles.toList()
                    )
                )
            }

            updateMessage(
                sessionId,
                assistantMsg.copy(
                    completedAt = System.currentTimeMillis(),
                    parts = parts.toList()
                )
            )
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            val msgText = t.localizedMessage ?: "Gagal memproses prompt ke endpoint AI"
            updateMessage(
                sessionId,
                assistantMsg.copy(
                    completedAt = System.currentTimeMillis(),
                    errorText = msgText,
                    parts = parts.toList()
                )
            )
            eventsFlow.tryEmit(OpenCodeEvent.SessionError(sessionId, msgText))
        } finally {
            setStatus(sessionId, SessionRunStatus.IDLE)
            activeJobs.remove(sessionId)
        }
    }

    private suspend fun executeLocalTool(
        sessionId: String,
        toolCall: LlmToolCallRequest,
        allowMutating: Boolean,
        touchedFiles: MutableSet<String>
    ): Result<String> = runCatching {
        val args = toolCall.arguments
        when (toolCall.name) {
            "list" -> {
                val path = args.str("path").orEmpty()
                val nodes = workspace.listFiles(path)
                if (nodes.isEmpty()) "(direktori kosong)"
                else nodes.joinToString("\n") { if (it.isDirectory) "[DIR] ${it.path}" else it.path }
            }
            "read" -> {
                val path = args.str("path") ?: throw IllegalArgumentException("Parameter 'path' wajib diisi")
                workspace.readFile(path).content
            }
            "grep" -> {
                val pattern = args.str("pattern") ?: throw IllegalArgumentException("Parameter 'pattern' wajib diisi")
                val matches = workspace.grepText(pattern)
                if (matches.isEmpty()) "Tidak ada kecocokan untuk '$pattern'."
                else matches.joinToString("\n") { "${it.path}:${it.lineNumber}: ${it.lineText}" }
            }
            "write" -> {
                if (!allowMutating) throw IllegalStateException("Mode 'plan' hanya mengizinkan operasi baca.")
                val path = args.str("path") ?: throw IllegalArgumentException("Parameter 'path' wajib diisi")
                val content = args.str("content") ?: ""
                val (before, after) = workspace.writeFile(path, content)
                recordDiff(sessionId, path, before, after)
                touchedFiles.add(path)
                "File '$path' berhasil ditulis (${after.lines().size} baris)."
            }
            "edit" -> {
                if (!allowMutating) throw IllegalStateException("Mode 'plan' hanya mengizinkan operasi baca.")
                val path = args.str("path") ?: throw IllegalArgumentException("Parameter 'path' wajib diisi")
                val oldStr = args.str("oldString") ?: throw IllegalArgumentException("Parameter 'oldString' wajib diisi")
                val newStr = args.str("newString") ?: ""
                val (before, after) = workspace.editFile(path, oldStr, newStr)
                recordDiff(sessionId, path, before, after)
                touchedFiles.add(path)
                "File '$path' berhasil diperbarui."
            }
            "bash" -> {
                if (!allowMutating) throw IllegalStateException("Mode 'plan' hanya mengizinkan operasi baca.")
                val cmd = args.str("command") ?: throw IllegalArgumentException("Parameter 'command' wajib diisi")
                if (!alwaysAllowShell) {
                    val permId = "perm_${UUID.randomUUID().toString().take(8)}"
                    val deferred = CompletableDeferred<String>()
                    permissionWaiters[permId] = deferred
                    eventsFlow.tryEmit(
                        OpenCodeEvent.PermissionAsked(
                            PermissionRequestItem(
                                id = permId,
                                sessionId = sessionId,
                                title = "Jalankan perintah shell di workspace Android?",
                                type = "bash",
                                patternText = cmd
                            )
                        )
                    )
                    val decision = deferred.await()
                    if (decision == "reject") {
                        throw IllegalStateException("Eksekusi perintah shell ditolak oleh pengguna.")
                    }
                }
                val res = workspace.runLocalShell(cmd)
                "exit=${res.exitCode}\n${res.output}"
            }
            else -> throw IllegalArgumentException("Tool tidak dikenal: ${toolCall.name}")
        }
    }

    private fun recordDiff(sessionId: String, filePath: String, before: String, after: String) {
        val bundle = loadBundle(sessionId)
        val diffs = bundle.diffs.toMutableList()
        val existingIdx = diffs.indexOfFirst { it.file == filePath }
        val originalBefore = if (existingIdx >= 0) diffs[existingIdx].before else before
        val lines = DiffComputation.computeDiffLines(originalBefore, after)
        val adds = lines.count { it.type == DiffLineType.ADDED }
        val dels = lines.count { it.type == DiffLineType.REMOVED }
        val updated = FileDiff(
            file = filePath,
            before = originalBefore,
            after = after,
            additions = adds,
            deletions = dels
        )
        if (existingIdx >= 0) diffs[existingIdx] = updated else diffs.add(updated)
        saveBundle(sessionId, bundle.copy(diffs = diffs))

        val all = loadSessionsList().toMutableList()
        val sIdx = all.indexOfFirst { it.id == sessionId }
        if (sIdx >= 0) {
            val updatedSession = all[sIdx].copy(
                time = (all[sIdx].time ?: TimeRange()).copy(updated = System.currentTimeMillis()),
                summary = SessionSummary(
                    additions = diffs.sumOf { it.additions },
                    deletions = diffs.sumOf { it.deletions },
                    files = diffs.size
                )
            )
            all[sIdx] = updatedSession
            saveSessionsList(all)
            eventsFlow.tryEmit(OpenCodeEvent.SessionUpserted(updatedSession))
        }
    }

    private fun buildConversationHistory(
        sessionId: String,
        agent: String,
        excludeMessageId: String? = null
    ): MutableList<LlmConversationMessage> {
        val workspacePath = workspace.workspaceDir.absolutePath
        val systemPrompt = buildString {
            appendLine("Anda adalah OpenCode Agent yang berjalan secara lokal di dalam backend aplikasi Android.")
            appendLine("Direktori workspace aktif: $workspacePath")
            appendLine("Mode agen: $agent (${if (agent == "plan") "read-only planning" else "full build & edit"}). ")
            appendLine("Gunakan tool `list`, `read`, `grep`, `write`, `edit`, `bash` sesuai kebutuhan untuk memeriksa atau mengubah file nyata di workspace.")
            appendLine("Jawab dengan ringkas, akurat, dan sertakan blok kode jika relevan. Gunakan bahasa yang sesuai dengan pengguna.")
        }

        val list = mutableListOf<LlmConversationMessage>(LlmConversationMessage.System(systemPrompt))
        val msgs = getMessages(sessionId).takeLast(14)
        for (m in msgs) {
            if (m.id == excludeMessageId || !m.errorText.isNullOrBlank()) continue
            val txt = m.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
            if (txt.isBlank()) continue
            if (m.role == "user") {
                list.add(LlmConversationMessage.User(txt))
            } else {
                list.add(LlmConversationMessage.Assistant(txt))
            }
        }
        return list
    }

    private fun autoTitleIfEmpty(sessionId: String, prompt: String) {
        val all = loadSessionsList().toMutableList()
        val idx = all.indexOfFirst { it.id == sessionId }
        if (idx >= 0 && all[idx].title.isBlank()) {
            val autoTitle = prompt.lineSequence().firstOrNull()?.take(42)?.trim().orEmpty()
            if (autoTitle.isNotEmpty()) {
                val updated = all[idx].copy(title = autoTitle)
                all[idx] = updated
                saveSessionsList(all)
                eventsFlow.tryEmit(OpenCodeEvent.SessionUpserted(updated))
            }
        }
    }

    private fun setStatus(sessionId: String, runStatus: SessionRunStatus) {
        val info = SessionStatusInfo(status = runStatus)
        sessionStatuses[sessionId] = info
        eventsFlow.tryEmit(OpenCodeEvent.SessionStatusChanged(sessionId, info))
    }

    private fun appendMessage(sessionId: String, msg: ChatMessage) {
        val bundle = loadBundle(sessionId)
        val updated = bundle.messages + msg.toStored()
        saveBundle(sessionId, bundle.copy(messages = updated))
        eventsFlow.tryEmit(OpenCodeEvent.MessageUpserted(msg))
    }

    private fun updateMessage(sessionId: String, msg: ChatMessage) {
        val bundle = loadBundle(sessionId)
        val list = bundle.messages.toMutableList()
        val idx = list.indexOfFirst { it.id == msg.id }
        if (idx >= 0) list[idx] = msg.toStored() else list.add(msg.toStored())
        saveBundle(sessionId, bundle.copy(messages = list))
        eventsFlow.tryEmit(OpenCodeEvent.MessageUpserted(msg))
    }

    private fun sessionsFile(): File {
        workspace.ensureEnvironmentInitialized()
        return File(workspace.stateDir, "sessions.json")
    }

    private fun bundleFile(sessionId: String): File {
        workspace.ensureEnvironmentInitialized()
        return File(workspace.stateDir, "session_$sessionId.json")
    }

    private fun loadSessionsList(): List<OpenCodeSession> {
        val file = sessionsFile()
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<OpenCodeSession>>(file.readText()) }
            .getOrElse { emptyList() }
    }

    private fun saveSessionsList(sessions: List<OpenCodeSession>) {
        sessionsFile().writeText(json.encodeToString(sessions))
    }

    private fun loadBundle(sessionId: String): StoredSessionBundle {
        val file = bundleFile(sessionId)
        if (!file.exists()) return StoredSessionBundle()
        return runCatching { json.decodeFromString<StoredSessionBundle>(file.readText()) }
            .getOrElse { StoredSessionBundle() }
    }

    private fun saveBundle(sessionId: String, bundle: StoredSessionBundle) {
        bundleFile(sessionId).writeText(json.encodeToString(bundle))
    }

    private fun summarizeArgs(args: JsonObject): String {
        val keys = listOf("command", "path", "pattern")
        for (k in keys) {
            val v = args.str(k)
            if (!v.isNullOrBlank()) return v
        }
        return args.toString().take(80)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun ChatMessage.toStored(): StoredMessage = StoredMessage(
        id = id,
        sessionId = sessionId,
        role = role,
        createdAt = createdAt,
        completedAt = completedAt,
        agent = agent,
        modelId = modelId,
        providerId = providerId,
        errorText = errorText,
        parts = parts.mapNotNull { it.toStored() }
    )

    private fun MessagePart.toStored(): StoredPart? = when (this) {
        is MessagePart.Text -> StoredPart(id, messageId, "text", text = text)
        is MessagePart.Reasoning -> StoredPart(id, messageId, "reasoning", text = text)
        is MessagePart.ToolCall -> StoredPart(
            id = id,
            messageId = messageId,
            type = "tool",
            callId = callId,
            toolName = toolName,
            status = status,
            title = title,
            inputSummary = inputSummary,
            output = output,
            error = error
        )
        is MessagePart.Patch -> StoredPart(id, messageId, "patch", hash = hash, files = files)
        else -> null
    }

    private fun StoredMessage.toDomain(): ChatMessage = ChatMessage(
        id = id,
        sessionId = sessionId,
        role = role,
        createdAt = createdAt,
        completedAt = completedAt,
        agent = agent,
        modelId = modelId,
        providerId = providerId,
        errorText = errorText,
        parts = parts.mapNotNull { p ->
            when (p.type) {
                "text" -> MessagePart.Text(p.id, p.messageId, p.text)
                "reasoning" -> MessagePart.Reasoning(p.id, p.messageId, p.text)
                "tool" -> MessagePart.ToolCall(
                    id = p.id,
                    messageId = p.messageId,
                    callId = p.callId,
                    toolName = p.toolName,
                    status = p.status,
                    title = p.title,
                    inputSummary = p.inputSummary,
                    output = p.output,
                    error = p.error
                )
                "patch" -> MessagePart.Patch(p.id, p.messageId, p.hash, p.files)
                else -> null
            }
        }
    )
}
