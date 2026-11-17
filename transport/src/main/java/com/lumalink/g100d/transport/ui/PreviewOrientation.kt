/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

/** EXIF stored-pixel orientation to upright display. Bitmap.createBitmap normalizes
 * transformed bounds; no translation or request for the original file is needed. */
internal fun previewOrientationMatrix(orientation: Int): FloatArray? = when (orientation) {
    2 -> floatArrayOf(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f) // horizontal mirror
    3 -> floatArrayOf(-1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f) // 180
    4 -> floatArrayOf(1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f) // vertical mirror
    5 -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f) // transpose
    6 -> floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f) // 90 clockwise
    7 -> floatArrayOf(0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f) // transverse
    8 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f) // 90 counterclockwise
    else -> null // normal, absent or invalid: never guess
}
