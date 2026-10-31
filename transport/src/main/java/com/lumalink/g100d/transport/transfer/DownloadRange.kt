/* Copyright 2026 Shiguang Transfer contributors. Licensed under Apache-2.0. */
package com.lumalink.g100d.transport.transfer

internal fun validDownloadRange(value: String?, offset: Long, expectedTotal: Long): Boolean {
    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value.orEmpty()) ?: return false
    val start = match.groupValues[1].toLongOrNull() ?: return false
    val end = match.groupValues[2].toLongOrNull() ?: return false
    val total = match.groupValues[3].toLongOrNull() ?: return false
    return start == offset && end == total - 1 && total > offset && (expectedTotal <= 0 || expectedTotal == total)
}
