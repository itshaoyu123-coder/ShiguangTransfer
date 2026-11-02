package com.lumalink.g100d.transport.transfer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadRetryPolicyTest {
    @Test fun persistentFailuresDoNotWasteMoreCameraRequests() {
        for (code in listOf(400,401,403,404,410)) assertThat(shouldRetryDownload("HTTP $code；已保留下载进度", 0)).isFalse()
        assertThat(shouldRetryDownload("write failed: ENOSPC", 0)).isFalse()
    }
    @Test fun transientFailuresAndInvalidResumeHaveBoundedRecovery() {
        for (error in listOf("Read timed out", "HTTP 500", "HTTP 503", "HTTP 408", "HTTP 429", "HTTP 416")) {
            assertThat(shouldRetryDownload(error, 0)).isTrue()
            assertThat(shouldRetryDownload(error, 1)).isTrue()
            assertThat(shouldRetryDownload(error, 2)).isFalse()
        }
    }
}
