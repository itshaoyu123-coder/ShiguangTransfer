/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.protocol

import java.time.*
import java.time.format.DateTimeFormatter

internal fun parseCameraDate(value: String?, localZone: ZoneId = ZoneId.systemDefault()): Long? {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    text.toLongOrNull()?.let { return if (it <= 0) null else if (text.length >= 13) it / 1000 else it }
    runCatching { OffsetDateTime.parse(text).toEpochSecond() }.getOrNull()?.let { return it }
    for (format in listOf(DateTimeFormatter.ISO_LOCAL_DATE_TIME, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))) {
        runCatching { LocalDateTime.parse(text, format).atZone(localZone).toEpochSecond() }.getOrNull()?.let { return it }
    }
    return null
}
