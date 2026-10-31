/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

import com.lumalink.g100d.protocol.CameraFile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal const val UNKNOWN_GALLERY_DATE = "unknown"
/** One metadata request per photo; prefer its JPEG to a much larger RAW resource. */
internal fun dateReadCandidates(files: List<CameraFile>, dates: Map<String, Long>): List<CameraFile> =
    files.asSequence().filter { !it.isDirectory && it.kind in setOf(
        com.lumalink.g100d.protocol.MediaKind.JPEG, com.lumalink.g100d.protocol.MediaKind.RAW,
    ) }.groupBy { it.path.substringBeforeLast('.') }.values.mapNotNull { group ->
        if (group.any { captureDay(it, dates) != null }) null
        else group.firstOrNull { it.kind == com.lumalink.g100d.protocol.MediaKind.JPEG } ?: group.first()
    }

internal fun captureDay(file: CameraFile, exifDates: Map<String, Long>, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
    val seconds = file.capturedAtEpochSeconds ?: exifDates[file.path]
        ?: exifDates[file.path.substringBeforeLast('.') + ".JPG"]
        ?: exifDates[file.path.substringBeforeLast('.') + ".RW2"] ?: return null
    if (seconds <= 0) return null
    return runCatching { Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate() }.getOrNull()
}
internal fun matchesGalleryDate(day: LocalDate?, selected: String?): Boolean = when (selected) {
    null -> true
    UNKNOWN_GALLERY_DATE -> day == null
    else -> day?.toString() == selected
}
