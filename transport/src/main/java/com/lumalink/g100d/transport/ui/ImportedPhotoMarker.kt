/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

import com.lumalink.g100d.core.data.TransferState
import com.lumalink.g100d.protocol.CameraFile

/** Conservative resource identity: never infer a RAW import from its JPEG twin. */
data class ImportedPhotoKey(val cameraPath: String, val fileName: String, val sizeBytes: Long)

internal fun completedImportKeys(items: List<TransferItem>): Set<ImportedPhotoKey> = items.asSequence()
    .filter { it.state == TransferState.COMPLETED && !it.localUri.isNullOrBlank() && it.cameraPath.isNotBlank() }
    .map { ImportedPhotoKey(it.cameraPath, it.fileName, it.sizeBytes) }.toSet()

internal fun importKey(file: CameraFile) = ImportedPhotoKey(file.path, file.name, file.sizeBytes)
