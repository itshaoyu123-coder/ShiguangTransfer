/* Modified 2026-10-30 by Shiguang Transfer contributors; derived from LumaLink progressive.3, Apache-2.0. */
package com.lumalink.g100d

import android.app.Application
import com.lumalink.g100d.core.data.TransferRepository
import com.lumalink.g100d.transport.session.CameraSession
import com.lumalink.g100d.transport.transfer.TransferServiceHost

class LumaLinkApplication : Application(), TransferServiceHost, com.lumalink.g100d.transport.scan.ScanServiceHost {
    override val scanCamera get() = container.cameraViewModel
    val container: AppContainer by lazy { AppContainer(this) }

    override val transferRepository: TransferRepository
        get() = container.transferRepository

    override val sessionProvider: () -> CameraSession?
        get() = { container.connectionManager.session.value }
}
