/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.protocol

import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** HttpURLConnection blocking reads do not observe coroutine cancellation by themselves. */
suspend fun <T> withConnectionCancellation(connection: HttpURLConnection, block: suspend () -> T): T = coroutineScope {
    val finished = AtomicBoolean(false)
    val closer = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() }
        finally { if (!finished.get()) runCatching { connection.disconnect() } }
    }
    try { block() } finally { finished.set(true); closer.cancel() }
}
