package com.lumalink.g100d.transport.ui

internal data class ScanNotice(val title: String, val body: String, val fatal: Boolean)

/** A partial scan failure does not invalidate the files already loaded. */
internal fun scanNotice(
    loadedCount: Int,
    scanning: Boolean,
    complete: Boolean,
    error: String?,
    warning: String?,
): ScanNotice? {
    val issue = (if (complete) warning else error ?: warning) ?: return null
    if (loadedCount == 0 && error != null && !complete) {
        return ScanNotice("读取失败", issue, true)
    }
    if (loadedCount > 0 && !scanning && !complete) {
        return ScanNotice(
            "后台扫描暂停",
            "已保留 $loadedCount 个文件，可先使用已加载的内容。其余照片尚未读完；点击刷新可重新读取。",
            false,
        )
    }
    return ScanNotice("读取提示", issue, false)
}
