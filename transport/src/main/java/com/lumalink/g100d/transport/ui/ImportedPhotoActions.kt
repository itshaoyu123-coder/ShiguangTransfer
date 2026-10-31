/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal const val LUMIX_LAB_PACKAGE = "com.panasonic.jp.lumixlab"
internal enum class LabResult { EDIT_REQUESTED, MANUAL_IMPORT, NOT_INSTALLED, FAILED, SINGLE_PHOTO_REQUIRED }

internal fun importedContentUri(context: Context, value: String): Uri {
    val uri = Uri.parse(value)
    return when (uri.scheme) {
        "content" -> uri
        "file" -> {
            val file = File(requireNotNull(uri.path))
            require(file.isFile) { "文件已被移走" }
            FileProvider.getUriForFile(context, context.packageName + ".files", file)
        }
        else -> error("不支持的手机文件地址")
    }
}

/** A successful app launch does not establish that its editor accepted a photo. */
internal fun sendToLumixLab(context: Context, items: List<TransferItem>): LabResult {
    val manager = context.packageManager
    val packageInfo = runCatching { manager.getPackageInfo(LUMIX_LAB_PACKAGE, android.content.pm.PackageManager.GET_ACTIVITIES) }.getOrNull()
        ?: return LabResult.NOT_INSTALLED
    com.lumalink.g100d.core.diagnostics.Diagnostics.info("lab-handoff version=${packageInfo.versionName} exported=${packageInfo.activities?.filter { it.exported }?.map { it.name }}")
    if (items.size != 1) return LabResult.SINGLE_PHOTO_REQUIRED
    return runCatching {
        val item = items.single()
        val mime = requireNotNull(labPhotoMime(item.fileName))
        val uri = importedContentUri(context, requireNotNull(item.localUri))
        val clip = ClipData.newUri(context.contentResolver, "拾光导入照片", uri)
        val edit = Intent(Intent.ACTION_EDIT).setDataAndType(uri, mime)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        val share = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
        var editor: android.content.ComponentName? = null
        for (intent in listOf(edit, view, share)) {
            intent.setPackage(LUMIX_LAB_PACKAGE)
            val matches = manager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo }.filter { it.exported }
            com.lumalink.g100d.core.diagnostics.Diagnostics.info("lab-handoff action=${intent.action} mime=$mime matches=${matches.map { it.name }}")
            if (intent.action == Intent.ACTION_EDIT) {
                val available = matches.firstOrNull { info ->
                    info.permission.isNullOrBlank() || context.checkSelfPermission(info.permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (available != null) editor = android.content.ComponentName(available.packageName, available.name)
            }
        }
        if (editor == null) return LabResult.MANUAL_IMPORT
        edit.component = editor
        edit.clipData = clip
        edit.putExtra(Intent.EXTRA_STREAM, uri)
        edit.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(edit)
        LabResult.EDIT_REQUESTED
    }.onFailure { com.lumalink.g100d.core.diagnostics.Diagnostics.warn("lab-handoff failed=${it.javaClass.simpleName}: ${it.message}") }.getOrDefault(LabResult.FAILED)
}
internal fun launchLumixLab(context: Context): Boolean = runCatching {
    context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(LUMIX_LAB_PACKAGE)))
    true
}.getOrDefault(false)
