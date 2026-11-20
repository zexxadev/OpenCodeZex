package com.example.data.network

import com.example.data.model.OpenCodeEvent
import com.example.data.model.OpenCodeJsonParser
import com.example.data.model.OpenCodeSession
import com.example.data.model.ServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

class OpenCodeSseClient(
    private val json: Json
) {
    fun observeEvents(
        sseApi: OpenCodeApiService,
        profile: ServerProfile
    ): Flow<OpenCodeEvent> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            val baseUrl = profile.normalizedUrl
            if (baseUrl.isBlank()) return@launch

            var retryDelayMs = 1500L
            var lastEventId: String? = null
            var useGlobalEndpoint = false

            while (isActive) {
                try {
                    val response = if (useGlobalEndpoint) {
                        sseApi.streamGlobalEvents(lastEventId = lastEventId)
                    } else {
                        sseApi.streamEvents(lastEventId = lastEventId)
                    }

                    if (!response.isSuccessful) {
                        if (response.code() == 404 && !useGlobalEndpoint) {
                            useGlobalEndpoint = true
                            continue
                        }
                        trySend(
                            OpenCodeEvent.SessionError(
                                sessionId = null,
                                message = "SSE HTTP ${response.code()}"
                            )
                        )
                    } else {
                        retryDelayMs = 1500L
                        trySend(OpenCodeEvent.Connected)
                        response.body()?.use { body ->
                            SseStreamParser.readFrames(body) { frame ->
                                if (!frame.id.isNullOrBlank()) {
                                    lastEventId = frame.id
                                }
                                if (frame.retryMs != null && frame.retryMs in 200L..60_000L) {
                                    retryDelayMs = frame.retryMs
                                }
                                parseSsePayload(frame.data)?.let { event ->
                                    trySend(event)
                                }
                            }
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    // Reconnect silently on transient network drop while active
                }

                if (isActive) {
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(10_000L)
                }
            }
        }

        awaitClose {
            job.cancel()
        }
    }

    internal fun parseSsePayload(rawJson: String): OpenCodeEvent? {
        if (rawJson.isBlank() || rawJson == "[DONE]") return null
        return try {
            val root = json.parseToJsonElement(rawJson).jsonObject
            // /global/event wraps event in "payload", /event sends directly
            val eventObj = (root["payload"] as? JsonObject) ?: root
            val type = (eventObj["type"] as? JsonPrimitive)?.contentOrNull ?: return null
            val props = (eventObj["properties"] as? JsonObject) ?: JsonObject(emptyMap())

            when (type) {
                "server.connected", "server.heartbeat" -> OpenCodeEvent.Connected

                "session.status" -> {
                    val sessionId = props.str("sessionID") ?: props.str("sessionId") ?: return null
                    val statusEl = props["status"]
                    OpenCodeEvent.SessionStatusChanged(
                        sessionId = sessionId,
                        status = OpenCodeJsonParser.parseSessionStatus(statusEl)
                    )
                }

                "session.created", "session.updated" -> {
                    val info = (props["info"] as? JsonObject) ?: props
                    val session = json.decodeFromJsonElement(OpenCodeSession.serializer(), info)
                    OpenCodeEvent.SessionUpserted(session)
                }

                "session.deleted" -> {
                    val info = props["info"] as? JsonObject
                    val sessionId = props.str("sessionID") ?: info?.str("id") ?: return null
                    OpenCodeEvent.SessionRemoved(sessionId)
                }

                "message.updated" -> {
                    val info = (props["info"] as? JsonObject) ?: props
                    val wrapped = JsonObject(mapOf("info" to info))
                    val msg = OpenCodeJsonParser.parseMessageWithParts(wrapped) ?: return null
                    OpenCodeEvent.MessageUpserted(msg)
                }

                "message.part.updated" -> {
                    val partObj = (props["part"] as? JsonObject) ?: props
                    val part = OpenCodeJsonParser.parsePart(partObj) ?: return null
                    val sessionId = partObj.str("sessionID") ?: props.str("sessionID")
                    val delta = props.str("delta")
                    OpenCodeEvent.PartUpserted(sessionId = sessionId, part = part, delta = delta)
                }

                "permission.updated", "permission.asked" -> {
                    val permObj = (props["permission"] as? JsonObject) ?: props
                    val perm = OpenCodeJsonParser.parsePermission(permObj) ?: return null
                    OpenCodeEvent.PermissionAsked(perm)
                }

                "permission.replied" -> {
                    val sessionId = props.str("sessionID") ?: ""
                    val permId = props.str("permissionID") ?: props.str("requestID") ?: ""
                    OpenCodeEvent.PermissionResolved(sessionId, permId)
                }

                "session.error" -> {
                    val sessionId = props.str("sessionID")
                    val errObj = props["error"] as? JsonObject
                    val msg = (errObj?.get("data") as? JsonObject)?.str("message")
                        ?: errObj?.str("message")
                        ?: errObj?.str("name")
                        ?: "Terjadi kesalahan pada sesi OpenCode"
                    OpenCodeEvent.SessionError(sessionId, msg)
                }

                else -> OpenCodeEvent.Other(type)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
