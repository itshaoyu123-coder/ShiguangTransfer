package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import com.lumalink.g100d.protocol.CameraFile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class GalleryDatePolicyTest {
    private val zone = ZoneId.of("UTC")
    private val taken = Instant.parse("2026-10-01T23:30:00Z").epochSecond
    @Test fun cameraCaptureDateTakesPrecedenceOverCachedExifDate() {
        val file = CameraFile("/a.JPG", "a.JPG", 1, capturedAtEpochSeconds = taken)
        assertThat(captureDay(file, mapOf(file.path to taken + 86400), zone)).isEqualTo(LocalDate.of(2026,10,1))
    }
    @Test fun rawCanUseExifFromItsMatchingJpegResource() {
        val file = CameraFile("/DO0001.RW2", "a.RW2", 1)
        assertThat(captureDay(file, mapOf("/DO0001.JPG" to taken), zone)).isEqualTo(LocalDate.of(2026,10,1))
        assertThat(captureDay(file, mapOf("/other/DO0001.JPG" to taken), zone)).isNull()
    }
    @Test fun unknownDatesHaveTheirOwnFilterWithoutBeingInvented() {
        assertThat(captureDay(CameraFile("/a.JPG", "a.JPG", 1), emptyMap(), zone)).isNull()
        assertThat(matchesGalleryDate(null, UNKNOWN_GALLERY_DATE)).isTrue()
        assertThat(matchesGalleryDate(null, "2026-10-01")).isFalse()
        assertThat(matchesGalleryDate(null, null)).isTrue()
        assertThat(matchesGalleryDate(LocalDate.of(2026,10,1), "2026-10-01")).isTrue()
    }
    @Test fun dateFilteringUsesLocalDayAcrossTimezoneBoundary() {
        val file = CameraFile("/a.JPG", "a.JPG", 1, capturedAtEpochSeconds = taken)
        assertThat(captureDay(file, emptyMap(), ZoneId.of("Asia/Shanghai"))).isEqualTo(LocalDate.of(2026,10,2))
    }
}
