package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import com.lumalink.g100d.protocol.CameraFile
import org.junit.Test

class NearbyDatePriorityTest {
    private val photos = (0..9).map { CameraFile("/DO$it.JPG", "$it.JPG", 1) }
    private val positions = photos.mapIndexed { i, file -> photoResourceKey(file.path) to i }.toMap()
    @Test fun visiblePhotoThenNearestPendingPhotoInsteadOfStartOfCard() {
        assertThat(nextNearbyDateFile(photos, positions, listOf(photos[7].path))).isEqualTo(photos[7])
        assertThat(nextNearbyDateFile(photos.filterNot { it == photos[7] }, positions, listOf(photos[7].path))).isEqualTo(photos[6])
    }
    @Test fun newViewportChangesPriorityAndRawSharesJpegPosition() {
        assertThat(nextNearbyDateFile(photos, positions, listOf("/DO8.RW2"))).isEqualTo(photos[8])
        assertThat(nextNearbyDateFile(photos, positions, listOf(photos[2].path))).isEqualTo(photos[2])
    }
    @Test fun unknownViewportFallsBackSafelyAndEmptyWorkEnds() {
        assertThat(nextNearbyDateFile(photos, positions, listOf("/another-card.JPG"))).isEqualTo(photos.first())
        assertThat(nextNearbyDateFile(emptyList(), positions, listOf(photos[3].path))).isNull()
    }
}
