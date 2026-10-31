package com.lumalink.g100d.protocol

/** Immutable scan snapshot. Files are usable before [complete] becomes true. */
data class CameraBrowseProgress(
    val files: List<CameraFile>,
    /** Total Browse objects, not the number of JPG/RAW resource files. */
    val total: Int?,
    val scannedCount: Int,
    val complete: Boolean = false,
    val warning: String? = null,
)
