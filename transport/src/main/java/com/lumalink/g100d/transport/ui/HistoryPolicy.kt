/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

import com.lumalink.g100d.core.data.TransferState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class HistoryDay(val date: LocalDate, val items: List<TransferItem>)
internal fun historyTimestamp(item: TransferItem) = if (item.state == TransferState.COMPLETED) item.updatedAt else item.createdAt
internal fun historyDays(items: List<TransferItem>, zone: ZoneId = ZoneId.systemDefault()): List<HistoryDay> =
    items.sortedByDescending(::historyTimestamp)
        .groupBy { Instant.ofEpochMilli(historyTimestamp(it)).atZone(zone).toLocalDate() }
        .map { HistoryDay(it.key, it.value) }
internal fun canDeleteHistory(item: TransferItem) = item.state != TransferState.RUNNING && item.state != TransferState.QUEUED
internal fun labPhotoMime(fileName: String): String? = when (fileName.substringAfterLast('.').lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "rw2" -> "image/x-panasonic-rw2"
    "heif", "heic" -> "image/heif"
    else -> null
}
