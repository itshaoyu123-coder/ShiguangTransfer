/* Copyright 2026 Shiguang Transfer contributors. Licensed under Apache-2.0. */
package com.lumalink.g100d.transport.ui

import com.lumalink.g100d.protocol.CameraFile
import com.lumalink.g100d.protocol.MediaKind

internal fun enhancesPreview(file: CameraFile, forceOriginal: Boolean): Boolean =
    forceOriginal || file.kind == MediaKind.JPEG

internal fun qualityPreviewFile(file: CameraFile, forceOriginal: Boolean): CameraFile {
    val large = file.largePreviewPath
    return if (!forceOriginal && file.kind == MediaKind.JPEG && large != null)
        file.copy(path = large, sizeBytes = 0) else file
}

internal enum class PreviewPlan { CACHED_QUICK, CACHED_ORIGINAL, FETCH_QUICK, FETCH_ORIGINAL }
internal fun previewPlan(originalRequested: Boolean, quickCached: Boolean, originalCached: Boolean): PreviewPlan = when {
    originalCached -> PreviewPlan.CACHED_ORIGINAL
    !originalRequested && quickCached -> PreviewPlan.CACHED_QUICK
    originalRequested -> PreviewPlan.FETCH_ORIGINAL
    else -> PreviewPlan.FETCH_QUICK
}

/** A RAW/JPG pair usually advertises the same thumbnail. Cache by that URL, not by the original. */
internal fun quickPreviewKey(file: CameraFile): String {
    file.thumbnailPath?.let { return it }
    val name = file.path.substringAfterLast('/').substringBeforeLast('.')
    val leaf = (if (name.startsWith("DO")) "DT" + name.substring(2) else name) + ".JPG"
    return file.path.substringBeforeLast('/') + "/" + leaf
}

internal class SharedPreviewCache<T>(private val capacity: Int) {
    private val items = object : LinkedHashMap<String, T>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?): Boolean = size > capacity
    }
    @Synchronized fun get(key: String): T? = items[key]
    @Synchronized fun put(key: String, value: T) { items[key] = value }
    @Synchronized fun clear() { items.clear() }
}
