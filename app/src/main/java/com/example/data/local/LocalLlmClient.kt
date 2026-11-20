package com.example.data.local

import com.example.data.model.LocalProviderConfig
import com.example.data.model.ProviderApiFormat
import com.example.data.model.ProviderCatalogDefaults
import com.example.data.model.SavedProviderAccount
import com.example.data.network.AiRetryPolicy
import com.example.data.network.SseStreamParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

data class LlmToolCallRequest(
    val id: String,
    val name: String,
    val arguments: JsonObject
)

data class LlmTurnResponse(
    val text: String,
    val reasoning: String? = null,
    val toolCalls: List<LlmToolCallRequest> = emptyList()
)

sealed interface LlmConversationMessage {
    data class System(val content: String) : LlmConversationMessage
    data class User(val content: String) : LlmConversationMessage
    data class Assistant(
        val content: String,
        val toolCalls: List<LlmToolCallRequest> = emptyList()
    ) : LlmConversationMessage
    data class ToolResult(
        val toolCallId: String,
        val toolName: String,
        val output: String
    ) : LlmConversationMessage
}

interface StreamingLlmApiService {
    @Streaming
    @POST
    suspend fun streamPost(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @GET
    suspend fun getJson(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ResponseBody>
}

class LocalLlmClient(
    private val json: Json
) {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Never use Level.BODY on SSE streams as it buffers the entire stream
        level = HttpLoggingInterceptor.Level.HEADERS
    }

    private val sseHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(AiRetryPolicy.ENDPOINT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(AiRetryPolicy.ENDPOINT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(AiRetryPolicy.ENDPOINT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val streamingApi: StreamingLlmApiService = Retrofit.Builder()
        .baseUrl("https://localhost/")
        .client(sseHttpClient)
        .build()
        .create(StreamingLlmApiService::class.java)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateTurn(
        config: LocalProviderConfig,
        messages: List<LlmConversationMessage>,
        allowMutatingTools: Boolean
    ): LlmTurnResponse = streamTurn(
        config = config,
        messages = messages,
        allowMutatingTools = allowMutatingTools
    )

    suspend fun streamTurn(
        config: LocalProviderConfig,
        messages: List<LlmConversationMessage>,
        allowMutatingTools: Boolean,
        onTextDelta: suspend (String) -> Unit = {},
        onReasoningDelta: suspend (String) -> Unit = {},
        onRetryAttempt: suspend (retryAttempt: Int, maxRetries: Int, error: Throwable) -> Unit = { _, _, _ -> }
    ): LlmTurnResponse = withContext(Dispatchers.IO) {
        val isCustomEndpoint = config.customBaseUrl.isNotBlank() &&
            ProviderCatalogDefaults.builtInSpec(config.providerId) == null
        if (config.apiKey.isBlank() && !isCustomEndpoint) {
            throw AiRetryPolicy.NonRetryableAiException(
                "API Key untuk '${config.providerId}' belum diisi. Atur API Key di menu Server → Provider AI."
            )
        }

        val providerLower = config.providerId.lowercase()
        val resolvedFormat = when {
            providerLower == "google" -> ProviderApiFormat.GEMINI
            providerLower == "anthropic" -> ProviderApiFormat.ANTHROPIC
            providerLower == "opencode" -> {
                val cleanModel = config.modelId.removePrefix("opencode/").lowercase()
                when {
                    cleanModel.startsWith("claude") -> ProviderApiFormat.ANTHROPIC
                    cleanModel.startsWith("gemini") -> ProviderApiFormat.GEMINI
                    else -> ProviderApiFormat.OPENAI_COMPATIBLE
                }
            }
            else -> config.apiFormat
        }

        AiRetryPolicy.executeWithTimeoutAndRetry(
            timeoutMs = AiRetryPolicy.ENDPOINT_TIMEOUT_MS,
            maxRetries = AiRetryPolicy.MAX_RETRIES,
            onRetry = onRetryAttempt
        ) {
            when (resolvedFormat) {
                ProviderApiFormat.GEMINI -> streamGemini(config, messages, allowMutatingTools, onTextDelta, onReasoningDelta)
                ProviderApiFormat.ANTHROPIC -> streamAnthropic(config, messages, allowMutatingTools, onTextDelta, onReasoningDelta)
                ProviderApiFormat.OPENAI_COMPATIBLE -> streamOpenAiCompatible(config, messages, allowMutatingTools, onTextDelta, onReasoningDelta)
            }
        }
    }

    suspend fun fetchAvailableModels(account: SavedProviderAccount): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = account.baseUrl.ifBlank { ProviderCatalogDefaults.defaultBaseUrlFor(account.id) }
                .trim()
                .trimEnd('/')
            val format = when (account.id.lowercase()) {
                "google" -> ProviderApiFormat.GEMINI
                "anthropic" -> ProviderApiFormat.ANTHROPIC
                else -> account.apiFormat
            }
            val key = account.apiKey.trim()

            when (format) {
                ProviderApiFormat.GEMINI -> {
                    if (key.isBlank()) throw IllegalStateException("API Key Gemini diperlukan untuk memuat daftar model.")
                    val url = "$base/models?key=$key"
                    val resp = streamingApi.getJson(url, emptyMap())
                    val raw = resp.body()?.use { it.string() }
                        ?: resp.errorBody()?.use { it.string() }.orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code()}: ${extractErrorMsg(raw)}")
                    }
                    parseGeminiModelsResponse(raw)
                }

                ProviderApiFormat.ANTHROPIC -> {
                    if (key.isBlank()) throw IllegalStateException("API Key Anthropic diperlukan untuk memuat daftar model.")
                    val url = if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
                    val headers = mapOf(
                        "x-api-key" to key,
                        "anthropic-version" to "2023-06-01"
                    )
                    val resp = streamingApi.getJson(url, headers)
                    val raw = resp.body()?.use { it.string() }
                        ?: resp.errorBody()?.use { it.string() }.orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code()}: ${extractErrorMsg(raw)}")
                    }
                    parseOpenAiModelsResponse(raw)
                }

                ProviderApiFormat.OPENAI_COMPATIBLE -> {
                    val url = if (base.endsWith("/models")) base else "$base/models"
                    val headers = buildMap {
                        if (key.isNotBlank()) put("Authorization", "Bearer $key")
                    }
                    val resp = streamingApi.getJson(url, headers)
                    val raw = resp.body()?.use { it.string() }
                        ?: resp.errorBody()?.use { it.string() }.orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code()}: ${extractErrorMsg(raw)}")
                    }
                    parseOpenAiModelsResponse(raw)
                }
            }
        }
    }

    internal fun parseOpenAiModelsResponse(raw: String): List<String> {
        val root = json.parseToJsonElement(raw).jsonObject
        val arr = (root["data"] as? JsonArray) ?: (root["models"] as? JsonArray) ?: JsonArray(emptyList())
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            (obj["id"] as? JsonPrimitive)?.contentOrNull
                ?: (obj["name"] as? JsonPrimitive)?.contentOrNull
        }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    internal fun parseGeminiModelsResponse(raw: String): List<String> {
        val root = json.parseToJsonElement(raw).jsonObject
        val arr = (root["models"] as? JsonArray) ?: JsonArray(emptyList())
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val methods = (obj["supportedGenerationMethods"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }.orEmpty()
            if (methods.isNotEmpty() && "generateContent" !in methods) return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            name.removePrefix("models/")
        }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private suspend fun streamGemini(
        config: LocalProviderConfig,
        messages: List<LlmConversationMessage>,
        allowMutatingTools: Boolean,
        onTextDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit
    ): LlmTurnResponse {
        val isOpenCode = config.providerId.equals("opencode", ignoreCase = true)
        val model = config.modelId.removePrefix("opencode/").ifBlank { "gemini-2.5-flash" }
        val baseUrl = config.customBaseUrl.ifBlank {
            if (isOpenCode) ProviderCatalogDefaults.defaultBaseUrlFor("opencode")
            else ProviderCatalogDefaults.defaultBaseUrlFor("google")
        }.trimEnd('/')
        val key = config.apiKey.trim()
        val url = if (isOpenCode) {
            "$baseUrl/models/$model:streamGenerateContent?alt=sse"
        } else {
            "$baseUrl/models/$model:streamGenerateContent?alt=sse&key=$key"
        }

        val systemText = messages.filterIsInstance<LlmConversationMessage.System>()
            .joinToString("\n") { it.content }

        val contents = buildJsonArray {
            messages.filterNot { it is LlmConversationMessage.System }.forEach { msg ->
                when (msg) {
                    is LlmConversationMessage.User -> add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray {
                                add(buildJsonObject { put("text", msg.content) })
                            })
                        }
                    )
                    is LlmConversationMessage.Assistant -> add(
                        buildJsonObject {
                            put("role", "model")
                            put("parts", buildJsonArray {
                                if (msg.content.isNotBlank()) {
                                    add(buildJsonObject { put("text", msg.content) })
                                }
                                msg.toolCalls.forEach { tc ->
                                    add(buildJsonObject {
                                        put("functionCall", buildJsonObject {
                                            put("name", tc.name)
                                            put("args", tc.arguments)
                                        })
                                    })
                                }
                            })
                        }
                    )
                    is LlmConversationMessage.ToolResult -> add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray {
                                add(buildJsonObject {
                                    put("functionResponse", buildJsonObject {
                                        put("name", msg.toolName)
                                        put("response", buildJsonObject {
                                            put("result", msg.output)
                                        })
                                    })
                                })
                            })
                        }
                    )
                    is LlmConversationMessage.System -> Unit
                }
            }
        }

        val payload = buildJsonObject {
            if (systemText.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemText) })
                    })
                })
            }
            put("contents", contents)
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("functionDeclarations", buildToolDeclarations(allowMutatingTools))
                })
            })
        }

        val headers = buildMap {
            put("Accept", "text/event-stream")
            put("Cache-Control", "no-cache")
            if (isOpenCode && key.isNotBlank()) {
                put("Authorization", "Bearer $key")
                put("x-goog-api-key", key)
            }
        }
        val resp = streamingApi.streamPost(
            url = url,
            headers = headers,
            body = payload.toString().toRequestBody(jsonMediaType)
        )
        if (!resp.isSuccessful) {
            val errRaw = resp.errorBody()?.use { it.string() }.orEmpty()
            throw IllegalStateException("Gemini API Error (${resp.code()}): ${extractErrorMsg(errRaw)}")
        }
        val body = resp.body() ?: throw IllegalStateException("Respons stream Gemini kosong")
        val accumulator = GeminiSseAccumulator(json)
        body.use { responseBody ->
            SseStreamParser.readFrames(responseBody) { frame ->
                accumulator.accept(frame, onTextDelta, onReasoningDelta)
            }
        }
        return accumulator.buildResult()
    }

    private suspend fun streamAnthropic(
        config: LocalProviderConfig,
        messages: List<LlmConversationMessage>,
        allowMutatingTools: Boolean,
        onTextDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit
    ): LlmTurnResponse {
        val isOpenCode = config.providerId.equals("opencode", ignoreCase = true)
        val model = config.modelId.removePrefix("opencode/").ifBlank { "claude-sonnet-4-20250514" }
        val baseUrl = config.customBaseUrl.ifBlank {
            if (isOpenCode) ProviderCatalogDefaults.defaultBaseUrlFor("opencode")
            else ProviderCatalogDefaults.defaultBaseUrlFor("anthropic")
        }.trimEnd('/')
        val url = if (baseUrl.endsWith("/messages")) baseUrl else "$baseUrl/messages"

        val systemText = messages.filterIsInstance<LlmConversationMessage.System>()
            .joinToString("\n") { it.content }

        val anthropicMsgs = buildJsonArray {
            messages.filterNot { it is LlmConversationMessage.System }.forEach { msg ->
                when (msg) {
                    is LlmConversationMessage.User -> add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", msg.content)
                        }
                    )
                    is LlmConversationMessage.Assistant -> add(
                        buildJsonObject {
                            put("role", "assistant")
                            put("content", buildJsonArray {
                                if (msg.content.isNotBlank()) {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", msg.content)
                                    })
                                }
                                msg.toolCalls.forEach { tc ->
                                    add(buildJsonObject {
                                        put("type", "tool_use")
                                        put("id", tc.id)
                                        put("name", tc.name)
                                        put("input", tc.arguments)
                                    })
                                }
                            })
                        }
                    )
                    is LlmConversationMessage.ToolResult -> add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", msg.toolCallId)
                                    put("content", msg.output)
                                })
                            })
                        }
                    )
                    is LlmConversationMessage.System -> Unit
                }
            }
        }

        val tools = buildJsonArray {
            toolSpecs(allowMutatingTools).forEach { spec ->
                add(buildJsonObject {
                    put("name", spec.name)
                    put("description", spec.description)
                    put("input_schema", spec.parameters)
                })
            }
        }

        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put("stream", true)
            if (systemText.isNotBlank()) put("system", systemText)
            put("messages", anthropicMsgs)
            put("tools", tools)
        }

        val key = config.apiKey.trim()
        val headers = buildMap {
            if (key.isNotBlank()) {
                put("x-api-key", key)
                if (isOpenCode) put("Authorization", "Bearer $key")
            }
            put("anthropic-version", "2023-06-01")
            put("Accept", "text/event-stream")
            put("Cache-Control", "no-cache")
        }
        val resp = streamingApi.streamPost(
            url = url,
            headers = headers,
            body = payload.toString().toRequestBody(jsonMediaType)
        )
        if (!resp.isSuccessful) {
            val errRaw = resp.errorBody()?.use { it.string() }.orEmpty()
            throw IllegalStateException("Anthropic API Error (${resp.code()}): ${extractErrorMsg(errRaw)}")
        }
        val body = resp.body() ?: throw IllegalStateException("Respons stream Anthropic kosong")
        val accumulator = AnthropicSseAccumulator(json)
        body.use { responseBody ->
            SseStreamParser.readFrames(responseBody) { frame ->
                accumulator.accept(frame, onTextDelta, onReasoningDelta)
            }
        }
        return accumulator.buildResult()
    }

    private suspend fun streamOpenAiCompatible(
        config: LocalProviderConfig,
        messages: List<LlmConversationMessage>,
        allowMutatingTools: Boolean,
        onTextDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit
    ): LlmTurnResponse {
        val baseUrl = resolveOpenAiBaseUrl(config)
        val rawModel = config.modelId.ifBlank { defaultModelForProvider(config.providerId) }
        val model = if (config.providerId.equals("opencode", ignoreCase = true)) {
            rawModel.removePrefix("opencode/")
        } else {
            rawModel
        }

        val oaiMessages = buildJsonArray {
            messages.forEach { msg ->
                when (msg) {
                    is LlmConversationMessage.System -> add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", msg.content)
                        }
                    )
                    is LlmConversationMessage.User -> add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", msg.content)
                        }
                    )
                    is LlmConversationMessage.Assistant -> add(
                        buildJsonObject {
                            put("role", "assistant")
                            put("content", msg.content)
                            if (msg.toolCalls.isNotEmpty()) {
                                put("tool_calls", buildJsonArray {
                                    msg.toolCalls.forEach { tc ->
                                        add(buildJsonObject {
                                            put("id", tc.id)
                                            put("type", "function")
                                            put("function", buildJsonObject {
                                                put("name", tc.name)
                                                put("arguments", tc.arguments.toString())
                                            })
                                        })
                                    }
                                })
                            }
                        }
                    )
                    is LlmConversationMessage.ToolResult -> add(
                        buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", msg.toolCallId)
                            put("content", msg.output)
                        }
                    )
                }
            }
        }

        val tools = buildJsonArray {
            toolSpecs(allowMutatingTools).forEach { spec ->
                add(buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", spec.name)
                        put("description", spec.description)
                        put("parameters", spec.parameters)
                    })
                })
            }
        }

        val payload = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", oaiMessages)
            put("tools", tools)
        }

        val key = config.apiKey.trim()
        val headers = buildMap {
            if (key.isNotBlank()) put("Authorization", "Bearer $key")
            put("Accept", "text/event-stream")
            put("Cache-Control", "no-cache")
        }
        val endpointUrl = if (baseUrl.trimEnd('/').endsWith("/chat/completions")) {
            baseUrl.trimEnd('/')
        } else {
            "${baseUrl.trimEnd('/')}/chat/completions"
        }
        val resp = streamingApi.streamPost(
            url = endpointUrl,
            headers = headers,
            body = payload.toString().toRequestBody(jsonMediaType)
        )
        if (!resp.isSuccessful) {
            val errRaw = resp.errorBody()?.use { it.string() }.orEmpty()
            throw IllegalStateException("LLM API Error (${resp.code()}): ${extractErrorMsg(errRaw)}")
        }
        val body = resp.body() ?: throw IllegalStateException("Respons stream model kosong")
        val accumulator = OpenAiSseAccumulator(json)
        body.use { responseBody ->
            SseStreamParser.readFrames(responseBody) { frame ->
                accumulator.accept(frame, onTextDelta, onReasoningDelta)
            }
        }
        return accumulator.buildResult()
    }

    private fun resolveOpenAiBaseUrl(config: LocalProviderConfig): String {
        if (config.customBaseUrl.isNotBlank()) return config.customBaseUrl.trim()
        return ProviderCatalogDefaults.defaultBaseUrlFor(config.providerId)
    }

    private fun extractErrorMsg(raw: String): String {
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val errObj = obj["error"] as? JsonObject
            (errObj?.get("message") as? JsonPrimitive)?.contentOrNull ?: raw.take(240)
        }.getOrElse { raw.take(240) }
    }

    private data class ToolSpec(
        val name: String,
        val description: String,
        val parameters: JsonObject
    )

    private fun toolSpecs(allowMutating: Boolean): List<ToolSpec> {
        val readOnly = listOf(
            ToolSpec(
                name = "list",
                description = "List files and directories inside the workspace",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Relative directory path (empty for root)")
                        })
                    })
                }
            ),
            ToolSpec(
                name = "read",
                description = "Read the text content of a file in the workspace",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Relative path to the file")
                        })
                    })
                    put("required", buildJsonArray { add(JsonPrimitive("path")) })
                }
            ),
            ToolSpec(
                name = "grep",
                description = "Search for a text or regex pattern across workspace files",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("pattern", buildJsonObject {
                            put("type", "string")
                            put("description", "Text or regex pattern to search")
                        })
                    })
                    put("required", buildJsonArray { add(JsonPrimitive("pattern")) })
                }
            )
        )

        if (!allowMutating) return readOnly

        return readOnly + listOf(
            ToolSpec(
                name = "write",
                description = "Create or overwrite a file in the workspace",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject { put("type", "string") })
                        put("content", buildJsonObject { put("type", "string") })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("path"))
                        add(JsonPrimitive("content"))
                    })
                }
            ),
            ToolSpec(
                name = "edit",
                description = "Replace an exact text snippet inside an existing workspace file",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject { put("type", "string") })
                        put("oldString", buildJsonObject { put("type", "string") })
                        put("newString", buildJsonObject { put("type", "string") })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("path"))
                        add(JsonPrimitive("oldString"))
                        add(JsonPrimitive("newString"))
                    })
                }
            ),
            ToolSpec(
                name = "bash",
                description = "Execute a shell command inside the workspace directory",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("command", buildJsonObject { put("type", "string") })
                    })
                    put("required", buildJsonArray { add(JsonPrimitive("command")) })
                }
            )
        )
    }

    private fun buildToolDeclarations(allowMutating: Boolean): JsonArray {
        return buildJsonArray {
            toolSpecs(allowMutating).forEach { spec ->
                add(buildJsonObject {
                    put("name", spec.name)
                    put("description", spec.description)
                    put("parameters", spec.parameters)
                })
            }
        }
    }

    companion object {
        fun defaultModelForProvider(providerId: String): String =
            ProviderCatalogDefaults.defaultModelFor(providerId)

        fun availableLocalModels(
            config: LocalProviderConfig,
            accounts: List<SavedProviderAccount> = ProviderCatalogDefaults.defaultAccounts()
        ): List<Pair<String, String>> {
            val all = accounts.flatMap { account ->
                ProviderCatalogDefaults.modelsForAccount(account).map { model ->
                    account.id to model
                }
            }.toMutableList()
            if (config.modelId.isNotBlank() && all.none { it.first == config.providerId && it.second == config.modelId }) {
                all.add(0, config.providerId to config.modelId)
            }
            return all.distinct()
        }
    }
}
