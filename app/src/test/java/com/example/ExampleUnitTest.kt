package com.example

import com.example.data.local.AnthropicSseAccumulator
import com.example.data.local.GeminiSseAccumulator
import com.example.data.local.LocalLlmClient
import com.example.data.local.OpenAiSseAccumulator
import com.example.data.model.DiffComputation
import com.example.data.model.DiffLineType
import com.example.data.model.LocalProviderConfig
import com.example.data.model.MessagePart
import com.example.data.model.OpenCodeEvent
import com.example.data.model.OpenCodeJsonParser
import com.example.data.model.ProviderApiFormat
import com.example.data.model.ProviderCatalogDefaults
import com.example.data.model.SavedProviderAccount
import com.example.data.model.ServerProfile
import com.example.data.model.SessionRunStatus
import com.example.data.network.AiRetryPolicy
import com.example.data.network.OpenCodeSseClient
import com.example.data.network.SseFrame
import com.example.data.network.SseStreamParser
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun normalizeServerUrl_addsSchemeAndTrimsTrailingSlash() {
        assertEquals("http://192.168.1.50:4096", ServerProfile.normalizeServerUrl("192.168.1.50:4096/"))
        assertEquals("https://code.example.com", ServerProfile.normalizeServerUrl("  https://code.example.com/// "))
        assertEquals("", ServerProfile.normalizeServerUrl("   "))
    }

    @Test
    fun sseClient_parsesPartUpdatedAndGlobalPayloadWrapper() {
        val sse = OpenCodeSseClient(json)
        val raw = """
            {
              "directory": "/repo",
              "payload": {
                "type": "message.part.updated",
                "properties": {
                  "part": {
                    "id": "part_1",
                    "sessionID": "ses_1",
                    "messageID": "msg_1",
                    "type": "tool",
                    "callID": "call_1",
                    "tool": "bash",
                    "state": {
                      "status": "completed",
                      "input": { "command": "gradle test" },
                      "output": "BUILD SUCCESSFUL"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val event = sse.parseSsePayload(raw) as OpenCodeEvent.PartUpserted
        assertEquals("ses_1", event.sessionId)
        val toolPart = event.part as MessagePart.ToolCall
        assertEquals("bash", toolPart.toolName)
        assertEquals("completed", toolPart.status)
        assertEquals("gradle test", toolPart.inputSummary)
        assertEquals("BUILD SUCCESSFUL", toolPart.output)
    }

    @Test
    fun sseClient_parsesSessionStatusAndPermissionAsked() {
        val sse = OpenCodeSseClient(json)
        val statusEvent = sse.parseSsePayload(
            """{"type":"session.status","properties":{"sessionID":"s1","status":{"type":"busy"}}}"""
        ) as OpenCodeEvent.SessionStatusChanged
        assertEquals("s1", statusEvent.sessionId)
        assertEquals(SessionRunStatus.BUSY, statusEvent.status.status)

        val permEvent = sse.parseSsePayload(
            """{"type":"permission.asked","properties":{"id":"p1","sessionID":"s1","title":"Jalankan rm -rf build","type":"bash","pattern":"rm -rf build"}}"""
        ) as OpenCodeEvent.PermissionAsked
        assertEquals("p1", permEvent.permission.id)
        assertEquals("bash", permEvent.permission.type)
    }

    @Test
    fun jsonParser_parsesMessageWithParts() {
        val raw = """
            {
              "info": {
                "id": "msg_42",
                "sessionID": "ses_9",
                "role": "assistant",
                "agent": "build",
                "modelID": "claude-sonnet-4",
                "time": { "created": 1700000000, "completed": 1700000500 }
              },
              "parts": [
                { "id": "p1", "messageID": "msg_42", "type": "reasoning", "text": "Checking files" },
                { "id": "p2", "messageID": "msg_42", "type": "text", "text": "Done!" }
              ]
            }
        """.trimIndent()
        val msg = OpenCodeJsonParser.parseMessageWithParts(json.parseToJsonElement(raw).jsonObject)!!
        assertEquals("msg_42", msg.id)
        assertEquals("build", msg.agent)
        assertEquals(2, msg.parts.size)
        assertTrue(msg.parts[0] is MessagePart.Reasoning)
        assertTrue(msg.parts[1] is MessagePart.Text)
    }

    @Test
    fun diffComputation_detectsAdditionsAndDeletions() {
        val before = "line1\nline2\nline3"
        val after = "line1\nline2_edited\nline3\nline4"
        val diff = DiffComputation.computeDiffLines(before, after)

        assertTrue(diff.any { it.type == DiffLineType.REMOVED && it.content == "line2" })
        assertTrue(diff.any { it.type == DiffLineType.ADDED && it.content == "line2_edited" })
        assertTrue(diff.any { it.type == DiffLineType.ADDED && it.content == "line4" })
    }

    @Test
    fun localAndroidProfile_isDefaultPresetAndRecognized() {
        val first = ServerProfile.defaultPresets.first()
        assertTrue(first.isLocalAndroid)
        assertEquals("local://android", first.normalizedUrl)
        assertEquals("gemini-2.5-flash", com.example.data.local.LocalLlmClient.defaultModelForProvider("google"))
        assertEquals("claude-sonnet-4-20250514", com.example.data.local.LocalLlmClient.defaultModelForProvider("anthropic"))
    }

    @Test
    fun sseStreamParser_parsesMultilineCommentsRetryDoneAndTrailingFrame() = runBlocking {
        val rawSse = buildString {
            appendLine(": keepalive comment")
            appendLine("id: evt-1")
            appendLine("event: message.part.updated")
            appendLine("retry: 2500")
            appendLine("data: {\"line\":1}")
            appendLine("data: {\"line\":2}")
            appendLine()
            appendLine("id: evt-2")
            appendLine("data: [DONE]")
            appendLine()
            append("data: should_not_reach_after_done")
        }
        val body = rawSse.toResponseBody("text/event-stream".toMediaType())
        val frames = mutableListOf<SseFrame>()
        SseStreamParser.readFrames(body) { frames.add(it) }

        assertEquals(1, frames.size)
        assertEquals("evt-1", frames[0].id)
        assertEquals("message.part.updated", frames[0].event)
        assertEquals(2500L, frames[0].retryMs)
        assertEquals("{\"line\":1}\n{\"line\":2}", frames[0].data)

        val noBlankLineBody = "id: tail\ndata: {\"ok\":true}".toResponseBody("text/event-stream".toMediaType())
        val tailFrames = mutableListOf<SseFrame>()
        SseStreamParser.readFrames(noBlankLineBody) { tailFrames.add(it) }
        assertEquals(1, tailFrames.size)
        assertEquals("tail", tailFrames[0].id)
        assertEquals("{\"ok\":true}", tailFrames[0].data)
    }

    @Test
    fun openAiSseAccumulator_streamsContentAndStitchesToolCalls() = runBlocking {
        val acc = OpenAiSseAccumulator(json)
        val textChunks = mutableListOf<String>()
        val reasoningChunks = mutableListOf<String>()

        val frames = listOf(
            SseFrame(data = """{"choices":[{"delta":{"reasoning_content":"Inspecting "}}]}"""),
            SseFrame(data = """{"choices":[{"delta":{"reasoning_content":"workspace."}}]}"""),
            SseFrame(data = """{"choices":[{"delta":{"content":"```kotlin\n"}}]}"""),
            SseFrame(data = """{"choices":[{"delta":{"content":"fun main() {}\n```"}}]}"""),
            SseFrame(data = """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_abc","type":"function","function":{"name":"read","arguments":"{\"path\":"}}]}}]}"""),
            SseFrame(data = """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"Main.kt\"}"}}]}}]}"""),
            SseFrame(data = "[DONE]")
        )

        frames.forEach { f ->
            acc.accept(f, onTextDelta = { textChunks.add(it) }, onReasoningDelta = { reasoningChunks.add(it) })
        }

        val result = acc.buildResult()
        assertEquals(listOf("```kotlin\n", "fun main() {}\n```"), textChunks)
        assertEquals(listOf("Inspecting ", "workspace."), reasoningChunks)
        assertEquals("Inspecting workspace.", result.reasoning)
        assertEquals("```kotlin\nfun main() {}\n```", result.text)
        assertEquals(1, result.toolCalls.size)
        assertEquals("call_abc", result.toolCalls[0].id)
        assertEquals("read", result.toolCalls[0].name)
        assertEquals(JsonPrimitive("Main.kt"), result.toolCalls[0].arguments["path"])
    }

    @Test
    fun geminiAndAnthropicSseAccumulators_parseStreamingDeltasAndTools() = runBlocking {
        val gemAcc = GeminiSseAccumulator(json)
        val gemText = mutableListOf<String>()
        val gemThought = mutableListOf<String>()
        gemAcc.accept(
            SseFrame(data = """{"candidates":[{"content":{"parts":[{"text":"Thinking...","thought":true}]}}]}"""),
            onTextDelta = { gemText.add(it) },
            onReasoningDelta = { gemThought.add(it) }
        )
        gemAcc.accept(
            SseFrame(data = """{"candidates":[{"content":{"parts":[{"text":"Hello "},{"text":"world"},{"functionCall":{"name":"list","args":{"path":"src"}}}]}}]}"""),
            onTextDelta = { gemText.add(it) },
            onReasoningDelta = { gemThought.add(it) }
        )
        val gemRes = gemAcc.buildResult()
        assertEquals("Thinking...", gemRes.reasoning)
        assertEquals("Hello world", gemRes.text)
        assertEquals(1, gemRes.toolCalls.size)
        assertEquals("list", gemRes.toolCalls[0].name)
        assertEquals(JsonPrimitive("src"), gemRes.toolCalls[0].arguments["path"])

        val antAcc = AnthropicSseAccumulator(json)
        val antFrames = listOf(
            SseFrame(event = "content_block_delta", data = """{"index":0,"delta":{"type":"thinking_delta","thinking":"Plan step 1"}}"""),
            SseFrame(event = "content_block_delta", data = """{"index":1,"delta":{"type":"text_delta","text":"Checking "}}"""),
            SseFrame(event = "content_block_delta", data = """{"index":1,"delta":{"type":"text_delta","text":"code."}}"""),
            SseFrame(event = "content_block_start", data = """{"index":2,"content_block":{"type":"tool_use","id":"toolu_1","name":"grep","input":{}}}"""),
            SseFrame(event = "content_block_delta", data = """{"index":2,"delta":{"type":"input_json_delta","partial_json":"{\"pattern\":"}}"""),
            SseFrame(event = "content_block_delta", data = """{"index":2,"delta":{"type":"input_json_delta","partial_json":"\"MainActivity\"}"}}"""),
            SseFrame(event = "content_block_stop", data = """{"index":2}""")
        )
        antFrames.forEach { antAcc.accept(it, {}, {}) }
        val antRes = antAcc.buildResult()
        assertEquals("Plan step 1", antRes.reasoning)
        assertEquals("Checking code.", antRes.text)
        assertEquals(1, antRes.toolCalls.size)
        assertEquals("toolu_1", antRes.toolCalls[0].id)
        assertEquals("grep", antRes.toolCalls[0].name)
        assertEquals(JsonPrimitive("MainActivity"), antRes.toolCalls[0].arguments["pattern"])
    }

    @Test
    fun providerCatalogDefaults_includesOpenCodeAllModelsAndMergesCustomProviders() {
        val openCodeSpec = ProviderCatalogDefaults.builtInSpec("opencode")!!
        assertEquals("OPENCODE_API_KEY", openCodeSpec.envVarName)
        assertEquals("https://opencode.ai/zen/v1", openCodeSpec.baseUrl)
        assertTrue(openCodeSpec.models.contains("big-pickle"))
        assertTrue(openCodeSpec.models.contains("qwen3-coder"))
        assertTrue(ProviderCatalogDefaults.builtInProviders.all { it.models.isNotEmpty() })

        val savedCustom = SavedProviderAccount(
            id = "my_ollama",
            name = "Ollama Lokal",
            baseUrl = "http://10.0.2.2:11434/v1",
            apiKey = "",
            defaultModelId = "qwen2.5-coder:14b",
            apiFormat = ProviderApiFormat.OPENAI_COMPATIBLE,
            isCustom = true,
            cachedModels = listOf("qwen2.5-coder:14b", "llama3.2")
        )
        val legacy = LocalProviderConfig(
            providerId = "opencode",
            modelId = "grok-code",
            apiKey = "oc-test-key"
        )
        val merged = ProviderCatalogDefaults.mergeWithBuiltIns(listOf(savedCustom), legacy)
        val opencodeAcc = merged.first { it.id == "opencode" }
        assertEquals("oc-test-key", opencodeAcc.apiKey)
        assertTrue(ProviderCatalogDefaults.modelsForAccount(opencodeAcc).contains("grok-code"))
        assertTrue(ProviderCatalogDefaults.modelsForAccount(opencodeAcc).contains("big-pickle"))

        val customAcc = merged.first { it.id == "my_ollama" }
        assertTrue(customAcc.isCustom)
        assertEquals("http://10.0.2.2:11434/v1", customAcc.baseUrl)
        assertEquals(listOf("qwen2.5-coder:14b", "llama3.2"), ProviderCatalogDefaults.modelsForAccount(customAcc))
    }

    @Test
    fun localLlmClient_parsesOpenAiAndGeminiModelResponses() {
        val client = LocalLlmClient(json)
        val openAiJson = """
            {
              "data": [
                { "id": "big-pickle", "object": "model" },
                { "id": "qwen3-coder", "object": "model" },
                { "id": "big-pickle", "object": "model" }
              ]
            }
        """.trimIndent()
        assertEquals(listOf("big-pickle", "qwen3-coder"), client.parseOpenAiModelsResponse(openAiJson))

        val geminiJson = """
            {
              "models": [
                {
                  "name": "models/gemini-2.5-flash",
                  "supportedGenerationMethods": ["generateContent", "countTokens"]
                },
                {
                  "name": "models/embedding-001",
                  "supportedGenerationMethods": ["embedContent"]
                }
              ]
            }
        """.trimIndent()
        assertEquals(listOf("gemini-2.5-flash"), client.parseGeminiModelsResponse(geminiJson))
    }

    @Test
    fun aiRetryPolicy_uses30SecondTimeoutAndRetriesThreeTimes() = runBlocking {
        assertEquals(30_000L, AiRetryPolicy.ENDPOINT_TIMEOUT_MS)
        assertEquals(3, AiRetryPolicy.MAX_RETRIES)

        var attempts = 0
        val retryCallbacks = mutableListOf<Pair<Int, Int>>()
        val result = AiRetryPolicy.executeWithTimeoutAndRetry(
            timeoutMs = 1_000L,
            maxRetries = AiRetryPolicy.MAX_RETRIES,
            retryDelayMs = { 1L },
            onRetry = { retryAttempt, maxRetries, _ ->
                retryCallbacks.add(retryAttempt to maxRetries)
            }
        ) { attempt ->
            attempts++
            if (attempt < 3) {
                throw IOException("Endpoint gagal sementara #$attempt")
            }
            "sukses-attempt-$attempt"
        }

        assertEquals("sukses-attempt-3", result)
        assertEquals(4, attempts) // 1 initial + 3 retries
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), retryCallbacks)
    }

    @Test
    fun aiRetryPolicy_exhaustsAfterThreeRetriesAndHandlesTimeoutsAndFailFast() = runBlocking {
        var timeoutAttempts = 0
        var caughtError: Throwable? = null
        try {
            AiRetryPolicy.executeWithTimeoutAndRetry<String>(
                timeoutMs = 30L,
                maxRetries = 3,
                retryDelayMs = { 1L }
            ) {
                timeoutAttempts++
                delay(200L)
                "never"
            }
        } catch (t: Throwable) {
            caughtError = t
        }
        assertEquals(4, timeoutAttempts) // 1 initial + 3 retries
        assertTrue(caughtError?.message?.contains("Timeout", ignoreCase = true) == true)

        var authAttempts = 0
        var authError: Throwable? = null
        try {
            AiRetryPolicy.executeWithTimeoutAndRetry<String>(
                timeoutMs = 1_000L,
                maxRetries = 3,
                retryDelayMs = { 1L }
            ) {
                authAttempts++
                throw IllegalStateException("HTTP 401: Unauthorized")
            }
        } catch (t: Throwable) {
            authError = t
        }
        assertEquals(1, authAttempts) // No retry on 401
        assertEquals("HTTP 401: Unauthorized", authError?.message)
    }

    @Test
    fun providerCatalogDefaults_sortsReadyFirstAndProvidesApiKeyLinks() {
        val readyModel = com.example.data.model.ModelOption(
            providerId = "openai",
            providerName = "OpenAI",
            modelId = "gpt-4o",
            modelName = "gpt-4o",
            isConnected = true,
            supportsReasoning = true
        )
        val unreadyModel = com.example.data.model.ModelOption(
            providerId = "anthropic",
            providerName = "Anthropic",
            modelId = "claude-sonnet-4-20250514",
            modelName = "claude-sonnet-4-20250514",
            isConnected = false,
            supportsReasoning = true
        )
        val readyOpenCode = com.example.data.model.ModelOption(
            providerId = "opencode",
            providerName = "OpenCode (Zen)",
            modelId = "big-pickle",
            modelName = "big-pickle",
            isConnected = true,
            supportsReasoning = true
        )

        val sortedModels = ProviderCatalogDefaults.sortModelsReadyFirst(
            models = listOf(unreadyModel, readyModel, readyOpenCode)
        )
        assertEquals(listOf("opencode", "openai", "anthropic"), sortedModels.map { it.providerId })
        assertTrue(sortedModels.first().isConnected)

        val accounts = listOf(
            SavedProviderAccount(id = "google", name = "Google Gemini", baseUrl = "https://generativelanguage.googleapis.com/v1beta", apiKey = ""),
            SavedProviderAccount(id = "openai", name = "OpenAI", baseUrl = "https://api.openai.com/v1", apiKey = "sk-live"),
            SavedProviderAccount(id = "local_ollama", name = "Ollama", baseUrl = "http://127.0.0.1:11434/v1", apiKey = "", isCustom = true)
        )
        val sortedAccounts = ProviderCatalogDefaults.sortAccountsReadyFirst(accounts)
        assertEquals(listOf("openai", "local_ollama", "google"), sortedAccounts.map { it.id })

        assertEquals("https://opencode.ai/auth", ProviderCatalogDefaults.apiKeyUrlFor("opencode"))
        assertEquals("https://aistudio.google.com/apikey", ProviderCatalogDefaults.apiKeyUrlFor("google"))
        assertEquals("https://console.anthropic.com/settings/keys", ProviderCatalogDefaults.apiKeyUrlFor("anthropic"))
        assertEquals("https://platform.openai.com/api-keys", ProviderCatalogDefaults.apiKeyUrlFor("openai"))
        assertEquals("https://openrouter.ai/keys", ProviderCatalogDefaults.apiKeyUrlFor("openrouter"))
        assertEquals("https://console.groq.com/keys", ProviderCatalogDefaults.apiKeyUrlFor("groq"))
        assertEquals("https://platform.deepseek.com/api_keys", ProviderCatalogDefaults.apiKeyUrlFor("deepseek"))
        assertEquals("https://api.together.xyz", ProviderCatalogDefaults.apiKeyUrlFor("custom_together", "https://api.together.xyz/v1"))
        assertEquals("", ProviderCatalogDefaults.apiKeyUrlFor("custom_local", "http://192.168.1.20:11434/v1"))
    }
}
