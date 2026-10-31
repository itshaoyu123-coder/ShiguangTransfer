package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import com.lumalink.g100d.transport.session.cameraCredentialsError
import org.junit.Test

class CameraCredentialsTest {
    @Test fun acceptsCameraAndOpenHotspot() {
        assertThat(cameraCredentialsError("S5M2-12345", "camera123")).isNull()
        assertThat(cameraCredentialsError("S5M2-12345", "")).isNull()
    }
    @Test fun rejectsInvalidWifiInputsBeforeAndroidBuilderCanThrow() {
        assertThat(cameraCredentialsError("", "password")).isNotNull()
        assertThat(cameraCredentialsError("相".repeat(11), "password")).isNotNull()
        assertThat(cameraCredentialsError("S5", "short")).isNotNull()
        assertThat(cameraCredentialsError("S5", "a".repeat(64))).isNotNull()
    }
}
