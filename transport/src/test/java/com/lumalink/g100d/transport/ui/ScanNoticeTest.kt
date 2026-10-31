package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScanNoticeTest {
    @Test fun partialListFailureIsNonFatalAndDoesNotExposeDuplicateTechnicalError() {
        val notice = scanNotice(298, false, false, "Software caused connection abort", "Software caused connection abort")!!
        assertThat(notice.fatal).isFalse()
        assertThat(notice.title).isEqualTo("后台扫描暂停")
        assertThat(notice.body).contains("298")
        assertThat(notice.body).doesNotContain("Software caused connection abort")
    }
    @Test fun noLoadedFilesStillShowsFatalReadFailure() {
        val notice = scanNotice(0, false, false, "UPnP HTTP 500", "UPnP HTTP 500")!!
        assertThat(notice.fatal).isTrue()
        assertThat(notice.body).contains("500")
    }
    @Test fun completedScanDoesNotShowStaleError() {
        assertThat(scanNotice(298, false, true, "old error", null)).isNull()
    }
    @Test fun unavailableTotalWarningRemainsInformationalDuringScan() {
        val notice = scanNotice(50, true, false, null, "相机未提供总数")!!
        assertThat(notice.fatal).isFalse()
        assertThat(notice.title).isEqualTo("读取提示")
        assertThat(notice.body).contains("未提供总数")
    }
}
