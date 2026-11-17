package com.lumalink.g100d.protocol

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

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

    private data class Occupant(val operation: String, val startedNanos: Long)
    @Volatile private var occupant: Occupant? = null
    private val waiters = AtomicInteger(0)

    fun diagnosticState(): String {
        val active = occupant
        val heldMs = active?.let { (System.nanoTime() - it.startedNanos) / 1_000_000 } ?: 0
        return "locked=${cameraMutex.isLocked} owner=${active?.operation ?: "none"} heldMs=$heldMs waiting=${waiters.get()} previews=${foregroundRequests.get()} transfer=$transferActive"
    }

    private suspend fun <T> withAccess(operation: String, block: suspend () -> T): T {
        waiters.incrementAndGet()
        try {
            cameraMutex.lock()
        } finally {
            waiters.decrementAndGet()
        }
        occupant = Occupant(operation, System.nanoTime())
        try {
            return block()
        } finally {
            occupant = null
            cameraMutex.unlock()
        }
    }

    suspend fun <T> withCamera(operation: String = "control", block: suspend () -> T): T = withAccess(operation) {
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
            return withAccess("gallery") {
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
        return withAccess("scan") {
            if (transferActive || foregroundRequests.get() > 0) null else block()
        }
    }
}
