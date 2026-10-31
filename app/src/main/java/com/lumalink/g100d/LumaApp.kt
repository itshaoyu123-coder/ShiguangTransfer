/* Modified 2026-10-30 by Shiguang Transfer contributors, Apache-2.0.
 * Replaces the upstream navigation shell; retains attributed ViewModels/services. */
package com.lumalink.g100d

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumalink.g100d.core.settings.AppSettings
import com.lumalink.g100d.core.settings.ThemeMode
import com.lumalink.g100d.transport.ui.ConnectViewModel
import com.lumalink.g100d.transport.ui.TransfersViewModel
import com.lumalink.g100d.transport.ui.ShiguangBrowser

@Composable
@Suppress("UNUSED_PARAMETER")
fun LumaApp(container: AppContainer, settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit, onDynamicColorChange: (Boolean) -> Unit) {
    val camera = container.cameraViewModel
    val transfers: TransfersViewModel = viewModel(factory = TransfersViewModel.factory(
        container.transferRepository, container.applicationContext))
    ShiguangBrowser(camera, transfers)
}
