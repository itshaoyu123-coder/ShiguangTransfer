package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CameraDisplayNameTest {
    @Test fun displaysActualModelWithoutConfusingS5S9OrS5II() {
        assertThat(cameraDisplayName("DC-S9")).isEqualTo("S9")
        assertThat(cameraDisplayName("DC-S5")).isEqualTo("S5")
        assertThat(cameraDisplayName("LUMIX DC-S5M2")).isEqualTo("S5 II")
        assertThat(cameraDisplayName("DC-S5M2X")).isEqualTo("S5 IIX")
        assertThat(cameraDisplayName("DC-GH7")).isEqualTo("GH7")
    }
    @Test fun missingIdentificationDoesNotInventAModel() {
        assertThat(cameraDisplayName(null)).isEqualTo("松下相机")
        assertThat(cameraDisplayName("LUMIX")).isEqualTo("松下相机")
    }
}
