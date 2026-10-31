package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import com.lumalink.g100d.transport.transfer.validDownloadRange
import org.junit.Test

class DownloadRangeTest {
    @Test fun acceptsMatchingFullRemainder() { assertThat(validDownloadRange("bytes 500-999/1000", 500, 1000)).isTrue() }
    @Test fun rejectsWrongOffsetsTotalsAndTruncatedRanges() {
        assertThat(validDownloadRange("bytes 0-999/1000", 500, 1000)).isFalse()
        assertThat(validDownloadRange("bytes 500-998/1000", 500, 1000)).isFalse()
        assertThat(validDownloadRange("bytes 500-1999/2000", 500, 1000)).isFalse()
        assertThat(validDownloadRange(null, 500, 1000)).isFalse()
    }
}
