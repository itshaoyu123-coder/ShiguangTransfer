/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

internal fun cameraDisplayName(model: String?): String {
    val name = model.orEmpty().trim().replace(Regex("(?i)^LUMIX\\s+"), "")
        .replace(Regex("(?i)^DC-"), "")
    return when (name.uppercase()) {
        "", "LUMIX", "UNKNOWN" -> "松下相机"
        "S5M2", "S52" -> "S5 II"
        "S5M2X" -> "S5 IIX"
        else -> name.take(24)
    }
}
