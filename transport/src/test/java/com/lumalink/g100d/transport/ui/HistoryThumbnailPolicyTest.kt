package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import com.lumalink.g100d.core.data.TransferState
import org.junit.Test

class HistoryThumbnailPolicyTest {
    private fun item(state: TransferState = TransferState.COMPLETED, uri: String? = "content://media/1") =
        TransferItem("1", "a.JPG", 100, 100, 0, state, null, uri, "image/jpeg", 10)
    @Test fun onlyFinishedLocalFilesAreEligible() {
        assertThat(historyThumbnailKey(item())).isNotNull()
        assertThat(historyThumbnailKey(item(uri = "file:///private/a.JPG"))).isNotNull()
        assertThat(historyThumbnailKey(item(uri = "http://192.168.54.1:50001/DO1.JPG"))).isNull()
        assertThat(historyThumbnailKey(item(uri = null))).isNull()
        assertThat(historyThumbnailKey(item(TransferState.RUNNING))).isNull()
        assertThat(historyThumbnailKey(item(TransferState.QUEUED))).isNull()
        assertThat(historyThumbnailKey(item(TransferState.FAILED))).isNull()
    }
    @Test fun localFileVersionInvalidatesCachedThumbnail() {
        assertThat(historyThumbnailKey(item())).isNotEqualTo(historyThumbnailKey(item().copy(updatedAt = 11)))
    }
    @Test fun largeImagesStaySmallWithTheirAspectRatio() {
        assertThat(historyThumbnailSize(6000, 4000)).isEqualTo(192 to 128)
        assertThat(historyThumbnailSize(4000, 6000)).isEqualTo(128 to 192)
    }
    @Test fun smallImagesAreNotUpscaledAndThinImagesRemainValid() {
        assertThat(historyThumbnailSize(80, 60)).isEqualTo(80 to 60)
        assertThat(historyThumbnailSize(1, 6000)).isEqualTo(1 to 192)
    }
}
