package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class CameraDateParserTest {
    @Test fun readsOffsetTimestamp() {
        assertThat(parseCameraDate("2026-10-01T08:00:00+08:00")).isEqualTo(Instant.parse("2026-10-01T00:00:00Z").epochSecond)
    }
    @Test fun readsCameraLocalDateWithoutDiscardingMissingOffset() {
        val zone = ZoneId.of("Asia/Shanghai")
        val expected = Instant.parse("2026-10-01T00:00:00Z").epochSecond
        assertThat(parseCameraDate("2026-10-01T08:00:00", zone)).isEqualTo(expected)
        assertThat(parseCameraDate("2026:10:01 08:00:00", zone)).isEqualTo(expected)
    }
    @Test fun normalizesEpochSecondsAndMilliseconds() {
        assertThat(parseCameraDate("1790812800000")).isEqualTo(parseCameraDate("1790812800"))
    }
    @Test fun missingOrInvalidDateStaysUnknown() {
        assertThat(parseCameraDate(null)).isNull()
        assertThat(parseCameraDate("not-a-date")).isNull()
        assertThat(parseCameraDate("0")).isNull()
    }
}
