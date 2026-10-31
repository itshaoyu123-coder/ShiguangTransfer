/* Copyright 2026 Shiguang Transfer contributors. Licensed under Apache-2.0. */
package com.lumalink.g100d.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable fun ShiguangTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF275D4C), onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E9DF), onPrimaryContainer = Color(0xFF17382D),
        background = Color(0xFFF6F4EF), onBackground = Color(0xFF202922),
        surface = Color(0xFFFFFEFA), onSurface = Color(0xFF202922),
        surfaceVariant = Color(0xFFEAEAE2), onSurfaceVariant = Color(0xFF71796F),
        outline = Color(0xFF899286), outlineVariant = Color(0xFFDDE1D5),
    ), typography = LumaTypography, content = content)
}
