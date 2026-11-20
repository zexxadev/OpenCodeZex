package com.example.data.firebase

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val exception = task.exception
        if (exception == null) {
            if (task.isCanceled) {
                cont.cancel()
            } else {
                @Suppress("UNCHECKED_CAST")
                cont.resume(task.result as T)
            }
        } else {
            cont.resumeWithException(exception)
        }
    }
}
