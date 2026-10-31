package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

class CameraAccessGateTest {
    @After fun reset() { CameraAccessGate.transferActive = false }

    @Test fun scanYieldsToQueuedPreviewAndCancelledPreviewDoesNotBlockFutureScans() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val holder = launch(start = CoroutineStart.UNDISPATCHED) {
            CameraAccessGate.withCamera { release.await() }
        }
        val preview = launch(start = CoroutineStart.UNDISPATCHED) {
            CameraAccessGate.withGalleryCamera { "preview" }
        }
        assertThat(CameraAccessGate.withScanCamera { "scan" }).isNull()
        preview.cancelAndJoin()
        release.complete(Unit)
        holder.join()
        assertThat(CameraAccessGate.withScanCamera { "scan" }).isEqualTo("scan")
    }

    @Test fun visibleThumbnailWaitsForTransferAndResumesWithoutAnotherUiRequest() = runBlocking {
        CameraAccessGate.transferActive = true
        var fetched = false
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            CameraAccessGate.withGalleryCameraWhenAvailable { fetched = true }
        }
        assertThat(fetched).isFalse()
        CameraAccessGate.transferActive = false
        pending.join()
        assertThat(fetched).isTrue()
    }

    @Test fun scansAndPreviewsDoNotTouchCameraWhileTransferActive() = runBlocking {
        CameraAccessGate.transferActive = true
        var cameraCalls = 0
        assertThat(CameraAccessGate.withScanCamera { cameraCalls++ }).isNull()
        assertThat(CameraAccessGate.withGalleryCamera { cameraCalls++ }).isNull()
        assertThat(cameraCalls).isEqualTo(0)
    }
}
