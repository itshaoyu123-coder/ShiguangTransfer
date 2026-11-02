package com.lumalink.g100d.transport.transfer

/** Recovery of grants/ranges happens inside one attempt. Do not blindly repeat
 * missing files, rejected access or full local storage after that recovery. */
internal fun shouldRetryDownload(error: String?, retries: Int): Boolean {
    if (retries >= 2) return false
    val message = error.orEmpty()
    if (listOf("ENOSPC", "No space left", "空间不足").any { message.contains(it, ignoreCase = true) }) return false
    val status = Regex("HTTP\\s+(\\d{3})").find(message)?.groupValues?.get(1)?.toIntOrNull()
    return status == null || status in setOf(408, 416, 429) || status in 500..599
}
