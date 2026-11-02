package com.lumalink.g100d.protocol

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The G100D grants one 50001 file per Browse, and a download consumes that
 * grant. Camera Browse, thumbnail access and transfer therefore share one
 * mutex: a transfer file holds it for the whole reauth + Browse + download,
 * so a gallery prefetch cannot invalidate the active grant mid-file.
 */
object CameraAccessGate {
    @Volatile
    var transferActive: Boolean = false

    private val cameraMutex = Mutex()
    private val foregroundRequests = AtomicInteger(0)

    fun diagnosticState(): String = "locked=${cameraMutex.isLocked} previews=${foregroundRequests.get()} transfer=$transferActive"

    suspend fun <T> withCamera(block: suspend () -> T): T = cameraMutex.withLock {
        block()
    }

    /**
     * Gallery-side operation: skip instead of waiting if transfer owns the
     * camera, and re-check after acquiring the mutex to close the start race.
     */
    suspend fun <T> withGalleryCamera(block: suspend () -> T): T? {
        if (transferActive) return null
        foregroundRequests.incrementAndGet()
        try {
            return cameraMutex.withLock {
                if (transferActive) null else block()
            }
        } finally {
            foregroundRequests.decrementAndGet()
        }
    }

    /** Keep a visible thumbnail pending through a transfer; cancellation drops offscreen work. */
    suspend fun <T> withGalleryCameraWhenAvailable(block: suspend () -> T): T? {
        while (true) {
            var attempted = false
            val value = withGalleryCamera {
                attempted = true
                block()
            }
            if (attempted) return value
            delay(100)
        }
    }

    /** One background page only; queued previews and transfers take precedence. */
    suspend fun <T> withScanCamera(block: suspend () -> T): T? {
        if (transferActive || foregroundRequests.get() > 0) return null
        return cameraMutex.withLock {
            if (transferActive || foregroundRequests.get() > 0) null else block()
        }
    }
}
