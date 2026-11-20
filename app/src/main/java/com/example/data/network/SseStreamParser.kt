package com.example.data.network

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.InputStreamReader

data class SseFrame(
    val id: String? = null,
    val event: String? = null,
    val data: String,
    val retryMs: Long? = null
)

object SseStreamParser {

    fun parseFlow(body: ResponseBody): Flow<SseFrame> = flow {
        readFrames(body) { frame ->
            emit(frame)
        }
    }

    suspend fun readFrames(
        body: ResponseBody,
        onFrame: suspend (SseFrame) -> Unit
    ) {
        BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
            var currentId: String? = null
            var currentEvent: String? = null
            var currentRetry: Long? = null
            val dataBuffer = StringBuilder()

            while (currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) {
                    if (dataBuffer.isNotEmpty()) {
                        val payload = dataBuffer.toString()
                        dataBuffer.clear()
                        if (payload == "[DONE]") {
                            break
                        }
                        onFrame(
                            SseFrame(
                                id = currentId,
                                event = currentEvent,
                                data = payload,
                                retryMs = currentRetry
                            )
                        )
                        currentEvent = null
                        currentRetry = null
                    }
                    continue
                }

                // Comment / keep-alive line
                if (line.startsWith(":")) continue

                val colonIndex = line.indexOf(':')
                val field = if (colonIndex == -1) line else line.substring(0, colonIndex)
                val rawValue = if (colonIndex == -1) "" else line.substring(colonIndex + 1)
                val value = if (rawValue.startsWith(" ")) rawValue.substring(1) else rawValue

                when (field) {
                    "data" -> {
                        if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                        dataBuffer.append(value)
                    }
                    "event" -> currentEvent = value
                    "id" -> if (!value.contains('\u0000')) currentId = value
                    "retry" -> currentRetry = value.toLongOrNull()
                }
            }

            // Flush trailing frame if stream closed without trailing blank line
            if (dataBuffer.isNotEmpty()) {
                val payload = dataBuffer.toString()
                if (payload != "[DONE]") {
                    onFrame(
                        SseFrame(
                            id = currentId,
                            event = currentEvent,
                            data = payload,
                            retryMs = currentRetry
                        )
                    )
                }
            }
        }
    }
}
