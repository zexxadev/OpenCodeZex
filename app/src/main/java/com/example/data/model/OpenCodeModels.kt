package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class HealthResponse(
    val healthy: Boolean = false,
    val version: String? = null
)

@Serializable
data class TimeRange(
    val created: Long = 0L,
    val updated: Long? = null,
    val completed: Long? = null
)

@Serializable
data class SessionSummary(
    val additions: Int = 0,
    val deletions: Int = 0,
    val files: Int = 0
)

@Serializable
data class OpenCodeSession(
    val id: String,
    val title: String = "",
    val slug: String? = null,
    val version: String? = null,
    val parentID: String? = null,
    val directory: String? = null,
    val time: TimeRange? = null,
    val summary: SessionSummary? = null
) {
    val displayTitle: String
        get() = title.ifBlank { slug ?: "Sesi ${id.take(8)}" }
}

enum class SessionRunStatus {
    IDLE, BUSY, RETRY
}

data class SessionStatusInfo(
    val status: SessionRunStatus = SessionRunStatus.IDLE,
    val attempt: Int? = null,
    val message: String? = null
)

@Serializable
data class CreateSessionRequest(
    val title: String? = null,
    val parentID: String? = null
)

@Serializable
data class UpdateSessionRequest(
    val title: String
)

@Serializable
data class ForkSessionRequest(
    val messageID: String? = null
)

@Serializable
data class ModelReference(
    val providerID: String,
    val modelID: String
)

@Serializable
data class PromptPartInput(
    val type: String = "text",
    val text: String? = null,
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null
)

@Serializable
data class SendPromptRequest(
    val agent: String? = null,
    val model: ModelReference? = null,
    val noReply: Boolean? = null,
    val system: String? = null,
    val parts: List<PromptPartInput>
)

@Serializable
data class SendCommandRequest(
    val command: String,
    val arguments: String = "",
    val agent: String? = null,
    val model: String? = null
)

@Serializable
data class SendShellRequest(
    val agent: String = "build",
    val command: String,
    val model: ModelReference? = null
)

@Serializable
data class PermissionReplyRequest(
    val response: String // "once" | "always" | "reject"
)

@Serializable
data class AuthCredentialRequest(
    val type: String = "api",
    val key: String
)

@Serializable
data class FileDiff(
    val file: String,
    val before: String = "",
    val after: String = "",
    val additions: Int = 0,
    val deletions: Int = 0
)

@Serializable
data class FileNode(
    val name: String,
    val path: String,
    val absolute: String? = null,
    val type: String = "file", // "file" | "directory"
    val ignored: Boolean = false
) {
    val isDirectory: Boolean
        get() = type == "directory"
}

@Serializable
data class FileContentResponse(
    val type: String = "text",
    val content: String = ""
)

@Serializable
data class AgentInfo(
    val name: String,
    val description: String? = null,
    val mode: String? = null,
    val builtIn: Boolean? = null
)

@Serializable
data class CommandInfo(
    val name: String,
    val description: String? = null,
    val template: String? = null
)

@Serializable
data class ProviderCatalog(
    val all: List<ProviderItem> = emptyList(),
    val default: Map<String, String> = emptyMap(),
    val connected: List<String> = emptyList()
)

@Serializable
data class ProviderItem(
    val id: String,
    val name: String = "",
    val models: Map<String, ProviderModelItem> = emptyMap()
) {
    val displayName: String
        get() = name.ifBlank { id }
}

@Serializable
data class ProviderModelItem(
    val id: String = "",
    val name: String = "",
    val reasoning: Boolean = false,
    val toolCall: Boolean = true,
    @SerialName("attachment") val attachment: Boolean = false
) {
    val displayName: String
        get() = name.ifBlank { id }
}

data class ModelOption(
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val modelName: String,
    val isConnected: Boolean,
    val supportsReasoning: Boolean
)

data class GrepMatch(
    val path: String,
    val lineNumber: Int,
    val lineText: String
)

data class PermissionRequestItem(
    val id: String,
    val sessionId: String,
    val title: String,
    val type: String,
    val patternText: String? = null
)

sealed class MessagePart {
    abstract val id: String
    abstract val messageId: String

    data class Text(
        override val id: String,
        override val messageId: String,
        val text: String
    ) : MessagePart()

    data class Reasoning(
        override val id: String,
        override val messageId: String,
        val text: String
    ) : MessagePart()

    data class ToolCall(
        override val id: String,
        override val messageId: String,
        val callId: String,
        val toolName: String,
        val status: String, // pending, running, completed, error
        val title: String?,
        val inputSummary: String,
        val output: String?,
        val error: String?
    ) : MessagePart()

    data class Patch(
        override val id: String,
        override val messageId: String,
        val hash: String,
        val files: List<String>
    ) : MessagePart()

    data class FileAttachment(
        override val id: String,
        override val messageId: String,
        val filename: String,
        val mime: String
    ) : MessagePart()

    data class StepSummary(
        override val id: String,
        override val messageId: String,
        val kind: String,
        val cost: Double? = null,
        val tokensTotal: Int? = null
    ) : MessagePart()
}

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: String, // "user" | "assistant"
    val createdAt: Long,
    val completedAt: Long?,
    val agent: String?,
    val modelId: String?,
    val providerId: String?,
    val errorText: String?,
    val parts: List<MessagePart>
) {
    val isAssistant: Boolean get() = role == "assistant"
    val isStreaming: Boolean get() = isAssistant && completedAt == null && errorText == null
}

sealed interface OpenCodeEvent {
    data object Connected : OpenCodeEvent
    data class SessionStatusChanged(val sessionId: String, val status: SessionStatusInfo) : OpenCodeEvent
    data class SessionUpserted(val session: OpenCodeSession) : OpenCodeEvent
    data class SessionRemoved(val sessionId: String) : OpenCodeEvent
    data class MessageUpserted(val message: ChatMessage) : OpenCodeEvent
    data class PartUpserted(val sessionId: String?, val part: MessagePart, val delta: String? = null) : OpenCodeEvent
    data class PermissionAsked(val permission: PermissionRequestItem) : OpenCodeEvent
    data class PermissionResolved(val sessionId: String, val permissionId: String) : OpenCodeEvent
    data class SessionError(val sessionId: String?, val message: String) : OpenCodeEvent
    data class Other(val type: String, val detail: String? = null) : OpenCodeEvent
}

enum class DiffLineType {
    CONTEXT, ADDED, REMOVED, HUNK
}

data class DiffLine(
    val type: DiffLineType,
    val oldLineNum: Int?,
    val newLineNum: Int?,
    val content: String
)

object OpenCodeJsonParser {
    fun parseMessageWithParts(obj: JsonObject): ChatMessage? {
        val info = obj["info"]?.jsonObject ?: obj
        val id = info.str("id") ?: return null
        val sessionId = info.str("sessionID") ?: info.str("sessionId") ?: ""
        val role = info.str("role") ?: "assistant"
        val timeObj = info["time"] as? JsonObject
        val created = timeObj?.long("created") ?: 0L
        val completed = timeObj?.long("completed")
        val modelObj = info["model"] as? JsonObject
        val modelId = info.str("modelID") ?: modelObj?.str("modelID")
        val providerId = info.str("providerID") ?: modelObj?.str("providerID")
        val agent = info.str("agent")
        val errorObj = info["error"] as? JsonObject
        val errorText = errorObj?.let { err ->
            (err["data"] as? JsonObject)?.str("message")
                ?: err.str("message")
                ?: err.str("name")
        }

        val rawParts = obj["parts"] as? JsonArray
        val parts = rawParts?.mapNotNull { el ->
            (el as? JsonObject)?.let { parsePart(it) }
        } ?: emptyList()

        return ChatMessage(
            id = id,
            sessionId = sessionId,
            role = role,
            createdAt = created,
            completedAt = completed,
            agent = agent,
            modelId = modelId,
            providerId = providerId,
            errorText = errorText,
            parts = parts
        )
    }

    fun parsePart(obj: JsonObject): MessagePart? {
        val id = obj.str("id") ?: return null
        val messageId = obj.str("messageID") ?: obj.str("messageId") ?: ""
        val type = obj.str("type") ?: return null

        return when (type) {
            "text" -> MessagePart.Text(
                id = id,
                messageId = messageId,
                text = obj.str("text") ?: ""
            )

            "reasoning" -> MessagePart.Reasoning(
                id = id,
                messageId = messageId,
                text = obj.str("text") ?: ""
            )

            "tool" -> {
                val stateObj = obj["state"] as? JsonObject
                val status = stateObj?.str("status") ?: "pending"
                val inputObj = stateObj?.get("input") as? JsonObject
                val inputSummary = formatToolInputSummary(inputObj)
                MessagePart.ToolCall(
                    id = id,
                    messageId = messageId,
                    callId = obj.str("callID") ?: id,
                    toolName = obj.str("tool") ?: "tool",
                    status = status,
                    title = stateObj?.str("title"),
                    inputSummary = inputSummary,
                    output = stateObj?.str("output"),
                    error = stateObj?.str("error")
                )
            }

            "patch" -> {
                val files = (obj["files"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?: emptyList()
                MessagePart.Patch(
                    id = id,
                    messageId = messageId,
                    hash = obj.str("hash") ?: "",
                    files = files
                )
            }

            "file" -> MessagePart.FileAttachment(
                id = id,
                messageId = messageId,
                filename = obj.str("filename") ?: obj.str("url") ?: "file",
                mime = obj.str("mime") ?: "text/plain"
            )

            "step-finish" -> {
                val tokensObj = obj["tokens"] as? JsonObject
                val inputTokens = tokensObj?.int("input") ?: 0
                val outputTokens = tokensObj?.int("output") ?: 0
                val cost = (obj["cost"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                MessagePart.StepSummary(
                    id = id,
                    messageId = messageId,
                    kind = type,
                    cost = cost,
                    tokensTotal = if (inputTokens + outputTokens > 0) inputTokens + outputTokens else null
                )
            }

            else -> null
        }
    }

    fun parseSessionStatus(el: JsonElement?): SessionStatusInfo {
        if (el == null) return SessionStatusInfo()
        if (el is JsonPrimitive) {
            return when (el.contentOrNull?.lowercase()) {
                "busy", "running" -> SessionStatusInfo(SessionRunStatus.BUSY)
                "retry" -> SessionStatusInfo(SessionRunStatus.RETRY)
                else -> SessionStatusInfo(SessionRunStatus.IDLE)
            }
        }
        val obj = el as? JsonObject ?: return SessionStatusInfo()
        val type = obj.str("type")?.lowercase() ?: "idle"
        return when (type) {
            "busy", "running" -> SessionStatusInfo(SessionRunStatus.BUSY)
            "retry" -> SessionStatusInfo(
                status = SessionRunStatus.RETRY,
                attempt = obj.int("attempt"),
                message = obj.str("message")
            )
            else -> SessionStatusInfo(SessionRunStatus.IDLE)
        }
    }

    fun parsePermission(obj: JsonObject): PermissionRequestItem? {
        val id = obj.str("id") ?: return null
        val sessionId = obj.str("sessionID") ?: obj.str("sessionId") ?: return null
        val title = obj.str("title") ?: obj.str("permission") ?: "Izin Eksekusi Tool"
        val type = obj.str("type") ?: obj.str("permission") ?: "tool"
        val patternEl = obj["pattern"] ?: obj["patterns"]
        val patternText = when (patternEl) {
            is JsonPrimitive -> patternEl.contentOrNull
            is JsonArray -> patternEl.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ")
            else -> null
        }
        return PermissionRequestItem(
            id = id,
            sessionId = sessionId,
            title = title,
            type = type,
            patternText = patternText
        )
    }

    fun parseGrepMatches(arr: JsonArray): List<GrepMatch> {
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val path = (obj["path"] as? JsonObject)?.str("text")
                ?: obj.str("path")
                ?: return@mapNotNull null
            val lineText = (obj["lines"] as? JsonObject)?.str("text")?.trimEnd('\n', '\r')
                ?: obj.str("lines")
                ?: ""
            val lineNum = obj.int("line_number") ?: obj.int("lineNumber") ?: 1
            GrepMatch(path = path, lineNumber = lineNum, lineText = lineText)
        }
    }

    private fun formatToolInputSummary(input: JsonObject?): String {
        if (input == null || input.isEmpty()) return ""
        val priorityKeys = listOf("command", "filePath", "file_path", "path", "pattern", "query", "url", "description")
        for (key in priorityKeys) {
            val value = (input[key] as? JsonPrimitive)?.contentOrNull
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return input.entries.take(2).joinToString(" · ") { (k, v) ->
            val raw = when (v) {
                is JsonPrimitive -> v.contentOrNull ?: v.toString()
                else -> v.toString()
            }
            "$k: ${raw.take(80)}"
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
}

object DiffComputation {
    fun computeDiffLines(before: String, after: String): List<DiffLine> {
        if (before == after) return emptyList()
        val oldLines = if (before.isEmpty()) emptyList() else before.lines()
        val newLines = if (after.isEmpty()) emptyList() else after.lines()

        // Guard against huge files by summarizing if combined lines exceed 1500
        if (oldLines.size + newLines.size > 1500) {
            val result = mutableListOf<DiffLine>()
            oldLines.take(120).forEachIndexed { idx, line ->
                result.add(DiffLine(DiffLineType.REMOVED, idx + 1, null, line))
            }
            newLines.take(120).forEachIndexed { idx, line ->
                result.add(DiffLine(DiffLineType.ADDED, null, idx + 1, line))
            }
            return result
        }

        val m = oldLines.size
        val n = newLines.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                dp[i][j] = if (oldLines[i] == newLines[j]) {
                    1 + dp[i + 1][j + 1]
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val raw = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < m && j < n) {
            when {
                oldLines[i] == newLines[j] -> {
                    raw.add(DiffLine(DiffLineType.CONTEXT, i + 1, j + 1, oldLines[i]))
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    raw.add(DiffLine(DiffLineType.REMOVED, i + 1, null, oldLines[i]))
                    i++
                }
                else -> {
                    raw.add(DiffLine(DiffLineType.ADDED, null, j + 1, newLines[j]))
                    j++
                }
            }
        }
        while (i < m) {
            raw.add(DiffLine(DiffLineType.REMOVED, i + 1, null, oldLines[i]))
            i++
        }
        while (j < n) {
            raw.add(DiffLine(DiffLineType.ADDED, null, j + 1, newLines[j]))
            j++
        }

        // Filter to changed regions with 2 lines of context
        val changedIndices = raw.indices.filter { raw[it].type != DiffLineType.CONTEXT }
        if (changedIndices.isEmpty()) return emptyList()

        val keep = BooleanArray(raw.size)
        for (idx in changedIndices) {
            val start = maxOf(0, idx - 2)
            val end = minOf(raw.lastIndex, idx + 2)
            for (k in start..end) keep[k] = true
        }

        val compacted = mutableListOf<DiffLine>()
        var prevKept = false
        for (idx in raw.indices) {
            if (keep[idx]) {
                if (!prevKept && compacted.isNotEmpty()) {
                    compacted.add(DiffLine(DiffLineType.HUNK, null, null, "···"))
                }
                compacted.add(raw[idx])
                prevKept = true
            } else {
                prevKept = false
            }
        }
        return compacted
    }
}
