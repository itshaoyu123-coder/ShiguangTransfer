/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.ui.theme

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Opaque RGB only: invalid or alpha-bearing input never reaches the theme. */
fun parseGradientHex(text: String): Long? {
    val value = text.trim().removePrefix("#")
    if (!Regex("[0-9a-fA-F]{6}").matches(value)) return null
    return 0xFF000000L or (value.toLongOrNull(16) ?: return null)
}
fun gradientHex(color: Long): String = "#%06X".format(java.util.Locale.ROOT, color and 0xFFFFFF)

data class GradientPalette(val id: String, val name: String, val top: Long, val middle: Long, val bottom: Long) {
    // Keep arbitrary custom colours light enough for the fixed dark foreground.
    fun colors(): List<Color> = listOf(top, middle, bottom).map { color ->
        val c = Color(color)
        if (c.luminance() >= .58f) c else {
            fun lighten(weight: Float) = Color(1f - (1f - c.red) * weight,
                1f - (1f - c.green) * weight, 1f - (1f - c.blue) * weight)
            var low = 0f
            var high = 1f
            repeat(12) {
                val mid = (low + high) / 2f
                if (lighten(mid).luminance() >= .58f) low = mid else high = mid
            }
            lighten(low)
        }
    }
}
val GradientPresets = listOf(
    GradientPalette("sea", "海岸晨雾", 0xFFB4D3D1, 0xFFF3EFE6, 0xFFE6C6AA),
    GradientPalette("cloud", "云间蓝调", 0xFFBCD1E8, 0xFFF2F3F5, 0xFFD8D0E7),
    GradientPalette("sand", "暖砂日光", 0xFFE1D6C5, 0xFFF6F0E6, 0xFFE8C1A9),
    GradientPalette("forest", "林间微光", 0xFFC2D6C8, 0xFFF2F0E6, 0xFFDED1AD),
)
val LocalGradientPalette = staticCompositionLocalOf { GradientPresets.first() }
fun appearancePreferences(context: Context) = context.applicationContext.getSharedPreferences("shiguang_appearance", Context.MODE_PRIVATE)
fun readGradientPalette(context: Context): GradientPalette {
    val prefs = appearancePreferences(context)
    val id = prefs.getString("palette", "sea")
    if (id != "custom") return GradientPresets.firstOrNull { it.id == id } ?: GradientPresets.first()
    val fallback = GradientPresets.first()
    return GradientPalette("custom", "自定义", parseGradientHex(prefs.getString("top", "").orEmpty()) ?: fallback.top,
        parseGradientHex(prefs.getString("middle", "").orEmpty()) ?: fallback.middle,
        parseGradientHex(prefs.getString("bottom", "").orEmpty()) ?: fallback.bottom)
}
fun saveGradientPalette(context: Context, palette: GradientPalette) {
    appearancePreferences(context).edit().putString("palette", palette.id)
        .putString("top", gradientHex(palette.top)).putString("middle", gradientHex(palette.middle))
        .putString("bottom", gradientHex(palette.bottom)).apply()
}
