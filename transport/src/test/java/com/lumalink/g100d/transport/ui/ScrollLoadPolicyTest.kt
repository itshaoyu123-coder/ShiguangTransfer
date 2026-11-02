package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScrollLoadPolicyTest {
    @Test fun slowScrollingContinuesLoadingAndMediumDoesNotCancelExistingWork() {
        val policy = ScrollLoadPolicy()
        policy.sample(0.0, 0, false)
        assertThat(policy.sample(.02, 50, true)).isEqualTo(ThumbnailLoadMode.LOAD)
        assertThat(policy.sample(.12, 100, true)).isEqualTo(ThumbnailLoadMode.HOLD)
    }
    @Test fun fastScrollingRequiresConfirmationAndSlowRecoveryHasHysteresis() {
        val policy = ScrollLoadPolicy()
        policy.sample(0.0, 0, false)
        assertThat(policy.sample(.2, 50, true)).isEqualTo(ThumbnailLoadMode.HOLD)
        assertThat(policy.sample(.4, 100, true)).isEqualTo(ThumbnailLoadMode.HOLD)
        assertThat(policy.sample(.6, 150, true)).isEqualTo(ThumbnailLoadMode.PAUSE)
        assertThat(policy.sample(.7, 200, true)).isEqualTo(ThumbnailLoadMode.PAUSE)
        assertThat(policy.sample(.72, 250, true)).isEqualTo(ThumbnailLoadMode.PAUSE)
        assertThat(policy.sample(.74, 300, true)).isEqualTo(ThumbnailLoadMode.PAUSE)
        assertThat(policy.sample(.78, 400, true)).isEqualTo(ThumbnailLoadMode.LOAD)
    }
    @Test fun reverseScrollingAndStoppingRecoverWithoutAnotherSwipe() {
        val policy = ScrollLoadPolicy()
        policy.sample(2.0, 0, false)
        policy.sample(1.8, 50, true)
        assertThat(policy.sample(1.4, 150, true)).isEqualTo(ThumbnailLoadMode.PAUSE)
        policy.sample(1.4, 200, false)
        assertThat(policy.sample(1.4, 350, false)).isEqualTo(ThumbnailLoadMode.LOAD)
    }
}
