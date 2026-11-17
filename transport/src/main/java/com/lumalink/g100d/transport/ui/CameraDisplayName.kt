/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

internal fun cameraDisplayName(model: String?): String {
    fun clean(value: String) = value.trim().trim('"', '\'', '“', '”', '‘', '’').trim()
    val name = clean(clean(model.orEmpty()).replace(Regex("(?i)^LUMIX\\s+"), "")
        .replace(Regex("(?i)^DC-"), ""))
    return when (name.uppercase()) {
        "", "LUMIX", "UNKNOWN" -> "松下相机"
        "S5M2", "S52" -> "S5 II"
        "S5M2X" -> "S5 IIX"
        else -> name.take(24)
    }
}

internal fun cameraDisplayLabel(model: String?): String = cameraDisplayName(model).let {
    if (it == "松下相机") it else "LUMIX $it"
}
