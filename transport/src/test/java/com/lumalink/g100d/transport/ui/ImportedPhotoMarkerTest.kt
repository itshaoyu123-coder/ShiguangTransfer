package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import com.lumalink.g100d.core.data.TransferState
import com.lumalink.g100d.protocol.CameraFile
import org.junit.Test

class ImportedPhotoMarkerTest {
    private val photo = CameraFile("http://192.168.54.1:50001/DO01000001.JPG", "PANA0001.JPG", 100)
    private fun record(id: String, state: TransferState = TransferState.COMPLETED) = TransferItem(
        id, photo.name, photo.sizeBytes, 100, 0, state, null, "content://media/$id", "image/jpeg", 1,
        cameraPath = photo.path)

    @Test fun onlyCompletedImportsReceiveMark() {
        for (state in TransferState.entries) {
            assertThat(importKey(photo) in completedImportKeys(listOf(record("a", state))))
                .isEqualTo(state == TransferState.COMPLETED)
        }
    }
    @Test fun identicalNamesFromDifferentDirectoriesAreNotConfused() {
        val keys = completedImportKeys(listOf(record("a")))
        assertThat(importKey(photo.copy(path = "http://192.168.54.1:50001/DO02000001.JPG")) in keys).isFalse()
    }
    @Test fun jpegDoesNotMarkRawTwinOrChangedResourceSize() {
        val keys = completedImportKeys(listOf(record("a")))
        assertThat(importKey(photo.copy(path = photo.path.replace(".JPG", ".RW2"), name = "PANA0001.RW2")) in keys).isFalse()
        assertThat(importKey(photo.copy(sizeBytes = 101)) in keys).isFalse()
    }
    @Test fun removingHistoryUpdatesMarkButAnotherCompletedCopyStillCounts() {
        val a = record("a"); val b = record("b")
        assertThat(completedImportKeys(listOf(a, b))).hasSize(1)
        assertThat(importKey(photo) in completedImportKeys(listOf(b))).isTrue()
        assertThat(completedImportKeys(emptyList())).isEmpty()
    }
    @Test fun incompleteLocalReferenceCannotProduceCompletedBadge() {
        assertThat(completedImportKeys(listOf(record("a").copy(localUri = null)))).isEmpty()
        assertThat(completedImportKeys(listOf(record("a").copy(cameraPath = "")))).isEmpty()
    }
}
