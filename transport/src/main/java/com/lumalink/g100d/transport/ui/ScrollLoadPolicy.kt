/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

import kotlin.math.abs

enum class ThumbnailLoadMode { LOAD, HOLD, PAUSE }

/** Position is measured in photo rows, independent of screen density.
 * Separate entry/recovery windows prevent request churn around a threshold. */
internal class ScrollLoadPolicy {
    private var lastPosition: Double? = null
    private var lastTime = 0L
    private var fastSince: Long? = null
    private var slowSince: Long? = null
    private var mode = ThumbnailLoadMode.LOAD

    fun sample(position: Double, now: Long, scrolling: Boolean): ThumbnailLoadMode {
        val previous = lastPosition
        val elapsed = now - lastTime
        lastPosition = position
        lastTime = now
        if (previous == null || elapsed <= 0) return mode
        val speed = if (!scrolling) 0.0 else abs(position - previous) * 1000.0 / elapsed
        when {
            speed > 3.0 -> {
                slowSince = null
                if (fastSince == null) fastSince = now
                mode = if (mode == ThumbnailLoadMode.PAUSE || now - fastSince!! >= 100) ThumbnailLoadMode.PAUSE else ThumbnailLoadMode.HOLD
            }
            speed < 1.0 -> {
                fastSince = null
                if (slowSince == null) slowSince = now
                if (now - slowSince!! >= 150) mode = ThumbnailLoadMode.LOAD
            }
            else -> {
                fastSince = null
                slowSince = null
                if (mode != ThumbnailLoadMode.PAUSE) mode = ThumbnailLoadMode.HOLD
            }
        }
        return mode
    }
}
