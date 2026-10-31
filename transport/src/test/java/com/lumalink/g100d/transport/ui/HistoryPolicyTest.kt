package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import com.lumalink.g100d.core.data.TransferState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class HistoryPolicyTest {
    private fun item(id: String, time: String, state: TransferState = TransferState.COMPLETED, created: String = time) =
        TransferItem(id, "photo.JPG", 10, 10, 0, state, null, "content://media/$id", "image/jpeg",
            Instant.parse(time).toEpochMilli(), Instant.parse(created).toEpochMilli())

    @Test fun groupsByDayAndSortsDaysAndRecordsNewestFirst() {
        val a = item("a", "2026-10-01T10:00:00Z")
        val b = item("b", "2026-10-02T09:00:00Z")
        val c = item("c", "2026-10-02T15:00:00Z")
        val days = historyDays(listOf(a, b, c), ZoneId.of("UTC"))
        assertThat(days.map { it.date }).containsExactly(LocalDate.of(2026,10,2), LocalDate.of(2026,10,1)).inOrder()
        assertThat(days.first().items.map { it.id }).containsExactly("c", "b").inOrder()
    }
    @Test fun usesPhoneTimezoneAtMidnight() {
        val days = historyDays(listOf(item("a", "2026-10-02T00:30:00Z")), ZoneId.of("America/Los_Angeles"))
        assertThat(days.single().date).isEqualTo(LocalDate.of(2026,10,1))
    }
    @Test fun completionDayCanDifferFromQueueDay() {
        val complete = item("a", "2026-10-02T00:30:00Z", created = "2026-10-01T23:59:00Z")
        assertThat(historyDays(listOf(complete), ZoneId.of("UTC")).single().date).isEqualTo(LocalDate.of(2026,10,2))
    }
    @Test fun ongoingProgressDoesNotMoveQueueRecordToAnotherDay() {
        val running = item("a", "2026-10-02T00:30:00Z", TransferState.RUNNING, "2026-10-01T23:59:00Z")
        assertThat(historyDays(listOf(running), ZoneId.of("UTC")).single().date).isEqualTo(LocalDate.of(2026,10,1))
    }
    @Test fun activeRecordsCannotBeDeletedWhileWorkerOwnsThem() {
        assertThat(canDeleteHistory(item("a", "2026-10-01T00:00:00Z", TransferState.RUNNING))).isFalse()
        assertThat(canDeleteHistory(item("b", "2026-10-01T00:00:00Z", TransferState.QUEUED))).isFalse()
        assertThat(canDeleteHistory(item("c", "2026-10-01T00:00:00Z", TransferState.COMPLETED))).isTrue()
        assertThat(canDeleteHistory(item("d", "2026-10-01T00:00:00Z", TransferState.FAILED))).isTrue()
    }
    @Test fun normalizesPhotoMimeWithoutPretendingVideoIsAnImage() {
        assertThat(labPhotoMime("PANA.JPG")).isEqualTo("image/jpeg")
        assertThat(labPhotoMime("PANA.RW2")).isEqualTo("image/x-panasonic-rw2")
        assertThat(labPhotoMime("video.MP4")).isNull()
    }
}
