/* Copyright 2026 Shiguang Transfer contributors. Licensed under Apache-2.0. */
package com.lumalink.g100d.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable fun ShiguangTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var palette by remember(context.applicationContext) { mutableStateOf(readGradientPalette(context)) }
    DisposableEffect(context.applicationContext) {
        val prefs = appearancePreferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> palette = readGradientPalette(context) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
    CompositionLocalProvider(LocalGradientPalette provides palette) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF24585A), onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E9DF), onPrimaryContainer = Color(0xFF17382D),
        background = Color(0xFFF6F4EF), onBackground = Color(0xFF1B3435),
        surface = Color(0xFFFFFEFA), onSurface = Color(0xFF1B3435),
        surfaceVariant = Color(0xFFEAEAE2), onSurfaceVariant = Color(0xFF405657),
        outline = Color(0xFF899286), outlineVariant = Color(0xFFDDE1D5),
    ), typography = LumaTypography, content = content)
    }
}
