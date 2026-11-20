package com.example.data.local

import com.example.data.network.SseFrame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal class GeminiSseAccumulator(
    private val json: Json
) {
    private val textBuf = StringBuilder()
    private val reasoningBuf = StringBuilder()
    private val toolCalls = mutableListOf<LlmToolCallRequest>()

    suspend fun accept(
        frame: SseFrame,
        onTextDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit
    ) {
        if (frame.data.isBlank()) return
        val root = json.parseToJsonElement(frame.data) as? JsonObject ?: return

        (root["error"] as? JsonObject)?.let { err ->
            val msg = err.str("message") ?: frame.data.take(240)
            throw IllegalStateException("Gemini Stream Error: $msg")
        }

        val candidate = root["candidates"]?.jsonArray?.firstOrNull() as? JsonObject ?: return
        val parts = candidate["content"]?.jsonObject?.get("parts") as? JsonArray ?: return

        parts.forEach { el ->
            val obj = el as? JsonObject ?: return@forEach
            val isThought = (obj["thought"] as? JsonPrimitive)?.booleanOrNull == true
            val text = obj.str("text")
            if (!text.isNullOrEmpty()) {
                if (isThought) {
                    reasoningBuf.append(text)
                    onReasoningDelta(text)
                } else {
                    textBuf.append(text)
                    onTextDelta(text)
                }
            }
            (obj["functionCall"] as? JsonObject)?.let { fc ->
                val name = fc.str("name") ?: return@let
                val args = (fc["args"] as? JsonObject) ?: JsonObject(emptyMap())
                toolCalls.add(
                    LlmToolCallRequest(
                        id = "gem_${toolCalls.size}_$name",
                        name = name,
                        arguments = args
                    )
                )
            }
        }
    }

    fun buildResult(): LlmTurnResponse = LlmTurnResponse(
        text = textBuf.toString(),
        reasoning = reasoningBuf.toString().ifBlank { null },
        toolCalls = toolCalls.toList()
    )
}

internal class AnthropicSseAccumulator(
    private val json: Json
) {
    private data class PendingBlock(
        val type: String,
        val id: String,
        val name: String,
        val inputJson: StringBuilder = StringBuilder()
    )

    private val blocks = sortedMapOf<Int, PendingBlock>()
    private val completedTools = mutableListOf<LlmToolCallRequest>()
    private val textBuf = StringBuilder()
    private val reasoningBuf = StringBuilder()

    suspend fun accept(
        frame: SseFrame,
        onTextDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit
    ) {
        if (frame.data.isBlank()) return
        val root = json.parseToJsonElement(frame.data) as? JsonObject ?: return
        val eventType = frame.event ?: root.str("type") ?: return

        when (eventType) {
            "error" -> {
                val errObj = root["error"] as? JsonObject
                val msg = errObj?.str("message") ?: frame.data.take(240)
                throw IllegalStateException("Anthropic Stream Error: $msg")
            }

            "content_block_start" -> {
                val idx = (root["index"] as? JsonPrimitive)?.intOrNull ?: 0
                val cb = root["content_block"] as? JsonObject ?: return
                if (cb.str("type") == "tool_use") {
                    val pending = PendingBlock(
                        type = "tool_use",
                        id = cb.str("id") ?: "tool_$idx",
                        name = cb.str("name") ?: ""
                    )
                    val initialInput = cb["input"] as? JsonObject
                    if (initialInput != null && initialInput.isNotEmpty()) {
                        pending.inputJson.append(initialInput.toString())
                    }
                    blocks[idx] = pending
                }
            }

            "content_block_delta" -> {
                val idx = (root["index"] as? JsonPrimitive)?.intOrNull ?: 0
                val delta = root["delta"] as? JsonObject ?: return
                when (delta.str("type")) {
                    "text_delta" -> {
                        val text = delta.str("text")
                        if (!text.isNullOrEmpty()) {
                            textBuf.append(text)
                            onTextDelta(text)
                        }
                    }

                    "thinking_delta" -> {
                        val thinking = delta.str("thinking")
                        if (!thinking.isNullOrEmpty()) {
                            reasoningBuf.append(thinking)
                            onReasoningDelta(thinking)
                        }
                    }

                    "input_json_delta" -> {
                        val partial = delta.str("partial_json")
                        if (!partial.isNullOrEmpty()) {
                            blocks[idx]?.inputJson?.append(partial)
                        }
                    }
                }
            }

            "content_block_stop" -> {
                val idx = (root["index"] as? JsonPrimitive)?.intOrNull ?: return
                blocks.remove(idx)?.let { finalizeToolBlock(it) }
            }
        }
    }

    private fun finalizeToolBlock(block: PendingBlock) {
        if (block.type == "tool_use" && block.name.isNotBlank()) {
            completedTools.add(
                LlmToolCallRequest(
                    id = block.id,
                    name = block.name,
                    arguments = parseArgumentsSafely(json, block.inputJson.toString())
                )
            )
        }
    }

    fun buildResult(): LlmTurnResponse {
        blocks.values.toList().forEach { finalizeToolBlock(it) }
        blocks.clear()
        return LlmTurnResponse(
            text = textBuf.toString(),
            reasoning = reasoningBuf.toString().ifBlank { null },
            toolCalls = completedTools.toList()
        )
    }
}

internal class OpenAiSseAccumulator(
    private val json: Json
) {
    private data class PendingTool(
        var id: String,
        var name: String,
        val args: StringBuilder = StringBuilder()
    )

    private val toolsByIndex = sortedMapOf<Int, PendingTool>()
    private val textBuf = StringBuilder()
    private val reasoningBuf = StringBuilder()

    suspend fun accept(
        frame: SseFrame,
        onTextDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit
    ) {
        if (frame.data.isBlank() || frame.data == "[DONE]") return
        val root = json.parseToJsonElement(frame.data) as? JsonObject ?: return

        (root["error"] as? JsonObject)?.let { err ->
            val msg = err.str("message") ?: frame.data.take(240)
            throw IllegalStateException("LLM Stream Error: $msg")
        }

        val choice = root["choices"]?.jsonArray?.firstOrNull() as? JsonObject ?: return
        val delta = (choice["delta"] as? JsonObject) ?: (choice["message"] as? JsonObject) ?: return

        val reasoning = delta.str("reasoning_content") ?: delta.str("reasoning")
        if (!reasoning.isNullOrEmpty()) {
            reasoningBuf.append(reasoning)
            onReasoningDelta(reasoning)
        }

        val content = delta.str("content")
        if (!content.isNullOrEmpty()) {
            textBuf.append(content)
            onTextDelta(content)
        }

        (delta["tool_calls"] as? JsonArray)?.forEach { el ->
            val obj = el as? JsonObject ?: return@forEach
            val index = (obj["index"] as? JsonPrimitive)?.intOrNull ?: toolsByIndex.size
            val pending = toolsByIndex.getOrPut(index) {
                PendingTool(id = "call_$index", name = "")
            }
            obj.str("id")?.takeIf { it.isNotBlank() }?.let { pending.id = it }
            val fn = obj["function"] as? JsonObject
            fn?.str("name")?.takeIf { it.isNotBlank() }?.let { pending.name = it }
            fn?.str("arguments")?.let { pending.args.append(it) }
        }
    }

    fun buildResult(): LlmTurnResponse {
        val toolCalls = toolsByIndex.values
            .filter { it.name.isNotBlank() }
            .map { tool ->
                LlmToolCallRequest(
                    id = tool.id,
                    name = tool.name,
                    arguments = parseArgumentsSafely(json, tool.args.toString())
                )
            }
        return LlmTurnResponse(
            text = textBuf.toString(),
            reasoning = reasoningBuf.toString().ifBlank { null },
            toolCalls = toolCalls
        )
    }
}

private fun parseArgumentsSafely(json: Json, raw: String): JsonObject {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return JsonObject(emptyMap())
    return runCatching { json.parseToJsonElement(trimmed).jsonObject }
        .getOrElse { JsonObject(emptyMap()) }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
