package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class ConnectionCancellationTest {
    private class BlockingConnection : HttpURLConnection(URL("http://127.0.0.1/")) {
        val disconnected = AtomicBoolean(false)
        val released = CountDownLatch(1)
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected.set(true); released.countDown() }
    }
    @Test fun cancellationClosesBlockingConnectionBeforeReleasingCameraGate() = runBlocking {
        val connection = BlockingConnection()
        val started = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.IO) {
            CameraAccessGate.withGalleryCamera {
                withConnectionCancellation(connection) {
                    started.complete(Unit)
                    check(connection.released.await(10, TimeUnit.SECONDS))
                }
            }
        }
        started.await()
        job.cancel()
        withTimeout(2000) { job.join(); CameraAccessGate.withCamera { } }
        assertThat(connection.disconnected.get()).isTrue()
    }
    @Test fun successfulHeaderReadLeavesConnectionOpenForBody() = runBlocking {
        val connection = BlockingConnection()
        assertThat(withConnectionCancellation(connection) { 200 }).isEqualTo(200)
        assertThat(connection.disconnected.get()).isFalse()
    }
}
