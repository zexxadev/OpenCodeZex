package com.example.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.IOException

object AiRetryPolicy {
    const val ENDPOINT_TIMEOUT_MS: Long = 30_000L
    const val MAX_RETRIES: Int = 3

    class NonRetryableAiException(
        message: String,
        cause: Throwable? = null
    ) : IllegalStateException(message, cause)

    suspend fun <T> executeWithTimeoutAndRetry(
        timeoutMs: Long = ENDPOINT_TIMEOUT_MS,
        maxRetries: Int = MAX_RETRIES,
        retryDelayMs: (retryAttempt: Int) -> Long = { retryAttempt -> 600L * retryAttempt },
        isRetryable: (Throwable) -> Boolean = ::defaultIsRetryable,
        onRetry: suspend (retryAttempt: Int, maxRetries: Int, error: Throwable) -> Unit = { _, _, _ -> },
        block: suspend (attemptIndex: Int) -> T
    ): T {
        var lastError: Throwable? = null
        val totalAttempts = 1 + maxRetries.coerceAtLeast(0)
        for (attemptIndex in 0 until totalAttempts) {
            try {
                return withTimeout(timeoutMs) {
                    block(attemptIndex)
                }
            } catch (t: Throwable) {
                if (t is CancellationException && t !is TimeoutCancellationException) {
                    throw t
                }
                val normalized = if (t is TimeoutCancellationException) {
                    IllegalStateException(
                        "Timeout: endpoint AI tidak merespons dalam ${timeoutMs / 1000} detik.",
                        t
                    )
                } else {
                    t
                }
                lastError = normalized
                val nextRetryNumber = attemptIndex + 1
                if (attemptIndex >= maxRetries || !isRetryable(normalized)) {
                    throw normalized
                }
                onRetry(nextRetryNumber, maxRetries, normalized)
                val waitMs = retryDelayMs(nextRetryNumber)
                if (waitMs > 0L) {
                    delay(waitMs)
                }
            }
        }
        throw lastError ?: IllegalStateException("Gagal terhubung ke endpoint AI.")
    }

    fun defaultIsRetryable(t: Throwable): Boolean {
        if (t is NonRetryableAiException) return false
        if (t is CancellationException && t !is TimeoutCancellationException) return false
        if (t is TimeoutCancellationException || t is IOException) return true
        val msg = t.message.orEmpty()
        if (msg.contains("API Key", ignoreCase = true) && msg.contains("belum diisi", ignoreCase = true)) {
            return false
        }
        if (msg.startsWith("HTTP 401:") || msg.startsWith("HTTP 403:")) {
            return false
        }
        return true
    }
}
