/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.protocol

import java.time.ZoneId
import java.io.InputStream

data class CaptureDateHeaderResult(val date: Long?, val bytesRead: Int)

/** Stops as soon as a complete EXIF date is available, even if HTTP ignores Range. */
fun readCaptureDatePrefix(input: InputStream, checkActive: () -> Unit = {}): CaptureDateHeaderResult {
    val limit = 128 * 1024
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(2048)
    while (out.size() < limit) {
        checkActive()
        val count = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (count <= 0) break
        out.write(buffer, 0, count)
        captureDateFromHeader(out.toByteArray())?.let { return CaptureDateHeaderResult(it, out.size()) }
    }
    return CaptureDateHeaderResult(null, out.size())
}

/** Reads only genuine EXIF dates from a bounded JPEG/TIFF prefix, including RW2.
 * Never guesses a date from a filename, HTTP response or incomplete field. */
fun captureDateFromHeader(bytes: ByteArray, zone: ZoneId = ZoneId.systemDefault()): Long? {
    fun u8(p: Int) = bytes[p].toInt() and 255
    fun tiff(base: Int, end: Int): Long? {
        val availableEnd = minOf(end, bytes.size)
        if (base < 0 || availableEnd - base < 8) return null
        val little = when (String(bytes, base, 2, Charsets.US_ASCII)) { "II" -> true; "MM" -> false; else -> return null }
        fun number(p: Int, n: Int): Long? {
            if (p < base || p.toLong() + n > availableEnd) return null
            var result = 0L
            for (i in 0 until n) result = result or (u8(p + i).toLong() shl (8 * if (little) i else n - 1 - i))
            return result
        }
        val magic = number(base + 2, 2)
        if (magic != 42L && magic != 85L) return null
        fun offset(value: Long): Int? = (base.toLong() + value).takeIf { value >= 8 && it < end }?.toInt()
        var original: Long? = null
        var digitized: Long? = null
        var modified: Long? = null
        fun readIfd(p: Int): Int? {
            val count = number(p, 2)?.toInt() ?: return null
            if (count > 1024 || p.toLong() + 2 + count * 12L > availableEnd) return null
            var exif: Int? = null
            repeat(count) { i ->
                val entry = p + 2 + i * 12
                val tag = number(entry, 2)
                val type = number(entry + 2, 2)
                val length = number(entry + 4, 4) ?: return@repeat
                if (tag == 0x8769L && type == 4L && length == 1L) exif = offset(number(entry + 8, 4) ?: 0)
                if (tag !in listOf(0x9003L, 0x9004L, 0x132L) || type != 2L || length !in 19L..32L) return@repeat
                val start = offset(number(entry + 8, 4) ?: 0) ?: return@repeat
                if (start.toLong() + length > availableEnd) return@repeat
                val text = String(bytes, start, length.toInt(), Charsets.US_ASCII).trimEnd('\u0000', ' ')
                val date = parseCameraDate(text, zone) ?: return@repeat
                when (tag) { 0x9003L -> original = date; 0x9004L -> digitized = date; 0x132L -> modified = date }
            }
            return exif
        }
        val first = offset(number(base + 4, 4) ?: return null) ?: return null
        // Only IFD0 and its Exif IFD: no following thumbnails, cycles or arbitrary traversal.
        readIfd(first)?.let { readIfd(it) }
        // An Original date is authoritative as soon as its complete value arrives.
        // Do not prematurely choose a modification date while the original may
        // still be in an unread part of this EXIF segment.
        return original ?: if (end <= bytes.size) digitized ?: modified else null
    }
    if (bytes.size < 8) return null
    if (u8(0) != 255 || u8(1) != 216) return tiff(0, bytes.size)
    var p = 2
    while (p + 4 <= bytes.size) {
        if (u8(p) != 255) return null
        while (p < bytes.size && u8(p) == 255) p++
        if (p >= bytes.size) return null
        val marker = u8(p++)
        if (marker == 0xda || marker == 0xd9) return null
        if (marker == 1 || marker in 0xd0..0xd7) continue
        if (p + 2 > bytes.size) return null
        val length = (u8(p) shl 8) or u8(p + 1)
        if (length < 2) return null
        if (marker == 0xe1 && length >= 8 && p + 8 <= bytes.size && String(bytes, p + 2, 6, Charsets.US_ASCII) == "Exif\u0000\u0000") {
            tiff(p + 8, p + length)?.let { return it }
        }
        if (p.toLong() + length > bytes.size) return null
        p += length
    }
    return null
}
