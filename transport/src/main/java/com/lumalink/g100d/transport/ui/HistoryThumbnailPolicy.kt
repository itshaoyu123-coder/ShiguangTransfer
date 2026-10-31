/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

import com.lumalink.g100d.core.data.TransferState
import java.net.URI

internal data class HistoryThumbnailKey(val uri: String, val version: Long)
internal fun historyThumbnailKey(item: TransferItem): HistoryThumbnailKey? {
    if (item.state != TransferState.COMPLETED) return null
    val value = item.localUri?.takeIf { it.isNotBlank() } ?: return null
    val scheme = runCatching { URI(value).scheme }.getOrNull()
    if (scheme != "content" && scheme != "file") return null
    return HistoryThumbnailKey(value, item.updatedAt)
}
internal fun historyThumbnailSize(width: Int, height: Int, maxEdge: Int = 192): Pair<Int, Int> {
    require(width > 0 && height > 0 && maxEdge > 0)
    val ratio = minOf(1.0, maxEdge.toDouble() / maxOf(width, height))
    return maxOf(1, (width * ratio).toInt()) to maxOf(1, (height * ratio).toInt())
}
