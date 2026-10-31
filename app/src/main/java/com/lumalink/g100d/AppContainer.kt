/* Modified 2026-10-30 by Shiguang Transfer contributors; derived from LumaLink progressive.3, Apache-2.0. */
package com.lumalink.g100d

import android.content.Context
import com.lumalink.g100d.core.data.LumaLinkDatabase
import com.lumalink.g100d.core.data.TransferRepository
import com.lumalink.g100d.core.settings.SettingsRepository
import com.lumalink.g100d.transport.session.ConnectionManager
import com.lumalink.g100d.transport.session.WifiScanner

class AppContainer(context: Context) {
    private val cameraStore = androidx.lifecycle.ViewModelStore()
    val cameraViewModel: com.lumalink.g100d.transport.ui.ConnectViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(cameraStore, com.lumalink.g100d.transport.ui.ConnectViewModel.factory(
            connectionManager, wifiScanner, transferRepository, applicationContext))[com.lumalink.g100d.transport.ui.ConnectViewModel::class.java]
    }
    val applicationContext: Context = context.applicationContext

    val settingsRepository: SettingsRepository = SettingsRepository(applicationContext)

    val connectionManager: ConnectionManager by lazy { ConnectionManager(applicationContext) }

    val wifiScanner: WifiScanner by lazy { WifiScanner(applicationContext) }

    val database: LumaLinkDatabase by lazy { LumaLinkDatabase.create(applicationContext) }

    val transferRepository: TransferRepository by lazy {
        TransferRepository(database.transferDao())
    }
}
